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
public class MessWithObject {

    public static void instrument() throws NotFoundException, CannotCompileException, IOException {
        ClassPool pool = ClassPool.getDefault();

        System.out.println("********************************************************");
        System.out.println("* Messing with java.lang.Object. Adding isCurrent()    *");
        System.out.println("********************************************************");
        CtClass cObject = pool.get("java.lang.Object");
        CtBehavior[] behaviors = cObject.getDeclaredBehaviors();
        for (int i = 0; i < behaviors.length; i++) {
            System.out.println(behaviors[i].getLongName());
        }
        try {
            cObject.getDeclaredMethod("isModified");
            System.out.println("isModified() is already declared in Object");
        } catch (NotFoundException e) {
            //CtField f = CtField.make("private boolean isCurrent = false;", cObject);
            //cObject.addField(f);
            CtMethod m = CtNewMethod.make(
                    "public boolean isModified() { return false; }",
                    cObject);
            cObject.addMethod(m);

            System.out.println("The method is not declared, I will add it now");

            cObject.writeFile(".");
        }
    }

    public static void main(String[] args) {
        try {
            instrument();
        } catch (NotFoundException ex) {
            Logger.getLogger(MessWithObject.class.getName()).log(Level.SEVERE, null, ex);
        } catch (CannotCompileException ex) {
            Logger.getLogger(MessWithObject.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(MessWithObject.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}
