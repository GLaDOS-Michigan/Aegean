//
//  ========================================================================
//  Copyright (c) 1995-2013 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package Applications.jetty.io;

import Applications.jetty.util.Callback;
import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;
import Applications.jetty.util.thread.Scheduler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeoutException;

public abstract class AbstractEndPoint extends IdleTimeout implements EndPoint {
    private static final Logger LOG = Log.getLogger(AbstractEndPoint.class);
    private final long _created = System.currentTimeMillis();
    private final InetSocketAddress _local;
    private final InetSocketAddress _remote;
    private volatile Connection _connection;

    private final FillInterest _fillInterest = new FillInterest() {
        @Override
        protected boolean needsFill() throws IOException {
            return AbstractEndPoint.this.needsFill();
        }
    };
    private final WriteFlusher _writeFlusher = new WriteFlusher(this) {
        @Override
        protected void onIncompleteFlushed() {
            AbstractEndPoint.this.onIncompleteFlush();
        }
    };

    protected AbstractEndPoint(Scheduler scheduler, InetSocketAddress local, InetSocketAddress remote) {
        super(scheduler);
        _local = local;
        _remote = remote;
    }

    @Override
    public long getCreatedTimeStamp() {
        return _created;
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return _local;
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return _remote;
    }

    @Override
    public Connection getConnection() {
        return _connection;
    }

    @Override
    public void setConnection(Connection connection) {
        _connection = connection;
    }

    @Override
    public void onOpen() {
        LOG.debug("onOpen {}", this);
        super.onOpen();
    }

    @Override
    public void onClose() {
        super.onClose();
        LOG.debug("onClose {}", this);
        _writeFlusher.onClose();
        _fillInterest.onClose();
    }

    @Override
    public void close() {
        super.close();
    }

    @Override
    public void fillInterested(Callback callback) throws IllegalStateException {
        notIdle();
        _fillInterest.register(callback);
    }

    @Override
    public void write(Callback callback, ByteBuffer... buffers) throws IllegalStateException {
        _writeFlusher.write(callback, buffers);
    }

    protected abstract void onIncompleteFlush();

    protected abstract boolean needsFill() throws IOException;

    protected FillInterest getFillInterest() {
        return _fillInterest;
    }

    protected WriteFlusher getWriteFlusher() {
        return _writeFlusher;
    }

    @Override
    protected void onIdleExpired(TimeoutException timeout) {
        boolean output_shutdown = isOutputShutdown();
        _fillInterest.onFail(timeout);
        _writeFlusher.onFail(timeout);
        if (output_shutdown)
            close();
    }

    @Override
    public String toString() {
        return String.format("%s@%x{%s<->%d,%s,%s,%s,%s,%s,%d,%s}",
                getClass().getSimpleName(),
                hashCode(),
                getRemoteAddress(),
                getLocalAddress().getPort(),
                isOpen() ? "Open" : "CLOSED",
                isInputShutdown() ? "ISHUT" : "in",
                isOutputShutdown() ? "OSHUT" : "out",
                _fillInterest.isInterested() ? "R" : "-",
                _writeFlusher.isInProgress() ? "W" : "-",
                getIdleTimeout(),
                getConnection() == null ? null : getConnection().getClass().getSimpleName());
    }
}
