/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.engine;

import merkle.MerkleTreeInstance;
import merkle.wrapper.MTMapWrapper;
import org.h2.command.Command;
import org.h2.command.CommandInterface;
import org.h2.command.Parser;
import org.h2.command.Prepared;
import org.h2.command.dml.SetTypes;
import org.h2.constant.ErrorCode;
import org.h2.constant.SysProperties;
import org.h2.constraint.Constraint;
import org.h2.index.Index;
import org.h2.jdbc.JdbcConnection;
import org.h2.log.InDoubtTransaction;
import org.h2.log.LogSystem;
import org.h2.log.UndoLog;
import org.h2.log.UndoLogRecord;
import org.h2.message.Message;
import org.h2.message.Trace;
import org.h2.message.TraceSystem;
import org.h2.result.ResultInterface;
import org.h2.result.Row;
import org.h2.schema.Schema;
import org.h2.store.DataHandler;
import org.h2.table.Table;
import org.h2.util.New;
import org.h2.util.ObjectArray;
import org.h2.value.*;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;

/**
 * A session represents an embedded database connection. When using the server
 * mode, this object resides on the server side and communicates with a
 * SessionRemote object on the client side.
 */
public class Session extends SessionWithState {

    /**
     * The prefix of generated identifiers. It may not have letters, because
     * they are case sensitive.
     */
    private transient static final String SYSTEM_IDENTIFIER_PREFIX = "_";
    private transient static int nextSerialId;

    private final int serialId = nextSerialId++;
    private Database database;
    private ConnectionInfo connectionInfo;
    private User user;
    private int id;
    private transient ObjectArray<Table> locks = ObjectArray.newInstance(false);
    private UndoLog undoLog;
    private boolean autoCommit = true;
    private transient Random random;
    private transient LogSystem logSystem;
    private int lockTimeout;
    private transient Value lastIdentity = ValueLong.get(0);
    private transient Value scopeIdentity = ValueLong.get(0);
    private int firstUncommittedLog = LogSystem.LOG_WRITTEN;
    private int firstUncommittedPos = LogSystem.LOG_WRITTEN;
    private transient HashMap<String, Integer> savepoints;
    private transient Exception stackTrace = new Exception();
    private transient HashMap<String, Table> localTempTables;
    private MTMapWrapper localTempTableIndexes;
    private MTMapWrapper localTempTableConstraints;
    private int throttle;
    private long lastThrottle;
    private Command currentCommand;
    private boolean allowLiterals;
    private String currentSchemaName;
    private String[] schemaSearchPath;
    private transient String traceModuleName;
    private transient HashMap<String, ValueLob> unlinkMap;
    private int systemIdentifier;
    private transient HashMap<String, Procedure> procedures;
    private boolean undoLogEnabled = true;
    private boolean redoLogBinary = true;
    private boolean autoCommitAtTransactionEnd;
    private transient String currentTransactionName;
    private transient volatile long cancelAt;
    private boolean closed;
    // private long sessionStart = System.currentTimeMillis();
    // Modified by Yang for determinism
    private long sessionStart = 0;
    private long currentCommandStart;
    private transient HashMap<String, Value> variables;
    private transient HashSet<ResultInterface> temporaryResults;
    private int queryTimeout = SysProperties.getMaxQueryTimeout();
    private int lastUncommittedDelete;
    private boolean commitOrRollbackDisabled;
    private Table waitForLock;
    private int modificationId;
    private int modificationIdState;
    private int objectId;

    public Session() {

    }

