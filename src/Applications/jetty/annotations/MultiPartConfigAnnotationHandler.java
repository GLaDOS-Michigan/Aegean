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

import Applications.jetty.annotations.AnnotationIntrospector.AbstractIntrospectableAnnotationHandler;
import Applications.jetty.servlet.ServletHolder;
import Applications.jetty.webapp.Descriptor;
import Applications.jetty.webapp.MetaData;
import Applications.jetty.webapp.WebAppContext;

import javax.servlet.MultipartConfigElement;
import javax.servlet.Servlet;
import javax.servlet.annotation.MultipartConfig;

/**
 * MultiPartConfigAnnotationHandler
 */
public class MultiPartConfigAnnotationHandler extends AbstractIntrospectableAnnotationHandler {
    protected WebAppContext _context;

    public MultiPartConfigAnnotationHandler(WebAppContext context) {
        //TODO verify that MultipartConfig is not inheritable
        super(false);
        _context = context;
    }

    /**
     * @see Applications.jetty.annotations.AnnotationIntrospector.AbstractIntrospectableAnnotationHandler#doHandle(java.lang.Class)
     */
    public void doHandle(Class clazz) {
        if (!Servlet.class.isAssignableFrom(clazz))
            return;

        MultipartConfig multi = (MultipartConfig) clazz.getAnnotation(MultipartConfig.class);
        if (multi == null)
            return;

        MetaData metaData = _context.getMetaData();

        //TODO: The MultipartConfigElement needs to be set on the ServletHolder's Registration.
        //How to identify the correct Servlet?  If the Servlet has no WebServlet annotation on it, does it mean that this MultipartConfig
        //annotation applies to all declared instances in web.xml/programmatically?
        //Assuming TRUE for now.

        ServletHolder holder = getServletHolderForClass(clazz);
        if (holder != null) {
            Descriptor d = metaData.getOriginDescriptor(holder.getName() + ".servlet.multipart-config");
            //if a descriptor has already set the value for multipart config, do not 
            //let the annotation override it
            if (d == null) {
                metaData.setOrigin(holder.getName() + ".servlet.multipart-config");
                holder.getRegistration().setMultipartConfig(new MultipartConfigElement(multi));
            }
        }
    }

    private ServletHolder getServletHolderForClass(Class clazz) {
        ServletHolder holder = null;
        ServletHolder[] holders = _context.getServletHandler().getServlets();
        if (holders != null) {
            for (ServletHolder h : holders) {
                if (h.getClassName() != null && h.getClassName().equals(clazz.getName())) {
                    holder = h;
                }
            }
        }
        return holder;
    }
}
