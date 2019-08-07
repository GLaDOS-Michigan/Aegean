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

package Applications.jetty.plus.jndi;


import javax.naming.NamingException;


/**
 * EnvEntry
 */
public class EnvEntry extends NamingEntry {
    private boolean overrideWebXml;

    public EnvEntry(Object scope, String jndiName, Object objToBind, boolean overrideWebXml)
            throws NamingException {
        super(scope, jndiName);
        save(objToBind);
        this.overrideWebXml = overrideWebXml;
    }


    public EnvEntry(String jndiName, Object objToBind, boolean overrideWebXml)
            throws NamingException {
        super(jndiName);
        save(objToBind);
        this.overrideWebXml = overrideWebXml;
    }

    public EnvEntry(String jndiName, Object objToBind)
            throws NamingException {
        this(jndiName, objToBind, false);
    }

    public boolean isOverrideWebXml() {
        return this.overrideWebXml;
    }
}