    public Session(Database database, User user, int id) {
        MerkleTreeInstance.add(this);
        this.database = database;
        this.undoLog = new UndoLog(this);
        this.user = user;
        this.id = id;
        this.logSystem = database.getLog();
        Setting setting = database.findSetting(SetTypes
                .getTypeName(SetTypes.DEFAULT_LOCK_TIMEOUT));
        this.lockTimeout = setting == null ? Constants.INITIAL_LOCK_TIMEOUT
                : setting.getIntValue();
        this.currentSchemaName = Constants.SCHEMA_MAIN;
        MerkleTreeInstance.addStatic(Constants.class);
        MerkleTreeInstance.add(Constants.BUILD_DATE);
        MerkleTreeInstance.add(Constants.BUILD_DATE_STABLE);
        // TODO: this constant string is null!
        // MerkleTreeInstance.add(Constants.BUILD_VENDOR_AND_VERSION);
        MerkleTreeInstance.add(Constants.CHARACTER_SET_NAME);
        MerkleTreeInstance.add(Constants.CLUSTERING_DISABLED);
        MerkleTreeInstance.add(Constants.CONN_URL_COLUMNLIST);
        MerkleTreeInstance.add(Constants.CONN_URL_INTERNAL);
        MerkleTreeInstance.add(Constants.DBA_NAME);
        MerkleTreeInstance.add(Constants.DRIVER_NAME);
        MerkleTreeInstance.add(Constants.MAGIC_FILE_HEADER);
        MerkleTreeInstance.add(Constants.MANAGEMENT_DB_PREFIX);
        MerkleTreeInstance.add(Constants.MANAGEMENT_DB_USER);
        MerkleTreeInstance.add(Constants.PREFIX_INDEX);
        MerkleTreeInstance.add(Constants.PREFIX_PRIMARY_KEY);
        MerkleTreeInstance.add(Constants.PRODUCT_NAME);
        MerkleTreeInstance.add(Constants.PUBLIC_ROLE_NAME);
        MerkleTreeInstance.add(Constants.SCHEMA_INFORMATION);
        // MerkleTreeInstance.add(Constants.SCHEMA_MAIN);
        MerkleTreeInstance.add(Constants.SCRIPT_SQL);
        MerkleTreeInstance.add(Constants.SERVER_PROPERTIES_FILE);
        MerkleTreeInstance.add(Constants.SERVER_PROPERTIES_TITLE);
        MerkleTreeInstance.add(Constants.START_URL);
        MerkleTreeInstance.add(Constants.SUFFIX_DATA_FILE);
        MerkleTreeInstance.add(Constants.SUFFIX_DB_FILE);
        MerkleTreeInstance.add(Constants.SUFFIX_INDEX_FILE);
        MerkleTreeInstance.add(Constants.SUFFIX_LOB_FILE);
        MerkleTreeInstance.add(Constants.SUFFIX_LOBS_DIRECTORY);
        MerkleTreeInstance.add(Constants.SUFFIX_LOCK_FILE);
        MerkleTreeInstance.add(Constants.SUFFIX_LOG_FILE);
        MerkleTreeInstance.add(Constants.SUFFIX_PAGE_FILE);
        MerkleTreeInstance.add(Constants.SUFFIX_TEMP_FILE);
        MerkleTreeInstance.add(Constants.SUFFIX_TRACE_FILE);
        MerkleTreeInstance.add(Constants.TEMP_TABLE_PREFIX);
        MerkleTreeInstance.add(Constants.URL_FORMAT);
        MerkleTreeInstance.add(Constants.USER_PACKAGE);
        MerkleTreeInstance.add(Constants.UTF8);
    }

    public boolean setCommitOrRollbackDisabled(boolean x) {
        boolean old = commitOrRollbackDisabled;
        commitOrRollbackDisabled = x;
        return old;
    }

    private void initVariables() {
        if (variables == null) {
            variables = New.hashMap();
        }
    }

    /**
     * Set the value of the given variable for this session.
     *
     * @param name  the name of the variable (may not be null)
     * @param value the new value (may not be null)
     */
    public void setVariable(String name, Value value) throws SQLException {
        initVariables();
        modificationId++;
        Value old;
        if (value == ValueNull.INSTANCE) {
            old = variables.remove(name);
        } else {
            if (value instanceof ValueLob) {
                // link it, to make sure we have our own file
                value = value
                        .link(database, ValueLob.TABLE_ID_SESSION_VARIABLE);
            }
            old = variables.put(name, value);
        }
        if (old != null) {
            // close the old value (in case it is a lob)
            old.unlink();
            old.close();
        }
    }

    /**
     * Get the value of the specified user defined variable. This method always
     * returns a value; it returns ValueNull.INSTANCE if the variable doesn't
     * exist.
     *
     * @param name the variable name
     * @return the value, or NULL
     */
    public Value getVariable(String name) {
        initVariables();
        Value v = variables.get(name);
        return v == null ? ValueNull.INSTANCE : v;
    }

    /**
     * Get the list of variable names that are set for this session.
     *
     * @return the list of names
     */
    public String[] getVariableNames() {
        if (variables == null) {
            return new String[0];
        }
        String[] list = new String[variables.size()];
        variables.keySet().toArray(list);
        return list;
    }

