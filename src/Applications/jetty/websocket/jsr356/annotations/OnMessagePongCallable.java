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

import Applications.jetty.websocket.jsr356.JsrPongMessage;
import Applications.jetty.websocket.jsr356.JsrSession;
import Applications.jetty.websocket.jsr356.annotations.Param.Role;

import javax.websocket.DecodeException;
import javax.websocket.OnMessage;
import javax.websocket.PongMessage;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

/**
 * Callable for {@link OnMessage} annotated methods with a {@link PongMessage} message object.
 */
public class OnMessagePongCallable extends OnMessageCallable {
    public OnMessagePongCallable(Class<?> pojo, Method method) {
        super(pojo, method);
    }

    /**
     * Copy Constructor
     */
    public OnMessagePongCallable(OnMessageCallable copy) {
        super(copy);
    }

    public Object call(Object endpoint, ByteBuffer buf) throws DecodeException {
        super.args[idxMessageObject] = new JsrPongMessage(buf);
        return super.call(endpoint, super.args);
    }

    @Override
    public void init(JsrSession session) {
        idxMessageObject = findIndexForRole(Role.MESSAGE_PONG);
        assertRoleRequired(idxMessageObject, "Pong Message Object");
        super.init(session);
    }
}
