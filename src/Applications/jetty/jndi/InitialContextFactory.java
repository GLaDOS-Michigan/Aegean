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

package Applications.jetty.jndi;


import Applications.jetty.jndi.local.localContextRoot;
import Applications.jetty.util.log.Logger;

import javax.naming.*;
import java.util.Hashtable;
import java.util.Properties;


/*------------------------------------------------*/

/**
 * InitialContextFactory.java
 * <p>
 * Factory for the default InitialContext.
 * Created: Tue Jul  1 19:08:08 2003
 *
 * @version 1.0
 */
public class InitialContextFactory implements javax.naming.spi.InitialContextFactory {
    private static Logger __log = NamingUtil.__log;

    public static class DefaultParser implements NameParser {
        static Properties syntax = new Properties();

        static {
            syntax.put("jndi.syntax.direction", "left_to_right");
            syntax.put("jndi.syntax.separator", "/");
            syntax.put("jndi.syntax.ignorecase", "false");
        }

        public Name parse(String name)
                throws NamingException {
            return new CompoundName(name, syntax);
        }
    }

    ;



    /*------------------------------------------------*/

    /**
     * Get Context that has access to default Namespace.
     * This method won't be called if a name URL beginning
     * with java: is passed to an InitialContext.
     *
     * @param env a <code>Hashtable</code> value
     * @return a <code>Context</code> value
     * @see Applications.jetty.jndi.java.javaURLContextFactory
     */
    public Context getInitialContext(Hashtable env) {
        __log.debug("InitialContextFactory.getInitialContext()");

        Context ctx = new localContextRoot(env);
        if (__log.isDebugEnabled()) __log.debug("Created initial context delegate for local namespace:" + ctx);

        return ctx;
    }
}