    /**
     * Get the local temporary table if one exists with that name, or null if
     * not.
     *
     * @param name the table name
     * @return the table, or null
     */
    public Table findLocalTempTable(String name) {
        if (localTempTables == null) {
            return null;
        }
        return localTempTables.get(name);
    }

    public ObjectArray<Table> getLocalTempTables() {
        if (localTempTables == null) {
            return ObjectArray.newInstance(false);
        }
        return ObjectArray.newInstance(false, localTempTables.values());
    }

    /**
     * Add a local temporary table to this session.
     *
     * @param table the table to add
     * @throws SQLException if a table with this name already exists
     */
    public void addLocalTempTable(Table table) throws SQLException {
        if (localTempTables == null) {
            localTempTables = New.hashMap();
        }
        if (localTempTables.get(table.getName()) != null) {
            throw Message.getSQLException(
                    ErrorCode.TABLE_OR_VIEW_ALREADY_EXISTS_1, table.getSQL());
        }
        modificationId++;
        localTempTables.put(table.getName(), table);
    }

    /**
     * Drop and remove the given local temporary table from this session.
     *
     * @param table the table
     */
    public void removeLocalTempTable(Table table) throws SQLException {
        modificationId++;
        localTempTables.remove(table.getName());
        synchronized (database) {
            table.removeChildrenAndResources(this);
        }
    }

    /**
     * Get the local temporary index if one exists with that name, or null if
     * not.
     *
     * @param name the table name
     * @return the table, or null
     */
    public Index findLocalTempTableIndex(String name) {
        if (localTempTableIndexes == null) {
            return null;
        }
        return (Index) localTempTableIndexes.get(name);
    }

    // public HashMap<String, Index> getLocalTempTableIndexes()
    public MTMapWrapper getLocalTempTableIndexes() {
        if (localTempTableIndexes == null) {
            MTMapWrapper toRet = new MTMapWrapper(new HashMap<String, Index>(),
                    false, true, false);
            MerkleTreeInstance.add(toRet);
            return toRet;
        }
        return localTempTableIndexes;
    }

    /**
     * Add a local temporary index to this session.
     *
     * @param index the index to add
     * @throws SQLException if a index with this name already exists
     */
    public void addLocalTempTableIndex(Index index) throws SQLException {
        if (localTempTableIndexes == null) {
            localTempTableIndexes = new MTMapWrapper(
                    new HashMap<String, Index>(), false, true, false);
            MerkleTreeInstance.add(localTempTableIndexes);
        }
        if (localTempTableIndexes.get(index.getName()) != null) {
            throw Message.getSQLException(ErrorCode.INDEX_ALREADY_EXISTS_1,
                    index.getSQL());
        }
        localTempTableIndexes.put(index.getName(), index);
    }

    /**
     * Drop and remove the given local temporary index from this session.
     *
     * @param index the index
     */
    public void removeLocalTempTableIndex(Index index) throws SQLException {
        if (localTempTableIndexes != null) {
            localTempTableIndexes.remove(index.getName());
            synchronized (database) {
                index.removeChildrenAndResources(this);
            }
        }
    }

    /**
     * Get the local temporary constraint if one exists with that name, or null
     * if not.
     *
     * @param name the constraint name
     * @return the constraint, or null
     */
    public Constraint findLocalTempTableConstraint(String name) {
        if (localTempTableConstraints == null) {
            return null;
        }
        return (Constraint) localTempTableConstraints.get(name);
    }

    /**
     * Get the map of constraints for all constraints on local, temporary
     * tables, if any. The map's keys are the constraints' names.
     *
     * @return the map of constraints, or null
     */
    public MTMapWrapper getLocalTempTableConstraints() {
        if (localTempTableConstraints == null) {
            MTMapWrapper toRet = new MTMapWrapper(
                    new HashMap<String, Constraint>(), false, true, false);
            MerkleTreeInstance.add(toRet);
            return toRet;
        }
        return localTempTableConstraints;
    }

    /**
     * Add a local temporary constraint to this session.
     *
     * @param constraint the constraint to add
     * @throws SQLException if a constraint with the same name already exists
     */
    public void addLocalTempTableConstraint(Constraint constraint)
            throws SQLException {
        if (localTempTableConstraints == null) {
            localTempTableConstraints = new MTMapWrapper(
                    new HashMap<String, Constraint>(), false, true, false);
            MerkleTreeInstance.add(localTempTableConstraints);
        }
        String name = constraint.getName();
        if (localTempTableConstraints.get(name) != null) {
            throw Message.getSQLException(
                    ErrorCode.CONSTRAINT_ALREADY_EXISTS_1, constraint.getSQL());
        }
        localTempTableConstraints.put(name, constraint);
    }

