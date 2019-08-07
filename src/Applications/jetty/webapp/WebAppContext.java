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

import Applications.jetty.security.ConstraintAware;
import Applications.jetty.security.ConstraintMapping;
import Applications.jetty.security.ConstraintSecurityHandler;
import Applications.jetty.security.SecurityHandler;
import Applications.jetty.server.Connector;
import Applications.jetty.server.HandlerContainer;
import Applications.jetty.server.Server;
import Applications.jetty.server.handler.ContextHandler;
import Applications.jetty.server.handler.ErrorHandler;
import Applications.jetty.server.session.SessionHandler;
import Applications.jetty.servlet.ErrorPageErrorHandler;
import Applications.jetty.servlet.ServletContextHandler;
import Applications.jetty.servlet.ServletHandler;
import Applications.jetty.util.Loader;
import Applications.jetty.util.MultiException;
import Applications.jetty.util.URIUtil;
import Applications.jetty.util.annotation.ManagedAttribute;
import Applications.jetty.util.annotation.ManagedObject;
import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;
import Applications.jetty.util.resource.Resource;
import Applications.jetty.util.resource.ResourceCollection;

import javax.servlet.ServletContext;
import javax.servlet.ServletRegistration.Dynamic;
import javax.servlet.ServletSecurityElement;
import javax.servlet.http.*;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.PermissionCollection;
import java.util.*;

/* ------------------------------------------------------------ */

/**
 * Web Application Context Handler.
 * The WebAppContext handler is an extension of ContextHandler that
 * coordinates the construction and configuration of nested handlers:
 * {@link org.eclipse.jetty.security.ConstraintSecurityHandler}, {@link org.eclipse.jetty.server.session.SessionHandler}
 * and {@link org.eclipse.jetty.servlet.ServletHandler}.
 * The handlers are configured by pluggable configuration classes, with
 * the default being  {@link org.eclipse.jetty.webapp.WebXmlConfiguration} and
 * {@link org.eclipse.jetty.webapp.JettyWebXmlConfiguration}.
 *
 * @org.apache.xbean.XBean description="Creates a servlet web application at a given context from a resource base"
 */
@ManagedObject("Web Application ContextHandler")
public class WebAppContext extends ServletContextHandler implements WebAppClassLoader.Context {
    private static final Logger LOG = Log.getLogger(WebAppContext.class);

    public static final String TEMPDIR = "javax.servlet.context.tempdir";
    public static final String BASETEMPDIR = "org.eclipse.jetty.webapp.basetempdir";
    public final static String WEB_DEFAULTS_XML = "org/eclipse/jetty/webapp/webdefault.xml";
    public final static String ERROR_PAGE = "org.eclipse.jetty.server.error_page";
    public final static String SERVER_SYS_CLASSES = "org.eclipse.jetty.webapp.systemClasses";
    public final static String SERVER_SRV_CLASSES = "org.eclipse.jetty.webapp.serverClasses";

    private String[] __dftProtectedTargets = {"/web-inf", "/meta-inf"};

    public static final String[] DEFAULT_CONFIGURATION_CLASSES =
            {
                    "org.eclipse.jetty.webapp.WebInfConfiguration",
                    "org.eclipse.jetty.webapp.WebXmlConfiguration",
                    "org.eclipse.jetty.webapp.MetaInfConfiguration",
                    "org.eclipse.jetty.webapp.FragmentConfiguration",
                    "org.eclipse.jetty.webapp.JettyWebXmlConfiguration"
            };

    // System classes are classes that cannot be replaced by
    // the web application, and they are *always* loaded via
    // system classloader.
    public final static String[] __dftSystemClasses =
            {
                    "java.",                            // Java SE classes (per servlet spec v2.5 / SRV.9.7.2)
                    "javax.",                           // Java SE classes (per servlet spec v2.5 / SRV.9.7.2)
                    "org.xml.",                         // needed by javax.xml
                    "org.w3c.",                         // needed by javax.xml
                    "org.eclipse.jetty.continuation.",  // webapp cannot change continuation classes
                    "org.eclipse.jetty.jndi.",          // webapp cannot change naming classes
                    "org.eclipse.jetty.jaas.",          // webapp cannot change jaas classes
                    "org.eclipse.jetty.websocket.",     // webapp cannot change / replace websocket classes
                    "org.eclipse.jetty.servlet.DefaultServlet" // webapp cannot change default servlets
            };

    // Server classes are classes that are hidden from being
    // loaded by the web application using system classloader,
    // so if web application needs to load any of such classes,
    // it has to include them in its distribution.
    public final static String[] __dftServerClasses =
            {
                    "-org.eclipse.jetty.continuation.", // don't hide continuation classes
                    "-org.eclipse.jetty.jndi.",         // don't hide naming classes
                    "-org.eclipse.jetty.jaas.",         // don't hide jaas classes
                    "-org.eclipse.jetty.servlets.",     // don't hide jetty servlets
                    "-org.eclipse.jetty.servlet.DefaultServlet", // don't hide default servlet
                    "-org.eclipse.jetty.servlet.listener.", // don't hide useful listeners
                    "-org.eclipse.jetty.websocket.",    // don't hide websocket classes from webapps (allow webapp to use ones from system classloader)
                    "org.eclipse.jetty."                // hide other jetty classes
            };

    private final List<String> _configurationClasses = new ArrayList<>();
    private ClasspathPattern _systemClasses = null;
    private ClasspathPattern _serverClasses = null;

