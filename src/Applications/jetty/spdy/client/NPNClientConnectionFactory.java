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

import Applications.jetty.io.ClientConnectionFactory;
import Applications.jetty.io.Connection;
import Applications.jetty.io.EndPoint;
import Applications.jetty.io.ssl.SslClientConnectionFactory;

import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.util.Map;

public class NPNClientConnectionFactory implements ClientConnectionFactory {
    private final SPDYClient client;
    private final ClientConnectionFactory connectionFactory;

    public NPNClientConnectionFactory(SPDYClient client, ClientConnectionFactory connectionFactory) {
        this.client = client;
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Connection newConnection(EndPoint endPoint, Map<String, Object> context) throws IOException {
        return new NPNClientConnection(endPoint, client, connectionFactory,
                (SSLEngine) context.get(SslClientConnectionFactory.SSL_ENGINE_CONTEXT_KEY), context);
    }
}