    /**
     * Drop and remove the given local temporary constraint from this session.
     *
     * @param constraint the constraint
     */
    public void removeLocalTempTableConstraint(Constraint constraint)
            throws SQLException {
        if (localTempTableConstraints != null) {
            localTempTableConstraints.remove(constraint.getName());
            synchronized (database) {
                constraint.removeChildrenAndResources(this);
            }
        }
    }

    protected void finalize() {
        if (!SysProperties.runFinalize) {
            return;
        }
        if (!closed) {
            throw Message.getInternalError("not closed", stackTrace);
        }
    }

    public boolean getAutoCommit() {
        return autoCommit;
    }

    public User getUser() {
        return user;
    }

    /**
     * Change the autocommit setting for this session.
     *
     * @param b the new value
     */
    public void setAutoCommit(boolean b) {
        autoCommit = b;
    }

    public int getLockTimeout() {
        return lockTimeout;
    }

    public void setLockTimeout(int lockTimeout) {
        this.lockTimeout = lockTimeout;
    }

    public CommandInterface prepareCommand(String sql, int fetchSize)
            throws SQLException {
        return prepareLocal(sql);
    }

    /**
     * Parse and prepare the given SQL statement. This method also checks the
     * rights.
     *
     * @param sql the SQL statement
     * @return the prepared statement
     */
    public Prepared prepare(String sql) throws SQLException {
        return prepare(sql, false);
    }

    /**
     * Parse and prepare the given SQL statement.
     *
     * @param sql           the SQL statement
     * @param rightsChecked true if the rights have already been checked
     * @return the prepared statement
     */
    public Prepared prepare(String sql, boolean rightsChecked)
            throws SQLException {
        Parser parser = new Parser(this);
        parser.setRightsChecked(rightsChecked);
        return parser.prepare(sql);
    }

    /**
     * Parse and prepare the given SQL statement. This method also checks if the
     * connection has been closed.
     *
     * @param sql the SQL statement
     * @return the prepared statement
     */
    public Command prepareLocal(String sql) throws SQLException {
        if (closed) {
            throw Message.getSQLException(ErrorCode.CONNECTION_BROKEN_1,
                    "session closed");
        }
        Parser parser = new Parser(this);
        return parser.prepareCommand(sql);
    }

    public Database getDatabase() {
        return database;
    }

    public int getPowerOffCount() {
        return database.getPowerOffCount();
    }

    public void setPowerOffCount(int count) {
        database.setPowerOffCount(count);
    }

    public int getLastUncommittedDelete() {
        return lastUncommittedDelete;
    }

    public void setLastUncommittedDelete(int deleteId) {
        lastUncommittedDelete = deleteId;
    }

    /**
     * Commit the current transaction. If the statement was not a data
     * definition statement, and if there are temporary tables that should be
     * dropped or truncated at commit, this is done as well.
     *
     * @param ddl if the statement was a data definition statement
     */
    public void commit(boolean ddl) throws SQLException {
        checkCommitRollback();
        //MerkleTreeInstance.update(this);
        lastUncommittedDelete = 0;
        currentTransactionName = null;
        if (containsUncommitted()) {
            // need to commit even if rollback is not possible
            // (create/drop table and so on)
            logSystem.commit(this);
        }
        if (undoLog.size() > 0) {
            // commit the rows, even when not using MVCC
            // see also TableData.addRow
            ArrayList<Row> rows = New.arrayList();
            synchronized (database) {
                while (undoLog.size() > 0) {
                    UndoLogRecord entry = undoLog.getLast();
                    entry.commit();
                    rows.add(entry.getRow());
                    undoLog.removeLast(false);
                }
                for (Row r : rows) {
                    r.commit();
                }
            }
            undoLog.clear();
        }
        if (!ddl) {
            // do not clean the temp tables if the last command was a
            // create/drop
            cleanTempTables(false);
            if (autoCommitAtTransactionEnd) {
                autoCommit = true;
                autoCommitAtTransactionEnd = false;
            }
        }
        if (unlinkMap != null && unlinkMap.size() > 0) {
            // need to flush the log file, because we can't unlink lobs if the
            // commit record is not written
            logSystem.flush();
            for (Value v : unlinkMap.values()) {
                v.unlink();
            }
            unlinkMap = null;
        }
        unlockAll();
    }

