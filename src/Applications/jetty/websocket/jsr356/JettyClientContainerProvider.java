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

package Applications.jetty.websocket.jsr356;

import javax.websocket.ContainerProvider;
import javax.websocket.WebSocketContainer;

/**
 * Client {@link ContainerProvider} implementation
 */
public class JettyClientContainerProvider extends ContainerProvider {
    @Override
    protected WebSocketContainer getContainer() {
        ClientContainer container = new ClientContainer();
        try {
            container.start();
            return container;
        } catch (Exception e) {
            throw new RuntimeException("Unable to start Client Container", e);
        }
    }
}
