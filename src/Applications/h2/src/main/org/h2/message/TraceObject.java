/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.message;

import org.h2.constant.SysProperties;
import org.h2.expression.ParameterInterface;
import org.h2.util.*;

import java.io.PrintWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Map;

/**
 * The base class for objects that can print trace information about themselves.
 */
public class TraceObject {

    /**
     * The trace type id for callable statements.
     */
    protected static final int CALLABLE_STATEMENT = 0;

    /**
     * The trace type id for connections.
     */
    protected static final int CONNECTION = 1;

    /**
     * The trace type id for database meta data objects.
     */
    protected static final int DATABASE_META_DATA = 2;

    /**
     * The trace type id for prepared statements.
     */
    protected static final int PREPARED_STATEMENT = 3;

    /**
     * The trace type id for result sets.
     */
    protected static final int RESULT_SET = 4;

    /**
     * The trace type id for result set meta data objects.
     */
    protected static final int RESULT_SET_META_DATA = 5;

    /**
     * The trace type id for savepoint objects.
     */
    protected static final int SAVEPOINT = 6;

    /**
     * The trace type id for sql exceptions.
     */
    protected static final int SQL_EXCEPTION = 7;

    /**
     * The trace type id for statements.
     */
    protected static final int STATEMENT = 8;

    /**
     * The trace type id for blobs.
     */
    protected static final int BLOB = 9;

    /**
     * The trace type id for clobs.
     */
    protected static final int CLOB = 10;

    /**
     * The trace type id for parameter meta data objects.
     */
    protected static final int PARAMETER_META_DATA = 11;

    /**
     * The trace type id for data sources.
     */
    protected static final int DATA_SOURCE = 12;

    /**
     * The trace type id for XA data sources.
     */
    protected static final int XA_DATA_SOURCE = 13;

    /**
     * The trace type id for transaction ids.
     */
    protected static final int XID = 14;

    /**
     * The trace type id for array objects.
     */
    protected static final int ARRAY = 15;

    private static final int LAST = ARRAY + 1;
    private static final int[] ID = new int[LAST];
    private static final String[] PREFIX = {"call", "conn", "dbMeta", "prep",
            "rs", "rsMeta", "sp", "ex", "stat", "blob", "clob", "pMeta", "ds",
            "xads", "xid", "ar"};

    private Trace trace;
    private int traceType;
    private int id;

    /**
     * Set the options to use when writing trace message.
     *
     * @param trace the trace object
     * @param type  the trace object type
     * @param id    the trace object id
     */
    protected void setTrace(Trace trace, int type, int id) {
        this.trace = trace;
        this.traceType = type;
        this.id = id;
    }

    /**
     * Update the trace object.
     *
     * @param trace the trace object
     */
    protected void setTrace(Trace trace) {
        this.trace = trace;
    }

    /**
     * Get the trace object.
     *
     * @return the trace object
     */
    protected Trace getTrace() {
        return trace;
    }

    /**
     * INTERNAL
     */
    public int getTraceId() {
        return id;
    }

    /**
     * INTERNAL
     */
    public String getTraceObjectName() {
        return PREFIX[traceType] + id;
    }

    /**
     * Get the next trace object id for this object type.
     *
     * @param type the object type
     * @return the new trace object id
     */
    protected int getNextId(int type) {
        return ID[type]++;
    }

    /**
     * Check if the debug trace level is enabled.
     *
     * @return true if it is
     */
    protected boolean isDebugEnabled() {
        if (trace == null) {
            return false;
        }
        return trace.isDebugEnabled();
    }

    /**
     * Check if info trace level is enabled.
     *
     * @return true if it is
     */
    protected boolean isInfoEnabled() {
        if (trace == null) {
            return false;
        }
        return trace.isInfoEnabled();
    }

    /**
     * Write trace information as an assignment in the form className prefixId =
     * objectName.value.
     *
     * @param className the class name of the result
     * @param newType   the prefix type
     * @param newId     the trace object id of the created object
     * @param value     the value to assign this new object to
     */
    protected void debugCodeAssign(String className, int newType, int newId,
                                   String value) {
        if (trace != null && trace.isDebugEnabled()) {
            trace.debugCode(className + " " + PREFIX[newType] + newId + " = "
                    + getTraceObjectName() + "." + value + ";");
        }
    }

    /**
     * Write trace information as a method call in the form
     * objectName.methodName().
     *
     * @param methodName the method name
     */
    protected void debugCodeCall(String methodName) {
        if (trace != null && trace.isDebugEnabled()) {
            trace.debugCode(getTraceObjectName() + "." + methodName + "();");
        }
    }

    /**
     * Write trace information as a method call in the form
     * objectName.methodName(param) where the parameter is formatted as a long
     * value.
     *
     * @param methodName the method name
     * @param param      one single long parameter
     */
    protected void debugCodeCall(String methodName, long param) {
        if (trace != null && trace.isDebugEnabled()) {
            trace.debugCode(getTraceObjectName() + "." + methodName + "("
                    + param + ");");
        }
    }

    /**
     * Write trace information as a method call in the form
     * objectName.methodName(param) where the parameter is formatted as a Java
     * string.
     *
     * @param methodName the method name
     * @param param      one single string parameter
     */
    protected void debugCodeCall(String methodName, String param) {
        if (trace != null && trace.isDebugEnabled()) {
            trace.debugCode(getTraceObjectName() + "." + methodName + "("
                    + quote(param) + ");");
        }
    }