    private final List<Configuration> _configurations = new ArrayList<>();
    private String _defaultsDescriptor = WEB_DEFAULTS_XML;
    private String _descriptor = null;
    private final List<String> _overrideDescriptors = new ArrayList<>();
    private boolean _distributable = false;
    private boolean _extractWAR = true;
    private boolean _copyDir = false;
    private boolean _copyWebInf = false;
    private boolean _logUrlOnStart = false;
    private boolean _parentLoaderPriority = Boolean.getBoolean("org.eclipse.jetty.server.webapp.parentLoaderPriority");
    private PermissionCollection _permissions;

    private String[] _contextWhiteList = null;

    private File _tmpDir;
    private boolean _persistTmpDir = false;

    private String _war;
    private String _extraClasspath;
    private Throwable _unavailableException;

    private Map<String, String> _resourceAliases;
    private boolean _ownClassLoader = false;
    private boolean _configurationDiscovered = true;
    private boolean _allowDuplicateFragmentNames = false;
    private boolean _throwUnavailableOnStartupException = false;


    private MetaData _metadata = new MetaData();

    public static WebAppContext getCurrentWebAppContext() {
        ContextHandler.Context context = ContextHandler.getCurrentContext();
        if (context != null) {
            ContextHandler handler = context.getContextHandler();
            if (handler instanceof WebAppContext)
                return (WebAppContext) handler;
        }
        return null;
    }

    /* ------------------------------------------------------------ */
    public WebAppContext() {
        this(null, null, null, null, null, new ErrorPageErrorHandler(), SESSIONS | SECURITY);
    }

    /* ------------------------------------------------------------ */

    /**
     * @param contextPath The context path
     * @param webApp      The URL or filename of the webapp directory or war file.
     */
    public WebAppContext(String webApp, String contextPath) {
        this(null, contextPath, null, null, null, new ErrorPageErrorHandler(), SESSIONS | SECURITY);
        setWar(webApp);
    }

    /* ------------------------------------------------------------ */

    /**
     * @param parent      The parent HandlerContainer.
     * @param contextPath The context path
     * @param webApp      The URL or filename of the webapp directory or war file.
     */
    public WebAppContext(HandlerContainer parent, String webApp, String contextPath) {
        this(parent, contextPath, null, null, null, new ErrorPageErrorHandler(), SESSIONS | SECURITY);
        setWar(webApp);
    }

    /* ------------------------------------------------------------ */

    /**
     * This constructor is used in the geronimo integration.
     *
     * @param sessionHandler  SessionHandler for this web app
     * @param securityHandler SecurityHandler for this web app
     * @param servletHandler  ServletHandler for this web app
     * @param errorHandler    ErrorHandler for this web app
     */
    public WebAppContext(SessionHandler sessionHandler, SecurityHandler securityHandler, ServletHandler servletHandler, ErrorHandler errorHandler) {
        this(null, null, sessionHandler, securityHandler, servletHandler, errorHandler, 0);
    }

    /* ------------------------------------------------------------ */

    /**
     * This constructor is used in the geronimo integration.
     *
     * @param sessionHandler  SessionHandler for this web app
     * @param securityHandler SecurityHandler for this web app
     * @param servletHandler  ServletHandler for this web app
     * @param errorHandler    ErrorHandler for this web app
     */
    public WebAppContext(HandlerContainer parent, String contextPath, SessionHandler sessionHandler, SecurityHandler securityHandler, ServletHandler servletHandler, ErrorHandler errorHandler, int options) {
        super(parent, contextPath, sessionHandler, securityHandler, servletHandler, errorHandler, options);
        _scontext = new Context();
        setErrorHandler(errorHandler != null ? errorHandler : new ErrorPageErrorHandler());
        setProtectedTargets(__dftProtectedTargets);
    }

    /* ------------------------------------------------------------ */

    /**
     * @param servletContextName The servletContextName to set.
     */
    @Override
    public void setDisplayName(String servletContextName) {
        super.setDisplayName(servletContextName);
        ClassLoader cl = getClassLoader();
        if (cl != null && cl instanceof WebAppClassLoader && servletContextName != null)
            ((WebAppClassLoader) cl).setName(servletContextName);
    }

    /* ------------------------------------------------------------ */

    /**
     * Get an exception that caused the webapp to be unavailable
     *
     * @return A throwable if the webapp is unavailable or null
     */
    public Throwable getUnavailableException() {
        return _unavailableException;
    }


    /* ------------------------------------------------------------ */

    /**
     * Set Resource Alias.
     * Resource aliases map resource uri's within a context.
     * They may optionally be used by a handler when looking for
     * a resource.
     *
     * @param alias
     * @param uri
     */
    public void setResourceAlias(String alias, String uri) {
        if (_resourceAliases == null)
            _resourceAliases = new HashMap<String, String>(5);
        _resourceAliases.put(alias, uri);
    }

    /* ------------------------------------------------------------ */
    public Map<String, String> getResourceAliases() {
        if (_resourceAliases == null)
            return null;
        return _resourceAliases;
    }

    /* ------------------------------------------------------------ */
    public void setResourceAliases(Map<String, String> map) {
        _resourceAliases = map;
    }

    /* ------------------------------------------------------------ */
    public String getResourceAlias(String path) {
        if (_resourceAliases == null)
            return null;
        String alias = _resourceAliases.get(path);

        int slash = path.length();
        while (alias == null) {
            slash = path.lastIndexOf("/", slash - 1);
            if (slash < 0)
                break;
            String match = _resourceAliases.get(path.substring(0, slash + 1));
            if (match != null)
                alias = match + path.substring(slash + 1);
        }
        return alias;
    }

    /* ------------------------------------------------------------ */
    public String removeResourceAlias(String alias) {
        if (_resourceAliases == null)
            return null;
        return _resourceAliases.remove(alias);
    }

