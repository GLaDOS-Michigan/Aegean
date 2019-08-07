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

package Applications.jetty.websocket.server;

import Applications.jetty.websocket.api.UpgradeRequest;
import Applications.jetty.websocket.api.UpgradeResponse;
import Applications.jetty.websocket.common.LogicalConnection;

public class UpgradeContext {
    private LogicalConnection connection;
    private UpgradeRequest request;
    private UpgradeResponse response;

    public LogicalConnection getConnection() {
        return connection;
    }

    public UpgradeRequest getRequest() {
        return request;
    }

    public UpgradeResponse getResponse() {
        return response;
    }

    public void setConnection(LogicalConnection connection) {
        this.connection = connection;
    }

    public void setRequest(UpgradeRequest request) {
        this.request = request;
    }

    public void setResponse(UpgradeResponse response) {
        this.response = response;
    }
}
