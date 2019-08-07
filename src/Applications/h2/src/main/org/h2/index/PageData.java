/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.index;

import org.h2.engine.Session;
import org.h2.result.Row;
import org.h2.store.Data;
import org.h2.store.Page;

import java.sql.SQLException;

/**
 * A page that contains data rows.
 */
abstract class PageData extends Page {

    /**
     * The position of the parent page id.
     */
    static final int START_PARENT = 3;

    /**
     * This is a root page.
     */
    static final int ROOT = 0;

    /**
     * Indicator that the row count is not known.
     */
    static final int UNKNOWN_ROWCOUNT = -1;

    /**
     * The index.
     */
    protected final PageDataIndex index;

    /**
     * The page number of the parent.
     */
    protected int parentPageId;

    /**
     * The data page.
     */
    protected final Data data;

    /**
     * The number of entries.
     */
    protected int entryCount;

    /**
     * The row keys.
     */
    protected long[] keys;

    /**
     * Whether the data page is up-to-date.
     */
    protected boolean written;

    PageData(PageDataIndex index, int pageId, Data data) {
        this.index = index;
        this.data = data;
        setPos(pageId);
    }

    /**
     * Get the real row count. If required, this will read all child pages.
     *
     * @return the row count
     */
    abstract int getRowCount() throws SQLException;

    /**
     * Set the stored row count. This will write the page.
     *
     * @param rowCount the stored row count
     */
    abstract void setRowCountStored(int rowCount) throws SQLException;

    /**
     * Find an entry by key.
     *
     * @param key the key (may not exist)
     * @return the matching or next index
     */
    int find(long key) {
        int l = 0, r = entryCount;
        while (l < r) {
            int i = (l + r) >>> 1;
            long k = keys[i];
            if (k == key) {
                return i;
            } else if (k > key) {
                r = i;
            } else {
                l = i + 1;
            }
        }
        return l;
    }

    /**
     * Add a row if possible. If it is possible this method returns -1, otherwise
     * the split point. It is always possible to add one row.
     *
     * @param row the now to add
     * @return the split point of this page, or -1 if no split is required
     */
    abstract int addRowTry(Row row) throws SQLException;

    /**
     * Get a cursor.
     *
     * @param session      the session
     * @param min          the smallest key
     * @param max          the largest key
     * @param multiVersion if the delta should be used
     * @return the cursor
     */
    abstract Cursor find(Session session, long min, long max, boolean multiVersion) throws SQLException;

    /**
     * Get the key at this position.
     *
     * @param at the index
     * @return the key
     */
    long getKey(int at) {
        return keys[at];
    }

    /**
     * Split the index page at the given point.
     *
     * @param splitPoint the index where to split
     * @return the new page that contains about half the entries
     */
    abstract PageData split(int splitPoint) throws SQLException;

    /**
     * Change the page id.
     *
     * @param id the new page id
     */
    void setPageId(int id) throws SQLException {
        int old = getPos();
        index.getPageStore().removeRecord(getPos());
        setPos(id);
        index.getPageStore().logUndo(this, null);
        remapChildren(old);
    }

    /**
     * Get the last key of a page.
     *
     * @return the last key
     */
    abstract long getLastKey() throws SQLException;

    /**
     * Get the first child leaf page of a page.
     *
     * @return the page
     */
    abstract PageDataLeaf getFirstLeaf() throws SQLException;

    /**
     * Change the parent page id.
     *
     * @param id the new parent page id
     */
    void setParentPageId(int id) throws SQLException {
        index.getPageStore().logUndo(this, data);
        parentPageId = id;
        if (written) {
            changeCount = index.getPageStore().getChangeCount();
            data.setInt(START_PARENT, parentPageId);
        }
    }

    /**
     * Update the parent id of all children.
     *
     * @param old the previous position
     */
    abstract void remapChildren(int old) throws SQLException;

    /**
     * Remove a row.
     *
     * @param key the key of the row to remove
     * @return true if this page is now empty
     */
    abstract boolean remove(long key) throws SQLException;

    /**
     * Free this page and all child pages.
     */
    abstract void freeRecursive() throws SQLException;

    /**
     * Get the row for the given key.
     *
     * @param key the key
     * @return the row
     */
    abstract Row getRow(long key) throws SQLException;

    /**
     * Get the estimated memory size.
     *
     * @return number of double words (4 bytes)
     */
    public int getMemorySize() {
        // four times the byte array size
        return index.getPageStore().getPageSize();
    }

    int getParentPageId() {
        return parentPageId;
    }

    public boolean canRemove() {
        if (changeCount >= index.getPageStore().getChangeCount()) {
            return false;
        }
        return super.canRemove();
    }

}
