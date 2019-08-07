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

package Applications.jetty.spdy.server;

import Applications.jetty.server.ConnectionFactory;
import Applications.jetty.server.HttpConnectionFactory;
import Applications.jetty.server.Server;
import Applications.jetty.server.ServerConnector;
import Applications.jetty.spdy.api.SPDY;
import Applications.jetty.spdy.api.server.ServerSessionFrameListener;
import Applications.jetty.util.ssl.SslContextFactory;

public class SPDYServerConnector extends ServerConnector {
    public SPDYServerConnector(Server server, ServerSessionFrameListener listener) {
        this(server, null, listener);
    }

    public SPDYServerConnector(Server server, SslContextFactory sslContextFactory, ServerSessionFrameListener listener) {
        super(server,
                sslContextFactory,
                sslContextFactory == null
                        ? new ConnectionFactory[]{new SPDYServerConnectionFactory(SPDY.V2, listener)}
                        : new ConnectionFactory[]{
                        new NPNServerConnectionFactory("spdy/3", "spdy/2", "http/1.1"),
                        new HttpConnectionFactory(),
                        new SPDYServerConnectionFactory(SPDY.V2, listener),
                        new SPDYServerConnectionFactory(SPDY.V3, listener)});
        if (getConnectionFactory(NPNServerConnectionFactory.class) != null)
            getConnectionFactory(NPNServerConnectionFactory.class).setDefaultProtocol("http/1.1");

    }

}
