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
import Applications.jetty.websocket.jsr356.MessageHandlerWrapper;

import javax.websocket.MessageHandler;
import javax.websocket.MessageHandler.Partial;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Partial TEXT MessageAppender for MessageHandler.Partial interface
 */
public class TextPartialMessage implements MessageAppender {
    @SuppressWarnings("unused")
    private final MessageHandlerWrapper msgWrapper;
    private final MessageHandler.Partial<String> partialHandler;

    @SuppressWarnings("unchecked")
    public TextPartialMessage(MessageHandlerWrapper wrapper) {
        this.msgWrapper = wrapper;
        this.partialHandler = (Partial<String>) wrapper.getHandler();
    }

    @Override
    public void appendMessage(ByteBuffer payload, boolean isLast) throws IOException {
        // No decoders for Partial messages per JSR-356 (PFD1 spec)
        partialHandler.onMessage(BufferUtil.toUTF8String(payload.slice()), isLast);
    }

    @Override
    public void messageComplete() {
        /* nothing to do here */
    }
}
