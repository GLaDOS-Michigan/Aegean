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

package Applications.jetty.websocket.api;

/**
 * Default implementation of the {@link WebSocketListener}.
 * <p>
 * Convenient abstract class to base standard WebSocket implementations off of.
 */
public class WebSocketAdapter implements WebSocketListener {
    private volatile Session session;

    public RemoteEndpoint getRemote() {
        Session sess = this.session;
        return sess == null ? null : session.getRemote();
    }

    public Session getSession() {
        return session;
    }

    public boolean isConnected() {
        Session sess = this.session;
        return (sess != null) && (sess.isOpen());
    }

    public boolean isNotConnected() {
        Session sess = this.session;
        return (sess == null) || (!sess.isOpen());
    }

    @Override
    public void onWebSocketBinary(byte[] payload, int offset, int len) {
        /* do nothing */
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason) {
        this.session = null;
    }

    @Override
    public void onWebSocketConnect(Session sess) {
        this.session = sess;
    }

    @Override
    public void onWebSocketError(Throwable cause) {
        /* do nothing */
    }

    @Override
    public void onWebSocketText(String message) {
        /* do nothing */
    }
}
