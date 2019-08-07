/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.table;

import merkle.MerkleTreeInstance;
import org.h2.api.DatabaseEventListener;
import org.h2.command.ddl.CreateTableData;
import org.h2.constant.ErrorCode;
import org.h2.constant.SysProperties;
import org.h2.constraint.Constraint;
import org.h2.constraint.ConstraintReferential;
import org.h2.engine.Constants;
import org.h2.engine.DbObject;
import org.h2.engine.Session;
import org.h2.index.*;
import org.h2.message.Message;
import org.h2.message.Trace;
import org.h2.result.Row;
import org.h2.result.SortOrder;
import org.h2.schema.SchemaObject;
import org.h2.store.DataPage;
import org.h2.store.Record;
import org.h2.store.RecordReader;
import org.h2.util.*;
import org.h2.value.CompareMode;
import org.h2.value.DataType;
import org.h2.value.Value;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/**
 * Most tables are an instance of this class. For this table, the data is stored
 * in the database. The actual data is not kept here, instead it is kept in the
 * indexes. There is at least one index, the scan index.
 */
public class TableData extends Table implements RecordReader {
    private RowIndex scanIndex;
    private long rowCount;
    private transient volatile Session lockExclusive;
    private transient HashSet<Session> lockShared = New.hashSet();
    private transient Trace traceLock;
    private boolean globalTemporary;
    private final ObjectArray<Index> indexes = ObjectArray.newInstance(true);
    private transient long lastModificationId;
    private boolean containsLargeObject;
    private transient PageDataIndex mainIndex;

    /**
     * True if one thread ever was waiting to lock this table. This is to avoid
     * calling notifyAll if no session was ever waiting to lock this table. If
     * set, the flag stays. In theory, it could be reset, however not sure when.
     */
    private transient boolean waitForLock;

    public TableData() {

    }

    public TableData(CreateTableData data) throws SQLException {
        super(data.schema, data.id, data.tableName, data.persistIndexes,
                data.persistData);
        Column[] cols = new Column[data.columns.size()];
        MerkleTreeInstance.add(cols);
        MerkleTreeInstance.update(cols);
        data.columns.toArray(cols);
        // for (int i = 0; i < cols.length; i++)
        // {
        // MerkleTreeInstance.add(cols[i]);
        // }
        setColumns(cols);
        setTemporary(data.temporary);
        if (database.isPageStoreEnabled() && data.persistData
                && database.isPersistent()) {
            mainIndex = new PageDataIndex(this, data.id,
                    IndexColumn.wrap(cols), IndexType
                    .createScan(data.persistData), data.headPos,
                    data.session);
            scanIndex = mainIndex;
        } else {
            scanIndex = new ScanIndex(this, data.id, IndexColumn.wrap(cols),
                    IndexType.createScan(data.persistData));
        }
        indexes.add(scanIndex);
        for (Column col : cols) {
            if (DataType.isLargeObject(col.getType())) {
                containsLargeObject = true;
                memoryPerRow = Row.MEMORY_CALCULATE;
            }
        }
        traceLock = database.getTrace(Trace.LOCK);
    }

    public int getHeadPos() {
        return scanIndex.getHeadPos();
    }

    public void close(Session session) throws SQLException {
        for (Index index : indexes) {
            index.close(session);
        }
    }

    /**
     * Read the given row.
     *
     * @param session the session
     * @param key     unique key
     * @return the row
     */
    public Row getRow(Session session, long key) throws SQLException {
        return scanIndex.getRow(session, key);
    }

