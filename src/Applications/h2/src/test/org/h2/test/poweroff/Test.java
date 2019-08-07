/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.poweroff;

import org.h2.util.FileUtils;

import java.io.*;
import java.net.Socket;
import java.sql.*;

/**
 * This application tests the durability / non-durability of file systems and
 * databases. Two computers with network connection are required to run this
 * test. Before starting this application, the Listener application must be
 * started on another computer.
 */
public class Test {

    private String url;
    private Connection conn;
    private Statement stat;
    private PreparedStatement prep;

    private Test() {
        // nothing to do
    }

    private Test(String driver, String url, String user, String password, boolean writeDelay0) {
        this.url = url;
        try {
            Class.forName(driver);
            conn = DriverManager.getConnection(url, user, password);
            stat = conn.createStatement();
            if (writeDelay0) {
                stat.execute("SET WRITE_DELAY 0");
            }
            System.out.println(url + " started");
        } catch (Exception e) {
            System.out.println(url + ": " + e.toString());
            return;
        }
        try {
            ResultSet rs = stat.executeQuery("SELECT MAX(ID) FROM TEST");
            rs.next();
            System.out.println(url + ": MAX(ID)=" + rs.getInt(1));
            stat.execute("DROP TABLE TEST");
        } catch (SQLException e) {
            // ignore
        }
        try {
            stat.execute("CREATE TABLE TEST(ID INT PRIMARY KEY, NAME VARCHAR(255))");
            prep = conn.prepareStatement("INSERT INTO TEST VALUES(?, ?)");
        } catch (SQLException e) {
            System.out.println(url + ": " + e.toString());
        }
    }

    private void insert(int id) {
        try {
            if (prep != null) {
                prep.setInt(1, id);
                prep.setString(2, "World " + id);
                prep.execute();
            }
        } catch (SQLException e) {
            System.out.println(url + ": " + e.toString());
        }
    }

    /**
     * This method is called when executing this application from the command
     * line.
     *
     * @param args the command line parameters
     */
    public static void main(String... args) throws Exception {
        new Test().test(args);
    }

    private void test(String... args) throws Exception {
        int port = 9099;
        String connect = "192.168.0.3";
        boolean file = false;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-port")) {
                port = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-connect")) {
                connect = args[++i];
            } else if (args[i].equals("-file")) {
                file = true;
            }
        }
        test(connect, port, file);
    }

    private void test(String connect, int port, boolean file) throws Exception {
        Socket socket = new Socket(connect, port);
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        System.out.println("Connected to " + socket.toString());
        if (file) {
            testFile(out);
        } else {
            testDatabases(out);
        }
    }

    private void testFile(DataOutputStream out) throws IOException {
        File file = new File("test.txt");
        if (file.exists()) {
            file.delete();
        }
        RandomAccessFile write = new RandomAccessFile(file, "rws");
        // RandomAccessFile write = new RandomAccessFile(file, "rwd");
        int fileSize = 10 * 1024 * 1024;
        FileUtils.setLength(write, fileSize);
        write.seek(0);
        int i = 0;
        FileDescriptor fd = write.getFD();
        while (true) {
            if (write.getFilePointer() >= fileSize) {
                break;
            }
            write.writeBytes(i + "\r\n");
            fd.sync();
            out.writeInt(i);
            out.flush();
            i++;
        }
        write.close();
    }

    private void testDatabases(DataOutputStream out) throws Exception {
        Test[] dbs = new Test[]{
                new Test("org.h2.Driver", "jdbc:h2:test1", "sa", "", true),
                new Test("org.h2.Driver", "jdbc:h2:test2", "sa", "", false),
                new Test("org.hsqldb.jdbcDriver", "jdbc:hsqldb:test4", "sa", "", false),
                // new Test("com.mysql.jdbc.Driver",
                // "jdbc:mysql://localhost/test", "sa", ""),
                new Test("org.postgresql.Driver", "jdbc:postgresql:test", "sa", "sa", false),
                new Test("org.apache.derby.jdbc.EmbeddedDriver", "jdbc:derby:test;create=true", "sa", "", false),
                new Test("org.h2.Driver", "jdbc:h2:test5", "sa", "", true),
                new Test("org.h2.Driver", "jdbc:h2:test6", "sa", "", false),};
        for (int i = 0; ; i++) {
            for (Test t : dbs) {
                t.insert(i);
            }
            out.writeInt(i);
            out.flush();
        }
    }

}
