package BFT.clientShim;

import BFT.BaseNode;
import BFT.Debug;
import BFT.clientShim.messages.CPStateMessage;
import BFT.clientShim.messages.FetchCheckPointState;
import BFT.clientShim.messages.MessageTags;
import BFT.clientShim.statemanagement.CheckPointState;
import BFT.exec.ExecBaseNode;
import BFT.messages.*;
import BFT.util.Role;
import util.ThreadMonitor;

import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// outgoing messages
// incoming messages
//import BFT.clientShim.MessageFactory;

public class ClientShimBaseNode extends BaseNode implements ClientShimInterface {

    transient protected long seqNo = 1;
    transient protected long readOnlySeqno = 1;
    transient protected HashMap<Long, Quorum<Reply>> pipedReplies;
    transient protected Quorum<WatchReply> watchReplies;

    transient protected Quorum<OldRequestMessage> oldRequests;
    transient protected ClientGlueInterface glue;
    transient protected int resendBase;
    transient protected int readQuorumSize;
    transient protected int pendingRequestCount;
    transient protected int requestThreshold;

    transient private int primary = 0;
    transient private int blockingFlag = 0;

    transient protected Hashtable<Long, Request> activeRequests;
    transient protected Hashtable<Long, ReadOnlyRequest> activeReadOnlyRequests;
    transient protected Hashtable<Long, Quorum<ReadOnlyReply>> readOnlyReplies;
    transient protected Hashtable<Long, Quorum<Reply>> replySet;

    transient protected CheckPointState workingState;
    transient protected Hashtable<Long, CheckPointState> stateSnapshots;

    // seqNo that contains Digest info
    // FIXME: may be we should seperate pipereqs which across CP
    transient protected long seqDigest;
    // Digest info sending for filter node
    transient protected Digest stateDigest;
    transient protected long digestId;
    transient protected Digest loadStateDigest;

    // if currently i am using CP to fecth reply
    transient protected boolean readyForRequests;
    // if loading CP is done
    transient protected boolean doneLoadingCP;
    // seqNo to be loaded from CP
    transient protected long loadCPSeqNo;
    // called by echo, indicates to remove replies no longer needed
    transient protected long nextGC;

    // Logging pool 
    transient private ExecutorService pool = null;
    transient public Random random;


    transient protected long readRetrans = 1000;
    transient protected long baseRetrans = 4000;//2000000;
    public transient byte[] copyReplyFromSecondService = null;
    transient ExecBaseNode singletonEBN;

    public ClientShimBaseNode(String membership, int myId) {
        this(membership, myId, 0);
    }

    public ClientShimBaseNode(String membership, int myId, int subId) {
        super(membership, Role.CLIENT, myId, subId);
        if (parameters.linearizeReads) {
            readQuorumSize = parameters.linearizedExecutionQuorumSize();
        } else {
            readQuorumSize = parameters.rightExecutionQuorumSize();
        }
        // do whatever else is necessary
        // What would that be? -Michael

        pipedReplies = new HashMap<Long, Quorum<Reply>>();
        activeRequests = new Hashtable<Long, Request>();
        activeReadOnlyRequests = new Hashtable<Long, ReadOnlyRequest>();
        readOnlyReplies = new Hashtable<Long, Quorum<ReadOnlyReply>>();
        replySet = new Hashtable<Long, Quorum<Reply>>();


        watchReplies = new Quorum<WatchReply>(parameters.getExecutionCount(),
                parameters.rightExecutionQuorumSize(),
                0);

        Random rand = new Random(System.currentTimeMillis());
        int resendBase;
        pendingRequestCount = 0;

        resendBase = rand.nextInt(parameters.getExecutionCount());
        requestThreshold = parameters.getConcurrentRequestLimit();

        workingState = new CheckPointState(parameters);
        stateSnapshots = new Hashtable<Long, CheckPointState>();
        loadCPSeqNo = 0;
        readyForRequests = true;
        doneLoadingCP = false;
        nextGC = 0;
        stateDigest = null;
        digestId = 0;
        seqDigest = 0;
        oldRequests = new Quorum<OldRequestMessage>(parameters.getFilterCount(),
                parameters.smallFilterQuorumSize(), 0);

        pool = Executors.newCachedThreadPool();

        /**
         AGC: 7/29/10: note that the client should *really* record
         each request to disk (2 file circular queue, each
         containing one request is sufficient).  on start up, the
         client should read the request from disk and reset all
         local properties (i.e. seqno) approrpiately.
         **/
        random = new Random(myId);
        Debug.info(Debug.MODULE_EXEC, "CSBN: done!");
        if(parameters.batchSuggestion && null == ExecBaseNode.singletonExec) {
            parameters.batchSuggestion = false;
            System.out.println("normal client will send normal request");
        }
        else if(parameters.batchSuggestion){
            singletonEBN = ExecBaseNode.singletonExec;
            System.out.println("middle client will send group of requests as a batch suggestion to the backends");
            assert (singletonEBN != null);
        }
    }

    public void setNonBlocking() {
        this.blockingFlag = 1;
    }

    public void setGlue(ClientGlueInterface g) {
        glue = g;
    }

    public void enqueueRequest(byte[] operation) {
        byte[] res = execute(operation);
        glue.returnReply(res);
    }

    public void enqueueReadOnlyRequest(byte[] op) {
        byte[] res = executeReadOnlyRequest(op);
        glue.returnReply(res);
    }