    public void rebuildScanIndex() {
        // System.out
        // .println("REBUILD SCAN INDEX IS CALLED!!!!!!!!!!!!!!!!!!!!!!!!");
        TreeIndex index = (TreeIndex) indexes.get(1);

        ((ScanIndex) scanIndex).rows = ObjectArray
                .newInstance(((ScanIndex) scanIndex).inTree);

        ObjectArray<Row> rows = ((ScanIndex) scanIndex).rows;

        // rows = ObjectArray.newInstance(((ScanIndex) scanIndex).inTree);

        try {
            Cursor cursor = index.find(null, null, null);
            ObjectArray<Row> buffer = ObjectArray.newInstance(false,
                    Constants.DEFAULT_MAX_MEMORY_ROWS);
            while (cursor.next()) {
                Row row = cursor.get();
                buffer.add(row);
            }

            buffer.sort(new Comparator<Row>() {
                public int compare(Row r1, Row r2) {
                    if (r1.getKey() == r2.getKey()) {
                        return 0;
                    }
                    return (r1.getKey() < r2.getKey() ? -1 : 1);
                }
            });

            Row previousFree = null;
            int current_key = 0;
            for (Row row : buffer) {
                while (row.getKey() > current_key) {
                    Row free = new Row(true, null, 0);
                    free.setKey(-1);
                    free.setPos(-1);
                    if (previousFree != null) {
                        previousFree.setKey(current_key);
                        previousFree.setPos((int) current_key);
                    }
                    previousFree = free;
                    current_key++;
                    rows.add(free);
                }
                rows.add(row);
                current_key++;
            }
            // If the last lines in the scan index are "empty" lines
            while (((ScanIndex) scanIndex).rowsSize > current_key) {
                Row free = new Row(false, null, 0);
                free.setKey(-1);
                free.setPos(-1);
                if (previousFree != null) {
                    previousFree.setKey(current_key);
                    previousFree.setPos((int) current_key);
                }
                previousFree = free;
                current_key++;
                rows.add(free);
            }

            // COMPARE GENERATED AND EXISTING SCAN_INDEX
            // System.out.println("COMPARING THE SCAN INDICES");
            // int i = 0;
            // for (Row r : rows)
            // {
            // Row r2 = ((ScanIndex) scanIndex).rows.get(i);
            // i++;
            // if (!r.equals(r2) && (r.getKey() != r2.getKey()))
            // {
            // System.out.println("2 ROWS ARE DIFFERENT (index=" + i
            // + ") (first Free="
            // + ((ScanIndex) scanIndex).firstFree + "): " + r
            // + " " + r2);
            // System.out.println("r: " + r.getKey() + " " + r.getPos()
            // + " " + r.getSessionId() + " " + r.getStorageId()
            // + " " + r.getVersion());
            // System.out.println("r2: " + r2.getKey() + " " + r2.getPos()
            // + " " + r2.getSessionId() + " " + r2.getStorageId()
            // + " " + r2.getVersion());
            //
            // System.out.println("DIFF: r.getKey()=" + r.getKey()
            // + " r2.getKey()=" + r2.getKey()
            // + "(number of rows in real index="
            // + ((ScanIndex) scanIndex).rows.size()
            // + ") (number of rows in rebuilt index="
            // + rows.size() + ") (size of the rows array="
            // + ((ScanIndex) scanIndex).rowsSize + ")");
            // if (r.getKey() == -1)
            // {
            // System.out.println("at index "
            // + r2.getKey()
            // + " there is r="
            // + rows.get((int) r2.getKey())
            // + " and r2="
            // + ((ScanIndex) scanIndex).rows.get((int) r2
            // .getKey()));
            // }
            // System.exit(1);
            // }
            // }
            // System.out.println("END COMPARISON (nb rows=" + i + ")");
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public void addRow(Session session, Row row) throws SQLException {
        MerkleTreeInstance.update(this);
        int i = 0;
        lastModificationId = database.getNextModificationDataId();
        // even when not using MVCC
        // set the session, to ensure the row is kept in the cache
        // until the transaction is committed or rolled back
        // otherwise the row is not found when doing insert-delete-rollback
        row.setSessionId(session.getId());
        try {
            for (; i < indexes.size(); i++) {
                Index index = indexes.get(i);
                index.add(session, row);
                checkRowCount(session, index, 1);
            }
            rowCount++;
        } catch (Throwable e) {
            try {
                while (--i >= 0) {
                    Index index = indexes.get(i);
                    index.remove(session, row);
                    checkRowCount(session, index, 0);
                }
            } catch (SQLException e2) {
                // this could happen, for example on failure in the storage
                // but if that is not the case it means there is something wrong
                // with the database
                trace.error("Could not undo operation", e);
                throw e2;
            }
            if (e instanceof Exception) {
                throw Message.convert((Exception) e);
            }
            throw Message.convertThrowable(e);
        }
    }

    private void checkRowCount(Session session, Index index, int offset) {
        if (SysProperties.CHECK && !database.isMultiVersion()) {
            if (!(index instanceof PageDelegateIndex)) {
                long rc = index.getRowCount(session);
                if (rc != rowCount + offset) {
                    Message.throwInternalError("rowCount expected "
                            + (rowCount + offset) + " got " + rc + " "
                            + getName() + "." + index.getName());
                }
            }
        }
    }

    public Index getScanIndex(Session session) {
        return indexes.get(0);
    }

    public Index getUniqueIndex() {
        for (Index idx : indexes) {
            if (idx.getIndexType().isUnique()) {
                return idx;
            }
        }
        return null;
    }

    public ObjectArray<Index> getIndexes() {
        return indexes;
    }

    public Index addIndex(Session session, String indexName, int indexId,
                          IndexColumn[] cols, IndexType indexType, int headPos,
                          String indexComment) throws SQLException {
        MerkleTreeInstance.update(this);
        if (indexType.isPrimaryKey()) {
            for (IndexColumn c : cols) {
                Column column = c.column;
                if (column.isNullable()) {
                    throw Message.getSQLException(
                            ErrorCode.COLUMN_MUST_NOT_BE_NULLABLE_1, column
                                    .getName());
                }
                column.setPrimaryKey(true);
            }
        }
        Index index;
        if (isPersistIndexes() && indexType.isPersistent()) {
            if (database.isPageStoreEnabled()) {
                int mainIndexColumn;
                if (database.isStarting()
                        && database.getPageStore().getRootPageId(indexId) != 0) {
                    mainIndexColumn = -1;
                } else if (!database.isStarting()
                        && mainIndex.getRowCount(session) != 0) {
                    mainIndexColumn = -1;
                } else {
                    mainIndexColumn = getMainIndexColumn(indexType, cols);
                }
                if (mainIndexColumn != -1) {
                    mainIndex.setMainIndexColumn(mainIndexColumn);
                    index = new PageDelegateIndex(this, indexId, indexName,
                            indexType, mainIndex, headPos, session);
                } else {
                    index = new PageBtreeIndex(this, indexId, indexName, cols,
                            indexType, headPos, session);
                }
            } else {
                index = new BtreeIndex(session, this, indexId, indexName, cols,
                        indexType, headPos);
            }
        } else {
            if (indexType.isHash()) {
                if (indexType.isUnique()) {
                    index = new HashIndex(this, indexId, indexName, cols,
                            indexType);
                } else {
                    index = new NonUniqueHashIndex(this, indexId, indexName,
                            cols, indexType);
                }
            } else {
                index = new TreeIndex(this, indexId, indexName, cols, indexType);
            }
        }
        if (database.isMultiVersion()) {
            index = new MultiVersionIndex(index, this);
        }
        if (index.needRebuild() && rowCount > 0) {
            try {
                Index scan = getScanIndex(session);
                long remaining = scan.getRowCount(session);
                long total = remaining;
                Cursor cursor = scan.find(session, null, null);
                long i = 0;
                int bufferSize = Constants.DEFAULT_MAX_MEMORY_ROWS;
                ObjectArray<Row> buffer = ObjectArray.newInstance(false,
                        bufferSize);
                String n = getName() + ":" + index.getName();
                int t = MathUtils.convertLongToInt(total);
                while (cursor.next()) {
                    database.setProgress(
                            DatabaseEventListener.STATE_CREATE_INDEX, n,
                            MathUtils.convertLongToInt(i++), t);
                    Row row = cursor.get();
                    buffer.add(row);
                    if (buffer.size() >= bufferSize) {
                        addRowsToIndex(session, buffer, index);
                    }
                    remaining--;
                }
                addRowsToIndex(session, buffer, index);
                if (SysProperties.CHECK && remaining != 0) {
                    Message.throwInternalError("rowcount remaining="
                            + remaining + " " + getName());
                }
            } catch (SQLException e) {
                getSchema().freeUniqueName(indexName);
                try {
                    index.remove(session);
                } catch (SQLException e2) {
                    // this could happen, for example on failure in the storage
                    // but if that is not the case it means
                    // there is something wrong with the database
                    trace.error("Could not remove index", e);
                    throw e2;
                }
                throw e;
            }
        }
        boolean temporary = isTemporary();
        index.setTemporary(temporary);
        if (index.getCreateSQL() != null) {
            index.setComment(indexComment);
            if (temporary && !isGlobalTemporary()) {
                session.addLocalTempTableIndex(index);
            } else {
                database.addSchemaObject(session, index);
            }
            // must not do this when using the page store
            // because recovery is not done yet
            if (!database.isPageStoreEnabled()) {
                // need to update, because maybe the index is rebuilt at
                // startup,
                // and so the head pos may have changed, which needs to be
                // stored now.
                // addSchemaObject doesn't update the sys table at startup
                if (index.getIndexType().isPersistent()
                        && !database.isReadOnly()
                        && !database.getLog().containsInDoubtTransactions()) {
                    // can not save anything in the log file if it contains
                    // in-doubt transactions
                    database.update(session, index);
                }
            }
        }
        indexes.add(index);
        setModified();
        return index;
    }

    private int getMainIndexColumn(IndexType indexType, IndexColumn[] cols) {
        if (mainIndex.getMainIndexColumn() != -1) {
            return -1;
        }
        if (!indexType.isPrimaryKey() || cols.length != 1) {
            return -1;
        }
        IndexColumn first = cols[0];
        if (first.sortType != SortOrder.ASCENDING) {
            return -1;
        }
        switch (first.column.getType()) {
            case Value.BYTE:
            case Value.SHORT:
            case Value.INT:
            case Value.LONG:
                break;
            default:
                return -1;
        }
        return first.column.getColumnId();
    }

    public boolean canGetRowCount() {
        return true;
    }

    private void addRowsToIndex(Session session, ObjectArray<Row> list,
                                Index index) throws SQLException {
        final Index idx = index;
        try {
            list.sort(new Comparator<Row>() {
                public int compare(Row r1, Row r2) {
                    try {
                        return idx.compareRows(r1, r2);
                    } catch (SQLException e) {
                        throw Message.convertToInternal(e);
                    }
                }
            });
        } catch (Exception e) {
            throw Message.convert(e);
        }
        for (Row row : list) {
            index.add(session, row);
        }
        list.clear();
    }

    public boolean canDrop() {
        return true;
    }

    public long getRowCount(Session session) {
        if (database.isMultiVersion()) {
            return getScanIndex(session).getRowCount(session);
        }
        return rowCount;
    }

    public void removeRow(Session session, Row row) throws SQLException {
        MerkleTreeInstance.update(this);
        if (database.isMultiVersion()) {
            if (row.isDeleted()) {
                throw Message.getSQLException(ErrorCode.CONCURRENT_UPDATE_1,
                        getName());
            }
            int old = row.getSessionId();
            int newId = session.getId();
            if (old == 0) {
                row.setSessionId(newId);
            } else if (old != newId) {
                throw Message.getSQLException(ErrorCode.CONCURRENT_UPDATE_1,
                        getName());
            }
        }
        lastModificationId = database.getNextModificationDataId();
        int i = indexes.size() - 1;
        try {
            for (; i >= 0; i--) {
                Index index = indexes.get(i);
                index.remove(session, row);
                checkRowCount(session, index, -1);
            }
            rowCount--;
        } catch (Throwable e) {
            try {
                while (++i < indexes.size()) {
                    Index index = indexes.get(i);
                    index.add(session, row);
                    checkRowCount(session, index, 0);
                }
            } catch (SQLException e2) {
                // this could happen, for example on failure in the storage
                // but if that is not the case it means there is something wrong
                // with the database
                trace.error("Could not undo operation", e);
                throw e2;
            }
            if (e instanceof Exception) {
                throw Message.convert((Exception) e);
            }
            throw Message.convertThrowable(e);
        }
    }

    public void truncate(Session session) throws SQLException {
        MerkleTreeInstance.update(this);
        lastModificationId = database.getNextModificationDataId();
        for (int i = indexes.size() - 1; i >= 0; i--) {
            Index index = indexes.get(i);
            index.truncate(session);
            if (SysProperties.CHECK) {
                if (!database.isPageStoreEnabled()) {
                    long rc = index.getRowCount(session);
                    if (rc != 0) {
                        Message.throwInternalError("rowCount expected 0 got "
                                + rc);
                    }
                }
            }
        }
        rowCount = 0;
    }

    boolean isLockedExclusivelyBy(Session session) {
        return lockExclusive == session;
    }

    public void lock(Session session, boolean exclusive, boolean force)
            throws SQLException {
        int lockMode = database.getLockMode();
        if (lockMode == Constants.LOCK_MODE_OFF) {
            return;
        }
        if (!force && database.isMultiVersion()) {
            // MVCC: update, delete, and insert use a shared lock.
            // Select doesn't lock
            if (exclusive) {
                exclusive = false;
            } else {
                if (lockExclusive == null) {
                    return;
                }
            }
        }
        if (lockExclusive == session) {
            return;
        }
        synchronized (database) {
            try {
                doLock(session, lockMode, exclusive);
            } finally {
                session.setWaitForLock(null);
            }
        }
    }

    private void doLock(Session session, int lockMode, boolean exclusive)
            throws SQLException {
        traceLock(session, exclusive, "requesting for");
        long max = System.currentTimeMillis() + session.getLockTimeout();
        boolean checkDeadlock = false;
        while (true) {
            if (lockExclusive == session) {
                return;
            }
            if (exclusive) {
                if (lockExclusive == null) {
                    if (lockShared.isEmpty()) {
                        traceLock(session, exclusive, "added for");
                        session.addLock(this);
                        MerkleTreeInstance.update(this);
                        lockExclusive = session;
                        return;
                    } else if (lockShared.size() == 1
                            && lockShared.contains(session)) {
                        traceLock(session, exclusive, "add (upgraded) for ");
                        MerkleTreeInstance.update(this);
                        lockExclusive = session;
                        return;
                    }
                }
            } else {
                if (lockExclusive == null) {
                    if (lockMode == Constants.LOCK_MODE_READ_COMMITTED) {
                        if (!database.isMultiThreaded()
                                && !database.isMultiVersion()) {
                            // READ_COMMITTED: a read lock is acquired,
                            // but released immediately after the operation
                            // is complete.
                            // When allowing only one thread, no lock is
                            // required.
                            // Row level locks work like read committed.
                            return;
                        }
                    }
                    if (!lockShared.contains(session)) {
                        traceLock(session, exclusive, "ok");
                        session.addLock(this);
                        lockShared.add(session);
                    }
                    return;
                }
            }
            session.setWaitForLock(this);
            if (checkDeadlock) {
                ObjectArray<Session> sessions = checkDeadlock(session, null,
                        null);
                if (sessions != null) {
                    throw Message.getSQLException(ErrorCode.DEADLOCK_1,
                            getDeadlockDetails(sessions));
                }
            } else {
                // check for deadlocks from now on
                checkDeadlock = true;
            }
            long now = System.currentTimeMillis();
            if (now >= max) {
                traceLock(session, exclusive, "timeout after "
                        + session.getLockTimeout());
                throw Message.getSQLException(ErrorCode.LOCK_TIMEOUT_1,
                        getName());
            }
            try {
                traceLock(session, exclusive, "waiting for");
                if (database.getLockMode() == Constants.LOCK_MODE_TABLE_GC) {
                    for (int i = 0; i < 20; i++) {
                        long free = Runtime.getRuntime().freeMemory();
                        System.gc();
                        long free2 = Runtime.getRuntime().freeMemory();
                        if (free == free2) {
                            break;
                        }
                    }
                }
                // don't wait too long so that deadlocks are detected early
                long sleep = Math.min(Constants.DEADLOCK_CHECK, max - now);
                if (sleep == 0) {
                    sleep = 1;
                }
                waitForLock = true;
                database.wait(sleep);
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    private String getDeadlockDetails(ObjectArray<Session> sessions) {
        StringBuilder buff = new StringBuilder();
        for (Session s : sessions) {
            Table lock = s.getWaitForLock();
            buff.append("\nSession ").append(s.toString()).append(
                    " is waiting to lock ").append(lock.toString()).append(
                    " while locking ");
            int i = 0;
            for (Table t : s.getLocks()) {
                if (i++ > 0) {
                    buff.append(", ");
                }
                buff.append(t.toString());
                if (t instanceof TableData) {
                    if (((TableData) t).lockExclusive == s) {
                        buff.append(" (exclusive)");
                    } else {
                        buff.append(" (shared)");
                    }
                }
            }
            buff.append('.');
        }
        return buff.toString();
    }

    public ObjectArray<Session> checkDeadlock(Session session, Session clash,
                                              Set<Session> visited) {
        // only one deadlock check at any given time
        synchronized (TableData.class) {
            if (clash == null) {
                // verification is started
                clash = session;
                visited = New.hashSet();
            } else if (clash == session) {
                // we found a circle where this session is involved
                return ObjectArray.newInstance(false);
            } else if (visited.contains(session)) {
                // we have already checked this session.
                // there is a circle, but the sessions in the circle need to
                // find it out themselves
                return null;
            }
            visited.add(session);
            ObjectArray<Session> error = null;
            for (Session s : lockShared) {
                if (s == session) {
                    // it doesn't matter if we have locked the object already
                    continue;
                }
                Table t = s.getWaitForLock();
                if (t != null) {
                    error = t.checkDeadlock(s, clash, visited);
                    if (error != null) {
                        error.add(session);
                        break;
                    }
                }
            }
            if (error == null && lockExclusive != null) {
                Table t = lockExclusive.getWaitForLock();
                if (t != null) {
                    error = t.checkDeadlock(lockExclusive, clash, visited);
                    if (error != null) {
                        error.add(session);
                    }
                }
            }
            return error;
        }
    }

    private void traceLock(Session session, boolean exclusive, String s) {
        // TODO: the test has been added because of MerkleTree
        if (traceLock != null) {
            if (traceLock.isDebugEnabled()) {
                traceLock.debug(session.getId()
                        + " "
                        + (exclusive ? "exclusive write lock"
                        : "shared read lock") + " " + s + " "
                        + getName());
            }
        }
    }

    public String getDropSQL() {
        return "DROP TABLE IF EXISTS " + getSQL();
    }

    public String getCreateSQL() {
        StatementBuilder buff = new StatementBuilder("CREATE ");
        if (isTemporary()) {
            if (globalTemporary) {
                buff.append("GLOBAL ");
            } else {
                buff.append("LOCAL ");
            }
            buff.append("TEMPORARY ");
        } else if (isPersistIndexes()) {
            buff.append("CACHED ");
        } else {
            buff.append("MEMORY ");
        }
        buff.append("TABLE ").append(getSQL());
        if (comment != null) {
            buff.append(" COMMENT ")
                    .append(StringUtils.quoteStringSQL(comment));
        }
        buff.append("(\n    ");
        for (Column column : columns) {
            buff.appendExceptFirst(",\n    ");
            buff.append(column.getCreateSQL());
        }
        buff.append("\n)");
        if (!isTemporary() && !isPersistIndexes() && !isPersistData()) {
            buff.append("\nNOT PERSISTENT");
        }
        return buff.toString();
    }

    public boolean isLockedExclusively() {
        return lockExclusive != null;
    }

    public void unlock(Session s) {
        if (database != null) {
            traceLock(s, lockExclusive == s, "unlock");
            if (lockExclusive == s) {
                MerkleTreeInstance.update(this);
                lockExclusive = null;
            }
            if (lockShared.size() > 0) {
                lockShared.remove(s);
            }
            // TODO lock: maybe we need we fifo-queue to make sure nobody
            // starves. check what other databases do
            synchronized (database) {
                if (database.getSessionCount() > 1 && waitForLock) {
                    database.notifyAll();
                }
            }
        }
    }

    public Record read(Session session, DataPage s) throws SQLException {
        return readRow(s);
    }

    /**
     * Read a row from the data page.
     *
     * @param s the data page
     * @return the row
     */
    public Row readRow(DataPage s) throws SQLException {
        int len = s.readInt();
        Value[] data = new Value[len];
        for (int i = 0; i < len; i++) {
            data[i] = s.readValue();
        }
        return createRow(data);
    }

    /**
     * Create a row from the values.
     *
     * @param data the value list
     * @return the row
     */
    public Row createRow(Value[] data) {
        return new Row(true, data, memoryPerRow);
    }

    /**
     * Set the row count of this table.
     *
     * @param count the row count
     */
    public void setRowCount(long count) {
        MerkleTreeInstance.update(this);
        this.rowCount = count;
    }

    public void removeChildrenAndResources(Session session) throws SQLException {
        super.removeChildrenAndResources(session);
        // go backwards because database.removeIndex will call table.removeIndex
        while (indexes.size() > 1) {
            Index index = indexes.get(1);
            if (index.getName() != null) {
                database.removeSchemaObject(session, index);
            }
        }
        if (SysProperties.CHECK) {
            for (SchemaObject obj : database
                    .getAllSchemaObjects(DbObject.INDEX)) {
                Index index = (Index) obj;
                if (index.getTable() == this) {
                    Message.throwInternalError("index not dropped: "
                            + index.getName());
                }
            }
        }
        scanIndex.remove(session);
        database.removeMeta(session, getId());
        scanIndex = null;
        lockExclusive = null;
        lockShared = null;
        invalidate();
    }

    public String toString() {
        return getSQL();
    }

    public void checkRename() {
        // ok
    }

    public void checkSupportAlter() {
        // ok
    }

    public boolean canTruncate() {
        ObjectArray<Constraint> constraints = getConstraints();
        for (int i = 0; constraints != null && i < constraints.size(); i++) {
            Constraint c = constraints.get(i);
            if (!(c.getConstraintType().equals(Constraint.REFERENTIAL))) {
                continue;
            }
            ConstraintReferential ref = (ConstraintReferential) c;
            if (ref.getRefTable() == this) {
                return false;
            }
        }
        return true;
    }

    public String getTableType() {
        return Table.TABLE;
    }

    public void setGlobalTemporary(boolean globalTemporary) {
        MerkleTreeInstance.update(this);
        this.globalTemporary = globalTemporary;
    }

    public boolean isGlobalTemporary() {
        return globalTemporary;
    }

    public long getMaxDataModificationId() {
        return lastModificationId;
    }

    public boolean getContainsLargeObject() {
        return containsLargeObject;
    }

    public long getRowCountApproximation() {
        return scanIndex.getRowCountApproximation();
    }

    public void setCompareMode(CompareMode compareMode) {
        this.compareMode = compareMode;
    }

    public boolean isDeterministic() {
        return true;
    }

}
