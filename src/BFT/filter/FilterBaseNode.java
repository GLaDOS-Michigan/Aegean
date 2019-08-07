package BFT.filter;

import BFT.BaseNode;
import BFT.Debug;
import BFT.MessageFactory;
import BFT.exec.TimingLogger.RealTimingLogger;
import BFT.exec.TimingLogger.TimingLogger;
import BFT.filter.statemanagement.CheckPointState;
import BFT.messages.*;
import BFT.network.PassThroughNetworkQueue;
import BFT.network.netty2.ReceiverWrapper;
import BFT.network.netty2.SenderWrapper;
import BFT.util.Role;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;

//import BFT.network.netty.NettyTCPReceiver;
//import BFT.network.netty.NettyTCPSender;

public class FilterBaseNode extends BaseNode {

    protected long[] retransDelay, // current retransmission delay for the client
            lastRemoveId, // last request id to be removed from hashMap
    //            lastreqId, // last request id ordered for the client
    lastTime, // last time a request was sent for the client
            retransCount; // number of retransmissions for the specified client
    protected long baseRetrans = 00;
    protected CheckPointState[] intervals; // requests that were ordered in the specified interval
    protected int baseIntervalIndex; // the oldest interval we are currently maintaining
    protected ClientRequest[] earlyReqs;// 1 request per client with seqno > the last completed one for that client
    protected long[] early; // sequence nubmers from the future sent
    // by exec replicas
    protected BatchCompleted[][] futureBatch; // set of futur-istic
    // batches.  for each
    // client, an array with
    // one entry per filter
    // node for batch
    // completed messages
    // not in a checkpoint
    // interval that we are
    // currently caching
    protected CPUpdate[] cpus; // 1 cpupdate message per filter
    protected Hashtable<Long, GoodQuorum<ClientRequest>>[] replicatedClients;

    protected Hashtable<Long, Digest>[] logsDigest;

    protected Entry[] pendingClientEntries; // pending client entries.
    // client request with
    // sequence number greater
    // than the last request
    // to be forwarded but not
    // yet committed
    protected int maxPendingRequestPerClient; // max number of outstanding requests per client
    //  protected FilteredRequest[] lastsent; // Cached copy of hte last message sent for each client.
    protected long[] lastGC;
    protected int currentPrimary = 0;
    protected long currentView = 0;
    protected int loggerIndex = 0;
    protected int loggerIndexMax = 3;
    protected MsgLogQueue mlq;
    protected int min_size;
    // minimum size to force a digest rather than raw request
    protected Integer clientLocks[];
    protected int myIndex;

    //Timing Logger to determine bottle neck for client requests.
    //Jim Given 10/1/15
    TimingLogger messageRecieved;
    private HashMap<Integer, GoodQuorum<BatchSuggestion>> groupMap = new HashMap<>();
    private int lastRemovedGroupId;


    public FilterBaseNode(String membership, int id) {
        super(membership, BFT.util.Role.FILTER, id);
        myIndex = id;
        min_size = 4 * BFT.messages.Digest.size(parameters);
        clientLocks = new Integer[parameters.getNumberOfClients()];
        retransDelay = new long[parameters.getNumberOfClients()];
        retransCount = new long[retransDelay.length];
        lastTime = new long[parameters.getNumberOfClients()];
        lastRemoveId = new long[parameters.getNumberOfClients()];
        lastGC = new long[parameters.getNumberOfClients()];
//        lastreqId = new long[lastTime.length];
//        lastsent = new FilteredRequest[lastTime.length];
        pendingClientEntries = new Entry[lastTime.length];
        replicatedClients = new Hashtable[parameters.getNumberOfClients()];
        logsDigest = new Hashtable[parameters.getNumberOfClients()];
//        for (int i = 0 ; i < parameters.getNumberOfClients(); i ++ ) {
//            replicatedClients[i] = new Quorum<ClientRequest>(this.members.getClientReplicationCount(i),
//                    this.members.getClientQuorumSize(i),
//                    0);
//            System.out.println("new quorum: count " + this.members.getClientReplicationCount(i) + 
//                    " quorum size" + this.members.getClientQuorumSize(i));
//        }
        for (int i = 0; i < parameters.getNumberOfClients(); i++) {
            retransDelay[i] = baseRetrans;
            retransCount[i] = 0;
            clientLocks[i] = new Integer(i);
            replicatedClients[i] = new Hashtable<Long, GoodQuorum<ClientRequest>>();
            logsDigest[i] = new Hashtable<Long, Digest>();
            lastGC[i] = 0;
//            lastreqId[i] = -1;
        }
        maxPendingRequestPerClient = 128;

        cpus = new CPUpdate[parameters.getExecutionCount()];

        intervals = new CheckPointState[5 * (BFT.order.Parameters.maxPeriods) + 1];
        baseIntervalIndex = 0;
        for (int i = 0; i < intervals.length; i++) {
            intervals[i] = new CheckPointState(parameters, i * BFT.order.Parameters.checkPointInterval);
        }

        early = new long[parameters.getExecutionCount()];
        earlyReqs = new ClientRequest[parameters.getNumberOfClients()];
        futureBatch = new BatchCompleted[BFT.order.Parameters.checkPointInterval][];
        for (int i = 0; i < futureBatch.length; i++) {
            futureBatch[i] = new BatchCompleted[parameters.getExecutionCount()];
        }
        loadLogFiles();
        messageRecieved = new RealTimingLogger("Message Recieved", 500);
        //messageRecieved.logStart();
    }