    private void checkCommitRollback() throws SQLException {
        if (commitOrRollbackDisabled && locks.size() > 0) {
            throw Message
                    .getSQLException(ErrorCode.COMMIT_ROLLBACK_NOT_ALLOWED);
        }
    }

    /**
     * Fully roll back the current transaction.
     */
    public void rollback() throws SQLException {
        checkCommitRollback();
        currentTransactionName = null;
        boolean needCommit = false;
        if (undoLog.size() > 0) {
            rollbackTo(0, false);
            needCommit = true;
        }
        if (locks.size() > 0 || needCommit) {
            // TODO: this test has been added for Merkle Trees
            if (logSystem != null) {
                logSystem.commit(this);
            }
        }
        cleanTempTables(false);
        unlockAll();
        if (autoCommitAtTransactionEnd) {
            autoCommit = true;
            autoCommitAtTransactionEnd = false;
        }
    }

    /**
     * Partially roll back the current transaction.
     *
     * @param index      the position to which should be rolled back
     * @param trimToSize if the list should be trimmed
     */
    public void rollbackTo(int index, boolean trimToSize) throws SQLException {
        while (undoLog.size() > index) {
            UndoLogRecord entry = undoLog.getLast();
            entry.undo(this);
            undoLog.removeLast(trimToSize);
        }
        if (savepoints != null) {
            String[] names = new String[savepoints.size()];
            savepoints.keySet().toArray(names);
            for (String name : names) {
                Integer savepointIndex = savepoints.get(name);
                if (savepointIndex.intValue() > index) {
                    savepoints.remove(name);
                }
            }
        }
    }

    public int getLogId() {
        return undoLog.size();
    }

    public int getId() {
        return id;
    }

    public void cancel() {
        cancelAt = System.currentTimeMillis();
    }

    public void close() throws SQLException {
        if (!closed) {
            try {
                database.checkPowerOff();
                cleanTempTables(true);
                database.removeSession(this);
            } finally {
                closed = true;
            }
        }
    }

    /**
     * Add a lock for the given table. The object is unlocked on commit or
     * rollback.
     *
     * @param table the table that is locked
     */
    public void addLock(Table table) {
        if (SysProperties.CHECK) {
            if (locks.indexOf(table) >= 0) {
                Message.throwInternalError();
            }
        }
        locks.add(table);
    }

    /**
     * Add an undo log entry to this session.
     *
     * @param table the table
     * @param type  the operation type (see {@link UndoLogRecord})
     * @param row   the row
     */
    public void log(Table table, short type, Row row) throws SQLException {
        log(new UndoLogRecord(table, type, row));
    }

    private void log(UndoLogRecord log) throws SQLException {
        // called _after_ the row was inserted successfully into the table,
        // otherwise rollback will try to rollback a not-inserted row
        if (SysProperties.CHECK) {
            int lockMode = database.getLockMode();
            if (lockMode != Constants.LOCK_MODE_OFF
                    && !database.isMultiVersion()) {
                if (locks.indexOf(log.getTable()) < 0
                        && !Table.TABLE_LINK.equals(log.getTable()
                        .getTableType())) {
                    Message.throwInternalError();
                }
            }
        }
        if (undoLogEnabled) {
            undoLog.add(log);
        } else {
            log.commit();
            log.getRow().commit();
        }
    }

    /**
     * Unlock all read locks. This is done if the transaction isolation mode is
     * READ_COMMITTED.
     */
    public void unlockReadLocks() {
        if (database.isMultiVersion()) {
            // MVCC: keep shared locks (insert / update / delete)
            return;
        }
        for (int i = 0; i < locks.size(); i++) {
            Table t = locks.get(i);
            if (!t.isLockedExclusively()) {
                synchronized (database) {
                    t.unlock(this);
                    locks.remove(i);
                }
                i--;
            }
        }
    }

    private void unlockAll() {
        //MerkleTreeInstance.update(this);
        if (SysProperties.CHECK) {
            if (undoLog.size() > 0) {
                Message.throwInternalError();
            }
        }
        if (locks.size() > 0) {
            synchronized (database) {
                for (Table t : locks) {
                    t.unlock(this);
                }
                locks.clear();
            }
        }
        savepoints = null;

        if (modificationIdState != modificationId) {
            sessionStateChanged = true;
        }
    }

