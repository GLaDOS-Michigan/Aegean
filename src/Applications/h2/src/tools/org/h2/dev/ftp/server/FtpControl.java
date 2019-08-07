/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.dev.ftp.server;

import org.h2.engine.Constants;
import org.h2.store.fs.FileSystem;
import org.h2.util.StringUtils;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;

/**
 * The implementation of the control channel of the FTP server.
 */
public class FtpControl extends Thread {

    private static final String SERVER_NAME = "Small FTP Server";

    private FtpServer server;
    private FileSystem fs;
    private Socket control;
    private FtpData data;
    private PrintWriter output;
    private String userName;
    private boolean connected, readonly;
    private String currentDir = "/";
    private String serverIpAddress;
    private boolean stop;
    private String renameFrom;
    private boolean replied;
    private long restart;

    FtpControl(Socket control, FtpServer server, boolean stop) {
        this.server = server;
        this.fs = server.getFileSystem();
        this.control = control;
        this.stop = stop;
    }

    public void run() {
        try {
            output = new PrintWriter(new OutputStreamWriter(control.getOutputStream(), Constants.UTF8));
            if (stop) {
                reply(421, "Too many users");
            } else {
                reply(220, SERVER_NAME);
                // TODO need option to configure the serverIpAddress?
                serverIpAddress = control.getLocalAddress().getHostAddress().replace('.', ',');
                BufferedReader input = new BufferedReader(new InputStreamReader(control.getInputStream()));
                while (!stop) {
                    String command = null;
                    try {
                        command = input.readLine();
                    } catch (IOException e) {
                        // ignore
                    }
                    if (command == null) {
                        break;
                    }
                    process(command);
                }
                if (data != null) {
                    data.close();
                }
            }
        } catch (Throwable t) {
            server.traceError(t);
        }
        server.closeConnection();
    }

    private void process(String command) throws SQLException, IOException {
        int idx = command.indexOf(' ');
        String param = "";
        if (idx >= 0) {
            param = command.substring(idx).trim();
            command = command.substring(0, idx);
        }
        command = StringUtils.toUpperEnglish(command);
        if (command.length() == 0) {
            reply(506, "No command");
            return;
        }
        server.trace(">" + command);
        FtpEventListener listener = server.getEventListener();
        FtpEvent event = null;
        if (listener != null) {
            event = new FtpEvent(this, command, param);
            listener.beforeCommand(event);
        }
        replied = false;
        if (connected) {
            processConnected(command, param);
        }
        if (!replied) {
            if ("USER".equals(command)) {
                userName = param;
                reply(331, "Need password");
            } else if ("QUIT".equals(command)) {
                reply(221, "Bye");
                stop = true;
            } else if ("PASS".equals(command)) {
                if (userName == null) {
                    reply(332, "Need username");
                } else if (server.checkUserPasswordWrite(userName, param)) {
                    reply(230, "Ok");
                    readonly = false;
                    connected = true;
                } else if (server.checkUserPasswordReadOnly(userName)) {
                    reply(230, "Ok, readonly");
                    readonly = true;
                    connected = true;
                } else {
                    reply(431, "Wrong user/password");
                }
            } else if ("REIN".equals(command)) {
                userName = null;
                connected = false;
                currentDir = "/";
                reply(200, "Ok");
            } else if ("HELP".equals(command)) {
                reply(214, SERVER_NAME);
            }
        }
        if (!replied) {
            if (listener != null) {
                listener.onUnsupportedCommand(event);
            }
            reply(506, "Invalid command");
        }
        if (listener != null) {
            listener.afterCommand(event);
        }
    }

