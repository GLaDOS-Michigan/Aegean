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

import Applications.jetty.io.*;
import Applications.jetty.io.ssl.SslClientConnectionFactory;
import Applications.jetty.spdy.FlowControlStrategy;
import Applications.jetty.spdy.api.GoAwayInfo;
import Applications.jetty.spdy.api.Session;
import Applications.jetty.spdy.api.SessionFrameListener;
import Applications.jetty.util.Callback;
import Applications.jetty.util.FuturePromise;
import Applications.jetty.util.Promise;
import Applications.jetty.util.component.ContainerLifeCycle;
import Applications.jetty.util.ssl.SslContextFactory;
import Applications.jetty.util.thread.QueuedThreadPool;
import Applications.jetty.util.thread.ScheduledExecutorScheduler;
import Applications.jetty.util.thread.Scheduler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

/**
 * A {@link SPDYClient} allows applications to connect to one or more SPDY servers,
 * obtaining {@link Session} objects that can be used to send/receive SPDY frames.
 * <p/>
 * {@link SPDYClient} instances are created through a {@link Factory}:
 * <pre>
 * SPDYClient.Factory factory = new SPDYClient.Factory();
 * SPDYClient client = factory.newSPDYClient(SPDY.V3);
 * </pre>
 * and then used to connect to the server:
 * <pre>
 * FuturePromise&lt;Session&gt; promise = new FuturePromise&lt;&gt;();
 * client.connect("server.com", null, promise);
 * Session session = promise.get();
 * </pre>
 */
public class SPDYClient {
    private final short version;
    private final Factory factory;
    private volatile SocketAddress bindAddress;
    private volatile long idleTimeout = -1;
    private volatile int initialWindowSize;
    private volatile boolean dispatchIO;
    private volatile ClientConnectionFactory connectionFactory;

    protected SPDYClient(short version, Factory factory) {
        this.version = version;
        this.factory = factory;
        setInitialWindowSize(65536);
        setDispatchIO(true);
        ClientConnectionFactory connectionFactory = new SPDYClientConnectionFactory();
        if (factory.sslContextFactory != null)
            connectionFactory = new SslClientConnectionFactory(factory.getSslContextFactory(), factory.getByteBufferPool(), factory.getExecutor(), new NPNClientConnectionFactory(this, connectionFactory));
        setClientConnectionFactory(connectionFactory);
    }

    public short getVersion() {
        return version;
    }

    public Factory getFactory() {
        return factory;
    }

    /**
     * Equivalent to:
     * <pre>
     * Future&lt;Session&gt; promise = new FuturePromise&lt;&gt;();
     * connect(address, listener, promise);
     * </pre>
     *
     * @param address  the address to connect to
     * @param listener the session listener that will be notified of session events
     * @return a {@link Session} when connected
     */
    public Session connect(SocketAddress address, SessionFrameListener listener) throws ExecutionException, InterruptedException {
        FuturePromise<Session> promise = new FuturePromise<>();
        connect(address, listener, promise);
        return promise.get();
    }

    /**
     * Equivalent to:
     * <pre>
     * connect(address, listener, promise, null);
     * </pre>
     *
     * @param address  the address to connect to
     * @param listener the session listener that will be notified of session events
     * @param promise  the promise notified of connection success/failure
     */
    public void connect(SocketAddress address, SessionFrameListener listener, Promise<Session> promise) {
        connect(address, listener, promise, new HashMap<String, Object>());
    }

    /**
     * Connects to the given {@code address}, binding the given {@code listener} to session events,
     * and notified the given {@code promise} of the connect result.
     * <p/>
     * If the connect operation is successful, the {@code promise} will be invoked with the {@link Session}
     * object that applications can use to perform SPDY requests.
     *
     * @param address  the address to connect to
     * @param listener the session listener that will be notified of session events
     * @param promise  the promise notified of connection success/failure
     * @param context  a context object passed to the {@link #getClientConnectionFactory() ConnectionFactory}
     *                 for the creation of the connection
     */
    public void connect(final SocketAddress address, final SessionFrameListener listener, final Promise<Session> promise, Map<String, Object> context) {
        if (!factory.isStarted())
            throw new IllegalStateException(Factory.class.getSimpleName() + " is not started");

        try {
            SocketChannel channel = SocketChannel.open();
            if (bindAddress != null)
                channel.bind(bindAddress);
            configure(channel);
            channel.configureBlocking(false);
            channel.connect(address);

            context.put(SslClientConnectionFactory.SSL_PEER_HOST_CONTEXT_KEY, ((InetSocketAddress) address).getHostString());
            context.put(SslClientConnectionFactory.SSL_PEER_PORT_CONTEXT_KEY, ((InetSocketAddress) address).getPort());
            context.put(SPDYClientConnectionFactory.SPDY_CLIENT_CONTEXT_KEY, this);
            context.put(SPDYClientConnectionFactory.SPDY_SESSION_LISTENER_CONTEXT_KEY, listener);
            context.put(SPDYClientConnectionFactory.SPDY_SESSION_PROMISE_CONTEXT_KEY, promise);

            factory.selector.connect(channel, context);
        } catch (IOException x) {
            promise.failed(x);
        }
    }

    protected void configure(SocketChannel channel) throws IOException {
        channel.socket().setTcpNoDelay(true);
    }

    /**
     * @return the address to bind the socket channel to
     * @see #setBindAddress(SocketAddress)
     */
    public SocketAddress getBindAddress() {
        return bindAddress;
    }

