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

import Applications.jetty.client.AbstractHttpClientTransport;
import Applications.jetty.client.HttpDestination;
import Applications.jetty.client.Origin;
import Applications.jetty.client.api.Connection;
import Applications.jetty.io.EndPoint;
import Applications.jetty.util.Promise;

import java.io.IOException;
import java.util.Map;

public class HttpClientTransportOverHTTP extends AbstractHttpClientTransport {
    public HttpClientTransportOverHTTP() {
        this(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
    }

    public HttpClientTransportOverHTTP(int selectors) {
        super(selectors);
    }

    @Override
    public HttpDestination newHttpDestination(Origin origin) {
        return new HttpDestinationOverHTTP(getHttpClient(), origin);
    }

    @Override
    public Applications.jetty.io.Connection newConnection(EndPoint endPoint, Map<String, Object> context) throws IOException {

        HttpDestination destination = (HttpDestination) context.get(HTTP_DESTINATION_CONTEXT_KEY);
        HttpConnectionOverHTTP connection = new HttpConnectionOverHTTP(endPoint, destination);
        @SuppressWarnings("unchecked")
        Promise<Connection> promise = (Promise<Connection>) context.get(HTTP_CONNECTION_PROMISE_CONTEXT_KEY);
        promise.succeeded(connection);
        return connection;
    }
}
