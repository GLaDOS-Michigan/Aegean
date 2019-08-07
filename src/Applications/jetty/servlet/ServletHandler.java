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

package Applications.jetty.servlet;

import Applications.jetty.http.HttpHeader;
import Applications.jetty.http.HttpHeaderValue;
import Applications.jetty.http.PathMap;
import Applications.jetty.io.EofException;
import Applications.jetty.io.RuntimeIOException;
import Applications.jetty.security.IdentityService;
import Applications.jetty.security.SecurityHandler;
import Applications.jetty.server.*;
import Applications.jetty.server.handler.ContextHandler;
import Applications.jetty.server.handler.ScopedHandler;
import Applications.jetty.servlet.Holder.Source;
import Applications.jetty.util.*;
import Applications.jetty.util.annotation.ManagedAttribute;
import Applications.jetty.util.annotation.ManagedObject;
import Applications.jetty.util.component.LifeCycle;
import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;

import javax.servlet.*;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

/* --------------------------------------------------------------------- */

/**
 * Servlet HttpHandler.
 * This handler maps requests to servlets that implement the
 * javax.servlet.http.HttpServlet API.
 * <p>
 * This handler does not implement the full J2EE features and is intended to
 * be used directly when a full web application is not required.  If a Web application is required,
 * then this handler should be used as part of a <code>org.eclipse.jetty.webapp.WebAppContext</code>.
 * <p>
 * Unless run as part of a {@link ServletContextHandler} or derivative, the {@link #initialize()}
 * method must be called manually after start().
 */
@ManagedObject("Servlet Handler")
public class ServletHandler extends ScopedHandler {
    private static final Logger LOG = Log.getLogger(ServletHandler.class);

    /* ------------------------------------------------------------ */
    public static final String __DEFAULT_SERVLET = "default";

    /* ------------------------------------------------------------ */
    private ServletContextHandler _contextHandler;
    private ServletContext _servletContext;
    private FilterHolder[] _filters = new FilterHolder[0];
    private FilterMapping[] _filterMappings;
    private int _matchBeforeIndex = -1; //index of last programmatic FilterMapping with isMatchAfter=false
    private int _matchAfterIndex = -1;  //index of 1st programmatic FilterMapping with isMatchAfter=true
    private boolean _filterChainsCached = true;
    private int _maxFilterChainsCacheSize = 512;
    private boolean _startWithUnavailable = false;
    private IdentityService _identityService;

    private ServletHolder[] _servlets = new ServletHolder[0];
    private ServletMapping[] _servletMappings;
    private Map<String, ServletMapping> _servletPathMappings = new HashMap<String, ServletMapping>();

    private final Map<String, FilterHolder> _filterNameMap = new HashMap<>();
    private List<FilterMapping> _filterPathMappings;
    private MultiMap<FilterMapping> _filterNameMappings;

    private final Map<String, ServletHolder> _servletNameMap = new HashMap<>();
    private PathMap<ServletHolder> _servletPathMap;

    protected final ConcurrentMap<?, ?> _chainCache[] = new ConcurrentMap[FilterMapping.ALL];
    protected final Queue<?>[] _chainLRU = new Queue[FilterMapping.ALL];


    /* ------------------------------------------------------------ */

    /**
     * Constructor.
     */
    public ServletHandler() {
    }

    /* ----------------------------------------------------------------- */
    @Override
    protected synchronized void doStart()
            throws Exception {
        ContextHandler.Context context = ContextHandler.getCurrentContext();
        _servletContext = context == null ? new ContextHandler.NoContext() : context;
        _contextHandler = (ServletContextHandler) (context == null ? null : context.getContextHandler());

        if (_contextHandler != null) {
            SecurityHandler security_handler = _contextHandler.getChildHandlerByClass(SecurityHandler.class);
            if (security_handler != null)
                _identityService = security_handler.getIdentityService();
        }

        updateNameMappings();
        updateMappings();

        if (getServletMapping("/") == null) {
            LOG.debug("Adding Default404Servlet to {}", this);
            addServletWithMapping(Default404Servlet.class, "/");
            updateMappings();
            getServletMapping("/").setDefault(true);
        }

        if (_filterChainsCached) {
            _chainCache[FilterMapping.REQUEST] = new ConcurrentHashMap<String, FilterChain>();
            _chainCache[FilterMapping.FORWARD] = new ConcurrentHashMap<String, FilterChain>();
            _chainCache[FilterMapping.INCLUDE] = new ConcurrentHashMap<String, FilterChain>();
            _chainCache[FilterMapping.ERROR] = new ConcurrentHashMap<String, FilterChain>();
            _chainCache[FilterMapping.ASYNC] = new ConcurrentHashMap<String, FilterChain>();

            _chainLRU[FilterMapping.REQUEST] = new ConcurrentLinkedQueue<String>();
            _chainLRU[FilterMapping.FORWARD] = new ConcurrentLinkedQueue<String>();
            _chainLRU[FilterMapping.INCLUDE] = new ConcurrentLinkedQueue<String>();
            _chainLRU[FilterMapping.ERROR] = new ConcurrentLinkedQueue<String>();
            _chainLRU[FilterMapping.ASYNC] = new ConcurrentLinkedQueue<String>();
        }

        if (_contextHandler == null)
            initialize();

        super.doStart();
    }


    /* ----------------------------------------------------------------- */
    @Override
    protected void start(LifeCycle l) throws Exception {
        //Don't start the whole object tree (ie all the servlet and filter Holders) when
        //this handler starts. They have a slightly special lifecycle, and should only be
        //started AFTER the handlers have all started (and the ContextHandler has called
        //the context listeners).
        if (!(l instanceof Holder))
            super.start(l);
    }

