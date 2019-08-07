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
import Applications.jetty.server.Server;
import Applications.jetty.util.annotation.ManagedAttribute;
import Applications.jetty.util.annotation.ManagedObject;
import Applications.jetty.util.component.LifeCycle;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/* ------------------------------------------------------------ */

/**
 * A <code>HandlerWrapper</code> acts as a {@link Handler} but delegates the {@link Handler#handle handle} method and
 * {@link LifeCycle life cycle} events to a delegate. This is primarily used to implement the <i>Decorator</i> pattern.
 */
@ManagedObject("Handler wrapping another Handler")
public class HandlerWrapper extends AbstractHandlerContainer {
    protected Handler _handler;

    /* ------------------------------------------------------------ */

    /**
     *
     */
    public HandlerWrapper() {
    }

    /* ------------------------------------------------------------ */

    /**
     * @return Returns the handlers.
     */
    @ManagedAttribute(value = "Wrapped Handler", readonly = true)
    public Handler getHandler() {
        return _handler;
    }

    /* ------------------------------------------------------------ */

    /**
     * @return Returns the handlers.
     */
    @Override
    public Handler[] getHandlers() {
        if (_handler == null)
            return new Handler[0];
        return new Handler[]{_handler};
    }

    /* ------------------------------------------------------------ */

    /**
     * @param handler Set the {@link Handler} which should be wrapped.
     */
    public void setHandler(Handler handler) {
        if (isStarted())
            throw new IllegalStateException(STARTED);

        if (handler != null)
            handler.setServer(getServer());

        updateBean(_handler, handler);
        _handler = handler;
    }

    /* ------------------------------------------------------------ */
    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        if (_handler != null && isStarted()) {
            _handler.handle(target, baseRequest, request, response);
        }
    }


    /* ------------------------------------------------------------ */
    @Override
    public void setServer(Server server) {
        if (server == getServer())
            return;

        if (isStarted())
            throw new IllegalStateException(STARTED);

        super.setServer(server);
        Handler h = getHandler();
        if (h != null)
            h.setServer(server);
    }


    /* ------------------------------------------------------------ */
    @Override
    protected void expandChildren(List<Handler> list, Class<?> byClass) {
        expandHandler(_handler, list, byClass);
    }

    /* ------------------------------------------------------------ */
    @Override
    public void destroy() {
        if (!isStopped())
            throw new IllegalStateException("!STOPPED");
        Handler child = getHandler();
        if (child != null) {
            setHandler(null);
            child.destroy();
        }
        super.destroy();
    }

}
