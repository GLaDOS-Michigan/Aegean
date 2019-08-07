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
import Applications.jetty.plus.annotation.Injection;
import Applications.jetty.plus.annotation.InjectionCollection;
import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;
import Applications.jetty.webapp.MetaData;
import Applications.jetty.webapp.WebAppContext;

import javax.annotation.Resource;
import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;

public class ResourceAnnotationHandler extends AbstractIntrospectableAnnotationHandler {
    private static final Logger LOG = Log.getLogger(ResourceAnnotationHandler.class);

    protected WebAppContext _context;


    public ResourceAnnotationHandler(WebAppContext wac) {
        super(true);
        _context = wac;
    }


    /**
     * Class level Resource annotations declare a name in the
     * environment that will be looked up at runtime. They do
     * not specify an injection.
     */
    public void doHandle(Class<?> clazz) {
        if (Util.supportsResourceInjection(clazz)) {
            handleClass(clazz);

            Method[] methods = clazz.getDeclaredMethods();
            for (int i = 0; i < methods.length; i++)
                handleMethod(clazz, methods[i]);
            Field[] fields = clazz.getDeclaredFields();
            //For each field, get all of it's annotations
            for (int i = 0; i < fields.length; i++)
                handleField(clazz, fields[i]);
        }
    }

    public void handleClass(Class<?> clazz) {
        Resource resource = (Resource) clazz.getAnnotation(Resource.class);
        if (resource != null) {
            String name = resource.name();
            String mappedName = resource.mappedName();

            if (name == null || name.trim().equals(""))
                throw new IllegalStateException("Class level Resource annotations must contain a name (Common Annotations Spec Section 2.3)");

            try {
                if (!Applications.jetty.plus.jndi.NamingEntryUtil.bindToENC(_context, name, mappedName))
                    if (!Applications.jetty.plus.jndi.NamingEntryUtil.bindToENC(_context.getServer(), name, mappedName))
                        throw new IllegalStateException("No resource at " + (mappedName == null ? name : mappedName));
            } catch (NamingException e) {
                LOG.warn(e);
            }
        }
    }

