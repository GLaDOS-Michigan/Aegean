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

package Applications.jetty.websocket.jsr356.client;

import Applications.jetty.websocket.api.WebSocketPolicy;
import Applications.jetty.websocket.common.events.EventDriver;
import Applications.jetty.websocket.common.events.EventDriverImpl;
import Applications.jetty.websocket.jsr356.annotations.JsrEvents;
import Applications.jetty.websocket.jsr356.endpoints.EndpointInstance;
import Applications.jetty.websocket.jsr356.endpoints.JsrAnnotatedEventDriver;

import javax.websocket.ClientEndpoint;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;

/**
 * Event Driver for classes annotated with &#064;{@link ClientEndpoint}
 */
public class JsrClientEndpointImpl implements EventDriverImpl {
    @Override
    public EventDriver create(Object websocket, WebSocketPolicy policy) throws DeploymentException {
        if (!(websocket instanceof EndpointInstance)) {
            throw new IllegalStateException(String.format("Websocket %s must be an %s", websocket.getClass().getName(), EndpointInstance.class.getName()));
        }

        EndpointInstance ei = (EndpointInstance) websocket;
        AnnotatedClientEndpointMetadata metadata = (AnnotatedClientEndpointMetadata) ei.getMetadata();
        JsrEvents<ClientEndpoint, ClientEndpointConfig> events = new JsrEvents<>(metadata);

        return new JsrAnnotatedEventDriver(policy, ei, events);
    }

    @Override
    public String describeRule() {
        return "class is annotated with @" + ClientEndpoint.class.getName();
    }

    @Override
    public boolean supports(Object websocket) {
        if (!(websocket instanceof EndpointInstance)) {
            return false;
        }

        EndpointInstance ei = (EndpointInstance) websocket;
        Object endpoint = ei.getEndpoint();

        ClientEndpoint anno = endpoint.getClass().getAnnotation(ClientEndpoint.class);
        return (anno != null);
    }
}