    /* ------------------------------------------------------------ */
    /* (non-Javadoc)
     * @see org.eclipse.jetty.server.server.handler.ContextHandler#setClassLoader(java.lang.ClassLoader)
     */
    @Override
    public void setClassLoader(ClassLoader classLoader) {
        super.setClassLoader(classLoader);

//        if ( !(classLoader instanceof WebAppClassLoader) )
//        {
//            LOG.info("NOTE: detected a classloader which is not an instance of WebAppClassLoader being set on WebAppContext, some typical class and resource locations may be missing on: " + toString() );
//        }

        if (classLoader != null && classLoader instanceof WebAppClassLoader && getDisplayName() != null)
            ((WebAppClassLoader) classLoader).setName(getDisplayName());
    }

    /* ------------------------------------------------------------ */
    @Override
    public Resource getResource(String uriInContext) throws MalformedURLException {
        if (uriInContext == null || !uriInContext.startsWith(URIUtil.SLASH))
            throw new MalformedURLException(uriInContext);

        IOException ioe = null;
        Resource resource = null;
        int loop = 0;
        while (uriInContext != null && loop++ < 100) {
            try {
                resource = super.getResource(uriInContext);
                if (resource != null && resource.exists())
                    return resource;

                uriInContext = getResourceAlias(uriInContext);
            } catch (IOException e) {
                LOG.ignore(e);
                if (ioe == null)
                    ioe = e;
            }
        }

        if (ioe != null && ioe instanceof MalformedURLException)
            throw (MalformedURLException) ioe;

        return resource;
    }


    /* ------------------------------------------------------------ */

    /**
     * Is the context Automatically configured.
     *
     * @return true if configuration discovery.
     */
    public boolean isConfigurationDiscovered() {
        return _configurationDiscovered;
    }

    /* ------------------------------------------------------------ */

    /**
     * Set the configuration discovery mode.
     * If configuration discovery is set to true, then the JSR315
     * servlet 3.0 discovered configuration features are enabled.
     * These are:<ul>
     * <li>Web Fragments</li>
     * <li>META-INF/resource directories</li>
     * </ul>
     *
     * @param discovered true if configuration discovery is enabled for automatic configuration from the context
     */
    public void setConfigurationDiscovered(boolean discovered) {
        _configurationDiscovered = discovered;
    }

    /* ------------------------------------------------------------ */

    /**
     * Pre configure the web application.
     * <p>
     * The method is normally called from {@link #start()}. It performs
     * the discovery of the configurations to be applied to this context,
     * specifically:<ul>
     * <li>Instantiate the {@link Configuration} instances with a call to {@link #loadConfigurations()}.
     * <li>Setup the default System classes by calling {@link #loadSystemClasses()}
     * <li>Setup the default Server classes by calling <code>loadServerClasses()</code>
     * <li>Instantiates a classload (if one is not already set)
     * <li>Calls the {@link Configuration#preConfigure(WebAppContext)} method of all
     * Configuration instances.
     * </ul>
     *
     * @throws Exception
     */
    public void preConfigure() throws Exception {
        // Setup configurations
        loadConfigurations();

        // Setup system classes
        loadSystemClasses();

        // Setup server classes
        loadServerClasses();

        // Configure classloader
        _ownClassLoader = false;
        if (getClassLoader() == null) {
            WebAppClassLoader classLoader = new WebAppClassLoader(this);
            setClassLoader(classLoader);
            _ownClassLoader = true;
        }

        if (LOG.isDebugEnabled()) {
            ClassLoader loader = getClassLoader();
            LOG.debug("Thread Context classloader {}", loader);
            loader = loader.getParent();
            while (loader != null) {
                LOG.debug("Parent class loader: {} ", loader);
                loader = loader.getParent();
            }
        }

        // Prepare for configuration
        for (Configuration configuration : _configurations) {
            LOG.debug("preConfigure {} with {}", this, configuration);
            configuration.preConfigure(this);
        }
    }

    /* ------------------------------------------------------------ */
    public void configure() throws Exception {
        // Configure webapp
        for (Configuration configuration : _configurations) {
            LOG.debug("configure {} with {}", this, configuration);
            configuration.configure(this);
        }
    }

    /* ------------------------------------------------------------ */
    public void postConfigure() throws Exception {
        // Clean up after configuration
        for (Configuration configuration : _configurations) {
            LOG.debug("postConfigure {} with {}", this, configuration);
            configuration.postConfigure(this);
        }
    }

    /* ------------------------------------------------------------ */
    /*
     * @see org.eclipse.thread.AbstractLifeCycle#doStart()
     */
    @Override
    protected void doStart() throws Exception {
        try {
            _metadata.setAllowDuplicateFragmentNames(isAllowDuplicateFragmentNames());
            preConfigure();
            super.doStart();
            postConfigure();

            if (isLogUrlOnStart())
                dumpUrl();
        } catch (Exception e) {
            //start up of the webapp context failed, make sure it is not started
            LOG.warn("Failed startup of context " + this, e);
            _unavailableException = e;
            setAvailable(false);
            if (isThrowUnavailableOnStartupException())
                throw e;
        }
    }

    /* ------------------------------------------------------------ */
    /*
     * @see org.eclipse.thread.AbstractLifeCycle#doStop()
     */
    @Override
    protected void doStop() throws Exception {
        super.doStop();

        try {
            for (int i = _configurations.size(); i-- > 0; )
                _configurations.get(i).deconfigure(this);

            if (_metadata != null)
                _metadata.clear();
            _metadata = new MetaData();

        } finally {
            if (_ownClassLoader)
                setClassLoader(null);

            setAvailable(true);
            _unavailableException = null;
        }
    }

