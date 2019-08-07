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

import Applications.jetty.util.ConcurrentHashSet;
import Applications.jetty.util.Loader;
import Applications.jetty.util.MultiException;
import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;
import Applications.jetty.util.resource.Resource;
import Applications.jetty.webapp.JarScanner;
import org.objectweb.asm.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;


/**
 * AnnotationParser
 * <p>
 * Use asm to scan classes for annotations. A SAX-style parsing is done.
 * Handlers are registered which will be called back when various types of
 * entity are encountered, eg a class, a method, a field.
 * <p>
 * Handlers are not called back in any particular order and are assumed
 * to be order-independent.
 * <p>
 * As a registered Handler will be called back for each annotation discovered
 * on a class, a method, a field, the Handler should test to see if the annotation
 * is one that it is interested in.
 * <p>
 * For the servlet spec, we are only interested in annotations on classes, methods and fields,
 * so the callbacks for handling finding a class, a method a field are themselves
 * not fully implemented.
 */
public class AnnotationParser {
    private static final Logger LOG = Log.getLogger(AnnotationParser.class);

    protected Set<String> _parsedClassNames = new ConcurrentHashSet<String>();


    /**
     * Convert internal name to simple name
     *
     * @param name
     * @return
     */
    public static String normalize(String name) {
        if (name == null)
            return null;

        if (name.startsWith("L") && name.endsWith(";"))
            name = name.substring(1, name.length() - 1);

        if (name.endsWith(".class"))
            name = name.substring(0, name.length() - ".class".length());

        return name.replace('/', '.');
    }

    /**
     * Convert internal names to simple names.
     *
     * @param list
     * @return
     */
    public static String[] normalize(String[] list) {
        if (list == null)
            return null;
        String[] normalList = new String[list.length];
        int i = 0;
        for (String s : list)
            normalList[i++] = normalize(s);
        return normalList;
    }


    /**
     * ClassInfo
     * <p>
     * Immutable information gathered by parsing class header.
     */
    public class ClassInfo {
        final Resource _containingResource;
        final String _className;
        final int _version;
        final int _access;
        final String _signature;
        final String _superName;
        final String[] _interfaces;

        public ClassInfo(Resource resource, String className, int version, int access, String signature, String superName, String[] interfaces) {
            super();
            _containingResource = resource;
            _className = className;
            _version = version;
            _access = access;
            _signature = signature;
            _superName = superName;
            _interfaces = interfaces;
        }

        public String getClassName() {
            return _className;
        }

        public int getVersion() {
            return _version;
        }

        public int getAccess() {
            return _access;
        }

        public String getSignature() {
            return _signature;
        }

        public String getSuperName() {
            return _superName;
        }

        public String[] getInterfaces() {
            return _interfaces;
        }

        public Resource getContainingResource() {
            return _containingResource;
        }
    }


    /**
     * MethodInfo
     * <p>
     * Immutable information gathered by parsing a method on a class.
     */
    public class MethodInfo {
        final ClassInfo _classInfo;
        final String _methodName;
        final int _access;
        final String _desc;
        final String _signature;
        final String[] _exceptions;

        public MethodInfo(ClassInfo classInfo, String methodName, int access, String desc, String signature, String[] exceptions) {
            super();
            _classInfo = classInfo;
            _methodName = methodName;
            _access = access;
            _desc = desc;
            _signature = signature;
            _exceptions = exceptions;
        }

        public ClassInfo getClassInfo() {
            return _classInfo;
        }

        public String getMethodName() {
            return _methodName;
        }

        public int getAccess() {
            return _access;
        }

        public String getDesc() {
            return _desc;
        }

        public String getSignature() {
            return _signature;
        }

        public String[] getExceptions() {
            return _exceptions;
        }
    }


    /**
     * FieldInfo
     * <p>
     * Immutable information gathered by parsing a field on a class.
     */
    public class FieldInfo {
        final ClassInfo _classInfo;
        final String _fieldName;
        final int _access;
        final String _fieldType;
        final String _signature;
        final Object _value;

        public FieldInfo(ClassInfo classInfo, String fieldName, int access, String fieldType, String signature, Object value) {
            super();
            _classInfo = classInfo;
            _fieldName = fieldName;
            _access = access;
            _fieldType = fieldType;
            _signature = signature;
            _value = value;
        }

        public ClassInfo getClassInfo() {
            return _classInfo;
        }

        public String getFieldName() {
            return _fieldName;
        }

        public int getAccess() {
            return _access;
        }