    private void processConnected(String command, String param) throws SQLException, IOException {
        switch (command.charAt(0)) {
            case 'C':
                if ("CWD".equals(command)) {
                    String path = getPath(param);
                    String fileName = getFileName(path);
                    if (fs.exists(fileName) && fs.isDirectory(fileName)) {
                        if (!path.endsWith("/")) {
                            path += "/";
                        }
                        currentDir = path;
                        reply(250, "Ok");
                    } else {
                        reply(550, "Failed");
                    }
                } else if ("CDUP".equals(command)) {
                    if (currentDir.length() > 1) {
                        int idx = currentDir.lastIndexOf("/", currentDir.length() - 2);
                        currentDir = currentDir.substring(0, idx + 1);
                        reply(250, "Ok");
                    } else {
                        reply(550, "Failed");
                    }
                }
                break;
            case 'D':
                if ("DELE".equals(command)) {
                    String fileName = getFileName(param);
                    if (!readonly && fs.exists(fileName) && !fs.isDirectory(fileName) && fs.tryDelete(fileName)) {
                        if (server.getAllowTask() && fileName.endsWith(FtpServer.TASK_SUFFIX)) {
                            server.stopTask(fileName);
                        }
                        reply(250, "Ok");
                    } else {
                        reply(500, "Delete failed");
                    }
                }
                break;
            case 'L':
                if ("LIST".equals(command)) {
                    processList(param, true);
                }
                break;
            case 'M':
                if ("MKD".equals(command)) {
                    processMakeDir(param);
                } else if ("MODE".equals(command)) {
                    if ("S".equals(StringUtils.toUpperEnglish(param))) {
                        reply(200, "Ok");
                    } else {
                        reply(504, "Invalid");
                    }
                } else if ("MDTM".equals(command)) {
                    String fileName = getFileName(param);
                    if (fs.exists(fileName) && !fs.isDirectory(fileName)) {
                        reply(213, server.formatLastModified(fileName));
                    } else {
                        reply(550, "Failed");
                    }
                }
                break;
            case 'N':
                if ("NLST".equals(command)) {
                    processList(param, false);
                } else if ("NOOP".equals(command)) {
                    reply(200, "Ok");
                }
                break;
            case 'P':
                if ("PWD".equals(command)) {
                    reply(257, StringUtils.quoteIdentifier(currentDir) + " directory");
                } else if ("PASV".equals(command)) {
                    ServerSocket dataSocket = server.createDataSocket();
                    data = new FtpData(server, control.getInetAddress(), dataSocket);
                    data.start();
                    int port = dataSocket.getLocalPort();
                    reply(227, "Passive Mode (" + serverIpAddress + "," + (port >> 8) + "," + (port & 255) + ")");
                } else if ("PORT".equals(command)) {
                    String[] list = StringUtils.arraySplit(param, ',', true);
                    String host = list[0] + "." + list[1] + "." + list[2] + "." + list[3];
                    int port = (Integer.parseInt(list[4]) << 8) | Integer.parseInt(list[5]);
                    InetAddress address = InetAddress.getByName(host);
                    if (address.equals(control.getInetAddress())) {
                        data = new FtpData(server, address, port);
                        reply(200, "Ok");
                    } else {
                        server.trace("Port REJECTED:" + address + " expected:" + control.getInetAddress());
                        reply(550, "Failed");
                    }
                }
                break;
            case 'R':
                if ("RNFR".equals(command)) {
                    String fileName = getFileName(param);
                    if (fs.exists(fileName)) {
                        renameFrom = fileName;
                        reply(350, "Ok");
                    } else {
                        reply(450, "Not found");
                    }
                } else if ("RNTO".equals(command)) {
                    if (renameFrom == null) {
                        reply(503, "RNFR required");
                    } else {
                        String fileOld = renameFrom;
                        String fileNew = getFileName(param);
                        boolean ok = false;
                        if (!readonly) {
                            try {
                                fs.rename(fileOld, fileNew);
                                reply(250, "Ok");
                                ok = true;
                            } catch (SQLException e) {
                                server.traceError(e);
                            }
                        }
                        if (!ok) {
                            reply(550, "Failed");
                        }
                    }
                } else if ("RETR".equals(command)) {
                    String fileName = getFileName(param);
                    if (fs.exists(fileName) && !fs.isDirectory(fileName)) {
                        reply(150, "Starting transfer");
                        try {
                            data.send(fs, fileName, restart);
                            reply(226, "Ok");
                        } catch (IOException e) {
                            server.traceError(e);
                            reply(426, "Failed");
                        }
                        restart = 0;
                    } else {
                        // Firefox compatibility
                        // (still not good)
                        processList(param, true);
                        // reply(426, "Not a file");
                    }
                } else if ("RMD".equals(command)) {
                    processRemoveDir(param);
                } else if ("REST".equals(command)) {
                    try {
                        restart = Integer.parseInt(param);
                        reply(350, "Ok");
                    } catch (NumberFormatException e) {
                        reply(500, "Invalid");
                    }
                }
                break;
            case 'S':
                if ("SYST".equals(command)) {
                    reply(215, "UNIX Type: L8");
                } else if ("SITE".equals(command)) {
                    reply(500, "Not understood");
                } else if ("SIZE".equals(command)) {
                    param = getFileName(param);
                    if (fs.exists(param) && !fs.isDirectory(param)) {
                        reply(250, String.valueOf(fs.length(param)));
                    } else {
                        reply(500, "Failed");
                    }
                } else if ("STOR".equals(command)) {
                    String fileName = getFileName(param);
                    if (!readonly && !fs.exists(fileName) || !fs.isDirectory(fileName)) {
                        reply(150, "Starting transfer");
                        try {
                            data.receive(fs, fileName);
                            if (server.getAllowTask() && param.endsWith(FtpServer.TASK_SUFFIX)) {
                                server.startTask(fileName);
                            }
                            reply(226, "Ok");
                        } catch (Exception e) {
                            server.traceError(e);
                            reply(426, "Failed");
                        }
                    } else {
                        reply(550, "Failed");
                    }
                } else if ("STRU".equals(command)) {
                    if ("F".equals(StringUtils.toUpperEnglish(param))) {
                        reply(200, "Ok");
                    } else {
                        reply(504, "Invalid");
                    }
                }
                break;
            case 'T':
                if ("TYPE".equals(command)) {
                    param = StringUtils.toUpperEnglish(param);
                    if ("A".equals(param) || "A N".equals(param)) {
                        reply(200, "Ok");
                    } else if ("I".equals(param) || "L 8".equals(param)) {
                        reply(200, "Ok");
                    } else {
                        reply(500, "Invalid");
                    }
                }
                break;
            case 'X':
                if ("XMKD".equals(command)) {
                    processMakeDir(param);
                } else if ("XRMD".equals(command)) {
                    processRemoveDir(param);
                }
                break;
        }
    }

