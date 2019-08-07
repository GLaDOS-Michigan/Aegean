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

package Applications.jetty.spdy.client;

import Applications.jetty.spdy.FlowControlStrategy;
import Applications.jetty.spdy.SPDYv3FlowControlStrategy;
import Applications.jetty.spdy.api.SPDY;

public class FlowControlStrategyFactory {
    private FlowControlStrategyFactory() {
    }

    public static FlowControlStrategy newFlowControlStrategy(short version) {
        switch (version) {
            case SPDY.V2:
                return new FlowControlStrategy.None();
            case SPDY.V3:
                return new SPDYv3FlowControlStrategy();
            default:
                throw new IllegalStateException();
        }
    }
}
