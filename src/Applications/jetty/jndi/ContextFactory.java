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


import Applications.jetty.server.handler.ContextHandler;
import Applications.jetty.util.log.Logger;

import javax.naming.*;
import javax.naming.spi.ObjectFactory;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Map;
import java.util.WeakHashMap;


/**
 * ContextFactory.java
 * <p>
 * This is an object factory that produces a jndi naming
 * context based on a classloader.
 * <p>
 * It is used for the java:comp context.
 * <p>
 * This object factory is bound at java:comp. When a
 * lookup arrives for java:comp,  this object factory
 * is invoked and will return a context specific to
 * the caller's environment (so producing the java:comp/env
 * specific to a webapp).
 * <p>
 * The context selected is based on classloaders. First
 * we try looking at the thread context classloader if it is set, and walk its
 * hierarchy, creating a context if none is found. If the thread context classloader
 * is not set, then we use the classloader associated with the current Context.
 * <p>
 * If there is no current context, or no classloader, we return null.
 * <p>
 * Created: Fri Jun 27 09:26:40 2003
 */
public class ContextFactory implements ObjectFactory {
    private static Logger __log = NamingUtil.__log;

    /**
     * Map of classloaders to contexts.
     */
    private static final WeakHashMap __contextMap = new WeakHashMap();

    /**
     * Threadlocal for injecting a context to use
     * instead of looking up the map.
     */
    private static final ThreadLocal<Context> __threadContext = new ThreadLocal<Context>();

    /**
     * Threadlocal for setting a classloader which must be used
     * when finding the comp context.
     */
    private static final ThreadLocal<ClassLoader> __threadClassLoader = new ThreadLocal<ClassLoader>();


    /**
     * Find or create a context which pertains to a classloader.
     * <p>
     * If the thread context classloader is set, we try to find an already-created naming context
     * for it. If one does not exist, we walk its classloader hierarchy until one is found, or we
     * run out of parent classloaders. In the latter case, we will create a new naming context associated
     * with the original thread context classloader.
     * <p>
     * If the thread context classloader is not set, we obtain the classloader from the current
     * jetty Context, and look for an already-created naming context.
     * <p>
     * If there is no current jetty Context, or it has no associated classloader, we
     * return null.
     *
     * @see javax.naming.spi.ObjectFactory#getObjectInstance(java.lang.Object, javax.naming.Name, javax.naming.Context, java.util.Hashtable)
     */
    public Object getObjectInstance(Object obj,
                                    Name name,
                                    Context nameCtx,
                                    Hashtable env)
            throws Exception {
        //First, see if we have had a context injected into us to use.
        Context ctx = (Context) __threadContext.get();
        if (ctx != null) {
            if (__log.isDebugEnabled()) __log.debug("Using the Context that is bound on the thread");
            return ctx;
        }

        //See if there is a classloader to use for finding the comp context
        //Don't use its parent hierarchy if set.
        ClassLoader loader = (ClassLoader) __threadClassLoader.get();
        if (loader != null) {
            if (__log.isDebugEnabled() && loader != null) __log.debug("Using threadlocal classloader");
            ctx = getContextForClassLoader(loader);
            if (ctx == null) {
                ctx = newNamingContext(obj, loader, env, name, nameCtx);
                __contextMap.put(loader, ctx);
                if (__log.isDebugEnabled()) __log.debug("Made context " + name.get(0) + " for classloader: " + loader);
            }
            return ctx;
        }

        //If the thread context classloader is set, then try its hierarchy to find a matching context
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        loader = tccl;
        if (loader != null) {
            if (__log.isDebugEnabled() && loader != null) __log.debug("Trying thread context classloader");
            while (ctx == null && loader != null) {
                ctx = getContextForClassLoader(loader);
                if (ctx == null && loader != null)
                    loader = loader.getParent();
            }

            if (ctx == null) {
                ctx = newNamingContext(obj, tccl, env, name, nameCtx);
                __contextMap.put(tccl, ctx);
                if (__log.isDebugEnabled()) __log.debug("Made context " + name.get(0) + " for classloader: " + tccl);
            }
            return ctx;
        }


        //If trying thread context classloader hierarchy failed, try the
        //classloader associated with the current context
        if (ContextHandler.getCurrentContext() != null) {

            if (__log.isDebugEnabled() && loader != null)
                __log.debug("Trying classloader of current org.eclipse.jetty.server.handler.ContextHandler");
            loader = ContextHandler.getCurrentContext().getContextHandler().getClassLoader();
            ctx = (Context) __contextMap.get(loader);

            if (ctx == null && loader != null) {
                ctx = newNamingContext(obj, loader, env, name, nameCtx);
                __contextMap.put(loader, ctx);
                if (__log.isDebugEnabled()) __log.debug("Made context " + name.get(0) + " for classloader: " + loader);
            }

            return ctx;
        }
        return null;
    }


    /**
     * Create a new NamingContext.
     *
     * @param obj
     * @param loader
     * @param env
     * @param name
     * @param parentCtx
     * @throws Exception
     */
    public NamingContext newNamingContext(Object obj, ClassLoader loader, Hashtable env, Name name, Context parentCtx)
            throws Exception {
        Reference ref = (Reference) obj;
        StringRefAddr parserAddr = (StringRefAddr) ref.get("parser");
        String parserClassName = (parserAddr == null ? null : (String) parserAddr.getContent());
        NameParser parser = (NameParser) (parserClassName == null ? null : loader.loadClass(parserClassName).newInstance());

        return new NamingContext(env,
                name.get(0),
                (NamingContext) parentCtx,
                parser);
    }


    /**
     * Find the naming Context for the given classloader
     *
     * @param loader
     */
    public Context getContextForClassLoader(ClassLoader loader) {
        if (loader == null)
            return null;

        return (Context) __contextMap.get(loader);
    }


    /**
     * Associate the given Context with the current thread.
     * disassociate method should be called to reset the context.
     *
     * @param ctx the context to associate to the current thread.
     * @return the previous context associated on the thread (can be null)
     */
    public static Context associateContext(final Context ctx) {
        Context previous = (Context) __threadContext.get();
        __threadContext.set(ctx);
        return previous;
    }

    public static void disassociateContext(final Context ctx) {
        __threadContext.remove();
    }


    public static ClassLoader associateClassLoader(final ClassLoader loader) {
        ClassLoader prev = (ClassLoader) __threadClassLoader.get();
        __threadClassLoader.set(loader);
        return prev;
    }


    public static void disassociateClassLoader() {
        __threadClassLoader.remove();
    }

    public static void dump(Appendable out, String indent) throws IOException {
        out.append("o.e.j.jndi.ContextFactory@").append(Long.toHexString(__contextMap.hashCode())).append("\n");
        int size = __contextMap.size();
        int i = 0;
        for (Map.Entry<ClassLoader, NamingContext> entry : ((Map<ClassLoader, NamingContext>) __contextMap).entrySet()) {
            boolean last = ++i == size;
            ClassLoader loader = entry.getKey();
            out.append(indent).append(" +- ").append(loader.getClass().getSimpleName()).append("@").append(Long.toHexString(loader.hashCode())).append(": ");

            NamingContext context = entry.getValue();
            context.dump(out, indent + (last ? "    " : " |  "));
        }
    }

}