    private void cleanTempTables(boolean closeSession) throws SQLException {
        if (localTempTables != null && localTempTables.size() > 0) {
            synchronized (database) {
                for (Table table : ObjectArray.newInstance(false,
                        localTempTables.values())) {
                    if (closeSession || table.getOnCommitDrop()) {
                        modificationId++;
                        table.setModified();
                        localTempTables.remove(table.getName());
                        table.removeChildrenAndResources(this);
                    } else if (table.getOnCommitTruncate()) {
                        table.truncate(this);
                    }
                }
            }
        }
    }

    public Random getRandom() {
        if (random == null) {
            // Modified by Yang for determinism
            random = new Random(1234);
            // random = new Random();
        }
        return random;
    }

    public Trace getTrace() {
        if (traceModuleName == null) {
            traceModuleName = Trace.JDBC + "[" + id + "]";
        }
        if (closed) {
            return new TraceSystem(null).getTrace(traceModuleName);
        }
        return database.getTrace(traceModuleName);
    }

    public void setLastIdentity(Value last) {
        this.scopeIdentity = last;
        this.lastIdentity = last;
    }

    public Value getLastIdentity() {
        return lastIdentity;
    }

    /**
     * Called when a log entry for this session is added. The session keeps
     * track of the first entry in the log file that is not yet committed.
     *
     * @param logId the log file id
     * @param pos   the position of the log entry in the log file
     */
    public void addLogPos(int logId, int pos) {
        if (firstUncommittedLog == LogSystem.LOG_WRITTEN) {
            firstUncommittedLog = logId;
            firstUncommittedPos = pos;
        }
    }

    public int getFirstUncommittedLog() {
        return firstUncommittedLog;
    }

    public int getFirstUncommittedPos() {
        return firstUncommittedPos;
    }

    /**
     * This method is called after the log file has committed this session.
     */
    public void setAllCommitted() {
        firstUncommittedLog = LogSystem.LOG_WRITTEN;
        firstUncommittedPos = LogSystem.LOG_WRITTEN;
    }

    private boolean containsUncommitted() {
        return firstUncommittedLog != LogSystem.LOG_WRITTEN;
    }

    /**
     * Create a savepoint that is linked to the current log position.
     *
     * @param name the savepoint name
     */
    public void addSavepoint(String name) {
        if (savepoints == null) {
            savepoints = New.hashMap();
        }
        savepoints.put(name, getLogId());
    }

    /**
     * Undo all operations back to the log position of the given savepoint.
     *
     * @param name the savepoint name
     */
    public void rollbackToSavepoint(String name) throws SQLException {
        checkCommitRollback();
        if (savepoints == null) {
            throw Message.getSQLException(ErrorCode.SAVEPOINT_IS_INVALID_1,
                    name);
        }
        Integer savepointIndex = savepoints.get(name);
        if (savepointIndex == null) {
            throw Message.getSQLException(ErrorCode.SAVEPOINT_IS_INVALID_1,
                    name);
        }
        int i = savepointIndex.intValue();
        rollbackTo(i, false);
    }

    /**
     * Prepare the given transaction.
     *
     * @param transactionName the name of the transaction
     */
    public void prepareCommit(String transactionName) throws SQLException {
        if (containsUncommitted()) {
            // need to commit even if rollback is not possible (create/drop
            // table and so on)
            logSystem.prepareCommit(this, transactionName);
        }
        currentTransactionName = transactionName;
    }

