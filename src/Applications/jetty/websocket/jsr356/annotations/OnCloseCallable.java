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

import Applications.jetty.websocket.common.CloseInfo;
import Applications.jetty.websocket.jsr356.JsrSession;
import Applications.jetty.websocket.jsr356.annotations.Param.Role;

import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.Decoder;
import javax.websocket.OnClose;
import java.lang.reflect.Method;

/**
 * Callable for {@link OnClose} annotated methods
 */
public class OnCloseCallable extends JsrCallable {
    private int idxCloseReason = -1;

    public OnCloseCallable(Class<?> pojo, Method method) {
        super(pojo, method);
    }

    public OnCloseCallable(OnCloseCallable copy) {
        super(copy);
        this.idxCloseReason = copy.idxCloseReason;
    }

    public void call(Object endpoint, CloseInfo close) {
        this.call(endpoint, close.getStatusCode(), close.getReason());
    }

    public void call(Object endpoint, CloseReason closeReason) {
        // Close Reason is an optional parameter
        if (idxCloseReason >= 0) {
            // convert to javax.websocket.CloseReason
            super.args[idxCloseReason] = closeReason;
        }
        super.call(endpoint, super.args);
    }

    public void call(Object endpoint, int statusCode, String reason) {
        // Close Reason is an optional parameter
        if (idxCloseReason >= 0) {
            // convert to javax.websocket.CloseReason
            CloseReason jsrclose = new CloseReason(CloseCodes.getCloseCode(statusCode), reason);
            super.args[idxCloseReason] = jsrclose;
        }
        super.call(endpoint, super.args);
    }

    @Override
    public void init(JsrSession session) {
        idxCloseReason = findIndexForRole(Role.CLOSE_REASON);
        super.init(session);
    }

    @Override
    public void setDecoderClass(Class<? extends Decoder> decoderClass) {
        /* ignore, not relevant for onClose */
    }
}
