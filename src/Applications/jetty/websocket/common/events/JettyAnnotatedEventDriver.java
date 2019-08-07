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

package Applications.jetty.websocket.common.events;

import Applications.jetty.websocket.api.WebSocketPolicy;
import Applications.jetty.websocket.api.annotations.WebSocket;
import Applications.jetty.websocket.api.extensions.Frame;
import Applications.jetty.websocket.common.CloseInfo;
import Applications.jetty.websocket.common.message.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;

/**
 * Handler for Annotated User WebSocket objects.
 */
public class JettyAnnotatedEventDriver extends AbstractEventDriver {
    private final JettyAnnotatedMetadata events;
    private boolean hasCloseBeenCalled = false;

    public JettyAnnotatedEventDriver(WebSocketPolicy policy, Object websocket, JettyAnnotatedMetadata events) {
        super(policy, websocket);
        this.events = events;

        WebSocket anno = websocket.getClass().getAnnotation(WebSocket.class);
        // Setup the policy
        if (anno.maxTextMessageSize() > 0) {
            this.policy.setMaxTextMessageSize(anno.maxTextMessageSize());
        }
        if (anno.maxBinaryMessageSize() > 0) {
            this.policy.setMaxBinaryMessageSize(anno.maxBinaryMessageSize());
        }
        if (anno.inputBufferSize() > 0) {
            this.policy.setInputBufferSize(anno.inputBufferSize());
        }
        if (anno.maxIdleTime() > 0) {
            this.policy.setIdleTimeout(anno.maxIdleTime());
        }
    }

    @Override
    public void onBinaryFrame(ByteBuffer buffer, boolean fin) throws IOException {
        if (events.onBinary == null) {
            // not interested in binary events
            return;
        }

        if (activeMessage == null) {
            if (events.onBinary.isStreaming()) {
                activeMessage = new MessageInputStream(session.getConnection());
                final MessageAppender msg = activeMessage;
                dispatch(new Runnable() {
                    @Override
                    public void run() {
                        events.onBinary.call(websocket, session, msg);
                    }
                });
            } else {
                activeMessage = new SimpleBinaryMessage(this);
            }
        }

        appendMessage(buffer, fin);
    }

    @Override
    public void onBinaryMessage(byte[] data) {
        if (events.onBinary != null) {
            events.onBinary.call(websocket, session, data, 0, data.length);
        }
    }

    @Override
    public void onClose(CloseInfo close) {
        if (hasCloseBeenCalled) {
            // avoid duplicate close events (possible when using harsh Session.disconnect())
            return;
        }
        hasCloseBeenCalled = true;
        if (events.onClose != null) {
            events.onClose.call(websocket, session, close.getStatusCode(), close.getReason());
        }
    }

    @Override
    public void onConnect() {
        if (events.onConnect != null) {
            events.onConnect.call(websocket, session);
        }
    }

    @Override
    public void onError(Throwable cause) {
        if (events.onError != null) {
            events.onError.call(websocket, session, cause);
        }
    }

    @Override
    public void onFrame(Frame frame) {
        if (events.onFrame != null) {
            events.onFrame.call(websocket, session, frame);
        }
    }

    @Override
    public void onInputStream(InputStream stream) {
        if (events.onBinary != null) {
            events.onBinary.call(websocket, session, stream);
        }
    }

    @Override
    public void onReader(Reader reader) {
        if (events.onText != null) {
            events.onText.call(websocket, session, reader);
        }
    }

    @Override
    public void onTextFrame(ByteBuffer buffer, boolean fin) throws IOException {
        if (events.onText == null) {
            // not interested in text events
            return;
        }

        if (activeMessage == null) {
            if (events.onText.isStreaming()) {
                activeMessage = new MessageReader(new MessageInputStream(session.getConnection()));
                final MessageAppender msg = activeMessage;
                dispatch(new Runnable() {
                    @Override
                    public void run() {
                        events.onText.call(websocket, session, msg);
                    }
                });
            } else {
                activeMessage = new SimpleTextMessage(this);
            }
        }

        appendMessage(buffer, fin);
    }

    @Override
    public void onTextMessage(String message) {
        if (events.onText != null) {
            events.onText.call(websocket, session, message);
        }
    }

    @Override
    public String toString() {
        return String.format("%s[%s]", this.getClass().getSimpleName(), websocket);
    }
}
