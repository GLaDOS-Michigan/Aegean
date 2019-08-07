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

package Applications.jetty.servlet.jmx;

import Applications.jetty.jmx.ObjectMBean;
import Applications.jetty.servlet.FilterMapping;

public class FilterMappingMBean extends ObjectMBean {

    public FilterMappingMBean(Object managedObject) {
        super(managedObject);
    }

    public String getObjectNameBasis() {
        if (_managed != null && _managed instanceof FilterMapping) {
            FilterMapping mapping = (FilterMapping) _managed;
            String name = mapping.getFilterName();
            if (name != null)
                return name;
        }

        return super.getObjectNameBasis();
    }
}
