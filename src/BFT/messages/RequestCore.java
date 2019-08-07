package BFT.messages;

public interface RequestCore extends ClientMessage {

    /**
     * gets the identifier of the sending client
     **/
    public abstract int getSendingClient();

    /**
     * gets the subId identifier of the sending client
     **/
    public abstract int getSubId();

    /**
     * gets the request identifier/sequence number
     **/
    public abstract long getRequestId();

    /**
     * get the client GC seq id
     */
    public abstract long getGCId();

    /**
     * retrieves the byte representation of the command
     **/
    public abstract byte[] getCommand();

    //	public abstract int getTag();

    public Entry getEntry();

    public abstract byte[] getBytes();

    public abstract int getTotalSize();

}
