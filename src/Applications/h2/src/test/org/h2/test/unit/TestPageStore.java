/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.unit;

import org.h2.api.DatabaseEventListener;
import org.h2.test.TestBase;

import java.sql.*;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/**
 * Test the page store.
 */
public class TestPageStore extends TestBase implements DatabaseEventListener {

    static StringBuilder eventBuffer = new StringBuilder();

    /**
     * Run just this test.
     *
     * @param a ignored
     */
    public static void main(String... a) throws Exception {
        System.setProperty("h2.pageStore", "true");
        TestBase.createCaller().init().test();
    }

    public void test() throws Exception {
        if (!config.pageStore) {
            return;
        }
        testCloseTempTable();
        testDuplicateKey();
        testUpdateOverflow();
        testTruncateReconnect();
        testReverseIndex();
        testLargeUpdates();
        testLargeInserts();
        testAutoConvert();
        testLargeDatabaseFastOpen();
        testUniqueIndexReopen();
        testExistingOld();
        testLargeRows();
        testRecoverDropIndex();
        testDropPk();
        testCreatePkLater();
        testTruncate();
        testLargeIndex();
        testUniqueIndex();
        testCreateIndexLater();
        testFuzzOperations();
    }

    private void testCloseTempTable() throws SQLException {
        deleteDb("pageStore");
        Connection conn;
        String url = "pageStore;PAGE_STORE=TRUE;CACHE_SIZE=0";
        conn = getConnection(url);
        Statement stat = conn.createStatement();
        stat.execute("create local temporary table test(id int)");
        conn.rollback();
        Connection conn2 = getConnection(url);
        Statement stat2 = conn2.createStatement();
        stat2.execute("create table test2 as select x from system_range(1, 5000)");
        stat2.execute("shutdown immediately");
        try {
            conn.close();
            fail();
        } catch (SQLException e) {
            assertKnownException(e);
        }
        try {
            conn2.close();
            fail();
        } catch (SQLException e) {
            assertKnownException(e);
        }
    }

    private void testDuplicateKey() throws SQLException {
        deleteDb("pageStore");
        Connection conn;
        conn = getConnection("pageStore;PAGE_STORE=TRUE");
        Statement stat = conn.createStatement();
        stat.execute("create table test(id int primary key, name varchar)");
        stat.execute("insert into test values(0, space(3000))");
        try {
            stat.execute("insert into test values(0, space(3000))");
        } catch (SQLException e) {
            // ignore
        }
        stat.execute("select * from test");
        conn.close();
    }

    private void testTruncateReconnect() throws SQLException {
        if (config.memory) {
            return;
        }
        deleteDb("pageStore");
        Connection conn;
        conn = getConnection("pageStore;PAGE_STORE=TRUE");
        conn.createStatement().execute("create table test(id int primary key, name varchar)");
        conn.createStatement().execute("insert into test(id) select x from system_range(1, 390)");
        conn.createStatement().execute("checkpoint");
        conn.createStatement().execute("shutdown immediately");
        conn = getConnection("pageStore;PAGE_STORE=TRUE");
        conn.createStatement().execute("truncate table test");
        conn.createStatement().execute("insert into test(id) select x from system_range(1, 390)");
        conn.createStatement().execute("shutdown immediately");
        conn = getConnection("pageStore;PAGE_STORE=TRUE");
        conn.close();
    }

    private void testUpdateOverflow() throws SQLException {
        if (config.memory) {
            return;
        }
        deleteDb("pageStore");
        Connection conn;
        conn = getConnection("pageStore;PAGE_STORE=TRUE");
        conn.createStatement().execute("create table test(id int primary key, name varchar)");
        conn.createStatement().execute("insert into test values(0, space(3000))");
        conn.createStatement().execute("checkpoint");
        conn.createStatement().execute("shutdown immediately");

        conn = getConnection("pageStore;PAGE_STORE=TRUE");
        conn.createStatement().execute("update test set id = 1");
        conn.createStatement().execute("shutdown immediately");

        conn = getConnection("pageStore;PAGE_STORE=TRUE");
        conn.close();
    }

