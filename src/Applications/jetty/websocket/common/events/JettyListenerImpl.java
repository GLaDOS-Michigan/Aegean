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

package Applications.jetty.websocket.common.events;

import Applications.jetty.websocket.api.WebSocketListener;
import Applications.jetty.websocket.api.WebSocketPolicy;

public class JettyListenerImpl implements EventDriverImpl {
    @Override
    public EventDriver create(Object websocket, WebSocketPolicy policy) {
        WebSocketListener listener = (WebSocketListener) websocket;
        return new JettyListenerEventDriver(policy, listener);
    }

    @Override
    public String describeRule() {
        return "class implements " + WebSocketListener.class.getName();
    }

    @Override
    public boolean supports(Object websocket) {
        return (websocket instanceof WebSocketListener);
    }
}
