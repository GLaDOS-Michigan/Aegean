package BFT.logdaemon;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;

public class LogDaemonServer extends Thread {
    private LogDaemon logDaemon = null;

    private int port;
    private LinkedBlockingQueue<LogRequest> requestQueue = new LinkedBlockingQueue<LogRequest>();

    public LogDaemonServer(String logDir, int port) {
        logDaemon = new LogDaemon(logDir, 1048576);
        this.port = port;
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java LogDaemonServer logDir port");
        }
        new LogDaemonServer(args[0], Integer.parseInt(args[1])).start();
    }

    ServerSocket serverSocket = null;

    public void run() {
        try {
            System.out.println("Server port=" + port);
            serverSocket = new ServerSocket(port);
            Socket sock;
            while ((sock = serverSocket.accept()) != null) {
                new ServerThread(sock).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (Exception e2) {
                    e2.printStackTrace();
                }
            }

        }
    }

    private class ServerThread extends Thread {
        private Socket sock;

        public ServerThread(Socket sock) {
            this.sock = sock;
        }

        public void run() {
            try {
                DataInputStream in = new DataInputStream(sock.getInputStream());
                DataOutputStream out = new DataOutputStream(sock
                        .getOutputStream());
                while (true) {
                    int type = in.readInt();
                    int tag = in.readInt();
                    switch (type) {
                        case LogRequest.WRITE:
                            int size = in.readInt();
                            byte[] data = new byte[size];
                            in.readFully(data);
                            try {
                                logDaemon.writeData(tag, data);
                                logDaemon.flush();
                                out.writeInt(LogRequest.WRITE_OK);
                            } catch (Exception e) {
                                e.printStackTrace();
                                out.writeInt(LogRequest.WRITE_FAIL);
                            }
                            break;
                        case LogRequest.READ:
                            try {
                                ArrayList<byte[]> ret = logDaemon.read(tag);
                                out.writeInt(LogRequest.READ_OK);
                                out.writeInt(ret.size());
                                for (int i = 0; i < ret.size(); i++) {
                                    out.writeInt(ret.get(i).length);
                                    out.write(ret.get(i));
                                }
                            } catch (Exception e) {
                                out.writeInt(LogRequest.READ_FAIL);
                            }
                            break;
                        case LogRequest.BARRIER:
                            try {
                                long seqNo = in.readLong();
                                logDaemon.writeBarrier(tag, seqNo);
                                logDaemon.flush();
                                out.writeInt(LogRequest.BARRIER_OK);
                            } catch (Exception e) {
                                e.printStackTrace();
                                out.writeInt(LogRequest.BARRIER_FAIL);
                            }
                            break;
                        case LogRequest.GC:
                            try {
                                long seqNo = in.readLong();
                                logDaemon.gc(tag, seqNo);
                                out.writeInt(LogRequest.GC_OK);
                            } catch (Exception e) {
                                e.printStackTrace();
                                out.writeInt(LogRequest.GC_FAIL);
                            }
                            break;
                        default:
                            throw new RuntimeException("Unknown type " + type);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (sock != null) {
                    try {
                        sock.close();
                    } catch (Exception e2) {
                        e2.printStackTrace();
                    }
                }
            }
        }
    }

}
