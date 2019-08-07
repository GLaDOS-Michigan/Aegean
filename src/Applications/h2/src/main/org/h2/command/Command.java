/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.command;

import org.h2.constant.ErrorCode;
import org.h2.engine.Constants;
import org.h2.engine.Database;
import org.h2.engine.Session;
import org.h2.expression.ParameterInterface;
import org.h2.message.Message;
import org.h2.message.Trace;
import org.h2.message.TraceObject;
import org.h2.result.ResultInterface;
import org.h2.util.ObjectArray;

import java.sql.SQLException;

/**
 * Represents a SQL statement. This object is only used on the server side.
 */
public abstract class Command implements CommandInterface {

    /**
     * The session.
     */
    protected final Session session;

    /**
     * The trace module.
     */
    protected final Trace trace;

    /**
     * The last start time.
     */
    protected long startTime;

    /**
     * If this query was canceled.
     */
    private volatile boolean cancel;

    private final String sql;

    public Command(Parser parser, String sql) {
        this.session = parser.getSession();
        this.sql = sql;
        trace = session.getDatabase().getTrace(Trace.COMMAND);
    }

    /**
     * Check if this command is transactional.
     * If it is not, then it forces the current transaction to commit.
     *
     * @return true if it is
     */
    public abstract boolean isTransactional();

    /**
     * Check if this command is a query.
     *
     * @return true if it is
     */
    public abstract boolean isQuery();

    /**
     * Get the list of parameters.
     *
     * @return the list of parameters
     */
    public abstract ObjectArray<? extends ParameterInterface> getParameters();

    /**
     * Check if this command is read only.
     *
     * @return true if it is
     */
    public abstract boolean isReadOnly();

    /**
     * Get an empty result set containing the meta data.
     *
     * @return an empty result set
     */
    public abstract ResultInterface queryMeta() throws SQLException;

    /**
     * Execute an updating statement, if this is possible.
     *
     * @return the update count
     * @throws SQLException if the command is not an updating statement
     */
    public int update() throws SQLException {
        throw Message.getSQLException(ErrorCode.METHOD_NOT_ALLOWED_FOR_QUERY);
    }

    /**
     * Execute a query statement, if this is possible.
     *
     * @param maxrows the maximum number of rows returned
     * @return the local result set
     * @throws SQLException if the command is not a query
     */
    public ResultInterface query(int maxrows) throws SQLException {
        throw Message.getSQLException(ErrorCode.METHOD_ONLY_ALLOWED_FOR_QUERY);
    }

    public final ResultInterface getMetaData() throws SQLException {
        return queryMeta();
    }

    /**
     * Execute a query and return the result.
     * This method prepares everything and calls {@link #query(int)} finally.
     *
     * @param maxrows    the maximum number of rows to return
     * @param scrollable if the result set must be scrollable (ignored)
     * @return the result set
     */
    public ResultInterface executeQuery(int maxrows, boolean scrollable) throws SQLException {
        startTime = System.currentTimeMillis();
        Database database = session.getDatabase();
        Object sync = database.isMultiThreaded() ? (Object) session : (Object) database;
        session.waitIfExclusiveModeEnabled();
        synchronized (sync) {
            try {
                database.checkPowerOff();
                session.setCurrentCommand(this, startTime);
                return query(maxrows);
            } catch (Exception e) {
                SQLException s = Message.convert(e, sql);
                database.exceptionThrown(s, sql);
                throw s;
            } finally {
                stop();
            }
        }
    }

    /**
     * Start the stopwatch.
     */
    void start() {
        startTime = System.currentTimeMillis();
    }

    /**
     * Check if this command has been canceled, and throw an exception if yes.
     *
     * @throws SQLException if the statement has been canceled
     */
    public void checkCanceled() throws SQLException {
        if (cancel) {
            cancel = false;
            throw Message.getSQLException(ErrorCode.STATEMENT_WAS_CANCELED);
        }
    }

    private void stop() throws SQLException {
        session.closeTemporaryResults();
        session.setCurrentCommand(null, 0);
        if (!isTransactional()) {
            session.commit(true);
        } else if (session.getAutoCommit()) {
            session.commit(false);
        } else if (session.getDatabase().isMultiThreaded()) {
            Database db = session.getDatabase();
            if (db != null) {
                if (db.getLockMode() == Constants.LOCK_MODE_READ_COMMITTED) {
                    session.unlockReadLocks();
                }
            }
        }
        // TODO: added != null test for Merkle Trees
        if (trace != null && trace.isInfoEnabled()) {
            long time = System.currentTimeMillis() - startTime;
            if (time > Constants.SLOW_QUERY_LIMIT_MS) {
                trace.info("slow query: " + time);
            }
        }
    }

    public int executeUpdate() throws SQLException {
        long start = startTime = System.currentTimeMillis();
        Database database = session.getDatabase();
        Object sync = database.isMultiThreaded() ? (Object) session : (Object) database;
        session.waitIfExclusiveModeEnabled();
        boolean callStop = true;
        session.getDatabase().beforeWriting();
        synchronized (sync) {
            int rollback = session.getLogId();
            session.setCurrentCommand(this, startTime);
            try {
                while (true) {
                    database.checkPowerOff();
                    try {
                        return update();
                    } catch (SQLException e) {
                        if (e.getErrorCode() == ErrorCode.CONCURRENT_UPDATE_1) {
                            long now = System.currentTimeMillis();
                            if (now - start > session.getLockTimeout()) {
                                throw Message.getSQLException(ErrorCode.LOCK_TIMEOUT_1, e, "");
                            }
                            try {
                                if (sync == database) {
                                    database.wait(10);
                                } else {
                                    Thread.sleep(10);
                                }
                            } catch (InterruptedException e1) {
                                // ignore
                            }
                            continue;
                        }
                        throw e;
                    } catch (Exception e) {
                        throw Message.convert(e);
                    } catch (Throwable e) {
                        throw Message.convertThrowable(e);
                    }
                }
            } catch (SQLException e) {
                Message.addSQL(e, sql);
                database.exceptionThrown(e, sql);
                database.checkPowerOff();
                if (e.getErrorCode() == ErrorCode.DEADLOCK_1) {
                    session.rollback();
                } else if (e.getErrorCode() == ErrorCode.OUT_OF_MEMORY) {
                    // there is a serious problem:
                    // the transaction may be applied partially
                    // in this case we need to panic:
                    // close the database
                    callStop = false;
                    session.getDatabase().shutdownImmediately();
                    throw e;
                } else {
                    session.rollbackTo(rollback, false);
                }
                throw e;
            } finally {
                try {
                    if (callStop) {
                        stop();
                    }
                } finally {
                    session.getDatabase().afterWriting();
                }
            }
        }
    }

    public void close() {
        // nothing to do
    }

    public void cancel() {
        this.cancel = true;
    }

    public String toString() {
        return TraceObject.toString(sql, getParameters());
    }
}
