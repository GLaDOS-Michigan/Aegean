// $Id: ChangeRequest.java 373 2010-05-06 02:02:03Z glc $


package BFT.network;

import java.nio.channels.SocketChannel;

public class ChangeRequest {
    public static final int REGISTER = 1;
    public static final int CHANGEOPS = 2;

    public SocketChannel socket;
    public int type;
    public int ops;

    public ChangeRequest(SocketChannel socket, int type, int ops) {
        this.socket = socket;
        this.type = type;
        this.ops = ops;
    }
} 