    /**
     * Write trace information in the form objectName.text.
     *
     * @param text the trace text
     */
    protected void debugCode(String text) {
        if (trace != null && trace.isDebugEnabled()) {
            trace.debugCode(getTraceObjectName() + "." + text);
        }
    }

    /**
     * Format a string as a Java string literal.
     *
     * @param s the string to convert
     * @return the Java string literal
     */
    protected String quote(String s) {
        return StringUtils.quoteJavaString(s);
    }

    /**
     * Format a time to the Java source code that represents this object.
     *
     * @param x the time to convert
     * @return the Java source code
     */
    protected String quoteTime(java.sql.Time x) {
        if (x == null) {
            return "null";
        }
        return "Time.valueOf(\"" + x.toString() + "\")";
    }

    /**
     * Format a timestamp to the Java source code that represents this object.
     *
     * @param x the timestamp to convert
     * @return the Java source code
     */
    protected String quoteTimestamp(java.sql.Timestamp x) {
        if (x == null) {
            return "null";
        }
        return "Timestamp.valueOf(\"" + x.toString() + "\")";
    }

    /**
     * Format a date to the Java source code that represents this object.
     *
     * @param x the date to convert
     * @return the Java source code
     */
    protected String quoteDate(java.sql.Date x) {
        if (x == null) {
            return "null";
        }
        return "Date.valueOf(\"" + x.toString() + "\")";
    }

    /**
     * Format a big decimal to the Java source code that represents this object.
     *
     * @param x the big decimal to convert
     * @return the Java source code
     */
    protected String quoteBigDecimal(BigDecimal x) {
        if (x == null) {
            return "null";
        }
        return "new BigDecimal(\"" + x.toString() + "\")";
    }

    /**
     * Format a byte array to the Java source code that represents this object.
     *
     * @param x the byte array to convert
     * @return the Java source code
     */
    protected String quoteBytes(byte[] x) {
        if (x == null) {
            return "null";
        }
        return "org.h2.util.ByteUtils.convertStringToBytes(\""
                + ByteUtils.convertBytesToString(x) + "\")";
    }

    /**
     * Format a string array to the Java source code that represents this
     * object.
     *
     * @param s the string array to convert
     * @return the Java source code
     */
    protected String quoteArray(String[] s) {
        return StringUtils.quoteJavaStringArray(s);
    }

    /**
     * Format an int array to the Java source code that represents this object.
     *
     * @param s the int array to convert
     * @return the Java source code
     */
    protected String quoteIntArray(int[] s) {
        return StringUtils.quoteJavaIntArray(s);
    }

    /**
     * Format a map to the Java source code that represents this object.
     *
     * @param map the map to convert
     * @return the Java source code
     */
    protected String quoteMap(Map<String, Class<?>> map) {
        if (map == null) {
            return "null";
        }
        if (map.size() == 0) {
            return "new Map()";
        }
        StringBuilder buff = new StringBuilder("new Map() /* ");
        try {
            for (Map.Entry<String, Class<?>> entry : map.entrySet()) {
                String key = entry.getKey();
                Class<?> clazz = entry.getValue();
                buff.append(key).append(':').append(clazz.getName());
            }
        } catch (Exception e) {
            buff.append(e.toString()).append(": ").append(map.toString());
        }
        buff.append("*/");
        return buff.toString();
    }

    /**
     * Log an exception and convert it to a SQL exception if required.
     *
     * @param e the exception
     * @return the SQL exception object
     */
    protected SQLException logAndConvert(Exception e) {
        if (SysProperties.LOG_ALL_ERRORS) {
            synchronized (TraceObject.class) {
                // e.printStackTrace();
                try {
                    Writer writer = IOUtils.getWriter(FileUtils
                            .openFileOutputStream(
                                    SysProperties.LOG_ALL_ERRORS_FILE, true));
                    PrintWriter p = new PrintWriter(writer);
                    e.printStackTrace(p);
                    p.close();
                    writer.close();
                } catch (Exception e2) {
                    e2.printStackTrace();
                }
            }
        }
        if (trace == null) {
            TraceSystem.traceThrowable(e);
        } else {
            if (e instanceof SQLException) {
                SQLException e2 = (SQLException) e;
                int errorCode = e2.getErrorCode();
                if (errorCode >= 23000 && errorCode < 24000) {
                    trace.info("SQLException", e);
                } else {
                    trace.error("SQLException", e);
                }
                return (SQLException) e;
            }
            trace.error("Uncaught Exception", e);
        }
        return Message.convert(e);
    }

    /**
     * INTERNAL
     */
    public static String toString(String sql,
                                  ObjectArray<? extends ParameterInterface> params) {
        StatementBuilder buff = new StatementBuilder(sql);
        if (params != null && params.size() > 0) {
            buff.append(" {");
            int i = 0;
            for (ParameterInterface p : params) {
                i++;
                try {
                    buff.appendExceptFirst(", ");
                    buff.append(i).append(": ");
                    if (p == null || p.getParamValue() == null) {
                        buff.append('-');
                    } else {
                        buff.append(p.getParamValue().getSQL());
                    }
                } catch (SQLException e) {
                    buff.append("/* ").append(i).append(": ").append(
                            e.toString()).append("*/ ");
                }
            }
            buff.append("};");
        }
        return buff.toString();

    }

}
