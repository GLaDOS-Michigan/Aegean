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
 * Partial BINARY MessageAppender for MessageHandler.Partial interface
 */
public class BinaryPartialMessage implements MessageAppender {
    private final MessageHandlerWrapper msgWrapper;
    private final MessageHandler.Partial<Object> partialHandler;

    @SuppressWarnings("unchecked")
    public BinaryPartialMessage(MessageHandlerWrapper wrapper) {
        this.msgWrapper = wrapper;
        this.partialHandler = (Partial<Object>) wrapper.getHandler();
    }

    @Override
    public void appendMessage(ByteBuffer payload, boolean isLast) throws IOException {
        // No decoders for Partial messages per JSR-356 (PFD1 spec)

        // Supported Partial<> Type #1: ByteBuffer
        if (msgWrapper.isMessageType(ByteBuffer.class)) {
            partialHandler.onMessage(payload.slice(), isLast);
            return;
        }

        // Supported Partial<> Type #2: byte[]
        if (msgWrapper.isMessageType(byte[].class)) {
            partialHandler.onMessage(BufferUtil.toArray(payload), isLast);
            return;
        }

        StringBuilder err = new StringBuilder();
        err.append(msgWrapper.getHandler().getClass());
        err.append(" does not implement an expected ");
        err.append(MessageHandler.Partial.class.getName());
        err.append(" of type ");
        err.append(ByteBuffer.class.getName());
        err.append(" or byte[]");
        throw new IllegalStateException(err.toString());
    }

    @Override
    public void messageComplete() {
        /* nothing to do here */
    }
}
