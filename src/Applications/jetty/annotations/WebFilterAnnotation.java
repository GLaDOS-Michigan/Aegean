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

package Applications.jetty.annotations;

import Applications.jetty.servlet.FilterHolder;
import Applications.jetty.servlet.FilterMapping;
import Applications.jetty.servlet.Holder;
import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;
import Applications.jetty.util.resource.Resource;
import Applications.jetty.webapp.DiscoveredAnnotation;
import Applications.jetty.webapp.MetaData;
import Applications.jetty.webapp.Origin;
import Applications.jetty.webapp.WebAppContext;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.annotation.WebFilter;
import javax.servlet.annotation.WebInitParam;
import java.util.ArrayList;
import java.util.EnumSet;

/**
 * WebFilterAnnotation
 */
public class WebFilterAnnotation extends DiscoveredAnnotation {
    private static final Logger LOG = Log.getLogger(WebFilterAnnotation.class);

    /**
     * @param context
     * @param className
     */
    public WebFilterAnnotation(WebAppContext context, String className) {
        super(context, className);
    }

    public WebFilterAnnotation(WebAppContext context, String className, Resource resource) {
        super(context, className, resource);
    }

    /**
     * @see DiscoveredAnnotation#apply()
     */
    public void apply() {
        // TODO verify against rules for annotation v descriptor

        Class clazz = getTargetClass();
        if (clazz == null) {
            LOG.warn(_className + " cannot be loaded");
            return;
        }


        //Servlet Spec 8.1.2
        if (!Filter.class.isAssignableFrom(clazz)) {
            LOG.warn(clazz.getName() + " is not assignable from javax.servlet.Filter");
            return;
        }
        MetaData metaData = _context.getMetaData();

        WebFilter filterAnnotation = (WebFilter) clazz.getAnnotation(WebFilter.class);

        if (filterAnnotation.value().length > 0 && filterAnnotation.urlPatterns().length > 0) {
            LOG.warn(clazz.getName() + " defines both @WebFilter.value and @WebFilter.urlPatterns");
            return;
        }

        String name = (filterAnnotation.filterName().equals("") ? clazz.getName() : filterAnnotation.filterName());
        String[] urlPatterns = filterAnnotation.value();
        if (urlPatterns.length == 0)
            urlPatterns = filterAnnotation.urlPatterns();

        FilterHolder holder = _context.getServletHandler().getFilter(name);
        if (holder == null) {
            //Filter with this name does not already exist, so add it
            holder = _context.getServletHandler().newFilterHolder(Holder.Source.ANNOTATION);
            holder.setName(name);

            holder.setHeldClass(clazz);
            metaData.setOrigin(name + ".filter.filter-class");

            holder.setDisplayName(filterAnnotation.displayName());
            metaData.setOrigin(name + ".filter.display-name");

            for (WebInitParam ip : filterAnnotation.initParams()) {
                holder.setInitParameter(ip.name(), ip.value());
                metaData.setOrigin(name + ".filter.init-param." + ip.name());
            }

            FilterMapping mapping = new FilterMapping();
            mapping.setFilterName(holder.getName());

            if (urlPatterns.length > 0) {
                ArrayList paths = new ArrayList();
                for (String s : urlPatterns) {
                    paths.add(Util.normalizePattern(s));
                }
                mapping.setPathSpecs((String[]) paths.toArray(new String[paths.size()]));
            }

            if (filterAnnotation.servletNames().length > 0) {
                ArrayList<String> names = new ArrayList<String>();
                for (String s : filterAnnotation.servletNames()) {
                    names.add(s);
                }
                mapping.setServletNames((String[]) names.toArray(new String[names.size()]));
            }

            EnumSet<DispatcherType> dispatcherSet = EnumSet.noneOf(DispatcherType.class);
            for (DispatcherType d : filterAnnotation.dispatcherTypes()) {
                dispatcherSet.add(d);
            }
            mapping.setDispatcherTypes(dispatcherSet);
            metaData.setOrigin(name + ".filter.mappings");

            holder.setAsyncSupported(filterAnnotation.asyncSupported());
            metaData.setOrigin(name + ".filter.async-supported");

            _context.getServletHandler().addFilter(holder);
            _context.getServletHandler().addFilterMapping(mapping);
        } else {
            //A Filter definition for the same name already exists from web.xml
            //ServletSpec 3.0 p81 if the Filter is already defined and has mappings,
            //they override the annotation. If it already has DispatcherType set, that
            //also overrides the annotation. Init-params are additive, but web.xml overrides
            //init-params of the same name.
            for (WebInitParam ip : filterAnnotation.initParams()) {
                //if (holder.getInitParameter(ip.name()) == null)
                if (metaData.getOrigin(name + ".filter.init-param." + ip.name()) == Origin.NotSet) {
                    holder.setInitParameter(ip.name(), ip.value());
                    metaData.setOrigin(name + ".filter.init-param." + ip.name());
                }
            }

            FilterMapping[] mappings = _context.getServletHandler().getFilterMappings();
            boolean mappingExists = false;
            if (mappings != null) {
                for (FilterMapping m : mappings) {
                    if (m.getFilterName().equals(name)) {
                        mappingExists = true;
                        break;
                    }
                }
            }
            //if a descriptor didn't specify at least one mapping, use the mappings from the annotation and the DispatcherTypes
            //from the annotation
            if (!mappingExists) {
                FilterMapping mapping = new FilterMapping();
                mapping.setFilterName(holder.getName());

                if (urlPatterns.length > 0) {
                    ArrayList paths = new ArrayList();
                    for (String s : urlPatterns) {
                        paths.add(Util.normalizePattern(s));
                    }
                    mapping.setPathSpecs((String[]) paths.toArray(new String[paths.size()]));
                }
                if (filterAnnotation.servletNames().length > 0) {
                    ArrayList<String> names = new ArrayList<String>();
                    for (String s : filterAnnotation.servletNames()) {
                        names.add(s);
                    }
                    mapping.setServletNames((String[]) names.toArray(new String[names.size()]));
                }

                EnumSet<DispatcherType> dispatcherSet = EnumSet.noneOf(DispatcherType.class);
                for (DispatcherType d : filterAnnotation.dispatcherTypes()) {
                    dispatcherSet.add(d);
                }
                mapping.setDispatcherTypes(dispatcherSet);
                _context.getServletHandler().addFilterMapping(mapping);
                metaData.setOrigin(name + ".filter.mappings");
            }
        }
    }

}