    protected void loadLogFiles() {
        // the following is for reading logs from disc
        // read file.
        try {
            long base = 0;
            for (int i = 0; i < 3; i++) {
                Debug.debug(Debug.MODULE_FILTER, "normal log %d", i);
                FileInputStream fs = new FileInputStream("normallog_" + i + ".LOG");
                byte[] tmpBase = new byte[4];
                fs.read(tmpBase);
                long b = util.UnsignedTypes.bytesToLong(tmpBase);
                if (b > base) {
                    base = b;
                }
                Debug.debug(Debug.MODULE_FILTER, "start at: %d", b);
                Debug.debug(Debug.MODULE_FILTER, ("available: " + fs.available()));
                while (fs.available() > 0) {
                    Debug.debug(Debug.MODULE_FILTER, ("available: " + fs.available()));
                    byte[] tmp = new byte[4];
                    fs.read(tmp);
                    tmp = new byte[(int) util.UnsignedTypes.bytesToLong(tmp)];
                    fs.read(tmp);
                    Entry entry = Entry.fromBytes(parameters, tmp, 0);
                    for (int j = 0; j < intervals.length; j++) {
                        intervals[j].addRequest(entry);
                    }
                    Debug.debug(Debug.MODULE_FILTER, entry.toString());
                }
            }
            for (int i = 0; i < intervals.length; i++) {
                intervals[i].setBaseSequenceNumber(base + i * BFT.order.Parameters.checkPointInterval);
            }

            for (int i = 0; i < 2; i++) {
                FileInputStream fs = new FileInputStream("dumplog_" + i + ".LOG");
                Debug.debug(Debug.MODULE_FILTER, "dump log %d", i);
                while (fs.available() > 0) {
                    byte[] tmp = new byte[4];
                    fs.read(tmp);
                    tmp = new byte[(int) util.UnsignedTypes.bytesToLong(tmp)];
                    fs.read(tmp);
                    Entry entry = Entry.fromBytes(parameters, tmp, 0);
                    if (pendingClientEntries[(int) entry.getClient()] == null
                            || pendingClientEntries[(int) entry.getClient()].getRequestId() < entry.getRequestId()) {
                        pendingClientEntries[(int) entry.getClient()] = entry;
                        FilteredRequestCore frc = new FilteredRequestCore(parameters, entry);
                        authenticateOrderMacSignatureMessage(frc, myIndex);
                        int client = (int) frc.getSendingClient();
                        FilteredRequestCore[] frc2 = new FilteredRequestCore[1];
                        frc2[0] = frc;
                        FilteredRequest req2 =
                                new FilteredRequest(parameters, getMyFilterIndex(),
                                        frc2);
                        authenticateOrderMacArrayMessage(req2);
                        //lastsent[client] = req2;
                    }
                    Debug.debug(Debug.MODULE_FILTER, entry.toString());
                }
            }
        } catch (FileNotFoundException f) {
            Debug.error(Debug.MODULE_FILTER, "no file to read");
        } catch (Exception e) {
            e.printStackTrace();
            BFT.Debug.kill("fail");
        }

    }

    //TODO think about locking granularity
    protected synchronized void process(BatchSuggestion bs) {
        int sender = bs.getSender();
        int subId = bs.getSubId();
        //System.out.println("processing BatchSuggestion from id: " + sender + ", subid: " + subId);
////TODO work on these signing stuff later
//        if (!validateClientMacArrayMessage(bs, myIndex) && !parameters.cheapClients) {
//            Debug.kill("bad verification on client mac array message");
//        }
//        if (!bs.checkAuthenticationDigest() && !parameters.cheapClients) {
//            Debug.kill("bad digest");
//        }

        //System.out.println("wow signatures are working");
        //Create quorum and forward to the execution replicas as batch
        //gc and discarding old requests etc...
        GroupClientRequests gr =  (GroupClientRequests) bs.getCore();
        //System.out.println("Finally: " + gr);

        int newNestedGroupNo = gr.getNewNestedGroupNo();
        GoodQuorum<BatchSuggestion> quorum = groupMap.get(newNestedGroupNo);
        //TODO think about GC. I think it is also not implemented in previous version but since process(ClientRequest req) doesn't have condition with equality,
        //lastGC's stay zero and the client sends it as zero and continue without a problem and without GC

        //should use subId
        if(quorum != null && quorum.isComplete()) {
            return;
            /*
            There is no resending of client requests. If client couldn't get a response for its request, it should send normal
            request after resolving speculation and any possible divergence. Therefore, there is nothing to do here in terms
            of retransmitting the client request but think about other things needs to be done.
             */
        }
        else {
            if (quorum == null) {

                quorum = new GoodQuorum<BatchSuggestion>(
                        this.members.getClientReplicationCount(sender),
                        this.members.getShimLayerQuorumSize(sender),
                        0);
                groupMap.put(newNestedGroupNo, quorum);
                while (newNestedGroupNo > lastRemovedGroupId + maxPendingRequestPerClient) {
                    // remove old quorum from hashtable
                    Debug.debug(Debug.MODULE_FILTER, "remove old request: %d", lastRemoveId[sender]);
                    groupMap.remove(lastRemovedGroupId++);
                }
            }

            quorum.addEntry(bs);
            if (!quorum.isComplete()) {
//                System.out.println("not yet complete: " + newNestedGroupNo);
                return;
            }

//            System.out.println("wow complete quourum: " + newNestedGroupNo);
            byte[][] requests = gr.getRequests();
            for(int i = 0; i < requests.length; i++) {
                if(requests[i] != null) {
                    SimpleRequestCore src = new SimpleRequestCore(parameters, requests[i]);
                    FilteredRequestCore frc = new FilteredRequestCore(parameters, src.getEntry());
                    requests[i] = frc.getBytes();
                }
            }

            FilteredBatchSuggestion fbs = new FilteredBatchSuggestion(parameters, getMyFilterIndex(), gr);
            if (parameters.useVerifier) {
                authenticateExecMacArrayMessage(fbs);
            } else {
                authenticateOrderMacArrayMessage(fbs);
            }
            //sendToAllOrderReplicas(req2.getBytes());
            //	System.out.println("sending "+req2+" to currentPrimary");
            if (parameters.useVerifier) {
                sendToExecutionReplica(fbs.getBytes(), currentPrimary);
            } else {
                sendToOrderReplica(fbs.getBytes(), currentPrimary);
            }
        }

    }