    private void processMakeDir(String param) {
        String fileName = getFileName(param);
        boolean ok = false;
        if (!readonly) {
            try {
                fs.mkdirs(fileName);
                reply(257, StringUtils.quoteIdentifier(param) + " directory");
                ok = true;
            } catch (SQLException e) {
                server.traceError(e);
            }
        }
        if (!ok) {
            reply(500, "Failed");
        }
    }

    private void processRemoveDir(String param) {
        String fileName = getFileName(param);
        if (!readonly && fs.exists(fileName) && fs.isDirectory(fileName) && fs.tryDelete(fileName)) {
            reply(250, "Ok");
        } else {
            reply(500, "Failed");
        }
    }

    private String getFileName(String file) {
        return server.getFileName(file.startsWith("/") ? file : currentDir + file);
    }

    private String getPath(String path) {
        return path.startsWith("/") ? path : currentDir + path;
    }

    private void processList(String param, boolean directories) throws SQLException, IOException {
        String directory = getFileName(param);
        if (!fs.exists(directory)) {
            reply(450, "Directory does not exist");
            return;
        } else if (!fs.isDirectory(directory)) {
            reply(450, "Not a directory");
            return;
        }
        String list = server.getDirectoryListing(directory, directories);
        reply(150, "Starting transfer");
        server.trace(list);
        // need to use the current locale (UTF-8 would be wrong for the Windows
        // Explorer)
        data.send(list.getBytes());
        reply(226, "Done");
    }

    private void reply(int code, String message) {
        server.trace(code + " " + message);
        output.print(code + " " + message + "\r\n");
        output.flush();
        replied = true;
    }

}
