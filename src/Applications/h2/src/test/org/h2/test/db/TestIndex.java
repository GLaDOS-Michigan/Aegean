/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.db;

import org.h2.result.SortOrder;
import org.h2.test.TestBase;

import java.sql.*;
import java.util.Random;

/**
 * Index tests.
 */
public class TestIndex extends TestBase {

    private Connection conn;
    private Statement stat;
    private Random random = new Random();

    /**
     * Run just this test.
     *
     * @param a ignored
     */
    public static void main(String... a) throws Exception {
        TestBase.createCaller().init().test();
    }

    public void test() throws SQLException {
        deleteDb("index");
        testRenamePrimaryKey();
        testRandomized();
        testDescIndex();
        testHashIndex();

        if (config.networked && config.big) {
            return;
        }

        random.setSeed(100);

        deleteDb("index");
        testWideIndex(147);
        testWideIndex(313);
        testWideIndex(979);
        testWideIndex(1200);
        testWideIndex(2400);
        if (config.big && config.logMode == 2) {
            for (int i = 0; i < 2000; i++) {
                if ((i % 100) == 0) {
                    System.out.println("width: " + i);
                }
                testWideIndex(i);
            }
        }

        testLike();
        reconnect();
        testConstraint();
        testLargeIndex();
        testMultiColumnIndex();
        // long time;
        // time = System.currentTimeMillis();
        testHashIndex(true, false);

        testHashIndex(false, false);
        // System.out.println("b-tree="+(System.currentTimeMillis()-time));
        // time = System.currentTimeMillis();
        testHashIndex(true, true);
        testHashIndex(false, true);
        // System.out.println("hash="+(System.currentTimeMillis()-time));

        testMultiColumnHashIndex();

        conn.close();
        deleteDb("index");
    }

    private void testRenamePrimaryKey() throws SQLException {
        if (config.memory) {
            return;
        }
        reconnect();
        stat.execute("create table test(id int not null)");
        stat.execute("alter table test add constraint x primary key(id)");
        ResultSet rs;
        rs = conn.getMetaData().getIndexInfo(null, null, "TEST", true, false);
        rs.next();
        String old = rs.getString("INDEX_NAME");
        stat.execute("alter index " + old + " rename to y");
        rs = conn.getMetaData().getIndexInfo(null, null, "TEST", true, false);
        rs.next();
        assertEquals("Y", rs.getString("INDEX_NAME"));
        reconnect();
        rs = conn.getMetaData().getIndexInfo(null, null, "TEST", true, false);
        rs.next();
        assertEquals("Y", rs.getString("INDEX_NAME"));
        stat.execute("drop table test");
    }

    private void testRandomized() throws SQLException {
        boolean reopen = !config.memory;
        Random rand = new Random(1);
        reconnect();
        stat.execute("CREATE TABLE TEST(ID identity)");
        int len = getSize(100, 1000);
        for (int i = 0; i < len; i++) {
            switch (rand.nextInt(4)) {
                case 0:
                    if (reopen) {
                        trace("reconnect");
                        reconnect();
                    }
                    break;
                case 1:
                    trace("insert");
                    stat.execute("insert into test(id) values(null)");
                    break;
                case 2:
                    trace("delete");
                    stat.execute("delete from test");
                    break;
                case 3:
                    trace("insert 1-100");
                    stat.execute("insert into test select null from system_range(1, 100)");
                    break;
            }
        }
        stat.execute("drop table test");
    }

    private void testHashIndex() throws SQLException {
        reconnect();
        stat.execute("create table testA(id int primary key, name varchar)");
        stat.execute("create table testB(id int primary key hash, name varchar)");
        int len = getSize(1000, 10000);
        stat.execute("insert into testA select x, 'Hello' from system_range(1, " + len + ")");
        stat.execute("insert into testB select x, 'Hello' from system_range(1, " + len + ")");
        Random rand = new Random(1);
        for (int i = 0; i < len; i++) {
            int x = rand.nextInt(len);
            String sql = "";
            switch (rand.nextInt(3)) {
                case 0:
                    sql = "delete from testA where id = " + x;
                    break;
                case 1:
                    sql = "update testA set name = " + rand.nextInt(100) + " where id = " + x;
                    break;
                case 2:
                    sql = "select name from testA where id = " + x;
                    break;
                default:
            }
            boolean result = stat.execute(sql);
            if (result) {
                ResultSet rs = stat.getResultSet();
                String s1 = rs.next() ? rs.getString(1) : null;
                rs = stat.executeQuery(sql.replace('A', 'B'));
                String s2 = rs.next() ? rs.getString(1) : null;
                assertEquals(s1, s2);
            } else {
                int count1 = stat.getUpdateCount();
                int count2 = stat.executeUpdate(sql.replace('A', 'B'));
                assertEquals(count1, count2);
            }
        }
        stat.execute("drop table testA, testB");
        conn.close();
    }

