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

import Applications.jetty.websocket.api.UpgradeRequest;
import Applications.jetty.websocket.api.UpgradeResponse;
import Applications.jetty.websocket.client.io.UpgradeListener;

import javax.websocket.ClientEndpointConfig.Configurator;
import java.util.List;
import java.util.Map;

public class JsrUpgradeListener implements UpgradeListener {
    private Configurator configurator;

    public JsrUpgradeListener(Configurator configurator) {
        this.configurator = configurator;
    }

    @Override
    public void onHandshakeRequest(UpgradeRequest request) {
        if (configurator == null) {
            return;
        }

        Map<String, List<String>> headers = request.getHeaders();
        configurator.beforeRequest(headers);
        request.setHeaders(headers);
    }

    @Override
    public void onHandshakeResponse(UpgradeResponse response) {
        if (configurator == null) {
            return;
        }

        JsrHandshakeResponse hr = new JsrHandshakeResponse(response);
        configurator.afterResponse(hr);
    }
}
