/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.store;

import org.h2.constant.ErrorCode;
import org.h2.engine.Session;

import java.sql.SQLException;

/**
 * A trunk page of a stream. It contains the page numbers of the stream, and the
 * page number of the next trunk. The format is:
 * <ul>
 * <li>page type: byte (0)</li>
 * <li>checksum: short (1-2)</li>
 * <li>previous trunk page, or 0 if none: int (3-6)</li>
 * <li>log key: int (7-10)</li>
 * <li>next trunk page: int (11-14)</li>
 * <li>number of pages: short (15-16)</li>
 * <li>page ids (17-)</li>
 * </ul>
 */
public class PageStreamTrunk extends Page {

    private static final int DATA_START = 17;

    /**
     * The previous stream trunk.
     */
    int parent;

    /**
     * The next stream trunk.
     */
    int nextTrunk;

    private final PageStore store;
    private int logKey;
    private int[] pageIds;
    private int pageCount;
    private Data data;
    private int index;

    private PageStreamTrunk(PageStore store, int parent, int pageId, int next, int logKey, int[] pageIds) {
        setPos(pageId);
        this.parent = parent;
        this.store = store;
        this.nextTrunk = next;
        this.logKey = logKey;
        this.pageCount = pageIds.length;
        this.pageIds = pageIds;
    }

    private PageStreamTrunk(PageStore store, Data data, int pageId) {
        setPos(pageId);
        this.data = data;
        this.store = store;
    }

    /**
     * Read a stream trunk page.
     *
     * @param store  the page store
     * @param data   the data
     * @param pageId the page id
     * @return the page
     */
    static PageStreamTrunk read(PageStore store, Data data, int pageId) {
        PageStreamTrunk p = new PageStreamTrunk(store, data, pageId);
        p.read();
        return p;
    }

    /**
     * Create a new stream trunk page.
     *
     * @param store   the page store
     * @param parent  the parent page
     * @param pageId  the page id
     * @param next    the next trunk page
     * @param logKey  the log key
     * @param pageIds the stream data page ids
     * @return the page
     */
    static PageStreamTrunk create(PageStore store, int parent, int pageId, int next, int logKey, int[] pageIds) {
        return new PageStreamTrunk(store, parent, pageId, next, logKey, pageIds);
    }

    /**
     * Read the page from the disk.
     */
    private void read() {
        data.reset();
        data.readByte();
        data.readShortInt();
        parent = data.readInt();
        logKey = data.readInt();
        nextTrunk = data.readInt();
        pageCount = data.readShortInt();
        pageIds = new int[pageCount];
        for (int i = 0; i < pageCount; i++) {
            pageIds[i] = data.readInt();
        }
    }

    /**
     * Reset the read/write index.
     */
    void resetIndex() {
        index = 0;
    }

    void setNextDataPage(int page) {
        pageIds[index++] = page;
    }

    int getNextPageData() {
        if (index >= pageIds.length) {
            return -1;
        }
        return pageIds[index++];
    }

    public int getByteCount(DataPage dummy) {
        return store.getPageSize();
    }

    public void write(DataPage buff) throws SQLException {
        data = store.createData();
        data.writeByte((byte) Page.TYPE_STREAM_TRUNK);
        data.writeShortInt(0);
        data.writeInt(parent);
        data.writeInt(logKey);
        data.writeInt(nextTrunk);
        data.writeShortInt(pageCount);
        for (int i = 0; i < pageCount; i++) {
            data.writeInt(pageIds[i]);
        }
        store.writePage(getPos(), data);
    }

    /**
     * Get the number of pages that can be addressed in a stream trunk page.
     *
     * @param pageSize the page size
     * @return the number of pages
     */
    static int getPagesAddressed(int pageSize) {
        return (pageSize - DATA_START) / 4;
    }

    /**
     * Check if the given data page is in this trunk page.
     *
     * @param dataPageId the page id
     * @return true if it is
     */
    boolean contains(int dataPageId) {
        for (int i = 0; i < pageCount; i++) {
            if (pageIds[i] == dataPageId) {
                return true;
            }
        }
        return false;
    }

    /**
     * Free this page and all data pages.
     *
     * @return the number of pages freed
     */
    int free() throws SQLException {
        store.free(getPos(), false);
        int freed = 1;
        for (int i = 0; i < pageCount; i++) {
            int page = pageIds[i];
            store.free(page, false);
            freed++;
        }
        return freed;
    }

    /**
     * Get the estimated memory size.
     *
     * @return number of double words (4 bytes)
     */
    public int getMemorySize() {
        return store.getPageSize() >> 2;
    }

    /**
     * One of the children has moved to another place.
     *
     * @param oldPos the old position
     * @param newPos the new position
     */
    void moveChild(int oldPos, int newPos) throws SQLException {
        store.logUndo(this, data);
        for (int i = 0; i < pageIds.length; i++) {
            if (pageIds[i] == oldPos) {
                pageIds[i] = newPos;
                break;
            }
        }
        store.update(this);
    }

    public void moveTo(Session session, int newPos) {
        // not required
    }

    int getLogKey() {
        return logKey;
    }

    public int getNextTrunk() {
        return nextTrunk;
    }

    /**
     * An iterator over page stream trunk pages.
     */
    static class Iterator {

        private PageStore store;
        private int first;
        private int next;
        private int previous;
        private boolean canDelete;
        private int current;

        Iterator(PageStore store, int first) {
            this.store = store;
            this.next = first;
        }

        int getCurrentPageId() {
            return current;
        }

        /**
         * Get the next trunk page or null if no next trunk page.
         *
         * @return the next trunk page or null
         */
        PageStreamTrunk next() throws SQLException {
            canDelete = false;
            if (first == 0) {
                first = next;
            } else if (first == next) {
                return null;
            }
            if (next == 0 || next >= store.getPageCount()) {
                return null;
            }
            Page p;
            current = next;
            try {
                p = store.getPage(next);
            } catch (SQLException e) {
                if (e.getErrorCode() != ErrorCode.FILE_CORRUPTED_1) {
                    // wrong checksum means end of stream
                    throw e;
                }
                return null;
            }
            if (p == null || p instanceof PageStreamTrunk || p instanceof PageStreamData) {
                canDelete = true;
            }
            if (!(p instanceof PageStreamTrunk)) {
                return null;
            }
            PageStreamTrunk t = (PageStreamTrunk) p;
            if (previous > 0 && t.parent != previous) {
                return null;
            }
            previous = next;
            next = t.nextTrunk;
            return t;
        }

        /**
         * Check if the current page can be deleted. It can if it's empty, a
         * stream trunk, or a stream data page.
         *
         * @return true if it can be deleted
         */
        boolean canDelete() {
            return canDelete;
        }

    }

    public boolean canRemove() {
        return true;
    }

}
