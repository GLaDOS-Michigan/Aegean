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


import Applications.jetty.io.Connection;
import Applications.jetty.io.EndPoint;

/**
 * <p>A Factory to create {@link Connection} instances for {@link Connector}s.</p>
 * <p>A Connection factory is responsible for instantiating and configuring a {@link Connection} instance
 * to handle an {@link EndPoint} accepted by a {@link Connector}.</p>
 * <p>
 * A ConnectionFactory has a protocol name that represents the protocol of the Connections
 * created.  Example of protocol names include:<dl>
 * <dt>http</dt><dd>Creates a HTTP connection that can handle multiple versions of HTTP from 0.9 to 1.1</dd>
 * <dt>spdy/2</dt><dd>Creates a HTTP connection that handles a specific version of the SPDY protocol</dd>
 * <dt>SSL-XYZ</dt><dd>Create an SSL connection chained to a connection obtained from a connection factory
 * with a protocol "XYZ".</dd>
 * <dt>SSL-http</dt><dd>Create an SSL connection chained to a HTTP connection (aka https)</dd>
 * <dt>SSL-npn</dt><dd>Create an SSL connection chained to a NPN connection, that uses a negotiation with
 * the client to determine the next protocol.</dd>
 * </dl>
 */
public interface ConnectionFactory {
    /* ------------------------------------------------------------ */

    /**
     * @return A string representing the protocol name.
     */
    public String getProtocol();

    /**
     * <p>Creates a new {@link Connection} with the given parameters</p>
     *
     * @param connector The {@link Connector} creating this connection
     * @param endPoint  the {@link EndPoint} associated with the connection
     * @return a new {@link Connection}
     */
    public Connection newConnection(Connector connector, EndPoint endPoint);

}