    protected void process(ClientRequest req) {
        Debug.debug(Debug.MODULE_FILTER, "FilterBaseNode, received clientrequest from client %d", req.getCore().getSendingClient());
//        System.err.println(req + " FilterBaseNode, received clientrequest from client " + req.getCore().getSendingClient());
        int sender = (int) (req.getCore().getSendingClient());
        GoodQuorum<ClientRequest> quorum;
        if (!validateClientMacArrayMessage(req, myIndex) && !parameters.cheapClients) {
            Debug.kill("bad verification on client mac array message");
        }
        if (!req.checkAuthenticationDigest() && !parameters.cheapClients) {
            Debug.kill("bad digest");
        }

        synchronized (clientLocks[sender]) {
            SimpleRequestCore src = (SimpleRequestCore) req.getCore();
//            System.err.println("entry: " + Arrays.toString(src.getEntry().getBytes()));

            // if its old, or we've already got something cached
            // for this guy, consider retransmitting
            int client = sender;
            long current = System.currentTimeMillis();
            quorum = replicatedClients[sender].get(src.getRequestId());
            Debug.debug(Debug.MODULE_FILTER, "received Request from %d:%d:%d GCId: %d",
                    sender,
                    src.getSubId(),
                    src.getRequestId(),
                    src.getGCId());

            // old request, plz fetch CP
            if (src.getRequestId() < lastGC[sender]) {
                // find the right seq for CP to load
                long tmp = (src.getRequestId() - 1) / BFT.Parameters.checkPointInterval;
                tmp = tmp * BFT.Parameters.checkPointInterval + 1;
                Debug.debug(Debug.MODULE_FILTER, "\tOld request, plz fetch CP from clients,reqid = %d lastGC = %d",
                        tmp,
                        lastGC[sender]);
                // ignore this request
                if (logsDigest[sender].get(tmp) == null) {
                    Debug.warning(Debug.MODULE_FILTER, "\tDigest not exist for sid ignore here!");
                    return;
                }

                OldRequestMessage orm = new OldRequestMessage(getMyFilterIndex(),
                        tmp, logsDigest[sender].get(tmp).getBytes());
                authenticateClientMacMessage(orm, sender, src.getSubId());
                sendToClient(orm.getBytes(), sender, src.getSubId());
//                System.err.println("old request");

                return;
            }

            // otherwise just do the quorum check and then forward the request
            if (quorum != null && quorum.isComplete()) {
                if (current - lastTime[client] < retransDelay[client]) {
                    Debug.info(Debug.MODULE_FILTER, "\tblocking retransmisssion of client %d for request %d",
                            client,
                            src.getRequestId());
                    return;
                }
                //else if(!quorum.isFull()) {//TODO I am not sure about its effect on the performance, check after fixing the performance problem of ps mode
                //    quorum.addEntry(req);
                //    return;
                //}

                resetEntrySubId(src.getEntry());
                FilteredRequestCore frc;

                if (!parameters.filterCaching
                        || src.getCommand().length < min_size) {
                    frc = new FilteredRequestCore(parameters, src.getEntry());
                } else {
                    System.err.println("ngno: " + src.getEntry().getParameters().newNestedGroupNo);
                    frc =
                            new FilteredRequestCore(parameters,
                                    new Entry(src.getEntry().getParameters(), src.getSendingClient(),
                                            src.getSubId(),
                                            src.getRequestId(),
                                            src.getEntry().getMyDigest()));
                }

                if (parameters.useVerifier) {
                    FilteredRequestCore[] frc2 = new FilteredRequestCore[1];
                    authenticateExecutionMacSignatureMessage(frc, myIndex);
                    frc2[0] = frc;
                    FilteredRequest req2 = new FilteredRequest(parameters, getMyFilterIndex(), frc2);

                    sendToAllExecutionReplicas(req2.getBytes());
                } else {
                    Debug.debug(Debug.MODULE_FILTER, "Sending to all order replicas %d: %d",
                            sender,
                            src.getRequestId());
                    FilteredRequestCore[] frc2 = new FilteredRequestCore[1];
                    authenticateOrderMacSignatureMessage(frc, myIndex);
                    frc2[0] = frc;
                    FilteredRequest req2 = new FilteredRequest(parameters, getMyFilterIndex(), frc2);

//                    System.err.println("sending to all order replicas");
                    sendToAllOrderReplicas(frc2);
                }
                retransDelay[client] *= 2;
                retransCount[client]++;
                if (retransDelay[client] > 1000) {
                    retransDelay[client] = 1000;
                }
                lastTime[client] = current;
                return;

            } // otherwise the request is not early, the request is new, and
            // there are no outsanding requests from the client so i can
            // process it
            else {
                // completely new request, added to hashtable
                // and remove old quorum from it

                if (quorum == null) {
                    //TODO write a new method for processing nested requests if they sent after resolving speculation (the
                    //main difference should be the quorum size will be getClientQuorumSize
                    int quorumSize = parameters.isMiddleClient ? this.members.getShimLayerQuorumSize(sender) : this.members.getClientQuorumSize(sender);
//                    System.out.println("quorum size in filter is: " + quorumSize);
                    quorum = new GoodQuorum<ClientRequest>(
                            this.members.getClientReplicationCount(sender),
                            quorumSize,
                            0);
                    replicatedClients[sender].put(src.getRequestId(), quorum);
                    while (src.getRequestId() > lastRemoveId[sender] + maxPendingRequestPerClient) {
                        // remove old quorum from hashtable
                        Debug.debug(Debug.MODULE_FILTER, "remove old request: %d", lastRemoveId[sender]);
                        replicatedClients[sender].remove(lastRemoveId[sender]++);
                    }
                }
                // do the quorum first, if not complete return, else forward to order

                quorum.addEntry(req);
                if (!quorum.isComplete()) {
                    return;
                }

                // do GC, make sure the all clients agree on this GCId
                // and log the digest
                if (src.getGCId() > lastGC[sender]) {

                    for (long i = lastGC[sender]; i < src.getGCId(); i++) {
                        //quorum = replicatedClients[sender].get(i);
                        //quorum.clear();
                        replicatedClients[sender].remove(i);
                    }
                    lastGC[sender] = src.getGCId();
                    long digestId = req.getDigestId();
                    Digest digest = req.getDigest();
                    if (digest == null || digestId == 0) {
                        Debug.error(Debug.MODULE_FILTER, "\tDigest:WTF %d", digestId);
                    } else {
                        logsDigest[sender].put(digestId, digest);
                        Debug.debug(Debug.MODULE_FILTER, "\tDigest:new log digest for reqId: %d, digest: %s",
                                digestId,
                                digest.toString());
                    }
                }

                Debug.debug(Debug.MODULE_FILTER, "first receipt of %d %d",
                        sender,
                        src.getRequestId());

                // create and authenticate the filtered requestcore
                FilteredRequestCore frc;
                resetEntrySubId(src.getEntry());
                boolean use_digest = false;
                if (!parameters.filterCaching
                        || src.getCommand().length < min_size) {
//                    System.err.println("updated src bytes: " + Arrays.toString(src.getBytes()));

                    frc = new FilteredRequestCore(parameters, src.getEntry());
                } else {
                    System.err.println("ngno: " + src.getEntry().getParameters().newNestedGroupNo);
                    frc =
                            new FilteredRequestCore(parameters, new Entry(src.getEntry().getParameters(), src.getSendingClient(),
                                    src.getSubId(),
                                    src.getRequestId(),
                                    src.getEntry().getMyDigest()));
                    use_digest = true;
                }

//                System.err.println("before authentication: " + Arrays.toString(frc.getBytes()));

                if (parameters.useVerifier) {
                    authenticateExecutionMacSignatureMessage(frc, myIndex);
                } else {
//                    System.err.println("authenticate: " + myIndex);
                    authenticateOrderMacSignatureMessage(frc, myIndex);
                }
//                System.err.println("after authentication: " + Arrays.toString(frc.getBytes()));

                // if we're sending a digest, record the
                // simplecore for posterity
                if (frc.getEntry().getDigest() != null) {
                    pendingClientEntries[client] = src.getEntry();
                }
                // log it to disk and send the entry if appropriate
                if (use_digest) {
                    // if i'm the designated forwarder, then forward now
                    if (parameters.speculativeForward
                            && (src.getEntry().getClient() + 1)
                            % parameters.getFilterCount() == myIndex) {
                        CacheCommand fwd = new CacheCommand(parameters, src.getEntry(), myIndex);
                        authenticateExecMacArrayMessage(fwd);
                        // if chain exectuion then send to one
                        if (parameters.chainExecution) {
                            Debug.debug(Debug.MODULE_FILTER, "Sending to 1 exec replica");
                            sendToExecutionReplica(fwd.getBytes(),
                                    client % parameters.getExecutionCount());
                        } else //otherwise send to all
                        {
                            Debug.debug(Debug.MODULE_FILTER, "Sending to all exec replicas");
                            sendToAllExecutionReplicas(fwd.getBytes());
                        }
                    }
                    log(frc, src.getEntry());
                } else {
//                    System.err.println("andSend FBN 401: ");
                    andSend(frc);
                }
            }
            if (parameters.chainFilter) {
                if (myIndex != ((int) src.getEntry().getClient() + parameters.mediumFilterQuorumSize()) % parameters.getFilterCount()) {
                    sendToFilterReplica(src.getBytes(), (myIndex + 1) % parameters.getFilterCount());
                }
            }
        }
    }

