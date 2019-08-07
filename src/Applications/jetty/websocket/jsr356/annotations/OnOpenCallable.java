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

package Applications.jetty.websocket.jsr356.annotations;

import Applications.jetty.websocket.jsr356.JsrSession;
import Applications.jetty.websocket.jsr356.annotations.Param.Role;

import javax.websocket.Decoder;
import javax.websocket.EndpointConfig;
import javax.websocket.OnOpen;
import java.lang.reflect.Method;

/**
 * Callable for {@link OnOpen} annotated methods
 */
public class OnOpenCallable extends JsrCallable {
    private int idxEndpointConfig = -1;

    public OnOpenCallable(Class<?> pojo, Method method) {
        super(pojo, method);
    }

    public OnOpenCallable(OnOpenCallable copy) {
        super(copy);
        this.idxEndpointConfig = copy.idxEndpointConfig;
    }

    public void call(Object endpoint, EndpointConfig config) {
        // EndpointConfig is an optional parameter
        if (idxEndpointConfig >= 0) {
            super.args[idxEndpointConfig] = config;
        }
        super.call(endpoint, super.args);
    }

    @Override
    public void init(JsrSession session) {
        idxEndpointConfig = findIndexForRole(Role.ENDPOINT_CONFIG);
        super.init(session);
    }

    @Override
    public void setDecoderClass(Class<? extends Decoder> decoderClass) {
        /* ignore, not relevant for onClose */
    }
}