    /* ------------------------------------------------------------ */
    @Override
    public void destroy() {
        // Prepare for configuration
        MultiException mx = new MultiException();
        if (_configurations != null) {
            for (int i = _configurations.size(); i-- > 0; ) {
                try {
                    _configurations.get(i).destroy(this);
                } catch (Exception e) {
                    mx.add(e);
                }
            }
        }
        _configurations.clear();
        super.destroy();
        mx.ifExceptionThrowRuntime();
    }


    /* ------------------------------------------------------------ */
    /*
     * Dumps the current web app name and URL to the log
     */
    private void dumpUrl() {
        Connector[] connectors = getServer().getConnectors();
        for (int i = 0; i < connectors.length; i++) {
            String displayName = getDisplayName();
            if (displayName == null)
                displayName = "WebApp@" + connectors.hashCode();

            LOG.info(displayName + " at http://" + connectors[i].toString() + getContextPath());
        }
    }

    /* ------------------------------------------------------------ */

    /**
     * @return Returns the configurations.
     */
    @ManagedAttribute(value = "configuration classes used to configure webapp", readonly = true)
    public String[] getConfigurationClasses() {
        return _configurationClasses.toArray(new String[_configurationClasses.size()]);
    }

    /* ------------------------------------------------------------ */

    /**
     * @return Returns the configurations.
     */
    public Configuration[] getConfigurations() {
        return _configurations.toArray(new Configuration[_configurations.size()]);
    }

    /* ------------------------------------------------------------ */

    /**
     * The default descriptor is a web.xml format file that is applied to the context before the standard WEB-INF/web.xml
     *
     * @return Returns the defaultsDescriptor.
     */
    @ManagedAttribute(value = "default web.xml deascriptor applied before standard web.xml", readonly = true)
    public String getDefaultsDescriptor() {
        return _defaultsDescriptor;
    }

    /* ------------------------------------------------------------ */

    /**
     * The override descriptor is a web.xml format file that is applied to the context after the standard WEB-INF/web.xml
     *
     * @return Returns the Override Descriptor.
     */
    public String getOverrideDescriptor() {
        if (_overrideDescriptors.size() != 1)
            return null;
        return _overrideDescriptors.get(0);
    }

    /* ------------------------------------------------------------ */

    /**
     * An override descriptor is a web.xml format file that is applied to the context after the standard WEB-INF/web.xml
     *
     * @return Returns the Override Descriptor list
     */
    @ManagedAttribute(value = "web.xml deascriptors applied after standard web.xml", readonly = true)
    public List<String> getOverrideDescriptors() {
        return Collections.unmodifiableList(_overrideDescriptors);
    }

    /* ------------------------------------------------------------ */

    /**
     * @return Returns the permissions.
     */
    @Override
    public PermissionCollection getPermissions() {
        return _permissions;
    }

    /* ------------------------------------------------------------ */

    /**
     * @return Returns the serverClasses.
     * @see #setServerClasses(String[])
     */
    @ManagedAttribute(value = "classes and packages hidden by the context classloader", readonly = true)
    public String[] getServerClasses() {
        if (_serverClasses == null)
            loadServerClasses();

        return _serverClasses.getPatterns();
    }

    /* ------------------------------------------------------------ */

    /**
     * Add to the list of Server classes.
     *
     * @param classOrPackage A fully qualified class name (eg com.foo.MyClass)
     *                       or a qualified package name ending with '.' (eg com.foo.).  If the class
     *                       or package has '-' it is excluded from the server classes and order is thus
     *                       important when added system class patterns. This argument may also be a comma
     *                       separated list of classOrPackage patterns.
     * @see #setServerClasses(String[])
     * @see <a href="http://www.eclipse.org/jetty/documentation/current/jetty-classloading.html">Jetty Documentation: Classloading</a>
     */
    public void addServerClass(String classOrPackage) {
        if (_serverClasses == null)
            loadServerClasses();

        _serverClasses.addPattern(classOrPackage);
    }

    /* ------------------------------------------------------------ */

    /**
     * Prepend to the list of Server classes.
     *
     * @param classOrPackage A fully qualified class name (eg com.foo.MyClass)
     *                       or a qualified package name ending with '.' (eg com.foo.).  If the class
     *                       or package has '-' it is excluded from the server classes and order is thus
     *                       important when added system class patterns. This argument may also be a comma
     *                       separated list of classOrPackage patterns.
     * @see #setServerClasses(String[])
     * @see <a href="http://www.eclipse.org/jetty/documentation/current/jetty-classloading.html">Jetty Documentation: Classloading</a>
     */
    public void prependServerClass(String classOrPackage) {
        if (_serverClasses == null)
            loadServerClasses();

        _serverClasses.prependPattern(classOrPackage);
    }

    /* ------------------------------------------------------------ */

    /**
     * @return Returns the systemClasses.
     * @see #setSystemClasses(String[])
     */
    @ManagedAttribute(value = "classes and packages given priority by context classloader", readonly = true)
    public String[] getSystemClasses() {
        if (_systemClasses == null)
            loadSystemClasses();

        return _systemClasses.getPatterns();
    }

    /* ------------------------------------------------------------ */

    /**
     * Add to the list of System classes.
     *
     * @param classOrPackage A fully qualified class name (eg com.foo.MyClass)
     *                       or a qualified package name ending with '.' (eg com.foo.).  If the class
     *                       or package has '-' it is excluded from the system classes and order is thus
     *                       important when added system class patterns.  This argument may also be a comma
     *                       separated list of classOrPackage patterns.
     * @see #setSystemClasses(String[])
     * @see <a href="http://www.eclipse.org/jetty/documentation/current/jetty-classloading.html">Jetty Documentation: Classloading</a>
     */
    public void addSystemClass(String classOrPackage) {
        if (_systemClasses == null)
            loadSystemClasses();

        _systemClasses.addPattern(classOrPackage);
    }


