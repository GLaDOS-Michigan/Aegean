/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.index;

import merkle.MerkleTreeInstance;
import merkle.wrapper.MTCollectionWrapper;
import merkle.wrapper.MTMapWrapper;
import org.h2.engine.Constants;
import org.h2.engine.Session;
import org.h2.log.UndoLogRecord;
import org.h2.message.Message;
import org.h2.result.Row;
import org.h2.result.SearchRow;
import org.h2.store.Storage;
import org.h2.table.Column;
import org.h2.table.IndexColumn;
import org.h2.table.TableData;
import org.h2.util.ObjectArray;
import org.h2.value.Value;
import org.h2.value.ValueLob;

import java.sql.SQLException;
import java.util.*;

/**
 * The scan index is not really an 'index' in the strict sense, because it can
 * not be used for direct lookup. It can only be used to iterate over all rows
 * of a table. Each regular table has one such object, even if no primary key or
 * indexes are defined.
 */
public class ScanIndex extends BaseIndex implements RowIndex {
    public static final boolean inTree = false;
    public long firstFree = -1;
    public transient ObjectArray<Row> rows = ObjectArray.newInstance(inTree);
    private transient Storage storage;
    private TableData tableData;
    private int rowCountDiff;
    private MTMapWrapper sessionRowCount;
    // private HashMap<Integer, Integer> sessionRowCount;
    private MTCollectionWrapper delta;
    // private HashSet<Row> delta;
    private long rowCount;

    public int rowsSize;

    public ScanIndex() {

    }

    public ScanIndex(TableData table, int id, IndexColumn[] columns,
                     IndexType indexType) {
        MerkleTreeInstance.add(this);
        String str = table.getName() + "_DATA";
        MerkleTreeInstance.add(str);
        initBaseIndex(table, id, str, columns, indexType);
        if (database.isMultiVersion()) {
            sessionRowCount = new MTMapWrapper(new HashMap<Integer, Integer>(),
                    false, true, false);
        }
        tableData = table;
        if (!database.isPersistent() || id < 0 || !indexType.isPersistent()) {
            return;
        }
        this.storage = database.getStorage(table, id, true);
        int count = storage.getRecordCount();
        rowCount = count;
        table.setRowCount(count);
        trace.info("open existing " + table.getName() + " rows: " + count);
    }

    public void remove(Session session) throws SQLException {
        truncate(session);
        if (storage != null) {
            storage.truncate(session);
            database.removeStorage(storage.getId(), storage.getDiskFile());
        }
    }

    public void truncate(Session session) throws SQLException {
        MerkleTreeInstance.update(this);
        if (storage == null) {
            if (rows != null) {
                // MerkleTreeInstance.remove(rows);
            }

            rows = ObjectArray.newInstance(inTree);
            firstFree = -1;
            rowsSize = rows.size();
        } else {
            storage.truncate(session);
        }
        if (tableData.getContainsLargeObject() && tableData.isPersistData()) {
            ValueLob.removeAllForTable(database, table.getId());
        }
        tableData.setRowCount(0);
        rowCount = 0;
        rowCountDiff = 0;
        if (database.isMultiVersion()) {
            sessionRowCount.clear();
        }
    }

    public String getCreateSQL() {
        return null;
    }

    public void close(Session session) {
        if (storage != null) {
            storage = null;
        }
    }

    public Row getRow(Session session, long key) throws SQLException {
        if (storage != null) {
            return (Row) storage.getRecord(session, (int) key);
        }
        return rows.get((int) key);
    }

    public void add(Session session, Row row) throws SQLException {
        MerkleTreeInstance.update(this);
        if (storage != null) {
            if (tableData.getContainsLargeObject()) {
                for (int i = 0; i < row.getColumnCount(); i++) {
                    Value v = row.getValue(i);
                    Value v2 = v.link(database, getId());
                    if (v2.isLinked()) {
                        session.unlinkAtCommitStop(v2);
                    }
                    if (v != v2) {
                        row.setValue(i, v2);
                    }
                }
            }
            storage.addRecord(session, row, Storage.ALLOCATE_POS);
        } else {
            // in-memory
            if (firstFree == -1) {
                int key = rows.size();
                // System.out.println("ScanIndex setKey "+key);
                row.setKey(key);
                row.setPos(key);
                rows.add(row);
                rowsSize = rows.size();
                // row.referenceCounter++;
            } else {
                long key = firstFree;
                Row free = rows.get((int) key);
                firstFree = free.getKey();
                row.setPos((int) key);
                // System.out.println("ScanIndex setKey "+key);
                row.setKey(key);
                rows.set((int) key, row);
                // row.referenceCounter++;
                // free.referenceCounter--;
                // if (free.referenceCounter == 0)
                // {
                // free.inTree = false;
                // MerkleTreeInstance.remove(free);
                // }
            }
            row.setDeleted(false);

            // TODO: to be removed
            // if (rows.get((int) row.getKey()) != row)
            // {
            // System.out.println("THE KEY IS NOT THE RIGHT ONE.......");
            // System.exit(1);
            // }
        }
        if (database.isMultiVersion()) {
            if (delta == null) {
                delta = new MTCollectionWrapper(new HashSet<Row>(), false,
                        false);
            }
            boolean wasDeleted = delta.remove(row);
            if (!wasDeleted) {
                delta.add(row);
            }
            incrementRowCount(session.getId(), 1);
        }
        rowCount++;
    }

