/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.engine;

import org.h2.command.Parser;
import org.h2.constant.ErrorCode;
import org.h2.expression.Expression;
import org.h2.message.Message;
import org.h2.message.Trace;
import org.h2.table.Table;
import org.h2.util.*;
import org.h2.value.DataType;
import org.h2.value.Value;
import org.h2.value.ValueNull;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;

/**
 * Represents a user-defined function, or alias.
 *
 * @author Thomas Mueller
 * @author Gary Tong
 */
public class FunctionAlias extends DbObjectBase {

    private String className;
    private String methodName;
    private String source;
    private JavaMethod[] javaMethods;
    private boolean deterministic;

    private FunctionAlias(Database db, int id, String name) {
        initDbObjectBase(db, id, name, Trace.FUNCTION);
    }

    /**
     * Create a new alias based on a method name.
     *
     * @param db              the database
     * @param id              the id
     * @param name            the name
     * @param javaClassMethod the class and method name
     * @param force           create the object even if the class or method does not exist
     * @return the database object
     */
    public static FunctionAlias newInstance(Database db, int id, String name, String javaClassMethod, boolean force) throws SQLException {
        FunctionAlias alias = new FunctionAlias(db, id, name);
        int paren = javaClassMethod.indexOf('(');
        int lastDot = javaClassMethod.lastIndexOf('.', paren < 0 ? javaClassMethod.length() : paren);
        if (lastDot < 0) {
            throw Message.getSQLException(ErrorCode.SYNTAX_ERROR_1, javaClassMethod);
        }
        alias.className = javaClassMethod.substring(0, lastDot);
        alias.methodName = javaClassMethod.substring(lastDot + 1);
        alias.init(force);
        return alias;
    }

    /**
     * Create a new alias based on source code.
     *
     * @param db     the database
     * @param id     the id
     * @param name   the name
     * @param source the source code
     * @param force  create the object even if the class or method does not exist
     * @return the database object
     */
    public static FunctionAlias newInstanceFromSource(Database db, int id, String name, String source, boolean force) throws SQLException {
        FunctionAlias alias = new FunctionAlias(db, id, name);
        alias.source = source;
        alias.init(force);
        return alias;
    }

    private void init(boolean force) throws SQLException {
        try {
            // at least try to compile the class, otherwise the data type is not
            // initialized if it could be
            load();
        } catch (SQLException e) {
            if (!force) {
                throw e;
            }
        }
    }

    private synchronized void load() throws SQLException {
        if (javaMethods != null) {
            return;
        }
        if (source != null) {
            loadFromSource();
        } else {
            loadClass();
        }
    }

    private void loadFromSource() throws SQLException {
        SourceCompiler compiler = database.getCompiler();
        String fullClassName = Constants.USER_PACKAGE + "." + getName();
        compiler.setSource(fullClassName, source);
        try {
            Method m = compiler.getMethod(fullClassName);
            JavaMethod method = new JavaMethod(m, 0);
            javaMethods = new JavaMethod[]{
                    method
            };
        } catch (Exception e) {
            throw Message.getSQLException(ErrorCode.SYNTAX_ERROR_1, e, source);
        }
    }

    private void loadClass() throws SQLException {
        Class<?> javaClass = ClassUtils.loadUserClass(className);
        Method[] methods = javaClass.getMethods();
        ObjectArray<JavaMethod> list = ObjectArray.newInstance(false);
        for (int i = 0; i < methods.length; i++) {
            Method m = methods[i];
            if (!Modifier.isStatic(m.getModifiers())) {
                continue;
            }
            if (m.getName().equals(methodName) || getMethodSignature(m).equals(methodName)) {
                JavaMethod javaMethod = new JavaMethod(m, i);
                for (JavaMethod old : list) {
                    if (old.getParameterCount() == javaMethod.getParameterCount()) {
                        throw Message.getSQLException(
                                ErrorCode.METHODS_MUST_HAVE_DIFFERENT_PARAMETER_COUNTS_2,
                                old.toString(), javaMethod.toString()
                        );
                    }
                }
                list.add(javaMethod);
            }
        }
        if (list.size() == 0) {
            throw Message.getSQLException(ErrorCode.PUBLIC_STATIC_JAVA_METHOD_NOT_FOUND_1, methodName + " (" + className + ")");
        }
        javaMethods = new JavaMethod[list.size()];
        list.toArray(javaMethods);
        // Sort elements. Methods with a variable number of arguments must be at
        // the end. Reason: there could be one method without parameters and one
        // with a variable number. The one without parameters needs to be used
        // if no parameters are given.
        Arrays.sort(javaMethods);
    }

