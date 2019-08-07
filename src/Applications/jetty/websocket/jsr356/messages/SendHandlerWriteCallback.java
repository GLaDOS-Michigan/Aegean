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

package Applications.jetty.websocket.jsr356.messages;

import Applications.jetty.websocket.api.WriteCallback;

import javax.websocket.SendHandler;
import javax.websocket.SendResult;

public class SendHandlerWriteCallback implements WriteCallback {
    private final SendHandler sendHandler;

    public SendHandlerWriteCallback(SendHandler sendHandler) {
        this.sendHandler = sendHandler;
    }

    @Override
    public void writeFailed(Throwable x) {
        sendHandler.onResult(new SendResult(x));
    }

    @Override
    public void writeSuccess() {
        sendHandler.onResult(new SendResult());
    }
}
