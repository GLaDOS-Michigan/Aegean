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
import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;

public class DebugBinding implements AppLifeCycle.Binding {
    private static final Logger LOG = Log.getLogger(DebugBinding.class);

    final String[] _targets;

    public DebugBinding(String target) {
        _targets = new String[]{target};
    }

    public DebugBinding(final String... targets) {
        _targets = targets;
    }

    public String[] getBindingTargets() {
        return _targets;
    }

    public void processBinding(Node node, App app) throws Exception {
        LOG.info("processBinding {} {}", node, app.getContextHandler());
    }
}