    private String getMethodSignature(Method m) {
        StatementBuilder buff = new StatementBuilder(m.getName());
        buff.append('(');
        for (Class<?> p : m.getParameterTypes()) {
            buff.appendExceptFirst(", ");
            if (p.isArray()) {
                buff.append(p.getComponentType().getName()).append("[]");
            } else {
                buff.append(p.getName());
            }
        }
        return buff.append(')').toString();
    }

    public String getCreateSQLForCopy(Table table, String quotedName) {
        throw Message.throwInternalError();
    }

    public String getDropSQL() {
        return "DROP ALIAS IF EXISTS " + getSQL();
    }

    public String getCreateSQL() {
        StringBuilder buff = new StringBuilder("CREATE FORCE ALIAS ");
        buff.append(getSQL());
        if (deterministic) {
            buff.append(" DETERMINISTIC");
        }
        if (source != null) {
            buff.append(" AS ").append(StringUtils.quoteStringSQL(source));
        } else {
            buff.append(" FOR ").append(Parser.quoteIdentifier(className + "." + methodName));
        }
        return buff.toString();
    }

    public int getType() {
        return DbObject.FUNCTION_ALIAS;
    }

    public synchronized void removeChildrenAndResources(Session session) throws SQLException {
        database.removeMeta(session, getId());
        className = null;
        methodName = null;
        javaMethods = null;
        invalidate();
    }

    public void checkRename() throws SQLException {
        throw Message.getUnsupportedException("RENAME");
    }

    /**
     * Find the Java method that matches the arguments.
     *
     * @param args the argument list
     * @return the Java method
     * @throws SQLException if no matching method could be found
     */
    public JavaMethod findJavaMethod(Expression[] args) throws SQLException {
        load();
        int parameterCount = args.length;
        for (JavaMethod m : javaMethods) {
            int count = m.getParameterCount();
            if (count == parameterCount || (m.isVarArgs() && count <= parameterCount + 1)) {
                return m;
            }
        }
        throw Message.getSQLException(ErrorCode.METHOD_NOT_FOUND_1, methodName + " (" + className + ", parameter count: " + parameterCount + ")");
    }

    public String getJavaClassName() {
        return this.className;
    }

    public String getJavaMethodName() {
        return this.methodName;
    }

    /**
     * Get the Java methods mapped by this function.
     *
     * @return the Java methods.
     */
    public JavaMethod[] getJavaMethods() throws SQLException {
        load();
        return javaMethods;
    }

    /**
     * There may be multiple Java methods that match a function name.
     * Each method must have a different number of parameters however.
     * This helper class represents one such method.
     */
    public static class JavaMethod implements Comparable<JavaMethod> {
        private final int id;
        private final Method method;
        private final int dataType;
        private boolean hasConnectionParam;
        private boolean varArgs;
        private Class<?> varArgClass;
        private int paramCount;

        JavaMethod(Method method, int id) throws SQLException {
            this.method = method;
            this.id = id;
            Class<?>[] paramClasses = method.getParameterTypes();
            paramCount = paramClasses.length;
            if (paramCount > 0) {
                Class<?> paramClass = paramClasses[0];
                if (Connection.class.isAssignableFrom(paramClass)) {
                    hasConnectionParam = true;
                    paramCount--;
                }
            }
            if (paramCount > 0) {
                Class<?> lastArg = paramClasses[paramClasses.length - 1];
                if (lastArg.isArray() && ClassUtils.isVarArgs(method)) {
                    varArgs = true;
                    varArgClass = lastArg.getComponentType();
                }
            }
            Class<?> returnClass = method.getReturnType();
            dataType = DataType.getTypeFromClass(returnClass);
        }

