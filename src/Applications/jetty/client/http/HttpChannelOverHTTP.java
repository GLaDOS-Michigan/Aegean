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

import Applications.jetty.client.HttpChannel;
import Applications.jetty.client.HttpExchange;
import Applications.jetty.client.api.Result;
import Applications.jetty.http.HttpFields;
import Applications.jetty.http.HttpHeader;
import Applications.jetty.http.HttpHeaderValue;

import java.util.Enumeration;

public class HttpChannelOverHTTP extends HttpChannel {
    private final HttpConnectionOverHTTP connection;
    private final HttpSenderOverHTTP sender;
    private final HttpReceiverOverHTTP receiver;

    public HttpChannelOverHTTP(HttpConnectionOverHTTP connection) {
        super(connection.getHttpDestination());
        this.connection = connection;
        this.sender = new HttpSenderOverHTTP(this);
        this.receiver = new HttpReceiverOverHTTP(this);
    }

    public HttpConnectionOverHTTP getHttpConnection() {
        return connection;
    }

    @Override
    public void send() {
        HttpExchange exchange = getHttpExchange();
        if (exchange != null)
            sender.send(exchange);
    }

    @Override
    public void proceed(HttpExchange exchange, boolean proceed) {
        sender.proceed(exchange, proceed);
    }

    @Override
    public boolean abort(Throwable cause) {
        // We want the return value to be that of the response
        // because if the response has already successfully
        // arrived then we failed to abort the exchange
        sender.abort(cause);
        return receiver.abort(cause);
    }

    public void receive() {
        receiver.receive();
    }

    @Override
    public void exchangeTerminated(Result result) {
        super.exchangeTerminated(result);

        if (result.isSucceeded()) {
            HttpFields responseHeaders = result.getResponse().getHeaders();
            Enumeration<String> values = responseHeaders.getValues(HttpHeader.CONNECTION.asString(), ",");
            if (values != null) {
                while (values.hasMoreElements()) {
                    if (HttpHeaderValue.CLOSE.asString().equalsIgnoreCase(values.nextElement())) {
                        connection.close();
                        return;
                    }
                }
            }
            connection.release();
        } else {
            connection.close();
        }
    }

    @Override
    public String toString() {
        return String.format("%s@%x", getClass().getSimpleName(), hashCode());
    }
}
