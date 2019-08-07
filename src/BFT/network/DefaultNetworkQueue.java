/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package BFT.network;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author manos
 */
public class DefaultNetworkQueue implements NetworkQueue {

    ConcurrentLinkedQueue messageQueue;

    public DefaultNetworkQueue() {
        messageQueue = new ConcurrentLinkedQueue();
    }

    public void addWork(byte[] m) {
        messageQueue.add(m);
    }

    public byte[] getMessage() {
        return (byte[]) messageQueue.poll();
    }

}