    private void testReverseIndex() throws SQLException {
        if (config.memory) {
            return;
        }
        deleteDb("pageStore");
        Connection conn = getConnection("pageStore");
        Statement stat = conn.createStatement();
        stat.execute("create table test(x int, y varchar default space(200))");
        for (int i = 30; i < 100; i++) {
            stat.execute("insert into test(x) select null from system_range(1, " + i + ")");
            stat.execute("insert into test(x) select x from system_range(1, " + i + ")");
            stat.execute("create index idx on test(x desc, y)");
            ResultSet rs = stat.executeQuery("select min(x) from test");
            rs.next();
            assertEquals(1, rs.getInt(1));
            stat.execute("drop index idx");
            stat.execute("truncate table test");
        }
        conn.close();
    }

    private void testLargeUpdates() throws SQLException {
        if (config.memory) {
            return;
        }
        deleteDb("pageStore");
        Connection conn;
        conn = getConnection("pageStore;PAGE_STORE=TRUE");
        Statement stat = conn.createStatement();
        int size = 1500;
        stat.execute("call rand(1)");
        stat.execute("create table test(id int primary key, data varchar, test int) as " +
                "select x, '', 123 from system_range(1, " + size + ")");
        Random random = new Random(1);
        PreparedStatement prep = conn.prepareStatement(
                "update test set data=space(?) where id=?");
        for (int i = 0; i < 2500; i++) {
            int id = random.nextInt(size);
            int newSize = random.nextInt(6000);
            prep.setInt(1, newSize);
            prep.setInt(2, id);
            prep.execute();
        }
        conn.close();
        conn = getConnection("pageStore;PAGE_STORE=TRUE");
        stat = conn.createStatement();
        ResultSet rs = stat.executeQuery("select * from test where test<>123");
        assertFalse(rs.next());
        conn.close();
    }

    private void testLargeInserts() throws SQLException {
        if (config.memory) {
            return;
        }
        deleteDb("pageStore");
        Connection conn;
        conn = getConnection("pageStore;PAGE_STORE=TRUE");
        Statement stat = conn.createStatement();
        stat.execute("create table test(data varchar)");
        stat.execute("insert into test values(space(1024 * 1024))");
        stat.execute("insert into test values(space(1024 * 1024))");
        conn.close();
    }

    private void testAutoConvert() throws SQLException {
        if (config.memory) {
            return;
        }
        deleteDb("pageStore");
        Connection conn;
        conn = getConnection("pageStore;PAGE_STORE=FALSE");
        Statement stat = conn.createStatement();
        stat.execute("create table test(id int, data clob)");
        stat.execute("insert into test select x, space(10000) from system_range(1, 2)");
        stat.execute("checkpoint");
        stat.execute("set write_delay 0");
        stat.execute("insert into test select x, 'empty' from system_range(10, 20)");
        stat.execute("shutdown immediately");
        try {
            conn.close();
        } catch (SQLException e) {
            // ignore
        }

        // a database that was not closed normally can't be converted
        try {
            getConnection("pageStore;PAGE_STORE=TRUE");
            fail();
        } catch (SQLException e) {
            assertKnownException(e);
        }

        // now open and close the database normally
        conn = getConnection("pageStore");
        conn.close();

        // convert it
        conn = getConnection("pageStore;PAGE_STORE=TRUE");
        stat = conn.createStatement();
        ResultSet rs = stat.executeQuery("select * from test order by id");
        while (rs.next()) {
            rs.getString(1);
            rs.getString(2);
        }
        stat.execute("drop table test");
        conn.close();
    }

    private void testLargeDatabaseFastOpen() throws SQLException {
        if (config.memory) {
            return;
        }
        deleteDb("pageStore");
        Connection conn;
        String url = "pageStore";
        conn = getConnection(url);
        conn.createStatement().execute("CREATE TABLE TEST(ID INT PRIMARY KEY, NAME VARCHAR)");
        conn.createStatement().execute("create unique index idx_test_name on test(name)");
        conn.createStatement().execute("INSERT INTO TEST SELECT X, X || space(10) FROM SYSTEM_RANGE(1, 1000)");
        conn.close();
        conn = getConnection(url);
        conn.createStatement().execute("DELETE FROM TEST WHERE ID=1");
        conn.createStatement().execute("CHECKPOINT");
        conn.createStatement().execute("SHUTDOWN IMMEDIATELY");
        try {
            conn.close();
        } catch (SQLException e) {
            // ignore
        }
        eventBuffer.setLength(0);
        conn = getConnection(url + ";DATABASE_EVENT_LISTENER='" + getClass().getName() + "'");
        assertEquals("init;opened;", eventBuffer.toString());
        conn.close();
    }