    public synchronized byte[] executeReadOnlyRequest(byte[] op) {
        while (pendingRequestCount >= requestThreshold) {
            try {
                //System.out.println("pendingRequests/requestThreshold: "+pendingRequestCount+"/"+requestThreshold);
                wait(500);
            } catch (Exception e) {
            }
        }
        pendingRequestCount++;
        if (pendingRequestCount > requestThreshold) {
            BFT.Debug.kill("Too many outstanding requests: " +
                    pendingRequestCount + "/" + requestThreshold);
        }

        if (op.length > parameters.maxRequestSize)
            Debug.kill("operation is too big!");

        System.err.println("forming Read only " +
                "request with sequence number:" + (readOnlySeqno + 1));
        RequestCore origRC = null;
        /*if (parameters.primaryBackup)
      origRC = new SimpleRequestCore(members.getMyId(), readOnlySeqno,
	  op);
	  else*/
        if (!parameters.filtered)
            origRC = new SignedRequestCore(parameters, members.getMyId(), members.getMySubId(),
                    readOnlySeqno, nextGC, op);
        else
            origRC = new SimpleRequestCore(parameters, members.getMyId(), members.getMySubId(),
                    readOnlySeqno, nextGC, op);
        // dont need to sign readonly requests
        //origRC.sign(getMyPrivateKey());
        ReadOnlyRequest req = new ReadOnlyRequest(parameters, members.getMyId(), origRC);
        ReadOnlyReply reply = null;
        authenticateExecMacArrayMessage(req);

        Quorum<ReadOnlyReply> readReplies = new Quorum<ReadOnlyReply>(parameters.getExecutionCount(),
                parameters.rightExecutionQuorumSize(),
                0);

        readOnlyReplies.put(req.getCore().getRequestId(), readReplies);
        activeReadOnlyRequests.put(req.getCore().getRequestId(), req);

        readOnlySeqno++;

        boolean resend = true;
        int count = 0;
        byte[] replybytes = null;
        long startTime = System.currentTimeMillis();
        while (replybytes == null && count < 10) {
            if (resend) {
//                System.err.println("retransmitting!");
                byte[] tmp = req.getBytes();

                for (int j = 0; j < readQuorumSize; j++) {
                    int index = (resendBase++) % parameters.getExecutionCount();
                    sendToExecutionReplica(tmp, index);
                }
            }
            resend = true;
            count++;
            System.err.println("Sending read only sequence number: " + readOnlySeqno);
            try {
                long stopT = startTime + readRetrans;
                long currT = startTime;
                while (currT < stopT && !readReplies.isComplete()) {
                    wait(stopT - currT);
                    currT = System.currentTimeMillis();
                }
            } catch (Exception e) {
                e.printStackTrace();
                Debug.kill(new RuntimeException("interrupted!?"));
            }//System.err.println("not waiting anymore");

            if (readReplies.isComplete()) {
                //		System.err.println(readreplies.getEntries());
                VerifiedMessageBase vmb[] = readReplies.getEntries();
                byte[][] options = new byte[vmb.length][];
                for (int i = 0; i < vmb.length; i++) {
                    if (vmb[i] != null) {
                        options[i] = ((ReadOnlyReply) (vmb[i])).getCommand();
                    }
                }
                reply = readReplies.getEntry();
                replybytes = glue.canonicalEntry(options);
                if (replybytes == null) {
                    readReplies.clear();
                    reply = null;
                }
            }

            if (reply == null) {
                readRetrans *= 2;
            }
            if (readRetrans > 4000) {
                count = 11;
                readRetrans = 4000;
            }
        }

        activeReadOnlyRequests.remove(req.getCore().getRequestId());
        readOnlyReplies.remove(req.getCore().getRequestId());


        // if the retries failed, do the normal path
        if (replybytes == null) {
            // System.err.println("I give up, converting to regular");
            readReplies.clear();
            replybytes = execute(op);
            readRetrans = 2 * (System.currentTimeMillis() - startTime);
            if (readRetrans < 200)
                readRetrans = 200;
            pendingRequestCount--;
            if (pendingRequestCount < 0) {
                BFT.Debug.kill("pending request count should not be below 0: " + pendingRequestCount);
            }
            return replybytes;
        } else {
            readReplies.clear();
            //	    System.err.println("returning: "+new String(reply.getCommand()));
            readRetrans = 2 * (System.currentTimeMillis() - startTime);
            if (readRetrans < 200) {
                readRetrans = 200;
            }
            pendingRequestCount--;
            if (pendingRequestCount < 0) {
                BFT.Debug.kill("pending request count should not be below 0: " + pendingRequestCount);
            }
            notifyAll();
            return replybytes;
        }
    }

