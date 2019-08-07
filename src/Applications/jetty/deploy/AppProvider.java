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

package Applications.jetty.deploy;

import Applications.jetty.server.handler.ContextHandler;
import Applications.jetty.util.component.LifeCycle;

import java.io.IOException;

/**
 * Object responsible for providing {@link App}s to the {@link DeploymentManager}
 */
public interface AppProvider extends LifeCycle {
    /**
     * Set the Deployment Manager
     *
     * @param deploymentManager
     * @throws IllegalStateException if the provider {@link #isRunning()}.
     */
    void setDeploymentManager(DeploymentManager deploymentManager);
    
    /* ------------------------------------------------------------ */

    /**
     * Create a ContextHandler for an App
     *
     * @param app The App
     * @return A ContextHandler
     * @throws IOException
     * @throws Exception
     */
    ContextHandler createContextHandler(App app) throws Exception;
}
