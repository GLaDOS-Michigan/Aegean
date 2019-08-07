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

import Applications.jetty.websocket.common.events.annotated.InvalidSignatureException;
import Applications.jetty.websocket.jsr356.annotations.Param.Role;
import Applications.jetty.websocket.jsr356.decoders.PongMessageDecoder;

import javax.websocket.PongMessage;

public class JsrParamIdPong extends JsrParamIdOnMessage implements IJsrParamId {
    public static final IJsrParamId INSTANCE = new JsrParamIdPong();

    @Override
    public boolean process(Param param, JsrCallable callable) throws InvalidSignatureException {
        if (super.process(param, callable)) {
            // Found common roles
            return true;
        }

        if (param.type.isAssignableFrom(PongMessage.class)) {
            assertPartialMessageSupportDisabled(param, callable);
            param.bind(Role.MESSAGE_PONG);
            callable.setDecoderClass(PongMessageDecoder.class);
            return true;
        }
        return false;
    }
}
