/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package BFT.network;

import BFT.util.Role;

/**
 * @author manos
 */
public interface NetworkSender {
    abstract public void send(byte[] msg, Role role, int id);

    abstract public void send(byte[] msg, Role role, int id, int subId);

    abstract public void send(byte[] msg, Role role, int[] ids);
}