    /*
    In agree-execute mode, orders tries to reach decision on the clientRequests. When an order node receives
    some requests it checks whether the Filters sign these requests. However, clientRequests come to the filters
     with a small difference which is subId to identify replicated middle service clients. Also, these requests
     come to the filters in different orders so they use different clientRequests as representative requests.
     When an order replica tries compare Mac for each filteredRequest it calculates these mac arrays by using one
     client request and if one of the filters use another clientRequest as representative, this breaks authentication.
     In order to fix this issue, we set the same subId to each clientRequests and we make the bytes null to force
     the bytes to be re-calculated. Execute-verify mode does not have this problem because the verification is done
     on the final application state not on the filteredRequests. More effective ways can be thought but this approach
     works fine for now.
     */
    private void resetEntrySubId(Entry entry) {
        if (!parameters.useVerifier) {
            entry.setSubId(0);
            entry.setBytes(null);
        }
    }

    public void sendToAllOrderReplicas(FilteredRequestCore[] frc2) {
        for (int i = 0; i < parameters.getOrderCount(); i++) {
            andSend(frc2, i);
        }
    }

    protected void log(FilteredRequestCore frc, Entry entry) {
        // and now write the frc.getEntry() to disk
        // when done, call andsend
        if (parameters.doLogging) {
            MsgLogWrapper wrap = new MsgLogWrapper(frc, entry);
            logCount++;
            mlq.addWork(loggerIndex, wrap);
        } else {
            andSend(frc);
        }
    }