    /* ----------------------------------------------------------------- */
    @Override
    protected synchronized void doStop()
            throws Exception {
        super.doStop();

        // Stop filters
        List<FilterHolder> filterHolders = new ArrayList<FilterHolder>();
        List<FilterMapping> filterMappings = ArrayUtil.asMutableList(_filterMappings);
        if (_filters != null) {
            for (int i = _filters.length; i-- > 0; ) {
                try {
                    _filters[i].stop();
                } catch (Exception e) {
                    LOG.warn(Log.EXCEPTION, e);
                }
                if (_filters[i].getSource() != Source.EMBEDDED) {
                    //remove all of the mappings that were for non-embedded filters
                    _filterNameMap.remove(_filters[i].getName());
                    //remove any mappings associated with this filter
                    ListIterator<FilterMapping> fmitor = filterMappings.listIterator();
                    while (fmitor.hasNext()) {
                        FilterMapping fm = fmitor.next();
                        if (fm.getFilterName().equals(_filters[i].getName()))
                            fmitor.remove();
                    }
                } else
                    filterHolders.add(_filters[i]); //only retain embedded
            }
        }

        //Retain only filters and mappings that were added using jetty api (ie Source.EMBEDDED)
        FilterHolder[] fhs = (FilterHolder[]) LazyList.toArray(filterHolders, FilterHolder.class);
        updateBeans(_filters, fhs);
        _filters = fhs;
        FilterMapping[] fms = (FilterMapping[]) LazyList.toArray(filterMappings, FilterMapping.class);
        updateBeans(_filterMappings, fms);
        _filterMappings = fms;

        _matchAfterIndex = (_filterMappings == null || _filterMappings.length == 0 ? -1 : _filterMappings.length - 1);
        _matchBeforeIndex = -1;

        // Stop servlets
        List<ServletHolder> servletHolders = new ArrayList<ServletHolder>();  //will be remaining servlets
        List<ServletMapping> servletMappings = ArrayUtil.asMutableList(_servletMappings); //will be remaining mappings
        if (_servlets != null) {
            for (int i = _servlets.length; i-- > 0; ) {
                try {
                    _servlets[i].stop();
                } catch (Exception e) {
                    LOG.warn(Log.EXCEPTION, e);
                }

                if (_servlets[i].getSource() != Source.EMBEDDED) {
                    //remove from servlet name map
                    _servletNameMap.remove(_servlets[i].getName());
                    //remove any mappings associated with this servlet
                    ListIterator<ServletMapping> smitor = servletMappings.listIterator();
                    while (smitor.hasNext()) {
                        ServletMapping sm = smitor.next();
                        if (sm.getServletName().equals(_servlets[i].getName()))
                            smitor.remove();
                    }
                } else
                    servletHolders.add(_servlets[i]); //only retain embedded 
            }
        }

        //Retain only Servlets and mappings added via jetty apis (ie Source.EMBEDDED)
        ServletHolder[] shs = (ServletHolder[]) LazyList.toArray(servletHolders, ServletHolder.class);
        updateBeans(_servlets, shs);
        _servlets = shs;
        ServletMapping[] sms = (ServletMapping[]) LazyList.toArray(servletMappings, ServletMapping.class);
        updateBeans(_servletMappings, sms);
        _servletMappings = sms;

        //will be regenerated on next start
        _filterPathMappings = null;
        _filterNameMappings = null;
        _servletPathMap = null;
        _servletPathMappings = null;
    }

    /* ------------------------------------------------------------ */
    IdentityService getIdentityService() {
        return _identityService;
    }

    /* ------------------------------------------------------------ */

    /**
     * @return Returns the contextLog.
     */
    public Object getContextLog() {
        return null;
    }

    /* ------------------------------------------------------------ */

    /**
     * @return Returns the filterMappings.
     */
    @ManagedAttribute(value = "filters", readonly = true)
    public FilterMapping[] getFilterMappings() {
        return _filterMappings;
    }

    /* ------------------------------------------------------------ */

    /**
     * Get Filters.
     *
     * @return Array of defined servlets
     */
    @ManagedAttribute(value = "filters", readonly = true)
    public FilterHolder[] getFilters() {
        return _filters;
    }

    /* ------------------------------------------------------------ */

    /**
     * ServletHolder matching path.
     *
     * @param pathInContext Path within _context.
     * @return PathMap Entries pathspec to ServletHolder
     */
    public PathMap.MappedEntry<ServletHolder> getHolderEntry(String pathInContext) {
        if (_servletPathMap == null)
            return null;
        return _servletPathMap.getMatch(pathInContext);
    }

    /* ------------------------------------------------------------ */
    public ServletContext getServletContext() {
        return _servletContext;
    }

    /* ------------------------------------------------------------ */

    /**
     * @return Returns the servletMappings.
     */
    @ManagedAttribute(value = "mappings of servlets", readonly = true)
    public ServletMapping[] getServletMappings() {
        return _servletMappings;
    }

   
    
    /* ------------------------------------------------------------ */

    /**
     * Get the ServletMapping matching the path
     *
     * @param pathSpec
     * @return
     */
    public ServletMapping getServletMapping(String pathSpec) {
        if (pathSpec == null || _servletPathMappings == null)
            return null;

        return _servletPathMappings.get(pathSpec);
    }
    

 
    
    /* ------------------------------------------------------------ */

    /**
     * Get Servlets.
     *
     * @return Array of defined servlets
     */
    @ManagedAttribute(value = "servlets", readonly = true)
    public ServletHolder[] getServlets() {
        return _servlets;
    }

    /* ------------------------------------------------------------ */
    public ServletHolder getServlet(String name) {
        return _servletNameMap.get(name);
    }

