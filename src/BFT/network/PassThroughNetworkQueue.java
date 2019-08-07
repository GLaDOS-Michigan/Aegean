/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package BFT.network;

import BFT.BaseNode;

/**
 * @author manos
 */
public class PassThroughNetworkQueue implements NetworkQueue {

    BaseNode bn;

    public PassThroughNetworkQueue(BaseNode _bn) {
        this.bn = _bn;
    }

    public void addWork(byte[] m) {
        bn.handle(m);
    }

}