    /* ------------------------------------------------------------ */

    /**
     * Prepend to the list of System classes.
     *
     * @param classOrPackage A fully qualified class name (eg com.foo.MyClass)
     *                       or a qualified package name ending with '.' (eg com.foo.).  If the class
     *                       or package has '-' it is excluded from the system classes and order is thus
     *                       important when added system class patterns.This argument may also be a comma
     *                       separated list of classOrPackage patterns.
     * @see #setSystemClasses(String[])
     * @see <a href="http://www.eclipse.org/jetty/documentation/current/jetty-classloading.html">Jetty Documentation: Classloading</a>
     */
    public void prependSystemClass(String classOrPackage) {
        if (_systemClasses == null)
            loadSystemClasses();

        _systemClasses.prependPattern(classOrPackage);
    }

    /* ------------------------------------------------------------ */
    @Override
    public boolean isServerClass(String name) {
        if (_serverClasses == null)
            loadServerClasses();

        return _serverClasses.match(name);
    }

    /* ------------------------------------------------------------ */
    @Override
    public boolean isSystemClass(String name) {
        if (_systemClasses == null)
            loadSystemClasses();

        return _systemClasses.match(name);
    }

    /* ------------------------------------------------------------ */
    protected void loadSystemClasses() {
        if (_systemClasses != null)
            return;

        //look for a Server attribute with the list of System classes
        //to apply to every web application. If not present, use our defaults.
        Server server = getServer();
        if (server != null) {
            Object systemClasses = server.getAttribute(SERVER_SYS_CLASSES);
            if (systemClasses != null && systemClasses instanceof String[])
                _systemClasses = new ClasspathPattern((String[]) systemClasses);
        }

        if (_systemClasses == null)
            _systemClasses = new ClasspathPattern(__dftSystemClasses);
    }

    /* ------------------------------------------------------------ */
    private void loadServerClasses() {
        if (_serverClasses != null) {
            return;
        }

        // look for a Server attribute with the list of Server classes
        // to apply to every web application. If not present, use our defaults.
        Server server = getServer();
        if (server != null) {
            Object serverClasses = server.getAttribute(SERVER_SRV_CLASSES);
            if (serverClasses != null && serverClasses instanceof String[]) {
                _serverClasses = new ClasspathPattern((String[]) serverClasses);
            }
        }

        if (_serverClasses == null) {
            _serverClasses = new ClasspathPattern(__dftServerClasses);
        }
    }

    /* ------------------------------------------------------------ */

    /**
     * @return Returns the war as a file or URL string (Resource)
     */
    @ManagedAttribute(value = "war file location", readonly = true)
    public String getWar() {
        if (_war == null)
            _war = getResourceBase();
        return _war;
    }

    /* ------------------------------------------------------------ */
    public Resource getWebInf() throws IOException {
        if (super.getBaseResource() == null)
            return null;

        // Iw there a WEB-INF directory?
        Resource web_inf = super.getBaseResource().addPath("WEB-INF/");
        if (!web_inf.exists() || !web_inf.isDirectory())
            return null;

        return web_inf;
    }

    /* ------------------------------------------------------------ */

    /**
     * @return Returns the distributable.
     */
    @ManagedAttribute("web application distributable")
    public boolean isDistributable() {
        return _distributable;
    }

    /* ------------------------------------------------------------ */

    /**
     * @return Returns the extractWAR.
     */
    @ManagedAttribute(value = "extract war", readonly = true)
    public boolean isExtractWAR() {
        return _extractWAR;
    }

    /* ------------------------------------------------------------ */

    /**
     * @return True if the webdir is copied (to allow hot replacement of jars on windows)
     */
    @ManagedAttribute(value = "webdir copied on deploy (allows hot replacement on windows)", readonly = true)
    public boolean isCopyWebDir() {
        return _copyDir;
    }

    /* ------------------------------------------------------------ */

    /**
     * @return True if the web-inf lib and classes directories are copied (to allow hot replacement of jars on windows)
     */
    public boolean isCopyWebInf() {
        return _copyWebInf;
    }

    /* ------------------------------------------------------------ */

    /**
     * @return True if the classloader should delegate first to the parent
     * classloader (standard java behaviour) or false if the classloader
     * should first try to load from WEB-INF/lib or WEB-INF/classes (servlet
     * spec recommendation). Default is false or can be set by the system
     * property org.eclipse.jetty.server.webapp.parentLoaderPriority
     */
    @Override
    @ManagedAttribute(value = "parent classloader given priority", readonly = true)
    public boolean isParentLoaderPriority() {
        return _parentLoaderPriority;
    }


    /* ------------------------------------------------------------ */
    public static String[] getDefaultConfigurationClasses() {
        return DEFAULT_CONFIGURATION_CLASSES;
    }

    /* ------------------------------------------------------------ */
    public String[] getDefaultServerClasses() {
        return __dftServerClasses;
    }

    /* ------------------------------------------------------------ */
    public String[] getDefaultSystemClasses() {
        return __dftSystemClasses;
    }

    /* ------------------------------------------------------------ */
    protected void loadConfigurations()
            throws Exception {
        //if the configuration instances have been set explicitly, use them
        if (_configurations.size() > 0)
            return;

        if (_configurationClasses.size() == 0)
            _configurationClasses.addAll(Configuration.ClassList.serverDefault(getServer()));
        for (String configClass : _configurationClasses)
            _configurations.add((Configuration) Loader.loadClass(this.getClass(), configClass).newInstance());
    }