    /* ------------------------------------------------------------ */
    @Override
    public void doScope(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        // Get the base requests
        final String old_servlet_path = baseRequest.getServletPath();
        final String old_path_info = baseRequest.getPathInfo();

        DispatcherType type = baseRequest.getDispatcherType();

        ServletHolder servlet_holder = null;
        UserIdentity.Scope old_scope = null;

        // find the servlet
        if (target.startsWith("/")) {
            // Look for the servlet by path
            PathMap.MappedEntry<ServletHolder> entry = getHolderEntry(target);
            if (entry != null) {
                servlet_holder = entry.getValue();

                String servlet_path_spec = entry.getKey();
                String servlet_path = entry.getMapped() != null ? entry.getMapped() : PathMap.pathMatch(servlet_path_spec, target);
                String path_info = PathMap.pathInfo(servlet_path_spec, target);

                if (DispatcherType.INCLUDE.equals(type)) {
                    baseRequest.setAttribute(RequestDispatcher.INCLUDE_SERVLET_PATH, servlet_path);
                    baseRequest.setAttribute(RequestDispatcher.INCLUDE_PATH_INFO, path_info);
                } else {
                    baseRequest.setServletPath(servlet_path);
                    baseRequest.setPathInfo(path_info);
                }
            }
        } else {
            // look for a servlet by name!
            servlet_holder = _servletNameMap.get(target);
        }

        if (LOG.isDebugEnabled())
            LOG.debug("servlet {}|{}|{} -> {}", baseRequest.getContextPath(), baseRequest.getServletPath(), baseRequest.getPathInfo(), servlet_holder);

        try {
            // Do the filter/handling thang
            old_scope = baseRequest.getUserIdentityScope();
            baseRequest.setUserIdentityScope(servlet_holder);

            // start manual inline of nextScope(target,baseRequest,request,response);
            if (never())
                nextScope(target, baseRequest, request, response);
            else if (_nextScope != null)
                _nextScope.doScope(target, baseRequest, request, response);
            else if (_outerScope != null)
                _outerScope.doHandle(target, baseRequest, request, response);
            else
                doHandle(target, baseRequest, request, response);
            // end manual inline (pathentic attempt to reduce stack depth)
        } finally {
            if (old_scope != null)
                baseRequest.setUserIdentityScope(old_scope);

            if (!(DispatcherType.INCLUDE.equals(type))) {
                baseRequest.setServletPath(old_servlet_path);
                baseRequest.setPathInfo(old_path_info);
            }
        }
    }

    /* ------------------------------------------------------------ */
    /*
     * @see org.eclipse.jetty.server.Handler#handle(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, int)
     */
    @Override
    public void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        DispatcherType type = baseRequest.getDispatcherType();

        ServletHolder servlet_holder = (ServletHolder) baseRequest.getUserIdentityScope();
        FilterChain chain = null;

        // find the servlet
        if (target.startsWith("/")) {
            if (servlet_holder != null && _filterMappings != null && _filterMappings.length > 0)
                chain = getFilterChain(baseRequest, target, servlet_holder);
        } else {
            if (servlet_holder != null) {
                if (_filterMappings != null && _filterMappings.length > 0) {
                    chain = getFilterChain(baseRequest, null, servlet_holder);
                }
            }
        }

