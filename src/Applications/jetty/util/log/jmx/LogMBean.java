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

package Applications.jetty.util.log.jmx;

import Applications.jetty.jmx.ObjectMBean;
import Applications.jetty.util.annotation.ManagedAttribute;
import Applications.jetty.util.annotation.ManagedObject;
import Applications.jetty.util.annotation.ManagedOperation;
import Applications.jetty.util.annotation.Name;
import Applications.jetty.util.log.Log;

import java.util.ArrayList;
import java.util.List;

/* ------------------------------------------------------------ */

/**
 */
@ManagedObject("Jetty Logging")
public class LogMBean extends ObjectMBean {
    public LogMBean(Object managedObject) {
        super(managedObject);
    }

    @ManagedAttribute(value = "list of instantiated loggers")
    public List<String> getLoggers() {
        List<String> keySet = new ArrayList<String>(Log.getLoggers().keySet());
        return keySet;
    }

    @ManagedOperation(value = "true if debug enabled for the given logger")
    public boolean isDebugEnabled(@Name("logger") String logger) {
        return Log.getLogger(logger).isDebugEnabled();
    }

    @ManagedOperation(value = "Set debug enabled for given logger")
    public void setDebugEnabled(@Name("logger") String logger, @Name("enabled") Boolean enabled) {
        Log.getLogger(logger).setDebugEnabled(enabled);
    }
}
