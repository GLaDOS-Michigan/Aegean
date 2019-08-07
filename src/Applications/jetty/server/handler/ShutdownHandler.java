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

package Applications.jetty.server.handler;

import Applications.jetty.server.Connector;
import Applications.jetty.server.NetworkConnector;
import Applications.jetty.server.Request;
import Applications.jetty.server.Server;
import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.URL;

/* ------------------------------------------------------------ */

/**
 * A handler that shuts the server down on a valid request. Used to do "soft" restarts from Java. If _exitJvm ist set to true a hard System.exit() call is being
 * made.
 * <p>
 * This handler is a contribution from Johannes Brodwall: https://bugs.eclipse.org/bugs/show_bug.cgi?id=357687
 * <p>
 * Usage:
 * <p>
 * <pre>
 * Server server = new Server(8080);
 * HandlerList handlers = new HandlerList();
 * handlers.setHandlers(new Handler[]
 * { someOtherHandler, new ShutdownHandler(&quot;secret password&quot;) });
 * server.setHandler(handlers);
 * server.start();
 * </pre>
 * <p>
 * <pre>
 * public static void attemptShutdown(int port, String shutdownCookie) {
 * try {
 * URL url = new URL("http://localhost:" + port + "/shutdown?token=" + shutdownCookie);
 * HttpURLConnection connection = (HttpURLConnection)url.openConnection();
 * connection.setRequestMethod("POST");
 * connection.getResponseCode();
 * logger.info("Shutting down " + url + ": " + connection.getResponseMessage());
 * } catch (SocketException e) {
 * logger.debug("Not running");
 * // Okay - the server is not running
 * } catch (IOException e) {
 * throw new RuntimeException(e);
 * }
 * }
 * </pre>
 */
public class ShutdownHandler extends HandlerWrapper {
    private static final Logger LOG = Log.getLogger(ShutdownHandler.class);

    private final String _shutdownToken;
    private boolean _sendShutdownAtStart;
    private boolean _exitJvm = false;


    /**
     * Creates a listener that lets the server be shut down remotely (but only from localhost).
     *
     * @param server        the Jetty instance that should be shut down
     * @param shutdownToken a secret password to avoid unauthorized shutdown attempts
     */
    @Deprecated
    public ShutdownHandler(Server server, String shutdownToken) {
        this(shutdownToken);
    }

    public ShutdownHandler(String shutdownToken) {
        this(shutdownToken, false, false);
    }

    /**
     * @param shutdownToken
     * @param sendShutdownAtStart If true, a shutdown is sent as a HTTP post
     *                            during startup, which will shutdown any previously running instances of
     *                            this server with an identically configured ShutdownHandler
     */
    public ShutdownHandler(String shutdownToken, boolean exitJVM, boolean sendShutdownAtStart) {
        this._shutdownToken = shutdownToken;
        setExitJvm(exitJVM);
        setSendShutdownAtStart(sendShutdownAtStart);
    }


    public void sendShutdown() throws IOException {
        URL url = new URL(getServerUrl() + "/shutdown?token=" + _shutdownToken);
        try {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.getResponseCode();
            LOG.info("Shutting down " + url + ": " + connection.getResponseMessage());
        } catch (SocketException e) {
            LOG.debug("Not running");
            // Okay - the server is not running
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("resource")
    private String getServerUrl() {
        NetworkConnector connector = null;
        for (Connector c : getServer().getConnectors()) {
            if (c instanceof NetworkConnector) {
                connector = (NetworkConnector) c;
                break;
            }
        }

        if (connector == null)
            return "http://localhost";

        return "http://localhost:" + connector.getPort();
    }


    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (_sendShutdownAtStart)
            sendShutdown();
    }

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        if (!target.equals("/shutdown")) {
            super.handle(target, baseRequest, request, response);
            return;
        }

        if (!request.getMethod().equals("POST")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        if (!hasCorrectSecurityToken(request)) {
            LOG.warn("Unauthorized shutdown attempt from " + getRemoteAddr(request));
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        if (!requestFromLocalhost(request)) {
            LOG.warn("Unauthorized shutdown attempt from " + getRemoteAddr(request));
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        LOG.info("Shutting down by request from " + getRemoteAddr(request));

        final Server server = getServer();
        new Thread() {
            @Override
            public void run() {
                try {
                    shutdownServer(server);
                } catch (InterruptedException e) {
                    LOG.ignore(e);
                } catch (Exception e) {
                    throw new RuntimeException("Shutting down server", e);
                }
            }
        }.start();
    }

    private boolean requestFromLocalhost(HttpServletRequest request) {
        return "127.0.0.1".equals(getRemoteAddr(request));
    }

    protected String getRemoteAddr(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private boolean hasCorrectSecurityToken(HttpServletRequest request) {
        String tok = request.getParameter("token");
        LOG.debug("Token: {}", tok);
        return _shutdownToken.equals(tok);
    }

    private void shutdownServer(Server server) throws Exception {
        server.stop();

        if (_exitJvm) {
            System.exit(0);
        }
    }

    public void setExitJvm(boolean exitJvm) {
        this._exitJvm = exitJvm;
    }

    public boolean isSendShutdownAtStart() {
        return _sendShutdownAtStart;
    }

    public void setSendShutdownAtStart(boolean sendShutdownAtStart) {
        _sendShutdownAtStart = sendShutdownAtStart;
    }

    public String getShutdownToken() {
        return _shutdownToken;
    }

    public boolean isExitJvm() {
        return _exitJvm;
    }

}