    public void commit(int operation, Row row) {
        if (database.isMultiVersion()) {
            if (delta != null) {
                delta.remove(row);
            }
            incrementRowCount(row.getSessionId(),
                    operation == UndoLogRecord.DELETE ? 1 : -1);
        }
    }

    private void incrementRowCount(int sessionId, int count) {
        if (database.isMultiVersion()) {
            MerkleTreeInstance.update(this);
            Integer id = sessionId;
            Integer c = (Integer) sessionRowCount.get(id);
            int current = c == null ? 0 : c.intValue();
            sessionRowCount.put(id, current + count);
            rowCountDiff += count;
        }
    }

    public void remove(Session session, Row row) throws SQLException {
        MerkleTreeInstance.update(this);
        if (storage != null) {
            storage.removeRecord(session, (int) row.getKey());
            if (tableData.getContainsLargeObject()) {
                for (int i = 0; i < row.getColumnCount(); i++) {
                    Value v = row.getValue(i);
                    if (v.isLinked()) {
                        session.unlinkAtCommit((ValueLob) v);
                    }
                }
            }
        } else {
            // in-memory
            if (!database.isMultiVersion() && rowCount == 1) {

				/*
                 * if (rows != null) {
				 * 
				 * for (Row r : rows) { //r.referenceCounter--; //if
				 * (r.referenceCounter == 0) //{ // if (r.data != null) // { //
				 * MerkleTreeInstance.remove(r.data); // } // r.inTree = false; //
				 * MerkleTreeInstance.remove(r); //} }
				 * 
				 * if (rows.data != null) { //
				 * MerkleTreeInstance.remove(rows.data); } //
				 * MerkleTreeInstance.remove(rows); }
				 */

                rows = ObjectArray.newInstance(inTree);
                firstFree = -1;
                rowsSize = rows.size();
            } else {
                long key = row.getKey();

                Row free = new Row(false, null, 0);

                Row previous = null;
                Row next = firstFree == -1 ? null : rows.get((int) firstFree);

                long nextPos = firstFree;

                while ((next != null) && (key > nextPos)) {
                    previous = next;
                    nextPos = next.getKey();
                    if (nextPos == -1) {
                        next = null;
                    } else {
                        next = rows.get((int) nextPos);
                    }
                }

                if (previous == null) {
                    // first free row
                    firstFree = key;
                } else {
                    previous.setKey(key);
                    previous.setPos((int) key);
                }

                free.setKey(nextPos);
                free.setPos((int) nextPos);

                rows.set((int) key, free);
            }
        }
        if (database.isMultiVersion()) {
            // if storage is null, the delete flag is not yet set
            row.setDeleted(true);
            if (delta == null) {
                delta = new MTCollectionWrapper(new HashSet<Row>(), false,
                        false);
            }
            boolean wasAdded = delta.remove(row);
            if (!wasAdded) {
                delta.add(row);
            }
            incrementRowCount(session.getId(), -1);
        }
        rowCount--;
    }

    public Cursor find(Session session, SearchRow first, SearchRow last) {
        return new ScanCursor(session, this, database.isMultiVersion());
    }

    public double getCost(Session session, int[] masks) {
        long cost = tableData.getRowCountApproximation()
                + Constants.COST_ROW_OFFSET;
        if (storage != null) {
            cost *= 10;
        }
        return cost;
    }

    public long getRowCount(Session session) {
        if (database.isMultiVersion()) {
            Integer i = (Integer) sessionRowCount.get(session.getId());
            long count = i == null ? 0 : i.intValue();
            count += rowCount;
            count -= rowCountDiff;
            return count;
        }
        return rowCount;
    }

    /**
     * Get the next row that is stored after this row.
     *
     * @param session the session
     * @param row     the current row or null to start the scan
     * @return the next row or null if there are no more rows
     */
    Row getNextRow(Session session, Row row) throws SQLException {
        if (storage == null) {
            long key;
            if (row == null) {
                key = -1;
            } else {
                key = row.getKey();
            }
            while (true) {
                key++;
                if (key >= rows.size()) {
                    return null;
                }
                row = rows.get((int) key);
                if (!row.isEmpty()) {
                    return row;
                }
            }
        }
        int pos = storage.getNext(row);
        if (pos < 0) {
            return null;
        }
        return (Row) storage.getRecord(session, pos);
    }

    public int getColumnIndex(Column col) {
        // the scan index cannot use any columns
        return -1;
    }

    public void checkRename() throws SQLException {
        throw Message.getUnsupportedException("SCAN");
    }

    public boolean needRebuild() {
        return false;
    }

    public boolean canGetFirstOrLast() {
        return false;
    }

    public Cursor findFirstOrLast(Session session, boolean first)
            throws SQLException {
        throw Message.getUnsupportedException("SCAN");
    }

    Iterator<Row> getDelta() {
        if (delta == null) {
            List<Row> e = Collections.emptyList();
            return e.iterator();
        }
        return delta.iterator();
    }

    public long getRowCountApproximation() {
        return rowCount;
    }

}
