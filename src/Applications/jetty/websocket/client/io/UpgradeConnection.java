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

package Applications.jetty.websocket.client.io;

import Applications.jetty.io.AbstractConnection;
import Applications.jetty.io.ByteBufferPool;
import Applications.jetty.io.EndPoint;
import Applications.jetty.util.BufferUtil;
import Applications.jetty.util.FutureCallback;
import Applications.jetty.util.QuotedStringTokenizer;
import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;
import Applications.jetty.websocket.api.UpgradeException;
import Applications.jetty.websocket.api.WebSocketPolicy;
import Applications.jetty.websocket.api.extensions.ExtensionConfig;
import Applications.jetty.websocket.client.ClientUpgradeRequest;
import Applications.jetty.websocket.client.ClientUpgradeResponse;
import Applications.jetty.websocket.common.AcceptHash;
import Applications.jetty.websocket.common.SessionFactory;
import Applications.jetty.websocket.common.WebSocketSession;
import Applications.jetty.websocket.common.events.EventDriver;
import Applications.jetty.websocket.common.extensions.ExtensionStack;
import Applications.jetty.websocket.common.io.http.HttpResponseHeaderParser;
import Applications.jetty.websocket.common.io.http.HttpResponseHeaderParser.ParseException;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * This is the initial connection handling that exists immediately after physical connection is established to destination server.
 * <p>
 * Eventually, upon successful Upgrade request/response, this connection swaps itself out for the WebSocektClientConnection handler.
 */
public class UpgradeConnection extends AbstractConnection {
    public class SendUpgradeRequest extends FutureCallback implements Runnable {
        @Override
        public void run() {
            URI uri = connectPromise.getRequest().getRequestURI();
            request.setRequestURI(uri);

            UpgradeListener handshakeListener = connectPromise.getUpgradeListener();
            if (handshakeListener != null) {
                handshakeListener.onHandshakeRequest(request);
            }

            String rawRequest = request.generate();

            ByteBuffer buf = BufferUtil.toBuffer(rawRequest, StandardCharsets.UTF_8);
            getEndPoint().write(this, buf);
        }

        @Override
        public void succeeded() {
            // Writing the request header is complete.
            super.succeeded();
            // start the interest in fill
            fillInterested();
        }

        @Override
        public void failed(Throwable cause) {
            super.failed(cause);
            // Fail the connect promise when a fundamental exception during connect occurs.
            connectPromise.failed(cause);
        }
    }

    /**
     * HTTP Response Code: 101 Switching Protocols
     */
    private static final int SWITCHING_PROTOCOLS = 101;

    private static final Logger LOG = Log.getLogger(UpgradeConnection.class);
    private final ByteBufferPool bufferPool;
    private final ConnectPromise connectPromise;
    private final HttpResponseHeaderParser parser;
    private ClientUpgradeRequest request;

    public UpgradeConnection(EndPoint endp, Executor executor, ConnectPromise connectPromise) {
        super(endp, executor);
        this.connectPromise = connectPromise;
        this.bufferPool = connectPromise.getClient().getBufferPool();
        this.request = connectPromise.getRequest();

        // Setup the parser
        this.parser = new HttpResponseHeaderParser(new ClientUpgradeResponse());
    }

    public void disconnect(boolean onlyOutput) {
        EndPoint endPoint = getEndPoint();
        // We need to gently close first, to allow
        // SSL close alerts to be sent by Jetty
        LOG.debug("Shutting down output {}", endPoint);
        endPoint.shutdownOutput();
        if (!onlyOutput) {
            LOG.debug("Closing {}", endPoint);
            endPoint.close();
        }
    }

    private void notifyConnect(ClientUpgradeResponse response) {
        connectPromise.setResponse(response);

        UpgradeListener handshakeListener = connectPromise.getUpgradeListener();
        if (handshakeListener != null) {
            handshakeListener.onHandshakeResponse(response);
        }
    }

    @Override
    public void onFillable() {
        ByteBuffer buffer = bufferPool.acquire(getInputBufferSize(), false);
        BufferUtil.clear(buffer);
        boolean readMore = false;
        try {
            readMore = read(buffer);
        } finally {
            bufferPool.release(buffer);
        }

        if (readMore) {
            fillInterested();
        }
    }

