/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package network;

/**
 * @author manos
 */
public class DiscardNetworkQueue implements NetworkQueue {

    public DiscardNetworkQueue() {

    }

    public void addWork(byte[] m) {
        System.out.println("Dropping bytes on the floor: " + m);
        //do nothing
    }

}
