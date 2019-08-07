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

import Applications.jetty.io.*;
import Applications.jetty.io.ssl.SslConnection;
import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;
import Applications.jetty.util.ssl.SslContextFactory;
import Applications.jetty.websocket.api.WebSocketPolicy;
import Applications.jetty.websocket.client.WebSocketClient;

import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;

public class WebSocketClientSelectorManager extends SelectorManager {
    private static final Logger LOG = Log.getLogger(WebSocketClientSelectorManager.class);
    private final WebSocketPolicy policy;
    private final ByteBufferPool bufferPool;
    private SslContextFactory sslContextFactory;

    public WebSocketClientSelectorManager(WebSocketClient client) {
        super(client.getExecutor(), client.getScheduler());
        this.bufferPool = client.getBufferPool();
        this.policy = client.getPolicy();
    }

    @Override
    protected void connectionFailed(SocketChannel channel, Throwable ex, Object attachment) {
        LOG.debug("Connection Failed", ex);
        ConnectPromise connect = (ConnectPromise) attachment;
        connect.failed(ex);
    }

    public SslContextFactory getSslContextFactory() {
        return sslContextFactory;
    }

    @Override
    public Connection newConnection(final SocketChannel channel, EndPoint endPoint, final Object attachment) throws IOException {
        LOG.debug("newConnection({},{},{})", channel, endPoint, attachment);
        ConnectPromise connectPromise = (ConnectPromise) attachment;

        try {
            String scheme = connectPromise.getRequest().getRequestURI().getScheme();

            if ("wss".equalsIgnoreCase(scheme)) {
                // Encrypted "wss://"
                SslContextFactory sslContextFactory = getSslContextFactory();
                if (sslContextFactory != null) {
                    SSLEngine engine = newSSLEngine(sslContextFactory, channel);
                    SslConnection sslConnection = new SslConnection(bufferPool, getExecutor(), endPoint, engine);
                    sslConnection.setRenegotiationAllowed(sslContextFactory.isRenegotiationAllowed());
                    EndPoint sslEndPoint = sslConnection.getDecryptedEndPoint();

                    Connection connection = newUpgradeConnection(channel, sslEndPoint, connectPromise);
                    sslEndPoint.setIdleTimeout(connectPromise.getClient().getMaxIdleTimeout());
                    sslEndPoint.setConnection(connection);
                    return sslConnection;
                } else {
                    throw new IOException("Cannot init SSL");
                }
            } else {
                // Standard "ws://"
                endPoint.setIdleTimeout(connectPromise.getClient().getMaxIdleTimeout());
                return newUpgradeConnection(channel, endPoint, connectPromise);
            }
        } catch (IOException e) {
            LOG.ignore(e);
            connectPromise.failed(e);
            // rethrow
            throw e;
        }
    }

    @Override
    protected EndPoint newEndPoint(SocketChannel channel, ManagedSelector selectSet, SelectionKey selectionKey) throws IOException {
        LOG.debug("newEndPoint({}, {}, {})", channel, selectSet, selectionKey);
        return new SelectChannelEndPoint(channel, selectSet, selectionKey, getScheduler(), policy.getIdleTimeout());
    }

    public SSLEngine newSSLEngine(SslContextFactory sslContextFactory, SocketChannel channel) {
        String peerHost = channel.socket().getInetAddress().getHostName();
        int peerPort = channel.socket().getPort();
        SSLEngine engine = sslContextFactory.newSSLEngine(peerHost, peerPort);
        engine.setUseClientMode(true);
        return engine;
    }

    public UpgradeConnection newUpgradeConnection(SocketChannel channel, EndPoint endPoint, ConnectPromise connectPromise) {
        WebSocketClient client = connectPromise.getClient();
        Executor executor = client.getExecutor();
        UpgradeConnection connection = new UpgradeConnection(endPoint, executor, connectPromise);
        return connection;
    }

    public void setSslContextFactory(SslContextFactory sslContextFactory) {
        this.sslContextFactory = sslContextFactory;
    }
}
