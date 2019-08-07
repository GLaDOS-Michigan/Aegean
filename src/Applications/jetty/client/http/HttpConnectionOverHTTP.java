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

package Applications.jetty.client.http;

import Applications.jetty.client.HttpConnection;
import Applications.jetty.client.HttpDestination;
import Applications.jetty.client.HttpExchange;
import Applications.jetty.client.api.Connection;
import Applications.jetty.client.api.Request;
import Applications.jetty.client.api.Response;
import Applications.jetty.io.AbstractConnection;
import Applications.jetty.io.EndPoint;
import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;

import java.util.concurrent.TimeoutException;

public class HttpConnectionOverHTTP extends AbstractConnection implements Connection {
    private static final Logger LOG = Log.getLogger(HttpConnectionOverHTTP.class);

    private final Delegate delegate;
    private final HttpChannelOverHTTP channel;
    private boolean closed;
    private long idleTimeout;

    public HttpConnectionOverHTTP(EndPoint endPoint, HttpDestination destination) {
        super(endPoint, destination.getHttpClient().getExecutor(), destination.getHttpClient().isDispatchIO());
        this.delegate = new Delegate(destination);
        this.channel = new HttpChannelOverHTTP(this);
    }

    public HttpChannelOverHTTP getHttpChannel() {
        return channel;
    }

    public HttpDestinationOverHTTP getHttpDestination() {
        return (HttpDestinationOverHTTP) delegate.getHttpDestination();
    }

    @Override
    public void send(Request request, Response.CompleteListener listener) {
        delegate.send(request, listener);
    }

    protected void send(HttpExchange exchange) {
        delegate.send(exchange);
    }

    @Override
    public void onOpen() {
        super.onOpen();
        fillInterested();
    }

    @Override
    public void onClose() {
        closed = true;
        super.onClose();
    }

    protected boolean isClosed() {
        return closed;
    }

    @Override
    protected boolean onReadTimeout() {
        LOG.debug("{} idle timeout", this);

        HttpExchange exchange = channel.getHttpExchange();
        if (exchange != null)
            return exchange.getRequest().abort(new TimeoutException());

        getHttpDestination().close(this);
        return true;
    }

    @Override
    public void onFillable() {
        HttpExchange exchange = channel.getHttpExchange();
        if (exchange != null) {
            channel.receive();
        } else {
            // If there is no exchange, then could be either a remote close,
            // or garbage bytes; in both cases we close the connection
            close();
        }
    }

    public void release() {
        // Restore idle timeout
        getEndPoint().setIdleTimeout(idleTimeout);
        getHttpDestination().release(this);
    }

    @Override
    public void close() {
        getHttpDestination().close(this);
        getEndPoint().shutdownOutput();
        LOG.debug("{} oshut", this);
        getEndPoint().close();
        LOG.debug("{} closed", this);
    }

    @Override
    public String toString() {
        return String.format("%s@%h(l:%s <-> r:%s)",
                getClass().getSimpleName(),
                this,
                getEndPoint().getLocalAddress(),
                getEndPoint().getRemoteAddress());
    }

    private class Delegate extends HttpConnection {
        private Delegate(HttpDestination destination) {
            super(destination);
        }

        @Override
        protected void send(HttpExchange exchange) {
            Request request = exchange.getRequest();
            normalizeRequest(request);

            // Save the old idle timeout to restore it
            EndPoint endPoint = getEndPoint();
            idleTimeout = endPoint.getIdleTimeout();
            endPoint.setIdleTimeout(request.getIdleTimeout());

            // One channel per connection, just delegate the send
            channel.associate(exchange);
            channel.send();
        }

        @Override
        public void close() {
            HttpConnectionOverHTTP.this.close();
        }

        @Override
        public String toString() {
            return HttpConnectionOverHTTP.this.toString();
        }
    }
}
