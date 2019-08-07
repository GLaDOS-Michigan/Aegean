/*
 *  checked by Allen 2011.04.19
 */

package network;

import java.net.InetSocketAddress;

/**
 * @author manos
 */
public interface NetworkSender {
    abstract public void send(byte[] msg, InetSocketAddress rcpt);

    abstract public void send(byte[] msg, InetSocketAddress rcpt[]);
}

