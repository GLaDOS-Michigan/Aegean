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

import Applications.jetty.client.api.*;
import Applications.jetty.http.HttpField;
import Applications.jetty.http.HttpHeader;
import Applications.jetty.http.HttpScheme;
import Applications.jetty.io.ClientConnectionFactory;
import Applications.jetty.io.ssl.SslClientConnectionFactory;
import Applications.jetty.util.BlockingArrayQueue;
import Applications.jetty.util.Promise;
import Applications.jetty.util.component.ContainerLifeCycle;
import Applications.jetty.util.component.Dumpable;
import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.AsynchronousCloseException;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.RejectedExecutionException;

public abstract class HttpDestination implements Destination, Closeable, Dumpable {
    protected static final Logger LOG = Log.getLogger(HttpDestination.class);

    private final HttpClient client;
    private final Origin origin;
    private final Queue<HttpExchange> exchanges;
    private final RequestNotifier requestNotifier;
    private final ResponseNotifier responseNotifier;
    private final ProxyConfiguration.Proxy proxy;
    private final ClientConnectionFactory connectionFactory;
    private final HttpField hostField;

    public HttpDestination(HttpClient client, Origin origin) {
        this.client = client;
        this.origin = origin;

        this.exchanges = new BlockingArrayQueue<>(client.getMaxRequestsQueuedPerDestination());

        this.requestNotifier = new RequestNotifier(client);
        this.responseNotifier = new ResponseNotifier(client);

        ProxyConfiguration proxyConfig = client.getProxyConfiguration();
        proxy = proxyConfig.match(origin);
        ClientConnectionFactory connectionFactory = client.getTransport();
        if (proxy != null) {
            connectionFactory = proxy.newClientConnectionFactory(connectionFactory);
        } else {
            if (HttpScheme.HTTPS.is(getScheme()))
                connectionFactory = newSslClientConnectionFactory(connectionFactory);
        }
        this.connectionFactory = connectionFactory;

        String host = getHost();
        if (!client.isDefaultPort(getScheme(), getPort()))
            host += ":" + getPort();
        hostField = new HttpField(HttpHeader.HOST, host);
    }

    protected ClientConnectionFactory newSslClientConnectionFactory(ClientConnectionFactory connectionFactory) {
        return new SslClientConnectionFactory(client.getSslContextFactory(), client.getByteBufferPool(), client.getExecutor(), connectionFactory);
    }

    public HttpClient getHttpClient() {
        return client;
    }

    public Origin getOrigin() {
        return origin;
    }

    public Queue<HttpExchange> getHttpExchanges() {
        return exchanges;
    }

    public RequestNotifier getRequestNotifier() {
        return requestNotifier;
    }

    public ResponseNotifier getResponseNotifier() {
        return responseNotifier;
    }

    public ProxyConfiguration.Proxy getProxy() {
        return proxy;
    }

    public ClientConnectionFactory getClientConnectionFactory() {
        return connectionFactory;
    }

    @Override
    public String getScheme() {
        return origin.getScheme();
    }

    @Override
    public String getHost() {
        // InetSocketAddress.getHostString() transforms the host string
        // in case of IPv6 addresses, so we return the original host string
        return origin.getAddress().getHost();
    }

    @Override
    public int getPort() {
        return origin.getAddress().getPort();
    }

    public Origin.Address getConnectAddress() {
        return proxy == null ? origin.getAddress() : proxy.getAddress();
    }

    public HttpField getHostField() {
        return hostField;
    }

    protected void send(Request request, List<Response.ResponseListener> listeners) {
        if (!getScheme().equals(request.getScheme()))
            throw new IllegalArgumentException("Invalid request scheme " + request.getScheme() + " for destination " + this);
        if (!getHost().equals(request.getHost()))
            throw new IllegalArgumentException("Invalid request host " + request.getHost() + " for destination " + this);
        int port = request.getPort();
        if (port >= 0 && getPort() != port)
            throw new IllegalArgumentException("Invalid request port " + port + " for destination " + this);

        HttpConversation conversation = client.getConversation(request.getConversationID(), true);
        HttpExchange exchange = new HttpExchange(conversation, this, request, listeners);

        if (client.isRunning()) {
            if (exchanges.offer(exchange)) {
                if (!client.isRunning() && exchanges.remove(exchange)) {
                    throw new RejectedExecutionException(client + " is stopping");
                } else {
                    LOG.debug("Queued {}", request);
                    requestNotifier.notifyQueued(request);
                    send();
                }
            } else {
                LOG.debug("Max queued exceeded {}", request);
                abort(exchange, new RejectedExecutionException("Max requests per destination " + client.getMaxRequestsQueuedPerDestination() + " exceeded for " + this));
            }
        } else {
            throw new RejectedExecutionException(client + " is stopped");
        }
    }

    protected abstract void send();

    public void newConnection(Promise<Connection> promise) {
        createConnection(promise);
    }

    protected void createConnection(Promise<Connection> promise) {
        client.newConnection(this, promise);
    }

    public boolean remove(HttpExchange exchange) {
        return exchanges.remove(exchange);
    }

    public void close() {
        abort(new AsynchronousCloseException());
        LOG.debug("Closed {}", this);
    }

    public void close(Connection connection) {
    }

    /**
     * Aborts all the {@link HttpExchange}s queued in this destination.
     *
     * @param cause the abort cause
     * @see #abort(HttpExchange, Throwable)
     */
    public void abort(Throwable cause) {
        HttpExchange exchange;
        while ((exchange = exchanges.poll()) != null)
            abort(exchange, cause);
    }

    /**
     * Aborts the given {@code exchange}, notifies listeners of the failure, and completes the exchange.
     *
     * @param exchange the {@link HttpExchange} to abort
     * @param cause    the abort cause
     */
    protected void abort(HttpExchange exchange, Throwable cause) {
        Request request = exchange.getRequest();
        HttpResponse response = exchange.getResponse();
        getRequestNotifier().notifyFailure(request, cause);
        List<Response.ResponseListener> listeners = exchange.getConversation().getResponseListeners();
        getResponseNotifier().notifyFailure(listeners, response, cause);
        getResponseNotifier().notifyComplete(listeners, new Result(request, cause, response, cause));
    }

    @Override
    public String dump() {
        return ContainerLifeCycle.dump(this);
    }

    @Override
    public void dump(Appendable out, String indent) throws IOException {
        ContainerLifeCycle.dumpObject(out, this + " - requests queued: " + exchanges.size());
    }

    public String asString() {
        return origin.asString();
    }

    @Override
    public String toString() {
        return String.format("%s(%s)%s",
                HttpDestination.class.getSimpleName(),
                asString(),
                proxy == null ? "" : " via " + proxy);
    }
}