    private void reconnect() throws SQLException {
        if (conn != null) {
            conn.close();
            conn = null;
        }
        conn = getConnection("index");
        stat = conn.createStatement();
    }

    private void testDescIndex() throws SQLException {
        if (config.memory) {
            return;
        }
        ResultSet rs;
        reconnect();
        stat.execute("CREATE TABLE TEST(ID INT)");
        stat.execute("CREATE INDEX IDX_ND ON TEST(ID DESC)");
        rs = conn.getMetaData().getIndexInfo(null, null, "TEST", false, false);
        rs.next();
        assertEquals("D", rs.getString("ASC_OR_DESC"));
        assertEquals(SortOrder.DESCENDING, rs.getInt("SORT_TYPE"));
        stat.execute("INSERT INTO TEST SELECT X FROM SYSTEM_RANGE(1, 30)");
        rs = stat.executeQuery("SELECT COUNT(*) FROM TEST WHERE ID BETWEEN 10 AND 20");
        rs.next();
        assertEquals(11, rs.getInt(1));
        reconnect();
        rs = conn.getMetaData().getIndexInfo(null, null, "TEST", false, false);
        rs.next();
        assertEquals("D", rs.getString("ASC_OR_DESC"));
        assertEquals(SortOrder.DESCENDING, rs.getInt("SORT_TYPE"));
        rs = stat.executeQuery("SELECT COUNT(*) FROM TEST WHERE ID BETWEEN 10 AND 20");
        rs.next();
        assertEquals(11, rs.getInt(1));
        stat.execute("DROP TABLE TEST");
        conn.close();
    }

    private String getRandomString(int len) {
        StringBuilder buff = new StringBuilder();
        for (int i = 0; i < len; i++) {
            buff.append((char) ('a' + random.nextInt(26)));
        }
        return buff.toString();
    }

    private void testWideIndex(int length) throws SQLException {
        reconnect();
        stat.execute("CREATE TABLE TEST(ID INT, NAME VARCHAR)");
        stat.execute("CREATE INDEX IDXNAME ON TEST(NAME)");
        for (int i = 0; i < 100; i++) {
            stat.execute("INSERT INTO TEST VALUES(" + i + ", SPACE(" + length + ") || " + i + " )");
        }
        ResultSet rs = stat.executeQuery("SELECT * FROM TEST ORDER BY NAME");
        while (rs.next()) {
            int id = rs.getInt("ID");
            String name = rs.getString("NAME");
            assertEquals("" + id, name.trim());
        }
        if (!config.memory) {
            reconnect();
            rs = stat.executeQuery("SELECT * FROM TEST ORDER BY NAME");
            while (rs.next()) {
                int id = rs.getInt("ID");
                String name = rs.getString("NAME");
                assertEquals("" + id, name.trim());
            }
        }
        stat.execute("DROP TABLE TEST");
    }

    private void testLike() throws SQLException {
        reconnect();
        stat.execute("CREATE TABLE ABC(ID INT, NAME VARCHAR)");
        stat.execute("INSERT INTO ABC VALUES(1, 'Hello')");
        PreparedStatement prep = conn.prepareStatement("SELECT * FROM ABC WHERE NAME LIKE CAST(? AS VARCHAR)");
        prep.setString(1, "Hi%");
        prep.execute();
        stat.execute("DROP TABLE ABC");
    }

    private void testConstraint() throws SQLException {
        if (config.memory) {
            return;
        }
        stat.execute("CREATE TABLE PARENT(ID INT PRIMARY KEY)");
        stat.execute("CREATE TABLE CHILD(ID INT PRIMARY KEY, PID INT, FOREIGN KEY(PID) REFERENCES PARENT(ID))");
        reconnect();
        stat.execute("DROP TABLE PARENT");
        stat.execute("DROP TABLE CHILD");
    }

    private void testLargeIndex() throws SQLException {
        random.setSeed(10);
        for (int i = 1; i < 100; i += getSize(1000, 3)) {
            stat.execute("DROP TABLE IF EXISTS TEST");
            stat.execute("CREATE TABLE TEST(NAME VARCHAR(" + i + "))");
            stat.execute("CREATE INDEX IDXNAME ON TEST(NAME)");
            PreparedStatement prep = conn.prepareStatement("INSERT INTO TEST VALUES(?)");
            for (int j = 0; j < getSize(2, 5); j++) {
                prep.setString(1, getRandomString(i));
                prep.execute();
            }
            if (!config.memory) {
                conn.close();
                conn = getConnection("index");
                stat = conn.createStatement();
            }
            ResultSet rs = stat.executeQuery("SELECT COUNT(*) FROM TEST WHERE NAME > 'mdd'");
            rs.next();
            int count = rs.getInt(1);
            trace(i + " count=" + count);
        }

        stat.execute("DROP TABLE IF EXISTS TEST");
    }