    public void pipedRetransmit(long id, byte[] operation) {
        RequestCore origRC;
        ClientRequest req;
        ClientRequest req_test;
        if (!parameters.filtered) {
            origRC = new SignedRequestCore(parameters, members.getMyId(), members.getMySubId(), id, nextGC, operation);
            ((SignedRequestCore) (origRC)).sign(getMyPrivateKey());

            if (stateDigest != null && id == seqDigest) {
                req = new ClientRequest(parameters, members.getMyId(), members.getMySubId(),
                        origRC, stateDigest, nextGC);
                req_test = new ClientRequest(parameters, members.getMyId(), 0, origRC);
            } else {
                req = new ClientRequest(parameters, members.getMyId(), members.getMySubId(), origRC);
                req_test = new ClientRequest(parameters, members.getMyId(), 0, origRC);
            }
        } else {
            Debug.info(Debug.MODULE_EXEC, "filtered, will created SimpleRequestCore");
            origRC = new SimpleRequestCore(parameters, members.getMyId(), members.getMySubId(), id, nextGC, operation);
            if (stateDigest != null && seqDigest == 0) {
                req = new ClientRequest(parameters, members.getMyId(), members.getMySubId(), origRC, false, stateDigest, digestId);
                req_test = new ClientRequest(parameters, members.getMyId(), 0, origRC, false);
                seqDigest = new Long(seqNo);
            } else {
                req = new ClientRequest(parameters, members.getMyId(), members.getMySubId(), origRC, false);
                req_test = new ClientRequest(parameters, members.getMyId(), 0, origRC, false);
            }
        }
        Debug.profileStart("SND_TO_FILTER-ORDER");
        //				Debug.println("sending request: "+req+" "+req.getCore().getRequestId())
        if (parameters.primaryBackup) {
            Debug.info(Debug.MODULE_EXEC, "Send to execution primary");
            sendToAllExecutionReplicas(req.getBytes());
        } else if (!parameters.filtered) {
            sendToAllOrderReplicas(req.getBytes());
        } else {
            Debug.info(Debug.MODULE_EXEC, "!firstsend, will sendToAllFilterReplicas %s", req.toString());
            sendToAllFilterReplicas(req.getBytes());
        }
    }

    public byte[] fetchState(long seq) {
        Debug.debug(Debug.MODULE_EXEC, "CSBN: fetchState seqNo %d", seqNo);
        return util.UnsignedTypes.longToBytes(seqNo);
    }

    public byte[] pipedExecute(byte[] req, ThreadMonitor m, long seq) {
        byte[] result;
        long sid;
        if (!m.isMyTurn(seq)) {
            // throw out execeptions
            Debug.error(Debug.MODULE_EXEC, "pipedExecuted: order break!");
        }
        // send out request;
        Debug.debug(Debug.MODULE_EXEC, "pipeSend: %d", seq);
        sid = pipedSend(req);

        // release turn;
        m.releaseTurn(seq);

        m.waitMyTurn(seq);
        Debug.debug(Debug.MODULE_EXEC, "pipeReceive: %d", seq);
        result = pipedReceive(sid, req);
        Debug.debug(Debug.MODULE_EXEC, "wait for my turn to return replies: %d", seq);
        Debug.debug(Debug.MODULE_EXEC, "Done replying."); // [Commented by lcosvse]: What????
        return result;
    }

    public long pipedSend(byte[] operation) {
        Debug.debug(Debug.MODULE_EXEC, "executing %d bytes", operation.length);
        Debug.debug(Debug.MODULE_EXEC, "Starting execute of request\n");
        if (pendingRequestCount > requestThreshold) {
            BFT.Debug.kill("Too many outstanding requests: " +
                    pendingRequestCount + "/" + requestThreshold);
        }

        if (operation.length > parameters.maxRequestSize) {
            Debug.kill("Operation is too big");
        }

        RequestCore origRC;
        ClientRequest req;
        ClientRequest req_test;
        if (!parameters.filtered) {

            origRC = new SignedRequestCore(parameters, members.getMyId(), members.getMySubId(), seqNo, nextGC, operation);
            ((SignedRequestCore) (origRC)).sign(getMyPrivateKey());

            // send digest while necessary
            if (stateDigest != null && seqDigest == 0) {
                req = new ClientRequest(parameters, members.getMyId(), members.getMySubId(),
                        origRC, stateDigest, nextGC);
                req_test = new ClientRequest(parameters, members.getMyId(), 0, origRC);
                seqDigest = new Long(seqNo);
            } else {
                req = new ClientRequest(parameters, members.getMyId(), members.getMySubId(),
                        origRC);
                req_test = new ClientRequest(parameters, members.getMyId(), 0, origRC);
            }
        } else {

            Debug.info(Debug.MODULE_EXEC, "filtered, will created SimpleRequestCore");
            origRC = new SimpleRequestCore(parameters, members.getMyId(), members.getMySubId(), seqNo, nextGC, operation);
            if (stateDigest != null && seqDigest == 0) {
                req = new ClientRequest(parameters, members.getMyId(), members.getMySubId(), origRC, false, stateDigest, digestId);
                req_test = new ClientRequest(parameters, members.getMyId(), 0, origRC, false);
                seqDigest = new Long(seqNo);
            } else {
                req = new ClientRequest(parameters, members.getMyId(), members.getMySubId(), origRC, false);
                req_test = new ClientRequest(parameters, members.getMyId(), 0, origRC, false);
            }
        }
        Debug.debug(Debug.MODULE_EXEC, "exec seqNo %d with subId = %d", seqNo, members.getMySubId());

        if (parameters.primaryBackup) {
            ;//do nothing
        } else if (!parameters.filtered) {
            authenticateOrderMacArrayMessage(req);
        } else {
            authenticateFilterMacArrayMessage(req);
        }

        long start = System.currentTimeMillis();

        // in case there can be future replies received, cache these
        if (replySet.get(seqNo) == null) {
            Quorum<Reply> reply = new Quorum<Reply>(parameters.getExecutionCount(),
                    parameters.rightExecutionQuorumSize(),
                    0);
            replySet.put(seqNo, reply);
        }

        Debug.info(Debug.MODULE_EXEC, " add new quorum for %d ", seqNo);


        /* 
         * Here is the added logic for CP
         * if loading CP now, don't need to send it out
         */
        if (workingState == null) {
            //System.out.println("\t WTF: something wrong with workingState");
        }
        if (seqNo < workingState.getNextSeqNum()) {
            Debug.info(Debug.MODULE_EXEC, "\t current using CP, no need to send %d", seqNo);
            seqNo++;
            return seqNo - 1;
        }

        if (parameters.primaryBackup) {
            Debug.info(Debug.MODULE_EXEC, "Send to execution primary");
            sendToExecutionReplica(req.getBytes(), primary);
        } else if (!parameters.filtered) {
            //System.err.println("byte count: "+req.getBytes().length);
            sendToAllOrderReplicas(req.getBytes());
        } else {
            byte[] tmp = req.getBytes();
            int index = members.getMyId();
            int fc = parameters.getFilterCount();

            if (parameters.chainFilter) {
                //System.err.println("chaining the filter nodes");
                sendToFilterReplica(tmp, (index + 1) % fc);
            } else {
                //System.err.println("sending to subset");
                int j = parameters.mediumFilterQuorumSize();

                for (int i = 0; i < j; i++) {
                    index = (index + 1) % fc;
                    sendToFilterReplica(tmp, index);
                    Debug.debug(Debug.MODULE_EXEC, "sending to filter replica %d request with subId = %d replysize: %d",
                            i,
                            req.getSubId(),
                            pipedReplies.size());
                }
            }
        }
        seqNo++;
        return seqNo - 1;
    }

