// $Id$

package BFT.serverShim;

import BFT.messages.CommandBatch;
import BFT.messages.NonDeterminism;

/**
 * Interface to be implemented by the application glue.
 * <p>
 * All functions in the interface must be implemented in a thread safe fashion.
 **/
public interface GlueShimInterface {

    /**
     * Execute the commands in batch with associated order sequence
     * number seqNo and using time for any non-determinism
     * <p>
     * calls ServerShimInterface.result() for each request in the batch.
     **/
    public void exec(CommandBatch batch, long seqNo,
                     NonDeterminism time, boolean takeCP);


    /**
     * execute operation as a readonly request for client id at read
     * only id number reqid
     **/
    public void execReadOnly(int clientId, long reqId, byte[] operation);

    /**
     * load the Application checkpoint indicated by cpToken
     * <p>
     * returns true if the checkpoint is successfully loaded
     * returns false otherwise
     * <p>
     * Should not return until all 'in flight' replies are cleared and
     * will not be delivered to the shim.  Return is a confirmation
     * that no 'old' replies will make it back to the shim.
     **/
    public void loadCP(byte[] appCPToken, long seqNo);

    /**
     * release the checkpoint associated with cpToken
     **/
    public void releaseCP(byte[] appCPToken);

    /**
     * fetch the state corresponding to stateToken
     * <p>
     * calls ServerShimInterface.returnState() when the state is ready
     * to be delivered to the shim
     **/
    public void fetchState(byte[] stateToken);

    /**
     * load state corresponding to stateToken into the appropriate
     * location
     **/
    public void loadState(byte[] stateToken, byte[] State);


}