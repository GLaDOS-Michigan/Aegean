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

package Applications.jetty.websocket.jsr356.endpoints;

import Applications.jetty.websocket.api.WebSocketPolicy;
import Applications.jetty.websocket.common.events.EventDriverFactory;
import Applications.jetty.websocket.jsr356.client.JsrClientEndpointImpl;

public class JsrEventDriverFactory extends EventDriverFactory {
    public JsrEventDriverFactory(WebSocketPolicy policy) {
        super(policy);

        clearImplementations();
        // Classes that extend javax.websocket.Endpoint
        addImplementation(new JsrEndpointImpl());
        // Classes annotated with @javax.websocket.ClientEndpoint
        addImplementation(new JsrClientEndpointImpl());
    }

    /**
     * Unwrap ConfiguredEndpoint for end-user.
     */
    @Override
    protected String getClassName(Object websocket) {
        if (websocket instanceof EndpointInstance) {
            EndpointInstance ce = (EndpointInstance) websocket;
            return ce.getEndpoint().getClass().getName();
        }

        return websocket.getClass().getName();
    }
}
