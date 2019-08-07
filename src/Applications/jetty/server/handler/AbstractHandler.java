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
import Applications.jetty.server.Server;
import Applications.jetty.util.annotation.ManagedObject;
import Applications.jetty.util.component.ContainerLifeCycle;
import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;

import java.io.IOException;


/* ------------------------------------------------------------ */

/**
 * AbstractHandler.
 */
@ManagedObject("Jetty Handler")
public abstract class AbstractHandler extends ContainerLifeCycle implements Handler {
    private static final Logger LOG = Log.getLogger(AbstractHandler.class);

    private Server _server;
    
    /* ------------------------------------------------------------ */

    /**
     *
     */
    public AbstractHandler() {
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see org.eclipse.thread.LifeCycle#start()
     */
    @Override
    protected void doStart() throws Exception {
        LOG.debug("starting {}", this);
        if (_server == null)
            LOG.warn("No Server set for {}", this);
        super.doStart();
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see org.eclipse.thread.LifeCycle#stop()
     */
    @Override
    protected void doStop() throws Exception {
        LOG.debug("stopping {}", this);
        super.doStop();
    }

    /* ------------------------------------------------------------ */
    @Override
    public void setServer(Server server) {
        if (_server == server)
            return;
        if (isStarted())
            throw new IllegalStateException(STARTED);
        _server = server;
    }

    /* ------------------------------------------------------------ */
    @Override
    public Server getServer() {
        return _server;
    }

    /* ------------------------------------------------------------ */
    @Override
    public void destroy() {
        if (!isStopped())
            throw new IllegalStateException("!STOPPED");
        super.destroy();
    }

    /* ------------------------------------------------------------ */
    @Override
    public void dumpThis(Appendable out) throws IOException {
        out.append(toString()).append(" - ").append(getState()).append('\n');
    }

}
