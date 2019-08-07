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

package Applications.jetty.server.nio;

import Applications.jetty.io.*;
import Applications.jetty.server.ConnectionFactory;
import Applications.jetty.server.HttpConnectionFactory;
import Applications.jetty.server.Server;
import Applications.jetty.server.ServerConnector;
import Applications.jetty.util.ssl.SslContextFactory;
import Applications.jetty.util.thread.Scheduler;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

/**
 * <p>A specialized version of {@link ServerConnector} that supports {@link NetworkTrafficListener}s.</p>
 * <p>{@link NetworkTrafficListener}s can be added and removed dynamically before and after this connector has
 * been started without causing {@link ConcurrentModificationException}s.</p>
 */
public class NetworkTrafficSelectChannelConnector extends ServerConnector {
    private final List<NetworkTrafficListener> listeners = new CopyOnWriteArrayList<NetworkTrafficListener>();

    public NetworkTrafficSelectChannelConnector(Server server) {
        this(server, null, null, null, 0, 0, new HttpConnectionFactory());
    }

    public NetworkTrafficSelectChannelConnector(Server server, ConnectionFactory connectionFactory, SslContextFactory sslContextFactory) {
        super(server, sslContextFactory, connectionFactory);
    }

    public NetworkTrafficSelectChannelConnector(Server server, ConnectionFactory connectionFactory) {
        super(server, connectionFactory);
    }

    public NetworkTrafficSelectChannelConnector(Server server, Executor executor, Scheduler scheduler, ByteBufferPool pool, int acceptors, int selectors,
                                                ConnectionFactory... factories) {
        super(server, executor, scheduler, pool, acceptors, selectors, factories);
    }

    public NetworkTrafficSelectChannelConnector(Server server, SslContextFactory sslContextFactory) {
        super(server, sslContextFactory);
    }

    /**
     * @param listener the listener to add
     */
    public void addNetworkTrafficListener(NetworkTrafficListener listener) {
        listeners.add(listener);
    }

    /**
     * @param listener the listener to remove
     */
    public void removeNetworkTrafficListener(NetworkTrafficListener listener) {
        listeners.remove(listener);
    }

    @Override
    protected SelectChannelEndPoint newEndPoint(SocketChannel channel, SelectorManager.ManagedSelector selectSet, SelectionKey key) throws IOException {
        NetworkTrafficSelectChannelEndPoint endPoint = new NetworkTrafficSelectChannelEndPoint(channel, selectSet, key, getScheduler(), getIdleTimeout(), listeners);
        endPoint.notifyOpened();
        return endPoint;
    }

}
