// $ Id $

package BFT.serverShim;

import java.net.InetAddress;


/**
 * Interface implemented by the Server Shim and exported to the application.
 **/
public interface ServerShimInterface {

    /**
     * Upcall that delivers the result of executing clientId's
     * reqId^th request at seqNo position in the sequence to the shim.
     **/
    public void result(byte[] result, int clientId, long reqId,
                       long seqNo, boolean toCache);


    /**
     * Upcall delivering the result of executing clientId's reqId^th
     * read only request.
     **/
    public void readOnlyResult(byte[] result, int clientId, long reqId);

    /**
     * Upcall delivering the Application checkpoint token cpToken
     * taken at batch number seqNo to the shim
     **/
    public void returnCP(byte[] AppCPToken, long seqNo);

    /**
     * Upcall delivering the application state corresponding to
     * stateToken to the shim
     **/
    public void returnState(byte[] stateToken, byte[] state);

    /**
     * Upcall request application state described by stateToken from
     * the shim
     **/
    public void requestState(byte[] stateToken);

    /**
     * Upcall indicating that requests can be processed again
     **/
    public void readyForRequests();


    /**
     * called when the glue cant handle anymore requests!
     **/
    public void noMoreRequests();

    /**
     * Return the inetaddress and port of the specified client
     **/
    public InetAddress getIP(int clientId, int subId);

    public int getPort(int clientId, int subId);

}
