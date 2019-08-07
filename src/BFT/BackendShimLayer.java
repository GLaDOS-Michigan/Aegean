package BFT;

/*
    This class to use in all modes. The functionality of this class can be summarized as following:
    1) It gets trustable messages (normal message in CFT mode or filtered messages in BFT mode) so it does not have to check
    signatures
    2) It waits until all messages for this batch arrives before forwarding
    3) It checks the hash in the first one of these messages and try to create necessary quorum
    4) If it can create the quorum it forwards the batch of requests to its owner (filter, executor etc)
    5) It will keep the last responses for each client if necessary (For example, it is not necessary in Eve, because the
    execution replicas keep. However, it will keep the last seq id's of clients so won't accept old messages
 */
public class BackendShimLayer {

    public synchronized void receiveRequest() {

    }

}
