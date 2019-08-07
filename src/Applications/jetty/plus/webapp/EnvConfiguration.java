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

package Applications.jetty.plus.webapp;

import Applications.jetty.jndi.ContextFactory;
import Applications.jetty.jndi.NamingContext;
import Applications.jetty.jndi.NamingUtil;
import Applications.jetty.jndi.local.localContextRoot;
import Applications.jetty.plus.jndi.EnvEntry;
import Applications.jetty.plus.jndi.NamingEntryUtil;
import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;
import Applications.jetty.webapp.AbstractConfiguration;
import Applications.jetty.webapp.Configuration;
import Applications.jetty.webapp.WebAppContext;
import Applications.jetty.xml.XmlConfiguration;

import javax.naming.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;


/**
 * EnvConfiguration
 */
public class EnvConfiguration extends AbstractConfiguration {
    private static final Logger LOG = Log.getLogger(EnvConfiguration.class);

    private static final String JETTY_ENV_BINDINGS = "org.eclipse.jetty.jndi.EnvConfiguration";
    private URL jettyEnvXmlUrl;

    public void setJettyEnvXml(URL url) {
        this.jettyEnvXmlUrl = url;
    }

    /**
     * @throws Exception
     * @see Configuration#configure(WebAppContext)
     */
    @Override
    public void preConfigure(WebAppContext context) throws Exception {
        //create a java:comp/env
        createEnvContext(context);
    }

    /**
     * @throws Exception
     */
    @Override
    public void configure(WebAppContext context) throws Exception {
        if (LOG.isDebugEnabled())
            LOG.debug("Created java:comp/env for webapp " + context.getContextPath());

        //check to see if an explicit file has been set, if not,
        //look in WEB-INF/jetty-env.xml
        if (jettyEnvXmlUrl == null) {
            //look for a file called WEB-INF/jetty-env.xml
            //and process it if it exists
            Applications.jetty.util.resource.Resource web_inf = context.getWebInf();
            if (web_inf != null && web_inf.isDirectory()) {
                Applications.jetty.util.resource.Resource jettyEnv = web_inf.addPath("jetty-env.xml");
                if (jettyEnv.exists()) {
                    jettyEnvXmlUrl = jettyEnv.getURL();
                }
            }
        }

        if (jettyEnvXmlUrl != null) {
            synchronized (localContextRoot.getRoot()) {
                // create list and listener to remember the bindings we make.
                final List<Bound> bindings = new ArrayList<Bound>();
                NamingContext.Listener listener = new NamingContext.Listener() {
                    public void unbind(NamingContext ctx, Binding binding) {
                    }

                    public Binding bind(NamingContext ctx, Binding binding) {
                        bindings.add(new Bound(ctx, binding.getName()));
                        return binding;
                    }
                };

                try {
                    localContextRoot.getRoot().addListener(listener);
                    XmlConfiguration configuration = new XmlConfiguration(jettyEnvXmlUrl);
                    configuration.configure(context);
                } finally {
                    localContextRoot.getRoot().removeListener(listener);
                    context.setAttribute(JETTY_ENV_BINDINGS, bindings);
                }
            }
        }

        //add java:comp/env entries for any EnvEntries that have been defined so far
        bindEnvEntries(context);
    }


    /**
     * Remove jndi setup from start
     *
     * @throws Exception
     * @see Configuration#deconfigure(WebAppContext)
     */
    @Override
    public void deconfigure(WebAppContext context) throws Exception {
        //get rid of any bindings for comp/env for webapp
        ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(context.getClassLoader());
        ContextFactory.associateClassLoader(context.getClassLoader());
        try {
            Context ic = new InitialContext();
            Context compCtx = (Context) ic.lookup("java:comp");
            compCtx.destroySubcontext("env");

            //unbind any NamingEntries that were configured in this webapp's name space
            @SuppressWarnings("unchecked")
            List<Bound> bindings = (List<Bound>) context.getAttribute(JETTY_ENV_BINDINGS);
            context.setAttribute(JETTY_ENV_BINDINGS, null);
            if (bindings != null) {
                Collections.reverse(bindings);
                for (Bound b : bindings)
                    b._context.destroySubcontext(b._name);
            }
        } catch (NameNotFoundException e) {
            LOG.warn(e);
        } finally {
            ContextFactory.disassociateClassLoader();
            Thread.currentThread().setContextClassLoader(oldLoader);
        }
    }


