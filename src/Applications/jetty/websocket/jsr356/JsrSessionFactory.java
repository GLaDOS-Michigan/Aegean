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

package Applications.jetty.websocket.jsr356;

import Applications.jetty.websocket.common.LogicalConnection;
import Applications.jetty.websocket.common.SessionFactory;
import Applications.jetty.websocket.common.WebSocketSession;
import Applications.jetty.websocket.common.events.EventDriver;
import Applications.jetty.websocket.jsr356.endpoints.AbstractJsrEventDriver;

import java.net.URI;
import java.util.concurrent.atomic.AtomicLong;

public class JsrSessionFactory implements SessionFactory {
    private AtomicLong idgen = new AtomicLong(0);
    private final ClientContainer container;

    public JsrSessionFactory(ClientContainer container) {
        this.container = container;
    }

    @Override
    public WebSocketSession createSession(URI requestURI, EventDriver websocket, LogicalConnection connection) {
        return new JsrSession(requestURI, websocket, connection, container, getNextId());
    }

    public String getNextId() {
        return String.format("websocket-%d", idgen.incrementAndGet());
    }

    @Override
    public boolean supports(EventDriver websocket) {
        return (websocket instanceof AbstractJsrEventDriver);
    }
}
