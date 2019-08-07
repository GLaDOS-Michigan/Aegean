/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package network;

/**
 * @author manos
 */
public class PassThroughNetworkQueue implements NetworkQueue {

    ByteHandler bn;

    public PassThroughNetworkQueue(ByteHandler _bn) {
        this.bn = _bn;
    }

    public void addWork(byte[] m) {
        bn.handle(m);
    }

}