        public String toString() {
            return method.toString();
        }

        /**
         * Check if this function requires a database connection.
         *
         * @return if the function requires a connection
         */
        public boolean hasConnectionParam() {
            return this.hasConnectionParam;
        }

        /**
         * Call the user-defined function and return the value.
         *
         * @param session    the session
         * @param args       the argument list
         * @param columnList true if the function should only return the column list
         * @return the value
         */
        public Value getValue(Session session, Expression[] args, boolean columnList) throws SQLException {
            Class<?>[] paramClasses = method.getParameterTypes();
            Object[] params = new Object[paramClasses.length];
            int p = 0;
            if (hasConnectionParam && params.length > 0) {
                params[p++] = session.createConnection(columnList);
            }

            // allocate array for varArgs parameters
            Object varArg = null;
            if (varArgs) {
                int len = args.length - params.length + 1 + (hasConnectionParam ? 1 : 0);
                varArg = Array.newInstance(varArgClass, len);
                params[params.length - 1] = varArg;
            }

            for (int a = 0; a < args.length; a++, p++) {
                boolean currentIsVarArg = varArgs && p >= paramClasses.length - 1;
                Class<?> paramClass;
                if (currentIsVarArg) {
                    paramClass = varArgClass;
                } else {
                    paramClass = paramClasses[p];
                }
                int type = DataType.getTypeFromClass(paramClass);
                Value v = args[a].getValue(session);
                v = v.convertTo(type);
                Object o = v.getObject();
                if (o == null) {
                    if (paramClass.isPrimitive()) {
                        if (columnList) {
                            // if the column list is requested, the parameters may
                            // be null
                            // need to set to default value otherwise the function
                            // can't be called at all
                            o = DataType.getDefaultForPrimitiveType(paramClass);
                        } else {
                            // NULL for a java primitive: return NULL
                            return ValueNull.INSTANCE;
                        }
                    }
                } else {
                    if (!paramClass.isAssignableFrom(o.getClass()) && !paramClass.isPrimitive()) {
                        o = DataType.convertTo(session, session.createConnection(false), v, paramClass);
                    }
                }
                if (currentIsVarArg) {
                    Array.set(varArg, p - params.length + 1, o);
                } else {
                    params[p] = o;
                }
            }
            boolean old = session.getAutoCommit();
            Value identity = session.getScopeIdentity();
            try {
                session.setAutoCommit(false);
                try {
                    Object returnValue;
                    returnValue = method.invoke(null, params);
                    if (returnValue == null) {
                        return ValueNull.INSTANCE;
                    }
                    Value ret = DataType.convertToValue(session, returnValue, dataType);
                    return ret.convertTo(dataType);
                } catch (Exception e) {
                    throw Message.convert(e);
                }
            } finally {
                session.setScopeIdentity(identity);
                session.setAutoCommit(old);
            }
        }

        public Class<?>[] getColumnClasses() {
            return method.getParameterTypes();
        }

        public int getDataType() {
            return dataType;
        }

        public int getParameterCount() {
            return paramCount;
        }

        public boolean isVarArgs() {
            return varArgs;
        }

        public int compareTo(JavaMethod m) {
            if (varArgs != m.varArgs) {
                return varArgs ? 1 : -1;
            }
            if (paramCount != m.paramCount) {
                return paramCount - m.paramCount;
            }
            if (hasConnectionParam != m.hasConnectionParam) {
                return hasConnectionParam ? 1 : -1;
            }
            return id - m.id;
        }

    }

    public void setDeterministic(boolean deterministic) {
        this.deterministic = deterministic;
    }

    public boolean isDeterministic() {
        return deterministic;
    }

    public String getSource() {
        return source;
    }

}
