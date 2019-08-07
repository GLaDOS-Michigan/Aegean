package BFT.generalcp;

import BFT.clientShim.ClientShimBaseNode;

import java.net.InetAddress;

public interface CPAppInterface {

    // The callback of execAsync. Should be called when the application finishes
    // processing the request
    public void execDone(byte[] reply, RequestInfo info);

    public void execReadonlyDone(byte[] reply, int clientId, long requestId);

    public void sendEvent(byte[] event, int clientId, long eventSeqNo);

    //public void flushDone(long seqNo, String fileName);

    public void syncDone(String fileName);

    //public void consumeLogDone(String fileName);

    //public void loadSnapshotDone();

    // Setup the interface for the General CP library.
    public void setupApplication(AppCPInterface app);

    public InetAddress getIP(int clientId, int subId);

    public int getPort(int clientId, int subId);

    byte[] execNestedRequest(byte[] bytes, ClientShimBaseNode[] csbns);
}
