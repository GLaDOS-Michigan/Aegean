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

import Applications.jetty.util.TypeUtil;
import Applications.jetty.util.component.Dumpable;
import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;

import javax.servlet.*;
import java.io.IOException;
import java.util.*;

/* --------------------------------------------------------------------- */

/**
 *
 */
public class FilterHolder extends Holder<Filter> {
    private static final Logger LOG = Log.getLogger(FilterHolder.class);

    /* ------------------------------------------------------------ */
    private transient Filter _filter;
    private transient Config _config;
    private transient FilterRegistration.Dynamic _registration;

    /* ---------------------------------------------------------------- */

    /**
     * Constructor
     */
    public FilterHolder() {
        this(Source.EMBEDDED);
    }


    /* ---------------------------------------------------------------- */

    /**
     * Constructor
     */
    public FilterHolder(Holder.Source source) {
        super(source);
    }

    /* ---------------------------------------------------------------- */

    /**
     * Constructor
     */
    public FilterHolder(Class<? extends Filter> filter) {
        this(Source.EMBEDDED);
        setHeldClass(filter);
    }

    /* ---------------------------------------------------------------- */

    /**
     * Constructor for existing filter.
     */
    public FilterHolder(Filter filter) {
        this(Source.EMBEDDED);
        setFilter(filter);
    }

    /* ------------------------------------------------------------ */
    @Override
    public void doStart()
            throws Exception {
        super.doStart();

        if (!javax.servlet.Filter.class
                .isAssignableFrom(_class)) {
            String msg = _class + " is not a javax.servlet.Filter";
            super.stop();
            throw new IllegalStateException(msg);
        }
    }


    /* ------------------------------------------------------------ */
    @Override
    public void initialize() throws Exception {
        super.initialize();

        if (_filter == null) {
            try {
                ServletContext context = _servletHandler.getServletContext();
                _filter = (context instanceof ServletContextHandler.Context)
                        ? ((ServletContextHandler.Context) context).createFilter(getHeldClass())
                        : getHeldClass().newInstance();
            } catch (ServletException se) {
                Throwable cause = se.getRootCause();
                if (cause instanceof InstantiationException)
                    throw (InstantiationException) cause;
                if (cause instanceof IllegalAccessException)
                    throw (IllegalAccessException) cause;
                throw se;
            }
        }

        _config = new Config();
        LOG.debug("Filter.init {}", _filter);
        _filter.init(_config);
    }


    /* ------------------------------------------------------------ */
    @Override
    public void doStop()
            throws Exception {
        if (_filter != null) {
            try {
                destroyInstance(_filter);
            } catch (Exception e) {
                LOG.warn(e);
            }
        }
        if (!_extInstance)
            _filter = null;

        _config = null;
        super.doStop();
    }

    /* ------------------------------------------------------------ */
    @Override
    public void destroyInstance(Object o)
            throws Exception {
        if (o == null)
            return;
        Filter f = (Filter) o;
        f.destroy();
        getServletHandler().destroyFilter(f);
    }

    /* ------------------------------------------------------------ */
    public synchronized void setFilter(Filter filter) {
        _filter = filter;
        _extInstance = true;
        setHeldClass(filter.getClass());
        if (getName() == null)
            setName(filter.getClass().getName());
    }

    /* ------------------------------------------------------------ */
    public Filter getFilter() {
        return _filter;
    }

    /* ------------------------------------------------------------ */
    @Override
    public String toString() {
        return getName();
    }

    /* ------------------------------------------------------------ */
    @Override
    public void dump(Appendable out, String indent) throws IOException {
        super.dump(out, indent);
        if (_filter instanceof Dumpable) {
            ((Dumpable) _filter).dump(out, indent);
        }
    }

    /* ------------------------------------------------------------ */
    public FilterRegistration.Dynamic getRegistration() {
        if (_registration == null)
            _registration = new Registration();
        return _registration;
    }

    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    protected class Registration extends HolderRegistration implements FilterRegistration.Dynamic {
        public void addMappingForServletNames(EnumSet<DispatcherType> dispatcherTypes, boolean isMatchAfter, String... servletNames) {
            illegalStateIfContextStarted();
            FilterMapping mapping = new FilterMapping();
            mapping.setFilterHolder(FilterHolder.this);
            mapping.setServletNames(servletNames);
            mapping.setDispatcherTypes(dispatcherTypes);
            if (isMatchAfter)
                _servletHandler.addFilterMapping(mapping);
            else
                _servletHandler.prependFilterMapping(mapping);
        }

        public void addMappingForUrlPatterns(EnumSet<DispatcherType> dispatcherTypes, boolean isMatchAfter, String... urlPatterns) {
            illegalStateIfContextStarted();
            FilterMapping mapping = new FilterMapping();
            mapping.setFilterHolder(FilterHolder.this);
            mapping.setPathSpecs(urlPatterns);
            mapping.setDispatcherTypes(dispatcherTypes);
            if (isMatchAfter)
                _servletHandler.addFilterMapping(mapping);
            else
                _servletHandler.prependFilterMapping(mapping);
        }

        public Collection<String> getServletNameMappings() {
            FilterMapping[] mappings = _servletHandler.getFilterMappings();
            List<String> names = new ArrayList<String>();
            for (FilterMapping mapping : mappings) {
                if (mapping.getFilterHolder() != FilterHolder.this)
                    continue;
                String[] servlets = mapping.getServletNames();
                if (servlets != null && servlets.length > 0)
                    names.addAll(Arrays.asList(servlets));
            }
            return names;
        }

        public Collection<String> getUrlPatternMappings() {
            FilterMapping[] mappings = _servletHandler.getFilterMappings();
            List<String> patterns = new ArrayList<String>();
            for (FilterMapping mapping : mappings) {
                if (mapping.getFilterHolder() != FilterHolder.this)
                    continue;
                String[] specs = mapping.getPathSpecs();
                patterns.addAll(TypeUtil.asList(specs));
            }
            return patterns;
        }
    }

    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    class Config extends HolderConfig implements FilterConfig {
        /* ------------------------------------------------------------ */
        public String getFilterName() {
            return _name;
        }
    }
}
