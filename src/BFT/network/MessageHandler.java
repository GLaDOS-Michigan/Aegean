// $Id: MessageHandler.java 67 2010-03-05 21:22:02Z yangwang $

package BFT.network;

public interface MessageHandler {
    public void handle(byte[] bytes);
}
