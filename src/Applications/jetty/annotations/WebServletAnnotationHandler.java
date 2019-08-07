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

import Applications.jetty.annotations.AnnotationParser.ClassInfo;
import Applications.jetty.annotations.AnnotationParser.FieldInfo;
import Applications.jetty.annotations.AnnotationParser.MethodInfo;
import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;
import Applications.jetty.webapp.WebAppContext;

/**
 * WebServletAnnotationHandler
 * <p>
 * Process a WebServlet annotation on a class.
 */
public class WebServletAnnotationHandler extends AbstractDiscoverableAnnotationHandler {
    private static final Logger LOG = Log.getLogger(WebServletAnnotationHandler.class);

    public WebServletAnnotationHandler(WebAppContext context) {
        super(context);
    }


    /**
     * Handle discovering a WebServlet annotation.
     *
     * @see org.eclipse.jetty.annotations.AnnotationParser.Handler#handle(ClassInfo, String)
     */
    @Override
    public void handle(ClassInfo info, String annotationName) {
        if (annotationName == null || !"javax.servlet.annotation.WebServlet".equals(annotationName))
            return;

        WebServletAnnotation annotation = new WebServletAnnotation(_context, info.getClassName(), info.getContainingResource());
        addAnnotation(annotation);
    }

    @Override
    public void handle(FieldInfo info, String annotationName) {
        if (annotationName == null || !"javax.servlet.annotation.WebServlet".equals(annotationName))
            return;

        LOG.warn("@WebServlet annotation not supported for fields");
    }

    @Override
    public void handle(MethodInfo info, String annotationName) {
        if (annotationName == null || !"javax.servlet.annotation.WebServlet".equals(annotationName))
            return;

        LOG.warn("@WebServlet annotation not supported for methods");
    }
}
