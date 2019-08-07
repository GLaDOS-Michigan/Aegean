/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.index;

import org.h2.engine.Session;
import org.h2.message.Message;
import org.h2.result.Row;
import org.h2.result.SearchRow;
import org.h2.store.PageStore;
import org.h2.table.Column;
import org.h2.table.IndexColumn;
import org.h2.table.TableData;

import java.sql.SQLException;

/**
 * An index that delegates indexing to the page data index.
 */
public class PageDelegateIndex extends PageIndex {

    private final PageDataIndex mainIndex;

    public PageDelegateIndex(TableData table, int id, String name, IndexType indexType, PageDataIndex mainIndex, int headPos, Session session) throws SQLException {
        IndexColumn[] cols = IndexColumn.wrap(new Column[]{table.getColumn(mainIndex.getMainIndexColumn())});
        this.initBaseIndex(table, id, name, cols, indexType);
        this.mainIndex = mainIndex;
        if (!database.isPersistent() || id < 0) {
            throw Message.throwInternalError("" + name);
        }
        PageStore store = database.getPageStore();
        store.addIndex(this);
        if (headPos == Index.EMPTY_HEAD) {
            store.addMeta(this, session);
        }
    }

    public void add(Session session, Row row) {
        // nothing to do
    }

    public boolean canFindNext() {
        return false;
    }

    public boolean canGetFirstOrLast() {
        return true;
    }

    public void close(Session session) {
        // nothing to do
    }

    public Cursor find(Session session, SearchRow first, SearchRow last) throws SQLException {
        long min = mainIndex.getLong(first, Long.MIN_VALUE);
        long max = mainIndex.getLong(last, Long.MAX_VALUE);
        return mainIndex.find(session, min, max, false);
    }

    public Cursor findFirstOrLast(Session session, boolean first) throws SQLException {
        Cursor cursor;
        if (first) {
            cursor = mainIndex.find(session, Long.MIN_VALUE, Long.MAX_VALUE, false);
        } else {
            long x = mainIndex.getLastKey();
            cursor = mainIndex.find(session, x, x, false);
        }
        cursor.next();
        return cursor;
    }

    public Cursor findNext(Session session, SearchRow higherThan, SearchRow last) {
        throw Message.throwInternalError();
    }

    public int getColumnIndex(Column col) {
        return mainIndex.getColumnIndex(col);
    }

    public double getCost(Session session, int[] masks) {
        return 10 * getCostRangeIndex(masks, mainIndex.getRowCount(session));
    }

    public boolean needRebuild() {
        return false;
    }

    public void remove(Session session, Row row) {
        // nothing to do
    }

    public void remove(Session session) throws SQLException {
        mainIndex.setMainIndexColumn(-1);
        session.getDatabase().getPageStore().removeMeta(this, session);
    }

    public void truncate(Session session) {
        // nothing to do
    }

    public void checkRename() {
        // ok
    }

    public long getRowCount(Session session) {
        return mainIndex.getRowCount(session);
    }

    public long getRowCountApproximation() {
        return mainIndex.getRowCountApproximation();
    }

    public void writeRowCount() {
        // ignore
    }

}
