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

package Applications.jetty.server;

import Applications.jetty.util.component.LifeCycle;

/**
 * A <code>RequestLog</code> can be attached to a {@link org.eclipse.jetty.server.handler.RequestLogHandler} to enable
 * logging of requests/responses.
 */
public interface RequestLog extends LifeCycle {
    public void log(Request request, Response response);
}
