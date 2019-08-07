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

package Applications.jetty.server.jmx;

import Applications.jetty.jmx.ObjectMBean;
import Applications.jetty.server.Handler;
import Applications.jetty.server.Server;
import Applications.jetty.server.handler.ContextHandler;
import Applications.jetty.util.annotation.ManagedAttribute;
import Applications.jetty.util.annotation.ManagedObject;

/**
 *
 */
@ManagedObject("MBean Wrapper for Server")
public class ServerMBean extends ObjectMBean {
    private final long startupTime;
    private final Server server;

    public ServerMBean(Object managedObject) {
        super(managedObject);
        startupTime = System.currentTimeMillis();
        server = (Server) managedObject;
    }

    @ManagedAttribute("contexts on this server")
    public Handler[] getContexts() {
        return server.getChildHandlersByClass(ContextHandler.class);
    }

    @ManagedAttribute("the startup time since January 1st, 1970 (in ms)")
    public long getStartupTime() {
        return startupTime;
    }
}
