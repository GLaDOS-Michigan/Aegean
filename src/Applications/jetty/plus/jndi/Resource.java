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
 * Resource
 */
public class Resource extends NamingEntry {

    public Resource(Object scope, String jndiName, Object objToBind)
            throws NamingException {
        super(scope, jndiName);
        save(objToBind);
    }

    /**
     * @param jndiName
     * @param objToBind
     */
    public Resource(String jndiName, Object objToBind)
            throws NamingException {
        super(jndiName);
        save(objToBind);
    }

}
