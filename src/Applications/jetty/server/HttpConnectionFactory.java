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


package Applications.jetty.server;


import Applications.jetty.http.HttpVersion;
import Applications.jetty.io.Connection;
import Applications.jetty.io.EndPoint;
import Applications.jetty.util.annotation.Name;


/* ------------------------------------------------------------ */

/**
 * A Connection Factory for HTTP Connections.
 * <p>Accepts connections either directly or via SSL and/or NPN chained connection factories.  The accepted
 * {@link HttpConnection}s are configured by a {@link HttpConfiguration} instance that is either created by
 * default or passed in to the constructor.
 */
public class HttpConnectionFactory extends AbstractConnectionFactory implements HttpConfiguration.ConnectionFactory {
    private final HttpConfiguration _config;

    public HttpConnectionFactory() {
        this(new HttpConfiguration());
        setInputBufferSize(16384);
    }

    public HttpConnectionFactory(@Name("config") HttpConfiguration config) {
        super(HttpVersion.HTTP_1_1.toString());
        _config = config;
        addBean(_config);
    }

    @Override
    public HttpConfiguration getHttpConfiguration() {
        return _config;
    }

    @Override
    public Connection newConnection(Connector connector, EndPoint endPoint) {
        return configure(new HttpConnection(_config, connector, endPoint), connector, endPoint);
    }

}
