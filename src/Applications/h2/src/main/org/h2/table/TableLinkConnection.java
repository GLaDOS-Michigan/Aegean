/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.table;

import merkle.wrapper.MTMapWrapper;
import org.h2.constant.SysProperties;
import org.h2.util.JdbcUtils;
import org.h2.util.ObjectUtils;
import org.h2.util.StringUtils;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * A connection for a linked table. The same connection may be used for multiple
 * tables, that means a connection may be shared.
 */
public class TableLinkConnection {

    /**
     * The map where the link is kept.
     */
    private MTMapWrapper map;

    /**
     * The connection information.
     */
    private final String driver, url, user, password;

    /**
     * The database connection.
     */
    private Connection conn;

    /**
     * How many times the connection is used.
     */
    private int useCounter;

    private TableLinkConnection(MTMapWrapper map, String driver, String url, String user, String password) {
        this.map = map;
        this.driver = driver;
        this.url = url;
        this.user = user;
        this.password = password;
    }

    /**
     * Open a new connection.
     *
     * @param map      the map where the connection should be stored
     *                 (if shared connections are enabled).
     * @param driver   the JDBC driver class name
     * @param url      the database URL
     * @param user     the user name
     * @param password the password
     * @return a connection
     */
    public static TableLinkConnection open(MTMapWrapper map, String driver, String url, String user, String password) throws SQLException {
        TableLinkConnection t = new TableLinkConnection(map, driver, url, user, password);
        if (!SysProperties.SHARE_LINKED_CONNECTIONS) {
            t.open();
            return t;
        }
        synchronized (map) {
            TableLinkConnection result;
            result = (TableLinkConnection) map.get(t);
            if (result == null) {
                synchronized (t) {
                    t.open();
                }
                // put the connection in the map after is has been opened,
                // so we know it works
                map.put(t, t);
                result = t;
            }
            synchronized (result) {
                result.useCounter++;
            }
            return result;
        }
    }

    private void open() throws SQLException {
        conn = JdbcUtils.getConnection(driver, url, user, password);
    }

    public int hashCode() {
        return ObjectUtils.hashCode(driver)
                ^ ObjectUtils.hashCode(url)
                ^ ObjectUtils.hashCode(user)
                ^ ObjectUtils.hashCode(password);
    }

    public boolean equals(Object o) {
        if (o instanceof TableLinkConnection) {
            TableLinkConnection other = (TableLinkConnection) o;
            return StringUtils.equals(driver, other.driver)
                    && StringUtils.equals(url, other.url)
                    && StringUtils.equals(user, other.user)
                    && StringUtils.equals(password, other.password);
        }
        return false;
    }

    /**
     * Get the connection.
     * This method and methods on the statement must be
     * synchronized on this object.
     *
     * @return the connection
     */
    Connection getConnection() {
        return conn;
    }

    /**
     * Closes the connection if this is the last link to it.
     */
    synchronized void close() throws SQLException {
        if (--useCounter <= 0) {
            conn.close();
            conn = null;
            synchronized (map) {
                map.remove(this);
            }
        }
    }

}