    private void testUniqueIndexReopen() throws SQLException {
        if (config.memory) {
            return;
        }
        deleteDb("pageStore");
        Connection conn;
        String url = "pageStore";
        conn = getConnection(url);
        conn.createStatement().execute("CREATE TABLE test(ID INT PRIMARY KEY, NAME VARCHAR(255))");
        conn.createStatement().execute("create unique index idx_test_name on test(name)");
        conn.createStatement().execute("INSERT INTO TEST VALUES(1, 'Hello')");
        conn.close();
        conn = getConnection(url);
        try {
            conn.createStatement().execute("INSERT INTO TEST VALUES(2, 'Hello')");
            fail();
        } catch (SQLException e) {
            assertKnownException(e);
        }
        conn.close();
    }

    private void testExistingOld() throws SQLException {
        if (config.memory) {
            return;
        }
        Connection conn;
        deleteDb("pageStore");
        String url;
        url = "jdbc:h2:" + baseDir + "/pageStore";
        conn = DriverManager.getConnection(url + ";PAGE_STORE=FALSE");
        conn.createStatement().execute("create table test(id int) as select 1");
        conn.close();
        conn = DriverManager.getConnection(url);
        conn.createStatement().execute("select * from test");
        conn.close();
        // the database is automatically converted
        conn = DriverManager.getConnection(url + ";PAGE_STORE=TRUE");
        assertResult("1", conn.createStatement(), "select * from test");
        conn.close();
    }

    private void testLargeRows() throws Exception {
        if (config.memory) {
            return;
        }
        for (int i = 0; i < 10; i++) {
            testLargeRows(i);
        }
    }

    private void testLargeRows(int seed) throws Exception {
        deleteDb("pageStore");
        String url = getURL("pageStore;CACHE_SIZE=16", true);
        Connection conn = null;
        Statement stat = null;
        int count = 0;
        try {
            Class.forName("org.h2.Driver");
            conn = DriverManager.getConnection(url);
            stat = conn.createStatement();
            int tableCount = 1;
            PreparedStatement[] insert = new PreparedStatement[tableCount];
            PreparedStatement[] deleteMany = new PreparedStatement[tableCount];
            PreparedStatement[] updateMany = new PreparedStatement[tableCount];
            for (int i = 0; i < tableCount; i++) {
                stat.execute("create table test" + i + "(id int primary key, name varchar)");
                stat.execute("create index idx_test" + i + " on test" + i + "(name)");
                insert[i] = conn.prepareStatement("insert into test" + i + " values(?, ? || space(?))");
                deleteMany[i] = conn.prepareStatement("delete from test" + i + " where id between ? and ?");
                updateMany[i] = conn.prepareStatement("update test" + i
                        + " set name=? || space(?) where id between ? and ?");
            }
            Random random = new Random(seed);
            for (int i = 0; i < 1000; i++) {
                count = i;
                PreparedStatement p;
                if (random.nextInt(100) < 95) {
                    p = insert[random.nextInt(tableCount)];
                    p.setInt(1, i);
                    p.setInt(2, i);
                    if (random.nextInt(30) == 5) {
                        p.setInt(3, 3000);
                    } else {
                        p.setInt(3, random.nextInt(100));
                    }
                    p.execute();
                } else if (random.nextInt(100) < 90) {
                    p = updateMany[random.nextInt(tableCount)];
                    p.setInt(1, i);
                    p.setInt(2, random.nextInt(50));
                    int first = random.nextInt(1 + i);
                    p.setInt(3, first);
                    p.setInt(4, first + random.nextInt(50));
                    p.executeUpdate();
                } else {
                    p = deleteMany[random.nextInt(tableCount)];
                    int first = random.nextInt(1 + i);
                    p.setInt(1, first);
                    p.setInt(2, first + random.nextInt(100));
                    p.executeUpdate();
                }
            }
            conn.close();
            conn = DriverManager.getConnection(url);
            conn.close();
            conn = DriverManager.getConnection(url);
            stat = conn.createStatement();
            stat.execute("script to '" + baseDir + "/pageStore.sql'");
            conn.close();
        } catch (Exception e) {
            try {
                stat.execute("shutdown immediately");
            } catch (SQLException e2) {
                // ignore
            }
            try {
                conn.close();
            } catch (SQLException e2) {
                // ignore
            }
            fail("count: " + count + " " + e);
        }
    }

