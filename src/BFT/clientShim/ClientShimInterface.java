// $Id: ClientShimInterface.java 49 2010-02-26 19:33:49Z yangwang $

package BFT.clientShim;

/**
 * Interface between the application and the BFT plug and play client.
 **/
public interface ClientShimInterface {
    /**
     * returns the result of execution operation at the server.
     **/
    public byte[] execute(byte[] operation);


    public byte[] executeReadOnlyRequest(byte[] op);

    public void enqueueReadOnlyRequest(byte[] op);

    public void enqueueRequest(byte[] operation);

}

