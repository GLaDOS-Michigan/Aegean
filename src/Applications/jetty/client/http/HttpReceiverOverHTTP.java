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

import Applications.jetty.client.*;
import Applications.jetty.http.HttpField;
import Applications.jetty.http.HttpMethod;
import Applications.jetty.http.HttpParser;
import Applications.jetty.http.HttpVersion;
import Applications.jetty.io.ByteBufferPool;
import Applications.jetty.io.EndPoint;
import Applications.jetty.io.EofException;
import Applications.jetty.util.BufferUtil;

import java.io.EOFException;
import java.nio.ByteBuffer;

public class HttpReceiverOverHTTP extends HttpReceiver implements HttpParser.ResponseHandler<ByteBuffer> {
    private final HttpParser parser = new HttpParser(this);

    public HttpReceiverOverHTTP(HttpChannelOverHTTP channel) {
        super(channel);
    }

    @Override
    public HttpChannelOverHTTP getHttpChannel() {
        return (HttpChannelOverHTTP) super.getHttpChannel();
    }

    private HttpConnectionOverHTTP getHttpConnection() {
        return getHttpChannel().getHttpConnection();
    }

    public void receive() {
        HttpConnectionOverHTTP connection = getHttpConnection();
        EndPoint endPoint = connection.getEndPoint();
        HttpClient client = getHttpDestination().getHttpClient();
        ByteBufferPool bufferPool = client.getByteBufferPool();
        ByteBuffer buffer = bufferPool.acquire(client.getResponseBufferSize(), true);
        try {
            while (true) {
                // Connection may be closed in a parser callback
                if (connection.isClosed()) {
                    LOG.debug("{} closed", connection);
                    break;
                } else {
                    int read = endPoint.fill(buffer);
                    if (LOG.isDebugEnabled()) // Avoid boxing of variable 'read'
                        LOG.debug("Read {} bytes from {}", read, endPoint);
                    if (read > 0) {
                        parse(buffer);
                    } else if (read == 0) {
                        fillInterested();
                        break;
                    } else {
                        shutdown();
                        break;
                    }
                }
            }
        } catch (EofException x) {
            LOG.ignore(x);
            failAndClose(x);
        } catch (Exception x) {
            LOG.debug(x);
            failAndClose(x);
        } finally {
            bufferPool.release(buffer);
        }
    }

    private void parse(ByteBuffer buffer) {
        while (buffer.hasRemaining())
            parser.parseNext(buffer);
    }

    private void fillInterested() {
        // TODO: do we need to call fillInterested() only if we are not failed (or we have an exchange) ?
        getHttpChannel().getHttpConnection().fillInterested();
    }

    private void shutdown() {
        // Shutting down the parser may invoke messageComplete() or earlyEOF()
        parser.atEOF();
        parser.parseNext(BufferUtil.EMPTY_BUFFER);
        if (!responseFailure(new EOFException()))
            getHttpChannel().getHttpConnection().close();
    }

    @Override
    public int getHeaderCacheSize() {
        // TODO get from configuration
        return 256;
    }

    @Override
    public boolean startResponse(HttpVersion version, int status, String reason) {
        HttpExchange exchange = getHttpExchange();
        if (exchange == null)
            return false;

        String method = exchange.getRequest().getMethod();
        parser.setHeadResponse(HttpMethod.HEAD.is(method) || HttpMethod.CONNECT.is(method));
        exchange.getResponse().version(version).status(status).reason(reason);

        responseBegin(exchange);
        return false;
    }

    @Override
    public boolean parsedHeader(HttpField field) {
        HttpExchange exchange = getHttpExchange();
        if (exchange == null)
            return false;

        responseHeader(exchange, field);
        return false;
    }

    @Override
    public boolean headerComplete() {
        HttpExchange exchange = getHttpExchange();
        if (exchange == null)
            return false;

        responseHeaders(exchange);
        return false;
    }

    @Override
    public boolean content(ByteBuffer buffer) {
        HttpExchange exchange = getHttpExchange();
        if (exchange == null)
            return false;

        responseContent(exchange, buffer);
        return false;
    }

    @Override
    public boolean messageComplete() {
        HttpExchange exchange = getHttpExchange();
        if (exchange == null)
            return false;

        responseSuccess(exchange);
        return true;
    }

    @Override
    public void earlyEOF() {
        failAndClose(new EOFException());
    }

    @Override
    public void badMessage(int status, String reason) {
        HttpExchange exchange = getHttpExchange();
        if (exchange != null) {
            HttpResponse response = exchange.getResponse();
            response.status(status).reason(reason);
            failAndClose(new HttpResponseException("HTTP protocol violation: bad response", response));
        }
    }

    @Override
    protected void reset() {
        super.reset();
        parser.reset();
    }

    @Override
    protected void dispose() {
        super.dispose();
        parser.close();
    }

    private void failAndClose(Throwable failure) {
        if (responseFailure(failure))
            getHttpChannel().getHttpConnection().close();
    }

    @Override
    public String toString() {
        return String.format("%s@%x on %s", getClass().getSimpleName(), hashCode(), getHttpConnection());
    }
}
