/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package BFT.util;

import java.net.Socket;
import java.util.Hashtable;

/**
 * @author manos
 */
public class Multiplexor {
    Hashtable<Socket, Integer> sockets;

    public Multiplexor() {
        sockets = new Hashtable<Socket, Integer>();
    }

    public synchronized void add(Socket socket) {
        sockets.put(socket, 0);
    }

    public Hashtable<Socket, Integer> getSockets() {
        return sockets;
    }

}
