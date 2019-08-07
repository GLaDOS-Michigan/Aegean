/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package BCI;

import javassist.*;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

/**
 * @author manos
 */
public class ParseClass {

    public static void parse(CtClass cc) throws Exception {

        System.out.println("********************************************************");
        System.out.println("* Searching for all method calls                       *");
        System.out.println("********************************************************");

        cc.instrument(
                new ExprEditor() {
                    public void edit(MethodCall m) throws CannotCompileException {
                        System.out.println("Method call: " + m.getClassName() + "." + m.getMethodName() + " in " + m.where().getName());
                    }
                }
        );
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java BCI.ParseClass <className>");
            return;
        }

        final ClassPool pool = ClassPool.getDefault();
        CtClass cc = pool.get(args[0]);
        pool.importPackage(cc.getPackageName());
        ParseClass.parse(cc);

    }
}