    @Override
    public void onOpen() {
        super.onOpen();
        // TODO: handle timeout?
        getExecutor().execute(new SendUpgradeRequest());
    }

    /**
     * Read / Parse the waiting read/fill buffer
     *
     * @param buffer the buffer to fill into from the endpoint
     * @return true if there is more to read, false if reading should stop
     */
    private boolean read(ByteBuffer buffer) {
        EndPoint endPoint = getEndPoint();
        try {
            while (true) {
                int filled = endPoint.fill(buffer);
                if (filled == 0) {
                    return true;
                } else if (filled < 0) {
                    LOG.debug("read - EOF Reached");
                    return false;
                } else {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Filled {} bytes - {}", filled, BufferUtil.toDetailString(buffer));
                    }
                    ClientUpgradeResponse resp = (ClientUpgradeResponse) parser.parse(buffer);
                    if (resp != null) {
                        // Got a response!
                        validateResponse(resp);
                        notifyConnect(resp);
                        upgradeConnection(resp);
                        if (buffer.hasRemaining()) {
                            LOG.debug("Has remaining client bytebuffer of {}", buffer.remaining());
                        }
                        return false; // do no more reading
                    }
                }
            }
        } catch (IOException | ParseException e) {
            UpgradeException ue = new UpgradeException(request.getRequestURI(), e);
            connectPromise.failed(ue);
            disconnect(false);
            return false;
        } catch (UpgradeException e) {
            connectPromise.failed(e);
            disconnect(false);
            return false;
        }
    }

    private void upgradeConnection(ClientUpgradeResponse response) {
        EndPoint endp = getEndPoint();
        Executor executor = getExecutor();

        EventDriver websocket = connectPromise.getDriver();
        WebSocketPolicy policy = websocket.getPolicy();

        WebSocketClientConnection connection = new WebSocketClientConnection(endp, executor, connectPromise, policy);

        SessionFactory sessionFactory = connectPromise.getClient().getSessionFactory();
        WebSocketSession session = sessionFactory.createSession(request.getRequestURI(), websocket, connection);
        session.setPolicy(policy);
        session.setUpgradeResponse(response);

        connection.setSession(session);

        // Initialize / Negotiate Extensions
        ExtensionStack extensionStack = new ExtensionStack(connectPromise.getClient().getExtensionFactory());
        extensionStack.negotiate(response.getExtensions());

        extensionStack.configure(connection.getParser());
        extensionStack.configure(connection.getGenerator());

        // Setup Incoming Routing
        connection.setNextIncomingFrames(extensionStack);
        extensionStack.setNextIncoming(session);

        // Setup Outgoing Routing
        session.setOutgoingHandler(extensionStack);
        extensionStack.setNextOutgoing(connection);

        // Now swap out the connection
        endp.setConnection(connection);
        connection.onOpen();
    }

    private void validateResponse(ClientUpgradeResponse response) {
        // Validate Response Status Code
        if (response.getStatusCode() != SWITCHING_PROTOCOLS) {
            throw new UpgradeException(request.getRequestURI(), response.getStatusCode(), "Didn't switch protocols");
        }

        // Validate Connection header
        String connection = response.getHeader("Connection");
        if (!"upgrade".equalsIgnoreCase(connection)) {
            throw new UpgradeException(request.getRequestURI(), response.getStatusCode(), "Connection is " + connection + " (expected upgrade)");
        }

        // Check the Accept hash
        String reqKey = request.getKey();
        String expectedHash = AcceptHash.hashKey(reqKey);
        String respHash = response.getHeader("Sec-WebSocket-Accept");

        response.setSuccess(true);
        if (expectedHash.equalsIgnoreCase(respHash) == false) {
            response.setSuccess(false);
            throw new UpgradeException(request.getRequestURI(), response.getStatusCode(), "Invalid Sec-WebSocket-Accept hash");
        }

        // Parse extensions
        List<ExtensionConfig> extensions = new ArrayList<>();
        List<String> extValues = response.getHeaders("Sec-WebSocket-Extensions");
        if (extValues != null) {
            for (String extVal : extValues) {
                QuotedStringTokenizer tok = new QuotedStringTokenizer(extVal, ",");
                while (tok.hasMoreTokens()) {
                    extensions.add(ExtensionConfig.parse(tok.nextToken()));
                }
            }
        }
        response.setExtensions(extensions);
    }
}
