/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.tools;

//## AWT begin ##

import org.h2.constant.SysProperties;
import org.h2.server.ShutdownHandler;
import org.h2.util.Resources;
import org.h2.util.StartBrowser;
import org.h2.util.Tool;

import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.sql.SQLException;

//## AWT end ##

/**
 * Starts the H2 Console (web-) server, as well as the TCP and PG server.
 *
 * @author Thomas Mueller, Ridvan Agar
 * @h2.resource
 */
public class Console extends Tool implements
//## AWT begin ##
        ActionListener, MouseListener,
//## AWT end ##
        ShutdownHandler {

    //## AWT begin ##
    Frame frame;
    private Font font;
    private Button startBrowser;
    //## AWT end ##
    private Server web, tcp, pg;
    private boolean isWindows;

    /**
     * When running without options, -tcp, -web, -browser and -pg are started.
     * <br />
     * Options are case sensitive. Supported options are:
     * <table>
     * <tr><td>[-help] or [-?]</td>
     * <td>Print the list of options</td></tr>
     * <tr><td>[-web]</td>
     * <td>Start the web server with the H2 Console</td></tr>
     * <tr><td>[-tool]</td>
     * <td>Start the icon or window that allows to start a browser</td></tr>
     * <tr><td>[-browser]</td>
     * <td>Start a browser and open a page to connect to the web server</td></tr>
     * <tr><td>[-tcp]</td>
     * <td>Start the TCP server</td></tr>
     * <tr><td>[-pg]</td>
     * <td>Start the PG server</td></tr>
     * </table>
     * For each Server, additional options are available;
     * for details, see the Server tool.<br />
     * If a service can not be started, the program
     * terminates with an exit code of 1.
     *
     * @param args the command line arguments
     * @h2.resource
     */
    public static void main(String... args) throws SQLException {
        new Console().run(args);
    }

    /**
     * This tool starts the H2 Console (web-) server, as well as the TCP and PG
     * server. For JDK 1.6, a system tray icon is created, for platforms that
     * support it. Otherwise, a small window opens.
     *
     * @param args the command line arguments
     */
    public void run(String... args) throws SQLException {
        isWindows = SysProperties.getStringSetting("os.name", "").startsWith("Windows");
        boolean tcpStart = false, pgStart = false, webStart = false, toolStart = false;
        boolean browserStart = false;
        boolean startDefaultServers = true;

        for (int i = 0; args != null && i < args.length; i++) {
            String arg = args[i];
            if (arg == null) {
                continue;
            } else if ("-?".equals(arg) || "-help".equals(arg)) {
                showUsage();
                return;
            } else if ("-web".equals(arg)) {
                startDefaultServers = false;
                webStart = true;
            } else if ("-tool".equals(arg)) {
                startDefaultServers = false;
                webStart = true;
                toolStart = true;
            } else if ("-browser".equals(arg)) {
                startDefaultServers = false;
                webStart = true;
                browserStart = true;
            } else if ("-tcp".equals(arg)) {
                startDefaultServers = false;
                tcpStart = true;
            } else if ("-pg".equals(arg)) {
                startDefaultServers = false;
                pgStart = true;
            }
        }
        if (startDefaultServers) {
            webStart = true;
            toolStart = true;
            browserStart = true;
            tcpStart = true;
            pgStart = true;
        }
        SQLException startException = null;
        boolean webRunning = false;
        if (webStart) {
            try {
                web = Server.createWebServer(args);
                web.setShutdownHandler(this);
                web.start();
                webRunning = true;
            } catch (SQLException e) {
                printProblem(e, web);
                startException = e;
            }
        }

//## AWT begin ##
        if (toolStart && webRunning && !GraphicsEnvironment.isHeadless()) {
            loadFont();
            try {
                if (!createTrayIcon()) {
                    showWindow(true);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
//## AWT end ##

        // start browser in any case (even if the server is already running)
        // because some people don't look at the output,
        // but are wondering why nothing happens
        if (browserStart) {
            StartBrowser.openURL(web.getURL());
        }

        if (tcpStart) {
            try {
                tcp = Server.createTcpServer(args);
                tcp.start();
            } catch (SQLException e) {
                printProblem(e, tcp);
                if (startException == null) {
                    startException = e;
                }
            }
        }
        if (pgStart) {
            try {
                pg = Server.createPgServer(args);
                pg.start();
            } catch (SQLException e) {
                printProblem(e, pg);
                if (startException == null) {
                    startException = e;
                }
            }
        }
        if (startException != null) {
            throw startException;
        }
    }

    private void printProblem(SQLException e, Server server) {
        if (server == null) {
            e.printStackTrace();
        } else {
            out.println(server.getStatus());
            out.println("Root cause: " + e.getMessage());
        }
    }

    private Image loadImage(String name) {
        try {
            byte[] imageData = Resources.get(name);
            if (imageData == null) {
                return null;
            }
            return Toolkit.getDefaultToolkit().createImage(imageData);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * INTERNAL
     */
    public void shutdown() {
        stopAll();
    }

    /**
     * Stop all servers that were started using the console.
     */
    void stopAll() {
        if (web != null && web.isRunning(false)) {
            web.stop();
            web = null;
        }
        if (tcp != null && tcp.isRunning(false)) {
            tcp.stop();
            tcp = null;
        }
        if (pg != null && pg.isRunning(false)) {
            pg.stop();
            pg = null;
        }
//## AWT begin ##
        if (frame != null) {
            frame.dispose();
            frame = null;
        }
//## AWT end ##
        System.exit(0);
    }

    //## AWT begin ##
    private void loadFont() {
        if (isWindows) {
            font = new Font("Dialog", Font.PLAIN, 11);
        } else {
            font = new Font("Dialog", Font.PLAIN, 12);
        }
    }

    private boolean createTrayIcon() {
        try {
            // SystemTray.isSupported();
            Boolean supported = (Boolean) Class.forName("java.awt.SystemTray").
                    getMethod("isSupported").
                    invoke(null);
            if (!supported) {
                return false;
            }
            PopupMenu menuConsole = new PopupMenu();
            MenuItem itemConsole = new MenuItem("H2 Console");
            itemConsole.setActionCommand("console");
            itemConsole.addActionListener(this);
            itemConsole.setFont(font);
            menuConsole.add(itemConsole);
            MenuItem itemStatus = new MenuItem("Status");
            itemStatus.setActionCommand("status");
            itemStatus.addActionListener(this);
            itemStatus.setFont(font);
            menuConsole.add(itemStatus);
            MenuItem itemExit = new MenuItem("Exit");
            itemExit.setFont(font);
            itemExit.setActionCommand("exit");
            itemExit.addActionListener(this);
            menuConsole.add(itemExit);

            // SystemTray tray = SystemTray.getSystemTray();
            Object tray = Class.forName("java.awt.SystemTray").
                    getMethod("getSystemTray").
                    invoke(null);

            // Dimension d = tray.getTrayIconSize();
            Dimension d = (Dimension) Class.forName("java.awt.SystemTray").
                    getMethod("getTrayIconSize").
                    invoke(tray);
            String iconFile;
            if (d.width >= 24 && d.height >= 24) {
                iconFile = "/org/h2/res/h2-24.png";
            } else if (d.width >= 22 && d.height >= 22) {
                iconFile = "/org/h2/res/h2-22.png";
            } else {
                iconFile = "/org/h2/res/h2.png";
            }
            Image icon = loadImage(iconFile);
            // TrayIcon icon = new TrayIcon(image, "H2 Database Engine", menuConsole);
            Object trayIcon = Class.forName("java.awt.TrayIcon").
                    getConstructor(Image.class, String.class, PopupMenu.class).
                    newInstance(icon, "H2 Database Engine", menuConsole);

            // trayIcon.addMouseListener(this);
            trayIcon.getClass().
                    getMethod("addMouseListener", MouseListener.class).
                    invoke(trayIcon, this);

            // tray.add(icon);
            tray.getClass().
                    getMethod("add", Class.forName("java.awt.TrayIcon")).
                    invoke(tray, trayIcon);

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void showWindow(final boolean exit) {
        frame = new Frame("H2 Console");
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent we) {
                if (exit) {
                    stopAll();
                } else {
                    frame.dispose();
                }
            }
        });
        Image image = loadImage("/org/h2/res/h2.png");
        if (image != null) {
            frame.setIconImage(image);
        }
        frame.setResizable(false);
        frame.setBackground(SystemColor.control);

        GridBagLayout layout = new GridBagLayout();
        frame.setLayout(layout);

        // the main panel keeps everything together
        Panel mainPanel = new Panel(layout);

        GridBagConstraints constraintsPanel = new GridBagConstraints();
        constraintsPanel.gridx = 0;
        constraintsPanel.weightx = 1.0D;
        constraintsPanel.weighty = 1.0D;
        constraintsPanel.fill = GridBagConstraints.BOTH;
        constraintsPanel.insets = new Insets(0, 10, 0, 10);
        constraintsPanel.gridy = 0;

        GridBagConstraints constraintsButton = new GridBagConstraints();
        constraintsButton.gridx = 0;
        constraintsButton.gridwidth = 2;
        constraintsButton.insets = new Insets(10, 0, 0, 0);
        constraintsButton.gridy = 1;
        constraintsButton.anchor = GridBagConstraints.EAST;

        GridBagConstraints constraintsTextField = new GridBagConstraints();
        constraintsTextField.fill = GridBagConstraints.HORIZONTAL;
        constraintsTextField.gridy = 0;
        constraintsTextField.weightx = 1.0;
        constraintsTextField.insets = new Insets(0, 5, 0, 0);
        constraintsTextField.gridx = 1;

        GridBagConstraints constraintsLabel = new GridBagConstraints();
        constraintsLabel.gridx = 0;
        constraintsLabel.gridy = 0;

        Label label = new Label("H2 Console URL:", Label.LEFT);
        label.setFont(font);
        mainPanel.add(label, constraintsLabel);

        TextField text = new TextField();
        text.setEditable(false);
        text.setFont(font);
        text.setText(web.getURL());
        if (isWindows) {
            text.setFocusable(false);
        }
        mainPanel.add(text, constraintsTextField);

        startBrowser = new Button("Start Browser");
        startBrowser.setFocusable(false);
        startBrowser.setActionCommand("console");
        startBrowser.addActionListener(this);
        startBrowser.setFont(font);
        mainPanel.add(startBrowser, constraintsButton);
        frame.add(mainPanel, constraintsPanel);

        int width = 300, height = 120;
        frame.setSize(width, height);
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        frame.setLocation((screenSize.width - width) / 2, (screenSize.height - height) / 2);
        try {
            frame.setVisible(true);
        } catch (Throwable t) {
            // ignore
            // some systems don't support this method, for example IKVM
            // however it still works
        }
    }

    private void startBrowser() {
        if (web != null) {
            StartBrowser.openURL(web.getURL());
        }
    }
//## AWT end ##

    /**
     * INTERNAL
     */
//## AWT begin ##
    public void actionPerformed(ActionEvent e) {
        String command = e.getActionCommand();
        if ("exit".equals(command)) {
            stopAll();
        } else if ("console".equals(command)) {
            startBrowser();
        } else if ("status".equals(command)) {
            showWindow(false);
        } else if (startBrowser == e.getSource()) {
            // for some reason, IKVM ignores setActionCommand
            startBrowser();
        }
    }
//## AWT end ##

    /**
     * INTERNAL
     */
//## AWT begin ##
    public void mouseClicked(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            startBrowser();
        }
    }
//## AWT end ##

    /**
     * INTERNAL
     */
//## AWT begin ##
    public void mouseEntered(MouseEvent e) {
        // nothing to do
    }
//## AWT end ##

    /**
     * INTERNAL
     */
//## AWT begin ##
    public void mouseExited(MouseEvent e) {
        // nothing to do
    }
//## AWT end ##

    /**
     * INTERNAL
     */
//## AWT begin ##
    public void mousePressed(MouseEvent e) {
        // nothing to do
    }
//## AWT end ##

    /**
     * INTERNAL
     */
//## AWT begin ##
    public void mouseReleased(MouseEvent e) {
        // nothing to do
    }
//## AWT end ##

}
