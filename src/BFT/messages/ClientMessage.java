// $Id

package BFT.messages;


public interface ClientMessage {
    public abstract int getSendingClient();

    public abstract long getRequestId();

    public int getTag();

    public int getTotalSize();

    public abstract long getGCId();

}
