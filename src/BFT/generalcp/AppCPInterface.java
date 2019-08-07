package BFT.generalcp;

public interface AppCPInterface {

    // Execute the request. This is a non-blocking call. The application should
    // call execDone in CPAppInterface when it finishes the request.
    // Info contains all the information about the request, including seqNo,
    // clientId, etc
    public void execAsync(byte[] request, RequestInfo info);

    public void execReadonly(byte[] request, int clientId, long requestId);

    // Read the state from the snapshot file. Blocking call.
    public void loadSnapshot(String fileName);

    // write the whole state into the snapshot file
    public void sync();

}
