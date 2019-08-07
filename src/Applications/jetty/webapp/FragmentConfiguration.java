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


package Applications.jetty.webapp;

import Applications.jetty.util.resource.Resource;

import java.util.Map;

/**
 * FragmentConfiguration
 * <p>
 * <p>
 * <p>
 * Process web-fragments in jars
 */
public class FragmentConfiguration extends AbstractConfiguration {
    public final static String FRAGMENT_RESOURCES = "org.eclipse.jetty.webFragments";

    @Override
    public void preConfigure(WebAppContext context) throws Exception {
        if (!context.isConfigurationDiscovered())
            return;

        //find all web-fragment.xmls
        findWebFragments(context, context.getMetaData());

    }

    @Override
    public void configure(WebAppContext context) throws Exception {
        if (!context.isConfigurationDiscovered())
            return;

        //order the fragments
        context.getMetaData().orderFragments();
    }

    @Override
    public void postConfigure(WebAppContext context) throws Exception {
        context.setAttribute(FRAGMENT_RESOURCES, null);
    }

    /* ------------------------------------------------------------------------------- */

    /**
     * Look for any web-fragment.xml fragments in META-INF of jars in WEB-INF/lib
     *
     * @throws Exception
     */
    public void findWebFragments(final WebAppContext context, final MetaData metaData) throws Exception {
        @SuppressWarnings("unchecked")
        Map<Resource, Resource> frags = (Map<Resource, Resource>) context.getAttribute(FRAGMENT_RESOURCES);
        if (frags != null) {
            for (Resource key : frags.keySet()) {
                if (key.isDirectory()) //tolerate the case where the library is a directory, not a jar. useful for OSGi for example
                {
                    metaData.addFragment(key, frags.get(key));
                } else //the standard case: a jar most likely inside WEB-INF/lib
                {
                    metaData.addFragment(key, frags.get(key));
                }
            }
        }
    }
}
