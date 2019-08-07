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

import Applications.jetty.client.HttpClient;
import Applications.jetty.client.HttpExchange;
import Applications.jetty.client.Origin;
import Applications.jetty.client.PoolingHttpDestination;

public class HttpDestinationOverHTTP extends PoolingHttpDestination<HttpConnectionOverHTTP> {
    public HttpDestinationOverHTTP(HttpClient client, Origin origin) {
        super(client, origin);
    }

    @Override
    protected void send(HttpConnectionOverHTTP connection, HttpExchange exchange) {
        connection.send(exchange);
    }
}
