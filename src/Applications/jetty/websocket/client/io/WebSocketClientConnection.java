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

package Applications.jetty.websocket.client.io;

import Applications.jetty.io.EndPoint;
import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;
import Applications.jetty.websocket.api.ProtocolException;
import Applications.jetty.websocket.api.WebSocketPolicy;
import Applications.jetty.websocket.api.WriteCallback;
import Applications.jetty.websocket.api.extensions.Frame;
import Applications.jetty.websocket.api.extensions.IncomingFrames;
import Applications.jetty.websocket.client.masks.Masker;
import Applications.jetty.websocket.common.WebSocketFrame;
import Applications.jetty.websocket.common.WebSocketSession;
import Applications.jetty.websocket.common.io.AbstractWebSocketConnection;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Client side WebSocket physical connection.
 */
public class WebSocketClientConnection extends AbstractWebSocketConnection {
    private static final Logger LOG = Log.getLogger(WebSocketClientConnection.class);
    private final ConnectPromise connectPromise;
    private final Masker masker;
    private final AtomicBoolean opened = new AtomicBoolean(false);

    public WebSocketClientConnection(EndPoint endp, Executor executor, ConnectPromise connectPromise, WebSocketPolicy policy) {
        super(endp, executor, connectPromise.getClient().getScheduler(), policy, connectPromise.getClient().getBufferPool());
        this.connectPromise = connectPromise;
        this.masker = connectPromise.getMasker();
        assert (this.masker != null);
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
        ConnectionManager connectionManager = connectPromise.getClient().getConnectionManager();
        connectionManager.removeSession(getSession());
    }

    @Override
    public void onOpen() {
        boolean beenOpened = opened.getAndSet(true);
        if (!beenOpened) {
            WebSocketSession session = getSession();
            ConnectionManager connectionManager = connectPromise.getClient().getConnectionManager();
            connectionManager.addSession(session);
            connectPromise.succeeded(session);

            ByteBuffer extraBuf = connectPromise.getResponse().getRemainingBuffer();
            if (extraBuf.hasRemaining()) {
                LOG.debug("Parsing extra remaining buffer from UpgradeConnection");
                getParser().parse(extraBuf);
            }
        }
        super.onOpen();
    }

    /**
     * Overrride to set masker
     */
    @Override
    public void outgoingFrame(Frame frame, WriteCallback callback) {
        if (frame instanceof WebSocketFrame) {
            if (masker == null) {
                ProtocolException ex = new ProtocolException("Must set a Masker");
                LOG.warn(ex);
                if (callback != null) {
                    callback.writeFailed(ex);
                }
                return;
            }
            masker.setMask((WebSocketFrame) frame);
        }
        super.outgoingFrame(frame, callback);
    }

    @Override
    public void setNextIncomingFrames(IncomingFrames incoming) {
        getParser().setIncomingFramesHandler(incoming);
    }
}
