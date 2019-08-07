/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.store;

import org.h2.message.Trace;
import org.h2.util.BitField;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;

/**
 * An input stream that reads from a page store.
 */
public class PageInputStream extends InputStream {

    private PageStore store;
    private final Trace trace;
    private int firstTrunkPage;
    private PageStreamTrunk.Iterator trunkIterator;
    private int dataPage;
    private PageStreamTrunk trunk;
    private PageStreamData data;
    private boolean endOfFile;
    private int remaining;
    private byte[] buffer = new byte[1];
    private int logKey;

    PageInputStream(PageStore store, int logKey, int firstTrunkPage, int dataPage) {
        this.store = store;
        this.trace = store.getTrace();
        // minus one because we increment before comparing
        this.logKey = logKey - 1;
        this.firstTrunkPage = firstTrunkPage;
        trunkIterator = new PageStreamTrunk.Iterator(store, firstTrunkPage);
        this.dataPage = dataPage;
    }

    public int read() throws IOException {
        int len = read(buffer);
        return len < 0 ? -1 : (buffer[0] & 255);
    }

    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    public int read(byte[] b, int off, int len) throws IOException {
        if (len == 0) {
            return 0;
        }
        int read = 0;
        while (len > 0) {
            int r = readBlock(b, off, len);
            if (r < 0) {
                break;
            }
            read += r;
            off += r;
            len -= r;
        }
        return read == 0 ? -1 : read;
    }

    private int readBlock(byte[] buff, int off, int len) throws IOException {
        try {
            fillBuffer();
            if (endOfFile) {
                return -1;
            }
            int l = Math.min(remaining, len);
            data.read(buff, off, l);
            remaining -= l;
            return l;
        } catch (SQLException e) {
            throw new EOFException();
        }
    }

    private void fillBuffer() throws SQLException {
        if (remaining > 0 || endOfFile) {
            return;
        }
        int next;
        while (true) {
            if (trunk == null) {
                trunk = trunkIterator.next();
                logKey++;
                if (trunk == null || trunk.getLogKey() != logKey) {
                    endOfFile = true;
                    return;
                }
                trunk.resetIndex();
            }
            if (trunk != null) {
                next = trunk.getNextPageData();
                if (next == -1) {
                    trunk = null;
                } else if (dataPage == -1 || dataPage == next) {
                    break;
                }
            }
        }
        if (trace.isDebugEnabled()) {
            trace.debug("pageIn.readPage " + next);
        }
        dataPage = -1;
        data = null;
        Page p = store.getPage(next);
        if (p instanceof PageStreamData) {
            data = (PageStreamData) p;
        }
        if (data == null || data.getLogKey() != logKey) {
            endOfFile = true;
            return;
        }
        data.initRead();
        remaining = data.getRemaining();
    }

    /**
     * Set all pages as 'allocated' in the page store.
     *
     * @return the bit set
     */
    BitField allocateAllPages() throws SQLException {
        BitField pages = new BitField();
        int key = logKey;
        PageStreamTrunk.Iterator it = new PageStreamTrunk.Iterator(store, firstTrunkPage);
        while (true) {
            PageStreamTrunk t = it.next();
            key++;
            if (it.canDelete()) {
                store.allocatePage(it.getCurrentPageId());
            }
            if (t == null || t.getLogKey() != key) {
                break;
            }
            pages.set(t.getPos());
            t.resetIndex();
            while (true) {
                int n = t.getNextPageData();
                if (n == -1) {
                    break;
                }
                pages.set(n);
                store.allocatePage(n);
            }
        }
        return pages;
    }

    int getDataPage() {
        return data.getPos();
    }

    public void close() {
        // nothing to do
    }

}
