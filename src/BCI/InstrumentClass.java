/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package BCI;

import javassist.*;
import javassist.bytecode.*;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;
import javassist.expr.MethodCall;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author manos
 */
public class InstrumentClass {

    static ArrayList<String> staticClasses = new ArrayList<String>();

    public static void instrument(String classString) throws Exception {
        ClassPool pool = ClassPool.getDefault();
        pool.importPackage("merkle");
        pool.importPackage("merkle.wrapper");
        CtClass cc = pool.get(classString);
        InstrumentClass.instrument(cc);
    }

    public static void instrument(Class clazz, boolean doStatic,
                                  boolean doFieldAccess, boolean doSystemCalls) throws Exception {
        ClassPool pool = ClassPool.getDefault();
        pool.importPackage("merkle");
        pool.importPackage("merkle.wrapper");
        CtClass cc = pool.get(clazz.getName());
        InstrumentClass.instrument(cc, doStatic, doFieldAccess, doSystemCalls);
    }

    public static void instrument(Class clazz) throws Exception {
        instrument(clazz, true, true, true);
    }

    public static void instrument(CtClass cc) throws Exception {
        instrument(cc, true, true, true);
    }

    public static void instrument(CtClass cc, boolean doStatic,
                                  boolean doFieldAccess, boolean doSystemCalls) throws Exception {

        CtField[] flds = cc.getDeclaredFields();
        for (int i = 0; i < flds.length; i++) {
            CtField fld = flds[i];
            int mods = fld.getModifiers();
            if (Modifier.isPublic(mods) && !Modifier.isFinal(mods)) {
                String isStatic = Modifier.isStatic(mods) ? " static" : "";
                System.out.println("Class " + cc.getName() + isStatic + " public non-final field: " + fld.getName());
            }
        }
        if (true) return;
        boolean bypass = false;
        if (cc.getName().endsWith("ObjectArrayIterator")) {
            System.out.println("Skipping ObjectArrayIterator");
            bypass = true;
        }
        if (doStatic && !bypass) {
            //System.out.println("********************************************************");
            //System.out.println("* Searching for non-final static fields                *");
            //System.out.println("********************************************************");
            CtField[] fields = cc.getDeclaredFields();
            boolean found = false;
            for (int i = 0; i < fields.length && !found; i++) {
                int modifiers = fields[i].getModifiers();
                if (Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers)) {
                    found = true;
                    staticClasses.add(cc.getName());
                    //System.out.println("Class "+cc.getName()+": found a non-final static field "+fields[i].getName());
                }
            }
        }

        ClassPool pool = ClassPool.getDefault();
        pool.importPackage("merkle");
        pool.importPackage("merkle.wrapper");
        CtClass mtocc = pool.get("merkle.MerkleTreeObject");

