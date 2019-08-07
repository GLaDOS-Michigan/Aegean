// $Id: ClientGlueInterface.java 49 2010-02-26 19:33:49Z yangwang $

package BFT.clientShim;

/**
 * Interface between the application and the BFT plug and play client.
 **/
public interface ClientGlueInterface {

    /**
     * Function called when the connection between the client and the
     * server is determined to be broken.
     **/
    public void brokenConnection();

    /**
     * Returns a reply received from the server
     **/
    public void returnReply(byte[] reply);

    /**
     * Considers the set of possible replies options.  Returns a
     * canonical version of those replies if it exists, returns null
     * otherwise
     **/
    public byte[] canonicalEntry(byte[][] options);


}