    /* ------------------------------------------------------------ */
    @Override
    public String toString() {
        if (_war != null) {
            String war = _war;
            if (war.indexOf("/webapps/") >= 0)
                war = war.substring(war.indexOf("/webapps/") + 8);
            return super.toString() + "{" + war + "}";
        }
        return super.toString();
    }

    /* ------------------------------------------------------------ */

    /**
     * @param configurations The configuration class names.  If setConfigurations is not called
     *                       these classes are used to create a configurations array.
     */
    public void setConfigurationClasses(String[] configurations) {
        if (isRunning())
            throw new IllegalStateException();
        _configurationClasses.clear();
        if (configurations != null)
            _configurationClasses.addAll(Arrays.asList(configurations));
        _configurations.clear();
    }

    public void setConfigurationClasses(List<String> configurations) {
        setConfigurationClasses(configurations.toArray(new String[configurations.size()]));
    }
    
    /* ------------------------------------------------------------ */

    /**
     * @param configurations The configurations to set.
     */
    public void setConfigurations(Configuration[] configurations) {
        if (isRunning())
            throw new IllegalStateException();
        _configurations.clear();
        if (configurations != null)
            _configurations.addAll(Arrays.asList(configurations));
    }

    /* ------------------------------------------------------------ */

    /**
     * The default descriptor is a web.xml format file that is applied to the context before the standard WEB-INF/web.xml
     *
     * @param defaultsDescriptor The defaultsDescriptor to set.
     */
    public void setDefaultsDescriptor(String defaultsDescriptor) {
        _defaultsDescriptor = defaultsDescriptor;
    }

    /* ------------------------------------------------------------ */

    /**
     * The override descriptor is a web.xml format file that is applied to the context after the standard WEB-INF/web.xml
     *
     * @param overrideDescriptor The overrideDescritpor to set.
     */
    public void setOverrideDescriptor(String overrideDescriptor) {
        _overrideDescriptors.clear();
        _overrideDescriptors.add(overrideDescriptor);
    }

    /* ------------------------------------------------------------ */

    /**
     * The override descriptor is a web.xml format file that is applied to the context after the standard WEB-INF/web.xml
     *
     * @param overrideDescriptors The overrideDescriptors (file or URL) to set.
     */
    public void setOverrideDescriptors(List<String> overrideDescriptors) {
        _overrideDescriptors.clear();
        _overrideDescriptors.addAll(overrideDescriptors);
    }

    /* ------------------------------------------------------------ */

    /**
     * The override descriptor is a web.xml format file that is applied to the context after the standard WEB-INF/web.xml
     *
     * @param overrideDescriptor The overrideDescriptor (file or URL) to add.
     */
    public void addOverrideDescriptor(String overrideDescriptor) {
        _overrideDescriptors.add(overrideDescriptor);
    }

    /* ------------------------------------------------------------ */

    /**
     * @return the web.xml descriptor to use. If set to null, WEB-INF/web.xml is used if it exists.
     */
    @ManagedAttribute(value = "standard web.xml descriptor", readonly = true)
    public String getDescriptor() {
        return _descriptor;
    }

    /* ------------------------------------------------------------ */

    /**
     * @param descriptor the web.xml descriptor to use. If set to null, WEB-INF/web.xml is used if it exists.
     */
    public void setDescriptor(String descriptor) {
        _descriptor = descriptor;
    }

    /* ------------------------------------------------------------ */

    /**
     * @param distributable The distributable to set.
     */
    public void setDistributable(boolean distributable) {
        this._distributable = distributable;
    }

    /* ------------------------------------------------------------ */
    @Override
    public void setEventListeners(EventListener[] eventListeners) {
        if (_sessionHandler != null)
            _sessionHandler.clearEventListeners();

        super.setEventListeners(eventListeners);
    }

    /* ------------------------------------------------------------ */

    /**
     * Add EventListener
     * Convenience method that calls {@link #setEventListeners(EventListener[])}
     *
     * @param listener
     */
    @Override
    public void addEventListener(EventListener listener) {
        super.addEventListener(listener);
        if ((listener instanceof HttpSessionActivationListener)
                || (listener instanceof HttpSessionAttributeListener)
                || (listener instanceof HttpSessionBindingListener)
                || (listener instanceof HttpSessionListener)
                || (listener instanceof HttpSessionIdListener)) {
            if (_sessionHandler != null)
                _sessionHandler.addEventListener(listener);
        }
    }

    @Override
    public void removeEventListener(EventListener listener) {
        super.removeEventListener(listener);
        if ((listener instanceof HttpSessionActivationListener)
                || (listener instanceof HttpSessionAttributeListener)
                || (listener instanceof HttpSessionBindingListener)
                || (listener instanceof HttpSessionListener)
                || (listener instanceof HttpSessionIdListener)) {
            if (_sessionHandler != null)
                _sessionHandler.removeEventListener(listener);
        }

    }


    /* ------------------------------------------------------------ */

    /**
     * @param extractWAR True if war files are extracted
     */
    public void setExtractWAR(boolean extractWAR) {
        _extractWAR = extractWAR;
    }

    /* ------------------------------------------------------------ */

    /**
     * @param copy True if the webdir is copied (to allow hot replacement of jars)
     */
    public void setCopyWebDir(boolean copy) {
        _copyDir = copy;
    }

    /* ------------------------------------------------------------ */

    /**
     * @param copyWebInf True if the web-inf lib and classes directories are copied (to allow hot replacement of jars on windows)
     */
    public void setCopyWebInf(boolean copyWebInf) {
        _copyWebInf = copyWebInf;
    }

    /* ------------------------------------------------------------ */

