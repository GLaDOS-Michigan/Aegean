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

import Applications.jetty.server.Handler;
import Applications.jetty.server.Request;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/* ------------------------------------------------------------ */

/**
 * HandlerList.
 * This extension of {@link HandlerCollection} will call
 * each contained handler in turn until either an exception is thrown, the response
 * is committed or a positive response status is set.
 */
public class HandlerList extends HandlerCollection {
    /* ------------------------------------------------------------ */

    /**
     * @see Handler#handle(String, Request, HttpServletRequest, HttpServletResponse)
     */
    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        Handler[] handlers = getHandlers();

        if (handlers != null && isStarted()) {
            for (int i = 0; i < handlers.length; i++) {
                handlers[i].handle(target, baseRequest, request, response);
                if (baseRequest.isHandled())
                    return;
            }
        }
    }
}
