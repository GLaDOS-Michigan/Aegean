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

package Applications.jetty.deploy.bindings;

import Applications.jetty.deploy.App;
import Applications.jetty.deploy.AppLifeCycle;
import Applications.jetty.deploy.graph.Node;
import Applications.jetty.server.handler.ContextHandler;

public class StandardStopper implements AppLifeCycle.Binding {
    @Override
    public String[] getBindingTargets() {
        return new String[]
                {"stopping"};
    }

    @Override
    public void processBinding(Node node, App app) throws Exception {
        ContextHandler handler = app.getContextHandler();

        // Before stopping, take back management from the context
        app.getDeploymentManager().getContexts().unmanage(handler);

        // Stop it
        handler.stop();
    }
}
