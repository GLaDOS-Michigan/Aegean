/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.util;

import org.h2.message.InternalException;

import java.io.*;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;

/**
 * This class allows to convert source code to a class. It uses one class loader
 * per class.
 */
public class SourceCompiler {

    private static final Class<?> JAVAC_SUN;

    /**
     * The class name to source code map.
     */
    HashMap<String, String> sources = New.hashMap();

    /**
     * The class name to byte code map.
     */
    HashMap<String, Class<?>> compiled = New.hashMap();

    private String compileDir = System.getProperty("java.io.tmpdir");

    static {
        Class<?> clazz;
        try {
            clazz = Class.forName("com.sun.tools.javac.Main");
        } catch (Exception e) {
            clazz = null;
        }
        JAVAC_SUN = clazz;
    }

    /**
     * Set the source code for the specified class.
     * This will reset all compiled classes.
     *
     * @param className the class name
     * @param source    the source code
     */
    public void setSource(String className, String source) {
        sources.put(className, source);
        compiled.clear();
    }

    /**
     * Get the class object for the given name.
     *
     * @param packageAndClassName the class name
     * @return the class
     */
    private Class<?> getClass(String packageAndClassName) throws ClassNotFoundException {

        Class<?> compiledClass = compiled.get(packageAndClassName);
        if (compiledClass != null) {
            return compiledClass;
        }

        ClassLoader classLoader = new ClassLoader(getClass().getClassLoader()) {
            public Class<?> findClass(String name) throws ClassNotFoundException {
                Class<?> classInstance = compiled.get(name);
                if (classInstance == null) {
                    String source = sources.get(name);
                    String packageName = null;
                    int idx = name.lastIndexOf('.');
                    String className;
                    if (idx >= 0) {
                        packageName = name.substring(0, idx);
                        className = name.substring(idx + 1);
                    } else {
                        className = name;
                    }
                    byte[] data = javacCompile(packageName, className, source);
                    if (data == null) {
                        classInstance = findSystemClass(name);
                    } else {
                        classInstance = defineClass(name, data, 0, data.length);
                        compiled.put(name, classInstance);
                    }
                }
                return classInstance;
            }
        };
        return classLoader.loadClass(packageAndClassName);
    }

    /**
     * Get the first public static method of the given class.
     *
     * @param className the class name
     * @return the method name
     */
    public Method getMethod(String className) throws ClassNotFoundException {
        Class<?> clazz = getClass(className);
        Method[] methods = clazz.getDeclaredMethods();
        for (Method m : methods) {
            int modifiers = m.getModifiers();
            if (Modifier.isPublic(modifiers)) {
                if (Modifier.isStatic(modifiers)) {
                    return m;
                }
            }
        }
        return null;
    }

    /**
     * Compile the given class. This method tries to use the class
     * "com.sun.tools.javac.Main" if available. If not, it tries to run "javac"
     * in a separate process.
     *
     * @param packageName the package name
     * @param className   the class name
     * @param source      the source code
     * @return the class file
     */
    byte[] javacCompile(String packageName, String className, String source) {
        File dir = new File(compileDir);
        if (packageName != null) {
            dir = new File(dir, packageName.replace('.', '/'));
            try {
                FileUtils.mkdirs(dir);
            } catch (IOException e) {
                throw new InternalException(e);
            }
        }
        File javaFile = new File(dir, className + ".java");
        File classFile = new File(dir, className + ".class");
        try {
            PrintWriter out = new PrintWriter(new FileWriter(javaFile));
            classFile.delete();
            int endImport = source.indexOf("@CODE");
            String importCode = "import java.util.*;\n" +
                    "import java.math.*;\n" +
                    "import java.sql.*;\n";
            if (endImport >= 0) {
                importCode = source.substring(0, endImport);
                source = source.substring("@CODE".length() + endImport);
            }
            if (packageName != null) {
                out.println("package " + packageName + ";");
            }
            out.println(importCode);
            out.println("public class " + className + " {\n" +
                    "    public static " +
                    source + "\n" +
                    "}\n");
            out.close();
            if (JAVAC_SUN != null) {
                javacSun(javaFile);
            } else {
                javacProcess(javaFile);
            }
            byte[] data = new byte[(int) classFile.length()];
            DataInputStream in = new DataInputStream(new FileInputStream(classFile));
            in.readFully(data);
            in.close();
            return data;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            javaFile.delete();
            classFile.delete();
        }
    }

    private void javacProcess(File javaFile) {
        exec("javac",
                "-sourcepath", compileDir,
                "-d", compileDir,
                javaFile.getAbsolutePath());
    }

    private int exec(String... args) {
        ByteArrayOutputStream buff = new ByteArrayOutputStream();
        try {
            Process p = Runtime.getRuntime().exec(args);
            copyInThread(p.getInputStream(), buff);
            copyInThread(p.getErrorStream(), buff);
            p.waitFor();
            byte[] err = buff.toByteArray();
            if (err.length != 0) {
                throw new RuntimeException("Compile error: " + StringUtils.utf8Decode(err));
            }
            return p.exitValue();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void copyInThread(final InputStream in, final OutputStream out) {
        new Thread() {
            public void run() {
                try {
                    while (true) {
                        int x = in.read();
                        if (x < 0) {
                            return;
                        }
                        if (out != null) {
                            out.write(x);
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }.start();
    }

    private void javacSun(File javaFile) {
        PrintStream old = System.err;
        ByteArrayOutputStream buff = new ByteArrayOutputStream();
        PrintStream temp = new PrintStream(buff);
        try {
            System.setErr(temp);
            Method compile = JAVAC_SUN.getMethod("compile", String[].class);
            Object javac = JAVAC_SUN.newInstance();
            compile.invoke(javac, (Object) new String[]{
                    "-sourcepath", compileDir,
                    "-d", compileDir,
                    javaFile.getAbsolutePath()});
            byte[] err = buff.toByteArray();
            if (err.length != 0) {
                throw new RuntimeException("Compile error: " + StringUtils.utf8Decode(err));
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            System.setErr(old);
        }
    }

}
