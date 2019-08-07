/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package BCI;

import javassist.*;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author manos
 */
public class MessWithClass {

    public static void addMethods(CtClass cClass) throws NotFoundException, CannotCompileException, IOException {
        System.out.println("********************************************************");
        System.out.println("* Messing with the given Class. Adding methods         *");
        System.out.println("********************************************************");

        //CtBehavior[] behaviors = cClass.getDeclaredBehaviors();
        //for(int i=0; i<behaviors.length; i++) {
        //    System.out.println(behaviors[i].getLongName());
        //}
        try {
            cClass.getDeclaredMethod("getMTIdentifier");
            System.out.println("getMTIdentifier() is already declared in " + cClass.getName());
        } catch (NotFoundException e) {
            CtMethod m = CtNewMethod.make(
                    "public int getMTIdentifier() { return MTIdentifier; }",
                    cClass);
            cClass.addMethod(m);

            System.out.println("getMTIdentifier() was not declared, I added it now");
        }

        try {
            cClass.getDeclaredMethod("setMTIdentifier");
            System.out.println("setMTIdentifier() is already declared in " + cClass.getName());
        } catch (NotFoundException e) {
            CtMethod m = CtNewMethod.make(
                    "public void setMTIdentifier(int identifier) { this.MTIdentifier = identifier; }",
                    cClass);
            cClass.addMethod(m);

            System.out.println("setMTIdentifier() was not declared, I added it now");
        }
    }

    public static void addID(CtClass cClass) throws NotFoundException, CannotCompileException, IOException {
        System.out.println("************************************************************");
        System.out.println("* Messing with the given Class. Adding MTIdentifier        *");
        System.out.println("************************************************************");

        //CtBehavior[] behaviors = cClass.getDeclaredBehaviors();
        //for(int i=0; i<behaviors.length; i++) {
        //    System.out.println(behaviors[i].getLongName());
        //}
        try {
            cClass.getDeclaredField("MTIdentifier");
            System.out.println("MTIdentifier is already declared in " + cClass.getName());
        } catch (NotFoundException e) {
            CtField f = CtField.make(
                    "private int MTIdentifier = -1; }",
                    cClass);
            cClass.addField(f);

            System.out.println("The field 'private int MTIdentifier' was not declared, I added it now");
        }
    }

    public static void addIsModified(CtClass cClass) throws NotFoundException, CannotCompileException, IOException {
        System.out.println("********************************************************");
        System.out.println("* Messing with the given Class. Adding isModified()    *");
        System.out.println("********************************************************");

        //CtBehavior[] behaviors = cClass.getDeclaredBehaviors();
        //for(int i=0; i<behaviors.length; i++) {
        //    System.out.println(behaviors[i].getLongName());
        //}
        //try {
        //    cClass.getDeclaredMethod("isCurrent");
        //    System.out.println("isCurrent() is already declared in "+cClass.getName());
        //} catch (NotFoundException e) {
        CtMethod m = CtNewMethod.make(
                "public boolean isModified() { return true; }",
                cClass);
        cClass.addMethod(m);

        System.out.println("Added isModified()");
        //System.out.println("The method was not declared, I added it now");
        //}
    }

    public static void replaceArrayCopy(CtClass cClass) {
        System.out.println("********************************************************");
        System.out.println("* Messing with the given Class. Adding isModified()    *");
        System.out.println("********************************************************");


    }


    public static void instrument(String className) {
        System.out.println("Instrumenting class " + className);
        ClassPool pool = ClassPool.getDefault();
        pool.importPackage("merkle");

        try {
            CtClass cClass = pool.get(className);
            CtClass newInterface = pool.get("merkle.MerkleTreeObject");
            //cClass.addInterface(superClass);
            addID(cClass);
            addMethods(cClass);
            addInterface(cClass, newInterface);
            //addGetID(cClass);
            //addIsModified(cClass);
            cClass.writeFile(".");
        } catch (NotFoundException ex) {
            Logger.getLogger(MessWithClass.class.getName()).log(Level.SEVERE, null, ex);
        } catch (CannotCompileException ex) {
            Logger.getLogger(MessWithClass.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(MessWithClass.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static void changeParentClass(String child, String newParent) {
        System.out.println("Instrumenting class " + child);
        ClassPool pool = ClassPool.getDefault();
        pool.importPackage("merkle");

        try {
            CtClass cClass = pool.get(child);
            CtClass superClass = pool.get(newParent);
            System.out.println("Setting superclass of " + cClass.getName() + " to " + superClass.getName());
            cClass.setSuperclass(superClass);
            //addID(cClass);
            //addGetID(cClass);
            //addIsModified(cClass);
            cClass.writeFile(".");
        } catch (NotFoundException ex) {
            Logger.getLogger(MessWithClass.class.getName()).log(Level.SEVERE, null, ex);
        } catch (CannotCompileException ex) {
            Logger.getLogger(MessWithClass.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(MessWithClass.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static void addInterface(CtClass cClass, CtClass newInterface) {
        System.out.println("Adding interface " + newInterface.getName() + " to " + cClass.getName());
        cClass.addInterface(newInterface);
    }


    public static void main(String[] args) {
        /*if(args.length < 2) {
            System.out.println("Usage: java BCI.MessWithClass <childClass> <newParentClass>");
            return;
        }
        //changeParentClass(args[0], args[1]);
        //addInterface(args[0], args[1]);
        */


        if (args.length < 1) {
            System.out.println("Usage: java BCI.MessWithClass <fullClassName> [<fullClassName2> ...]");
            return;
        }
        for (int i = 0; i < args.length; i++) {
            instrument(args[i]);
        }
    }

}