        CtClass[] interfaces = cc.getInterfaces();
        for (int i = 0; i < interfaces.length; i++) {
            if (interfaces[i].subclassOf(mtocc)) {
                bypass = true;
            }
        }
        //if(true) return;
        if (doFieldAccess && !bypass) {

            cc.instrument(
                    new ExprEditor() {
                        public void edit(FieldAccess f) throws CannotCompileException {

                            //System.out.println("Found an access on class "+f.getClassName()+
                            //        " and field "+f.getFieldName()+
                            //        " in location "+f.where());

                            CtField fld = null;
                            boolean isArray = false;
                            boolean foundStore = false;
                            try {
                                fld = f.getField();
                                if (fld.getType().isArray()) {
                                    //System.out.println(f.getFileName() + " " + f.getLineNumber());
                                    isArray = true;
                                }
                            } catch (NotFoundException ex) {
                                Logger.getLogger(InstrumentClass.class.getName()).log(Level.SEVERE, null, ex);
                            }
                            if (isArray) {
                                int index = f.indexOfBytecode();
                                MethodInfo minfo = f.where().getMethodInfo();
                                CodeAttribute ca = minfo.getCodeAttribute();
                                CodeIterator ci = ca.iterator();
                                int length = ci.getCodeLength();
                                //System.out.println("length = "+length);
                                int op = 0;
                                //System.out.println("\nStarting count for field "+fld.getName()+" at index "+index+
                                //        " in source line "+f.getLineNumber());
                                for (int i = 0; i < 30; i++) {
                                    if ((index + i) >= length) {
                                        break;
                                    }
                                    op = ci.byteAt(index + i);
                                    //System.out.println("Opcode "+op+" Mnemonic "+Mnemonic.OPCODE[op]+" at index "+(index+i)+" offset "+i);
                                    if (op == Opcode.AASTORE || op == Opcode.BASTORE ||
                                            op == Opcode.CASTORE || op == Opcode.DASTORE ||
                                            op == Opcode.LASTORE || op == Opcode.SASTORE ||
                                            op == Opcode.FASTORE || op == Opcode.IASTORE) {
                                        System.out.println(Mnemonic.OPCODE[op] + " found at index " + (index + i) + " and offset " + i +
                                                " for field " + fld.getName() + " in source line " + f.getLineNumber());
                                        foundStore = true;
                                        break;
                                    }
                                }
                            }
                            //foundStore = true;
                            if (!f.isWriter() && !isArray) {
                                return;
                            } else if (isArray && !foundStore) {
                                return;
                            }

                            if (f.where().getMethodInfo().isStaticInitializer()) {
                                return;
                            }

                            System.out.println("Found a write access on class " + f.getClassName() +
                                    " and field " + f.getFieldName() +
                                    " in location " + f.where());

                            if (f.getFieldName().equalsIgnoreCase("MTIdentifier")) {
                                System.out.println("Skipping MTIdentifier");
                                return;
                            }

                            if (f.getFieldName().equalsIgnoreCase("this$0")) {
                                System.out.println("Skipping this$0");
                                return;
                            }

                            try {
                                CtField field = f.getField();

                                //System.out.println("IsStaticInit: "+);

                                //Class myAnn = MyAnnotation.class;
                                //CtBehavior where = f.where();
                                //if(where instanceof CtMethod) {
                                //System.out.println("The field is not in a constructor, will replace");

                                if (f.isStatic()) {
                                    if (Modifier.isFinal(f.getField().getModifiers())) {
                                        System.out.println("The field is final, skipping");
                                        return;
                                    }
                                    System.out.println("The field is non-final and static, doing updateStatic on it");
                                    if (f.isReader()) {
                                        if (field.getType().isPrimitive()) {
                                            System.out.println("Primitive type");
                                            String s = "{" +
                                                    "if(MerkleTreeInstance.get().getIndex(Class.forName(\"" + field.getDeclaringClass().getName() + "\")) > 0) {" +
                                                    //"MerkleTreeInstance.updateStatic("+field.getName()+");" +
                                                    "MerkleTreeInstance.updateStatic(Class.forName(\"" + field.getDeclaringClass().getName() + "\"));" +
                                                    "}" +
                                                    "$_ = $proceed($$);" +
                                                    "}";
                                            System.out.println(s);
                                            f.replace(s);
                                        } else {
                                            System.out.println("Reference type");
                                            String s = "{" +
                                                    "if(MerkleTreeInstance.get().getIndex(" + field.getName() + ") > 0) {" +
                                                    //"MerkleTreeInstance.updateStatic("+field.getName()+");" +
                                                    "MerkleTreeInstance.updateStatic(Class.forName(\"" + field.getDeclaringClass().getName() + "\"));" +
                                                    "}" +
                                                    "$_ = $proceed($$);" +
                                                    "}";
                                            System.out.println(s);
                                            f.replace(s);
                                        }
                                    } else {
                                        if (field.getType().isPrimitive()) {
                                            System.out.println("Primitive type");
                                            String s = "{" +
                                                    "if(MerkleTreeInstance.get().getIndex(Class.forName(\"" + field.getDeclaringClass().getName() + "\")) > 0) {" +
                                                    //"MerkleTreeInstance.updateStatic("+field.getName()+");" +
                                                    "MerkleTreeInstance.updateStatic(Class.forName(\"" + field.getDeclaringClass().getName() + "\"));" +
                                                    "}" +
                                                    "$proceed($$);" +
                                                    "}";
                                            System.out.println(s);
                                            f.replace(s);
                                        } else {
                                            System.out.println("Reference type");
                                            String s = "{" +
                                                    "if(MerkleTreeInstance.get().getIndex(" + field.getName() + ") > 0) {" +
                                                    //"MerkleTreeInstance.updateStatic("+field.getName()+");" +
                                                    "MerkleTreeInstance.updateStatic(Class.forName(\"" + field.getDeclaringClass().getName() + "\"));" +
                                                    "}" +
                                                    "$proceed($$);" +
                                                    "}";
                                            System.out.println(s);
                                            f.replace(s);
                                        }

                                    }

                                } else {
                                    if (f.isReader()) {
                                        if (f.where().getMethodInfo().isConstructor()) {
                                            System.out.println("Instrumenting " + field.getName() + " in constructor");
                                            f.replace("{" +
                                                    //"System.out.println(\""+field.getName()+", containing object=\"+$0+\" \"+MerkleTreeInstance.get().getIndex($0));"+
                                                    "if(this!=$0) {" +
                                                    "if(MerkleTreeInstance.get().getIndex($0) > 0) {" +
                                                    "MerkleTreeInstance.update($0);" +
                                                    "}" +
                                                    "}" +
                                                    //"System.out.println(\"here\");" +
                                                    "$_ = $proceed($$);" +
                                                    "}");
                                        } else {
                                            System.out.println("Instrumenting " + field.getName());
                                            f.replace("{" +
                                                    //"System.out.println(\""+field.getName()+", containing object=\"+$0+\" \"+MerkleTreeInstance.get().getIndex($0));"+
                                                    "if(MerkleTreeInstance.get().getIndex($0) > 0) {" +
                                                    //"MerkleTreeInstance.update($0);" +
                                                    "MerkleTreeInstance.update($0);" +
                                                    "}" +
                                                    //"System.out.println(\"here\");" +
                                                    "$_ = $proceed($$);" +
                                                    "}");
                                        }
                                    } else {
                                        System.out.println("The field is non-static, updating containing object");
                                        if (f.where().getMethodInfo().isConstructor()) {
                                            System.out.println("Instrumenting " + field.getName() + " in constructor");
                                            f.replace("{" +
                                                    //"System.out.println(\""+field.getName()+", containing object=\"+$0+\" \"+MerkleTreeInstance.get().getIndex($0));"+
                                                    "if(this!=$0) {" +
                                                    "if(MerkleTreeInstance.get().getIndex($0) > 0) {" +
                                                    "MerkleTreeInstance.update($0);" +
                                                    "}" +
                                                    "}" +
                                                    //"System.out.println(\"here\");" +
                                                    "$proceed($$);" +
                                                    "}");
                                        } else {
                                            System.out.println("Instrumenting " + field.getName());
                                            f.replace("{" +
                                                    //"System.out.println(\""+field.getName()+", containing object=\"+$0+\" \"+MerkleTreeInstance.get().getIndex($0));"+
                                                    "if(MerkleTreeInstance.get().getIndex($0) > 0) {" +
                                                    //"MerkleTreeInstance.update($0);" +
                                                    "MerkleTreeInstance.update($0);" +
                                                    "}" +
                                                    //"System.out.println(\"here\");" +
                                                    "$proceed($$);" +
                                                    "}");
                                        }
                                    }
                                }
                                //} else if(where instanceof CtConstructor) {
                                //    System.out.println("I will not do anything for this access, as it is inside a constructor");
                                //} else {
                                //    System.out.println("The field is somewhere else: "+where);
                                //}

                            } catch (NotFoundException ex) {
                                ex.printStackTrace();
                            }
                        }
                    }
            );
        }

