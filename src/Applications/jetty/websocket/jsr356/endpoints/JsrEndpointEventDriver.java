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

import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;
import Applications.jetty.websocket.api.WebSocketPolicy;
import Applications.jetty.websocket.api.extensions.Frame;
import Applications.jetty.websocket.common.events.EventDriver;
import Applications.jetty.websocket.common.message.MessageInputStream;
import Applications.jetty.websocket.common.message.MessageReader;
import Applications.jetty.websocket.jsr356.JsrSession;
import Applications.jetty.websocket.jsr356.MessageHandlerWrapper;
import Applications.jetty.websocket.jsr356.MessageType;
import Applications.jetty.websocket.jsr356.messages.BinaryPartialMessage;
import Applications.jetty.websocket.jsr356.messages.BinaryWholeMessage;
import Applications.jetty.websocket.jsr356.messages.TextPartialMessage;
import Applications.jetty.websocket.jsr356.messages.TextWholeMessage;

import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.MessageHandler;
import javax.websocket.MessageHandler.Whole;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.util.Map;

/**
 * EventDriver for websocket that extend from {@link javax.websocket.Endpoint}
 */
public class JsrEndpointEventDriver extends AbstractJsrEventDriver implements EventDriver {
    private static final Logger LOG = Log.getLogger(JsrEndpointEventDriver.class);

    private final Endpoint endpoint;
    private Map<String, String> pathParameters;

    public JsrEndpointEventDriver(WebSocketPolicy policy, EndpointInstance endpointInstance) {
        super(policy, endpointInstance);
        this.endpoint = (Endpoint) endpointInstance.getEndpoint();
    }

    @Override
    public void init(JsrSession jsrsession) {
        jsrsession.setPathParameters(pathParameters);
    }

    @Override
    public void onBinaryFrame(ByteBuffer buffer, boolean fin) throws IOException {
        if (activeMessage == null) {
            final MessageHandlerWrapper wrapper = jsrsession.getMessageHandlerWrapper(MessageType.BINARY);
            if (wrapper == null) {
                LOG.debug("No BINARY MessageHandler declared");
                return;
            }
            if (wrapper.wantsPartialMessages()) {
                activeMessage = new BinaryPartialMessage(wrapper);
            } else if (wrapper.wantsStreams()) {
                final MessageInputStream stream = new MessageInputStream(session.getConnection());
                activeMessage = stream;
                dispatch(new Runnable() {
                    @SuppressWarnings("unchecked")
                    @Override
                    public void run() {
                        MessageHandler.Whole<InputStream> handler = (Whole<InputStream>) wrapper.getHandler();
                        handler.onMessage(stream);
                    }
                });
            } else {
                activeMessage = new BinaryWholeMessage(this, wrapper);
            }
        }

        activeMessage.appendMessage(buffer, fin);

        if (fin) {
            activeMessage.messageComplete();
            activeMessage = null;
        }
    }

    @Override
    public void onBinaryMessage(byte[] data) {
        /* Ignored, handled by BinaryWholeMessage */
    }

    @Override
    protected void onClose(CloseReason closereason) {
        endpoint.onClose(this.jsrsession, closereason);
    }

    @Override
    public void onConnect() {
        LOG.debug("onConnect({}, {})", jsrsession, config);
        try {
            endpoint.onOpen(jsrsession, config);
        } catch (Throwable t) {
            LOG.warn("Uncaught exception", t);
        }
    }

    @Override
    public void onError(Throwable cause) {
        endpoint.onError(jsrsession, cause);
    }

    @Override
    public void onFrame(Frame frame) {
        /* Ignored, not supported by JSR-356 */
    }

    @Override
    public void onInputStream(InputStream stream) {
        /* Ignored, handled by BinaryStreamMessage */
    }

    @Override
    public void onReader(Reader reader) {
        /* Ignored, handled by TextStreamMessage */
    }

    @Override
    public void onTextFrame(ByteBuffer buffer, boolean fin) throws IOException {
        if (activeMessage == null) {
            final MessageHandlerWrapper wrapper = jsrsession.getMessageHandlerWrapper(MessageType.TEXT);
            if (wrapper == null) {
                LOG.debug("No TEXT MessageHandler declared");
                return;
            }
            if (wrapper.wantsPartialMessages()) {
                activeMessage = new TextPartialMessage(wrapper);
            } else if (wrapper.wantsStreams()) {
                final MessageReader stream = new MessageReader(new MessageInputStream(session.getConnection()));
                activeMessage = stream;

                dispatch(new Runnable() {
                    @SuppressWarnings("unchecked")
                    @Override
                    public void run() {
                        MessageHandler.Whole<Reader> handler = (Whole<Reader>) wrapper.getHandler();
                        handler.onMessage(stream);
                    }
                });
            } else {
                activeMessage = new TextWholeMessage(this, wrapper);
            }
        }

        activeMessage.appendMessage(buffer, fin);

        if (fin) {
            activeMessage.messageComplete();
            activeMessage = null;
        }
    }

    @Override
    public void onTextMessage(String message) {
        /* Ignored, handled by TextWholeMessage */
    }

    @Override
    public void setPathParameters(Map<String, String> pathParameters) {
        this.pathParameters = pathParameters;
    }

    @Override
    public String toString() {
        return String.format("%s[%s]", JsrEndpointEventDriver.class.getSimpleName(), endpoint.getClass().getName());
    }
}