    protected void dump(Entry ent) {
        // write DEADBEEF + ent.getBytes()
        if (ent == null) {
            return;
        }
        if (parameters.doLogging) {
            MsgLogWrapper wrap = new MsgLogWrapper(ent);
            logCount++;
            dumpCount++;
            mlq.addWork(loggerIndex, wrap);
        }
    }

    int dumpCount = 0;

    protected void startDump() {
        dumpCount = 0;
        MsgLogWrapper wrap = new MsgLogWrapper(LogFileOps.START_DUMP);
        mlq.addWork(loggerIndex, wrap);
    }

    protected void andSend(FilteredRequestCore frc) {
        FilteredRequestCore[] frc2 = new FilteredRequestCore[1];
        frc2[0] = frc;
//        System.err.println("to current primary: " + currentPrimary);
        andSend(frc2, currentPrimary);
    }

    protected void andSend(FilteredRequestCore[] frc, int index) {
        FilteredRequest req2 = new FilteredRequest(parameters, getMyFilterIndex(), frc);
        if (parameters.useVerifier) {
            authenticateExecMacArrayMessage(req2);
        } else {
//            System.err.println("second authenticate");
            authenticateOrderMacArrayMessage(req2);
        }
        //sendToAllOrderReplicas(req2.getBytes());
        //	System.out.println("sending "+req2+" to currentPrimary");
        if (parameters.useVerifier) {
            sendToExecutionReplica(req2.getBytes(), index);
        } else {
            sendToOrderReplica(req2.getBytes(), index);
        }

        for (int i = 0; i < frc.length; i++) {
            int client = (int) (frc[i].getSendingClient());
            synchronized (clientLocks[client]) {
                retransDelay[client] = baseRetrans;
                //lastsent[client] = req2;
                lastTime[client] = System.currentTimeMillis();
            }
        }
    }

    protected void andSend(FilteredRequestCore[] frc) {
        andSend(frc, currentPrimary);
    }

    int logCount = 0;

    protected void nextLogger() {
        loggerIndex = (loggerIndex + 1) % loggerIndexMax;
        MsgLogWrapper wrap = new MsgLogWrapper(LogFileOps.CREATE, baseSequenceNumber());
        mlq.addWork(loggerIndex, wrap);
        logCount = 0;
    }

    public void setLoggerQueue(MsgLogQueue mlq) {
        this.mlq = mlq;
        nextLogger();
    }