    public synchronized byte[] pipedReceive(long sid, byte[] req) {
        boolean resend = false;
        long retrans = 100;
        Reply reply = null;
        while (reply == null) {
            Quorum<Reply> quor = replySet.get(sid);
            if (quor.isComplete()) {
                reply = quor.getEntry();
                break;
            }

            if (workingState.fetchReply(sid) != null) {
                reply = workingState.fetchReply(sid);

                // check if the recovery CP is done, restart sending req
                if (!readyForRequests && workingState.getNextSeqNum() == sid + 1) {
                    readyForRequests = true;
                    //lastState = workingState;
                    //workingState = null;
                    workingState = new CheckPointState(parameters, nextGC);
                }
                return reply.getCommand();
            }
            // during recovery fetch replies through CP
            if (!readyForRequests) {
                while (!doneLoadingCP && !readyForRequests) {
                    try {
                        // hey I am still loading CP, wait here
                        wait(20);
                        //System.out.println("\twait loading CP");
                    } catch (Exception e) {
                        throw new RuntimeException("interrupted!?");
                    }
                }
//                reply = workingState.fetchReply(sid);
//                System.out.println("\tWokringState: start " + workingState.getStartSeqNum() 
//                        + " next " + workingState.getNextSeqNum() + " sid " + sid);
//                if ( workingState.getNextSeqNum() == sid + 1) {
//                    readyForRequests = true;
//                    lastState = workingState;
//                    workingState = null;
//                    System.out.println("\tWorkingState done resume sending!");
//                }
//                return reply.getCommand();
            }
            if (resend) {
                pipedRetransmit(sid, req);
            }
            resend = true;
            retrans *= 2;
            if (retrans > 4000)
                retrans = 4000;
            try {
                wait(retrans);
            } catch (Exception e) {
                throw new RuntimeException("interrupted!?");
            }
        }
        //Debug.println("returning: "+reply.getCommand()+" bytes");
        //retrans = 2*(System.currentTimeMillis() - start);
        if (retrans < 500)
            retrans = 500;
        if (retrans > 4000) {
            retrans = 4000;
        }
        replySet.remove(sid);
        workingState.addReply(reply, sid);

        // remove the digest if success
        if (stateDigest != null && sid == seqDigest) {
            digestId = 0;
            stateDigest = null;
            seqDigest = 0;
        }
        // take CPs if necessary
        if (readyForRequests && sid % BFT.Parameters.checkPointInterval == 0) {
            takeCheckPoint();
        }
        return reply.getCommand();
    }