    private void testRecoverDropIndex() throws SQLException {
        if (config.memory) {
            return;
        }
        deleteDb("pageStore");
        Connection conn = getConnection("pageStore");
        Statement stat = conn.createStatement();
        stat.execute("set write_delay 0");
        stat.execute("create table test(id int, name varchar) as select x, x from system_range(1, 1400)");
        stat.execute("create index idx_name on test(name)");
        conn.close();
        conn = getConnection("pageStore");
        stat = conn.createStatement();
        stat.execute("drop index idx_name");
        stat.execute("shutdown immediately");
        try {
            conn.close();
        } catch (SQLException e) {
            // ignore
        }
        conn = getConnection("pageStore;cache_size=1");
        conn.close();
    }

    private void testDropPk() throws SQLException {
        if (config.memory) {
            return;
        }
        deleteDb("pageStore");
        Connection conn;
        Statement stat;
        conn = getConnection("pageStore");
        stat = conn.createStatement();
        stat.execute("create table test(id int primary key)");
        stat.execute("insert into test values(" + Integer.MIN_VALUE + "), (" + Integer.MAX_VALUE + ")");
        stat.execute("alter table test drop primary key");
        conn.close();
        conn = getConnection("pageStore");
        stat = conn.createStatement();
        stat.execute("insert into test values(" + Integer.MIN_VALUE + "), (" + Integer.MAX_VALUE + ")");
        conn.close();
    }

    private void testCreatePkLater() throws SQLException {
        if (config.memory) {
            return;
        }
        deleteDb("pageStore");
        Connection conn;
        Statement stat;
        conn = getConnection("pageStore");
        stat = conn.createStatement();
        stat.execute("create table test(id int not null) as select 100");
        stat.execute("create primary key on test(id)");
        conn.close();
        conn = getConnection("pageStore");
        stat = conn.createStatement();
        ResultSet rs = stat.executeQuery("select * from test where id = 100");
        assertTrue(rs.next());
        conn.close();
    }

    private void testTruncate() throws SQLException {
        if (config.memory) {
            return;
        }
        deleteDb("pageStore");
        Connection conn = getConnection("pageStore");
        Statement stat = conn.createStatement();
        stat.execute("set write_delay 0");
        stat.execute("create table test(id int) as select 1");
        stat.execute("truncate table test");
        stat.execute("insert into test values(1)");
        stat.execute("shutdown immediately");
        try {
            conn.close();
        } catch (SQLException e) {
            // ignore
        }
        conn = getConnection("pageStore");
        conn.close();
    }

    private void testLargeIndex() throws SQLException {
        if (config.memory) {
            return;
        }
        deleteDb("pageStore");
        Connection conn = getConnection("pageStore");
        conn.createStatement().execute("create table test(id varchar primary key, d varchar)");
        PreparedStatement prep = conn.prepareStatement("insert into test values(?, space(500))");
        for (int i = 0; i < 20000; i++) {
            prep.setString(1, "" + i);
            prep.executeUpdate();
        }
        conn.close();
    }

    private void testUniqueIndex() throws SQLException {
        if (config.memory) {
            return;
        }
        deleteDb("pageStore");
        Connection conn = getConnection("pageStore");
        Statement stat = conn.createStatement();
        stat.execute("CREATE TABLE TEST(ID INT UNIQUE)");
        stat.execute("INSERT INTO TEST VALUES(1)");
        conn.close();
        conn = getConnection("pageStore");
        try {
            conn.createStatement().execute("INSERT INTO TEST VALUES(1)");
            fail();
        } catch (SQLException e) {
            assertKnownException(e);
        }
        conn.close();
    }

