/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.unit;

import org.h2.constant.ErrorCode;
import org.h2.engine.Constants;
import org.h2.store.FileLister;
import org.h2.test.TestBase;
import org.h2.test.trace.Player;
import org.h2.tools.*;
import org.h2.util.FileUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import java.util.ArrayList;
import java.util.Random;

/**
 * Tests the database tools.
 */
public class TestTools extends TestBase {

    private Server server;

    /**
     * Run just this test.
     *
     * @param a ignored
     */
    public static void main(String... a) throws Exception {
        TestBase.createCaller().init().test();
    }

    public void test() throws Exception {
        if (config.networked) {
            return;
        }
        org.h2.Driver.load();
        testWrongServer();
        deleteDb("utils");
        testDeleteFiles();
        testScriptRunscriptLob();
        deleteDb("utils");
        testServerMain();
        testRemove();
        testConvertTraceFile();
        testManagementDb();
        testChangeFileEncryption();
        testServer();
        testScriptRunscript();
        testBackupRestore();
        testRecover();
        deleteDb("utils");
    }

    private void testWrongServer() throws Exception {
        final ServerSocket serverSocket = new ServerSocket(9001);
        Thread thread = new Thread() {
            public void run() {
                try {
                    Socket socket = serverSocket.accept();
                    byte[] data = new byte[1024];
                    data[0] = 'x';
                    socket.getOutputStream().write(data);
                    socket.close();
                } catch (Exception e) {
                    // ignore
                }
            }
        };
        thread.start();
        try {
            Connection conn = getConnection("jdbc:h2:tcp://localhost:9001/test");
            conn.close();
            fail();
        } catch (SQLException e) {
            assertEquals(ErrorCode.CONNECTION_BROKEN_1, e.getErrorCode());
        }
        serverSocket.close();
        thread.join();
    }

    private void testDeleteFiles() throws SQLException {
        deleteDb("utilsMore");
        Connection conn = getConnection("utilsMore");
        Statement stat = conn.createStatement();
        stat.execute("create table test(c clob) as select space(10000) from dual");
        conn.close();
        DeleteDbFiles.execute(baseDir, "utils", true);
        conn = getConnection("utilsMore");
        stat = conn.createStatement();
        ResultSet rs;
        rs = stat.executeQuery("select * from test");
        rs.next();
        rs.getString(1);
        conn.close();
    }

    private void testServerMain() throws SQLException {
        String result;
        Connection conn;

        result = runServer(0, new String[]{"-?"});
        assertTrue(result.indexOf("Starts the H2 Console") >= 0);
        assertTrue(result.indexOf("Unknown option") < 0);

        result = runServer(1, new String[]{"-xy"});
        assertTrue(result.indexOf("Starts the H2 Console") >= 0);
        assertTrue(result.indexOf("Unsupported option") >= 0);
        result = runServer(0, new String[]{"-tcp", "-tcpPort", "9001", "-tcpPassword", "abc"});
        assertTrue(result.indexOf("tcp://") >= 0);
        assertTrue(result.indexOf(":9001") >= 0);
        assertTrue(result.indexOf("only local") >= 0);
        assertTrue(result.indexOf("Starts the H2 Console") < 0);
        conn = DriverManager.getConnection("jdbc:h2:tcp://localhost:9001/mem:", "sa", "sa");
        conn.close();
        result = runServer(0, new String[]{"-tcpShutdown", "tcp://localhost:9001", "-tcpPassword", "abc", "-tcpShutdownForce"});
        assertTrue(result.indexOf("Shutting down") >= 0);

        result = runServer(0, new String[]{"-tcp", "-tcpAllowOthers", "-tcpPort", "9001", "-tcpPassword", "abcdef", "-tcpSSL"});
        assertTrue(result.indexOf("ssl://") >= 0);
        assertTrue(result.indexOf(":9001") >= 0);
        assertTrue(result.indexOf("others can") >= 0);
        assertTrue(result.indexOf("Starts the H2 Console") < 0);
        conn = DriverManager.getConnection("jdbc:h2:ssl://localhost:9001/mem:", "sa", "sa");
        conn.close();

        result = runServer(0, new String[]{"-tcpShutdown", "ssl://localhost:9001", "-tcpPassword", "abcdef"});
        assertTrue(result.indexOf("Shutting down") >= 0);
        try {
            DriverManager.getConnection("jdbc:h2:ssl://localhost:9001/mem:", "sa", "sa");
            fail();
        } catch (SQLException e) {
            assertKnownException(e);
        }

        result = runServer(0, new String[]{
                "-web", "-webPort", "9002", "-webAllowOthers", "-webSSL",
                "-pg", "-pgAllowOthers", "-pgPort", "9003",
                "-tcp", "-tcpAllowOthers", "-tcpPort", "9006", "-tcpPassword", "abc"});
        Server stop = server;
        assertTrue(result.indexOf("https://") >= 0);
        assertTrue(result.indexOf(":9002") >= 0);
        assertTrue(result.indexOf("pg://") >= 0);
        assertTrue(result.indexOf(":9003") >= 0);
        assertTrue(result.indexOf("others can") >= 0);
        assertTrue(result.indexOf("only local") < 0);
        assertTrue(result.indexOf("tcp://") >= 0);
        assertTrue(result.indexOf(":9006") >= 0);

        conn = DriverManager.getConnection("jdbc:h2:tcp://localhost:9006/mem:", "sa", "sa");
        conn.close();

        result = runServer(0, new String[]{"-tcpShutdown", "tcp://localhost:9006", "-tcpPassword", "abc", "-tcpShutdownForce"});
        assertTrue(result.indexOf("Shutting down") >= 0);
        stop.shutdown();
        try {
            DriverManager.getConnection("jdbc:h2:tcp://localhost:9006/mem:", "sa", "sa");
            fail();
        } catch (SQLException e) {
            assertKnownException(e);
        }
    }

