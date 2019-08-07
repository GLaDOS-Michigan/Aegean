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

package Applications.jetty.websocket.jsr356.server;

import Applications.jetty.websocket.api.WebSocketPolicy;
import Applications.jetty.websocket.common.events.EventDriver;
import Applications.jetty.websocket.common.events.EventDriverImpl;
import Applications.jetty.websocket.jsr356.annotations.JsrEvents;
import Applications.jetty.websocket.jsr356.endpoints.EndpointInstance;
import Applications.jetty.websocket.jsr356.endpoints.JsrAnnotatedEventDriver;

import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;

/**
 * Event Driver for classes annotated with &#064;{@link ServerEndpoint}
 */
public class JsrServerEndpointImpl implements EventDriverImpl {
    @Override
    public EventDriver create(Object websocket, WebSocketPolicy policy) throws Throwable {
        if (!(websocket instanceof EndpointInstance)) {
            throw new IllegalStateException(String.format("Websocket %s must be an %s", websocket.getClass().getName(), EndpointInstance.class.getName()));
        }

        EndpointInstance ei = (EndpointInstance) websocket;
        AnnotatedServerEndpointMetadata metadata = (AnnotatedServerEndpointMetadata) ei.getMetadata();
        JsrEvents<ServerEndpoint, ServerEndpointConfig> events = new JsrEvents<>(metadata);
        JsrAnnotatedEventDriver driver = new JsrAnnotatedEventDriver(policy, ei, events);

        ServerEndpointConfig config = (ServerEndpointConfig) ei.getConfig();
        if (config instanceof PathParamServerEndpointConfig) {
            PathParamServerEndpointConfig ppconfig = (PathParamServerEndpointConfig) config;
            driver.setPathParameters(ppconfig.getPathParamMap());
        }

        return driver;
    }

    @Override
    public String describeRule() {
        return "class is annotated with @" + ServerEndpoint.class.getName();
    }

    @Override
    public boolean supports(Object websocket) {
        if (!(websocket instanceof EndpointInstance)) {
            return false;
        }

        EndpointInstance ei = (EndpointInstance) websocket;
        Object endpoint = ei.getEndpoint();

        ServerEndpoint anno = endpoint.getClass().getAnnotation(ServerEndpoint.class);
        return (anno != null);
    }
}
