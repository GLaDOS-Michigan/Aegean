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

import Applications.jetty.util.annotation.ManagedAttribute;
import Applications.jetty.util.annotation.ManagedObject;
import Applications.jetty.util.annotation.ManagedOperation;
import Applications.jetty.util.annotation.Name;
import Applications.jetty.util.resource.Resource;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * FileConfigurationManager
 * <p>
 * Supplies properties defined in a file.
 */
@ManagedObject("Configure deployed webapps via properties")
public class PropertiesConfigurationManager implements ConfigurationManager {
    private String _properties;
    private final Map<String, String> _map = new HashMap<String, String>();

    public PropertiesConfigurationManager(String properties) {
    }

    public PropertiesConfigurationManager() {
    }

    @ManagedAttribute("A file or URL of properties")
    public void setFile(String resource) throws MalformedURLException, IOException {
        _properties = resource;
        _map.clear();
        loadProperties(_properties);
    }

    public String getFile() {
        return _properties;
    }

    @ManagedOperation("Set a property")
    public void put(@Name("name") String name, @Name("value") String value) {
        _map.put(name, value);
    }

    /**
     * @see org.eclipse.jetty.deploy.ConfigurationManager#getProperties()
     */
    @Override
    public Map<String, String> getProperties() {
        return new HashMap<>(_map);
    }

    private void loadProperties(String resource) throws FileNotFoundException, IOException {
        Resource file = Resource.newResource(resource);
        if (file != null && file.exists()) {
            Properties properties = new Properties();
            properties.load(file.getInputStream());
            for (Map.Entry<Object, Object> entry : properties.entrySet())
                _map.put(entry.getKey().toString(), String.valueOf(entry.getValue()));
        }
    }
}
