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
import Applications.jetty.io.ssl.SslConnection;
import Applications.jetty.util.annotation.Name;
import Applications.jetty.util.ssl.SslContextFactory;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;

public class SslConnectionFactory extends AbstractConnectionFactory {
    private final SslContextFactory _sslContextFactory;
    private final String _nextProtocol;

    public SslConnectionFactory() {
        this(HttpVersion.HTTP_1_1.asString());
    }

    public SslConnectionFactory(@Name("next") String nextProtocol) {
        this(null, nextProtocol);
    }

    public SslConnectionFactory(@Name("sslContextFactory") SslContextFactory factory, @Name("next") String nextProtocol) {
        super("SSL-" + nextProtocol);
        _sslContextFactory = factory == null ? new SslContextFactory() : factory;
        _nextProtocol = nextProtocol;
        addBean(_sslContextFactory);
    }

    public SslContextFactory getSslContextFactory() {
        return _sslContextFactory;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        SSLEngine engine = _sslContextFactory.newSSLEngine();
        engine.setUseClientMode(false);
        SSLSession session = engine.getSession();

        if (session.getPacketBufferSize() > getInputBufferSize())
            setInputBufferSize(session.getPacketBufferSize());
    }

    @Override
    public Connection newConnection(Connector connector, EndPoint endPoint) {
        SSLEngine engine = _sslContextFactory.newSSLEngine(endPoint.getRemoteAddress());
        engine.setUseClientMode(false);

        SslConnection sslConnection = newSslConnection(connector, endPoint, engine);
        sslConnection.setRenegotiationAllowed(_sslContextFactory.isRenegotiationAllowed());
        configure(sslConnection, connector, endPoint);

        ConnectionFactory next = connector.getConnectionFactory(_nextProtocol);
        EndPoint decryptedEndPoint = sslConnection.getDecryptedEndPoint();
        Connection connection = next.newConnection(connector, decryptedEndPoint);
        decryptedEndPoint.setConnection(connection);

        return sslConnection;
    }

    protected SslConnection newSslConnection(Connector connector, EndPoint endPoint, SSLEngine engine) {
        return new SslConnection(connector.getByteBufferPool(), connector.getExecutor(), endPoint, engine);
    }

    @Override
    public String toString() {
        return String.format("%s@%x{%s}", this.getClass().getSimpleName(), hashCode(), getProtocol());
    }
}
