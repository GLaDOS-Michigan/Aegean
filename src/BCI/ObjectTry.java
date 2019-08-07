/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package BCI;

/**
 * @author manos
 */
public class ObjectTry {

    public static void main(String[] args) throws ClassNotFoundException, InstantiationException, IllegalAccessException {
        //Object a = new Object();
        //String b = new String();

        Class cls = Class.forName("BCI.org-h2-init");
        Object a = cls.newInstance();

        //System.out.println("Object.isModified() : "+a.isModified());
        //System.out.println("String.isModified() : "+b.isModified());
        //boolean b = a.isCurrent();
        //System.out.println("isCurrent() returns "+b);

        //a.isCurrent = true;
        //System.out.println("isCurrent is now "+a.isCurrent);
    }

}
