/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2;

import org.h2.engine.Constants;
import org.h2.jdbc.JdbcConnection;
import org.h2.message.Message;
import org.h2.message.TraceSystem;

import java.sql.*;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * The database driver. An application should not use this class directly. The
 * only thing the application needs to do is load the driver. This can be done
 * using Class.forName. To load the driver and open a database connection, use
 * the following code:
 * <p>
 * <pre>
 * Class.forName(&quot;org.h2.Driver&quot;);
 * Connection conn = DriverManager.getConnection(
 *      &quot;jdbc:h2:&tilde;/test&quot;, &quot;sa&quot;, &quot;sa&quot;);
 * </pre>
 */
public class Driver implements java.sql.Driver {

    private static final Driver INSTANCE = new Driver();
    private static volatile boolean registered;

    static {
        load();
    }

    /**
     * Open a database connection.
     * This method should not be called by an application.
     * Instead, the method DriverManager.getConnection should be used.
     *
     * @param url  the database URL
     * @param info the connection properties
     * @return the new connection
     */
    public Connection connect(String url, Properties info) throws SQLException {
        try {
            if (info == null) {
                info = new Properties();
            }
            if (!acceptsURL(url)) {
                return null;
            }
            return new JdbcConnection(url, info);
        } catch (Exception e) {
            throw Message.convert(e);
        }
    }

    /**
     * Check if the driver understands this URL.
     * This method should not be called by an application.
     *
     * @param url the database URL
     * @return if the driver understands the URL
     */
    public boolean acceptsURL(String url) {
        return url != null && url.startsWith(Constants.START_URL);
    }

    /**
     * Get the major version number of the driver.
     * This method should not be called by an application.
     *
     * @return the major version number
     */
    public int getMajorVersion() {
        return Constants.VERSION_MAJOR;
    }

    /**
     * Get the minor version number of the driver.
     * This method should not be called by an application.
     *
     * @return the minor version number
     */
    public int getMinorVersion() {
        return Constants.VERSION_MINOR;
    }

    /**
     * Get the list of supported properties.
     * This method should not be called by an application.
     *
     * @param url  the database URL
     * @param info the connection properties
     * @return a zero length array
     */
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
        return new DriverPropertyInfo[0];
    }

    /**
     * Check if this driver is compliant to the JDBC specification.
     * This method should not be called by an application.
     *
     * @return true
     */
    public boolean jdbcCompliant() {
        return true;
    }

    /**
     * INTERNAL
     */
    public static synchronized Driver load() {
        try {
            if (!registered) {
                System.out.println("Register driver");
                registered = true;
                DriverManager.registerDriver(INSTANCE);
            }
        } catch (SQLException e) {
            TraceSystem.traceThrowable(e);
        }
        return INSTANCE;
    }

    /**
     * INTERNAL
     */
    public static synchronized void unload() {
        try {
            if (registered) {
                registered = false;
                DriverManager.deregisterDriver(INSTANCE);
            }
        } catch (SQLException e) {
            TraceSystem.traceThrowable(e);
        }
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new RuntimeException("Method not implemented.");
    }

}
