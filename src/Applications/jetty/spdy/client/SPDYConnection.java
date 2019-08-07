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

package Applications.jetty.spdy.client;

import Applications.jetty.io.AbstractConnection;
import Applications.jetty.io.ByteBufferPool;
import Applications.jetty.io.EndPoint;
import Applications.jetty.io.RuntimeIOException;
import Applications.jetty.spdy.Controller;
import Applications.jetty.spdy.ISession;
import Applications.jetty.spdy.IdleListener;
import Applications.jetty.spdy.api.GoAwayInfo;
import Applications.jetty.spdy.parser.Parser;
import Applications.jetty.util.Callback;
import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;

public class SPDYConnection extends AbstractConnection implements Controller, IdleListener {
    private static final Logger LOG = Log.getLogger(SPDYConnection.class);
    private final ByteBufferPool bufferPool;
    private final Parser parser;
    private final int bufferSize;
    private volatile ISession session;
    private volatile boolean idle = false;

    public SPDYConnection(EndPoint endPoint, ByteBufferPool bufferPool, Parser parser, Executor executor, boolean dispatchIO) {
        this(endPoint, bufferPool, parser, executor, dispatchIO, 8192);
    }

    public SPDYConnection(EndPoint endPoint, ByteBufferPool bufferPool, Parser parser, Executor executor, boolean dispatchIO, int bufferSize) {
        // Since SPDY is multiplexed, onFillable() must never block while calling application code. In fact,
        // the SPDY code always dispatches to a new thread when calling application code,
        // so here we can safely pass false as last parameter, and avoid to dispatch to onFillable().
        // The IO operation (read, parse, etc.) will not block and will be fast in almost all cases.
        // Big uploads to a server, however, might occupy the Selector thread for a long time and
        // therefore starve other connections, so by default dispatchIO is true.
        super(endPoint, executor, dispatchIO);
        this.bufferPool = bufferPool;
        this.parser = parser;
        onIdle(true);
        this.bufferSize = bufferSize;
    }

    @Override
    public void onOpen() {
        super.onOpen();
        fillInterested();
    }

    @Override
    public void onFillable() {
        ByteBuffer buffer = bufferPool.acquire(bufferSize, false);
        boolean readMore = read(buffer) == 0;
        bufferPool.release(buffer);
        if (readMore)
            fillInterested();
    }

    protected int read(ByteBuffer buffer) {
        EndPoint endPoint = getEndPoint();
        while (true) {
            int filled = fill(endPoint, buffer);
            if (LOG.isDebugEnabled()) // Avoid boxing of variable 'filled'
                LOG.debug("Read {} bytes", filled);
            if (filled == 0) {
                return 0;
            } else if (filled < 0) {
                shutdown(session);
                return -1;
            } else {
                parser.parse(buffer);
            }
        }
    }

    private int fill(EndPoint endPoint, ByteBuffer buffer) {
        try {
            if (endPoint.isInputShutdown())
                return -1;
            return endPoint.fill(buffer);
        } catch (IOException x) {
            endPoint.close();
            throw new RuntimeIOException(x);
        }
    }

    @Override
    public void write(ByteBuffer buffer, final Callback callback) {
        EndPoint endPoint = getEndPoint();
        endPoint.write(callback, buffer);
    }

    @Override
    public void close() {
        goAway(session);
    }

    @Override
    public void close(boolean onlyOutput) {
        EndPoint endPoint = getEndPoint();
        // We need to gently close first, to allow
        // SSL close alerts to be sent by Jetty
        LOG.debug("Shutting down output {}", endPoint);
        endPoint.shutdownOutput();
        if (!onlyOutput) {
            LOG.debug("Closing {}", endPoint);
            endPoint.close();
        }
    }

    @Override
    public void onIdle(boolean idle) {
        this.idle = idle;
    }

    @Override
    protected boolean onReadTimeout() {
        boolean idle = this.idle;
        LOG.debug("Idle timeout on {}, idle={}", this, idle);
        if (idle)
            goAway(session);
        return false;
    }

    protected void goAway(ISession session) {
        if (session != null)
            session.goAway(new GoAwayInfo(), new Callback.Adapter());
    }

    private void shutdown(ISession session) {
        if (session != null && !getEndPoint().isOutputShutdown())
            session.shutdown();
    }

    protected ISession getSession() {
        return session;
    }

    public void setSession(ISession session) {
        this.session = session;
    }
}