    synchronized protected void process(CPUpdate cpu) {
        int sender = cpu.getSendingReplica();
        // if i already have a cpupdate and either its new than this one
        // or this one does not dominate the old one then return immeciately
        if (cpus[sender] != null
                && (cpu.getSequenceNumber() < cpus[sender].getSequenceNumber()
                || !cpu.dominates(cpus[sender]))) {
            return;
        }

        if (!validateExecMacArrayMessage(cpu, myIndex)) {
            BFT.Debug.kill("Bad execution replcia!");
        }

        cpus[sender] = cpu;
        int count = 0;
        int i = 0;
        // check to see if there is a cpupdate from a server
        // that is weakly dominated by $liars$ other servers.
        while (i < cpus.length
                && count < parameters.smallExecutionQuorumSize()) {
            cpu = cpus[i];
            count = 0;
            for (int j = 0; cpu != null && j < cpus.length; j++) {
                if (i == j) {
                    count++;
                } else if (cpus[j] != null && cpus[j].weaklyDominates(cpu)) {
                    count++;
                }
            }
            i++;
        }

        // no cpupdate is dominated --> i.e. nothing moves our lastreqid forward
        if (count < parameters.smallExecutionQuorumSize() || cpu == null) {
            //	    System.out.println("nothing was dominated");
            return;
        }


        long indices[] = cpu.getCommands();
        // remove the dominated cpu
        cpus[i - 1] = null;
        // update all tables to reflect the new maximal value for
        // sequence numbers committed for each client.
        for (i = 0; i < indices.length; i++) {
            // if the updated sequence nubmer is greater than the one
            // we record, then update
            //	    System.out.println(i+" old: "+lastreqId[i]+" new: "+indices[i]);
            synchronized (clientLocks[i]) {
//				if (indices[i] >= lastreqId[i]) {
//					lastreqId[i] = indices[i];
//					pendingClientEntries[i] = null;
//					if (earlyReqs[i] != null) {
//						process(earlyReqs[i]);
//						earlyReqs[i] = null;
//					}
//				}
            }
        }
    }

    synchronized protected void process(BatchCompleted bc) {
        //	System.out.println("batch completed: "+bc.getVersionNo());
        // if the batch sequence number is smaller than the base of our
        // minimal checkpoint, then done
        if (bc.getSeqNo() < baseSequenceNumber()) {
            return;
        }
        if (!validateExecMacArrayMessage(bc, myIndex)) {
            BFT.Debug.kill("Bad Authentication");
        }
        // if the sequence number is larger than any sequence numbers that we are currently caching
        if (bc.getSeqNo() >= maxSequenceNumber()) {
            // record the early sequencenumber
            early[bc.getSendingReplica()] = bc.getSeqNo();
            // if its in the near enough future, then cache it
            if (bc.getSeqNo() <= maxSequenceNumber() + BFT.order.Parameters.checkPointInterval) {
                futureBatch[(int) (bc.getSeqNo() % BFT.order.Parameters.checkPointInterval)][bc.getSendingReplica()] = bc;
            }
            int earlycount = 0;
            for (int i = 0; i < early.length; i++) {
                earlycount += (early[i] >= maxSequenceNumber()) ? 1 : 0;
            }
            // if small quorum of execution nodes agree that we are
            // slow, then garbage collect the oldest quorum
            if (earlycount >= parameters.smallExecutionQuorumSize()) {
                garbageCollect();
            }
            return;
        }

        int index = (int) ((bc.getSeqNo() - baseSequenceNumber())
                / BFT.order.Parameters.checkPointInterval);
        CheckPointState cps = intervals[(baseIntervalIndex + index) % intervals.length];
        Quorum<BatchCompleted> quorum = cps.getQuorum(bc.getSeqNo());

        boolean complete = quorum.isComplete();
        quorum.addEntry(bc);
        //if the current batch completed the quorum
        if (!complete && quorum.isComplete()) {
            CommandBatch cb = bc.getCommands();
            Entry[] entries = cb.getEntries();
            Digest d;
            Entry e;

            if (bc.getView() > currentView) {
                currentView = bc.getView();
                currentPrimary =
                        (int) (bc.getView() % (parameters.useVerifier ? parameters.getExecutionCount() : parameters.getOrderCount()));
            }
            //	    String completing = "\t completing: ";
            for (int i = 0; i < entries.length; i++) {
                e = entries[i];
                d = e.getDigest();
                int c = (int) e.getClient();
                int forme = getMyFilterIndex();
                synchronized (clientLocks[c]) {
                    Entry e2 = pendingClientEntries[(int) c];
                    // if the pending request matches the entry in the
                    // message, then add it to the checkpoint
                    // and clear hte pending slot
                    if (d != null && e2 != null
                            && e2.getRequestId() == e.getRequestId()
                            && e2.matches(e.getDigest())) {
                        //			completing += "<"+c+","+e2.getRequestId()+"> ";
                        cps.addRequest(e2);
                        pendingClientEntries[(int) c] = null;
                        // if im the sender designate, then send
                        if ((c + 1) % parameters.getFilterCount() == forme) {
                            //			    			    System.out.println("\tcomplete batch fetch for "+
                            //	       "client "+c+" to server "+
                            //		       bc.getSendingReplica());
                            fetch(bc.getSeqNo(), e, bc.getSendingReplica());

                        }
                    }// otherwise if there is no pending entry or the
                    // pending entry precedes the completed entry,
                    // clear the pending list and update the last requestid
                    else if (e2 == null || e2.getRequestId() <= e.getRequestId()) {
                        // 			if (e2 == null)
                        // 			    System.out.println("clearing null");
                        // 			else
                        // 			    System.out.println("clearing pending entry <"+
                        // 					       e2.getClient()+","+
                        // 					   e2.getRequestId()+">");
                        pendingClientEntries[(int) c] = null;
//						if (e.getRequestId() > lastreqId[(int) c]) {
//							lastreqId[(int) c] = e.getRequestId();
//						}
                    }
                    if (earlyReqs[(int) c] != null) {
                        //System.out.println("&&& processing that thing we got early");
                        process(earlyReqs[(int) c]);
                        earlyReqs[(int) c] = null;
                    }
                }
            }
            //	    System.out.println(completing);
            // if im at a checkpoint interval, then garbage collect
            if (bc.getSeqNo() == maxSequenceNumber() - 1 - BFT.order.Parameters.checkPointInterval) {
                garbageCollect();
            }

        } else {// wasnt complete, still need to respond
            CommandBatch cb = bc.getCommands();
            Entry[] entries = cb.getEntries();
            Entry e;
            int forme = getMyFilterIndex();

            for (int i = 0; i < entries.length; i++) {
                e = entries[i];
                if (e.getDigest() != null
                        && (e.getClient() + 1) % parameters.getFilterCount() == forme) {
                    synchronized (clientLocks[(int) e.getClient()]) {
                        //			System.out.println("\tincomplete batch fetch for client "+
                        //		   e.getClient()+" for "+bc.getSendingReplica());
                        fetch(bc.getSeqNo(), e, bc.getSendingReplica());
                    }
                }
            }
        }

    }