    public synchronized byte[] execute(byte[] operation) {
        long firstSendTime = 0;
        long csbnStart = System.nanoTime();//TODO delete
        long retrans = -1;
        RequestCore origRC;
        ClientRequest req;
        ClientRequest req_test;
        Quorum<Reply> replies = null;
        Reply reply = null;

        Debug.debug(true, "Starting execute of request\n");
        retrans = baseRetrans;
        while (pendingRequestCount >= requestThreshold) { //&& seqNo-requestThreshold <= lastSeqNo){
            try {
                wait(5000);
            } catch (Exception e) {
            }
        }
        //synchronized(this) {
        pendingRequestCount++;
        //}
        if (pendingRequestCount > requestThreshold) {
            BFT.Debug.kill("Too many outstanding requests: " +
                    pendingRequestCount + "/" + requestThreshold);
        }

        if (operation.length > parameters.maxRequestSize) {
            Debug.kill("Operation is too big");
        }

		
	/*if (parameters.primaryBackup){
	  origRC =
	  new SimpleRequestCore(members.getMyId(), seqNo,
	  operation);
	  req = new ClientRequest(members.getMyId(), origRC, false);
	  }
	  else*/
//	    System.err.println("create request for: " + parameters.newNestedGroupNo + " , in client " + getMembership().getMyId());
        if (!parameters.filtered) {
            origRC = new SignedRequestCore(parameters, members.getMyId(), members.getMySubId(), seqNo, operation);
            ((SignedRequestCore) (origRC)).sign(getMyPrivateKey());
            req = new ClientRequest(parameters, members.getMyId(), members.getMySubId(), origRC);
        } else {
//            int fakeSubId = members.getMySubId();
//            if (parameters.batchSuggestion && parameters.newNestedGroupNo > 0) {
//                fakeSubId = members.getMySubId() + parameters.numberOfNestedRequestInTheSlice * 10 + parameters.newNestedGroupNo * 1000;
//                //System.out.println("fakeSubId: " + fakeSubId);
//            }
            origRC = new SimpleRequestCore(parameters, members.getMyId(), members.getMySubId(), seqNo, operation);
//            System.err.println("entry: " + Arrays.toString(origRC.getEntry().getBytes()));
            //TODO this is unnecessary if we are using batch suggestion
            req = new ClientRequest(parameters, members.getMyId(), members.getMySubId(), origRC, false);
        }

        if (parameters.primaryBackup) {
//            System.out.println("primary backup client");//do nothing
        } else if (!parameters.filtered) {
            authenticateOrderMacArrayMessage(req);
        } else {
            authenticateFilterMacArrayMessage(req);
        }

        // add the request to the active set and introdue the appropriate reply quorum
        Debug.debug(Debug.MODULE_EXEC, "Creating new quorum with %d %d", parameters.getExecutionCount(), parameters.rightExecutionQuorumSize());

        if (replySet.containsKey(seqNo)) {
            // if replies for future
            replies = replySet.get(seqNo);
            Debug.debug(Debug.MODULE_EXEC, "ahhhhhhhhhhhhhhhh! future replies received.");
        } else {
            int quorumSize = parameters.rightExecutionQuorumSize();
//            System.out.println("quorum size in the client: " + quorumSize);
            replies = new Quorum<Reply>(parameters.getExecutionCount(),
                    quorumSize,
                    0);

            Debug.info(Debug.MODULE_EXEC, "Adding a reply quorum and a request with seqNo %d", seqNo);
            replySet.put(seqNo, replies);
        }

        activeRequests.put(seqNo, req);
        // increment the active sequence number
        seqNo++;

        boolean resend = true;
        boolean firstsend = true;
        long start = System.currentTimeMillis();
        long threadID = Thread.currentThread().getId();
        while (reply == null) {
//            System.err.println("client send request: " + seqNo);
            if (resend) {
                Debug.profileStart("SND_TO_FILTER-ORDER");
                //				Debug.println("sending request: "+req+" "+req.getCore().getRequestId())
                if (parameters.primaryBackup) {
//                    System.out.println("Send to execution primary");
                    if (firstsend) {
                        if(parameters.batchSuggestion)
                        {
                            singletonEBN.sendGroupClientRequestToExecution(origRC.getBytes(), this, members.getMyId(),
                                    parameters.numberOfNestedRequestInTheSlice, parameters.newNestedGroupNo, primary);
//                            System.out.println("we are in middle client");
                            firstsend = false;
                        }
                        else
                        {
                            sendToExecutionReplica(req.getBytes(), primary);
//                            System.out.println("myID: " + members.getMyId() + ", request id: " + origRC.getRequestId() + " primary: " + primary);
                            firstsend = false;
                        }
                    } else if(parameters.isMiddleClient && parameters.backendAidedVerification ) {
                            //TODO implement timeout actions for this branch
                    }
                    else {
                        // System.out.println("send to all execution replicas in primary backup mode");
                        // sendToAllExecutionReplicas(req.getBytes());
                    }
                } else if (!parameters.filtered) {
                    //System.err.println("byte count: "+req.getBytes().length);
                    sendToAllOrderReplicas(req.getBytes());
                } else if (!firstsend) {
                    if(parameters.isMiddleClient && parameters.backendAidedVerification) {
                        //TODO implement timeout actions for this branch
                    }
                    else {
                        //System.err.println("Sending to everybody primary="+primary);
                        Debug.debug(Debug.MODULE_EXEC, "Thread %d: !firstsend, will sendToAllFilterReplica %s\n", threadID, req.toString());
                        System.out.println("send to all filter replicas");
                        sendToAllFilterReplicas(req.getBytes());
                    }
                } else {
                    byte[] tmp = req.getBytes();
                    int index = members.getMyId();
                    int fc = parameters.getFilterCount();

                    Debug.debug(Debug.MODULE_EXEC, "Thread %d: inside else, will send to 1 filter node\n", threadID);
                    if (parameters.chainFilter) {
                        Debug.debug(Debug.MODULE_EXEC, "Thread %d: chaining the filter nodes\n", threadID);
                        sendToFilterReplica(tmp, (index + 1) % fc);
                    } else {
                        Debug.debug(Debug.MODULE_EXEC, "Thread %d: sending to subset\n", threadID);
                        int j = parameters.mediumFilterQuorumSize();
                        if(parameters.batchSuggestion)//TODO either send to all filters or fix the index for batch suggestion
                        {
                            singletonEBN.sendGroupClientRequestToFilter(origRC.getBytes(), this, members.getMyId(),
                                    parameters.numberOfNestedRequestInTheSlice, parameters.newNestedGroupNo);
                            //System.out.println("we are in middle client");
                        }
                        else
                        {
                            for (int i = 0; i < j; i++) {
                                index = (index + 1) % fc;
//                              System.err.println("send to filter replica " + index);
                                sendToFilterReplica(tmp, index);
//                                System.out.println("we are in normal client");

                            }
                        }

                        firstSendTime = System.nanoTime();
//                        System.err.println("firstSent: " + (firstSendTime-csbnStart)/1000);
                    }
                    firstsend = false;
                }
                Debug.profileFinis("SND_TO_FILTER-ORDER");
            }
            resend = true;

            // WAIT FOR A RESPONSE AND THEN SEE WHAT COMES NEXT!
            try {
//                System.err.println("client waits for a response");
                long startt = System.currentTimeMillis();
                long stop = startt + retrans;
                Debug.debug(Debug.MODULE_EXEC, "Thread %d: retrans=%d, stop=%d, current=%d\n",
                        threadID, retrans, stop, System.currentTimeMillis());
                // wait for a complete response or the resend timer to finish
                while (!replies.isComplete() && System.currentTimeMillis() < stop) {

                    Debug.debug(Debug.MODULE_EXEC, "Thread %d: waiting for replies for req (%d,%d)\n",
                            threadID, req.getCore().getSendingClient(), req.getCore().getRequestId());
                    long timeout = stop - System.currentTimeMillis();
                    if (timeout < 0)
                        break;
                    Debug.debug(Debug.MODULE_EXEC, "Thread %d: about to wait in CSBN for time: %d",
                            threadID, timeout);
                    wait(timeout);
                    Debug.debug(Debug.MODULE_EXEC, "Thread %d: woke up when waiting for replies for req (%d,%d)\n",
                            threadID, req.getCore().getSendingClient(), req.getCore().getRequestId());
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                throw new RuntimeException("interrupted!? " + e.getClass());
            } catch (IllegalArgumentException e) {
                Debug.debug(Debug.MODULE_EXEC, "Illegal argument exception.");
                e.printStackTrace();
            }
            Debug.debug(Debug.MODULE_EXEC, "Thread %d: is looking at replies=%s for req (%d,%d)\n",
                    Thread.currentThread().getId(), replies, req.getCore().getSendingClient(), req.getCore().getRequestId());

            if (replies.isComplete()) {
                reply = replies.getEntry();
                Debug.debug(Debug.MODULE_EXEC, "Thread %d: received reply %s for req (%d,%d)\n",
                        threadID, reply, req.getCore().getSendingClient(), req.getCore().getRequestId());
            } else {
                Debug.debug(Debug.MODULE_EXEC, "Thread %d: timeout when waiting for reply for req (%d,%d)!\n",
                        threadID, req.getCore().getSendingClient(), req.getCore().getRequestId());
                retrans *= 2;
            }

            // never wait more than 4 seconds for a retransmission
            if (retrans > 4000) {
                retrans = 4000;
            }
        }

        //  Measure how long it took to process the request, adjust base retransmission appropriately
        retrans = 2 * (System.currentTimeMillis() - start);
        if (retrans < 2000) {
            baseRetrans = 2000;
        }
        if (retrans > 4000) {
            baseRetrans = 4000;
        }
        //System.err.println("NEW RETRANS: " + retrans);
        //	System.err.println("returning a response!");
        pendingRequestCount--;
        if (pendingRequestCount < 0) {
            BFT.Debug.kill("pending request count should not be below 0: " + pendingRequestCount);
        }

        //synchronized(this) {
        // remove the request from the active set and the reply quorum from the table
        replySet.remove(reply.getRequestId());
        activeRequests.remove(reply.getRequestId());
        //}
        Debug.debug(true, "Ending execute of request\n");
        long returnTime = System.nanoTime();
//        System.err.println("total time in csbn: " + (returnTime-csbnStart)/1000);
//        System.err.println("time after first Send: " + (returnTime-firstSendTime)/1000);
//        System.err.println("return reply for: " + seqNo + " , in client " + getMembership().getMyId());
        return reply.getCommand();
    }

    synchronized public byte[] getState() {
        // useless func
        //System.out.println("\t getState: " + seqNo);
        //System.exit(-1);
        return util.UnsignedTypes.longToBytes(seqNo);
    }

    synchronized public void loadState(byte[] byt) {
        seqNo = util.UnsignedTypes.bytesToLong(byt);
        //System.out.println("\t loadState: set values appropriately. " + seqNo);
    }


    public void handle(byte[] bytes) {
        VerifiedMessageBase vmb = MessageFactory.fromBytes(bytes, parameters);
//		Debug.debug(Debug.MODULE_EXEC, "Thread %d: in ClientShimBaseNode.handle. received %s\n",
//				Thread.currentThread().getId(), vmb);
        switch (vmb.getTag()) {
            case MessageTags.Reply:
//                System.out.println("received reply");
                process((Reply) vmb);
                return;
            case MessageTags.WatchReply:
                process((WatchReply) vmb);
                return;
            case MessageTags.ReadOnlyReply:
                process((ReadOnlyReply) vmb);
                return;
            case MessageTags.FetchCheckPointState:
                process((FetchCheckPointState) vmb);
                return;
            case MessageTags.CPStateMessage:
                process((CPStateMessage) vmb);
                return;
            case MessageTags.OldRequestMessage:
                process((OldRequestMessage) vmb);
                return;
            default:
                Debug.kill("WTF");
        }
    }


    synchronized protected void process(ReadOnlyReply rep) {
        // Debug.println("=====\nprocessingReadonly reply "+rep);
        // 	    BFT.Debug.println("Got Reply from: "+rep.getSendingReplica());
        //Debug.println("req id:          "+rep.getRequestId());
        if (!validateExecMacMessage(rep)) {
            throw new RuntimeException("reply mac did not authenticate");
        }

        if (rep.getRequestId() < readOnlySeqno) {
            //System.err.println("discarding the older response");
            return;
        }
        // TODO: handle case where the read-only request doesn't exist yet.
        Quorum<ReadOnlyReply> readReplies = readOnlyReplies.get(rep.getRequestId());
        ReadOnlyReply rop = readReplies.getEntry();
        if (!readReplies.addEntry(rep)) {
            System.err.println("Didn't like the reply! Replaced");
            // 	    //System.err.println(rop);
            // 	    System.out.print("contents: ");
            // 	    for (int i = 0; i < 20 && i < rop.getCommand(); i++)
            // 		System.out.print(" "+rop.getCommand()[i]);
            // 	    //System.err.println("\nwith"); // 	    //System.err.println(rep);
            // 	    System.out.print("contents: ");
            // 	    for (int i = 0; i < 20 && i < rep.getCommand(); i++)
            // 		    System.out.print(" "+rep.getCommand()[i]);
            // 	    //System.err.println("=====");
        }
        if (readReplies.isComplete()) {
            notifyAll();
        }
    }

    synchronized protected void process(Reply rep) {
//        System.err.println("process(Reply): client receives reply");
        Debug.debug(Debug.MODULE_EXEC, "Thread %d: =====processing reply (size %d bytes) from replica %d for req %d\n",
                Thread.currentThread().getId(), rep.getCommand().length, rep.getSendingReplica(), rep.getRequestId());
        if (!validateExecMacMessage(rep)) {
            throw new RuntimeException("reply mac did not authenticate");
        }

        //if (rep.getRequestId() < seqNo) {
        //}
        Quorum<Reply> replies = replySet.get(rep.getRequestId());
        if (replies == null) {
            // we 've thrown away that quorum already
            // TODO: we should probably be caching those in case this replica
            // is actually behind and has not issued this request yet.
            Debug.debug(Debug.MODULE_EXEC, "Thread %d: replies for reqId %d is null\n",
                    Thread.currentThread().getId(), rep.getRequestId());
            if (rep.getRequestId() < nextGC) {
                //System.out.println("\t\tDiscarding old reply");
                return;
            }
            // this reply is far ahead, cache this
            //TODO implement new process method if reply is sent from nonspeculative state in aggre execute mode. This not
            //a problem in Eve mode because we have verification process at the end of batch
            int quorumSize = parameters.rightExecutionQuorumSize();
//            System.out.println("quorum size for client is: " + quorumSize);
            Quorum<Reply> reply = new Quorum<Reply>(parameters.getExecutionCount(),
                    quorumSize,
                    0);
            replySet.put(rep.getRequestId(), reply);
            replies = reply;

        }
        Reply olrep = replies.getEntry();
        if (!replies.addEntry(rep)) {
            Debug.println("replacing " + olrep);
            for (int i = 0; i < 20 && i < olrep.getCommand().length; i++)
                //System.out.print(" "+ olrep.getCommand()[i]);
                Debug.println("\nwith      " + rep);
            for (int i = 0; i < 20 && i < rep.getCommand().length; i++)
                //System.out.print(" "+ rep.getCommand()[i]);
                Debug.println("");

        }
        if (parameters.primaryBackup) {
            primary = rep.getSender();
        }
        //Debug.println("complete: "+replies.isComplete());
        Debug.debug(Debug.MODULE_EXEC, "Thread %d: is looking at replies=%s for req %d\n",
                Thread.currentThread().getId(), replies, rep.getRequestId());
        if (replies.isComplete()) {
            Debug.debug(Debug.MODULE_EXEC, "Thread %d: Quorum is complete for req %d\n",
                    Thread.currentThread().getId(), rep.getRequestId());
            notifyAll();
        } else {
            Debug.debug(Debug.MODULE_EXEC, "Thread %d: Quorum not complete for req %d\n",
                    Thread.currentThread().getId(), rep.getRequestId());
        }
    }

    protected void process(WatchReply rep) {
        //Debug.println("=====processing watch reply "+new String(rep.getCommand()));
        //BFT.//Debug.println("\tGot watch Reply from: "+rep.getSendingReplica());
        //Debug.println("\treq id:          "+rep.getRequestId());
        if (!validateExecMacMessage(rep)) {
            Debug.kill(new RuntimeException("reply mac did not authenticate"));
        }

        if (!watchReplies.addEntry(rep)) {
            glue.brokenConnection();
        } else if (watchReplies.isComplete()) {
            glue.returnReply(watchReplies.getEntry().getCommand());
            watchReplies.clear();
        }
    }


    // handle fetch message
    protected void process(FetchCheckPointState cp) {
        //System.out.println("\tCP:recieve fetch request from clientsub " + cp.getSendingReplica());
        CheckPointState tmp = stateSnapshots.get(new Long(cp.getSeqNum()));
        if (tmp == null) {
            //System.out.println("Fetch : something wrong with fetch checkpoint");
            // TODO 
            // this can also be the current CP
            // which means csbn need to take CP now
            return;
        }
        long seq = tmp.getStartSeqNum();
        CPStateMessage cpsm = new CPStateMessage(seq, members.getMySubId(), tmp.getBytes());
        // send to sub client
        authenticateClientMacMessage(cpsm, members.getMyId(), members.getMySubId());
        sendToClient(cpsm.getBytes(), members.getMyId(), cp.getSendingReplica());
    }

    // handle cp message
    synchronized protected void process(CPStateMessage cpsm) {
        //System.out.println("\tCP:CPstateMessage from clientSub " + cpsm.getSender() + 
        //        " for req " + cpsm.getSeqNum());
        CheckPointState tmp = new CheckPointState(parameters, cpsm.getState());
        if (stateSnapshots.get(new Long(cpsm.getSeqNum())) != null) {
            //System.out.println("CP: CP already in state " + cpsm.getSeqNum());
            return;
        }
//        stateSnapshots.put(new Long(cpsm.getSeqNum()),tmp);
        loadCheckPointState(tmp, cpsm.getSeqNum());
    }

    synchronized protected void process(OldRequestMessage orm) {
        //System.out.println("\toldRequest: need to fetch CPs:" + orm.getRequestId()
        //        + " current workingState " + workingState.getStartSeqNum());
        // the start seqNo of CP to be fetched 
        //    * should be less than the seqNo
        //    * should be less than the GCId 
        //    * should be multiple of CP intervals 
        if (orm.getRequestId() > seqNo
                || orm.getRequestId() < workingState.getStartSeqNum()
                || orm.getRequestId() < nextGC
                || orm.getRequestId() % BFT.Parameters.checkPointInterval != 1) {
            // late message, abandon
            //System.out.println("\tOldRequest: late Message, ignore, nextGC:" + nextGC
            //        + "orm: "+  orm.getRequestId() + "seqNo:" + seqNo 
            //        + "workingState:" + workingState.getStartSeqNum());
            return;
        }

        if (oldRequests.getEntry() != null &&
                orm.getRequestId() > oldRequests.getEntry().getRequestId()) {
            // this msg has larger legal seq id 
            // the former one should be abandoned
            //System.out.println("\tOldRequest: ,before " + oldRequests.getEntry().getRequestId()
            //        + "after "+  orm.getRequestId());
            oldRequests.clear();
        }
        oldRequests.addEntry(orm);
        if (!oldRequests.isComplete()) {
            return;
        }
        readyForRequests = false;
        doneLoadingCP = false;

        try {
            long base = orm.getRequestId();
            String suffix = "_CLIENT_CP.LOG";
            String suffix2 = "_" + members.getMyId() + "_" + members.getMySubId()
                    + suffix;
            File cpFile1 = new File(base + suffix2);
            //System.out.println("\ttry to load log file " + cpFile1);
            if (!cpFile1.exists()) {
                // fetch remotely
                //System.out.println("no log files exist");
                loadStateDigest = BFT.messages.Digest.fromBytes(parameters, orm.getDigest());
                //System.out.println("\tDigest for " + orm.getRequestId() + loadStateDigest.toString());
                FetchCheckPointState cps = new FetchCheckPointState(orm.getRequestId(),
                        members.getMySubId());
                authenticateClientMacMessage(cps, members.getMyId(), members.getMySubId());
                for (int i = 0; i < members.getClientReplicationCount(members.getMyId()); i++) {
                    if (i != members.getMySubId())
                        sendToClient(cps.getBytes(), members.getMyId(), i);
                }
            } else {
                // try to load CP from locally
                loadLogFile(cpFile1, base);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        oldRequests.clear();
    }

    protected void loadCheckPointState(CheckPointState cps, long seqid) {
        Digest tmp = new Digest(parameters, cps.getBytes());
        // check the digest
        if (!loadStateDigest.equals(tmp)) {
            // check failed, wrong digest ignore
            //System.out.println("\tCP: digest check failed");
            //System.out.println("\tDIgest check: " + loadStateDigest.toString() + ":" +
            //        tmp.toString());
            return;
        }
        workingState = new CheckPointState(parameters, cps);
        nextGC = workingState.getNextSeqNum();
        oldRequests.clear();
        doneLoadingCP = true;
        loadStateDigest = null;
        //System.out.println("\tCP: load CP done from " + seqid);
//        if (parameters.doLogging) {
//            CPLogger cpl = new CPLogger(workingState,members.getMyId(), 
//                    members.getMySubId(), this);
//            pool.execute(cpl);
//        }
    }

    public void takeCheckPoint() {
//        if (workingState == null ) {
//            // we are recovering from CP, no need to send digest
//            // NOTE: should be executed here right now!!
//            workingState = new CheckPointState(parameters, lastState.getNextSeqNum());
//            return ;
//        }
        //System.out.println("\ttake CPs at from " + workingState.getStartSeqNum() + " to " + 
        //        workingState.getNextSeqNum());
        stateSnapshots.put(new Long(workingState.getStartSeqNum()), workingState);

        nextGC = workingState.getNextSeqNum();
        if (parameters.doLogging) {
            // write the CP to disk
            CPLogger cpl = new CPLogger(workingState, members.getMyId(),
                    members.getMySubId(), this);
            pool.execute(cpl);
        }

        // keep the digest to send the digest of this log to filtenode
        stateDigest = new Digest(parameters, workingState.getBytes());
        digestId = workingState.getStartSeqNum();
        //System.out.println("\tDigest for " + workingState.getStartSeqNum() + " " +
        //        stateDigest.toString());
        //lastState = workingState;
        workingState = new CheckPointState(parameters, workingState.getNextSeqNum());
    }

    public void loadLogFile(File cpFile, long startSeqNum) {

        // load replies from log file
        //System.out.println("Loading log file " + cpFile + " from " + startSeqNum);
        if (cpFile == null || !cpFile.exists()) {
            BFT.Debug.kill("log file is not exist!");
            return;
        }
        try {
            FileInputStream fs = new FileInputStream(cpFile);
            int length = (int) cpFile.length();
            byte[] tmp = new byte[length];
            fs.read(tmp);
            fs.close();
            //System.out.println("\t file content:" + tmp);
            CheckPointState cps = new CheckPointState(parameters, tmp);
            //System.out.println(tmp);
            workingState = new CheckPointState(parameters, cps);
            stateSnapshots.put(new Long(workingState.getStartSeqNum()), workingState);
            //seqNo = workingState.getNextSeqNum();
            nextGC = workingState.getNextSeqNum();
            //System.out.println("\tCP: load CP from file:" + cpFile 
            //        + " next Seq " + nextGC);
        } catch (Exception e) {
            e.printStackTrace();
        }
        doneLoadingCP = true;
    }
}
