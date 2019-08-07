// $Id: DataEvent.java 373 2010-05-06 02:02:03Z glc $

package BFT.network;

import java.nio.channels.SocketChannel;


public class DataEvent {
    public SocketChannel socket;
    public byte[] data;

    public DataEvent(SocketChannel socket, byte[] data) {
        this.socket = socket;
        this.data = data;
    }
}
