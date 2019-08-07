/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package BCI;

/**
 * @author manos
 */
public class TestArrayCopy {
    public static void main(String[] args) {
        byte[] a = new byte[10];
        byte[] b = new byte[10];
        System.arraycopy(b, 0, b, 0, 5);
    }
}