    /**
     * Remove all jndi setup
     *
     * @throws Exception
     * @see Configuration#deconfigure(WebAppContext)
     */
    @Override
    public void destroy(WebAppContext context) throws Exception {
        try {
            //unbind any NamingEntries that were configured in this webapp's name space
            NamingContext scopeContext = (NamingContext) NamingEntryUtil.getContextForScope(context);
            scopeContext.getParent().destroySubcontext(scopeContext.getName());
        } catch (NameNotFoundException e) {
            LOG.ignore(e);
            LOG.debug("No naming entries configured in environment for webapp " + context);
        }
    }

    /**
     * Bind all EnvEntries that have been declared, so that the processing of the
     * web.xml file can potentially override them.
     * <p>
     * We first bind EnvEntries declared in Server scope, then WebAppContext scope.
     *
     * @throws NamingException
     */
    public void bindEnvEntries(WebAppContext context)
            throws NamingException {
        LOG.debug("Binding env entries from the jvm scope");
        InitialContext ic = new InitialContext();
        Context envCtx = (Context) ic.lookup("java:comp/env");
        Object scope = null;
        List<Object> list = NamingEntryUtil.lookupNamingEntries(scope, EnvEntry.class);
        Iterator<Object> itor = list.iterator();
        while (itor.hasNext()) {
            EnvEntry ee = (EnvEntry) itor.next();
            ee.bindToENC(ee.getJndiName());
            Name namingEntryName = NamingEntryUtil.makeNamingEntryName(null, ee);
            NamingUtil.bind(envCtx, namingEntryName.toString(), ee);//also save the EnvEntry in the context so we can check it later
        }

        LOG.debug("Binding env entries from the server scope");

        scope = context.getServer();
        list = NamingEntryUtil.lookupNamingEntries(scope, EnvEntry.class);
        itor = list.iterator();
        while (itor.hasNext()) {
            EnvEntry ee = (EnvEntry) itor.next();
            ee.bindToENC(ee.getJndiName());
            Name namingEntryName = NamingEntryUtil.makeNamingEntryName(null, ee);
            NamingUtil.bind(envCtx, namingEntryName.toString(), ee);//also save the EnvEntry in the context so we can check it later
        }

        LOG.debug("Binding env entries from the context scope");
        scope = context;
        list = NamingEntryUtil.lookupNamingEntries(scope, EnvEntry.class);
        itor = list.iterator();
        while (itor.hasNext()) {
            EnvEntry ee = (EnvEntry) itor.next();
            ee.bindToENC(ee.getJndiName());
            Name namingEntryName = NamingEntryUtil.makeNamingEntryName(null, ee);
            NamingUtil.bind(envCtx, namingEntryName.toString(), ee);//also save the EnvEntry in the context so we can check it later
        }
    }

    protected void createEnvContext(WebAppContext wac)
            throws NamingException {
        ClassLoader old_loader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(wac.getClassLoader());
        ContextFactory.associateClassLoader(wac.getClassLoader());
        try {
            Context context = new InitialContext();
            Context compCtx = (Context) context.lookup("java:comp");
            compCtx.createSubcontext("env");
        } finally {
            ContextFactory.disassociateClassLoader();
            Thread.currentThread().setContextClassLoader(old_loader);
        }
    }

    private static class Bound {
        final NamingContext _context;
        final String _name;

        Bound(NamingContext context, String name) {
            _context = context;
            _name = name;
        }
    }
}
