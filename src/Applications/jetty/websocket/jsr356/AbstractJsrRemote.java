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

package Applications.jetty.websocket.jsr356;

import Applications.jetty.util.BufferUtil;
import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;
import Applications.jetty.websocket.common.WebSocketRemoteEndpoint;
import Applications.jetty.websocket.common.io.FutureWriteCallback;
import Applications.jetty.websocket.common.message.MessageOutputStream;
import Applications.jetty.websocket.common.message.MessageWriter;
import Applications.jetty.websocket.jsr356.encoders.EncodeFailedFuture;

import javax.websocket.EncodeException;
import javax.websocket.Encoder;
import javax.websocket.RemoteEndpoint;
import javax.websocket.SendHandler;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Future;

public abstract class AbstractJsrRemote implements RemoteEndpoint {
    private static final Logger LOG = Log.getLogger(AbstractJsrRemote.class);

    protected final JsrSession session;
    protected final WebSocketRemoteEndpoint jettyRemote;
    protected final EncoderFactory encoders;

    protected AbstractJsrRemote(JsrSession session) {
        this.session = session;
        if (!(session.getRemote() instanceof WebSocketRemoteEndpoint)) {
            StringBuilder err = new StringBuilder();
            err.append("Unexpected implementation [");
            err.append(session.getRemote().getClass().getName());
            err.append("].  Expected an instanceof [");
            err.append(WebSocketRemoteEndpoint.class.getName());
            err.append("]");
            throw new IllegalStateException(err.toString());
        }
        this.jettyRemote = (WebSocketRemoteEndpoint) session.getRemote();
        this.encoders = session.getEncoderFactory();
    }

    protected void assertMessageNotNull(Object data) {
        if (data == null) {
            throw new IllegalArgumentException("message cannot be null");
        }
    }

    protected void assertSendHandlerNotNull(SendHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("SendHandler cannot be null");
        }
    }

    @Override
    public void flushBatch() throws IOException {
        // TODO Auto-generated method stub
    }

    @Override
    public boolean getBatchingAllowed() {
        // TODO Auto-generated method stub
        return false;
    }

    @SuppressWarnings(
            {"rawtypes", "unchecked"})
    public Future<Void> sendObjectViaFuture(Object data) {
        assertMessageNotNull(data);
        if (LOG.isDebugEnabled()) {
            LOG.debug("sendObject({})", data);
        }

        Encoder encoder = encoders.getEncoderFor(data.getClass());
        if (encoder == null) {
            throw new IllegalArgumentException("No encoder for type: " + data.getClass());
        }

        if (encoder instanceof Encoder.Text) {
            Encoder.Text etxt = (Encoder.Text) encoder;
            try {
                String msg = etxt.encode(data);
                return jettyRemote.sendStringByFuture(msg);
            } catch (EncodeException e) {
                return new EncodeFailedFuture(data, etxt, Encoder.Text.class, e);
            }
        } else if (encoder instanceof Encoder.TextStream) {
            Encoder.TextStream etxt = (Encoder.TextStream) encoder;
            FutureWriteCallback callback = new FutureWriteCallback();
            try (MessageWriter writer = new MessageWriter(session)) {
                writer.setCallback(callback);
                etxt.encode(data, writer);
                return callback;
            } catch (EncodeException | IOException e) {
                return new EncodeFailedFuture(data, etxt, Encoder.Text.class, e);
            }
        } else if (encoder instanceof Encoder.Binary) {
            Encoder.Binary ebin = (Encoder.Binary) encoder;
            try {
                ByteBuffer buf = ebin.encode(data);
                return jettyRemote.sendBytesByFuture(buf);
            } catch (EncodeException e) {
                return new EncodeFailedFuture(data, ebin, Encoder.Binary.class, e);
            }
        } else if (encoder instanceof Encoder.BinaryStream) {
            Encoder.BinaryStream ebin = (Encoder.BinaryStream) encoder;
            FutureWriteCallback callback = new FutureWriteCallback();
            try (MessageOutputStream out = new MessageOutputStream(session)) {
                out.setCallback(callback);
                ebin.encode(data, out);
                return callback;
            } catch (EncodeException | IOException e) {
                return new EncodeFailedFuture(data, ebin, Encoder.Binary.class, e);
            }
        }

        throw new IllegalArgumentException("Unknown encoder type: " + encoder);
    }

    @Override
    public void sendPing(ByteBuffer data) throws IOException, IllegalArgumentException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("sendPing({})", BufferUtil.toDetailString(data));
        }
        jettyRemote.sendPing(data);
    }

    @Override
    public void sendPong(ByteBuffer data) throws IOException, IllegalArgumentException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("sendPong({})", BufferUtil.toDetailString(data));
        }
        jettyRemote.sendPong(data);
    }

    @Override
    public void setBatchingAllowed(boolean allowed) throws IOException {
        // TODO Auto-generated method stub
    }
}