    protected void garbageCollect() {


        intervals[baseIntervalIndex] = new CheckPointState(parameters, maxSequenceNumber());
        baseIntervalIndex = (baseIntervalIndex + 1) % intervals.length;
        int earlycount = 0;
        for (int i = 0; i < early.length; i++) {
            earlycount += (early[i] >= maxSequenceNumber()) ? 1 : 0;
        }
        if (earlycount >= parameters.smallExecutionQuorumSize()) {
            garbageCollect();
        }
        BatchCompleted bc;
        for (int i = 0; i < futureBatch.length; i++) {
            for (int j = 0; j < parameters.getExecutionCount(); j++) {
                if (futureBatch[i][j] != null) {
                    bc = futureBatch[i][j];
                    futureBatch[i][j] = null;
                    process(futureBatch[i][j]);
                }
            }
        }

        if (parameters.doLogging) {
            startDump();
            for (int i = 0; i < pendingClientEntries.length; i++) {
                if (pendingClientEntries[i] != null) {
                    dump(pendingClientEntries[i]);
                }
            }
            nextLogger();
        }
    }

    synchronized protected void process(FetchCommand fc) {
        Debug.debug(Debug.MODULE_FILTER, "Fetching");
        if (fc.getSeqNo() < baseSequenceNumber()
                || fc.getSeqNo() > maxSequenceNumber()) {
            Debug.debug(Debug.MODULE_FILTER, ("trying to fetch " + fc.getSeqNo() +
                    " which is not in [" + baseSequenceNumber() +
                    ", " + maxSequenceNumber() + ")"));
            if (fc.getSeqNo() > maxSequenceNumber()) {
                BFT.Debug.kill("Bad things should not happen to good people");
            }
            return;
        }
        if (!validateExecMacArrayMessage(fc, myIndex) || !fc.checkAuthenticationDigest()) {
            BFT.Debug.kill("Bad authentication");
        }

        //	String fetching = fc.getVersionNo()+
        //	    " {"+fc.getEntry().getClient()+","+fc.getEntry().getRequestId()+" }";
        fetch(fc.getSeqNo(), fc.getEntry(), fc.getSendingReplica());
    }

    protected void fetch(long seqno, Entry ent, int sender) {
        fetch(seqno, (int) ent.getClient(),
                (int) ent.getRequestId(),
                ent, sender);
    }

    protected void fetch(long seqno, int client, long reqId,
                         Entry ent, int sender) {
        int index =
                (int) (baseIntervalIndex + (seqno - baseSequenceNumber()) / BFT.order.Parameters.checkPointInterval);
        index = index % intervals.length;
        Entry entry;
        synchronized (clientLocks[client]) {
            entry = intervals[index].getRequest(client,
                    reqId);
            if (entry == null) {
                entry = pendingClientEntries[client];

            }
        }
        if (entry == null || !entry.matches(ent.getDigest())) {
            if (entry == null) {
                Debug.debug(Debug.MODULE_FILTER, "\t nada -- failed to fetch <%d, %d> for %d at %d",
                        client,
                        reqId,
                        sender,
                        seqno);
            } else {
                Debug.debug(Debug.MODULE_FILTER, "\t wrong -- failed to fetch <%d, %d> for %d at %d",
                        client,
                        reqId,
                        sender,
                        seqno);
            }
            FetchDenied fd = new FetchDenied(seqno, ent, getMyFilterIndex());
            authenticateExecMacMessage(fd, sender);//, fc.getSendingReplica());
            sendToExecutionReplica(fd.getBytes(),
                    sender);

            return;
        }

        ForwardCommand fwd = new ForwardCommand(seqno,
                entry, getMyFilterIndex());
        authenticateExecMacMessage(fwd, sender);
        sendToExecutionReplica(fwd.getBytes(), sender);
    }