    public void handleField(Class<?> clazz, Field field) {
        Resource resource = (Resource) field.getAnnotation(Resource.class);
        if (resource != null) {
            //JavaEE Spec 5.2.3: Field cannot be static
            if (Modifier.isStatic(field.getModifiers())) {
                LOG.warn("Skipping Resource annotation on " + clazz.getName() + "." + field.getName() + ": cannot be static");
                return;
            }

            //JavaEE Spec 5.2.3: Field cannot be final
            if (Modifier.isFinal(field.getModifiers())) {
                LOG.warn("Skipping Resource annotation on " + clazz.getName() + "." + field.getName() + ": cannot be final");
                return;
            }

            //work out default name
            String name = clazz.getCanonicalName() + "/" + field.getName();

            //allow @Resource name= to override the field name
            name = (resource.name() != null && !resource.name().trim().equals("") ? resource.name() : name);
            String mappedName = (resource.mappedName() != null && !resource.mappedName().trim().equals("") ? resource.mappedName() : null);
            //get the type of the Field
            Class<?> type = field.getType();

            //Servlet Spec 3.0 p. 76
            //If a descriptor has specified at least 1 injection target for this
            //resource, then it overrides this annotation
            MetaData metaData = _context.getMetaData();
            if (metaData.getOriginDescriptor("resource-ref." + name + ".injection") != null) {
                //at least 1 injection was specified for this resource by a descriptor, so
                //it overrides this annotation
                return;
            }

            //No injections for this resource in any descriptors, so we can add it
            //Does the injection already exist?
            InjectionCollection injections = (InjectionCollection) _context.getAttribute(InjectionCollection.INJECTION_COLLECTION);
            if (injections == null) {
                injections = new InjectionCollection();
                _context.setAttribute(InjectionCollection.INJECTION_COLLECTION, injections);
            }
            Injection injection = injections.getInjection(name, clazz, field);
            if (injection == null) {
                //No injection has been specified, add it
                try {
                    boolean bound = Applications.jetty.plus.jndi.NamingEntryUtil.bindToENC(_context, name, mappedName);
                    if (!bound)
                        bound = Applications.jetty.plus.jndi.NamingEntryUtil.bindToENC(_context.getServer(), name, mappedName);
                    if (!bound)
                        bound = Applications.jetty.plus.jndi.NamingEntryUtil.bindToENC(null, name, mappedName);
                    if (!bound) {
                        //see if there is an env-entry value been bound
                        try {
                            InitialContext ic = new InitialContext();
                            String nameInEnvironment = (mappedName != null ? mappedName : name);
                            ic.lookup("java:comp/env/" + nameInEnvironment);
                            bound = true;
                        } catch (NameNotFoundException e) {
                            bound = false;
                        }
                    }
                    //Check there is a JNDI entry for this annotation
                    if (bound) {
                        LOG.debug("Bound " + (mappedName == null ? name : mappedName) + " as " + name);
                        //   Make the Injection for it if the binding succeeded
                        injection = new Injection();
                        injection.setTarget(clazz, field, type);
                        injection.setJndiName(name);
                        injection.setMappingName(mappedName);
                        injections.add(injection);

                        //TODO - an @Resource is equivalent to a resource-ref, resource-env-ref, message-destination
                        metaData.setOrigin("resource-ref." + name + ".injection");
                    } else if (!Util.isEnvEntryType(type)) {
                        //if this is an env-entry type resource and there is no value bound for it, it isn't
                        //an error, it just means that perhaps the code will use a default value instead
                        // JavaEE Spec. sec 5.4.1.3

                        throw new IllegalStateException("No resource at " + (mappedName == null ? name : mappedName));
                    }
                } catch (NamingException e) {
                    //if this is an env-entry type resource and there is no value bound for it, it isn't
                    //an error, it just means that perhaps the code will use a default value instead
                    // JavaEE Spec. sec 5.4.1.3
                    if (!Util.isEnvEntryType(type))
                        throw new IllegalStateException(e);
                }
            }
        }
    }


