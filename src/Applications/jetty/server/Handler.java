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

package Applications.jetty.server;

import Applications.jetty.server.handler.HandlerCollection;
import Applications.jetty.server.handler.HandlerWrapper;
import Applications.jetty.util.annotation.ManagedAttribute;
import Applications.jetty.util.annotation.ManagedObject;
import Applications.jetty.util.annotation.ManagedOperation;
import Applications.jetty.util.component.Destroyable;
import Applications.jetty.util.component.LifeCycle;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/* ------------------------------------------------------------ */

/**
 * A Jetty Server Handler.
 * <p>
 * A Handler instance is required by a {@link Server} to handle incoming
 * HTTP requests.  A Handler may: <ul>
 * <li>Completely generate the HTTP Response</li>
 * <li>Examine/modify the request and call another Handler (see {@link HandlerWrapper}).
 * <li>Pass the request to one or more other Handlers (see {@link HandlerCollection}).
 * </ul>
 * <p>
 * Handlers are passed the servlet API request and response object, but are
 * not Servlets.  The servlet container is implemented by handlers for
 * context, security, session and servlet that modify the request object
 * before passing it to the next stage of handling.
 */
@ManagedObject("Jetty Handler")
public interface Handler extends LifeCycle, Destroyable {
    /* ------------------------------------------------------------ */

    /**
     * Handle a request.
     *
     * @param target      The target of the request - either a URI or a name.
     * @param baseRequest The original unwrapped request object.
     * @param request     The request either as the {@link Request}
     *                    object or a wrapper of that request. The {@link HttpChannel#getCurrentHttpChannel()}
     *                    method can be used access the Request object if required.
     * @param response    The response as the {@link Response}
     *                    object or a wrapper of that request. The {@link HttpChannel#getCurrentHttpChannel()}
     *                    method can be used access the Response object if required.
     * @throws IOException
     * @throws ServletException
     */
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException;

    public void setServer(Server server);

    @ManagedAttribute(value = "the jetty server for this handler", readonly = true)
    public Server getServer();

    @ManagedOperation(value = "destroy associated resources", impact = "ACTION")
    public void destroy();

}

