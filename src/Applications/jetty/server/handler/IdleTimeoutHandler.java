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

import Applications.jetty.io.EndPoint;
import Applications.jetty.server.HttpConnection;
import Applications.jetty.server.Request;

import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Handler to adjust the idle timeout of requests while dispatched.
 * Can be applied in jetty.xml with
 * <pre>
 *   &lt;Get id='handler' name='Handler'/>
 *   &lt;Set name='Handler'>
 *     &lt;New id='idleTimeoutHandler' class='org.eclipse.jetty.server.handler.IdleTimeoutHandler'>
 *       &lt;Set name='Handler'>&lt;Ref id='handler'/>&lt;/Set>
 *       &lt;Set name='IdleTimeoutMs'>5000&lt;/Set>
 *     &lt;/New>
 *   &lt;/Set>
 * </pre>
 */
public class IdleTimeoutHandler extends HandlerWrapper {
    private long _idleTimeoutMs = 1000;
    private boolean _applyToAsync = false;

    public boolean isApplyToAsync() {
        return _applyToAsync;
    }

    /**
     * Should the adjusted idle time be maintained for asynchronous requests
     *
     * @param applyToAsync true if alternate idle timeout is applied to asynchronous requests
     */
    public void setApplyToAsync(boolean applyToAsync) {
        _applyToAsync = applyToAsync;
    }

    public long getIdleTimeoutMs() {
        return _idleTimeoutMs;
    }

    /**
     * @param idleTimeoutMs The idle timeout in MS to apply while dispatched or async
     */
    public void setIdleTimeoutMs(long idleTimeoutMs) {
        this._idleTimeoutMs = idleTimeoutMs;
    }


    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        HttpConnection connection = HttpConnection.getCurrentConnection();
        final EndPoint endp = connection == null ? null : connection.getEndPoint();

        final long idle_timeout;
        if (endp == null)
            idle_timeout = -1;
        else {
            idle_timeout = endp.getIdleTimeout();
            endp.setIdleTimeout(_idleTimeoutMs);
        }

        try {
            super.handle(target, baseRequest, request, response);
        } finally {
            if (endp != null) {
                if (_applyToAsync && request.isAsyncStarted()) {
                    request.getAsyncContext().addListener(new AsyncListener() {
                        @Override
                        public void onTimeout(AsyncEvent event) throws IOException {
                        }

                        @Override
                        public void onStartAsync(AsyncEvent event) throws IOException {
                        }

                        @Override
                        public void onError(AsyncEvent event) throws IOException {
                            endp.setIdleTimeout(idle_timeout);
                        }

                        @Override
                        public void onComplete(AsyncEvent event) throws IOException {
                            endp.setIdleTimeout(idle_timeout);
                        }
                    });
                } else
                    endp.setIdleTimeout(idle_timeout);
            }
        }
    }
}
