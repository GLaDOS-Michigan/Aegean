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
import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;
import Applications.jetty.util.resource.Resource;
import Applications.jetty.webapp.WebAppContext;
import Applications.jetty.xml.XmlConfiguration;

import java.io.File;

/**
 * Provides a way of globally setting various aspects of webapp contexts.
 * <p>
 * Adding this binding will allow the user to arbitrarily apply a file of
 * jetty-web.xml like settings to a webapp context.
 * <p>
 * Example usage would be:
 * - adding a server or system class setting to all webapp contexts
 * - adding an override descriptor
 * <p>
 * Note: Currently properties from startup will not be available for
 * reference.
 */
public class GlobalWebappConfigBinding implements AppLifeCycle.Binding {
    private static final Logger LOG = Log.getLogger(GlobalWebappConfigBinding.class);


    private String _jettyXml;

    public String getJettyXml() {
        return _jettyXml;
    }

    public void setJettyXml(String jettyXml) {
        this._jettyXml = jettyXml;
    }

    public String[] getBindingTargets() {
        return new String[]{"deploying"};
    }

    public void processBinding(Node node, App app) throws Exception {
        ContextHandler handler = app.getContextHandler();
        if (handler == null) {
            throw new NullPointerException("No Handler created for App: " + app);
        }

        if (handler instanceof WebAppContext) {
            WebAppContext context = (WebAppContext) handler;

            if (LOG.isDebugEnabled()) {
                LOG.debug("Binding: Configuring webapp context with global settings from: " + _jettyXml);
            }

            if (_jettyXml == null) {
                LOG.warn("Binding: global context binding is enabled but no jetty-web.xml file has been registered");
            }

            Resource globalContextSettings = Resource.newResource(_jettyXml);

            if (globalContextSettings.exists()) {
                XmlConfiguration jettyXmlConfig = new XmlConfiguration(globalContextSettings.getInputStream());

                Resource resource = Resource.newResource(app.getOriginId());
                File file = resource.getFile();
                jettyXmlConfig.getIdMap().put("Server", app.getDeploymentManager().getServer());
                jettyXmlConfig.getProperties().put("jetty.webapp", file.getCanonicalPath());
                jettyXmlConfig.getProperties().put("jetty.webapps", file.getParentFile().getCanonicalPath());

                jettyXmlConfig.configure(context);
            } else {
                LOG.info("Binding: Unable to locate global webapp context settings: " + _jettyXml);
            }
        }
    }

}
