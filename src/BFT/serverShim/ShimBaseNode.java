package BFT.serverShim;

import BFT.BaseNode;
import BFT.Debug;
import BFT.membership.Principal;
import BFT.messages.*;
import BFT.serverShim.messages.*;
import BFT.serverShim.messages.MessageTags;
import BFT.serverShim.statemanagement.CheckPointState;
import BFT.serverShim.statemanagement.NextBatchCertificate;

import java.io.File;
import java.io.FileInputStream;
import java.net.InetAddress;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// outgoing messages
// intra-shim
// incoming messages
// shim node state

public class ShimBaseNode extends BaseNode
        implements ServerShimInterface {


    // Maintain one copy of the working state
    protected CheckPointState workingState;
    // maintain a collection of previous checkpoints.  indexed by the
    // first sequence number *not* to be included in the snapshot
    protected Hashtable<Long, CheckPointState> stateSnapshots;

    // collection of all early batches while recovery
    protected Hashtable<Long, NextBatch> earlyBatches;

    // set of sequence numbers at upcoming snapshots
    protected Vector<Long> maxValues;
    // index of the most recent stable snapshot
    //    protected int baseIndex;

    // set of next batch messages being gathered in quorums
    protected NextBatchCertificate[] batches;
    // index of the lowest indexed batch
    protected int baseBatch;
    protected long baseSeqNo;
    protected long maxExecuted;
    // the last batch to be passed off to the glue

    // quorums for retransmits
    protected Quorum<Retransmit>[] retrans;
    protected long[] early;
    protected int[] retransCount;
    // quorums for load cps
    protected Quorum<LoadCPMessage> loadCPQuorum;
    // quorum for release cp
    protected Quorum<ReleaseCP> releaseQuorum;


    // added new cache per client 
    protected Hashtable<Long, Reply>[] repliesCache;
//    protected Hashtable<Long,Hashtable<Long,Reply> >[] repliesCache;

    // quorum indicating that you're ready for requests
    protected boolean readyForRequests;

    protected GlueShimInterface glue;
    private Object forwardReplyLock;
    private boolean forwardReplyArrival;
    private ForwardReply forwardReply;

    public void setGlue(GlueShimInterface g) {
        glue = g;
    }


    protected CheckPointState initialCP;

    // cache of requests to be executed
    protected Entry[] commandCache;

    private ExecutorService pool = null;


    boolean loadingCP = false;


    private int myIndex;
    private String filePrefix;

    //    private Lock maxValuesLock = new ReentrantLock();
    //private Lock objectLock = new ReentrantLock();


    public ShimBaseNode(String membership, int id,
                        byte[] initialCPToken, String filePrefix) {
        super(membership, BFT.util.Role.EXEC, id);
        this.filePrefix = filePrefix;
        myIndex = id;
        // do whatever else is necessary
        commandCache = new Entry[parameters.getNumberOfClients()];
        for (int i = 0; i < commandCache.length; i++)
            commandCache[i] = null;
        workingState = new CheckPointState(parameters, parameters.getNumberOfClients());
        workingState.setMaxSequenceNumber(0);
        workingState.addCheckpoint(initialCPToken, 0);
        initialCP = workingState;
        initialCP.markStable();
        maxValues = new Vector<Long>();
        stateSnapshots = new Hashtable<Long, CheckPointState>();
        earlyBatches = new Hashtable<Long, NextBatch>();
        stateSnapshots.put(new Long(workingState.getSequenceNumber()),
                workingState);
        workingState = new CheckPointState(parameters, workingState);
        batches = new NextBatchCertificate[BFT.order.Parameters.checkPointInterval * 3 * (BFT.order.Parameters.maxPeriods)];
        for (int i = 0; i < batches.length; i++) {
            batches[i] = new NextBatchCertificate(parameters);
        }
        //	baseIndex = 0;
        baseSeqNo = 0;
        maxExecuted = -1;

        retrans = new Quorum[parameters.getNumberOfClients()];
        retransCount = new int[retrans.length];
        for (int i = 0; i < retrans.length; i++) {
            retrans[i] = new Quorum<Retransmit>(parameters.getOrderCount(),
                    parameters.largeOrderQuorumSize(),
                    0);
            retransCount[i] = 0;
        }

        early = new long[parameters.getOrderCount()];

        loadCPQuorum = new Quorum<LoadCPMessage>(parameters.getOrderCount(),
                parameters.largeOrderQuorumSize(),
                0);

        releaseQuorum = new Quorum<ReleaseCP>(parameters.getOrderCount(),
                parameters.largeOrderQuorumSize(),
                0);

        pool = Executors.newCachedThreadPool();

        repliesCache = new Hashtable[parameters.getNumberOfClients()];
        for (int i = 0; i < parameters.getNumberOfClients(); i++) {
            //repliesCache[i] = new Hashtable<Long,Hashtable<Long,Reply>>();
            repliesCache[i] = new Hashtable<Long, Reply>();
        }

        readyForRequests = true;

        if(parameters.normalMode) {
            forwardReply = new ForwardReply();
            forwardReplyArrival = false;
            forwardReplyLock = new Object();
            assert (parameters.pipelinedBatchExecution == false);
            assert (parameters.batchSuggestion == false);
        }
    }

    public ShimBaseNode(String membership, int id,
                        byte[] initialCPToken) {
        this(membership, id, initialCPToken, "");
    }

    protected void recoveryFromLogs() {
        System.out.println(" reading cp log files");

        long seq = 0;
        int readable = 0;
        File maybe = null;
        FileInputStream fis = null;
        do {
            String suffix = "_" + myIndex + "_SHIM_CP.LOG";
            seq += BFT.Parameters.checkPointInterval;
            System.out.println("trying to locate " + filePrefix +  seq + suffix + " on disk");
            CheckPointState tmp = null;
            maybe = new File(filePrefix + seq + suffix);
            if (maybe.exists()) {
                try {
                    fis = new FileInputStream(maybe);
                    byte b[] = new byte[fis.available()];
                    fis.read(b);
                    tmp = new CheckPointState(parameters, b);
                } catch (Exception e) {
                    System.out.println("Some exception while reading from disk");
                    BFT.Debug.kill(e);
                }
                loadCheckPointState(tmp, seq);
            } else {
                break;
            }
        } while (true);

    }

    /**
     * Process the nextbatch
     **/
    synchronized protected void process(NextBatch nb) {
        //	if (fetchingstatenow >0)
//        System.err.println("Processing next batch" + nb.getSeqNo() + "/" + baseSeqNo +
//                " up to " + (baseSeqNo + batches.length) + " from " +
//                nb.getSendingReplica() + " " + nb.getTag());
        if (!amIReadyForRequests()
                && nb.getSeqNo() < baseSeqNo + batches.length) { // reject a request b/c we're not ready
            // TODO: maybe we should accept these reqs and store this
            System.out.println("Reject a request because we're" +
                    " not yet ready for it");
            return;
        }

        if (nb.getSeqNo() < baseSeqNo) {
            return;
        }
        if (!validateOrderMacArrayMessage(nb, myIndex)) {
            BFT.Debug.kill(new RuntimeException("FAILED VALIDATION! SN:" + nb.getSeqNo()));
        }
        // 	if (fetchingstatenow>0)
        // 	    System.out.println("\tprocessing that next batch");
        //Debug.println("Process next batch "+nb.getVersionNo()+ "/"+baseSeqNo+
        //	      " up to "+(baseSeqNo+batches.length) +" from "+
        //	      nb.getSendingReplica()+" with tag "+nb.getTag());

        if (nb.getSeqNo() >= baseSeqNo + batches.length) {
            // hi, i am late, do the recovery
            // meanwhile store all the incoming req
            noMoreRequests();
            // TODO
        }

        int earlycount = 0;
        if (nb.getSeqNo() >= baseSeqNo + batches.length) {
            early[(int) (nb.getSendingReplica())] = nb.getSeqNo();
            earlycount = 0;
            for (int i = 0; i < early.length; i++)
                if (early[i] >= baseSeqNo + batches.length)
                    earlycount++;
            if (earlycount >= parameters.smallOrderQuorumSize()) {
                if (!amIReadyForRequests()) {
                    lastExecuted(maxExecuted + 1);
                    for (int i = 0; i < early.length; i++)
                        early[i] = 0;
                    earlycount = 0;
                }
                // 		else{
                // 		    lastExecuted(baseSeqNo);
                // 		    for (int i = 0; i < early.length; i++)
                // 			early[i] = 0;
                // 		}
            }
            //	    return;
        }


        if (!amIReadyForRequests())
            return;

        // 	int index = (int) (nb.getVersionNo() - baseSeqNo);
        // 	index = baseIndex + index;
        // 	index = index % batches.length;
        // 	int initIndex =index;
        // 	boolean notcomplete = !batches[index].isComplete();

        // AGC:  7/11 -- now placing the nextbatch into the appropriate certificte no matter what
        int index = (int) (nb.getSeqNo() % batches.length);
        int initIndex = index;
        boolean notcomplete = !batches[index].isComplete();
        if (batches[index].previewNextBatch() != null &&
                nb.getSeqNo() > batches[index].previewNextBatch().getSeqNo()) {
            // 	    System.out.println("wrapped around the nextbach list.  replacing "+
            // 			       batches[index].previewNextBatch().getVersionNo()+
            // 			       " with "+nb.getVersionNo());
            batches[index].clear();

            int plusone = (index + 1) % batches.length;
            if (batches[plusone].previewNextBatch() != null)
                ;//		System.out.println("\tnext cp is: "+ batches[plusone].previewNextBatch().getVersionNo());
            else
                ;//System.out.println("\tthere is no next batch");
            if (batches[plusone].previewNextBatch() != null &&
                    nb.getSeqNo() == batches[plusone].previewNextBatch().getSeqNo() + batches.length - 1
                    && nb.getSeqNo() - batches.length > baseSeqNo)
                lastExecuted(baseSeqNo);
        }
        batches[index].addNextBatch(nb);

        // try to roll back completeness if appropriate
        if (notcomplete && batches[index].isComplete()) {

            // look for cached commands & put them in the nbc if appropriate
            Enumeration<Entry> missing = batches[index].getMissingEntries();
            Entry entry;
            int client = -1;
            Entry cache;
            while (parameters.speculativeForward && missing.hasMoreElements()) {
                entry = missing.nextElement();
                client = (int) entry.getClient();
                cache = commandCache[client];
                if (cache != null && entry.getDigest().equals(cache.getMyDigest())) {
                    batches[index].addCommand(client, cache);
                }
                commandCache[client] = null;
            }

            if (parameters.speculativeForward) {
                fetchCommands(batches[index], false);
                fetchCommands(batches[index], true);
            } else {
                fetchCommands(batches[index], false);
            }

            long seq = nb.getSeqNo() - 1;
            // if seq >= baseseqno, then push commit down as far as possible
            if (seq >= baseSeqNo) {
                //		System.out.println("need to commit "+seq+" first");

                NextBatchCertificate prv = batches[index];
                NextBatchCertificate tmp = prv;
                NextBatch old = null, nw = prv.getNextBatch();
                HistoryDigest hist = null;
                while (seq >= baseSeqNo && prv.isComplete()) {
                    //		    System.out.println("trying out "+seq+" above "+baseSeqNo);

                    // 		    index = (int) (seq - baseSeqNo);
                    // 		    index = baseIndex + index;
                    // 		    index = index % batches.length;

                    index = (int) (seq % batches.length);

                    tmp = batches[index];
                    old = tmp.previewNextBatch();
                    if (old == null || tmp.isComplete())
                        break;
                    hist =
                            new HistoryDigest(parameters, old.getHistory(),
                                    nw.getCommands(),
                                    nw.getNonDeterminism(),
                                    nw.getCPDigest());

                    if (nw.getHistory().equals(hist)) {
                        //			System.out.println("frocing completed!");
                        tmp.forceCompleted();
                        fetchCommands(tmp, false);
                    } else {
                        // 			System.out.println("mismatch");
                        // 			System.out.println(nw.getHistory());
                        // 			System.out.println(hist);
                        lastExecuted(baseSeqNo);
                    }
                    seq--;
                    nw = old;
                    prv = tmp;

                }

            }

        }

        // if its ready for execution then try to execute.
        if (batches[initIndex].isReadyForExecution()) {
            //	    System.out.println(initIndex+"  is ready for execute");
            if (!tryToExecute()) {
                ;//		System.out.println("failed to execute b/c we're missing an old message! "+baseIndex);
            }
        }

        //	if (baseSeqNo > maxExecuted+batches.length){
        //	    System.out.println("$$$$$$ last executed b/c baseseqno got huge "+
        //		       baseSeqNo+" "+maxExecuted);
        //	    lastExecuted(maxExecuted+1);
        //	}


    }


    protected boolean tryToExecute() {
        // 	if (fetchingstatenow > 0)

//        System.err.println(baseSeqNo +
//                " is ready for execute: " +
//                batches[(int) (baseSeqNo % batches.length)].isReadyForExecution());
        boolean res = false;
        int index = (int) (baseSeqNo % batches.length);
        while (batches[index].isReadyForExecution() && batches[index].getNextBatch().getSeqNo() == baseSeqNo) {
            acton(batches[index]);
            batches[index].clear();
            baseSeqNo += 1; // only updated on return res
//            System.err.println("\tTry to Execute done baseseqno:" + baseSeqNo);
            index = (index + 1) % batches.length;
            res = true;
        }
        return res;

    }


    protected void acton(NextBatchCertificate b) {
        NextBatch nb = b.getNextBatch();
        if (nb.getSeqNo() != baseSeqNo)
            Debug.kill("SequenceNUmbers out of whack");

        if (nb.takeCP()) {
            //	    maxValuesLock.lock();
            synchronized (maxValues) {
                //	    try{
                maxValues.add(new Long(nb.getSeqNo() + 1));
            }//finally{maxValuesLock.unlock();}
        }
//        System.err.println("Executing next batch at : " + nb.getSeqNo() +
//                " with commands: " + nb.getCommands());
        long tmpTime = System.currentTimeMillis();
        glue.exec(b.getCommands(), nb.getSeqNo(),
                nb.getNonDeterminism(), nb.takeCP());

    }


    protected void fetchCommands(NextBatchCertificate nbc, boolean fromAll) {
        // first notify everybody of the batch and the expected contents

        if (!fromAll) {

            //System.out.println("fetching with the batch completed");
            BatchCompleted bc = new BatchCompleted(parameters, nbc.getNextBatch(), myIndex);
            authenticateFilterMacArrayMessage(bc);
            sendToAllFilterReplicas(bc.getBytes());
            if (!parameters.speculativeForward) {
                return;
            }
        }
        Enumeration<Entry> entries = nbc.getMissingEntries();
        int target;
        while (entries.hasMoreElements()) {
            Entry entry = entries.nextElement();
            FetchCommand fc = new FetchCommand(parameters, nbc.getNextBatch().getSeqNo(),
                    entry, myIndex);
            target =
                    (int) ((entry.getClient() + 2) % parameters.getFilterCount());
            authenticateFilterMacArrayMessage(fc);

            if (parameters.speculativeForward)
                sendToFilterReplica(fc.getBytes(), target);
            else {
//                System.err.println("fetching " + fc.getSeqNo() + " client " +
//                        entry.getClient() + " from all");
                for (int i = 0; i < parameters.getFilterCount(); i++)
                    if (i != target) {
                        sendToFilterReplica(fc.getBytes(), i);
                        //		    System.err.println("\tfetching from "+i);
                    }
            }
        }
    }

    protected void process(CacheCommand fwd) {
        //	System.err.println("rcv : "+fwd.getEntry()+" from "+fwd.getSendingReplica());
        if (!validateFilterMacArrayMessage(fwd, myIndex))
            BFT.Debug.kill("BAD FILTER");
        commandCache[(int) fwd.getEntry().getClient()] = fwd.getEntry();

        // check to see if we're using chain forwarding of commands
        if (parameters.chainExecution)
            // if im not sending to the first guy in the chain
            if (myIndex != ((int) fwd.getEntry().getClient() + 1) % parameters.getNumberOfClients())
                sendToExecutionReplica(fwd.getBytes(), (myIndex + 1) % parameters.getNumberOfClients());
    }


    protected void process(ForwardCommand fwd) {


        if (fwd.getSeqNo() < baseSeqNo)
            return;
        if (!validateFilterMacMessage(fwd))
            BFT.Debug.kill("BAD FILTER");

        //	System.err.println("fwd command: "+fwd.getEntry());

        int index = (int) (fwd.getSeqNo() % batches.length);

        boolean notcomplete = !batches[index].isComplete();
        batches[index].addCommand(fwd.getEntry().getClient(), fwd.getEntry());
        tryToExecute();
    }


    protected void process(FetchDenied fd) {
        System.err.println("fetch denied! seqno: " + fd.getSeqNo() +
                " client: " + fd.getEntry().getClient() +
                " from " + fd.getSendingReplica());
        int index = (int) (fd.getSeqNo() % batches.length);

        if (fd.getSeqNo() < baseSeqNo || batches[index].isReadyForExecution())
            return;
        if (!validateFilterMacMessage(fd))
            BFT.Debug.kill("BAD FILTER");
        //	System.out.println("refetching!");
        if (fd.getSendingReplica() == (fd.getEntry().getClient() + 1) % parameters.getFilterCount())
            fetchCommands(batches[index], true);
    }

    /**
     * Process a read only request
     **/
    protected void process(ReadOnlyRequest req) {
        //Debug.println("Process ReadOnlyRequest: "+req.getSender()+
        //	      " id: "+req.getCore().getRequestId());
        if (!readyForRequests)
            return;
        if (!validateClientMacArrayMessage(req, myIndex)) {
            Debug.kill("should have valid client mac array");
        }

        //  need to rate limit read only requests

        // its valid, so execute it
        glue.execReadOnly((int) (req.getCore().getSendingClient()),
                req.getCore().getRequestId(),
                req.getCore().getCommand());
    }


    /**
     * Request an already taken checkpoint
     **/
    protected void process(RequestCP rcp) {
        // if i have the requested cp, send it back
        //Debug.println("Process Request CP from "+rcp.getSendingReplica());
//        System.err.println("Process Request CP from " + rcp.getSendingReplica());
        if (!validateOrderMacArrayMessage(rcp, myIndex)) {
            BFT.Debug.kill(new RuntimeException("FAILED VALIDATION! SN:"
                    + rcp.getSequenceNumber()));
        }
        CheckPointState cps = null;
        long seq = rcp.getSequenceNumber();

        Long key = new Long(seq);
        cps = (CheckPointState) stateSnapshots.get(key);
        if (cps == null && seq != 0) {
            System.out.println("no cp to return!");
            return;
        } else if (cps == null && seq == 0) {
            cps = initialCP;
        }
        if (cps != null && cps.isStable()) {
            Digest cpBytes = cps.getStableDigest();
//            System.err.println("\t sending cp back: " + seq);
            CPTokenMessage cp = new CPTokenMessage(parameters, cpBytes.getBytes(), seq, myIndex);
            authenticateOrderMacArrayMessage(cp);
            sendToOrderReplica(cp.getBytes(), (int) (rcp.getSendingReplica()));
        } else {
            Debug.kill("cant deliver the requested CP:" + seq +
                    " did you mean to get base instead of current? reason: cps != null " +
                    (cps != null) + " is stable " + ((cps != null) ? cps.isStable() : false));
        }
    }

    /**
     * release the specified checkpoint
     **/
    protected void process(ReleaseCP rcp) {
        //Debug.println("\tprocess releaseCP");
        if (!validateOrderMacArrayMessage(rcp, myIndex)) {
            BFT.Debug.kill(new RuntimeException("FAILED VALIDATION! SN:"
                    + rcp.getSequenceNumber()));
        }
        releaseQuorum.addEntry(rcp);
        if (releaseQuorum.isComplete()) {
            acton(rcp);
            releaseQuorum.clear();
        }
    }

    protected void acton(ReleaseCP rcp) {
        //Debug.println("\t releasing checkpoint at "+rcp.getSequenceNumber());
        // fetch the checkpoint, get the app chekcpoint
        long index = rcp.getSequenceNumber() - 3 * BFT.order.Parameters.checkPointInterval;
        Enumeration<Long> enume = stateSnapshots.keys();
        while (enume.hasMoreElements()) {
            Long next = enume.nextElement();
            if (index >= next.longValue()) {
                CheckPointState cps = (CheckPointState) stateSnapshots.remove(next);
                if (cps == null) {
                    return;
                }
                //		System.out.println("Instructing Release CP: "+next);
                glue.releaseCP(cps.getCheckpoint());
                File cpfile = new File(filePrefix + cps.getMaxSequenceNumber() + "_SHIM_CP.LOG");
                if (!cpfile.exists()) {
                    //		    System.out.println("looking for file "+cps.getMaxSequenceNumber()+
                    //"_"+myIndex+"_SHIM_CP.LOG");
                    cpfile = new File(filePrefix + cps.getMaxSequenceNumber() + "_" + myIndex + "_SHIM_CP.LOG");
                }
                if (cpfile.exists()) {
                    cpfile.delete();
                    //BFT.//Debug.println("DELETED CP LOG: " + cpfile.getName());
                }
            }
        }
    }

    long[] retranscount;

    synchronized protected void process(Retransmit ret) {
        if (retranscount == null) {
            retranscount = new long[parameters.getOrderCount()];
        }
        System.out.println("rcv retransmit " + retranscount[(int) ret.getSendingReplica()] +
                " from: " + ret.getSendingReplica() +
                " for " + ret.getClient() +
                " at seqno " + ret.getSequenceNumber() +
                " last executed is " + baseSeqNo +
                " max returned is " + maxExecuted +
                " ready is " + amIReadyForRequests());
        retranscount[(int) ret.getSendingReplica()]++;
        if (!validateOrderMacArrayMessage(ret, myIndex)) {
            BFT.Debug.kill(new RuntimeException("FAILED VALIDATION! " +
                    retranscount[(int) ret.getSendingReplica()] + "th retran SN:"
                    + ret.getSequenceNumber() +
                    " from " +
                    ret.getSendingReplica()));
        }
        int i = (int) ret.getClient();

        if (!retrans[i].addEntry(ret)) {
            ;//System.out.println("replacing retrans from "+i+" with"+ ret.getSequenceNumber());
        }
        if (retrans[i].isComplete()) {
            acton(ret);
            retrans[i].clear();
            System.out.println("************ successful retrans to client: " + ret.getClient());
        }
    }

    long cantretrans = 0;

    protected void acton(Retransmit ret) {


        System.out.println("acting on retransmit for " + ret.getClient() + " with max: " + maxExecuted + " and base: " + baseSeqNo + " and ret number " + ret.getSequenceNumber());
        System.out.println("client with IDs: " + (int) ret.getClient() + " " + (int) ret.getSubId());

        // fetch reply from caches
        Reply rep = null;
        System.out.println("replies caches: size=" + repliesCache[(int) ret.getClient()].size()
                + " seq=" + ret.getSequenceNumber() + " reqId=" + ret.getRequestId());
/*
        if ( repliesCache[(int)ret.getClient()].get(ret.getSequenceNumber()) != null) {
            System.out.println("replies caches: size=" + repliesCache[(int)ret.getClient()].get(ret.getSequenceNumber()).size() 
                    + " seq=" + ret.getSequenceNumber());
        }

        // if replies caches exists!
        if (repliesCache[(int)ret.getClient()].get(ret.getSequenceNumber()) != null &&
                repliesCache[(int)ret.getClient()].get(ret.getSequenceNumber()).get(ret.getRequestId()) != null){
            rep = repliesCache[(int)ret.getClient()].get(ret.getSequenceNumber()).get(ret.getRequestId());
        }
*/
        if (repliesCache[(int) ret.getClient()].get(ret.getRequestId()) != null) {
            rep = repliesCache[(int) ret.getClient()].get(ret.getRequestId());
        }

        if (rep != null) {
            if (retransCount[(int) ret.getClient()] > 5) {
                CPUpdate cpu = new CPUpdate(parameters, workingState.getCommandIndices(),
                        workingState.getSequenceNumber(),
                        myIndex);
                authenticateFilterMacArrayMessage(cpu);
                sendToAllFilterReplicas(cpu.getBytes());
            }
            // to faster client recovery, here we send all caches from this seqNo to the lattest one
            int client = (int) ret.getClient();
            long req = ret.getRequestId();
            while (rep != null) {
                System.out.println("client with IDs: " + (int) ret.getClient() + " " + (int) ret.getSubId()
                        + " replyId=" + rep.getRequestId());
                for (int i = 0; i < members.getClientReplicationCount((int) ret.getClient()); i++) {
                    authenticateClientMacMessage(rep, (int) ret.getClient(), i);
                    byte[] repBytes = rep.getBytes();
                    byte[] repBytesCopy = new byte[repBytes.length];
                    System.arraycopy(repBytes, 0, repBytesCopy, 0, repBytes.length);
                    sendToClient(repBytesCopy, (int) (ret.getClient()), i);
                }
                req++;
                rep = repliesCache[client].get(req);
            }
            return;

        } else {// this only happens when we're in the midst of fetching state
            System.out.println("\t\t\tfailed to retrans");
            cantretrans++;
            if (cantretrans > 100) {
                lastExecuted(baseSeqNo);
                cantretrans = 0;
                return;
            }
        }

        System.out.println("current workingState:" + workingState.getSequenceNumber());
        if (ret.getSequenceNumber() >= workingState.getSequenceNumber()) {
            // AGC:  7/29  commented these lines out.  need to test
            //	    if (!amIReadyForRequests())
            lastExecuted(maxExecuted + 1);
            //	    else
            //		lastExecuted(workingState.getSequenceNumber());
        }
        if (ret.getSequenceNumber() == maxExecuted) {
            long seq = ((workingState.getSequenceNumber() / BFT.order.Parameters.checkPointInterval) * BFT.order.Parameters.checkPointInterval);
            Long key = new Long(seq);
            CheckPointState cps = stateSnapshots.get(key);
            if (cps != null) {
                byte[] cpBytes = cps.getStableDigest().getBytes();
                CPTokenMessage cp = new CPTokenMessage(parameters, cpBytes, seq, myIndex);
                authenticateOrderMacArrayMessage(cp);
                sendToAllOrderReplicas(cp.getBytes());
            }
        }
        // 	if (ret.getSequenceNumber() < maxExecuted)
        // 	    ;  //send batch complete
        // superceded by cpupdate above
    }


    synchronized protected void process(LoadCPMessage lcp) {
        Debug.println("proces loadcp message " + lcp.getSequenceNumber() + " from " +
                lcp.getSendingReplica());
        if (lcp.getSequenceNumber() < baseSeqNo)
            return;
        if (!validateOrderMacMessage(lcp)) {
            BFT.Debug.kill(new RuntimeException("FAILED VALIDATION! SN:"
                    + lcp.getSequenceNumber()));
        }
        loadCPQuorum.addEntry(lcp);
        if (loadCPQuorum.isComplete()) {
            acton(lcp);
            loadCPQuorum.clear();
        }
    }

    protected Digest fetchingToken;
    protected long fetchingCP;

    protected void acton(LoadCPMessage lcp) {
        System.out.println("\t\tLoading cp at " + lcp.getSequenceNumber());
        // load the checkpoint locally.  once thats completed, fetch
        // the app checkpoint and...
        // 
        Long index = new Long(lcp.getSequenceNumber());
        CheckPointState tmp = stateSnapshots.get(index);
        if (tmp == null) {
            if (lcp.getSequenceNumber() < fetchingCP)
                return;
            // try to fetch it locally
            String suffix = "_" + myIndex + "_SHIM_CP.LOG";
            System.out.println("trying to locate " + filePrefix + lcp.getSequenceNumber() + suffix + " on disk");
            int readable = 0;
            File maybe = null;
            FileInputStream fis = null;
            try {
                maybe = new File(filePrefix + lcp.getSequenceNumber() +
                        suffix);
                fis = new FileInputStream(maybe);
                readable = fis.available();
            } catch (java.io.FileNotFoundException fnfe) {
            } catch (Exception e) {
                System.out.println("got an exception");
                e.printStackTrace();
                System.out.println(fis + " " + maybe + " " + readable);
                fis = null;
                maybe = null;
                readable = 0;
            }
            if (readable > 0) {
                try {
                    byte b[] = new byte[fis.available()];
                    fis.read(b);
                    tmp = new CheckPointState(parameters, b);
                    Digest d = new Digest(parameters, tmp.getBytes());
                    tmp.setStableDigest(d);
                    if (!d.equals(Digest.fromBytes(parameters, lcp.getToken()))) {
                        System.out.println("read: " + d);
                        System.out.println("want: " +
                                Digest.fromBytes(parameters, lcp.getToken()));
                        BFT.Debug.kill("uh oh.  not the same tokens");
                    }
                    // now cleanup all other cp files off of the disk
//					System.out.println(maybe);
//					File parent = maybe.getCanonicalFile().getParentFile();
//					System.out.println(parent);
//					LogFilter lf = new LogFilter(suffix);
//					String[] files = parent.list(lf);
//					for (int i = 0; i < files.length; i++)
//						if (new Long(files[i].replace(suffix, "")).longValue() < lcp.getSequenceNumber()){
//							System.out.println("deleteing "+files[i]);
//							new File(files[i]).delete();
//						}else
//							System.out.println("keeping "+files[i]);
                } catch (Exception e) {
                    System.out.println("Some exception while reading from disk");
                    BFT.Debug.kill(e);
                }
            } else {
                // that failed so fetch it remotely
                FetchCPMessage fcpm = new FetchCPMessage(parameters, lcp.getSequenceNumber(), myIndex);
                authenticateExecMacArrayMessage(fcpm);
                fetchingCP = lcp.getSequenceNumber();
                fetchingToken = Digest.fromBytes(parameters, lcp.getToken());
                System.out.println("\t\tfetching state from other nodes:  " + fetchingCP);
                sendToOtherExecutionReplicas(fcpm.getBytes(), myIndex);
                System.out.println(fcpm.toString());
                //sendToOtherExecutionReplicas(lcp.getBytes(), myIndex);
                return;
            }
        }
        Digest loading = new Digest(parameters, tmp.getBytes());
        Digest cpBytes = Digest.fromBytes(parameters, lcp.getToken());
        if (!cpBytes.equals(loading)) {
            Debug.kill("CP descriptors dont match. this is a local error");
        }

        loadCheckPointState(tmp, lcp.getSequenceNumber());
    }

    protected void process(FetchCPMessage fcp) {
        System.out.println("Process fetchcpmessage");
        System.out.println(fcp.toString());
        CheckPointState tmp = stateSnapshots.get(new Long(fcp.getSequenceNumber()));
        if (tmp == null || tmp.getCheckpoint() == null) {
            System.out.println("\trejecting fetch state b/c i dont have it");
            // i dont have it
            return;
        }
        byte[] bytes = tmp.getBytes();
        CPStateMessage cpsm = new CPStateMessage(bytes, fcp.getSequenceNumber(),
                myIndex);
        System.out.println("Sending state with bytes: " + new Digest(parameters, bytes));
        System.out.println("app cp: " + tmp.getCheckpoint());

        authenticateExecMacMessage(cpsm, (int) (fcp.getSendingReplica()));
        sendToExecutionReplica(cpsm.getBytes(), (int) (fcp.getSendingReplica()));
    }

    synchronized protected void process(CPStateMessage cpsm) {
        //Debug.println("Process CPStateMessage "+cpsm.getSequenceNumber());
        if (fetchingCP != cpsm.getSequenceNumber() ||
                fetchingToken == null) {
            //Debug.println("\trejecting a cpstatemessage b/c its not what we're looking for");
            return;
        }
        if (!validateExecMacMessage(cpsm)) {
            BFT.Debug.kill(new RuntimeException("FAILED VALIDATION! SN:"
                    + cpsm.getSequenceNumber()));
        }

        Digest check = new Digest(parameters, cpsm.getState());

        if (!fetchingToken.equals(check)) {
            System.out.println(cpsm.getSequenceNumber());
            System.out.println(fetchingCP);
            System.out.println(fetchingToken);
            System.out.println(check);

            Debug.kill(new RuntimeException("got back bytes that dont match"));
        }
        // here we get all the batches need to be reexecuted!!!!

        CheckPointState tmp = new CheckPointState(parameters, cpsm.getState());
        stateSnapshots.put(new Long(cpsm.getSequenceNumber()), tmp);
        loadCheckPointState(tmp, cpsm.getSequenceNumber());
    }

    protected void loadCheckPointState(CheckPointState cps, long seqno) {
        System.out.println("\t\tloading glue checkpoint at : " + seqno);
        System.out.println(cps);
        readyForRequests = true;
        glue.loadCP(cps.getCheckpoint(), seqno);
        workingState = new CheckPointState(parameters, cps);

        // 	for(int i = 0; i< batches.length; i++)
        // 	    batches[i].clear();

        //	baseIndex = 0;
        baseSeqNo = workingState.getSequenceNumber();
        System.out.println("\tLoadCPState baseseqno:" + baseSeqNo);
        maxExecuted = baseSeqNo - 1;
        //	maxValuesLock.lock();
        //	try{
        synchronized (maxValues) {
            maxValues.clear();
        }// finally {maxValuesLock.unlock();}
        fetchingToken = null;
        stateSnapshots.put(new Long(seqno), cps);
        if (amIReadyForRequests()) {
            System.out.println("\t\ttrying to execute with baseseqno at: " + baseSeqNo);
            tryToExecute();
            System.out.println("\t\tfinished executing and baseSeqNo is now " + baseSeqNo);
            CPLoaded cpl = new CPLoaded(parameters, baseSeqNo - 1, myIndex);
            authenticateOrderMacArrayMessage(cpl);
            sendToAllOrderReplicas(cpl.getBytes());
            CPUpdate cpu = new CPUpdate(parameters, workingState.getCommandIndices(),
                    workingState.getSequenceNumber(),
                    myIndex);
            authenticateFilterMacArrayMessage(cpu);
            sendToAllFilterReplicas(cpu.getBytes());
        }
    }

    int fetchingstatenow = 0;

    protected void process(FetchState fsm) {
        //Debug.println("Process FetchState from another replica ");
        if (fsm.getSendingReplica() == myIndex) {
            return;
        }
        if (!validateExecMacArrayMessage(fsm, myIndex)) {
            Debug.kill(new RuntimeException("FAILED VALIDATION!"));
        }
        System.out.println("looking for state token: " + new Digest(parameters, fsm.getToken()) + " for " + fsm.getSendingReplica());
        //	BFT.util.UnsignedTypes.printBytes(fsm.getToken());
        stateReqs.put(new String(fsm.getToken()), fsm);
        //glue.fetchState(fsm.getToken());
        fetchingstatenow++;
    }

    protected void process(AppState as) {
        //Debug.println("process appstate");
        if (!validateExecMacMessage(as))
            BFT.Debug.kill(new RuntimeException("failed validation"));
        Debug.println("process AppState: " + new Digest(parameters, as.getToken()));
        glue.loadState(as.getToken(), as.getState());
    }

    public void handle(byte[] vmbbytes) {
        VerifiedMessageBase vmb = null;
        try {
            vmb = MessageFactory.fromBytes(vmbbytes, parameters);

//            System.err.println("Got new tag " + vmb.getTag() + " Fetch:" + MessageTags.FetchCPMessage);
//            System.err.println(vmb);
            switch (vmb.getTag()) {
                case BFT.messages.MessageTags.ForwardReply:
//                    System.out.println("get forwarded reply");
                    process((ForwardReply) vmb);
                    return;
                case MessageTags.SpeculativeNextBatch:
                case MessageTags.TentativeNextBatch:
                case MessageTags.CommittedNextBatch:
//                    System.err.println("new batch from orders: " + ((NextBatch) vmb).getSeqNo());
                    process((NextBatch) vmb);
                    return;
                case MessageTags.ReleaseCP:
                    process((ReleaseCP) vmb);
                    return;
                case MessageTags.Retransmit:
                    process((Retransmit) vmb);
                    return;
                case MessageTags.LoadCPMessage:
                    process((LoadCPMessage) vmb);
                    return;
                case MessageTags.RequestCP:
                    process((RequestCP) vmb);
                    return;
                case MessageTags.FetchCPMessage:
                    process((FetchCPMessage) vmb);
                    return;
                case MessageTags.CPStateMessage:
                    process((CPStateMessage) vmb);
                    return;
                case MessageTags.FetchState:
                    process((FetchState) vmb);
                    return;
                case MessageTags.AppState:
                    process((AppState) vmb);
                    return;
                case MessageTags.ReadOnlyRequest:
                    process((ReadOnlyRequest) vmb);
                    return;
                case MessageTags.ForwardCommand:
                    process((ForwardCommand) vmb);
                    return;
                case MessageTags.FetchDenied:
                    process((FetchDenied) vmb);
                    return;
                case MessageTags.CacheCommand:
                    process((CacheCommand) vmb);
                    return;
                default:
                    Debug.kill("servershim does not handle message " + vmb.getTag());
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    // hashtable of things im asking other shims for
    protected Hashtable<String, FetchState> stateReqs = new Hashtable<String, FetchState>();

    public void requestState(byte[] b) {
        //Debug.println("processing request state call from the glue");
        FetchState fs = new FetchState(parameters, b, myIndex);
        //System.out.println("fetching ste token:");
        //	BFT.util.UnsignedTypes.printBytes(b);
        authenticateExecMacArrayMessage(fs);
        sendToOtherExecutionReplicas(fs.getBytes(), myIndex);
    }

    public void returnState(byte[] b, byte[] s) {
        //Debug.println("\t*calling returnstate issued by the glue");
        FetchState fs = stateReqs.get(new String(b));
        fetchingstatenow--;

        if (fs == null) {
            //Debug.println("sending nothing back b/c nothing in the table");
            //	    Debug.kill("WTF");
            return;
        }
        stateReqs.remove(new String(b));
        Debug.println("Sending state " + new Digest(parameters, b));
        //	BFT.util.UnsignedTypes.printBytes(b);
        AppState as = new AppState(b, s, myIndex);
        authenticateExecMacMessage(as, (int) (fs.getSendingReplica()));
        sendToExecutionReplica(as.getBytes(), (int) (fs.getSendingReplica()));
    }

    public void result(byte[] result, int clientId, long clientReqId,
                       long seqNo, boolean toCache) {
        result(result, clientId, clientReqId, seqNo, toCache, -1);
    }

    /**
     * Now the functions in the servershim interface
     **/
    public void result(byte[] result, int clientId, long clientReqId,
                       long seqNo, boolean toCache, int clientSubId) {
//        System.err.println("ShimBaseNode: sending result back to client " + clientId + " and for seqNo: " + seqNo);
        // 	Debug.println("\t\t&&&&result at "+seqNo+" for "+clientId+
        // 		      " is going to cache: "+toCache+" and is "+result.length + " bytes long");

//        System.err.println(1);
        if (toCache) { // add to the reply cache
            retransCount[clientId] = 0;
            Reply rep = new Reply(myIndex, clientReqId, result);
            authenticateClientMacMessage(rep, clientId, 0);
            //Debug.println("is there a max value: "+!maxValues.isEmpty());
            //Debug.println("current seqno: "+seqNo);
            //Debug.println("max seqno for this state: "+workingState.getMaxSequenceNumber());
            //	    maxValuesLock.lock();
            //try{
//            System.err.println(2);
            synchronized (maxValues) {
                while (!maxValues.isEmpty()
                        && seqNo >= maxValues.firstElement().longValue()) {
                    long val = maxValues.firstElement().longValue();
                    maxValues.remove(0);

                    workingState.setMaxSequenceNumber(val);
                    System.out.println("\tadding CP with " + val);
                    stateSnapshots.put(new Long(val),
                            workingState);
                    workingState = new CheckPointState(parameters, workingState);
                    //Debug.println("creating a new working state "+
                    //	       "based at: "+
                    //	       workingState.getBaseSequenceNumber());
                }
            } //finally {maxValuesLock.unlock();}
//            System.err.println(3);
            workingState.addReply(rep, seqNo, clientId);

            // add reply to replies caches
//            System.err.println(4);
//            System.err.println(" we are adding seqNo " + seqNo + " to caches for req "
//                    + rep.getRequestId());

            if (repliesCache[clientId].size() == BFT.Parameters.checkPointInterval) {
                // these replies caches should be cleared
//                System.err.println(" but first the caches are full, clear all!");
                repliesCache[clientId].clear();
            }
            // add new hashtale for seqNo if necessary
            /*Hashtable tmp = repliesCache[clientId].get(seqNo);
            if(tmp == null) {
                tmp = new Hashtable<Long,Reply>();
                repliesCache[clientId].put(seqNo,tmp);
            }
            tmp.put(rep.getRequestId(),rep);
            */
//            System.err.println(5);
            repliesCache[clientId].put(rep.getRequestId(), rep);
            Principal[] subclients = members.getClientNodes()[clientId];
            for (int i = 0; i < subclients.length; i++) {
//                System.err.println("ShimBaseNode: authenticating and sending to client "+clientId+" with subid = "+i);
                authenticateClientMacMessage(rep, clientId, i);
                sendToClient(rep.getBytes(), clientId, i);
            }
            if (seqNo > maxExecuted)
                maxExecuted = seqNo;
//            System.err.println(6);
        } else {
            WatchReply rep =
                    new WatchReply(myIndex, clientReqId, result);
            Principal[] subclients = members.getClientNodes()[clientId];

            System.out.println("for now just printing subId: " + clientSubId);
            if(parameters.normalMode) {
                authenticateClientMacMessage(rep, clientId, clientSubId);
                sendToClient(rep.getBytes(), clientId, clientSubId);

            }
            else {
                for (int i = 0; i < subclients.length; i++) {
                    authenticateClientMacMessage(rep, clientId, i);
                    sendToClient(rep.getBytes(), clientId, i);
                }
            }
        }
    }

    public void readOnlyResult(byte[] result, int clientId, long reqId) {
        //Debug.println("\t\treadonlyresult for "+clientId+" at "+reqId);
        ReadOnlyReply reply =
                new ReadOnlyReply(myIndex, reqId, result);

        Principal[] subclients = members.getClientNodes()[clientId];
        for (int i = 0; i < subclients.length; i++) {
            authenticateClientMacMessage(reply, clientId, i);
            sendToClient(reply.getBytes(), clientId, i);
        }
    }


    /**
     * send a cptoken message indexing the cptoken by seqno+1
     **/
    public void returnCP(byte[] AppCPToken, long seqNo) {
//        System.err.println("ShimBaseNode: return CP");
        CheckPointState cps = stateSnapshots.get(new Long(seqNo + 1));
        if (cps == null) {
            //Debug.println("Using the working state");
            cps = workingState;
            if (seqNo < workingState.getBaseSequenceNumber()) {
                //Debug.println("doing nothing since we've already released this checkpoint");
                return;
            }
        }
        cps.setMaxSequenceNumber(seqNo + 1);
        cps.addCheckpoint(AppCPToken, seqNo);
        Digest cpBytes = new Digest(parameters, cps.getBytes());
        //Debug.println(cps);
        //Debug.println("\t\t&&&&taking cp at "+seqNo);
        //Debug.println("\t\tcp digest is :\n"+cpBytes);

//		if (parameters.doLogging){
//        System.err.println("ShimBaseNode: make it to the logging");
        CPLogger cpl = new CPLogger(this, cps, myIndex, filePrefix);
        pool.execute(cpl);
//		} else {
//			makeCpStable(cps);
//		}
    }


    public void makeCpStable(CheckPointState cps) {
        cps.markStable();
        CPTokenMessage cp = new CPTokenMessage(parameters, cps.getStableDigest().getBytes(), cps.getMaxSequenceNumber(), myIndex);
        authenticateOrderMacArrayMessage(cp);
        sendToAllOrderReplicas(cp.getBytes());
    }


    public synchronized boolean amIReadyForRequests() {
        return readyForRequests;
    }

    public synchronized void noMoreRequests() {
        System.out.println("denying requests at max: " + maxExecuted + "base: " + baseSeqNo);
        readyForRequests = false;
        //Debug.println("Im not ready for batches anymore");
    }

    public synchronized void readyForRequests() {
        if (readyForRequests) {
            return;
        }
        System.out.println("ready for requests again, restart at " + baseSeqNo);
        readyForRequests = true;
        //System.out.println("sending cp loaded "+baseSeqNo);
        lastExecuted(baseSeqNo);
        //Debug.println("give me more stuff!");
    }

    /**
     * Upcall indicating the  last request executed by the application.
     **/
    protected long lastSentLastExecuted = 0;

    protected void lastExecuted(long seqNo) {
        //	if (seqNo != baseSeqNo)
        //  Debug.kill(new RuntimeException("Invalid seqno in last executed "+
        //				    seqNo+":"+baseSeqNo));
        long time = System.currentTimeMillis();
        if (time - lastSentLastExecuted < 2500) {
            return;
        }
        System.out.println("Last exectued at : " + time);
        //		try{throw new RuntimeException("seqNo: "+seqNo);}catch(Exception e){e.printStackTrace();}
        lastSentLastExecuted = time;
        System.out.println("sending last exected with " + seqNo);
        LastExecuted le = new LastExecuted(parameters, seqNo, myIndex);
        authenticateOrderMacArrayMessage(le);
        sendToAllOrderReplicas(le.getBytes());
        CPUpdate cpu = new CPUpdate(parameters, workingState.getCommandIndices(),
                workingState.getSequenceNumber(), myIndex);
        authenticateFilterMacArrayMessage(cpu);
        sendToAllFilterReplicas(cpu.getBytes());
    }


    public InetAddress getIP(int i, int j) {
        return getMembership().getClientNodes()[i][j].getIP();
    }

    public int getPort(int i, int j) {
        return getMembership().getClientNodes()[i][j].getPort();
    }

    public void start() {
        super.start();

        // load checkpoints and logs from disk, if possible
        recoveryFromLogs();

        System.out.println("started at : " + System.currentTimeMillis());
        CheckPointState cps = null;
        Long key = new Long((stateSnapshots.size() - 1) * BFT.Parameters.checkPointInterval);
        cps = (CheckPointState) stateSnapshots.get(key);
        //Debug.println("start!");
        if (cps != null) {
            Digest cpBytes = new Digest(parameters, cps.getBytes());
            CPTokenMessage cp = new CPTokenMessage(parameters, cpBytes.getBytes(), key, myIndex);
            authenticateOrderMacArrayMessage(cp);
            System.out.println("spontaneously sending CPtoken to all order nodes");
            sendToAllOrderReplicas(cp.getBytes());
        }
        // 	LastExecuted le = new LastExecuted(0, myIndex);
        // 	authenticateOrderMacArrayMessage(le);
        // 	sendToAllOrderReplicas(le.getBytes());
    }


    //	private void authenticateHere(){
    //		System.err.println("BFT/serverShim/ShimBAseNode authenticate here");
    //	}

    public int getMyExecutionIndex() {
        return myIndex;
    }

    public byte[] waitForwardedReply() {
        byte[] command = null;
//        System.out.println(40);

        synchronized (forwardReplyLock) {
//            System.out.println(41);

            while (!forwardReplyArrival) {
                try {
                    forwardReplyLock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
//            System.out.println(42);

            command = forwardReply.getCommand();
            forwardReplyArrival = false;
            forwardReplyLock.notify();
        }
//        System.out.println(43);

        return command;
    }

    private void process(ForwardReply vmb) {
        int clientId = (int) vmb.getClientId();
//        System.out.println(50);

        synchronized (forwardReplyLock) {
//            System.out.println(51);

            while (forwardReplyArrival) {
                try {
                    forwardReplyLock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
//            System.out.println(52);

            forwardReplyArrival = true;
            forwardReply = vmb;
            forwardReplyLock.notify();
        }

//        System.out.println(53);

    }
}

