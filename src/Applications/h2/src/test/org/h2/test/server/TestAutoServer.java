/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.server;

import org.h2.test.TestBase;
import org.h2.util.SortedProperties;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Tests automatic embedded/server mode.
 */
public class TestAutoServer extends TestBase {

    /**
     * The number of iterations.
     */
    static final int ITERATIONS = 30;

    /**
     * Run just this test.
     *
     * @param a ignored
     */
    public static void main(String... a) throws Exception {
        TestBase.createCaller().init().test();
    }

    public void test() throws Exception {
        testAutoServer();
        testLinkedLocalTablesWithAutoServerReconnect();
    }

    /**
     * Tests basic AUTO_SERVER functionality
     *
     * @throws Exception
     */
    public void testAutoServer() throws Exception {
        if (config.memory || config.networked) {
            return;
        }
        deleteDb("autoServer");
        String url = getURL("autoServer;AUTO_SERVER=TRUE", true);
        String user = getUser(), password = getPassword();
        Connection connServer = getConnection(url + ";OPEN_NEW=TRUE", user, password);

        int i = ITERATIONS;
        for (; i > 0; i--) {
            Thread.sleep(100);
            SortedProperties prop = SortedProperties.loadProperties(baseDir + "/autoServer.lock.db");
            String key = prop.getProperty("id");
            String server = prop.getProperty("server");
            if (server != null) {
                String u2 = url.substring(url.indexOf(";"));
                u2 = "jdbc:h2:tcp://" + server + "/" + key + u2;
                Connection conn = DriverManager.getConnection(u2, user, password);
                conn.close();
                break;
            }
        }
        if (i <= 0) {
            fail();
        }
        Connection conn = getConnection(url + ";OPEN_NEW=TRUE");
        Statement stat = conn.createStatement();
        if (config.big) {
            try {
                stat.execute("SHUTDOWN");
            } catch (SQLException e) {
                assertKnownException(e);
                // the connection is closed
            }
        }
        conn.close();
        connServer.close();
        deleteDb("autoServer");
    }

    /**
     * Tests recreation of temporary linked tables on reconnect
     */
    private void testLinkedLocalTablesWithAutoServerReconnect() throws SQLException {
        if (config.memory || config.networked) {
            return;
        }
        deleteDb("autoServerLinkedTable1");
        deleteDb("autoServerLinkedTable2");
        String url = getURL("autoServerLinkedTable1;AUTO_SERVER=TRUE", true);
        String urlLinked = getURL("autoServerLinkedTable2", true);
        String user = getUser(), password = getPassword();

        Connection connLinked = getConnection(urlLinked, user, password);
        Statement statLinked = connLinked.createStatement();
        statLinked.execute("CREATE TABLE TEST(ID VARCHAR)");

        // Server is connection 1
        Connection connAutoServer1 = getConnection(url + ";OPEN_NEW=TRUE", user, password);
        Statement statAutoServer1 = connAutoServer1.createStatement();
        statAutoServer1.execute("CREATE LOCAL TEMPORARY LINKED TABLE T('', '" +
                urlLinked + "', '" + user + "', '" + password + "', 'TEST')");

        // Connection 2 connects
        Connection connAutoServer2 = getConnection(url + ";OPEN_NEW=TRUE", user, password);
        Statement statAutoServer2 = connAutoServer2.createStatement();
        statAutoServer2.execute("CREATE LOCAL TEMPORARY LINKED TABLE T('', '" +
                urlLinked + "', '" + user + "', '" + password + "', 'TEST')");

        // Server 1 closes the connection => connection 2 will be the server
        // => the "force create local temporary linked..." must be reissued
        statAutoServer1.execute("shutdown immediately");
        try {
            connAutoServer1.close();
        } catch (SQLException e) {
            // ignore
        }

        // Now test insert
        statAutoServer2.execute("INSERT INTO T (ID) VALUES('abc')");

        connAutoServer2.close();

        connLinked.close();

        deleteDb("autoServerLinkedTable1");
        deleteDb("autoServerLinkedTable2");
    }

    /**
     * This method is called via reflection from the database.
     *
     * @param exitValue the exit value
     */
    public static void halt(int exitValue) {
        Runtime.getRuntime().halt(exitValue);
    }

}
