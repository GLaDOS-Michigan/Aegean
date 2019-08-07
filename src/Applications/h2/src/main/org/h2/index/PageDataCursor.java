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

import java.sql.SQLException;
import java.util.Iterator;

/**
 * The cursor implementation for the page scan index.
 */
class PageDataCursor implements Cursor {

    private PageDataLeaf current;
    private int idx;
    private final long max;
    private Row row;
    private final boolean multiVersion;
    private final Session session;
    private Iterator<Row> delta;

    PageDataCursor(Session session, PageDataLeaf current, int idx, long max, boolean multiVersion) {
        this.current = current;
        this.idx = idx;
        this.max = max;
        this.multiVersion = multiVersion;
        this.session = session;
        if (multiVersion) {
            delta = current.index.getDelta();
        }
    }

    public Row get() {
        return row;
    }

    public long getKey() {
        return row.getKey();
    }

    public SearchRow getSearchRow() {
        return get();
    }

    public boolean next() throws SQLException {
        if (!multiVersion) {
            nextRow();
            return checkMax();
        }
        while (true) {
            if (delta != null) {
                if (!delta.hasNext()) {
                    delta = null;
                    row = null;
                    continue;
                }
                row = delta.next();
                if (!row.isDeleted() || row.getSessionId() == session.getId()) {
                    continue;
                }
            } else {
                nextRow();
                if (row != null && row.getSessionId() != 0 && row.getSessionId() != session.getId()) {
                    continue;
                }
            }
            break;
        }
        return checkMax();
    }

    private boolean checkMax() throws SQLException {
        if (row != null) {
            if (max != Long.MAX_VALUE) {
                long x = current.index.getLong(row, Long.MAX_VALUE);
                if (x > max) {
                    row = null;
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private void nextRow() throws SQLException {
        if (idx >= current.getEntryCount()) {
            current = current.getNextPage();
            idx = 0;
            if (current == null) {
                row = null;
                return;
            }
        }
        row = current.getRowAt(idx);
        idx++;
    }

    public boolean previous() {
        throw Message.throwInternalError();
    }

}