    private String runServer(int exitCode, String... args) {
        ByteArrayOutputStream buff = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(buff);
        server = new Server();
        server.setOut(ps);
        int result = 0;
        try {
            server.run(args);
        } catch (SQLException e) {
            result = 1;
            e.printStackTrace(ps);
        }
        assertEquals(exitCode, result);
        ps.flush();
        String s = new String(buff.toByteArray());
        return s;
    }

    private void testConvertTraceFile() throws Exception {
        deleteDb("toolsConvertTraceFile");
        org.h2.Driver.load();
        String url = "jdbc:h2:" + baseDir + "/toolsConvertTraceFile";
        Connection conn = DriverManager.getConnection(url + ";TRACE_LEVEL_FILE=3", "sa", "sa");
        Statement stat = conn.createStatement();
        stat.execute("create table test(id int primary key, name varchar, amount decimal)");
        PreparedStatement prep = conn.prepareStatement("insert into test values(?, ?, ?)");
        prep.setInt(1, 1);
        prep.setString(2, "Hello \\'Joe\n\\'");
        prep.setBigDecimal(3, new BigDecimal("10.20"));
        prep.executeUpdate();
        stat.execute("create table test2(id int primary key,\n" +
                "a real, b double, c bigint,\n" +
                "d smallint, e boolean, f binary, g date, h time, i timestamp)", Statement.NO_GENERATED_KEYS);
        prep = conn.prepareStatement("insert into test2 values(1, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        prep.setFloat(1, Float.MIN_VALUE);
        prep.setDouble(2, Double.MIN_VALUE);
        prep.setLong(3, Long.MIN_VALUE);
        prep.setShort(4, Short.MIN_VALUE);
        prep.setBoolean(5, false);
        prep.setBytes(6, new byte[]{(byte) 10, (byte) 20});
        prep.setDate(7, java.sql.Date.valueOf("2007-12-31"));
        prep.setTime(8, java.sql.Time.valueOf("23:59:59"));
        prep.setTimestamp(9, java.sql.Timestamp.valueOf("2007-12-31 23:59:59"));
        prep.executeUpdate();
        conn.close();

        ConvertTraceFile.main("-traceFile", baseDir + "/toolsConvertTraceFile.trace.db", "-javaClass", baseDir + "/Test", "-script", baseDir + "/test.sql");
        new File(baseDir + "/Test.java").delete();

        File trace = new File(baseDir + "/toolsConvertTraceFile.trace.db");
        assertTrue(trace.exists());
        File newTrace = new File(baseDir + "/test.trace.db");
        newTrace.delete();
        assertTrue(trace.renameTo(newTrace));
        deleteDb("toolsConvertTraceFile");
        Player.main(baseDir + "/test.trace.db");
        testTraceFile(url);

        deleteDb("toolsConvertTraceFile");
        RunScript.main("-url", url, "-user", "sa", "-script", baseDir + "/test.sql");
        testTraceFile(url);
    }

    private void testTraceFile(String url) throws SQLException {
        Connection conn;
        Recover.main("-removePassword", "-dir", baseDir, "-db", "toolsConvertTraceFile");
        conn = DriverManager.getConnection(url, "sa", "");
        Statement stat = conn.createStatement();
        ResultSet rs;
        rs = stat.executeQuery("select * from test");
        rs.next();
        assertEquals(1, rs.getInt(1));
        assertEquals("Hello \\'Joe\n\\'", rs.getString(2));
        assertEquals("10.20", rs.getBigDecimal(3).toString());
        assertFalse(rs.next());
        rs = stat.executeQuery("select * from test2");
        rs.next();
        assertEquals(Float.MIN_VALUE, rs.getFloat("a"));
        assertEquals(Double.MIN_VALUE, rs.getDouble("b"));
        assertEquals(Long.MIN_VALUE, rs.getLong("c"));
        assertEquals(Short.MIN_VALUE, rs.getShort("d"));
        assertTrue(!rs.getBoolean("e"));
        assertEquals(new byte[]{(byte) 10, (byte) 20}, rs.getBytes("f"));
        assertEquals("2007-12-31", rs.getString("g"));
        assertEquals("23:59:59", rs.getString("h"));
        assertEquals("2007-12-31 23:59:59.0", rs.getString("i"));
        assertFalse(rs.next());
        conn.close();
    }

    private void testRemove() throws SQLException {
        deleteDb("toolsRemove");
        org.h2.Driver.load();
        String url = "jdbc:h2:" + baseDir + "/toolsRemove";
        Connection conn = DriverManager.getConnection(url, "sa", "sa");
        Statement stat = conn.createStatement();
        stat.execute("create table test(id int primary key, name varchar)");
        stat.execute("insert into test values(1, 'Hello')");
        conn.close();
        ArrayList<String> list = FileLister.getDatabaseFiles(baseDir, "toolsRemove", true);
        for (String fileName : list) {
            if (fileName.endsWith(Constants.SUFFIX_LOG_FILE)) {
                FileUtils.delete(fileName);
            }
        }
        Recover.main("-dir", baseDir, "-db", "toolsRemove", "-removePassword");
        conn = DriverManager.getConnection(url, "sa", "");
        stat = conn.createStatement();
        ResultSet rs;
        rs = stat.executeQuery("select * from test");
        rs.next();
        assertEquals(1, rs.getInt(1));
        assertEquals("Hello", rs.getString(2));
        conn.close();
    }

    private void testRecover() throws SQLException {
        deleteDb("toolsRecover");
        org.h2.Driver.load();
        String url = getURL("toolsRecover", true);
        Connection conn = DriverManager.getConnection(url, "sa", "sa");
        Statement stat = conn.createStatement();
        stat.execute("create table test(id int primary key, name varchar, b blob, c clob)");
        stat.execute("create table \"test 2\"(id int primary key, name varchar)");
        stat.execute("comment on table test is ';-)'");
        stat.execute("insert into test values(1, 'Hello', SECURE_RAND(2000), space(2000))");
        ResultSet rs;
        rs = stat.executeQuery("select * from test");
        rs.next();
        byte[] b1 = rs.getBytes(3);
        String s1 = rs.getString(4);

        conn.close();
        Recover.main("-dir", baseDir, "-db", "toolsRecover");

        // deleteDb would delete the .lob.db directory as well
        // deleteDb("toolsRecover");
        ArrayList<String> list = FileLister.getDatabaseFiles(baseDir, "toolsRecover", true);
        for (String fileName : list) {
            if (!FileUtils.isDirectory(fileName)) {
                FileUtils.delete(fileName);
            }
        }

        conn = DriverManager.getConnection(url, "another", "another");
        stat = conn.createStatement();
        String suffix = ".data.sql";
        if (new File(baseDir + "/toolsRecover.h2.sql").exists()) {
            suffix = ".h2.sql";
        }
        stat.execute("runscript from '" + baseDir + "/toolsRecover" + suffix + "'");
        rs = stat.executeQuery("select * from \"test 2\"");
        assertFalse(rs.next());
        rs = stat.executeQuery("select * from test");
        rs.next();
        assertEquals(1, rs.getInt(1));
        assertEquals("Hello", rs.getString(2));
        byte[] b2 = rs.getBytes(3);
        String s2 = rs.getString(4);
        assertEquals(2000, b2.length);
        assertEquals(2000, s2.length());
        assertEquals(b1, b2);
        assertEquals(s1, s2);
        assertFalse(rs.next());
        conn.close();
    }

    private void testManagementDb() throws SQLException {
        int count = getSize(2, 10);
        for (int i = 0; i < count; i++) {
            Server tcpServer = Server.createTcpServer("-tcpPort", "9192").start();
            tcpServer.stop();
            tcpServer = Server.createTcpServer("-tcpPassword", "abc", "-tcpPort", "9192").start();
            tcpServer.stop();
        }
    }

    private void testScriptRunscriptLob() throws Exception {
        org.h2.Driver.load();
        String url = "jdbc:h2:" + baseDir + "/utils";
        String user = "sa", password = "abc";
        String fileName = baseDir + "/b2.sql";
        Connection conn = DriverManager.getConnection(url, user, password);
        conn.createStatement().execute("CREATE TABLE TEST(ID INT PRIMARY KEY, BDATA BLOB, CDATA CLOB)");
        PreparedStatement prep = conn.prepareStatement("INSERT INTO TEST VALUES(?, ?, ?)");

        prep.setInt(1, 1);
        prep.setNull(2, Types.BLOB);
        prep.setNull(3, Types.CLOB);
        prep.execute();

        prep.setInt(1, 2);
        prep.setString(2, "face");
        prep.setString(3, "face");
        prep.execute();

        Random random = new Random(1);
        prep.setInt(1, 3);
        byte[] large = new byte[getSize(10 * 1024, 100 * 1024)];
        random.nextBytes(large);
        prep.setBytes(2, large);
        String largeText = new String(large, "ISO-8859-1");
        prep.setString(3, largeText);
        prep.execute();

        for (int i = 0; i < 2; i++) {
            ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM TEST ORDER BY ID");
            rs.next();
            assertEquals(1, rs.getInt(1));
            assertTrue(rs.getString(2) == null);
            assertTrue(rs.getString(3) == null);
            rs.next();
            assertEquals(2, rs.getInt(1));
            assertEquals("face", rs.getString(2));
            assertEquals("face", rs.getString(3));
            rs.next();
            assertEquals(3, rs.getInt(1));
            assertEquals(large, rs.getBytes(2));
            assertEquals(largeText, rs.getString(3));
            assertFalse(rs.next());

            conn.close();
            Script.main("-url", url, "-user", user, "-password", password, "-script", fileName);
            DeleteDbFiles.main("-dir", baseDir, "-db", "utils", "-quiet");
            RunScript.main("-url", url, "-user", user, "-password", password, "-script", fileName);
            conn = DriverManager.getConnection("jdbc:h2:" + baseDir + "/utils", "sa", "abc");
        }
        conn.close();

    }

    private void testScriptRunscript() throws SQLException {
        org.h2.Driver.load();
        String url = "jdbc:h2:" + baseDir + "/utils";
        String user = "sa", password = "abc";
        String fileName = baseDir + "/b2.sql";
        Connection conn = DriverManager.getConnection(url, user, password);
        conn.createStatement().execute("CREATE TABLE TEST(ID INT PRIMARY KEY, NAME VARCHAR)");
        conn.createStatement().execute("INSERT INTO TEST VALUES(1, 'Hello')");
        conn.close();
        Script.main("-url", url, "-user", user, "-password", password, "-script", fileName, "-options",
                "nodata", "compression", "lzf", "cipher", "xtea", "password", "'123'");
        DeleteDbFiles.main("-dir", baseDir, "-db", "utils", "-quiet");
        RunScript.main("-url", url, "-user", user, "-password", password, "-script", fileName,
                "-options", "compression", "lzf", "cipher", "xtea", "password", "'123'");
        conn = DriverManager.getConnection("jdbc:h2:" + baseDir + "/utils", "sa", "abc");
        ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM TEST");
        assertFalse(rs.next());
        conn.close();
    }

    private void testBackupRestore() throws SQLException {
        org.h2.Driver.load();
        String url = "jdbc:h2:" + baseDir + "/utils";
        String user = "sa", password = "abc";
        String fileName = baseDir + "/b2.zip";
        DeleteDbFiles.main("-dir", baseDir, "-db", "utils", "-quiet");
        Connection conn = DriverManager.getConnection(url, user, password);
        conn.createStatement().execute("CREATE TABLE TEST(ID INT PRIMARY KEY, NAME VARCHAR)");
        conn.createStatement().execute("INSERT INTO TEST VALUES(1, 'Hello')");
        conn.close();
        Backup.main("-file", fileName, "-dir", baseDir, "-db", "utils", "-quiet");
        DeleteDbFiles.main("-dir", baseDir, "-db", "utils", "-quiet");
        Restore.main("-file", fileName, "-dir", baseDir, "-db", "utils", "-quiet");
        conn = DriverManager.getConnection("jdbc:h2:" + baseDir + "/utils", "sa", "abc");
        ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM TEST");
        assertTrue(rs.next());
        assertFalse(rs.next());
        try {
            Backup.main("-file", fileName, "-dir", baseDir, "-db", "utils");
            fail();
        } catch (SQLException e) {
            assertKnownException(e);
        }
        conn.close();
        DeleteDbFiles.main("-dir", baseDir, "-db", "utils", "-quiet");
    }

    private void testChangeFileEncryption() throws SQLException {
        org.h2.Driver.load();
        DeleteDbFiles.execute(baseDir, "utils", true);
        Connection conn = DriverManager.getConnection("jdbc:h2:" +
                baseDir + "/utils;CIPHER=XTEA", "sa", "abc 123");
        Statement stat = conn.createStatement();
        stat.execute("CREATE TABLE TEST(ID INT PRIMARY KEY, DATA CLOB) " +
                "AS SELECT X, SPACE(3000) FROM SYSTEM_RANGE(1, 300)");
        conn.close();
        String[] args = new String[]{"-dir", baseDir, "-db", "utils", "-cipher", "XTEA", "-decrypt", "abc", "-quiet"};
        ChangeFileEncryption.main(args);
        args = new String[]{"-dir", baseDir, "-db", "utils", "-cipher", "AES", "-encrypt", "def", "-quiet"};
        ChangeFileEncryption.main(args);
        conn = DriverManager.getConnection("jdbc:h2:" +
                baseDir + "/utils;CIPHER=AES", "sa", "def 123");
        stat = conn.createStatement();
        stat.execute("SELECT * FROM TEST");
        try {
            args = new String[]{"-dir", baseDir, "-db", "utils", "-cipher", "AES", "-decrypt", "def", "-quiet"};
            ChangeFileEncryption.main(args);
            fail();
        } catch (SQLException e) {
            assertKnownException(e);
        }
        conn.close();
        args = new String[]{"-dir", baseDir, "-db", "utils", "-quiet"};
        DeleteDbFiles.main(args);
    }

    private void testServer() throws SQLException {
        Connection conn;
        deleteDb("test");
        Server tcpServer = Server.createTcpServer(
                "-baseDir", baseDir,
                "-tcpPort", "9192",
                "-tcpAllowOthers").start();
        conn = DriverManager.getConnection("jdbc:h2:tcp://localhost:9192/test", "sa", "");
        conn.close();
        tcpServer.stop();
        Server.createTcpServer(
                "-ifExists",
                "-tcpPassword", "abc",
                "-baseDir", baseDir,
                "-tcpPort", "9192").start();
        try {
            conn = DriverManager.getConnection("jdbc:h2:tcp://localhost:9192/test2", "sa", "");
            fail("should not be able to create new db");
        } catch (SQLException e) {
            assertKnownException(e);
        }
        conn = DriverManager.getConnection("jdbc:h2:tcp://localhost:9192/test", "sa", "");
        conn.close();
        try {
            Server.shutdownTcpServer("tcp://localhost:9192", "", true);
            fail("shouldn't work and should throw an exception");
        } catch (SQLException e) {
            assertKnownException(e);
        }
        conn = DriverManager.getConnection("jdbc:h2:tcp://localhost:9192/test", "sa", "");
        // conn.close();
        Server.shutdownTcpServer("tcp://localhost:9192", "abc", true);
        // check that the database is closed
        deleteDb("test");
        try {
            DriverManager.getConnection("jdbc:h2:tcp://localhost:9192/test", "sa", "");
            fail("server must have been closed");
        } catch (SQLException e) {
            assertKnownException(e);
        }
        try {
            conn.close();
        } catch (SQLException e) {
            // ignore
        }
    }

}
