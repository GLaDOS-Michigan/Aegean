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
import Applications.jetty.annotations.AnnotationParser.ClassInfo;
import Applications.jetty.annotations.AnnotationParser.FieldInfo;
import Applications.jetty.annotations.AnnotationParser.MethodInfo;
import Applications.jetty.plus.annotation.ContainerInitializer;

/**
 * ContainerInitializerAnnotationHandler
 * <p>
 * Discovers classes that contain the specified annotation, either at class or
 * method level. The specified annotation is derived from an @HandlesTypes on
 * a ServletContainerInitializer class.
 */

/**
 * @author janb
 *
 */
public class ContainerInitializerAnnotationHandler extends AbstractHandler {
    final ContainerInitializer _initializer;
    final Class _annotation;

    public ContainerInitializerAnnotationHandler(ContainerInitializer initializer, Class annotation) {
        _initializer = initializer;
        _annotation = annotation;
    }

    /**
     * Handle finding a class that is annotated with the annotation we were constructed with.
     *
     * @see Applications.jetty.annotations.AnnotationParser.Handler#handle(ClassInfo, String)
     */
    public void handle(ClassInfo info, String annotationName) {
        if (annotationName == null || !_annotation.getName().equals(annotationName))
            return;

        _initializer.addAnnotatedTypeName(info.getClassName());
    }

    /**
     * Handle finding a field that is annotated with the annotation we were constructed with.
     *
     * @see Applications.jetty.annotations.AnnotationParser.Handler#handle(FieldInfo, String)
     */
    public void handle(FieldInfo info, String annotationName) {
        if (annotationName == null || !_annotation.getName().equals(annotationName))
            return;
        _initializer.addAnnotatedTypeName(info.getClassInfo().getClassName());
    }

    /**
     * Handle finding a method that is annotated with the annotation we were constructed with. 
     *
     * @see Applications.jetty.annotations.AnnotationParser.Handler#handle(MethodInfo, String)
     */
    public void handle(MethodInfo info, String annotationName) {
        if (annotationName == null || !_annotation.getName().equals(annotationName))
            return;
        _initializer.addAnnotatedTypeName(info.getClassInfo().getClassName());
    }


    public ContainerInitializer getContainerInitializer() {
        return _initializer;
    }
}