    /**
     * Commit or roll back the given transaction.
     *
     * @param transactionName the name of the transaction
     * @param commit          true for commit, false for rollback
     */
    public void setPreparedTransaction(String transactionName, boolean commit)
            throws SQLException {
        if (currentTransactionName != null
                && currentTransactionName.equals(transactionName)) {
            if (commit) {
                commit(false);
            } else {
                rollback();
            }
        } else {
            ObjectArray<InDoubtTransaction> list = logSystem
                    .getInDoubtTransactions();
            int state = commit ? InDoubtTransaction.COMMIT
                    : InDoubtTransaction.ROLLBACK;
            boolean found = false;
            for (int i = 0; list != null && i < list.size(); i++) {
                InDoubtTransaction p = list.get(i);
                if (p.getTransaction().equals(transactionName)) {
                    p.setState(state);
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw Message.getSQLException(
                        ErrorCode.TRANSACTION_NOT_FOUND_1, transactionName);
            }
        }
    }

    public boolean isClosed() {
        return closed;
    }

    public void setThrottle(int throttle) {
        this.throttle = throttle;
    }

    /**
     * Wait for some time if this session is throttled (slowed down).
     */
    public void throttle() {
        if (throttle == 0) {
            return;
        }
        long time = System.currentTimeMillis();
        if (lastThrottle + Constants.THROTTLE_DELAY > time) {
            return;
        }
        lastThrottle = time + throttle;
        try {
            Thread.sleep(throttle);
        } catch (Exception e) {
            // ignore
        }
    }

    /**
     * Set the current command of this session. This is done just before
     * executing the statement.
     *
     * @param command   the command
     * @param startTime the time execution has been started
     */
    public void setCurrentCommand(Command command, long startTime) {
        // TODO: MT: comment to avoid updates in SELECT
        // MerkleTreeInstance.update(this);
        this.currentCommand = command;
        this.currentCommandStart = startTime;
        if (queryTimeout > 0 && startTime != 0) {
            cancelAt = startTime + queryTimeout;
        }
    }

    /**
     * Check if the current transaction is canceled by calling
     * Statement.cancel() or because a session timeout was set and expired.
     *
     * @throws SQLException if the transaction is canceled
     */
    public void checkCanceled() throws SQLException {
        throttle();
        if (cancelAt == 0) {
            return;
        }
        long time = System.currentTimeMillis();
        if (time >= cancelAt) {
            cancelAt = 0;
            throw Message.getSQLException(ErrorCode.STATEMENT_WAS_CANCELED);
        }
    }

    public Command getCurrentCommand() {
        return currentCommand;
    }

    public long getCurrentCommandStart() {
        return currentCommandStart;
    }

    public boolean getAllowLiterals() {
        return allowLiterals;
    }

    public void setAllowLiterals(boolean b) {
        this.allowLiterals = b;
    }

    public void setCurrentSchema(Schema schema) {
        modificationId++;
        this.currentSchemaName = schema.getName();
    }

    public String getCurrentSchemaName() {
        return currentSchemaName;
    }

    /**
     * Create an internal connection. This connection is used when initializing
     * triggers, and when calling user defined functions.
     *
     * @param columnList if the url should be 'jdbc:columnlist:connection'
     * @return the internal connection
     */
    public JdbcConnection createConnection(boolean columnList) {
        String url;
        if (columnList) {
            url = Constants.CONN_URL_COLUMNLIST;
        } else {
            url = Constants.CONN_URL_INTERNAL;
        }
        return new JdbcConnection(this, getUser().getName(), url);
    }

    public DataHandler getDataHandler() {
        return database;
    }

    /**
     * Remember that the given LOB value must be un-linked (disconnected from
     * the table) at commit.
     *
     * @param v the value
     */
    public void unlinkAtCommit(ValueLob v) {
        if (SysProperties.CHECK && !v.isLinked()) {
            Message.throwInternalError();
        }
        if (unlinkMap == null) {
            unlinkMap = New.hashMap();
        }
        unlinkMap.put(v.toString(), v);
    }

    /**
     * Do not unlink this LOB value at commit any longer.
     *
     * @param v the value
     */
    public void unlinkAtCommitStop(Value v) {
        if (unlinkMap != null) {
            unlinkMap.remove(v.toString());
        }
    }

    /**
     * Get the next system generated identifiers. The identifier returned does
     * not occur within the given SQL statement.
     *
     * @param sql the SQL statement
     * @return the new identifier
     */
    public String getNextSystemIdentifier(String sql) {
        String identifier;
        do {
            identifier = SYSTEM_IDENTIFIER_PREFIX + systemIdentifier++;
        } while (sql.indexOf(identifier) >= 0);
        return identifier;
    }

    /**
     * Add a procedure to this session.
     *
     * @param procedure the procedure to add
     */
    public void addProcedure(Procedure procedure) {
        if (procedures == null) {
            procedures = New.hashMap();
        }
        procedures.put(procedure.getName(), procedure);
    }

    /**
     * Remove a procedure from this session.
     *
     * @param name the name of the procedure to remove
     */
    public void removeProcedure(String name) {
        if (procedures != null) {
            procedures.remove(name);
        }
    }

    /**
     * Get the procedure with the given name, or null if none exists.
     *
     * @param name the procedure name
     * @return the procedure or null
     */
    public Procedure getProcedure(String name) {
        if (procedures == null) {
            return null;
        }
        return procedures.get(name);
    }

    public void setSchemaSearchPath(String[] schemas) {
        modificationId++;
        this.schemaSearchPath = schemas;
    }

    public String[] getSchemaSearchPath() {
        return schemaSearchPath;
    }

    public int hashCode() {
        return serialId;
    }

    public String toString() {
        if (user != null)
            return "#" + serialId + " (user: " + user.getName() + ")";
        else
            return "#" + serialId + " (user: null )";
    }

    public void setUndoLogEnabled(boolean b) {
        this.undoLogEnabled = b;
    }

    public void setRedoLogBinary(boolean b) {
        this.redoLogBinary = b;
    }

    public boolean isUndoLogEnabled() {
        return undoLogEnabled;
    }

    /**
     * Begin a transaction.
     */
    public void begin() {
        autoCommitAtTransactionEnd = true;
        autoCommit = false;
    }

    public long getSessionStart() {
        return sessionStart;
    }

    public Table[] getLocks() {
        synchronized (database) {
            Table[] list = new Table[locks.size()];
            locks.toArray(list);
            return list;
        }
    }

    /**
     * Wait if the exclusive mode has been enabled for another session. This
     * method returns as soon as the exclusive mode has been disabled.
     */
    public void waitIfExclusiveModeEnabled() {
        while (true) {
            Session exclusive = database.getExclusiveSession();
            if (exclusive == null || exclusive == this) {
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    /**
     * Remember the result set and close it as soon as the transaction is
     * committed (if it needs to be closed). This is done to delete temporary
     * files as soon as possible, and free object ids of temporary tables.
     *
     * @param result the temporary result set
     */
    public void addTemporaryResult(ResultInterface result) {
        if (!result.needToClose()) {
            return;
        }
        if (temporaryResults == null) {
            temporaryResults = New.hashSet();
        }
        if (temporaryResults.size() < 100) {
            // reference at most 100 result sets to avoid memory problems
            temporaryResults.add(result);
        }
    }

    /**
     * Close all temporary result set. This also deletes all temporary files
     * held by the result sets.
     */
    public void closeTemporaryResults() {
        if (temporaryResults != null) {
            for (ResultInterface result : temporaryResults) {
                result.close();
            }
            temporaryResults = null;
        }
    }

    public void setQueryTimeout(int queryTimeout) {
        int max = SysProperties.getMaxQueryTimeout();
        if (max != 0 && (max < queryTimeout || queryTimeout == 0)) {
            // the value must be at most max
            queryTimeout = max;
        }
        this.queryTimeout = queryTimeout;
        // must reset the cancel at here,
        // otherwise it is still used
        this.cancelAt = 0;
    }

    public int getQueryTimeout() {
        return queryTimeout;
    }

    public void setWaitForLock(Table table) {
        // TODO: MT: comment to avoid updates in SELECT
        // MerkleTreeInstance.update(this);
        this.waitForLock = table;
    }

    public Table getWaitForLock() {
        return waitForLock;
    }

    public int getModificationId() {
        return modificationId;
    }

    public boolean isReconnectNeeded(boolean write) {
        while (true) {
            boolean reconnect = database.isReconnectNeeded();
            if (reconnect) {
                return true;
            }
            if (write) {
                if (database.beforeWriting()) {
                    return false;
                }
            } else {
                return false;
            }
        }
    }

    public void afterWriting() {
        database.afterWriting();
    }

    public SessionInterface reconnect(boolean write) throws SQLException {
        readSessionState();
        close();
        Session newSession = Engine.getInstance().getSession(connectionInfo);
        newSession.sessionState = sessionState;
        newSession.recreateSessionState();
        if (write) {
            while (!newSession.database.beforeWriting()) {
                // wait until we are allowed to write
            }
        }
        return newSession;
    }

    public void setConnectionInfo(ConnectionInfo ci) {
        connectionInfo = ci;
    }

    public Value getTransactionId() {
        if (undoLog.size() == 0 || !database.isPersistent()) {
            return ValueNull.INSTANCE;
        }
        return ValueString.get(firstUncommittedLog + "-" + firstUncommittedPos
                + "-" + id);
    }

    /**
     * Get the next object id.
     *
     * @return the next object id
     */
    public int nextObjectId() {
        return objectId++;
    }

    public void setScopeIdentity(Value scopeIdentity) {
        this.scopeIdentity = scopeIdentity;
    }

    public Value getScopeIdentity() {
        return scopeIdentity;
    }

    public boolean isRedoLogBinaryEnabled() {
        return redoLogBinary;
    }

}
