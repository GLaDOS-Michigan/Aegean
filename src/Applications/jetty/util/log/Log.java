//
//  ========================================================================
//  Copyright (c) 1995-2013 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package Applications.jetty.util.log;

import Applications.jetty.util.Loader;
import Applications.jetty.util.annotation.ManagedAttribute;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Logging.
 * This class provides a static logging interface.  If an instance of the
 * org.slf4j.Logger class is found on the classpath, the static log methods
 * are directed to a slf4j logger for "org.eclipse.log".   Otherwise the logs
 * are directed to stderr.
 * <p>
 * The "org.eclipse.jetty.util.log.class" system property can be used
 * to select a specific logging implementation.
 * <p>
 * If the system property org.eclipse.jetty.util.log.IGNORED is set,
 * then ignored exceptions are logged in detail.
 *
 * @see StdErrLog
 * @see Slf4jLog
 */
public class Log {
    public final static String EXCEPTION = "EXCEPTION ";
    public final static String IGNORED = "IGNORED ";

    /**
     * Logging Configuration Properties
     */
    protected static final Properties __props;
    /**
     * The {@link Logger} implementation class name
     */
    public static String __logClass;
    /**
     * Legacy flag indicating if {@link Logger#ignore(Throwable)} methods produce any output in the {@link Logger}s
     */
    public static boolean __ignored;

    /**
     * Hold loggers only.
     */
    private final static ConcurrentMap<String, Logger> __loggers = new ConcurrentHashMap<>();


    static {
        /* Instantiate a default configuration properties (empty)
         */
        __props = new Properties();

        AccessController.doPrivileged(new PrivilegedAction<Object>() {
            public Object run() {
                /* First see if the jetty-logging.properties object exists in the classpath.
                 * This is an optional feature used by embedded mode use, and test cases to allow for early
                 * configuration of the Log class in situations where access to the System.properties are
                 * either too late or just impossible.
                 */
                URL testProps = Loader.getResource(Log.class, "jetty-logging.properties");
                if (testProps != null) {
                    InputStream in = null;
                    try {
                        in = testProps.openStream();
                        __props.load(in);
                    } catch (IOException e) {
                        System.err.println("Unable to load " + testProps);
                        e.printStackTrace(System.err);
                    } finally {
                        safeCloseInputStream(in);
                    }
                }

                /* Now load the System.properties as-is into the __props, these values will override
                 * any key conflicts in __props.
                 */
                @SuppressWarnings("unchecked")
                Enumeration<String> systemKeyEnum = (Enumeration<String>) System.getProperties().propertyNames();
                while (systemKeyEnum.hasMoreElements()) {
                    String key = systemKeyEnum.nextElement();
                    String val = System.getProperty(key);
                    //protect against application code insertion of non-String values (returned as null)
                    if (val != null)
                        __props.setProperty(key, val);
                }

                /* Now use the configuration properties to configure the Log statics
                 */
                __logClass = __props.getProperty("org.eclipse.jetty.util.log.class", "org.eclipse.jetty.util.log.Slf4jLog");
                __ignored = Boolean.parseBoolean(__props.getProperty("org.eclipse.jetty.util.log.IGNORED", "false"));
                return null;
            }
        });
    }

    private static void safeCloseInputStream(InputStream in) {
        try {
            if (in != null)
                in.close();
        } catch (IOException e) {
            LOG.ignore(e);
        }
    }

    private static Logger LOG;
    private static boolean __initialized;

    public static boolean initialized() {
        if (LOG != null) {
            return true;
        }

        synchronized (Log.class) {
            if (__initialized) {
                return LOG != null;
            }
            __initialized = true;
        }

        try {
            Class<?> log_class = Loader.loadClass(Log.class, __logClass);
            if (LOG == null || !LOG.getClass().equals(log_class)) {
                LOG = (Logger) log_class.newInstance();
                LOG.debug("Logging to {} via {}", LOG, log_class.getName());
            }
        } catch (Throwable e) {
            // Unable to load specified Logger implementation, default to standard logging.
            initStandardLogging(e);
        }

        return LOG != null;
    }

    private static void initStandardLogging(Throwable e) {
        Class<?> log_class;
        if (e != null && __ignored) {
            e.printStackTrace();
        }

        if (LOG == null) {
            log_class = StdErrLog.class;
            LOG = new StdErrLog();
            LOG.debug("Logging to {} via {}", LOG, log_class.getName());
        }
    }

    public static Logger getLog() {
        initialized();
        return LOG;
    }

    public static void setLog(Logger log) {
        Log.LOG = log;
    }

    /**
     * Get the root logger.
     *
     * @return the root logger
     */
    public static Logger getRootLogger() {
        initialized();
        return LOG;
    }

    static boolean isIgnored() {
        return __ignored;
    }

    /**
     * Set Log to parent Logger.
     * <p>
     * If there is a different Log class available from a parent classloader,
     * call {@link #getLogger(String)} on it and construct a {@link LoggerLog} instance
     * as this Log's Logger, so that logging is delegated to the parent Log.
     * <p>
     * This should be used if a webapp is using Log, but wishes the logging to be
     * directed to the containers log.
     * <p>
     * If there is not parent Log, then this call is equivalent to<pre>
     *   Log.setLog(Log.getLogger(name));
     * </pre>
     *
     * @param name Logger name
     */
    public static void setLogToParent(String name) {
        ClassLoader loader = Log.class.getClassLoader();
        if (loader != null && loader.getParent() != null) {
            try {
                Class<?> uberlog = loader.getParent().loadClass("org.eclipse.jetty.util.log.Log");
                Method getLogger = uberlog.getMethod("getLogger", new Class[]{String.class});
                Object logger = getLogger.invoke(null, name);
                setLog(new LoggerLog(logger));
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            setLog(getLogger(name));
        }
    }

    /**
     * Obtain a named Logger based on the fully qualified class name.
     *
     * @param clazz the class to base the Logger name off of
     * @return the Logger with the given name
     */
    public static Logger getLogger(Class<?> clazz) {
        return getLogger(clazz.getName());
    }

    /**
     * Obtain a named Logger or the default Logger if null is passed.
     *
     * @param name the Logger name
     * @return the Logger with the given name
     */
    public static Logger getLogger(String name) {
        if (!initialized()) {
            IllegalStateException e = new IllegalStateException();
            e.printStackTrace();
            throw e;
        }

        if (name == null)
            return LOG;

        Logger logger = __loggers.get(name);
        if (logger == null)
            logger = LOG.getLogger(name);

        return logger;
    }

    static ConcurrentMap<String, Logger> getMutableLoggers() {
        return __loggers;
    }

    /**
     * Get a map of all configured {@link Logger} instances.
     *
     * @return a map of all configured {@link Logger} instances
     */
    @ManagedAttribute("list of all instantiated loggers")
    public static Map<String, Logger> getLoggers() {
        return Collections.unmodifiableMap(__loggers);
    }
}
