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

package Applications.jetty.util.preventers;

/**
 * LoginConfigurationLeakPreventer
 * <p>
 * The javax.security.auth.login.Configuration class keeps a static reference to the
 * thread context classloader. We prevent a webapp context classloader being used for
 * that by invoking the classloading here.
 * <p>
 * Inspired by Tomcat JreMemoryLeakPrevention
 */
public class LoginConfigurationLeakPreventer extends AbstractLeakPreventer {
    /* ------------------------------------------------------------ */

    /**
     * @see Applications.jetty.util.preventers.AbstractLeakPreventer#prevent(java.lang.ClassLoader)
     */
    @Override
    public void prevent(ClassLoader loader) {
        try {
            Class.forName("javax.security.auth.login.Configuration", true, loader);
        } catch (ClassNotFoundException e) {
            LOG.warn(e);
        }
    }

}