    private void testCreateIndexLater() throws SQLException {
        deleteDb("pageStore");
        Connection conn = getConnection("pageStore");
        Statement stat = conn.createStatement();
        stat.execute("CREATE TABLE TEST(NAME VARCHAR) AS SELECT 1");
        stat.execute("CREATE INDEX IDX_N ON TEST(NAME)");
        stat.execute("INSERT INTO TEST SELECT X FROM SYSTEM_RANGE(20, 100)");
        stat.execute("INSERT INTO TEST SELECT X FROM SYSTEM_RANGE(1000, 1100)");
        stat.execute("SHUTDOWN IMMEDIATELY");
        try {
            conn.close();
        } catch (SQLException e) {
            // ignore
        }
        conn = getConnection("pageStore");
        conn.close();
    }

    private void testFuzzOperations() throws SQLException {
        int best = Integer.MAX_VALUE;
        for (int i = 0; i < 10; i++) {
            int x = testFuzzOperationsSeed(i, 10);
            if (x >= 0 && x < best) {
                best = x;
                fail("op:" + x + " seed:" + i);
            }
        }
    }

    private int testFuzzOperationsSeed(int seed, int len) throws SQLException {
        deleteDb("pageStore");
        Connection conn = getConnection("pageStore");
        Statement stat = conn.createStatement();
        log("DROP TABLE IF EXISTS TEST;");
        stat.execute("DROP TABLE IF EXISTS TEST");
        log("CREATE TABLE TEST(ID INT PRIMARY KEY, NAME VARCHAR DEFAULT 'Hello World');");
        stat.execute("CREATE TABLE TEST(ID INT PRIMARY KEY, NAME VARCHAR DEFAULT 'Hello World')");
        Set<Integer> rows = new TreeSet<Integer>();
        Random random = new Random(seed);
        for (int i = 0; i < len; i++) {
            int op = random.nextInt(3);
            Integer x = new Integer(random.nextInt(100));
            switch (op) {
                case 0:
                    if (!rows.contains(x)) {
                        log("insert into test(id) values(" + x + ");");
                        stat.execute("INSERT INTO TEST(ID) VALUES(" + x + ");");
                        rows.add(x);
                    }
                    break;
                case 1:
                    if (rows.contains(x)) {
                        log("delete from test where id=" + x + ";");
                        stat.execute("DELETE FROM TEST WHERE ID=" + x);
                        rows.remove(x);
                    }
                    break;
                case 2:
                    conn.close();
                    conn = getConnection("pageStore");
                    stat = conn.createStatement();
                    ResultSet rs = stat.executeQuery("SELECT * FROM TEST ORDER BY ID");
                    log("--reconnect");
                    for (int test : rows) {
                        if (!rs.next()) {
                            log("error: expected next");
                            conn.close();
                            return i;
                        }
                        int y = rs.getInt(1);
                        // System.out.println(" " + x);
                        if (y != test) {
                            log("error: " + y + " <> " + test);
                            conn.close();
                            return i;
                        }
                    }
                    if (rs.next()) {
                        log("error: unexpected next");
                        conn.close();
                        return i;
                    }
            }
        }
        conn.close();
        return -1;
    }

    private void log(String m) {
        trace("   " + m);
    }

    public void closingDatabase() {
        event("closing");
    }

    public void diskSpaceIsLow(long stillAvailable) {
        event("diskSpaceIsLow " + stillAvailable);
    }

    public void exceptionThrown(SQLException e, String sql) {
        event("exceptionThrown " + e + " " + sql);
    }

    public void init(String url) {
        event("init");
    }

    public void opened() {
        event("opened");
    }

    public void setProgress(int state, String name, int x, int max) {
        if (name.startsWith("SYS:SYS_ID")) {
            // ignore
            return;
        }
        event("setProgress " + state + " " + name + " " + x + " " + max);
    }

    private void event(String s) {
        eventBuffer.append(s).append(';');
    }

}
