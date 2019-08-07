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

import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;
import Applications.jetty.websocket.api.WebSocketListener;
import Applications.jetty.websocket.api.WebSocketPolicy;
import Applications.jetty.websocket.api.extensions.Frame;
import Applications.jetty.websocket.common.CloseInfo;
import Applications.jetty.websocket.common.message.SimpleBinaryMessage;
import Applications.jetty.websocket.common.message.SimpleTextMessage;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;

/**
 * Handler for {@link WebSocketListener} based User WebSocket implementations.
 */
public class JettyListenerEventDriver extends AbstractEventDriver {
    private static final Logger LOG = Log.getLogger(JettyListenerEventDriver.class);
    private final WebSocketListener listener;
    private boolean hasCloseBeenCalled = false;

    public JettyListenerEventDriver(WebSocketPolicy policy, WebSocketListener listener) {
        super(policy, listener);
        this.listener = listener;
    }

    @Override
    public void onBinaryFrame(ByteBuffer buffer, boolean fin) throws IOException {
        if (activeMessage == null) {
            activeMessage = new SimpleBinaryMessage(this);
        }

        appendMessage(buffer, fin);
    }

    @Override
    public void onBinaryMessage(byte[] data) {
        listener.onWebSocketBinary(data, 0, data.length);
    }

    @Override
    public void onClose(CloseInfo close) {
        if (hasCloseBeenCalled) {
            // avoid duplicate close events (possible when using harsh Session.disconnect())
            return;
        }
        hasCloseBeenCalled = true;

        int statusCode = close.getStatusCode();
        String reason = close.getReason();
        listener.onWebSocketClose(statusCode, reason);
    }

    @Override
    public void onConnect() {
        LOG.debug("onConnect()");
        listener.onWebSocketConnect(session);
    }

    @Override
    public void onError(Throwable cause) {
        listener.onWebSocketError(cause);
    }

    @Override
    public void onFrame(Frame frame) {
        /* ignore, not supported by WebSocketListener */
    }

    @Override
    public void onInputStream(InputStream stream) {
        /* not supported in Listener mode (yet) */
    }

    @Override
    public void onReader(Reader reader) {
        /* not supported in Listener mode (yet) */
    }

    @Override
    public void onTextFrame(ByteBuffer buffer, boolean fin) throws IOException {
        if (activeMessage == null) {
            activeMessage = new SimpleTextMessage(this);
        }

        appendMessage(buffer, fin);
    }

    @Override
    public void onTextMessage(String message) {
        listener.onWebSocketText(message);
    }

    @Override
    public String toString() {
        return String.format("%s[%s]", JettyListenerEventDriver.class.getSimpleName(), listener.getClass().getName());
    }
}
