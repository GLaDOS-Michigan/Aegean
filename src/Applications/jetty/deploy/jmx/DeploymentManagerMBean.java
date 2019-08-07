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

package Applications.jetty.deploy.jmx;

import Applications.jetty.deploy.App;
import Applications.jetty.deploy.AppProvider;
import Applications.jetty.deploy.DeploymentManager;
import Applications.jetty.deploy.graph.Node;
import Applications.jetty.jmx.ObjectMBean;
import Applications.jetty.server.handler.ContextHandler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DeploymentManagerMBean extends ObjectMBean {
    private final DeploymentManager _manager;

    public DeploymentManagerMBean(Object managedObject) {
        super(managedObject);
        _manager = (DeploymentManager) managedObject;
    }

    public Collection<String> getNodes() {
        List<String> nodes = new ArrayList<String>();
        for (Node node : _manager.getNodes())
            nodes.add(node.getName());
        return nodes;
    }

    public Collection<String> getApps() {
        List<String> apps = new ArrayList<String>();
        for (App app : _manager.getApps())
            apps.add(app.getOriginId());
        return apps;
    }

    public Collection<String> getApps(String nodeName) {
        List<String> apps = new ArrayList<String>();
        for (App app : _manager.getApps(nodeName))
            apps.add(app.getOriginId());
        return apps;
    }

    public Collection<ContextHandler> getContexts() throws Exception {
        List<ContextHandler> apps = new ArrayList<ContextHandler>();
        for (App app : _manager.getApps())
            apps.add(app.getContextHandler());
        return apps;
    }

    public Collection<AppProvider> getAppProviders() {
        return _manager.getAppProviders();
    }

    public void requestAppGoal(String appId, String nodeName) {
        _manager.requestAppGoal(appId, nodeName);
    }
}
