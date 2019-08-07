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

package Applications.jetty.client;

import Applications.jetty.client.api.Connection;
import Applications.jetty.client.api.Destination;
import Applications.jetty.util.BlockingArrayQueue;
import Applications.jetty.util.Promise;
import Applications.jetty.util.component.ContainerLifeCycle;
import Applications.jetty.util.component.Dumpable;
import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;

import java.io.IOException;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;

public class ConnectionPool implements Dumpable {
    private static final Logger LOG = Log.getLogger(ConnectionPool.class);

    private final AtomicInteger connectionCount = new AtomicInteger();
    private final Destination destination;
    private final int maxConnections;
    private final Promise<Connection> connectionPromise;
    private final BlockingDeque<Connection> idleConnections;
    private final BlockingQueue<Connection> activeConnections;

    public ConnectionPool(Destination destination, int maxConnections, Promise<Connection> connectionPromise) {
        this.destination = destination;
        this.maxConnections = maxConnections;
        this.connectionPromise = connectionPromise;
        this.idleConnections = new LinkedBlockingDeque<>(maxConnections);
        this.activeConnections = new BlockingArrayQueue<>(maxConnections);
    }

    public BlockingQueue<Connection> getIdleConnections() {
        return idleConnections;
    }

    public BlockingQueue<Connection> getActiveConnections() {
        return activeConnections;
    }

    public Connection acquire() {
        Connection result = acquireIdleConnection();
        if (result != null)
            return result;

        while (true) {
            int current = connectionCount.get();
            final int next = current + 1;

            if (next > maxConnections) {
                LOG.debug("Max connections {}/{} reached", current, maxConnections);
                // Try again the idle connections
                return acquireIdleConnection();
            }

            if (connectionCount.compareAndSet(current, next)) {
                LOG.debug("Connection {}/{} creation", next, maxConnections);

                destination.newConnection(new Promise<Connection>() {
                    @Override
                    public void succeeded(Connection connection) {
                        LOG.debug("Connection {}/{} creation succeeded {}", next, maxConnections, connection);
                        activate(connection);
                        connectionPromise.succeeded(connection);
                    }

                    @Override
                    public void failed(Throwable x) {
                        LOG.debug("Connection " + next + "/" + maxConnections + " creation failed", x);
                        connectionCount.decrementAndGet();
                        connectionPromise.failed(x);
                    }
                });

                // Try again the idle connections
                return acquireIdleConnection();
            }
        }
    }

    private Connection acquireIdleConnection() {
        Connection connection = idleConnections.pollFirst();
        if (connection != null)
            activate(connection);
        return connection;
    }

    private boolean activate(Connection connection) {
        if (activeConnections.offer(connection)) {
            LOG.debug("Connection active {}", connection);
            return true;
        } else {
            LOG.debug("Connection active overflow {}", connection);
            return false;
        }
    }

    public boolean release(Connection connection) {
        if (activeConnections.remove(connection)) {
            // Make sure we use "hot" connections first
            if (idleConnections.offerFirst(connection)) {
                LOG.debug("Connection idle {}", connection);
                return true;
            } else {
                LOG.debug("Connection idle overflow {}", connection);
            }
        }
        return false;
    }

    public boolean remove(Connection connection) {
        boolean removed = activeConnections.remove(connection);
        removed |= idleConnections.remove(connection);
        if (removed) {
            int pooled = connectionCount.decrementAndGet();
            LOG.debug("Connection removed {} - pooled: {}", connection, pooled);
        }
        return removed;
    }

    public boolean isActive(Connection connection) {
        return activeConnections.contains(connection);
    }

    public boolean isIdle(Connection connection) {
        return idleConnections.contains(connection);
    }

    public void close() {
        for (Connection connection : idleConnections)
            connection.close();
        idleConnections.clear();

        // A bit drastic, but we cannot wait for all requests to complete
        for (Connection connection : activeConnections)
            connection.close();
        activeConnections.clear();

        connectionCount.set(0);
    }

    @Override
    public String dump() {
        return ContainerLifeCycle.dump(this);
    }

    @Override
    public void dump(Appendable out, String indent) throws IOException {
        ContainerLifeCycle.dumpObject(out, this);
        ContainerLifeCycle.dump(out, indent, activeConnections, idleConnections);
    }

    @Override
    public String toString() {
        return String.format("%s %d/%d", getClass().getSimpleName(), connectionCount.get(), maxConnections);
    }
}