    private Quorum<ExecViewChangeMessage> execViewChangeQuorum = new Quorum<ExecViewChangeMessage>(parameters.getExecutionCount(), parameters.rightExecutionQuorumSize(), 0);

    private void process(ExecViewChangeMessage msg) {
        if (!this.validateExecMacArrayMessage(msg, myIndex)) {
            Debug.error(Debug.MODULE_FILTER, "Validate failed for %s", msg.toString());
            return;
        }
        execViewChangeQuorum.addEntry(msg);
        if (!execViewChangeQuorum.isComplete()) {
            return;
        }
        execViewChangeQuorum.clear();

        assert (msg.getViewNo() > this.currentView);

        Debug.debug(Debug.MODULE_FILTER, "Exec view changed to %d", msg.getViewNo());
        this.currentView = msg.getViewNo();
        this.currentPrimary = (int) this.currentView % parameters.getExecutionCount();
        Debug.debug(Debug.MODULE_FILTER, "Send lastsent to new primary");
//		for (int i = 0; i < lastsent.length; i++) {
//			if (lastsent[i] != null) {
//				sendToExecutionReplica(lastsent[i].getBytes(), currentPrimary);
//			}
//		}

    }

    public void handle(byte[] vmbbytes) {
        VerifiedMessageBase vmb = MessageFactory.fromBytes(vmbbytes, parameters);
        Debug.debug(Debug.MODULE_FILTER, "tag : %d\n", vmb.getTag());
//        System.out.println("we got a request: " + vmb.getTag());
        switch (vmb.getTag()) {
            case MessageTags.BatchSuggestion:
                BatchSuggestion bs = (BatchSuggestion) vmb;
                process(bs);
                return;
            case MessageTags.ClientRequest:
                process((ClientRequest) vmb);
                return;
            case MessageTags.BatchCompleted:
                process((BatchCompleted) vmb);
                return;
            case MessageTags.FetchCommand:
                process((FetchCommand) vmb);
                return;
            case MessageTags.CPUpdate:
                process((CPUpdate) vmb);
                return;
            case MessageTags.ExecViewChange:
                process((ExecViewChangeMessage) vmb);
                return;
            default:
                Debug.kill(new RuntimeException("filter does not handle message " + vmb.getTag()));
        }
    }

    protected long baseSequenceNumber() {
        return intervals[baseIntervalIndex].getBaseSequenceNumber();
    }

    protected long maxSequenceNumber() {
        return baseSequenceNumber() + intervals.length * BFT.order.Parameters.checkPointInterval;
    }

    public void start() {
        //super.start();
    }

    public int getMyFilterIndex() {
        return myIndex;
    }

    public static void main(String[] args) {

        if (args.length < 2) {
            System.err.println("Usage: java BFT.filter.FilterBaseNode <id> <config_file>");
            System.exit(0);
        }
        //Security.addProvider(new de.flexiprovider.core.FlexiCoreProvider());

        FilterBaseNode csbn =
                new FilterBaseNode(args[1], Integer.parseInt(args[0]));

        if (csbn.getParameters().doLogging) {
            Thread mlat = null, mlbt = null, mlct = null;

            MsgLogQueue mlq = new MsgLogQueue();
            csbn.setLoggerQueue(mlq);
            MsgLogger MLA = new MsgLogger(csbn.getParameters(), 0, mlq, csbn);
            MsgLogger MLB = new MsgLogger(csbn.getParameters(), 1, mlq, csbn);
            MsgLogger MLC = new MsgLogger(csbn.getParameters(), 2, mlq, csbn);
            mlat = new Thread(MLA);
            mlbt = new Thread(MLB);
            mlct = new Thread(MLC);

            mlat.start();
            mlbt.start();
            mlct.start();
        }

        SenderWrapper sendNet = new SenderWrapper(csbn.getMembership(), 1);
        csbn.setNetwork(sendNet);

        Role[] roles = new Role[3];
        roles[0] = Role.FILTER;
        roles[1] = Role.VERIFIER;
        roles[2] = Role.EXEC;
        PassThroughNetworkQueue ptnq = new PassThroughNetworkQueue(csbn);
        ReceiverWrapper receiveNet =
                new ReceiverWrapper(roles,
                        csbn.getMembership(),
                        ptnq, 1);

        roles = new Role[1];
        roles[0] = Role.CLIENT;
        ReceiverWrapper receiveClientNet =
                new ReceiverWrapper(roles, csbn.getMembership(),
                        ptnq, 4);

        // 	Role[] roles2 = new Role[1];
        // 	roles2[0] = Role.EXEC;
        // 	PassThroughNetworkQueue ptnq2 = new PassThroughNetworkQueue(csbn);
        // 	ReceiverWrapper receive2Net =
        // 	    new ReceiverWrapper(roles2,
        // 				 csbn.getMembership(),
        // 				 ptnq2, 1);

    }
}
