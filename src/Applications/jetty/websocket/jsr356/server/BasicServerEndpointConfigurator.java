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

import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;
import Applications.jetty.websocket.api.util.QuoteUtil;

import javax.websocket.Extension;
import javax.websocket.HandshakeResponse;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;
import javax.websocket.server.ServerEndpointConfig.Configurator;
import java.util.List;

public class BasicServerEndpointConfigurator extends Configurator {
    private static final Logger LOG = Log.getLogger(BasicServerEndpointConfigurator.class);
    public static final Configurator INSTANCE = new BasicServerEndpointConfigurator();

    @Override
    public boolean checkOrigin(String originHeaderValue) {
        return true;
    }

    @Override
    public <T> T getEndpointInstance(Class<T> endpointClass) throws InstantiationException {
        LOG.debug(".getEndpointInstance({})", endpointClass);
        try {
            return endpointClass.newInstance();
        } catch (IllegalAccessException e) {
            throw new InstantiationException(String.format("%s: %s", e.getClass().getName(), e.getMessage()));
        }
    }

    @Override
    public List<Extension> getNegotiatedExtensions(List<Extension> installed, List<Extension> requested) {
        return requested;
    }

    @Override
    public String getNegotiatedSubprotocol(List<String> supported, List<String> requested) {
        if ((requested == null) || (requested.size() == 0)) {
            // nothing requested, don't return anything
            return null;
        }

        // Nothing specifically called out as being supported by the endpoint
        if ((supported == null) || (supported.isEmpty())) {
            // Just return the first hit in this case
            LOG.warn("Client requested Subprotocols on endpoint with none supported: {}", QuoteUtil.join(requested, ","));
            return null;
        }

        // Return the first matching hit from the list of supported protocols.
        for (String possible : requested) {
            if (supported.contains(possible)) {
                return possible;
            }
        }

        LOG.warn("Client requested subprotocols {} do not match any endpoint supported subprotocols {}", QuoteUtil.join(requested, ","), QuoteUtil.join(supported, ","));
        return null;
    }

    @Override
    public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
        /* do nothing */
    }
}
