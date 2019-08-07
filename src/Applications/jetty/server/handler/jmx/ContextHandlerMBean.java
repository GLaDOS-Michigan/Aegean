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

package Applications.jetty.server.handler.jmx;

import Applications.jetty.server.handler.ContextHandler;
import Applications.jetty.util.Attributes;
import Applications.jetty.util.annotation.ManagedAttribute;
import Applications.jetty.util.annotation.ManagedObject;
import Applications.jetty.util.annotation.ManagedOperation;
import Applications.jetty.util.annotation.Name;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@ManagedObject("ContextHandler mbean wrapper")
public class ContextHandlerMBean extends AbstractHandlerMBean {
    public ContextHandlerMBean(Object managedObject) {
        super(managedObject);
    }

    @ManagedAttribute("Map of context attributes")
    public Map<String, Object> getContextAttributes() {
        Map<String, Object> map = new HashMap<String, Object>();
        Attributes attrs = ((ContextHandler) _managed).getAttributes();
        Enumeration<String> en = attrs.getAttributeNames();
        while (en.hasMoreElements()) {
            String name = (String) en.nextElement();
            Object value = attrs.getAttribute(name);
            map.put(name, value);
        }
        return map;
    }

    @ManagedOperation(value = "Set context attribute", impact = "ACTION")
    public void setContextAttribute(@Name(value = "name", description = "attribute name") String name, @Name(value = "value", description = "attribute value") Object value) {
        Attributes attrs = ((ContextHandler) _managed).getAttributes();
        attrs.setAttribute(name, value);
    }

    @ManagedOperation(value = "Set context attribute", impact = "ACTION")
    public void setContextAttribute(@Name(value = "name", description = "attribute name") String name, @Name(value = "value", description = "attribute value") String value) {
        Attributes attrs = ((ContextHandler) _managed).getAttributes();
        attrs.setAttribute(name, value);
    }

    @ManagedOperation(value = "Remove context attribute", impact = "ACTION")
    public void removeContextAttribute(@Name(value = "name", description = "attribute name") String name) {
        Attributes attrs = ((ContextHandler) _managed).getAttributes();
        attrs.removeAttribute(name);
    }
}