        public String getFieldType() {
            return _fieldType;
        }

        public String getSignature() {
            return _signature;
        }

        public Object getValue() {
            return _value;
        }
    }


    /**
     * Handler
     * <p>
     * Signature for all handlers that respond to parsing class files.
     */
    public static interface Handler {
        public void handle(ClassInfo classInfo);

        public void handle(MethodInfo methodInfo);

        public void handle(FieldInfo fieldInfo);

        public void handle(ClassInfo info, String annotationName);

        public void handle(MethodInfo info, String annotationName);

        public void handle(FieldInfo info, String annotationName);
    }


    /**
     * AbstractHandler
     * <p>
     * Convenience base class to provide no-ops for all Handler methods.
     */
    public static abstract class AbstractHandler implements Handler {

        @Override
        public void handle(ClassInfo classInfo) {
            //no-op
        }

        @Override
        public void handle(MethodInfo methodInfo) {
            // no-op           
        }

        @Override
        public void handle(FieldInfo fieldInfo) {
            // no-op 
        }

        @Override
        public void handle(ClassInfo info, String annotationName) {
            // no-op 
        }

        @Override
        public void handle(MethodInfo info, String annotationName) {
            // no-op            
        }

        @Override
        public void handle(FieldInfo info, String annotationName) {
            // no-op
        }
    }


    /**
     * MyMethodVisitor
     * <p>
     * ASM Visitor for parsing a method. We are only interested in the annotations on methods.
     */
    public class MyMethodVisitor extends MethodVisitor {
        final MethodInfo _mi;
        final Set<? extends Handler> _handlers;

        public MyMethodVisitor(final Set<? extends Handler> handlers,
                               final ClassInfo classInfo,
                               final int access,
                               final String name,
                               final String methodDesc,
                               final String signature,
                               final String[] exceptions) {
            super(Opcodes.ASM4);
            _handlers = handlers;
            _mi = new MethodInfo(classInfo, name, access, methodDesc, signature, exceptions);
        }


