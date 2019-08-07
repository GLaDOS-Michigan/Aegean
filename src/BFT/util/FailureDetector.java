/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package BFT.util;

/**
 * @author manos
 */
public class FailureDetector {
    int primary = 0;
    int dead = -1;

    public int getPrimary() {
        return primary;
    }

    public int getDead() {
        return dead;
    }
}
