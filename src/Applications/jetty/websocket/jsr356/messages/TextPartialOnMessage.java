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

import Applications.jetty.util.BufferUtil;
import Applications.jetty.websocket.common.message.MessageAppender;
import Applications.jetty.websocket.jsr356.endpoints.JsrAnnotatedEventDriver;

import javax.websocket.OnMessage;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Partial TEXT MessageAppender for &#064;{@link OnMessage} annotated methods
 */
public class TextPartialOnMessage implements MessageAppender {
    private final JsrAnnotatedEventDriver driver;
    private boolean finished;

    public TextPartialOnMessage(JsrAnnotatedEventDriver driver) {
        this.driver = driver;
        this.finished = false;
    }

    @Override
    public void appendMessage(ByteBuffer payload, boolean isLast) throws IOException {
        if (finished) {
            throw new IOException("Cannot append to finished buffer");
        }
        if (payload == null) {
            driver.onPartialTextMessage("", isLast);
        } else {
            String text = BufferUtil.toUTF8String(payload);
            driver.onPartialTextMessage(text, isLast);
        }
    }

    @Override
    public void messageComplete() {
        finished = true;
    }
}
