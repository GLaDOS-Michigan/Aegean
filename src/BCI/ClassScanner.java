/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package BCI;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author manos
 */
public class ClassScanner {
    /**
     * Scans all classes accessible from the context class loader which belong to the given package and subpackages.
     *
     * @param packageName The base package
     * @return The classes
     * @throws ClassNotFoundException
     * @throws IOException
     */
    private static Class[] getClasses(String packageName)
            throws ClassNotFoundException, IOException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        assert classLoader != null;
        String path = packageName.replace('.', '/');
        Enumeration<URL> resources = classLoader.getResources(path);
        List<File> dirs = new ArrayList<File>();
        //System.out.println(resources.hasMoreElements());
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            dirs.add(new File(resource.getFile()));
        }
        ArrayList<Class> classes = new ArrayList<Class>();
        for (File directory : dirs) {
            classes.addAll(findClasses(directory, packageName));
        }
        return classes.toArray(new Class[classes.size()]);
    }

    /**
     * Recursive method used to find all classes in a given directory and subdirs.
     *
     * @param directory   The base directory
     * @param packageName The package name for classes found inside the base directory
     * @return The classes
     * @throws ClassNotFoundException
     */
    private static List<Class> findClasses(File directory, String packageName) throws ClassNotFoundException {
        List<Class> classes = new ArrayList<Class>();
        if (!directory.exists()) {
            System.out.println("Directory " + directory.toString() + " doesnt exist");
            return classes;
        }
        try {
            if (directory.getCanonicalPath().endsWith("org/h2/build")) {
                System.out.println("Skipping org.h2.build");
                return classes;
            }
            if (directory.getCanonicalPath().endsWith("org/h2/test")) {
                System.out.println("Skipping org.h2.test");
                return classes;
            }
        } catch (IOException ex) {
            Logger.getLogger(ClassScanner.class.getName()).log(Level.SEVERE, null, ex);
        }
        File[] files = directory.listFiles();

        for (File file : files) {
            if (file.isDirectory()) {
                /*try {
                    System.out.println("dir: " + file.getCanonicalPath());
                } catch (IOException ex) {
                    Logger.getLogger(ClassScanner.class.getName()).log(Level.SEVERE, null, ex);
                }*/
                assert !file.getName().contains(".");
                try {
                    classes.addAll(findClasses(file, packageName + "." + file.getName()));
                } catch (VerifyError ve) {
                    System.out.println("VerifyError");
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            } else if (file.getName().endsWith(".class")) {
                try {
                    //fd
                    //System.out.println(packageName + '.' + file.getName());
                    Class cc = Class.forName(packageName + '.' + file.getName().substring(0, file.getName().length() - 6));
                    classes.add(cc);
                } catch (Exception ex) {
                    System.out.println("EXCEPTION!!");
                    //ex.printStackTrace();
                }
            }
        }
        return classes;
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java BCI.ClassScanner <packageName>");
            return;
        }
        try {
            String packageName = args[0];
            System.out.println("Scanning package " + args[0]);
            Class[] classes = getClasses(args[0]);
            System.out.println("Read " + classes.length + " classes");

            for (int i = 0; i < classes.length; i++) {
                if (classes[i].getName().endsWith("TableData$1")) {
                    System.out.println("Half-instrumenting " + classes[i]);
                    InstrumentClass.instrument(classes[i], false, false, false);
                } else {
                    System.out.println("Instrumenting " + classes[i]);
                    InstrumentClass.instrument(classes[i], true, true, true);
                }
            }

            ArrayList<String> listOfStaticClasses = InstrumentClass.staticClasses;
            //String source = "System.out.println(\"It prints something\");";
            String source = "";
            for (int i = 0; i < listOfStaticClasses.size(); i++) {
                //System.out.println("Classes with static field(s): "+listOfStaticClasses.get(i));
                //source += "MerkleTreeInstance.addStatic("+listOfStaticClasses.get(i)+");";
                source += "MerkleTreeInstance.addStatic(Class.forName(\"" + listOfStaticClasses.get(i) + "\"));";
                //source += "MerkleTreeInstance.addStatic(this);";

            }

            source = "{" + source + "}";

            System.out.println("Source code:");
            System.out.println(source);
            System.out.println();

            ClassPool pool = ClassPool.getDefault();
            pool.importPackage("merkle");
            pool.importPackage("merkle.wrapper");
            pool.importPackage("BCI");
            pool.importPackage("java.lang.reflect");
            String legalPackageName = "BCI." + packageName.replace('.', '-') + "-init";
            System.out.println("New class name will be " + legalPackageName);
            CtClass newClass = pool.makeClass(legalPackageName);
            CtClass[] parameters = new CtClass[0];
            CtConstructor empty = new CtConstructor(parameters, newClass);
            newClass.addConstructor(empty);
            newClass.getConstructors()[0].setBody(source);
            newClass.writeFile("build");
        } catch (ClassNotFoundException ex) {
            ex.printStackTrace();
            //Logger.getLogger(ClassScanner.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            ex.printStackTrace();
            //Logger.getLogger(ClassScanner.class.getName()).log(Level.SEVERE, null, ex);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