    /**
     * @param java2compliant True if the classloader should delegate first to the parent
     *                       classloader (standard java behaviour) or false if the classloader
     *                       should first try to load from WEB-INF/lib or WEB-INF/classes (servlet
     *                       spec recommendation).  Default is false or can be set by the system
     *                       property org.eclipse.jetty.server.webapp.parentLoaderPriority
     */
    public void setParentLoaderPriority(boolean java2compliant) {
        _parentLoaderPriority = java2compliant;
    }

    /* ------------------------------------------------------------ */

    /**
     * @param permissions The permissions to set.
     */
    public void setPermissions(PermissionCollection permissions) {
        _permissions = permissions;
    }

    /**
     * Set the context white list
     * <p>
     * In certain circumstances you want may want to deny access of one webapp from another
     * when you may not fully trust the webapp.  Setting this white list will enable a
     * check when a servlet called getContext(String), validating that the uriInPath
     * for the given webapp has been declaratively allows access to the context.
     *
     * @param contextWhiteList
     */
    public void setContextWhiteList(String[] contextWhiteList) {
        _contextWhiteList = contextWhiteList;
    }

    /* ------------------------------------------------------------ */

    /**
     * Set the server classes patterns.
     * <p>
     * Server classes/packages are classes used to implement the server and are hidden
     * from the context.  If the context needs to load these classes, it must have its
     * own copy of them in WEB-INF/lib or WEB-INF/classes.
     * A class pattern is a string of one of the forms:<dl>
     * <dt>org.package.Classname</dt><dd>Match a specific class</dd>
     * <dt>org.package.</dt><dd>Match a specific package hierarchy</dd>
     * <dt>-org.package.Classname</dt><dd>Exclude a specific class</dd>
     * <dt>-org.package.</dt><dd>Exclude a specific package hierarchy</dd>
     * </dl>
     *
     * @param serverClasses The serverClasses to set.
     */
    public void setServerClasses(String[] serverClasses) {
        _serverClasses = new ClasspathPattern(serverClasses);
    }

    /* ------------------------------------------------------------ */

    /**
     * Set the system classes patterns.
     * <p>
     * System classes/packages are classes provided by the JVM and that
     * cannot be replaced by classes of the same name from WEB-INF,
     * regardless of the value of {@link #setParentLoaderPriority(boolean)}.
     * A class pattern is a string of one of the forms:<dl>
     * <dt>org.package.Classname</dt><dd>Match a specific class</dd>
     * <dt>org.package.</dt><dd>Match a specific package hierarchy</dd>
     * <dt>-org.package.Classname</dt><dd>Exclude a specific class</dd>
     * <dt>-org.package.</dt><dd>Exclude a specific package hierarchy</dd>
     * </dl>
     *
     * @param systemClasses The systemClasses to set.
     */
    public void setSystemClasses(String[] systemClasses) {
        _systemClasses = new ClasspathPattern(systemClasses);
    }


    /* ------------------------------------------------------------ */

    /**
     * Set temporary directory for context.
     * The javax.servlet.context.tempdir attribute is also set.
     *
     * @param dir Writable temporary directory.
     */
    public void setTempDirectory(File dir) {
        if (isStarted())
            throw new IllegalStateException("Started");

        if (dir != null) {
            try {
                dir = new File(dir.getCanonicalPath());
            } catch (IOException e) {
                LOG.warn(Log.EXCEPTION, e);
            }
        }

        if (dir != null && !dir.exists()) {
            dir.mkdir();
            dir.deleteOnExit();
        }

        if (dir != null && (!dir.exists() || !dir.isDirectory() || !dir.canWrite()))
            throw new IllegalArgumentException("Bad temp directory: " + dir);

        try {
            if (dir != null)
                dir = dir.getCanonicalFile();
        } catch (Exception e) {
            LOG.warn(e);
        }
        _tmpDir = dir;
        setAttribute(TEMPDIR, _tmpDir);
    }

    /* ------------------------------------------------------------ */
    @ManagedAttribute(value = "temporary directory location", readonly = true)
    public File getTempDirectory() {
        return _tmpDir;
    }

    /**
     * If true the temp directory for this
     * webapp will be kept when the webapp stops. Otherwise,
     * it will be deleted.
     *
     * @param delete
     */
    public void setPersistTempDirectory(boolean persist) {
        _persistTmpDir = persist;
    }

    /**
     * @return
     */
    public boolean isPersistTempDirectory() {
        return _persistTmpDir;
    }
    
    
    /* ------------------------------------------------------------ */

    /**
     * @param war The war to set as a file name or URL
     */
    public void setWar(String war) {
        _war = war;
    }

    /* ------------------------------------------------------------ */

    /**
     * @return Comma or semicolon separated path of filenames or URLs
     * pointing to directories or jar files. Directories should end
     * with '/'.
     */
    @Override
    @ManagedAttribute(value = "extra classpath for context classloader", readonly = true)
    public String getExtraClasspath() {
        return _extraClasspath;
    }

    /* ------------------------------------------------------------ */

    /**
     * @param extraClasspath Comma or semicolon separated path of filenames or URLs
     *                       pointing to directories or jar files. Directories should end
     *                       with '/'.
     */
    public void setExtraClasspath(String extraClasspath) {
        _extraClasspath = extraClasspath;
    }

    /* ------------------------------------------------------------ */
    public boolean isLogUrlOnStart() {
        return _logUrlOnStart;
    }

    /* ------------------------------------------------------------ */

    /**
     * Sets whether or not the web app name and URL is logged on startup
     *
     * @param logOnStart whether or not the log message is created
     */
    public void setLogUrlOnStart(boolean logOnStart) {
        this._logUrlOnStart = logOnStart;
    }

    /* ------------------------------------------------------------ */
    @Override
    public void setServer(Server server) {
        super.setServer(server);
    }

    /* ------------------------------------------------------------ */
    public boolean isAllowDuplicateFragmentNames() {
        return _allowDuplicateFragmentNames;
    }