    /**
     * @param bindAddress the address to bind the socket channel to
     * @see #getBindAddress()
     */
    public void setBindAddress(SocketAddress bindAddress) {
        this.bindAddress = bindAddress;
    }

    public long getIdleTimeout() {
        return idleTimeout;
    }

    public void setIdleTimeout(long idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    public int getInitialWindowSize() {
        return initialWindowSize;
    }

    public void setInitialWindowSize(int initialWindowSize) {
        this.initialWindowSize = initialWindowSize;
    }

    public boolean isDispatchIO() {
        return dispatchIO;
    }

    public void setDispatchIO(boolean dispatchIO) {
        this.dispatchIO = dispatchIO;
    }

    public ClientConnectionFactory getClientConnectionFactory() {
        return connectionFactory;
    }

    public void setClientConnectionFactory(ClientConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    protected String selectProtocol(List<String> serverProtocols) {
        String protocol = "spdy/" + version;
        for (String serverProtocol : serverProtocols) {
            if (serverProtocol.equals(protocol))
                return protocol;
        }
        return null;
    }

    protected FlowControlStrategy newFlowControlStrategy() {
        return FlowControlStrategyFactory.newFlowControlStrategy(version);
    }

    public static class Factory extends ContainerLifeCycle {
        private final Queue<Session> sessions = new ConcurrentLinkedQueue<>();
        private final ByteBufferPool bufferPool = new MappedByteBufferPool();
        private final Scheduler scheduler;
        private final Executor executor;
        private final SslContextFactory sslContextFactory;
        private final SelectorManager selector;
        private final long idleTimeout;
        private long connectTimeout;

        public Factory() {
            this(null, null);
        }

        public Factory(SslContextFactory sslContextFactory) {
            this(null, null, sslContextFactory);
        }

        public Factory(Executor executor) {
            this(executor, null);
        }

        public Factory(Executor executor, Scheduler scheduler) {
            this(executor, scheduler, null);
        }

        public Factory(Executor executor, Scheduler scheduler, SslContextFactory sslContextFactory) {
            this(executor, scheduler, sslContextFactory, 30000);
        }

        public Factory(Executor executor, Scheduler scheduler, SslContextFactory sslContextFactory, long idleTimeout) {
            this.idleTimeout = idleTimeout;
            setConnectTimeout(15000);

            if (executor == null)
                executor = new QueuedThreadPool();
            this.executor = executor;
            addBean(executor);

            if (scheduler == null)
                scheduler = new ScheduledExecutorScheduler();
            this.scheduler = scheduler;
            addBean(scheduler);

            this.sslContextFactory = sslContextFactory;
            if (sslContextFactory != null)
                addBean(sslContextFactory);

            selector = new ClientSelectorManager(executor, scheduler);
            selector.setConnectTimeout(getConnectTimeout());
            addBean(selector);
        }

        public ByteBufferPool getByteBufferPool() {
            return bufferPool;
        }

        public Scheduler getScheduler() {
            return scheduler;
        }

        public Executor getExecutor() {
            return executor;
        }

        public SslContextFactory getSslContextFactory() {
            return sslContextFactory;
        }

        public long getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(long connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public SPDYClient newSPDYClient(short version) {
            return new SPDYClient(version, this);
        }

        @Override
        protected void doStop() throws Exception {
            closeConnections();
            super.doStop();
        }

        boolean sessionOpened(Session session) {
            // Add sessions only if the factory is not stopping
            return isRunning() && sessions.offer(session);
        }

        boolean sessionClosed(Session session) {
            // Remove sessions only if the factory is not stopping
            // to avoid concurrent removes during iterations
            return isRunning() && sessions.remove(session);
        }

        private void closeConnections() {
            for (Session session : sessions)
                session.goAway(new GoAwayInfo(), new Callback.Adapter());
            sessions.clear();
        }

        public Collection<Session> getSessions() {
            return Collections.unmodifiableCollection(sessions);
        }

        private class ClientSelectorManager extends SelectorManager {
            private ClientSelectorManager(Executor executor, Scheduler scheduler) {
                super(executor, scheduler);
            }

            @Override
            protected EndPoint newEndPoint(SocketChannel channel, ManagedSelector selectSet, SelectionKey key) throws IOException {
                @SuppressWarnings("unchecked")
                Map<String, Object> context = (Map<String, Object>) key.attachment();
                SPDYClient client = (SPDYClient) context.get(SPDYClientConnectionFactory.SPDY_CLIENT_CONTEXT_KEY);
                long clientIdleTimeout = client.getIdleTimeout();
                if (clientIdleTimeout < 0)
                    clientIdleTimeout = idleTimeout;
                return new SelectChannelEndPoint(channel, selectSet, key, getScheduler(), clientIdleTimeout);
            }

            @Override
            public Connection newConnection(SocketChannel channel, EndPoint endPoint, Object attachment) throws IOException {
                @SuppressWarnings("unchecked")
                Map<String, Object> context = (Map<String, Object>) attachment;
                try {
                    SPDYClient client = (SPDYClient) context.get(SPDYClientConnectionFactory.SPDY_CLIENT_CONTEXT_KEY);
                    return client.getClientConnectionFactory().newConnection(endPoint, context);
                } catch (Throwable x) {
                    @SuppressWarnings("unchecked")
                    Promise<Session> promise = (Promise<Session>) context.get(SPDYClientConnectionFactory.SPDY_SESSION_PROMISE_CONTEXT_KEY);
                    promise.failed(x);
                    throw x;
                }
            }
        }
    }
}
