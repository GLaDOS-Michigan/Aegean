// $Id: Network.java 67 2010-03-05 21:22:02Z yangwang $


package BFT.network;


public interface Network {


    /**
     * Send m[] to the node identified by role.index
     **/
    public void send(byte[] m, BFT.util.Role role, int index);

    /**
     * Start listening for incoming messages
     **/
    public void start();

    /**
     * Stop Listening for incoming messages
     **/
    public void stop();

}
