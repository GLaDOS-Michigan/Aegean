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

import Applications.jetty.annotations.AnnotationParser.AbstractHandler;
import Applications.jetty.webapp.DiscoveredAnnotation;
import Applications.jetty.webapp.WebAppContext;

/**
 * DiscoverableAnnotationHandler
 * <p>
 * Base class for handling the discovery of an annotation.
 */
public abstract class AbstractDiscoverableAnnotationHandler extends AbstractHandler {
    protected WebAppContext _context;


    public AbstractDiscoverableAnnotationHandler(WebAppContext context) {
        _context = context;
    }


    public void addAnnotation(DiscoveredAnnotation a) {
        _context.getMetaData().addDiscoveredAnnotation(a);
    }

}
