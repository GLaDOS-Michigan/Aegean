/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package BCI;

/**
 * @author manos
 */
public class IntegerTry {

    public static void main(String[] args) {
        Integer a = new Integer(3);
        /*if(a instanceof MerkleTreeObject) {
            System.out.println("Integer is a subclass of MerkleTreeObject");
        } else {
            System.out.println("Integer is NOT a subclass of MerkleTreeObject");
        }*/
        /*
        Class cls = null;
        try {
            cls = Class.forName("java.lang.Integer");
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(IntegerTry.class.getName()).log(Level.SEVERE, null, ex);
        }
        Object f = null;
        
        Class partypes[] = new Class[1];
        partypes[0] = Integer.TYPE;
        Object arglist[] = new Object[1];
        arglist[0] = new Integer(37);
        Constructor ct = null;
        try {
            ct = cls.getConstructor(partypes);
        } catch (NoSuchMethodException ex) {
            Logger.getLogger(IntegerTry.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SecurityException ex) {
            Logger.getLogger(IntegerTry.class.getName()).log(Level.SEVERE, null, ex);
        }
        try {
            f = ct.newInstance(arglist);
        } catch (InstantiationException ex) {
            Logger.getLogger(IntegerTry.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            Logger.getLogger(IntegerTry.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalArgumentException ex) {
            Logger.getLogger(IntegerTry.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvocationTargetException ex) {
            Logger.getLogger(IntegerTry.class.getName()).log(Level.SEVERE, null, ex);
        }

        if(f instanceof MerkleTreeObject) {
            System.out.println("Integer is a subclass of MerkleTreeObject");
            MerkleTreeObject mto = (MerkleTreeObject)f;
            int b = mto.getMTIdentifier();
            System.out.println("getMTIdentifier() returns "+b);

            mto.setMTIdentifier(37);
            System.out.println("getMTIdentifier() is now "+mto.getMTIdentifier());
        } else {
            System.out.println("Integer is NOT a subclass of MerkleTreeObject");
        }
        */
        //System.out.println("Superclass of String is "+a.getClass().getSuperclass());
        
        /*int b = a.getID();
        System.out.println("isCurrent() returns "+b);

        a.MT_consistentID = 37;
        System.out.println("isCurrent is now "+a.MT_consistentID);

        System.out.println("isModified() is "+a.isModified());
         */
    }

}