        if (doSystemCalls && !bypass) {
            cc.instrument(
                    new ExprEditor() {
                        public void edit(MethodCall m) throws CannotCompileException {
                            if (m.getClassName().equals("java.lang.System") &&
                                    m.getMethodName().equalsIgnoreCase("arraycopy")) {
                                System.out.println("found an access to System.arraycopy()");
                                m.replace("{" +
                                        "SystemWrapper.arraycopy($1,$2,$3,$4,$5);" +
                                        "}");
                            }
                        }
                    }
            );
        }

        cc.writeFile("build");
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java BCI.InstrumentClass <className>");
            return;
        }

        File dir1 = new File(".");
        try {
            System.out.println("Current dir : " + dir1.getCanonicalPath());
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("Class to instrument: " + args[0]);

        ClassLoader sysClassLoader = ClassLoader.getSystemClassLoader();

        //Get the URLs
        URL[] urls = ((URLClassLoader) sysClassLoader).getURLs();

        for (int i = 0; i < urls.length; i++) {
            System.out.println(urls[i].getFile());
        }


        final ClassPool pool = ClassPool.getDefault();
        pool.importPackage("merkle");
        pool.importPackage("merkle.wrapper");
        CtClass cc = pool.get(args[0]);
        InstrumentClass.instrument(cc);
    }
}