    private void testHashIndex(boolean primaryKey, boolean hash) throws SQLException {
        if (config.memory) {
            return;
        }

        reconnect();

        stat.execute("DROP TABLE IF EXISTS TEST");
        if (primaryKey) {
            stat.execute("CREATE TABLE TEST(A INT PRIMARY KEY " + (hash ? "HASH" : "") + ", B INT)");
        } else {
            stat.execute("CREATE TABLE TEST(A INT, B INT)");
            stat.execute("CREATE UNIQUE " + (hash ? "HASH" : "") + " INDEX ON TEST(A)");
        }
        PreparedStatement prep;
        prep = conn.prepareStatement("INSERT INTO TEST VALUES(?, ?)");
        int len = getSize(5, 1000);
        for (int a = 0; a < len; a++) {
            prep.setInt(1, a);
            prep.setInt(2, a);
            prep.execute();
            assertEquals(1, getValue("SELECT COUNT(*) FROM TEST WHERE A=" + a));
            assertEquals(0, getValue("SELECT COUNT(*) FROM TEST WHERE A=-1-" + a));
        }

        reconnect();

        prep = conn.prepareStatement("DELETE FROM TEST WHERE A=?");
        for (int a = 0; a < len; a++) {
            if (getValue("SELECT COUNT(*) FROM TEST WHERE A=" + a) != 1) {
                assertEquals(1, getValue("SELECT COUNT(*) FROM TEST WHERE A=" + a));
            }
            prep.setInt(1, a);
            assertEquals(1, prep.executeUpdate());
        }
        assertEquals(0, getValue("SELECT COUNT(*) FROM TEST"));
    }

    private void testMultiColumnIndex() throws SQLException {
        stat.execute("DROP TABLE IF EXISTS TEST");
        stat.execute("CREATE TABLE TEST(A INT, B INT)");
        PreparedStatement prep;
        prep = conn.prepareStatement("INSERT INTO TEST VALUES(?, ?)");
        int len = getSize(3, 260);
        for (int a = 0; a < len; a++) {
            prep.setInt(1, a);
            prep.setInt(2, a);
            prep.execute();
        }
        stat.execute("INSERT INTO TEST SELECT A, B FROM TEST");
        stat.execute("CREATE INDEX ON TEST(A, B)");
        prep = conn.prepareStatement("DELETE FROM TEST WHERE A=?");
        for (int a = 0; a < len; a++) {
            log("SELECT * FROM TEST");
            assertEquals(2, getValue("SELECT COUNT(*) FROM TEST WHERE A=" + (len - a - 1)));
            assertEquals((len - a) * 2, getValue("SELECT COUNT(*) FROM TEST"));
            prep.setInt(1, len - a - 1);
            prep.execute();
        }
        assertEquals(0, getValue("SELECT COUNT(*) FROM TEST"));
    }

    private void testMultiColumnHashIndex() throws SQLException {
        if (config.memory) {
            return;
        }

        stat.execute("DROP TABLE IF EXISTS TEST");
        stat.execute("CREATE TABLE TEST(A INT, B INT, DATA VARCHAR(255))");
        stat.execute("CREATE UNIQUE HASH INDEX IDX_AB ON TEST(A, B)");
        PreparedStatement prep;
        prep = conn.prepareStatement("INSERT INTO TEST VALUES(?, ?, ?)");
        // speed is quadratic (len*len)
        int len = getSize(2, 14);
        for (int a = 0; a < len; a++) {
            for (int b = 0; b < len; b += 2) {
                prep.setInt(1, a);
                prep.setInt(2, b);
                prep.setString(3, "i(" + a + "," + b + ")");
                prep.execute();
            }
        }

        reconnect();

        prep = conn.prepareStatement("UPDATE TEST SET DATA=DATA||? WHERE A=? AND B=?");
        for (int a = 0; a < len; a++) {
            for (int b = 0; b < len; b += 2) {
                prep.setString(1, "u(" + a + "," + b + ")");
                prep.setInt(2, a);
                prep.setInt(3, b);
                prep.execute();
            }
        }

        reconnect();

        ResultSet rs = stat.executeQuery("SELECT * FROM TEST WHERE DATA <> 'i('||a||','||b||')u('||a||','||b||')'");
        assertFalse(rs.next());
        assertEquals(len * (len / 2), getValue("SELECT COUNT(*) FROM TEST"));
        stat.execute("DROP TABLE TEST");
    }

    private int getValue(String sql) throws SQLException {
        ResultSet rs = stat.executeQuery(sql);
        rs.next();
        return rs.getInt(1);
    }

    private void log(String sql) throws SQLException {
        trace(sql);
        ResultSet rs = stat.executeQuery(sql);
        int cols = rs.getMetaData().getColumnCount();
        while (rs.next()) {
            StringBuilder buff = new StringBuilder();
            for (int i = 0; i < cols; i++) {
                if (i > 0) {
                    buff.append(", ");
                }
                buff.append("[" + i + "]=" + rs.getString(i + 1));
            }
            trace(buff.toString());
        }
        trace("---done---");
    }

}
