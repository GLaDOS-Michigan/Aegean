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

package Applications.jetty.websocket.server;

import Applications.jetty.io.ByteBufferPool;
import Applications.jetty.io.EndPoint;
import Applications.jetty.util.thread.Scheduler;
import Applications.jetty.websocket.api.WebSocketPolicy;
import Applications.jetty.websocket.api.extensions.IncomingFrames;
import Applications.jetty.websocket.common.io.AbstractWebSocketConnection;

import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

public class WebSocketServerConnection extends AbstractWebSocketConnection {
    private final WebSocketServerFactory factory;
    private final AtomicBoolean opened = new AtomicBoolean(false);

    public WebSocketServerConnection(EndPoint endp, Executor executor, Scheduler scheduler, WebSocketPolicy policy, ByteBufferPool bufferPool,
                                     WebSocketServerFactory factory) {
        super(endp, executor, scheduler, policy, bufferPool);
        if (policy.getIdleTimeout() > 0) {
            endp.setIdleTimeout(policy.getIdleTimeout());
        }
        this.factory = factory;
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return getEndPoint().getLocalAddress();
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return getEndPoint().getRemoteAddress();
    }

    @Override
    public void onClose() {
        super.onClose();
        factory.sessionClosed(getSession());
    }

    @Override
    public void onOpen() {
        boolean beenOpened = opened.getAndSet(true);
        if (!beenOpened) {
            factory.sessionOpened(getSession());
        }
        super.onOpen();
    }

    @Override
    public void setNextIncomingFrames(IncomingFrames incoming) {
        getParser().setIncomingFramesHandler(incoming);
    }
}
