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

package Applications.jetty.websocket.jsr356.endpoints;

import Applications.jetty.util.BufferUtil;
import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;
import Applications.jetty.websocket.api.WebSocketPolicy;
import Applications.jetty.websocket.api.extensions.Frame;
import Applications.jetty.websocket.common.events.EventDriver;
import Applications.jetty.websocket.common.message.MessageInputStream;
import Applications.jetty.websocket.common.message.MessageReader;
import Applications.jetty.websocket.common.message.SimpleBinaryMessage;
import Applications.jetty.websocket.common.message.SimpleTextMessage;
import Applications.jetty.websocket.jsr356.JsrSession;
import Applications.jetty.websocket.jsr356.annotations.JsrEvents;
import Applications.jetty.websocket.jsr356.messages.BinaryPartialOnMessage;
import Applications.jetty.websocket.jsr356.messages.TextPartialOnMessage;

import javax.websocket.CloseReason;
import javax.websocket.DecodeException;
import javax.websocket.MessageHandler.Whole;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.util.Map;

/**
 * Base implementation for JSR-356 Annotated event drivers.
 */
public class JsrAnnotatedEventDriver extends AbstractJsrEventDriver implements EventDriver {
    private static final Logger LOG = Log.getLogger(JsrAnnotatedEventDriver.class);
    private final JsrEvents<?, ?> events;

    public JsrAnnotatedEventDriver(WebSocketPolicy policy, EndpointInstance endpointInstance, JsrEvents<?, ?> events) {
        super(policy, endpointInstance);
        this.events = events;
    }

    @Override
    public void init(JsrSession jsrsession) {
        this.events.init(jsrsession);
    }

    /**
     * Entry point for all incoming binary frames.
     */
    @Override
    public void onBinaryFrame(ByteBuffer buffer, boolean fin) throws IOException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("onBinaryFrame({}, {})", BufferUtil.toDetailString(buffer), fin);
            LOG.debug("events.onBinary={}", events.hasBinary());
            LOG.debug("events.onBinaryStream={}", events.hasBinaryStream());
        }
        boolean handled = false;

        if (events.hasBinary()) {
            handled = true;
            if (activeMessage == null) {
                if (events.isBinaryPartialSupported()) {
                    // Partial Message Support (does not use messageAppender)
                    LOG.debug("Partial Binary Message: fin={}", fin);
                    activeMessage = new BinaryPartialOnMessage(this);
                } else {
                    // Whole Message Support
                    LOG.debug("Whole Binary Message");
                    activeMessage = new SimpleBinaryMessage(this);
                }
            }
        }

        if (events.hasBinaryStream()) {
            handled = true;
            // Streaming Message Support
            if (activeMessage == null) {
                LOG.debug("Binary Message InputStream");
                final MessageInputStream stream = new MessageInputStream(session.getConnection());
                activeMessage = stream;

                // Always dispatch streaming read to another thread.
                dispatch(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            events.callBinaryStream(jsrsession.getAsyncRemote(), websocket, stream);
                        } catch (DecodeException | IOException e) {
                            onFatalError(e);
                        }
                    }
                });
            }
        }

        LOG.debug("handled = {}", handled);

        // Process any active MessageAppender
        if (handled && (activeMessage != null)) {
            appendMessage(buffer, fin);
        }
    }

    /**
     * Entry point for binary frames destined for {@link Whole}
     */
    @Override
    public void onBinaryMessage(byte[] data) {
        if (data == null) {
            return;
        }

        ByteBuffer buf = ByteBuffer.wrap(data);

        if (LOG.isDebugEnabled()) {
            LOG.debug("onBinaryMessage({})", BufferUtil.toDetailString(buf));
        }

        try {
            // FIN is always true here
            events.callBinary(jsrsession.getAsyncRemote(), websocket, buf, true);
        } catch (DecodeException e) {
            onFatalError(e);
        }
    }

    @Override
    protected void onClose(CloseReason closereason) {
        events.callClose(websocket, closereason);
    }

    @Override
    public void onConnect() {
        events.callOpen(websocket, config);
    }

    @Override
    public void onError(Throwable cause) {
        events.callError(websocket, cause);
    }

    private void onFatalError(Throwable t) {
        onError(t);
    }

    @Override
    public void onFrame(Frame frame) {
        /* Ignored in JSR-356 */
    }

    @Override
    public void onInputStream(InputStream stream) {
        try {
            events.callBinaryStream(jsrsession.getAsyncRemote(), websocket, stream);
        } catch (DecodeException | IOException e) {
            onFatalError(e);
        }
    }

    public void onPartialBinaryMessage(ByteBuffer buffer, boolean fin) {
        try {
            events.callBinary(jsrsession.getAsyncRemote(), websocket, buffer, fin);
        } catch (DecodeException e) {
            onFatalError(e);
        }
    }

    public void onPartialTextMessage(String message, boolean fin) {
        try {
            events.callText(jsrsession.getAsyncRemote(), websocket, message, fin);
        } catch (DecodeException e) {
            onFatalError(e);
        }
    }

    @Override
    public void onPong(ByteBuffer buffer) {
        try {
            events.callPong(jsrsession.getAsyncRemote(), websocket, buffer);
        } catch (DecodeException | IOException e) {
            onFatalError(e);
        }
    }

    @Override
    public void onReader(Reader reader) {
        try {
            events.callTextStream(jsrsession.getAsyncRemote(), websocket, reader);
        } catch (DecodeException | IOException e) {
            onFatalError(e);
        }
    }

    /**
     * Entry point for all incoming text frames.
     */
    @Override
    public void onTextFrame(ByteBuffer buffer, boolean fin) throws IOException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("onTextFrame({}, {})", BufferUtil.toDetailString(buffer), fin);
            LOG.debug("events.hasText={}", events.hasText());
            LOG.debug("events.hasTextStream={}", events.hasTextStream());
        }

        boolean handled = false;

        if (events.hasText()) {
            handled = true;
            if (activeMessage == null) {
                if (events.isTextPartialSupported()) {
                    // Partial Message Support
                    LOG.debug("Partial Text Message: fin={}", fin);
                    activeMessage = new TextPartialOnMessage(this);
                } else {
                    // Whole Message Support
                    LOG.debug("Whole Text Message");
                    activeMessage = new SimpleTextMessage(this);
                }
            }
        }

        if (events.hasTextStream()) {
            handled = true;
            // Streaming Message Support
            if (activeMessage == null) {
                LOG.debug("Text Message Writer");

                final MessageReader stream = new MessageReader(new MessageInputStream(session.getConnection()));
                activeMessage = stream;

                // Always dispatch streaming read to another thread.
                dispatch(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            events.callTextStream(jsrsession.getAsyncRemote(), websocket, stream);
                        } catch (DecodeException | IOException e) {
                            onFatalError(e);
                        }
                    }
                });
            }
        }

        LOG.debug("handled = {}", handled);

        // Process any active MessageAppender
        if (handled && (activeMessage != null)) {
            appendMessage(buffer, fin);
        }
    }

    /**
     * Entry point for whole text messages
     */
    @Override
    public void onTextMessage(String message) {
        LOG.debug("onText({})", message);

        try {
            // FIN is always true here
            events.callText(jsrsession.getAsyncRemote(), websocket, message, true);
        } catch (DecodeException e) {
            onFatalError(e);
        }
    }

    @Override
    public void setPathParameters(Map<String, String> pathParameters) {
        events.setPathParameters(pathParameters);
    }

    @Override
    public String toString() {
        return String.format("%s[websocket=%s]", this.getClass().getSimpleName(), websocket);
    }
}