        LOG.debug("chain={}", chain);
        Throwable th = null;
        try {
            if (servlet_holder == null) {
                if (getHandler() == null)
                    notFound(request, response);
                else
                    nextHandle(target, baseRequest, request, response);
            } else {
                // unwrap any tunnelling of base Servlet request/responses
                ServletRequest req = request;
                if (req instanceof ServletRequestHttpWrapper)
                    req = ((ServletRequestHttpWrapper) req).getRequest();
                ServletResponse res = response;
                if (res instanceof ServletResponseHttpWrapper)
                    res = ((ServletResponseHttpWrapper) res).getResponse();

                // Do the filter/handling thang
                if (chain != null)
                    chain.doFilter(req, res);
                else
                    servlet_holder.handle(baseRequest, req, res);
            }
        } catch (EofException e) {
            throw e;
        } catch (RuntimeIOException e) {
            throw e;
        } catch (Exception e) {
            if (!(DispatcherType.REQUEST.equals(type) || DispatcherType.ASYNC.equals(type))) {
                if (e instanceof IOException)
                    throw (IOException) e;
                if (e instanceof RuntimeException)
                    throw (RuntimeException) e;
                if (e instanceof ServletException)
                    throw (ServletException) e;
            }

            // unwrap cause
            th = e;
            if (th instanceof ServletException) {
                if (th instanceof QuietServletException) {
                    LOG.warn(th.toString());
                    LOG.debug(th);
                } else
                    LOG.warn(th);
            } else if (th instanceof EofException) {
                throw (EofException) th;
            } else {
                LOG.warn(request.getRequestURI(), th);
                if (LOG.isDebugEnabled())
                    LOG.debug(request.toString());
            }

            request.setAttribute(RequestDispatcher.ERROR_EXCEPTION_TYPE, th.getClass());
            request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, th);
            if (!response.isCommitted()) {
                baseRequest.getResponse().getHttpFields().put(HttpHeader.CONNECTION, HttpHeaderValue.CLOSE);
                if (th instanceof UnavailableException) {
                    UnavailableException ue = (UnavailableException) th;
                    if (ue.isPermanent())
                        response.sendError(HttpServletResponse.SC_NOT_FOUND);
                    else
                        response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                } else
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            } else
                LOG.debug("Response already committed", th);
        } catch (Error e) {
            if ("ContinuationThrowable".equals(e.getClass().getSimpleName()))
                throw e;
            th = e;
            if (!(DispatcherType.REQUEST.equals(type) || DispatcherType.ASYNC.equals(type)))
                throw e;
            LOG.warn("Error for " + request.getRequestURI(), e);
            if (LOG.isDebugEnabled())
                LOG.debug(request.toString());

            request.setAttribute(RequestDispatcher.ERROR_EXCEPTION_TYPE, e.getClass());
            request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, e);
            if (!response.isCommitted()) {
                baseRequest.getResponse().getHttpFields().put(HttpHeader.CONNECTION, HttpHeaderValue.CLOSE);
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            } else
                LOG.debug("Response already committed for handling ", e);
        } finally {
            // Complete async errored requests 
            if (th != null && request.isAsyncStarted())
                baseRequest.getHttpChannelState().errorComplete();

            if (servlet_holder != null)
                baseRequest.setHandled(true);
        }
    }

    /* ------------------------------------------------------------ */
    private FilterChain getFilterChain(Request baseRequest, String pathInContext, ServletHolder servletHolder) {
        String key = pathInContext == null ? servletHolder.getName() : pathInContext;
        int dispatch = FilterMapping.dispatch(baseRequest.getDispatcherType());

        if (_filterChainsCached && _chainCache != null) {
            FilterChain chain = (FilterChain) _chainCache[dispatch].get(key);
            if (chain != null)
                return chain;
        }

        // Build list of filters (list of FilterHolder objects)
        List<FilterHolder> filters = new ArrayList<>();

        // Path filters
        if (pathInContext != null && _filterPathMappings != null) {
            for (FilterMapping filterPathMapping : _filterPathMappings) {
                if (filterPathMapping.appliesTo(pathInContext, dispatch))
                    filters.add(filterPathMapping.getFilterHolder());
            }
        }

        // Servlet name filters
        if (servletHolder != null && _filterNameMappings != null && _filterNameMappings.size() > 0) {
            // Servlet name filters
            if (_filterNameMappings.size() > 0) {
                Object o = _filterNameMappings.get(servletHolder.getName());

                for (int i = 0; i < LazyList.size(o); i++) {
                    FilterMapping mapping = (FilterMapping) LazyList.get(o, i);
                    if (mapping.appliesTo(dispatch))
                        filters.add(mapping.getFilterHolder());
                }

                o = _filterNameMappings.get("*");
                for (int i = 0; i < LazyList.size(o); i++) {
                    FilterMapping mapping = (FilterMapping) LazyList.get(o, i);
                    if (mapping.appliesTo(dispatch))
                        filters.add(mapping.getFilterHolder());
                }
            }
        }

        if (filters.isEmpty())
            return null;


        FilterChain chain = null;
        if (_filterChainsCached) {
            if (filters.size() > 0)
                chain = new CachedChain(filters, servletHolder);

            final Map<String, FilterChain> cache = (Map<String, FilterChain>) _chainCache[dispatch];
            final Queue<String> lru = (Queue<String>) _chainLRU[dispatch];

            // Do we have too many cached chains?
            while (_maxFilterChainsCacheSize > 0 && cache.size() >= _maxFilterChainsCacheSize) {
                // The LRU list is not atomic with the cache map, so be prepared to invalidate if
                // a key is not found to delete.
                // Delete by LRU (where U==created)
                String k = lru.poll();
                if (k == null) {
                    cache.clear();
                    break;
                }
                cache.remove(k);
            }

            cache.put(key, chain);
            lru.add(key);
        } else if (filters.size() > 0)
            chain = new Chain(baseRequest, filters, servletHolder);

        return chain;
    }

    /* ------------------------------------------------------------ */
    private void invalidateChainsCache() {
        if (_chainLRU[FilterMapping.REQUEST] != null) {
            _chainLRU[FilterMapping.REQUEST].clear();
            _chainLRU[FilterMapping.FORWARD].clear();
            _chainLRU[FilterMapping.INCLUDE].clear();
            _chainLRU[FilterMapping.ERROR].clear();
            _chainLRU[FilterMapping.ASYNC].clear();

            _chainCache[FilterMapping.REQUEST].clear();
            _chainCache[FilterMapping.FORWARD].clear();
            _chainCache[FilterMapping.INCLUDE].clear();
            _chainCache[FilterMapping.ERROR].clear();
            _chainCache[FilterMapping.ASYNC].clear();
        }
    }

    /* ------------------------------------------------------------ */

    /**
     * @return true if the handler is started and there are no unavailable servlets
     */
    public boolean isAvailable() {
        if (!isStarted())
            return false;
        ServletHolder[] holders = getServlets();
        for (ServletHolder holder : holders) {
            if (holder != null && !holder.isAvailable())
                return false;
        }
        return true;
    }

    /* ------------------------------------------------------------ */

    /**
     * @param start True if this handler will start with unavailable servlets
     */
    public void setStartWithUnavailable(boolean start) {
        _startWithUnavailable = start;
    }

    /* ------------------------------------------------------------ */

    /**
     * @return True if this handler will start with unavailable servlets
     */
    public boolean isStartWithUnavailable() {
        return _startWithUnavailable;
    }



    /* ------------------------------------------------------------ */

    /**
     * Initialize filters and load-on-startup servlets.
     * Called automatically from start if autoInitializeServlet is true.
     */
    public void initialize()
            throws Exception {
        MultiException mx = new MultiException();

        if (_servlets != null) {
            // Sort and Initialize servlets
            ServletHolder[] servlets = _servlets.clone();
            Arrays.sort(servlets);
            for (ServletHolder servlet : servlets) {
                try {
                    if (servlet.getClassName() == null && servlet.getForcedPath() != null) {
                        ServletHolder forced_holder = _servletPathMap.match(servlet.getForcedPath());
                        if (forced_holder == null || forced_holder.getClassName() == null) {
                            mx.add(new IllegalStateException("No forced path servlet for " + servlet.getForcedPath()));
                            continue;
                        }
                        servlet.setClassName(forced_holder.getClassName());
                    }
                } catch (Throwable e) {
                    LOG.debug(Log.EXCEPTION, e);
                    mx.add(e);
                }
            }
        }

        //start the servlet and filter holders now
        for (Holder<?> h : getBeans(Holder.class)) {
            try {
                h.start();
                h.initialize();
            } catch (Exception e) {
                mx.add(e);
            }
        }

        mx.ifExceptionThrow();
    }

    /* ------------------------------------------------------------ */

    /**
     * @return Returns the filterChainsCached.
     */
    public boolean isFilterChainsCached() {
        return _filterChainsCached;
    }

    /* ------------------------------------------------------------ */

    /**
     * see also newServletHolder(Class)
     */
    public ServletHolder newServletHolder(Holder.Source source) {
        return new ServletHolder(source);
    }

    /* ------------------------------------------------------------ */
    /** Convenience method to add a servlet Holder.
     public ServletHolder newServletHolder(Class<? extends Servlet> servlet)
     {
     return new ServletHolder(servlet);
     }

     /* ------------------------------------------------------------ */
    /**
     * Convenience method to add a servlet.
     *
     * @return The servlet holder.
     */
    public ServletHolder addServletWithMapping(String className, String pathSpec) {
        ServletHolder holder = newServletHolder(Holder.Source.EMBEDDED);
        holder.setClassName(className);
        addServletWithMapping(holder, pathSpec);
        return holder;
    }

    /* ------------------------------------------------------------ */

    /**
     * conveniance method to add a servlet.
     *
     * @return The servlet holder.
     */
    public ServletHolder addServletWithMapping(Class<? extends Servlet> servlet, String pathSpec) {
        ServletHolder holder = newServletHolder(Holder.Source.EMBEDDED);
        holder.setHeldClass(servlet);
        addServletWithMapping(holder, pathSpec);

        return holder;
    }

    /* ------------------------------------------------------------ */

    /**
     * conveniance method to add a servlet.
     *
     * @param servlet  servlet holder to add
     * @param pathSpec servlet mappings for the servletHolder
     */
    public void addServletWithMapping(ServletHolder servlet, String pathSpec) {
        ServletHolder[] holders = getServlets();
        if (holders != null)
            holders = holders.clone();

        try {
            setServlets(ArrayUtil.addToArray(holders, servlet, ServletHolder.class));

            ServletMapping mapping = new ServletMapping();
            mapping.setServletName(servlet.getName());
            mapping.setPathSpec(pathSpec);
            setServletMappings(ArrayUtil.addToArray(getServletMappings(), mapping, ServletMapping.class));
        } catch (Exception e) {
            setServlets(holders);
            if (e instanceof RuntimeException)
                throw (RuntimeException) e;
            throw new RuntimeException(e);
        }
    }


    /* ------------------------------------------------------------ */

    /**
     * Convenience method to add a pre-constructed ServletHolder.
     *
     * @param holder
     */
    public void addServlet(ServletHolder holder) {
        setServlets(ArrayUtil.addToArray(getServlets(), holder, ServletHolder.class));
    }

    /* ------------------------------------------------------------ */

    /**
     * Convenience method to add a pre-constructed ServletMapping.
     *
     * @param mapping
     */
    public void addServletMapping(ServletMapping mapping) {
        setServletMappings(ArrayUtil.addToArray(getServletMappings(), mapping, ServletMapping.class));
    }

    /* ------------------------------------------------------------ */
    public Set<String> setServletSecurity(ServletRegistration.Dynamic registration, ServletSecurityElement servletSecurityElement) {
        if (_contextHandler != null) {
            return _contextHandler.setServletSecurity(registration, servletSecurityElement);
        }
        return Collections.emptySet();
    }

    /* ------------------------------------------------------------ */
    public FilterHolder newFilterHolder(Holder.Source source) {
        return new FilterHolder(source);
    }

    /* ------------------------------------------------------------ */
    public FilterHolder getFilter(String name) {
        return _filterNameMap.get(name);
    }


    /* ------------------------------------------------------------ */

    /**
     * Convenience method to add a filter.
     *
     * @param filter     class of filter to create
     * @param pathSpec   filter mappings for filter
     * @param dispatches see {@link FilterMapping#setDispatches(int)}
     * @return The filter holder.
     */
    public FilterHolder addFilterWithMapping(Class<? extends Filter> filter, String pathSpec, EnumSet<DispatcherType> dispatches) {
        FilterHolder holder = newFilterHolder(Holder.Source.EMBEDDED);
        holder.setHeldClass(filter);
        addFilterWithMapping(holder, pathSpec, dispatches);

        return holder;
    }

    /* ------------------------------------------------------------ */

    /**
     * Convenience method to add a filter.
     *
     * @param className  of filter
     * @param pathSpec   filter mappings for filter
     * @param dispatches see {@link FilterMapping#setDispatches(int)}
     * @return The filter holder.
     */
    public FilterHolder addFilterWithMapping(String className, String pathSpec, EnumSet<DispatcherType> dispatches) {
        FilterHolder holder = newFilterHolder(Holder.Source.EMBEDDED);
        holder.setClassName(className);

        addFilterWithMapping(holder, pathSpec, dispatches);
        return holder;
    }

    /* ------------------------------------------------------------ */

    /**
     * Convenience method to add a filter.
     *
     * @param holder     filter holder to add
     * @param pathSpec   filter mappings for filter
     * @param dispatches see {@link FilterMapping#setDispatches(int)}
     */
    public void addFilterWithMapping(FilterHolder holder, String pathSpec, EnumSet<DispatcherType> dispatches) {
        FilterHolder[] holders = getFilters();
        if (holders != null)
            holders = holders.clone();

        try {
            setFilters(ArrayUtil.addToArray(holders, holder, FilterHolder.class));

            FilterMapping mapping = new FilterMapping();
            mapping.setFilterName(holder.getName());
            mapping.setPathSpec(pathSpec);
            mapping.setDispatcherTypes(dispatches);
            addFilterMapping(mapping);

        } catch (RuntimeException e) {
            setFilters(holders);
            throw e;
        } catch (Error e) {
            setFilters(holders);
            throw e;
        }

    }

    /* ------------------------------------------------------------ */

    /**
     * Convenience method to add a filter.
     *
     * @param filter     class of filter to create
     * @param pathSpec   filter mappings for filter
     * @param dispatches see {@link FilterMapping#setDispatches(int)}
     * @return The filter holder.
     */
    public FilterHolder addFilterWithMapping(Class<? extends Filter> filter, String pathSpec, int dispatches) {
        FilterHolder holder = newFilterHolder(Holder.Source.EMBEDDED);
        holder.setHeldClass(filter);
        addFilterWithMapping(holder, pathSpec, dispatches);

        return holder;
    }

    /* ------------------------------------------------------------ */

    /**
     * Convenience method to add a filter.
     *
     * @param className  of filter
     * @param pathSpec   filter mappings for filter
     * @param dispatches see {@link FilterMapping#setDispatches(int)}
     * @return The filter holder.
     */
    public FilterHolder addFilterWithMapping(String className, String pathSpec, int dispatches) {
        FilterHolder holder = newFilterHolder(Holder.Source.EMBEDDED);
        holder.setClassName(className);

        addFilterWithMapping(holder, pathSpec, dispatches);
        return holder;
    }

    /* ------------------------------------------------------------ */

    /**
     * Convenience method to add a filter.
     *
     * @param holder     filter holder to add
     * @param pathSpec   filter mappings for filter
     * @param dispatches see {@link FilterMapping#setDispatches(int)}
     */
    public void addFilterWithMapping(FilterHolder holder, String pathSpec, int dispatches) {
        FilterHolder[] holders = getFilters();
        if (holders != null)
            holders = holders.clone();

        try {
            setFilters(ArrayUtil.addToArray(holders, holder, FilterHolder.class));

            FilterMapping mapping = new FilterMapping();
            mapping.setFilterName(holder.getName());
            mapping.setPathSpec(pathSpec);
            mapping.setDispatches(dispatches);
            addFilterMapping(mapping);
        } catch (RuntimeException e) {
            setFilters(holders);
            throw e;
        } catch (Error e) {
            setFilters(holders);
            throw e;
        }

    }

    /* ------------------------------------------------------------ */

    /**
     * Convenience method to add a filter with a mapping
     *
     * @param className
     * @param pathSpec
     * @param dispatches
     * @return the filter holder created
     * @deprecated use {@link #addFilterWithMapping(Class, String, EnumSet)} instead
     */
    public FilterHolder addFilter(String className, String pathSpec, EnumSet<DispatcherType> dispatches) {
        return addFilterWithMapping(className, pathSpec, dispatches);
    }

    /* ------------------------------------------------------------ */

    /**
     * convenience method to add a filter and mapping
     *
     * @param filter
     * @param filterMapping
     */
    public void addFilter(FilterHolder filter, FilterMapping filterMapping) {
        if (filter != null)
            setFilters(ArrayUtil.addToArray(getFilters(), filter, FilterHolder.class));
        if (filterMapping != null)
            addFilterMapping(filterMapping);
    }

    /* ------------------------------------------------------------ */

    /**
     * Convenience method to add a preconstructed FilterHolder
     *
     * @param filter
     */
    public void addFilter(FilterHolder filter) {
        if (filter != null)
            setFilters(ArrayUtil.addToArray(getFilters(), filter, FilterHolder.class));
    }

    /* ------------------------------------------------------------ */

    /**
     * Convenience method to add a preconstructed FilterMapping
     *
     * @param mapping
     */
    public void addFilterMapping(FilterMapping mapping) {
        if (mapping != null) {
            Source source = (mapping.getFilterHolder() == null ? null : mapping.getFilterHolder().getSource());
            FilterMapping[] mappings = getFilterMappings();
            if (mappings == null || mappings.length == 0) {
                setFilterMappings(insertFilterMapping(mapping, 0, false));
                if (source != null && source == Source.JAVAX_API)
                    _matchAfterIndex = 0;
            } else {
                //there are existing entries. If this is a programmatic filtermapping, it is added at the end of the list.
                //If this is a normal filtermapping, it is inserted after all the other filtermappings (matchBefores and normals), 
                //but before the first matchAfter filtermapping.
                if (source != null && Source.JAVAX_API == source) {
                    setFilterMappings(insertFilterMapping(mapping, mappings.length - 1, false));
                    if (_matchAfterIndex < 0)
                        _matchAfterIndex = getFilterMappings().length - 1;
                } else {
                    //insert non-programmatic filter mappings before any matchAfters, if any
                    if (_matchAfterIndex < 0)
                        setFilterMappings(insertFilterMapping(mapping, mappings.length - 1, false));
                    else {
                        FilterMapping[] new_mappings = insertFilterMapping(mapping, _matchAfterIndex, true);
                        ++_matchAfterIndex;
                        setFilterMappings(new_mappings);
                    }
                }
            }
        }
    }
    

    /* ------------------------------------------------------------ */

    /**
     * Convenience method to add a preconstructed FilterMapping
     *
     * @param mapping
     */
    public void prependFilterMapping(FilterMapping mapping) {
        if (mapping != null) {
            Source source = mapping.getFilterHolder().getSource();

            FilterMapping[] mappings = getFilterMappings();
            if (mappings == null || mappings.length == 0) {
                setFilterMappings(insertFilterMapping(mapping, 0, false));
                if (source != null && Source.JAVAX_API == source)
                    _matchBeforeIndex = 0;
            } else {
                if (source != null && Source.JAVAX_API == source) {
                    //programmatically defined filter mappings are prepended to mapping list in the order
                    //in which they were defined. In other words, insert this mapping at the tail of the 
                    //programmatically prepended filter mappings, BEFORE the first web.xml defined filter mapping.

                    if (_matchBeforeIndex < 0) {
                        //no programmatically defined prepended filter mappings yet, prepend this one
                        _matchBeforeIndex = 0;
                        FilterMapping[] new_mappings = insertFilterMapping(mapping, 0, true);
                        setFilterMappings(new_mappings);
                    } else {
                        FilterMapping[] new_mappings = insertFilterMapping(mapping, _matchBeforeIndex, false);
                        ++_matchBeforeIndex;
                        setFilterMappings(new_mappings);
                    }
                } else {
                    //non programmatically defined, just prepend to list
                    FilterMapping[] new_mappings = insertFilterMapping(mapping, 0, true);
                    setFilterMappings(new_mappings);
                }

                //adjust matchAfterIndex ptr to take account of the mapping we just prepended
                if (_matchAfterIndex >= 0)
                    ++_matchAfterIndex;
            }
        }
    }


    /**
     * Insert a filtermapping in the list
     *
     * @param mapping the FilterMapping to add
     * @param pos     the position in the existing arry at which to add it
     * @param before  if true, insert before  pos, if false insert after it
     * @return
     */
    protected FilterMapping[] insertFilterMapping(FilterMapping mapping, int pos, boolean before) {
        if (pos < 0)
            throw new IllegalArgumentException("FilterMapping insertion pos < 0");
        FilterMapping[] mappings = getFilterMappings();

        if (mappings == null || mappings.length == 0) {
            return new FilterMapping[]{mapping};
        }
        FilterMapping[] new_mappings = new FilterMapping[mappings.length + 1];


        if (before) {
            //copy existing filter mappings up to but not including the pos
            System.arraycopy(mappings, 0, new_mappings, 0, pos);

            //add in the new mapping
            new_mappings[pos] = mapping;

            //copy the old pos mapping and any remaining existing mappings
            System.arraycopy(mappings, pos, new_mappings, pos + 1, mappings.length - pos);

        } else {
            //copy existing filter mappings up to and including the pos
            System.arraycopy(mappings, 0, new_mappings, 0, pos + 1);
            //add in the new mapping after the pos
            new_mappings[pos + 1] = mapping;

            //copy the remaining existing mappings
            if (mappings.length > pos + 1)
                System.arraycopy(mappings, pos + 1, new_mappings, pos + 2, mappings.length - (pos + 1));
        }
        return new_mappings;
    }


    /* ------------------------------------------------------------ */
    protected synchronized void updateNameMappings() {
        // update filter name map
        _filterNameMap.clear();
        if (_filters != null) {
            for (FilterHolder filter : _filters) {
                _filterNameMap.put(filter.getName(), filter);
                filter.setServletHandler(this);
            }
        }

        // Map servlet names to holders
        _servletNameMap.clear();
        if (_servlets != null) {
            // update the maps
            for (ServletHolder servlet : _servlets) {
                _servletNameMap.put(servlet.getName(), servlet);
                servlet.setServletHandler(this);
            }
        }
    }

    /* ------------------------------------------------------------ */
    protected synchronized void updateMappings() {
        // update filter mappings
        if (_filterMappings == null) {
            _filterPathMappings = null;
            _filterNameMappings = null;
        } else {
            _filterPathMappings = new ArrayList<>();
            _filterNameMappings = new MultiMap<FilterMapping>();
            for (FilterMapping filtermapping : _filterMappings) {
                FilterHolder filter_holder = _filterNameMap.get(filtermapping.getFilterName());
                if (filter_holder == null)
                    throw new IllegalStateException("No filter named " + filtermapping.getFilterName());
                filtermapping.setFilterHolder(filter_holder);
                if (filtermapping.getPathSpecs() != null)
                    _filterPathMappings.add(filtermapping);

                if (filtermapping.getServletNames() != null) {
                    String[] names = filtermapping.getServletNames();
                    for (String name : names) {
                        if (name != null)
                            _filterNameMappings.add(name, filtermapping);
                    }
                }
            }
        }

        // Map servlet paths to holders
        if (_servletMappings == null || _servletNameMap == null) {
            _servletPathMap = null;
        } else {
            PathMap<ServletHolder> pm = new PathMap<>();
            Map<String, ServletMapping> servletPathMappings = new HashMap<String, ServletMapping>();

            //create a map of paths to set of ServletMappings that define that mapping
            HashMap<String, Set<ServletMapping>> sms = new HashMap<String, Set<ServletMapping>>();
            for (ServletMapping servletMapping : _servletMappings) {
                String[] pathSpecs = servletMapping.getPathSpecs();
                if (pathSpecs != null) {
                    for (String pathSpec : pathSpecs) {
                        Set<ServletMapping> mappings = sms.get(pathSpec);
                        if (mappings == null) {
                            mappings = new HashSet<ServletMapping>();
                            sms.put(pathSpec, mappings);
                        }
                        mappings.add(servletMapping);
                    }
                }
            }

            //evaluate path to servlet map based on servlet mappings
            for (String pathSpec : sms.keySet()) {
                //for each path, look at the mappings where it is referenced
                //if a mapping is for a servlet that is not enabled, skip it
                Set<ServletMapping> mappings = sms.get(pathSpec);


                ServletMapping finalMapping = null;
                for (ServletMapping mapping : mappings) {
                    //Get servlet associated with the mapping and check it is enabled
                    ServletHolder servlet_holder = _servletNameMap.get(mapping.getServletName());
                    if (servlet_holder == null)
                        throw new IllegalStateException("No such servlet: " + mapping.getServletName());
                    //if the servlet related to the mapping is not enabled, skip it from consideration
                    if (!servlet_holder.isEnabled())
                        continue;

                    //only accept a default mapping if we don't have any other 
                    if (finalMapping == null)
                        finalMapping = mapping;
                    else {
                        //already have a candidate - only accept another one if the candidate is a default
                        if (finalMapping.isDefault())
                            finalMapping = mapping;
                        else {
                            //existing candidate isn't a default, if the one we're looking at isn't a default either, then its an error
                            if (!mapping.isDefault())
                                throw new IllegalStateException("Multiple servlets map to path: " + pathSpec);
                        }
                    }
                }
                if (finalMapping == null)
                    throw new IllegalStateException("No acceptable servlet mappings for " + pathSpec);

                if (LOG.isDebugEnabled())
                    LOG.debug("Chose path={} mapped to servlet={} from default={}", pathSpec, finalMapping.getServletName(), finalMapping.isDefault());

                servletPathMappings.put(pathSpec, finalMapping);
                pm.put(pathSpec, _servletNameMap.get(finalMapping.getServletName()));
            }

            _servletPathMap = pm;
            _servletPathMappings = servletPathMappings;
        }

        // flush filter chain cache
        if (_chainCache != null) {
            for (int i = _chainCache.length; i-- > 0; ) {
                if (_chainCache[i] != null)
                    _chainCache[i].clear();
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("filterNameMap=" + _filterNameMap);
            LOG.debug("pathFilters=" + _filterPathMappings);
            LOG.debug("servletFilterMap=" + _filterNameMappings);
            LOG.debug("servletPathMap=" + _servletPathMap);
            LOG.debug("servletNameMap=" + _servletNameMap);
        }

        try {
            if (_contextHandler != null && _contextHandler.isStarted() || _contextHandler == null && isStarted())
                initialize();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /* ------------------------------------------------------------ */
    protected void notFound(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (LOG.isDebugEnabled())
            LOG.debug("Not Found " + request.getRequestURI());
        //Override to send an error back, eg with: response.sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    /* ------------------------------------------------------------ */

    /**
     * @param filterChainsCached The filterChainsCached to set.
     */
    public void setFilterChainsCached(boolean filterChainsCached) {
        _filterChainsCached = filterChainsCached;
    }

    /* ------------------------------------------------------------ */

    /**
     * @param filterMappings The filterMappings to set.
     */
    public void setFilterMappings(FilterMapping[] filterMappings) {
        updateBeans(_filterMappings, filterMappings);
        _filterMappings = filterMappings;
        updateMappings();
        invalidateChainsCache();
    }

    /* ------------------------------------------------------------ */
    public synchronized void setFilters(FilterHolder[] holders) {
        if (holders != null)
            for (FilterHolder holder : holders)
                holder.setServletHandler(this);

        updateBeans(_filters, holders);
        _filters = holders;
        updateNameMappings();
        invalidateChainsCache();
    }

    /* ------------------------------------------------------------ */

    /**
     * @param servletMappings The servletMappings to set.
     */
    public void setServletMappings(ServletMapping[] servletMappings) {
        updateBeans(_servletMappings, servletMappings);
        _servletMappings = servletMappings;
        updateMappings();
        invalidateChainsCache();
    }

    /* ------------------------------------------------------------ */

    /**
     * Set Servlets.
     *
     * @param holders Array of servlets to define
     */
    public synchronized void setServlets(ServletHolder[] holders) {
        if (holders != null)
            for (ServletHolder holder : holders)
                holder.setServletHandler(this);

        updateBeans(_servlets, holders);
        _servlets = holders;
        updateNameMappings();
        invalidateChainsCache();
    }

    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    private class CachedChain implements FilterChain {
        FilterHolder _filterHolder;
        CachedChain _next;
        ServletHolder _servletHolder;

        /* ------------------------------------------------------------ */

        /**
         * @param filters       list of {@link FilterHolder} objects
         * @param servletHolder
         */
        CachedChain(List<FilterHolder> filters, ServletHolder servletHolder) {
            if (filters.size() > 0) {
                _filterHolder = filters.get(0);
                filters.remove(0);
                _next = new CachedChain(filters, servletHolder);
            } else
                _servletHolder = servletHolder;
        }

        /* ------------------------------------------------------------ */
        @Override
        public void doFilter(ServletRequest request, ServletResponse response)
                throws IOException, ServletException {
            final Request baseRequest = (request instanceof Request) ? ((Request) request) : HttpChannel.getCurrentHttpChannel().getRequest();

            // pass to next filter
            if (_filterHolder != null) {
                if (LOG.isDebugEnabled())
                    LOG.debug("call filter " + _filterHolder);
                Filter filter = _filterHolder.getFilter();
                if (_filterHolder.isAsyncSupported())
                    filter.doFilter(request, response, _next);
                else {
                    final boolean suspendable = baseRequest.isAsyncSupported();
                    if (suspendable) {
                        try {
                            baseRequest.setAsyncSupported(false);
                            filter.doFilter(request, response, _next);
                        } finally {
                            baseRequest.setAsyncSupported(true);
                        }
                    } else
                        filter.doFilter(request, response, _next);
                }
                return;
            }

            // Call servlet

            HttpServletRequest srequest = (HttpServletRequest) request;
            if (_servletHolder != null) {
                if (LOG.isDebugEnabled())
                    LOG.debug("call servlet " + _servletHolder);
                _servletHolder.handle(baseRequest, request, response);
            } else if (getHandler() == null)
                notFound(srequest, (HttpServletResponse) response);
            else
                nextHandle(URIUtil.addPaths(srequest.getServletPath(), srequest.getPathInfo()),
                        baseRequest, srequest, (HttpServletResponse) response);

        }

        @Override
        public String toString() {
            if (_filterHolder != null)
                return _filterHolder + "->" + _next.toString();
            if (_servletHolder != null)
                return _servletHolder.toString();
            return "null";
        }
    }

    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    private class Chain implements FilterChain {
        final Request _baseRequest;
        final List<FilterHolder> _chain;
        final ServletHolder _servletHolder;
        int _filter = 0;

        /* ------------------------------------------------------------ */
        Chain(Request baseRequest, List<FilterHolder> filters, ServletHolder servletHolder) {
            _baseRequest = baseRequest;
            _chain = filters;
            _servletHolder = servletHolder;
        }

        /* ------------------------------------------------------------ */
        @Override
        public void doFilter(ServletRequest request, ServletResponse response)
                throws IOException, ServletException {
            if (LOG.isDebugEnabled())
                LOG.debug("doFilter " + _filter);

            // pass to next filter
            if (_filter < _chain.size()) {
                FilterHolder holder = _chain.get(_filter++);
                if (LOG.isDebugEnabled())
                    LOG.debug("call filter " + holder);
                Filter filter = holder.getFilter();

                if (holder.isAsyncSupported() || !_baseRequest.isAsyncSupported()) {
                    filter.doFilter(request, response, this);
                } else {
                    try {
                        _baseRequest.setAsyncSupported(false);
                        filter.doFilter(request, response, this);
                    } finally {
                        _baseRequest.setAsyncSupported(true);
                    }
                }

                return;
            }

            // Call servlet
            HttpServletRequest srequest = (HttpServletRequest) request;
            if (_servletHolder != null) {
                if (LOG.isDebugEnabled())
                    LOG.debug("call servlet " + _servletHolder);
                _servletHolder.handle(_baseRequest, request, response);
            } else if (getHandler() == null)
                notFound(srequest, (HttpServletResponse) response);
            else {
                Request baseRequest = (request instanceof Request) ? ((Request) request) : HttpChannel.getCurrentHttpChannel().getRequest();
                nextHandle(URIUtil.addPaths(srequest.getServletPath(), srequest.getPathInfo()),
                        baseRequest, srequest, (HttpServletResponse) response);
            }
        }

        /* ------------------------------------------------------------ */
        @Override
        public String toString() {
            StringBuilder b = new StringBuilder();
            for (FilterHolder f : _chain) {
                b.append(f.toString());
                b.append("->");
            }
            b.append(_servletHolder);
            return b.toString();
        }
    }

    /* ------------------------------------------------------------ */

    /**
     * @return The maximum entries in a filter chain cache.
     */
    public int getMaxFilterChainsCacheSize() {
        return _maxFilterChainsCacheSize;
    }

    /* ------------------------------------------------------------ */

    /**
     * Set the maximum filter chain cache size.
     * Filter chains are cached if {@link #isFilterChainsCached()} is true. If the max cache size
     * is greater than zero, then the cache is flushed whenever it grows to be this size.
     *
     * @param maxFilterChainsCacheSize the maximum number of entries in a filter chain cache.
     */
    public void setMaxFilterChainsCacheSize(int maxFilterChainsCacheSize) {
        _maxFilterChainsCacheSize = maxFilterChainsCacheSize;
    }

    /* ------------------------------------------------------------ */
    void destroyServlet(Servlet servlet) {
        if (_contextHandler != null)
            _contextHandler.destroyServlet(servlet);
    }

    /* ------------------------------------------------------------ */
    void destroyFilter(Filter filter) {
        if (_contextHandler != null)
            _contextHandler.destroyFilter(filter);
    }

    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    public static class Default404Servlet extends HttpServlet {
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }
}