    /* ------------------------------------------------------------ */
    public void setAllowDuplicateFragmentNames(boolean allowDuplicateFragmentNames) {
        _allowDuplicateFragmentNames = allowDuplicateFragmentNames;
    }

    /* ------------------------------------------------------------ */
    public void setThrowUnavailableOnStartupException(boolean throwIfStartupException) {
        _throwUnavailableOnStartupException = throwIfStartupException;
    }

    /* ------------------------------------------------------------ */
    public boolean isThrowUnavailableOnStartupException() {
        return _throwUnavailableOnStartupException;
    }

    /* ------------------------------------------------------------ */
    @Override
    protected void startContext()
            throws Exception {
        configure();

        //resolve the metadata
        _metadata.resolve(this);

        super.startContext();
    }

    /* ------------------------------------------------------------ */
    @Override
    public Set<String> setServletSecurity(Dynamic registration, ServletSecurityElement servletSecurityElement) {
        Set<String> unchangedURLMappings = new HashSet<String>();
        //From javadoc for ServletSecurityElement:
        /*
        If a URL pattern of this ServletRegistration is an exact target of a security-constraint that 
        was established via the portable deployment descriptor, then this method does not change the 
        security-constraint for that pattern, and the pattern will be included in the return value.

        If a URL pattern of this ServletRegistration is an exact target of a security constraint 
        that was established via the ServletSecurity annotation or a previous call to this method, 
        then this method replaces the security constraint for that pattern.

        If a URL pattern of this ServletRegistration is neither the exact target of a security constraint 
        that was established via the ServletSecurity annotation or a previous call to this method, 
        nor the exact target of a security-constraint in the portable deployment descriptor, then 
        this method establishes the security constraint for that pattern from the argument ServletSecurityElement. 
         */

        Collection<String> pathMappings = registration.getMappings();
        if (pathMappings != null) {
            ConstraintSecurityHandler.createConstraint(registration.getName(), servletSecurityElement);

            for (String pathSpec : pathMappings) {
                Origin origin = getMetaData().getOrigin("constraint.url." + pathSpec);

                switch (origin) {
                    case NotSet: {
                        //No mapping for this url already established
                        List<ConstraintMapping> mappings = ConstraintSecurityHandler.createConstraintsWithMappingsForPath(registration.getName(), pathSpec, servletSecurityElement);
                        for (ConstraintMapping m : mappings)
                            ((ConstraintAware) getSecurityHandler()).addConstraintMapping(m);
                        ((ConstraintAware) getSecurityHandler()).checkPathsWithUncoveredHttpMethods();
                        getMetaData().setOrigin("constraint.url." + pathSpec, Origin.API);
                        break;
                    }
                    case WebXml:
                    case WebDefaults:
                    case WebOverride:
                    case WebFragment: {
                        //a mapping for this url was created in a descriptor, which overrides everything
                        unchangedURLMappings.add(pathSpec);
                        break;
                    }
                    case Annotation:
                    case API: {
                        //mapping established via an annotation or by previous call to this method,
                        //replace the security constraint for this pattern
                        List<ConstraintMapping> constraintMappings = ConstraintSecurityHandler.removeConstraintMappingsForPath(pathSpec, ((ConstraintAware) getSecurityHandler()).getConstraintMappings());

                        List<ConstraintMapping> freshMappings = ConstraintSecurityHandler.createConstraintsWithMappingsForPath(registration.getName(), pathSpec, servletSecurityElement);
                        constraintMappings.addAll(freshMappings);

                        ((ConstraintSecurityHandler) getSecurityHandler()).setConstraintMappings(constraintMappings);
                        ((ConstraintAware) getSecurityHandler()).checkPathsWithUncoveredHttpMethods();
                        break;
                    }
                }
            }
        }

        return unchangedURLMappings;
    }


    /* ------------------------------------------------------------ */
    public class Context extends ServletContextHandler.Context {

        /* ------------------------------------------------------------ */
        @Override
        public void checkListener(Class<? extends EventListener> listener) throws IllegalStateException {
            try {
                super.checkListener(listener);
            } catch (IllegalArgumentException e) {
                //not one of the standard servlet listeners, check our extended session listener types
                boolean ok = false;
                for (Class l : SessionHandler.SESSION_LISTENER_TYPES) {
                    if (l.isAssignableFrom(listener)) {
                        ok = true;
                        break;
                    }
                }
                if (!ok)
                    throw new IllegalArgumentException("Inappropriate listener type " + listener.getName());
            }
        }

        /* ------------------------------------------------------------ */
        @Override
        public URL getResource(String path) throws MalformedURLException {
            Resource resource = WebAppContext.this.getResource(path);
            if (resource == null || !resource.exists())
                return null;

            // Should we go to the original war?
            if (resource.isDirectory() && resource instanceof ResourceCollection && !WebAppContext.this.isExtractWAR()) {
                Resource[] resources = ((ResourceCollection) resource).getResources();
                for (int i = resources.length; i-- > 0; ) {
                    if (resources[i].getName().startsWith("jar:file"))
                        return resources[i].getURL();
                }
            }

            return resource.getURL();
        }

        /* ------------------------------------------------------------ */
        @Override
        public ServletContext getContext(String uripath) {
            ServletContext servletContext = super.getContext(uripath);

            if (servletContext != null && _contextWhiteList != null) {
                for (String context : _contextWhiteList) {
                    if (context.equals(uripath)) {
                        return servletContext;
                    }
                }

                return null;
            } else {
                return servletContext;
            }
        }

    }

    /* ------------------------------------------------------------ */
    public MetaData getMetaData() {
        return _metadata;
    }
}