        /**
         * We are only interested in finding the annotations on methods.
         *
         * @see org.objectweb.asm.MethodVisitor#visitAnnotation(java.lang.String, boolean)
         */
        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            String annotationName = normalize(desc);
            for (Handler h : _handlers)
                h.handle(_mi, annotationName);
            return null;
        }
    }


    /**
     * MyFieldVisitor
     * <p>
     * An ASM visitor for parsing Fields.
     * We are only interested in visiting annotations on Fields.
     */
    public class MyFieldVisitor extends FieldVisitor {
        final FieldInfo _fieldInfo;
        final Set<? extends Handler> _handlers;


        public MyFieldVisitor(final Set<? extends Handler> handlers,
                              final ClassInfo classInfo,
                              final int access,
                              final String fieldName,
                              final String fieldType,
                              final String signature,
                              final Object value) {
            super(Opcodes.ASM4);
            _handlers = handlers;
            _fieldInfo = new FieldInfo(classInfo, fieldName, access, fieldType, signature, value);
        }


        /**
         * Parse an annotation found on a Field.
         *
         * @see org.objectweb.asm.FieldVisitor#visitAnnotation(java.lang.String, boolean)
         */
        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            String annotationName = normalize(desc);
            for (Handler h : _handlers)
                h.handle(_fieldInfo, annotationName);

            return null;
        }
    }


    /**
     * MyClassVisitor
     * <p>
     * ASM visitor for a class.
     */
    public class MyClassVisitor extends ClassVisitor {

        final Resource _containingResource;
        final Set<? extends Handler> _handlers;
        ClassInfo _ci;

        public MyClassVisitor(Set<? extends Handler> handlers, Resource containingResource) {
            super(Opcodes.ASM4);
            _handlers = handlers;
            _containingResource = containingResource;
        }


        @Override
        public void visit(final int version,
                          final int access,
                          final String name,
                          final String signature,
                          final String superName,
                          final String[] interfaces) {
            _ci = new ClassInfo(_containingResource, normalize(name), version, access, signature, normalize(superName), normalize(interfaces));

            _parsedClassNames.add(_ci.getClassName());

            for (Handler h : _handlers)
                h.handle(_ci);
        }


        /**
         * Visit an annotation on a Class
         *
         * @see org.objectweb.asm.ClassVisitor#visitAnnotation(java.lang.String, boolean)
         */
        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            String annotationName = normalize(desc);
            for (Handler h : _handlers)
                h.handle(_ci, annotationName);

            return null;
        }


        /**
         * Visit a method to extract its annotations
         *
         * @see org.objectweb.asm.ClassVisitor#visitMethod(int, java.lang.String, java.lang.String, java.lang.String, java.lang.String[])
         */
        @Override
        public MethodVisitor visitMethod(final int access,
                                         final String name,
                                         final String methodDesc,
                                         final String signature,
                                         final String[] exceptions) {

            return new MyMethodVisitor(_handlers, _ci, access, name, methodDesc, signature, exceptions);
        }

        /**
         * Visit a field to extract its annotations
         *
         * @see org.objectweb.asm.ClassVisitor#visitField(int, java.lang.String, java.lang.String, java.lang.String, java.lang.Object)
         */
        @Override
        public FieldVisitor visitField(final int access,
                                       final String fieldName,
                                       final String fieldType,
                                       final String signature,
                                       final Object value) {
            return new MyFieldVisitor(_handlers, _ci, access, fieldName, fieldType, signature, value);
        }
    }


    /**
     * True if the class has already been processed, false otherwise
     *
     * @param className
     */
    public boolean isParsed(String className) {
        return _parsedClassNames.contains(className);
    }


    /**
     * Parse a given class
     *
     * @param className
     * @param resolver
     * @throws Exception
     */
    public void parse(Set<? extends Handler> handlers, String className, ClassNameResolver resolver)
            throws Exception {
        if (className == null)
            return;

        if (!resolver.isExcluded(className)) {
            if (!isParsed(className) || resolver.shouldOverride(className)) {
                className = className.replace('.', '/') + ".class";
                URL resource = Loader.getResource(this.getClass(), className);
                if (resource != null) {
                    Resource r = Resource.newResource(resource);
                    scanClass(handlers, null, r.getInputStream());
                }
            }
        }
    }


    /**
     * Parse the given class, optionally walking its inheritance hierarchy
     *
     * @param clazz
     * @param resolver
     * @param visitSuperClasses
     * @throws Exception
     */
    public void parse(Set<? extends Handler> handlers, Class<?> clazz, ClassNameResolver resolver, boolean visitSuperClasses)
            throws Exception {
        Class<?> cz = clazz;
        while (cz != null) {
            if (!resolver.isExcluded(cz.getName())) {
                if (!isParsed(cz.getName()) || resolver.shouldOverride(cz.getName())) {
                    String nameAsResource = cz.getName().replace('.', '/') + ".class";
                    URL resource = Loader.getResource(this.getClass(), nameAsResource);
                    if (resource != null) {
                        Resource r = Resource.newResource(resource);
                        scanClass(handlers, null, r.getInputStream());
                    }
                }
            }

            if (visitSuperClasses)
                cz = cz.getSuperclass();
            else
                cz = null;
        }
    }


    /**
     * Parse the given classes
     *
     * @param classNames
     * @param resolver
     * @throws Exception
     */
    public void parse(Set<? extends Handler> handlers, String[] classNames, ClassNameResolver resolver)
            throws Exception {
        if (classNames == null)
            return;

        parse(handlers, Arrays.asList(classNames), resolver);
    }


    /**
     * Parse the given classes
     *
     * @param classNames
     * @param resolver
     * @throws Exception
     */
    public void parse(Set<? extends Handler> handlers, List<String> classNames, ClassNameResolver resolver)
            throws Exception {
        MultiException me = new MultiException();

        for (String s : classNames) {
            try {
                if ((resolver == null) || (!resolver.isExcluded(s) && (!isParsed(s) || resolver.shouldOverride(s)))) {
                    s = s.replace('.', '/') + ".class";
                    URL resource = Loader.getResource(this.getClass(), s);
                    if (resource != null) {
                        Resource r = Resource.newResource(resource);
                        scanClass(handlers, null, r.getInputStream());
                    }
                }
            } catch (Exception e) {
                me.add(new RuntimeException("Error scanning class " + s, e));
            }
        }
        me.ifExceptionThrow();
    }


    /**
     * Parse all classes in a directory
     *
     * @param dir
     * @param resolver
     * @throws Exception
     */
    protected void parseDir(Set<? extends Handler> handlers, Resource dir, ClassNameResolver resolver)
            throws Exception {
        //skip dirs whose name start with . (ie hidden)
        if (!dir.isDirectory() || !dir.exists() || dir.getName().startsWith("."))
            return;

        if (LOG.isDebugEnabled()) {
            LOG.debug("Scanning dir {}", dir);
        }
        ;

        MultiException me = new MultiException();

        String[] files = dir.list();
        for (int f = 0; files != null && f < files.length; f++) {
            Resource res = dir.addPath(files[f]);
            if (res.isDirectory())
                parseDir(handlers, res, resolver);
            else {
                //we've already verified the directories, so just verify the class file name
                File file = res.getFile();
                if (isValidClassFileName((file == null ? null : file.getName()))) {
                    try {
                        String name = res.getName();
                        if ((resolver == null) || (!resolver.isExcluded(name) && (!isParsed(name) || resolver.shouldOverride(name)))) {
                            Resource r = Resource.newResource(res.getURL());
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Scanning class {}", r);
                            }
                            ;
                            scanClass(handlers, dir, r.getInputStream());
                        }
                    } catch (Exception ex) {
                        me.add(new RuntimeException("Error scanning file " + files[f], ex));
                    }
                } else {
                    if (LOG.isDebugEnabled()) LOG.debug("Skipping scan on invalid file {}", res);
                }
            }
        }

        me.ifExceptionThrow();
    }


    /**
     * Parse classes in the supplied classloader.
     * Only class files in jar files will be scanned.
     *
     * @param loader
     * @param visitParents
     * @param nullInclusive
     * @param resolver
     * @throws Exception
     */
    public void parse(final Set<? extends Handler> handlers, ClassLoader loader, boolean visitParents, boolean nullInclusive, final ClassNameResolver resolver)
            throws Exception {
        if (loader == null)
            return;

        if (!(loader instanceof URLClassLoader))
            return; //can't extract classes?

        final MultiException me = new MultiException();

        JarScanner scanner = new JarScanner() {
            @Override
            public void processEntry(URI jarUri, JarEntry entry) {
                try {
                    parseJarEntry(handlers, Resource.newResource(jarUri), entry, resolver);
                } catch (Exception e) {
                    me.add(new RuntimeException("Error parsing entry " + entry.getName() + " from jar " + jarUri, e));
                }
            }

        };

        scanner.scan(null, loader, nullInclusive, visitParents);
        me.ifExceptionThrow();
    }


    /**
     * Parse classes in the supplied uris.
     *
     * @param uris
     * @param resolver
     * @throws Exception
     */
    public void parse(final Set<? extends Handler> handlers, final URI[] uris, final ClassNameResolver resolver)
            throws Exception {
        if (uris == null)
            return;

        MultiException me = new MultiException();

        for (URI uri : uris) {
            try {
                parse(handlers, uri, resolver);
            } catch (Exception e) {
                me.add(new RuntimeException("Problem parsing classes from " + uri, e));
            }
        }
        me.ifExceptionThrow();
    }

    /**
     * Parse a particular uri
     *
     * @param uri
     * @param resolver
     * @throws Exception
     */
    public void parse(final Set<? extends Handler> handlers, URI uri, final ClassNameResolver resolver)
            throws Exception {
        if (uri == null)
            return;

        parse(handlers, Resource.newResource(uri), resolver);
    }


    /**
     * Parse a resource
     *
     * @param r
     * @param resolver
     * @throws Exception
     */
    public void parse(final Set<? extends Handler> handlers, Resource r, final ClassNameResolver resolver)
            throws Exception {
        if (r == null)
            return;

        if (r.exists() && r.isDirectory()) {
            parseDir(handlers, r, resolver);
            return;
        }

        String fullname = r.toString();
        if (fullname.endsWith(".jar")) {
            parseJar(handlers, r, resolver);
            return;
        }

        if (fullname.endsWith(".class")) {
            scanClass(handlers, null, r.getInputStream());
            return;
        }

        if (LOG.isDebugEnabled()) LOG.warn("Resource not scannable for classes: {}", r);
    }


    /**
     * Parse a resource that is a jar file.
     *
     * @param jarResource
     * @param resolver
     * @throws Exception
     */
    protected void parseJar(Set<? extends Handler> handlers, Resource jarResource, final ClassNameResolver resolver)
            throws Exception {
        if (jarResource == null)
            return;

        if (jarResource.toString().endsWith(".jar")) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Scanning jar {}", jarResource);
            }
            ;

            //treat it as a jar that we need to open and scan all entries from  
            //TODO alternative impl
            /*
            long start = System.nanoTime();
            Collection<Resource> resources = Resource.newResource("jar:"+jarResource+"!/").getAllResources();
            System.err.println(jarResource+String.valueOf(resources.size())+" resources listed in "+ ((TimeUnit.MILLISECONDS.convert(System.nanoTime()-start, TimeUnit.NANOSECONDS))));
            for (Resource r:resources)
            {
                //skip directories
                if (r.isDirectory())
                    continue;

                String name = r.getName();
                name = name.substring(name.indexOf("!/")+2);

                //check file is a valid class file name
                if (isValidClassFileName(name) && isValidClassFilePath(name))
                {
                    String shortName =  name.replace('/', '.').substring(0,name.length()-6);

                    if ((resolver == null)
                            ||
                        (!resolver.isExcluded(shortName) && (!isParsed(shortName) || resolver.shouldOverride(shortName))))
                    {
                        if (LOG.isDebugEnabled()) {LOG.debug("Scanning class from jar {}", r);};
                        scanClass(handlers, jarResource, r.getInputStream());
                    }
                }
            }
            */

            InputStream in = jarResource.getInputStream();
            if (in == null)
                return;

            MultiException me = new MultiException();

            JarInputStream jar_in = new JarInputStream(in);
            try {
                JarEntry entry = jar_in.getNextJarEntry();
                while (entry != null) {
                    try {
                        parseJarEntry(handlers, jarResource, entry, resolver);
                    } catch (Exception e) {
                        me.add(new RuntimeException("Error scanning entry " + entry.getName() + " from jar " + jarResource, e));
                    }
                    entry = jar_in.getNextJarEntry();
                }
            } finally {
                jar_in.close();
            }
            me.ifExceptionThrow();
        }
    }

    /**
     * Parse a single entry in a jar file
     *
     * @param jar
     * @param entry
     * @param resolver
     * @throws Exception
     */
    protected void parseJarEntry(Set<? extends Handler> handlers, Resource jar, JarEntry entry, final ClassNameResolver resolver)
            throws Exception {
        if (jar == null || entry == null)
            return;

        //skip directories
        if (entry.isDirectory())
            return;

        String name = entry.getName();

        //check file is a valid class file name
        if (isValidClassFileName(name) && isValidClassFilePath(name)) {
            String shortName = name.replace('/', '.').substring(0, name.length() - 6);

            if ((resolver == null)
                    ||
                    (!resolver.isExcluded(shortName) && (!isParsed(shortName) || resolver.shouldOverride(shortName)))) {
                Resource clazz = Resource.newResource("jar:" + jar.getURI() + "!/" + name);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Scanning class from jar {}", clazz);
                }
                ;
                scanClass(handlers, jar, clazz.getInputStream());
            }
        }
    }


    /**
     * Use ASM on a class
     *
     * @param containingResource the dir or jar that the class is contained within, can be null if not known
     * @param is
     * @throws IOException
     */
    protected void scanClass(Set<? extends Handler> handlers, Resource containingResource, InputStream is)
            throws IOException {
        ClassReader reader = new ClassReader(is);
        reader.accept(new MyClassVisitor(handlers, containingResource), ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }

    /**
     * Check that the given path represents a valid class file name.
     * The check is fairly cursory, checking that:
     * <ul>
     * <li> the name ends with .class</li>
     * <li> it isn't a dot file or in a hidden directory </li>
     * <li> the name of the class at least begins with a valid identifier for a class name </li>
     * </ul>
     *
     * @param name
     * @return
     */
    private boolean isValidClassFileName(String name) {
        //no name cannot be valid
        if (name == null || name.length() == 0)
            return false;

        //skip anything that is not a class file
        if (!name.toLowerCase(Locale.ENGLISH).endsWith(".class")) {
            if (LOG.isDebugEnabled()) LOG.debug("Not a class: {}", name);
            return false;
        }

        //skip any classfiles that are not a valid java identifier
        int c0 = 0;
        int ldir = name.lastIndexOf('/', name.length() - 6);
        c0 = (ldir > -1 ? ldir + 1 : c0);
        if (!Character.isJavaIdentifierStart(name.charAt(c0))) {
            if (LOG.isDebugEnabled()) LOG.debug("Not a java identifier: {}" + name);
            return false;
        }

        return true;
    }


    /**
     * Check that the given path does not contain hidden directories
     *
     * @param path
     * @return
     */
    private boolean isValidClassFilePath(String path) {
        //no path is not valid
        if (path == null || path.length() == 0)
            return false;

        //skip any classfiles that are in a hidden directory
        if (path.startsWith(".") || path.contains("/.")) {
            if (LOG.isDebugEnabled()) LOG.debug("Contains hidden dirs: {}" + path);
            return false;
        }

        return true;
    }
}

