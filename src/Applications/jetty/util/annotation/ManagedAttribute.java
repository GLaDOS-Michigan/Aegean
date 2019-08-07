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

package Applications.jetty.util.annotation;
//========================================================================
//Copyright 2011-2012 Mort Bay Consulting Pty. Ltd.
//------------------------------------------------------------------------
//All rights reserved. This program and the accompanying materials
//are made available under the terms of the Eclipse Public License v1.0
//and Apache License v2.0 which accompanies this distribution.
//The Eclipse Public License is available at
//http://www.eclipse.org/legal/epl-v10.html
//The Apache License v2.0 is available at
//http://www.opensource.org/licenses/apache2.0.php
//You may elect to redistribute this code under either of these licenses.
//========================================================================

import java.lang.annotation.*;

/**
 * The @ManagedAttribute annotation is used to indicate that a given method
 * exposes a JMX attribute. This annotation is placed always on the reader
 * method of a given attribute. Unless it is marked as read-only in the
 * configuration of the annotation a corresponding setter is looked for
 * following normal naming conventions. For example if this annotation is
 * on a method called getFoo() then a method called setFoo() would be looked
 * for and if found wired automatically into the jmx attribute.
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target({ElementType.METHOD})
public @interface ManagedAttribute {
    /**
     * Description of the Managed Attribute
     *
     * @returngit checkout
     */
    String value() default "Not Specified";

    /**
     * name to use for the attribute
     *
     * @return the name of the attribute
     */
    String name() default "";

    /**
     * Is the managed field read-only?
     * <p>
     * Required only when a setter exists but should not be exposed via JMX
     *
     * @return true if readonly
     */
    boolean readonly() default false;

    /**
     * Does the managed field exist on a proxy object?
     *
     * @return true if a proxy object is involved
     */
    boolean proxied() default false;


    /**
     * If is a field references a setter that doesn't conform to standards for discovery
     * it can be set here.
     *
     * @return the full name of the setter in question
     */
    String setter() default "";

}