    /**
     * Process a Resource annotation on a Method.
     * <p>
     * This will generate a JNDI entry, and an Injection to be
     * processed when an instance of the class is created.
     */
    public void handleMethod(Class<?> clazz, Method method) {

        Resource resource = (Resource) method.getAnnotation(Resource.class);
        if (resource != null) {
            /*
             * Commons Annotations Spec 2.3
             * " The Resource annotation is used to declare a reference to a resource.
             *   It can be specified on a class, methods or on fields. When the
             *   annotation is applied on a field or method, the container will
             *   inject an instance of the requested resource into the application
             *   when the application is initialized... Even though this annotation
             *   is not marked Inherited, if used all superclasses MUST be examined
             *   to discover all uses of this annotation. All such annotation instances
             *   specify resources that are needed by the application. Note that this
             *   annotation may appear on private fields and methods of the superclasses.
             *   Injection of the declared resources needs to happen in these cases as
             *   well, even if a method with such an annotation is overridden by a subclass."
             *
             *  Which IMHO, put more succinctly means "If you find a @Resource on any method
             *  or field, inject it!".
             */
            //JavaEE Spec 5.2.3: Method cannot be static
            if (Modifier.isStatic(method.getModifiers())) {
                LOG.warn("Skipping Resource annotation on " + clazz.getName() + "." + method.getName() + ": cannot be static");
                return;
            }

            // Check it is a valid javabean: must be void return type, the name must start with "set" and it must have
            // only 1 parameter
            if (!method.getName().startsWith("set")) {
                LOG.warn("Skipping Resource annotation on " + clazz.getName() + "." + method.getName() + ": invalid java bean, does not start with 'set'");
                return;
            }

            if (method.getParameterTypes().length != 1) {
                LOG.warn("Skipping Resource annotation on " + clazz.getName() + "." + method.getName() + ": invalid java bean, not single argument to method");
                return;
            }

            if (Void.TYPE != method.getReturnType()) {
                LOG.warn("Skipping Resource annotation on " + clazz.getName() + "." + method.getName() + ": invalid java bean, not void");
                return;
            }


            //default name is the javabean property name
            String name = method.getName().substring(3);
            name = name.substring(0, 1).toLowerCase(Locale.ENGLISH) + name.substring(1);
            name = clazz.getCanonicalName() + "/" + name;

            name = (resource.name() != null && !resource.name().trim().equals("") ? resource.name() : name);
            String mappedName = (resource.mappedName() != null && !resource.mappedName().trim().equals("") ? resource.mappedName() : null);
            Class<?> paramType = method.getParameterTypes()[0];

            Class<?> resourceType = resource.type();

            //Servlet Spec 3.0 p. 76
            //If a descriptor has specified at least 1 injection target for this
            //resource, then it overrides this annotation
            MetaData metaData = _context.getMetaData();
            if (metaData.getOriginDescriptor("resource-ref." + name + ".injection") != null) {
                //at least 1 injection was specified for this resource by a descriptor, so
                //it overrides this annotation
                return;
            }

            //check if an injection has already been setup for this target by web.xml
            InjectionCollection injections = (InjectionCollection) _context.getAttribute(InjectionCollection.INJECTION_COLLECTION);
            if (injections == null) {
                injections = new InjectionCollection();
                _context.setAttribute(InjectionCollection.INJECTION_COLLECTION, injections);
            }
            Injection injection = injections.getInjection(name, clazz, method, paramType);
            if (injection == null) {
                try {
                    //try binding name to environment
                    //try the webapp's environment first
                    boolean bound = Applications.jetty.plus.jndi.NamingEntryUtil.bindToENC(_context, name, mappedName);

                    //try the server's environment
                    if (!bound)
                        bound = Applications.jetty.plus.jndi.NamingEntryUtil.bindToENC(_context.getServer(), name, mappedName);

                    //try the jvm's environment
                    if (!bound)
                        bound = Applications.jetty.plus.jndi.NamingEntryUtil.bindToENC(null, name, mappedName);

                    //TODO if it is an env-entry from web.xml it can be injected, in which case there will be no
                    //NamingEntry, just a value bound in java:comp/env
                    if (!bound) {
                        try {
                            InitialContext ic = new InitialContext();
                            String nameInEnvironment = (mappedName != null ? mappedName : name);
                            ic.lookup("java:comp/env/" + nameInEnvironment);
                            bound = true;
                        } catch (NameNotFoundException e) {
                            bound = false;
                        }
                    }

                    if (bound) {
                        LOG.debug("Bound " + (mappedName == null ? name : mappedName) + " as " + name);
                        //   Make the Injection for it
                        injection = new Injection();
                        injection.setTarget(clazz, method, paramType, resourceType);
                        injection.setJndiName(name);
                        injection.setMappingName(mappedName);
                        injections.add(injection);
                        //TODO - an @Resource is equivalent to a resource-ref, resource-env-ref, message-destination
                        metaData.setOrigin("resource-ref." + name + ".injection");
                    } else if (!Util.isEnvEntryType(paramType)) {

                        //if this is an env-entry type resource and there is no value bound for it, it isn't
                        //an error, it just means that perhaps the code will use a default value instead
                        // JavaEE Spec. sec 5.4.1.3
                        throw new IllegalStateException("No resource at " + (mappedName == null ? name : mappedName));
                    }
                } catch (NamingException e) {
                    //if this is an env-entry type resource and there is no value bound for it, it isn't
                    //an error, it just means that perhaps the code will use a default value instead
                    // JavaEE Spec. sec 5.4.1.3
                    if (!Util.isEnvEntryType(paramType))
                        throw new IllegalStateException(e);
                }
            }

        }
    }
}
