/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.db;

import org.h2.test.TestAll;
import org.h2.test.TestBase;

import java.sql.*;
import java.util.Random;

/**
 * Multi-threaded tests.
 */
public class TestMultiThread extends TestBase implements Runnable {

    private boolean stop;
    private TestMultiThread parent;
    private Random random;
    private Connection conn;
    private Statement stat;

    public TestMultiThread() {
        // nothing to do
    }

    private TestMultiThread(TestAll config, TestMultiThread parent) throws SQLException {
        this.config = config;
        this.parent = parent;
        random = new Random();
        conn = getConnection();
        stat = conn.createStatement();
    }

    /**
     * Run just this test.
     *
     * @param a ignored
     */
    public static void main(String... a) throws Exception {
        TestBase.createCaller().init().test();
    }

    public void test() throws Exception {
        conn = getConnection();
        stat = conn.createStatement();
        stat.execute("CREATE TABLE TEST(ID IDENTITY, NAME VARCHAR)");
        int len = getSize(10, 200);
        Thread[] threads = new Thread[len];
        for (int i = 0; i < len; i++) {
            threads[i] = new Thread(new TestMultiThread(config, this));
        }
        for (int i = 0; i < len; i++) {
            threads[i].start();
        }
        int sleep = getSize(400, 10000);
        Thread.sleep(sleep);
        this.stop = true;
        for (int i = 0; i < len; i++) {
            threads[i].join();
        }
        ResultSet rs = stat.executeQuery("SELECT COUNT(*) FROM TEST");
        rs.next();
        trace("max id=" + rs.getInt(1));
        conn.close();
    }

    private Connection getConnection() throws SQLException {
        return getConnection("jdbc:h2:mem:multiThread");
    }

    public void run() {
        try {
            while (!parent.stop) {
                stat.execute("SELECT COUNT(*) FROM TEST");
                stat.execute("INSERT INTO TEST VALUES(NULL, 'Hi')");
                PreparedStatement prep = conn.prepareStatement("UPDATE TEST SET NAME='Hello' WHERE ID=?");
                prep.setInt(1, random.nextInt(10000));
                prep.execute();
                prep = conn.prepareStatement("SELECT * FROM TEST WHERE ID=?");
                prep.setInt(1, random.nextInt(10000));
                ResultSet rs = prep.executeQuery();
                while (rs.next()) {
                    rs.getString("NAME");
                }
            }
            conn.close();
        } catch (Exception e) {
            logError("multi", e);
        }
    }

}
