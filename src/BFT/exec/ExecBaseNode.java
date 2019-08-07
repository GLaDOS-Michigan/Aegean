package BFT.exec;

import BFT.BaseNode;
import BFT.Debug;
import BFT.clientShim.ClientShimBaseNode;
import BFT.exec.TimingLogger.FakeTimingLogger;
import BFT.exec.TimingLogger.TimingLogger;
import BFT.exec.glue.CRGlueThread;
import BFT.exec.glue.GeneralGlue;
import BFT.exec.glue.GeneralGlueTuple;
import BFT.exec.glue.UnreplicatedGlueThread;
import BFT.exec.messages.*;
import BFT.messages.*;
import BFT.messages.MessageTags;
import BFT.network.PassThroughNetworkQueue;
import BFT.network.netty2.ReceiverWrapper;
import BFT.network.netty2.SenderWrapper;
import BFT.util.Role;
import merkle.IndexedThread;
import merkle.MerkleTreeException;
import merkle.MerkleTreeInstance;
import util.UnsignedTypes;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ExecBaseNode extends BaseNode implements ReplyHandler {

    transient protected FilteredQuorum[] filteredQuorums;
    public transient long currentView = 0;
    transient private int primary = 0;
    public transient int myIndex;
    transient private LinkedBlockingQueue<VerifiedMessageBase> pendingRequests = new LinkedBlockingQueue<VerifiedMessageBase>();
    transient private LinkedBlockingQueue<VerifiedMessageBase> executeBatchRequests = new LinkedBlockingQueue<VerifiedMessageBase>();
    transient private LinkedBlockingQueue<RequestCore> pendingClientRequests = new LinkedBlockingQueue<RequestCore>();

    transient private CreateBatchThread createBatchThread = new CreateBatchThread();
    transient private ExecutionThread executionThread = new ExecutionThread();
    transient private ExecuteBatchThread executeBatchThread = new ExecuteBatchThread();
    public transient Object executeLock = new Object();
    transient private ExecLogger logger = new ExecLogger(this);
    transient private ExecLogCleaner logCleaner;
    transient private RequestHandler requestHandler;
    transient private RequestFilter filter;
    transient private GeneralGlue glue;
    public transient ReplyCache replyCache = null;
    public transient long lastSeqNoOrdered = 0; //Used to decide what should be the next seq no of the new batch by primary (see  createBatch method)
    transient private long lastSeqNoExecuted = 0; //Used to see whether the new batchExecute request is sequential or not. (see process(ExecuteBatchMessage)
    transient private long lastSeqNoVerified = 0; //It is used to mark the latest verified batch and it is helpful to reset other variables like lastSeqNoOrdered and lastSeqNoExecuted.
    // This variable is set while we are sending the replies at the end of a batch. (see sendAllReplies)
    //In verifier communication and exec internals, middle execs use version number as a process tracking, while backend uses seq no
    transient private long lastVersionNoExecuted = 0;
    transient private long lastVersionNoVerified = 0;
    transient private long lastSeqNoToBeVerified = 0;
    public transient TreeMap<Long, ExecuteBatchMessage> requestCache = new TreeMap<Long, ExecuteBatchMessage>();
    transient private HashMap<Long, Digest> statesToFetch = new HashMap<Long, Digest>();
    transient private TreeMap<Long, Digest> statesToVerify = new TreeMap<Long, Digest>();
    transient Digest hashVerified = null;
    transient private boolean execSequential = false;
    transient private QuorumSet<VerifyResponseMessage> verifierQuorum;
    public transient long[] lastRequestIdOrdered = null;
    public transient FakeFailureDetector fd;
    transient private boolean isMiddle = false;

    transient ArrayList<FilteredRequestCore> batch = new ArrayList<FilteredRequestCore>();
    transient private Random rand = new Random(1234);

    transient private HashSet<Long> rollbacks = new HashSet<Long>();
    transient private static byte[] emptyHash;

    transient private long lastNoOPTime = System.currentTimeMillis();

    transient private TimingLogger requestLogger;
    transient private TimingLogger messageLogger;
    transient private TimingLogger fillingLogger;
    transient private TimingLogger waitingLogger;

    transient public static ExecBaseNode singletonExec;
    transient public HashMap<Long, Long> versionNoToSeqNO = new HashMap<>();
    transient public Object verificationLock = new Object();
    transient public boolean sliceVerified = false;
    transient public boolean verifyResponseReceived = false;
    transient public AtomicInteger numberOfNestedRequestInTheSlice = new AtomicInteger(0);
    transient public AtomicInteger oldNestedGroupNo = new AtomicInteger(0);
    transient public AtomicInteger newNestedGroupNo = new AtomicInteger(0);
    transient public AtomicInteger tmpPreferredBatchSize = new AtomicInteger(0);
    transient public AtomicBoolean needBatch = new AtomicBoolean(true);
    transient public int finalNestedCount;
    transient public HashMap<Integer, LinkedBlockingQueue<RequestCore>> currentGroupRequests = new HashMap<>();
    transient public GroupClientRequests groupClientRequests;
    transient private HashMap<Integer, GroupClientRequests> filterGroupMap = new HashMap<>();
    transient private HashMap<Integer, FilteredQuorumForGroup> groupMap = new HashMap<>();
    transient private int lastRemovedGroupId = 0;
    transient private int maxPendingRequestPerClient = 128;
    transient public byte[] wallStateHash;
    transient private HashMap<Integer, byte[]> hashMap = new HashMap<>();
    transient private HashMap<Integer, GoodQuorum<BatchSuggestion>> groupQuorumMap = new HashMap<>();
    transient private int lastRemovedGroupQuorumId;
    transient private boolean[] forwardReplyArrivalArray;
    transient private ForwardReply[] forwardReplies;
    transient private Object[] forwardReplyLocks;
    transient private Hashtable<Integer, GoodQuorum<ClientRequest>>[] replicatedClients;
    transient private int[] lastRemovedQuorums;
    transient private UnreplicatedGlueThread availableUnreplicatedThreads;

    public ExecBaseNode(String membershipFile, int id, boolean isMiddleServer) {
        super(membershipFile, BFT.util.Role.EXEC, id);

        if(parameters.unreplicated) {
            System.out.println("unreplicated backend");
            assert (!isMiddleServer);
            assert (parameters.pipelinedBatchExecution == false);
            assert (parameters.noOfThreads == 16);//Since we use max 16 threads
            assert (parameters.primaryBackup);//We implement unreplicated on top of primary backup
            assert (createBatchThread.unreplicatedMode);
            parameters.useDummyTree = true;
            if(id != 0) {
                throw new RuntimeException("only one replica will run in unreplicated mode");
            }
        }

        logCleaner = new ExecLogCleaner(parameters);
        emptyHash = new byte[parameters.digestLength];
        MerkleTreeInstance.init(parameters, 8, parameters.noOfObjects, 3, parameters.parallelExecution);
        replyCache = new ReplyCache(parameters);
        myIndex = id;
        if(parameters.normalMode) {
            if(isMiddleServer) {
                System.out.println("isMiddle is overwritten because of normal mode");
            }
            isMiddle = false;
        } else {
            isMiddle = isMiddleServer;
        }

        replicatedClients = new Hashtable[parameters.getNumberOfClients()];
        lastRemovedQuorums = new int[parameters.getNumberOfClients()];
        for (int i = 0; i < parameters.getNumberOfClients(); i++) {
            replicatedClients[i] = new Hashtable<Integer, GoodQuorum<ClientRequest>>();
        }

        filteredQuorums = new FilteredQuorum[parameters.getNumberOfClients()];//TODO ?? TO set how many filters are enough maybe and such things
        for (int i = 0; i < filteredQuorums.length; i++) {
            filteredQuorums[i] = new FilteredQuorum(parameters.smallFilterQuorumSize(),
                    parameters.mediumFilterQuorumSize(), parameters.largeFilterQuorumSize(),
                    parameters.getFilterCount(), parameters);
        }
        verifierQuorum = new QuorumSet<VerifyResponseMessage>(parameters.getVerifierCount(),
                parameters.largeVerifierQuorumSize(), 0); //TODO set how many verifiers are enoguh and such things
        lastRequestIdOrdered = new long[parameters.numberOfClients]; //last request of each client
        for (int i = 0; i < lastRequestIdOrdered.length; i++) {
            lastRequestIdOrdered[i] = -1;
        }

        // TODO do we need all of these threads for a given execution or can we change it depending on sequential v parallel v parallel pipelined?
        createBatchThread.start(); //TODO check its run method
        executionThread.start(); //TODO check its run method
        executeBatchThread.start(); //TODO check its run method
        if (parameters.doLogging) {
            logCleaner.start();
            logger.start();
        }
        this.requestLogger = new FakeTimingLogger();
        this.messageLogger = new FakeTimingLogger(); //"messageReceived", 1000);
        this.fillingLogger = new FakeTimingLogger(); //"fillingTime", 1000);
        this.waitingLogger = new FakeTimingLogger(); //"waitingTime", 1000);
        this.requestLogger.logStart();
        this.messageLogger.logStart();
        singletonExec = this;

        if(parameters.normalMode) {
            forwardReplies = new ForwardReply[parameters.noOfThreads];
            forwardReplyArrivalArray = new boolean[parameters.noOfThreads];
            forwardReplyLocks = new Object[parameters.noOfThreads];
            assert (parameters.pipelinedBatchExecution == false);
            assert (parameters.batchSuggestion == false);
            assert (parameters.backendAidedVerification == false);


            for(int i = 0; i < parameters.noOfThreads; i++) {
                forwardReplies[i] = new ForwardReply();
                forwardReplyArrivalArray[i] = false;
                forwardReplyLocks[i] = new Object();
            }
        }
    }

    public ExecBaseNode(String membershipFile, int id) {
        this(membershipFile, id, false);
    }

    private synchronized void setLastVersionNoVerified(long versionNo) {
        this.lastVersionNoVerified = versionNo;
    }

    //TODO why is this not used anywhere?
    private synchronized long getLastVersionNoVerified() {
        return this.lastVersionNoVerified;
    }

    private synchronized void setLastSeqNoOrdered(long seqNo) {
        this.lastSeqNoOrdered = seqNo;
    }

    private synchronized long getLastSeqNoOrdered() {
        return this.lastSeqNoOrdered;
    }

    public synchronized void setLastVersionNoExecuted(long versionNo) {
        this.lastVersionNoExecuted = versionNo;
    }

    //TODO why is this not used anywhere?
    private synchronized long getLastVersionNoExecuted() {
        return this.lastVersionNoExecuted;
    }

    private synchronized void setCurrentView(long view) {
        this.currentView = view;
        this.primary = (int) this.currentView % parameters.getExecutionCount();
    }

    public synchronized long getCurrentView() {
        return this.currentView;
    }

    private synchronized int getPrimary() {
        return this.primary;
    }

    public void start(RequestHandler handler, RequestFilter filter) {
        this.requestHandler = handler;
        this.filter = filter;
        this.glue = new GeneralGlue(handler, filter, parameters.noOfThreads, parameters, isMiddle, myIndex, this);

        SenderWrapper sendNet = new SenderWrapper(this.getMembership(), 1);
        this.setNetwork(sendNet);

        Role[] roles = new Role[4];
        roles[0] = Role.EXEC;
        roles[1] = Role.FILTER;
        roles[2] = Role.CLIENT;
        roles[3] = Role.VERIFIER;

        PassThroughNetworkQueue ptnq = new PassThroughNetworkQueue(this);
        ReceiverWrapper receiveNet = new ReceiverWrapper(roles, this.getMembership(), ptnq, 1);
        if (parameters.primaryBackup) {
            fd = new FakeFailureDetector(myIndex, parameters);//TODO convert back to normal Failure Detector
            new HeartBeatThread().start();
        }
    }

    public void result(byte[] reply, RequestInfo info) {
        if(parameters.unreplicated) {
            //System.out.println("Send to all replicated clients");
            for (int i = 0; i < members.getClientReplicationCount(info.getClientId()); i++) {
                Debug.debug(Debug.MODULE_EXEC, "Sending to client (%d, %d)", info.getClientId(), i);
                Reply rep = new Reply(myIndex, info.getRequestId(), reply);
                this.sendToClient(rep.getBytes(), info.getClientId(), i);
            }
            return;
        }
        Thread t = Thread.currentThread();
        int id = 0;
        if (t instanceof IndexedThread) {
            id = ((IndexedThread) t).getIndex();
        }

        if(parameters.CFTmode) {
            MerkleTreeInstance.getShimInstance().addOutput(reply, id);
        }

        if (replyCache == null)
            Debug.error(Debug.MODULE_EXEC, "lcosvse: replyCache is null!");
        assert (reply != null && info != null);
//        System.err.println("reply handler result");
        replyCache.addReply(info.getClientId(), info.getSubId(), info.getRequestId(), info.getSeqNo(),
                reply);
    }

    private synchronized void preProcess(FilteredBatchSuggestion fbs) {
        int sender = fbs.getSender();
//        System.out.println("preprocess filtered batch suggestion from id: " + sender);
        GroupClientRequests gr =  (GroupClientRequests) fbs.getGcr();
//        System.out.println("Hope request group is the same: " + gr);
        int newNestedGroupNo = gr.getNewNestedGroupNo();
        FilteredQuorumForGroup quorum = groupMap.get(newNestedGroupNo);
        if(quorum != null && quorum.isComplete()) {
//            System.out.println("1");
            return;

            /*
            There is no resending of client requests. If client couldn't get a response for its request, it should send normal
            request after resolving speculation and any possible divergence. Therefore, there is nothing to do here in terms
            of retransmitting the client request but think about other things needs to be done.
             */
        }
        else {
//            System.out.println("2");

            if(quorum == null) {
//                System.out.println("3");
                quorum = new FilteredQuorumForGroup(parameters.smallFilterQuorumSize(),
                        parameters.mediumFilterQuorumSize(), parameters.largeFilterQuorumSize(),
                        parameters.getFilterCount(), parameters);
                groupMap.put(newNestedGroupNo, quorum);
                while (newNestedGroupNo > lastRemovedGroupId + maxPendingRequestPerClient) {
                    // remove old quorum from hashtable
                    groupMap.remove(lastRemovedGroupId++);
                }
            }
//            System.out.println("4");

            quorum.add(gr, sender);
            if (!quorum.isComplete()) {
//                System.out.println("not yet complete: " + newNestedGroupNo);
                return;
            }

//            System.out.println("wow complete quourum: " + newNestedGroupNo);


//            int fakeSubID = (int) req.getEntry().getFakeSubId();
//            //System.out.println("fakeSubid: " + fakeSubID);
//            int subId = fakeSubID % 10;
            int suggestedBatchSize = gr.getNumberOfNestedRequestInTheSlice();

           // req.getEntry().setSubId(subId);
           // LinkedBlockingQueue groupRequests = getRequestsForTheGroup(groupNo);
           // groupRequests.put(req);
           // if (groupRequests.size() == suggestedBatchSize) {
            tmpPreferredBatchSize.set(suggestedBatchSize);
            LinkedBlockingQueue<RequestCore> tmp = new LinkedBlockingQueue<>();
            byte[][] requests = gr.getRequests();

            for(int i = 0; i < requests.length; i++) {
                if(requests[i] != null) {
                    RequestCore rc = new FilteredRequestCore(requests[i], parameters);
                    tmp.add(rc);
                }
            }

            tmp.drainTo(pendingClientRequests);
            groupMap.remove(newNestedGroupNo);
           //     removeGroupFromTheHash(groupNo);
        }
    }

    private void preProcess(FilteredRequest request) {
        Debug.fine(Debug.MODULE_EXEC, "preProcess(FilteredRequest) from sender %d\n",
                request.getSender());
        Debug.fine(Debug.MODULE_EXEC, "Get filtered request from client %d\n", request.getSender());
        for (int i = 0; i < request.getCore().length; i++) {
            FilteredRequestCore tmp = request.getCore()[i];
            preProcess(tmp, (int) request.getSender());
        }
    }

    protected void preProcess(FilteredRequestCore req, int sender) {
        Debug.info(Debug.MODULE_EXEC, "ProcessFilterCore (sender,reqID)=(%d,%d)\n",
                req.getSendingClient(), req.getRequestId());
        FilteredRequestCore rc = req;

        int client = rc.getSendingClient();

        FilteredQuorum fq = filteredQuorums[client];
        // TODO Quorum size checks as else ifs?
        synchronized (fq) {
            fq.add(rc, sender);

            Debug.debug(Debug.MODULE_EXEC, "In FilteredRequestCore preProcess()");
            if (fq.small() && tryRetransmit(rc)) {
                Debug.info(Debug.MODULE_EXEC, "exec retransmitting(%d.%d)",
                        rc.getSendingClient(), rc.getRequestId());
                fq.clear();
                return;
            }
            // if the quorum is large then process the "cleaned" requestcore
            if (fq.medium() && myIndex == getPrimary()) {
                Debug.info(Debug.MODULE_EXEC, "Put filter request to queue (%d.%d)",
                        rc.getSendingClient(),
                        rc.getRequestId());
                if (this.lastRequestIdOrdered[client] >= rc.getRequestId()) {
                    Debug.fine(Debug.MODULE_EXEC, "whatever (%d.%d)\n", rc.getSendingClient(),
                            rc.getRequestId());
                    fq.clear();
                    return;
                } else {
                    this.lastRequestIdOrdered[client] = rc.getRequestId();
                }
                try {
                    Debug.fine(Debug.MODULE_EXEC, "Adding a request to pendingClientRequests (sender,reqID)=(%d,%d)\n"
                            , req.getSendingClient(), req.getRequestId());
                    //TODO I think it is okay if I remove the first branch of the if statement
                    if(!isMiddle && parameters.batchSuggestion)
                    {
                        int fakeSubID = (int) req.getEntry().getFakeSubId();
                        //System.out.println("fakeSubid: " + fakeSubID);
                        int subId = fakeSubID % 10;
                        int suggestedBatchSize = (fakeSubID / 10) % 100;
                        int groupNo = (fakeSubID / 1000);

                        req.getEntry().setSubId(subId);
                        LinkedBlockingQueue groupRequests = getRequestsForTheGroup(groupNo);
                        groupRequests.put(req);
                        if (groupRequests.size() == suggestedBatchSize) {
                            tmpPreferredBatchSize.set(suggestedBatchSize);
                            groupRequests.drainTo(pendingClientRequests);
                            removeGroupFromTheHash(groupNo);
                        }
                        System.out.println("new group no: " +  groupNo + ", numNestedRequest: " + suggestedBatchSize);
                    }
                    else {
                        this.pendingClientRequests.put(req);
                    }
                    //System.out.println("pending size1: " + pendingClientRequests.size());
                    //System.out.println("pending size2: " + pendingClientRequests.size());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                fq.clear();
                return;
            }

            if (fq.medium()) {
                fq.clear();
                return;
            }
        }
    }

    private synchronized void removeGroupFromTheHash(int groupNo) {
        currentGroupRequests.remove(groupNo);
    }

    private synchronized LinkedBlockingQueue getRequestsForTheGroup(int groupNo) {
        LinkedBlockingQueue group = currentGroupRequests.get(groupNo);
        if(group == null) {
            group = new LinkedBlockingQueue<>();
            currentGroupRequests.put(groupNo, group);
        }

        return group;
    }



    private void trySendNoOP() {
        if (System.currentTimeMillis() - lastNoOPTime > 5000) {
            NoOPMessage noop = new NoOPMessage(parameters, myIndex);
            this.authenticateVerifierMacArrayMessage(noop);
            this.sendToAllVerifierReplicas(noop.getBytes());
            Debug.info(Debug.MODULE_EXEC, "send noop=%s\n", noop);
            lastNoOPTime = System.currentTimeMillis();
        }
    }

    private boolean tryRetransmit(RequestCore req) {
        if (lastVersionNoExecuted == 0) {
            return false;
        }
        Debug.debug(Debug.MODULE_EXEC, "tryRetransmit (sender,reqID)=(%d.%d)",
                req.getSendingClient(),
                req.getRequestId());

        ReplyCache.ReplyInfo lastReply = replyCache.getLastReply(req.getSendingClient());
        Debug.debug(Debug.MODULE_EXEC, "lastReply=%d, curRequest=%d", lastReply.getRequestId(), req.getRequestId());
        if (req.getRequestId() <= lastReply.getRequestId()) {
            ReplyCache.ReplyInfo reply = replyCache.getReplyForRequestID(req.getRequestId(), req.getSendingClient());
            if (reply != null) {
                Debug.info(Debug.MODULE_EXEC, "Retransmit reply (%d.%d) for subId %d\n",
                        req.getSendingClient(), req.getRequestId(), req.getSubId());
                Debug.debug(Debug.MODULE_EXEC, "Previously reply (%d.%d) sent to subId %d\n",
                        reply.getClientId(), reply.getRequestId(), reply.getSubId());
                if (replyCache.isReplyVerified(req.getSendingClient())) {
                    Reply rep = new Reply(myIndex, reply.getRequestId(), reply.getReply());
                    this.authenticateClientMacMessage(rep, reply.getClientId(), req.getSubId());
                    if (lastReply.getReply().length == 0) {
                        Debug.kill("reply zero");
                    }
                    // when the client has multiple replicas, note that the replica requesting
                    // retransmit might be different from the replica that's last replied to.
                    // so here req.getSubId() should be used instead of lastReply.getSubId().
                    this.sendToClient(rep.getBytes(), reply.getClientId(), req.getSubId());
                } else {
                    Debug.info(Debug.MODULE_EXEC, "Reply for (%d.%d) not verified yet\n",
                            reply.getClientId(), reply.getRequestId());
                    if (!parameters.primaryBackup) {
                        trySendNoOP();
                    }
                }
                return true;
            } else {
                Debug.debug(Debug.MODULE_EXEC, "client %d is seriously behind.\n", req.getSendingClient());
            }
        }
        return false;
    }

    // This matters for state transitions, where a replica is requesting a state from another node
    private void fetchState(long targetSeqNo, Digest digest) {
        Debug.info(Debug.MODULE_EXEC, "Exec Go FetchState %d currentStableSeqNo=%d\n", targetSeqNo,
                this.lastVersionNoVerified);
        try {
            this.statesToFetch.put(targetSeqNo, digest);
            Debug.debug(Debug.MODULE_EXEC, "lastSeqNoExecuted=%d lastSeqNoVerified=%d", this.lastVersionNoExecuted, this.lastVersionNoVerified);
            if (this.lastVersionNoExecuted > this.lastVersionNoVerified) {
                Debug.info(Debug.MODULE_EXEC, "Exec rollback to %d\n", this.lastVersionNoVerified);
                MerkleTreeInstance.getShimInstance()
                        .rollBack(glue.getMerkleTreeVersionLinkedWithRequestNo(this.lastVersionNoVerified));
                Debug.debug(Debug.MODULE_EXEC, "Setting lastSeqNoExecuted to lastSeqNoVerified, %d", this.lastVersionNoVerified);
                this.setLastVersionNoExecuted(this.lastVersionNoVerified);
            }
            FetchStateMessage message = new FetchStateMessage(parameters, this.lastVersionNoVerified,
                    targetSeqNo, myIndex);
            this.authenticateExecMacArrayMessage(message);
            this.sendToOtherExecutionReplicas(message.getBytes(), myIndex);
        } catch (MerkleTreeException e) {
            e.printStackTrace();
        }
    }

    public void setHistoryHash(byte[] historyHash) {
        replyCache.setHistoryHash(historyHash);
    }

    public ExecLogger getLogger() {
        return logger;
    }

    public TreeMap<Long, Digest> getStatesToVerify() {
        return statesToVerify;
    }

    public void setLastSeqNoExecuted(long lastSeqNoExecuted) {
        this.lastSeqNoExecuted = lastSeqNoExecuted;
    }

    public long getLastSeqNoExecuted() {
        return lastSeqNoExecuted;
    }

    public int getNumClients() {
        int numClients = parameters.pipelinedBatchExecution ?
                parameters.numPipelinedBatches * parameters.numWorkersPerBatch :
                parameters.noOfThreads;
        return numClients;
    }

    public void setLastSeqNoVerified(long lastSeqNoVerified) {
        this.lastSeqNoVerified = lastSeqNoVerified;
    }

    public byte[] getHistoryHash() {
        return replyCache.getHistoryHash();
    }

    private class CreateBatchThread extends Thread {

        transient private final boolean unreplicatedMode = parameters.unreplicated;
        transient private final int PREFERED_BATCH_SIZE = setBatchSize();
        transient private final int MIN_BATCH_SIZE = parameters.execBatchMinSize;
        //Now used as the max time for exponential backoff
        transient private final long MAX_FILL_TIME = parameters.execBatchWaitTime + 50;
        transient private long batchFillTime = parameters.execBatchWaitTime;

        // The increment to use to adjust the batch sizing
        transient private double increment = batchFillTime / 10;
        //Booleans to prevent hysteresis. True is wait time too large, false is wait time too small.
        transient private int lastBatchSize = -1;
        transient private int thisBatchSize = -1;
        transient private boolean decreasedLast = true;
        transient private int count = 0;

        public int setBatchSize() {
            System.out.println("BatchSuggestion: " + parameters.batchSuggestion);
            System.out.println("WaitTime: " + batchFillTime);
            int numThreads = parameters.numWorkersPerBatch;
            if(parameters.isMiddleClient) {
                System.out.println("serving to a middle client so needs the middle's #threads as batch size. It should be" +
                        "set at property file");
                System.out.println("number of clients: " + parameters.getNumberOfClients() + "\nPREFERRED_BATCH_SIZE: " + parameters.execBatchSize);

                return parameters.execBatchSize;
            }

            if (parameters.pipelinedBatchExecution) {
                numThreads *= parameters.numPipelinedBatches;
                System.out.println("number of total threads: " + numThreads);
            }

            if (parameters.roundBatchSize) {
                int batchSize = ((parameters.execBatchSize + numThreads - 1) / numThreads) * numThreads;
                System.out.println("PREFERRED_BATCH_SIZE: " + batchSize);
                return batchSize;
            }
            System.out.println("PREFERRED_BATCH_SIZE: " + parameters.execBatchSize);
            return parameters.execBatchSize;
        }

        @Override
        public void run() {
            try {
                if(unreplicatedMode) {
                    runInUnreplicatedMode();
                }
                LinkedList<RequestCore> batch = new LinkedList<RequestCore>();
                LinkedList<RequestCore> waiting = new LinkedList<RequestCore>();
                LinkedList<RequestCore> pending = new LinkedList<RequestCore>();

                Map<Object, Boolean> readMap = new HashMap<Object, Boolean>();
                Map<Object, Boolean> writeMap = new HashMap<Object, Boolean>();

                double realBatchFillTime = batchFillTime;
                int oldWaitingSize = waiting.size();
                double startFilling = System.currentTimeMillis();
                double endFilling;
                tmpPreferredBatchSize.set(PREFERED_BATCH_SIZE);

                while (true) {
                    // Run this once to ensure no conflicts
                    if (!parameters.runMixerAfter) {
                        runMixerOnRequests(batch, waiting, readMap, writeMap);
                    } else {
                        batch.addAll(waiting);
                    }
                    if (!batch.isEmpty() && waitingLogger.hasStarted()) {
                        waitingLogger.logEnd();
                    }

                    // Wait for requests
                    long startTime = System.currentTimeMillis();
                    long stallTimer = 0;
                    long timeSpent;
                    batchFillTime = (long) realBatchFillTime;
                    replyCache.printReplies();
                    Object[] myArray = batch.toArray();
//                    for (Object obj : myArray) {
//                        System.out.println(obj.toString());
//
//                    }
                    Debug.debug(Debug.MODULE_EXEC, "Pending Length: %d", pending.size());
                    /* This throws a cannot find symbol error for myReq, 
                        no clue why... 
                    Debug.debug(Debug.MODULE_EXEC, "Printing batch...");
                    for (RequestCore myReq : batch) {
                        int no = myReq.getVersionNo();
                        Debug.debug(Debug.MODULE_EXEC, "SeqNo:%d", no);
                    }
                    Debug.debug(Debug.MODULE_EXEC, "Printing waiting...");
                    for (RequestCore req : waiting) {
                        int no = req.getVersionNo();
                        Debug.debug(Debug.MODULE_EXEC, "SeqNo:%d", no);
                    }
                    Debug.debug(Debug.MODULE_EXEC, "Printing pending...");
                    for (RequestCore req : pending) {
                        int no = req.getVersionNo();
                        Debug.debug(Debug.MODULE_EXEC, "SeqNo:%d", no);
                    }
                    */

                    //batchFillTime = 1000;
                    long tmpBathcTime = batchFillTime;
                    while (batch.size() < calculateLimit(waiting.size()) || executeBatchRequests.size() > 1) {
                        //System.out.println("tmpPreferredBatchSize: " + tmpPreferredBatchSize.get());
                        timeSpent = (System.currentTimeMillis() - startTime);
                        if (timeSpent > tmpBathcTime) {
                            if (needBatch() )
                                 break;
                            else {
                                startTime = System.currentTimeMillis();
                                timeSpent = 0;
                                //tmpBathcTime /= 2;
                            }
                        }
                        
                        RequestCore one = pendingClientRequests.poll(tmpBathcTime - timeSpent,
                                TimeUnit.MILLISECONDS);
                        //System.out.println(1);
                        if (one != null) {
                            //System.out.println("tmpPreferredBatchSize: " + tmpPreferredBatchSize.get() + ", waitingSize: " + waiting.size() );
                            Debug.info(Debug.MODULE_EXEC, "TIMINGS[EBN]: Thread %d reqRec %d", one.hashCode(), System.nanoTime());
                            Debug.debug(Debug.MODULE_EXEC, "\tCreateBatchThread: polling one request");
                            if (waitingLogger.hasStarted()) {
                                waitingLogger.logEnd();
                            }
                            if (batch.isEmpty()) {
                                fillingLogger.logStart();
                            }
                            //System.out.println(2);

                            //
                            pending.clear();
                            pending.add(one);
                            int numStillNeeded = PREFERED_BATCH_SIZE - 1 - batch.size();
                            if(numStillNeeded < 0) {
                                numStillNeeded %= PREFERED_BATCH_SIZE;
                            }

                            int totalPendingSize = pendingClientRequests.size();
                            int drainSize = numStillNeeded;

                            if(totalPendingSize >= numStillNeeded + PREFERED_BATCH_SIZE) {
                                int remainingSize = totalPendingSize - numStillNeeded;
                                drainSize = totalPendingSize - remainingSize % PREFERED_BATCH_SIZE;
                            }

                            if (drainSize > 0) {
                                pendingClientRequests.drainTo(pending, drainSize);
                            }
				
			    //System.out.println("pendingSize: " + pendingClientRequests.size() + ", waiting size: " + waiting.size() + ", batch size: " + batch.size());

                            if (!parameters.runMixerAfter) {
//                                System.out.println("running mixer before creating batch");
                                addAllSafeToBatch(batch, waiting, pending, readMap, writeMap);
                            } else {
                                //System.out.println("to be sure");
                                batch.addAll(pending);
                            }
                            //System.out.println("pendingClientRequests.size2: " + pendingClientRequests.size() + ", waiting size: " + waiting.size());

                            //System.out.println("batchSize: " + batch.size());
                        } else {
                            Debug.debug(Debug.MODULE_EXEC, "RequestCore one is null");
                            //break;
                        }
                    }
                    oldWaitingSize = waiting.size();
                    //if(executeBatchRequests.size() > 1 && batch.size() < PREFERED_BATCH_SIZE )
                    //continue;
                    endFilling = System.currentTimeMillis();
                    Debug.debug(Debug.MODULE_EXEC, "[Batching Thread] batch size: %f waiting: %f", (double) thisBatchSize, (double) (endFilling - startFilling));
                    startFilling = System.currentTimeMillis();
                    if (parameters.dynamicBatchFillTime) {
                        lastBatchSize = thisBatchSize;
                        thisBatchSize = batch.size();

                        if (thisBatchSize < PREFERED_BATCH_SIZE) {

                            // If we didn't get a big enough batch then increase the wait time.
                            realBatchFillTime = Math.min(realBatchFillTime + increment, MAX_FILL_TIME);
                            Debug.warning(Debug.MODULE_EXEC, "[Batching Thread] Now Increasing wait time to %f\n", realBatchFillTime);
                            if (decreasedLast) {
                                increment *= .95;
                            }
                            decreasedLast = false;
                        } else if (lastBatchSize >= PREFERED_BATCH_SIZE &&
                                thisBatchSize >= PREFERED_BATCH_SIZE) {

                            // If we had enough time, try decreasing the batch size
                            realBatchFillTime = Math.max(5, realBatchFillTime - increment);
                            Debug.warning(Debug.MODULE_EXEC, "[Batching Thread] Decreasing wait time to %f\n", realBatchFillTime);
                            if (!decreasedLast) {
                                increment *= .95;
                            }
                            decreasedLast = true;
                        } else {
                            decreasedLast = false;
                        }
                    }

                    // Create batch
                    Debug.debug(Debug.MODULE_EXEC, "[Batching Thread] Batch size is %d", batch.size());
                    if (batch.size() > 0) {
                        // Throw out stuff from the batch until, we're at a multiple of workersPer
//                        System.out.println(-1);
                        int roundingLimit = PREFERED_BATCH_SIZE;
                        int numThread = parameters.numWorkersPerBatch * parameters.numPipelinedBatches;
                        if (parameters.roundBatchSize && batch.size() > roundingLimit) {
                            int numToPushBack = batch.size() % roundingLimit;
                            while (numToPushBack-- > 0) {
                                waiting.addFirst(batch.pollLast());
                            }
                        }

                        if (fillingLogger.hasStarted()) {
                            fillingLogger.logEnd();
                        }
                        //count++;
                        //if (count % 200 == 1) {
                            Debug.warning(Debug.MODULE_EXEC, "[Batching Thread] NEW Batch with size = %d and waiting = %d with wait Time %d, waiting batches: %d\n", batch.size(), waiting.size(), batchFillTime, executeBatchRequests.size());
                        //}
                        RequestCore[] reqs = new RequestCore[batch.size()];
                        batch.toArray(reqs);
//                        System.out.println(22);
                        createBatch(reqs);
//                        batchCount.incrementAndGet();
//                        System.out.println("Early arrivers size before emptying: " + earlyArrivers.size());
//                        emptyEarlyArrivers();
//                        System.out.println("Early arrivers size after emptying: " + earlyArrivers.size());
                        waitingLogger.logStart();
                    }
                    batch.clear();
                    batch.addAll(waiting);
                    if (!batch.isEmpty() && waitingLogger.hasStarted()) {
                        waitingLogger.logEnd();
                    }
                    waiting.clear();
                    //tmpPreferredBatchSize.set(PREFERED_BATCH_SIZE);
                }
            } catch (InterruptedException e) {
                Debug.error(Debug.MODULE_EXEC, "CreateBatchThread interrupted\n");
            }
        }

        private void runInUnreplicatedMode() {
            System.out.println("threadPoolSize: " + parameters.noOfThreads);
            ExecutorService executor = Executors.newFixedThreadPool(parameters.noOfThreads);
            while(requestHandler ==null);
            assert (requestHandler != null);

            while(true) {
                try {
                    RequestCore req = pendingClientRequests.take();
                    Entry tmp = req.getEntry();
                    RequestInfo info = new RequestInfo(false, (int) tmp.getClient(), (int) tmp.getSubId(), 0,
                    tmp.getRequestId(), 0, rand.nextLong());
                    GeneralGlueTuple tuple = new GeneralGlueTuple(tmp.getCommand(), info);
                    Runnable thread = new UnreplicatedGlueThread(requestHandler, tuple);
                    executor.execute(thread);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }


        private boolean needBatch() {
            if (isMiddle || !parameters.batchSuggestion) {
                return executeBatchRequests.size() < 1;
            }
            else {
                return needBatch.get();
            }
        }

        private int calculateLimit(int waitingSize) {
            if(!parameters.batchSuggestion || isMiddle || !needBatch.get() ) {
                return PREFERED_BATCH_SIZE;
            }
            else
                return tmpPreferredBatchSize.get() - waitingSize;
        }

//        private void emptyEarlyArrivers() {
//            int newBatchNo = batchCount.get();
//            RequestCore head = earlyArrivers.peek();
//            if(head == null) {
//                return;
//            }
//            int fakeSubID = (int) head.getEntry().getFakeSubId();
//            int groupNo = fakeSubID/1000;
//            while(groupNo <= newBatchNo) {
//                head = earlyArrivers.poll();
//                if(groupNo == newBatchNo) {
//                    int suggestedBatchSize = (fakeSubID/10) % 100;
//                    tmpPreferredBatchSize.set(suggestedBatchSize);
//                }
//                try {
//                    head.getEntry().setSubId(fakeSubID %10);
//                    pendingClientRequests.put(head);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//
//                head = earlyArrivers.peek();
//                if(head == null) {
//                    return;
//                }
//                fakeSubID = (int) head.getEntry().getFakeSubId();
//                groupNo = fakeSubID/1000;
//            }
//        }
    }

    private void addAllSafeToBatch(LinkedList<RequestCore> batch,
                                   LinkedList<RequestCore> waiting,
                                   LinkedList<RequestCore> pending,
                                   Map<Object, Boolean> readMap,
                                   Map<Object, Boolean> writeMap) {

        if (filter == null) {
            batch.addAll(pending);
            return;
        }

        Iterator<RequestCore> iter = pending.iterator();
        while (iter.hasNext()) {
            RequestCore rc = iter.next();
            List<RequestKey> keys;
            if(parameters.unreplicated) {
                keys = new ArrayList<>();
            }
            else {
                keys = filter.generateKeys(getTupleFromRequestCore(rc).request);
            }

            boolean conflict = false;
            for (RequestKey key : keys) {
                // A write is taking place with this key
                if (writeMap.containsKey(key)) {
                    conflict = true;
                    break;
                }
                // Trying to write when others are reading
                if (key.isWrite() && readMap.containsKey(key)) {
                    conflict = true;
                    break;
                }
            }
            if (!conflict) {
                // Non-conflicting, keep it in the batch and update the map
                for (RequestKey key : keys) {
                    if (key.isRead()) {
                        readMap.put(key, true);
                    } else {
                        writeMap.put(key, true);
                    }
                }
                // Add this request to the current batch
                batch.add(rc);
                iter.remove();
            } else {
                // Add this request to the waiting queue
                waiting.add(rc);
                iter.remove();
            }
        }
    }

    public void runMixerOnRequests(LinkedList<RequestCore> primaryRequests, LinkedList<RequestCore> secondaryRequests,
                                   Map<Object, Boolean> readMap, Map<Object, Boolean> writeMap) {
        // If there is no filter, we can't do anything, add all secondary to primary for now
        if (filter == null) {
            primaryRequests.addAll(secondaryRequests);
            secondaryRequests.clear();
            return;
        }

        readMap.clear();
        writeMap.clear();

        // Try to add the secondary requests to the primary requests

        Iterator<RequestCore> iter = primaryRequests.iterator();
        ArrayList<RequestCore> kickedOff = new ArrayList<RequestCore>();

        while (iter.hasNext()) {
            RequestCore rc = iter.next();
            List<RequestKey> keys;
            if(parameters.unreplicated) {
                keys = new ArrayList<>();
            }
            else {
                keys = filter.generateKeys(getTupleFromRequestCore(rc).request);
            }
            boolean conflict = false;
            for (RequestKey key : keys) {
                // A write is taking place with this key
                if (writeMap.containsKey(key)) {
                    conflict = true;
                    break;
                }
                // Trying to write when others are reading
                if (key.isWrite() && readMap.containsKey(key)) {
                    conflict = true;
                    break;
                }
            }
            if (!conflict) {
                // Non-conflicting, keep it in the batch and update the map
                for (RequestKey key : keys) {
                    if (key.isRead()) {
                        readMap.put(key, true);
                    } else {
                        writeMap.put(key, true);
                    }
                }
            } else {
                // Move the request to the secondary
                kickedOff.add(rc);
                iter.remove();
            }
        }

        // Push on all the kicked off ones to the secondary list
        for (int i = kickedOff.size() - 1; i >= 0; i--) {
            secondaryRequests.addFirst(kickedOff.get(i));
        }

        // Now add all of the requests from the secondary that we can.
        iter = secondaryRequests.iterator();
        while (iter.hasNext()) {
            RequestCore rc = iter.next();
            List<RequestKey> keys;
            if(parameters.unreplicated) {
                keys = new ArrayList<>();
            }
            else {
                keys = filter.generateKeys(getTupleFromRequestCore(rc).request);
            }

            boolean conflict = false;
            for (RequestKey key : keys) {
                // A write is taking place with this key
                if (writeMap.containsKey(key)) {
                    conflict = true;
                    break;
                }
                // Trying to write when others are reading
                if (key.isWrite() && readMap.containsKey(key)) {
                    conflict = true;
                    break;
                }
            }
            if (!conflict) {
                primaryRequests.addLast(rc);
                for (RequestKey key : keys) {
                    if (key.isRead()) {
                        readMap.put(key, true);
                    } else {
                        writeMap.put(key, true);
                    }
                }
                iter.remove();
            }
        }
    }

    /**
     * Returns a GeneralGlueTuple without info from a requestCore.
     *
     * @param requestCore
     * @return
     */
    private GeneralGlueTuple getTupleFromRequestCore(RequestCore requestCore) {
        return new GeneralGlueTuple(requestCore.getEntry().getCommand(), null);
    }

    private void createBatch(RequestCore[] reqs) {
//        System.out.println(23);
        Debug.fine(Debug.MODULE_EXEC, "CreateBatch: seqNo %d\n", lastSeqNoOrdered + 1);
        RequestBatch batch = new RequestBatch(parameters, reqs);
        NonDeterminism nondt = new NonDeterminism(System.currentTimeMillis(), rand.nextLong());
        long lastOne = getLastSeqNoOrdered();
        ExecuteBatchMessage msg = new ExecuteBatchMessage(parameters, getCurrentView(), lastOne + 1,
                batch, nondt, myIndex);
        if (parameters.primaryBackup && myIndex != fd.primary()) {
            return;
        } else if (!parameters.primaryBackup
                && msg.getView() % parameters.getExecutionCount() != myIndex) {
            return;
        }
        this.setLastSeqNoOrdered(lastOne + 1);
        this.authenticateExecMacArrayMessage(msg);
        // process(msg);
//        System.out.println(24);

        boolean needBackup = true;
        if (parameters.primaryBackup) {
            if(!parameters.unreplicated) {
                needBackup = (fd.dead() == -1);
            }
            else {
                needBackup = false;
            }
        }
        if (parameters.primaryBackup) {
            msg.setNeedBackup(needBackup);
        }
        try {
            // process(msg); if (true)return;
            if(parameters.batchSuggestion)
            {
                needBatch.set(false);
            }
//            System.out.println(25);
            this.executeBatchRequests.put(msg);
            Debug.debug(Debug.MODULE_EXEC, "pending requests = %d", this.executeBatchRequests.size());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (parameters.primaryBackup && !needBackup) {
//            System.out.println("no need to send to other execution replicas in the unreplicated mode and needBackup is false");
            return;
        }
        Debug.debug(Debug.MODULE_EXEC, "Sending next batch %d, timestamp = %d", msg.getSeqNo(), System.currentTimeMillis());
        this.sendToOtherExecutionReplicas(msg.getBytes(), myIndex);
//        System.out.println(26);
    }

    private void preProcess(ExecuteBatchMessage request) {
        if (!this.validateExecMacArrayMessage(request, myIndex)) {
            Debug.warning(Debug.MODULE_EXEC, "Validate Failed2 %s\n", request);
            return;
        }
        try {
            this.executeBatchRequests.put(request);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    transient private long noOfRequests = 0;
    transient private long timeOfRequests = 0;
    transient private long timeOfHash = 0;
    transient private long timeOfAll = 0;
    transient private long beforeTime = 0;
    transient private long afterTime = 0;
    transient private long last = 0;
    transient private long waitTime = 0;

    //This appears to be the main process method (most of the important stuff happens here) -Michael
    private void process(ExecuteBatchMessage request) {
//        System.err.println("a");
        long time = System.currentTimeMillis();
        long start = System.currentTimeMillis();
        Debug.debug(Debug.MODULE_EXEC, "parameters.digestlength = %d", parameters.digestLength);
        byte[] historyHash = new byte[parameters.digestLength];
        synchronized (executeLock) {
            Debug.debug(Debug.MODULE_EXEC, "Execute size = %d, req seqNo = %d, timestamp = %d",
                    request.getRequestBatch().getEntries().length,
                    request.getSeqNo(),
                    System.currentTimeMillis());
            if (request.getSeqNo() > 3) {
                noOfRequests += request.getRequestBatch().getEntries().length;
                waitTime += System.currentTimeMillis() - last;
            }
            Debug.fine(Debug.MODULE_EXEC, "Process batch %d lastExecuted = %d\n", request.getSeqNo(),
                    this.lastSeqNoExecuted);
            //Making sure request has the right view
            if (!parameters.primaryBackup && request.getView() != this.currentView) {
                if (request.getView() < this.currentView) {
                    Debug.info(Debug.MODULE_EXEC, "Discard request in old view %d\n", request.getSeqNo());
                } else {
                    Debug.info(Debug.MODULE_EXEC, "Cache request %d\n", request.getSeqNo());
                    requestCache.put(request.getSeqNo(), request);
                }
                return;
            }
            //if the request is not the next sequential request, either cache it or get a new state
            if (request.getSeqNo() != this.lastSeqNoExecuted + 1) {
                if (request.getSeqNo() > this.lastSeqNoExecuted) {
                    Debug.info(Debug.MODULE_EXEC, "Cache request %d\n", request.getSeqNo());
                    boolean needFetch = false;
                    if (requestCache.size() == 0 || requestCache.lastKey() < request.getSeqNo() - 1)
                        needFetch = true;
                    requestCache.put(request.getSeqNo(), request);
                    if (parameters.primaryBackup && needFetch) {
                        FetchStateMessage message = new FetchStateMessage(parameters, this.lastVersionNoVerified,
                                request.getSeqNo(), myIndex);
                        this.sendToOtherExecutionReplicas(message.getBytes(), myIndex);
                        Debug.info(Debug.MODULE_EXEC, "Send %s %d\n", message.toString(),
                                System.currentTimeMillis());
                    }
                } else {
                    Debug.info(Debug.MODULE_EXEC, "Discard old request %d\n", request.getSeqNo());
                }
                return;
            } else {
                //TODO should we return?-Remzi
                requestCache.put(request.getSeqNo(), request);
            }

            if (!parameters.primaryBackup && request.getSender() != primary) {
                Debug.info(Debug.MODULE_EXEC, "Batch %d is from an unexpected primary %d\n",
                        request.getSeqNo(), request.getSender());
                return;
            }

            if (parameters.primaryBackup) {
                long stableVersion = request.getSeqNo() - parameters.executionPipelineDepth;
                if (stableVersion > 0) {
//                    System.out.println("stable version: " + stableVersion);
                    MerkleTreeInstance.getShimInstance().makeStable(
                            glue.getMerkleTreeVersionLinkedWithRequestNo(stableVersion));
                }

            }

            // FIXME: the verification infrastructure here are to be removed
            // once we finish implementing verification after each slice.
            // also, after this is removed, we only need to call MerkleTree.update()
            // once per slice in BatchManager.java.
            if(!isMiddle)
            {
                historyHash = MerkleTreeInstance.getShimInstance().getHash();
            }
            replyCache.setHistoryHash(historyHash);
            Debug.fine(Debug.MODULE_EXEC, "Current sequence number: %d", request.getSeqNo());

            assert (parameters.parallelExecution);//I didn't understand if below, so I put assert to check its necessity
//            if (parameters.parallelExecution) {
//            System.out.println("put version for seqNo: " + request.getSeqNo());
            glue.linkRequestNoToMerkleTreeVersion(request.getSeqNo());
//            }

            if (request.getSeqNo() > 3)
                beforeTime += (System.currentTimeMillis() - time);
        }
        time = System.currentTimeMillis();
        long seqNo = request.getSeqNo();
        this.setLastSeqNoExecuted(seqNo);

//        System.out.println("executing batch");
        executeBatch(request);
//        System.out.println("executed batch");

        Debug.debug(Debug.MODULE_EXEC, "Setting lastSeqNoExecuted=%d to request.getVersionNo()", this.lastSeqNoExecuted);
        synchronized (executeLock) {
//            System.out.println(11);
            Debug.info(Debug.MODULE_EXEC, "Exec time = %d", (System.currentTimeMillis() - time));
            if (request.getSeqNo() > 3) {
                timeOfRequests += (System.currentTimeMillis() - time);
            }

            if (this.execSequential == true) {
                this.execSequential = false;
            }
            if (parameters.forceSequential == true && this.getLastSeqNoExecuted() > 500) {
                this.execSequential = true;
            }

//            System.out.println(12);
            if (parameters.pipelinedBatchExecution && parameters.sendVerify) {
                // versionNo = glue.getCurrentMerkleTreeVersion();
                // TODO why is this commented out-Remzi
                return;
            } else if (parameters.pipelinedBatchExecution) {//TODO why is that?-Remzi
                sendAllReplies(seqNo);
                return;
            }

            time = System.currentTimeMillis();
            byte[] hash = null;
            VerifyMessage vm = null;
//            System.out.println(13);
            if (!isMiddle) {
//                ((MerkleTree) MerkleTreeInstance.getShimInstance()).printTree();
                hash = MerkleTreeInstance.getShimInstance().getHash();
//                System.err.println("hash for seqNo " + seqNo + ": " + Arrays.toString(hash) );
//                ((MerkleTree) MerkleTreeInstance.getShimInstance()).printTree();

                synchronized (this.statesToVerify) {
                    this.statesToVerify.put(seqNo, Digest.fromBytes(parameters, hash));
                }

                HistoryAndState hasToSend = new HistoryAndState(parameters, Digest.fromBytes(parameters, historyHash),
                        Digest.fromBytes(parameters, hash));
                vm = new VerifyMessage(parameters, currentView, seqNo,
                        hasToSend, myIndex);
                //System.out.println("KeyToSend: " + hasToSend.toString());
                this.authenticateVerifierMacArrayMessage(vm);
            }

            Debug.debug(Debug.MODULE_EXEC, "After executing seqNo %d", seqNo);
//            System.out.println(14);
            if(!isMiddle) {
                if (parameters.primaryBackup) {
                    lastSeqNoToBeVerified = seqNo;

                    if (myIndex != fd.primary()) {
                        this.lastSeqNoOrdered = seqNo;
                        replyCache.getLastRequestId(this.lastRequestIdOrdered);
                        VerifyResponseMessage vrm = new VerifyResponseMessage(parameters, currentView,
                                seqNo, new HistoryAndState(parameters, Digest.fromBytes(parameters,
                                historyHash), Digest.fromBytes(parameters, hash)), false, myIndex);
//                        System.out.println("send verify response message for version seqno: " + seqNo);
//                        System.out.println("send to other execution replicas");
                        this.sendToOtherExecutionReplicas(vrm.getBytes(), myIndex);

                        Iterator<Long> iter = this.requestCache.keySet().iterator();
                        while (iter.hasNext()) {
                            long tmp = iter.next();
                            if (tmp <= seqNo - parameters.executionPipelineDepth) {
                                iter.remove();
                            } else {
                                break;
                            }
                        }

                        Debug.fine(Debug.MODULE_EXEC, "send vrm=%s\n", vrm);
                    }

                    if (myIndex == fd.primary() && !request.needBackup()) {
                        Iterator<Long> iter = this.requestCache.keySet().iterator();
                        while (iter.hasNext()) {
                            long tmp = iter.next();
                            if (tmp <= seqNo) {
                                iter.remove();
                            } else {
                                break;
                            }
                        }
//                        System.out.println("send replies");
                        sendAllReplies(seqNo);
                    }
                } else {
                    if (parameters.sendVerify) {
//                        System.out.println("send verify message for version no: " + vm.getVersionNo());
                        this.sendToAllVerifierReplicas(vm.getBytes());
                        lastSeqNoToBeVerified = vm.getVersionNo();
                    } else {
                        fakedVerification(vm.getView(), vm.getVersionNo(), vm.getHistoryAndState());
                    }
                    Debug.fine(Debug.MODULE_EXEC, "send vm=%s\n", vm);
                }
            }
//            System.out.println(15);
            if (seqNo > 3) {
                timeOfAll += (System.currentTimeMillis() - start);
            }

            if (seqNo > 3) {
                afterTime += (System.currentTimeMillis() - time);
            }

            if (noOfRequests > 0) {
                Debug.info(Debug.MODULE_EXEC, ("exec = " + timeOfRequests * 1000 / noOfRequests + " hash=" + timeOfHash
                        * 1000 / noOfRequests + " before=" + beforeTime * 1000 / noOfRequests + " after="
                        + afterTime * 1000 / noOfRequests + " total=" + timeOfAll * 1000 / noOfRequests
                        + " wait=" + waitTime * 1000 / noOfRequests + " current=" + System.currentTimeMillis()));
            }

            if (false) {
                timeOfRequests = 0;
                noOfRequests = 0;
                timeOfHash = 0;
                beforeTime = 0;
                afterTime = 0;
                timeOfAll = 0;
                waitTime = 0;
            }
            last = System.currentTimeMillis();
        }
//        System.out.println(16);
    }

    private long lastRequestedVerify = -1;

    private synchronized void sendAllReplies(long seqNo) {
        long start = System.currentTimeMillis();
        ArrayList<ReplyCache.ReplyInfo> s = replyCache.getReplies(seqNo);
        Debug.debug(Debug.MODULE_EXEC, "Sending replies to all clients, found %d replies\n", s.size());
        if (s == null) {
            Debug.info(Debug.MODULE_EXEC, "Nothing to send for seqNo %d\n", seqNo);
        } else {
            for (ReplyCache.ReplyInfo replyInfo : s) {
                // TODO This should happen when we receive a VRM, 
                // because there are other reasons to call send all Replies/someone could call it to mess with the verification.
                replyCache.setReplyVerified(replyInfo.getClientId());
                Reply rep = new Reply(myIndex, replyInfo.getRequestId(), replyInfo.getReply());
                if (!parameters.primaryBackup) {
                    for (int i = 0; i < members.getClientReplicationCount(replyInfo.getClientId()); i++) {
                        this.authenticateClientMacMessage(rep, replyInfo.getClientId(), i);
                    }
                }
                if (replyInfo.getReply().length == 0) {
                    Debug.kill("reply zero");
                }

                if(parameters.normalMode) {
//                    System.out.println("In normal mode send only to the sending client");
                    this.sendToClient(rep.getBytes(), replyInfo.getClientId(), 0);
                }
                else {
//                    System.out.println("Send to all replicated clients");
                    for (int i = 0; i < members.getClientReplicationCount(replyInfo.getClientId()); i++) {
                        Debug.debug(Debug.MODULE_EXEC, "Sending to client (%d, %d)", replyInfo.getClientId(), i);
                        this.sendToClient(rep.getBytes(), replyInfo.getClientId(), i);
                    }
                }
            }
        }
        this.setLastSeqNoVerified(seqNo);

        Debug.debug(Debug.MODULE_EXEC, "Setting lastSeqNoVerified=%d to seqNo", seqNo);
        //Added because if we verify something, we must have executed it. That said,
        //I don't know if this should be done here -Michael
        //this is actually never executed and I have no idea why
        //Debug.debug(Debug.MODULE_EXEC, "Setting lastSeqNoExecuted=%d to seqNo", seqNo);
        //this.setLastSeqNoExecuted(seqNo);
    }

    private void executeBatch(ExecuteBatchMessage request) {
        Debug.fine(Debug.MODULE_EXEC, "Executing batch with seqNo %d of size %d\n",
                request.getSeqNo(), request.getRequestBatch().getEntries().length);
        this.glue.executeBatch(request);
        //this fails to update lastSeqNoExecuted I think -Michael
    }

    private void preProcess(PBRollbackMessage request) {
        Debug.fine(Debug.MODULE_EXEC, "Received PBRollback: %s\n", request);
        if (!parameters.primaryBackup) {
            Debug.warning(Debug.MODULE_EXEC, "This message should only happen in Primary Backup %s\n");
            return;
        }

        try {
            this.pendingRequests.put(request);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /*
    ** Process Rollback Request
    ** A PBRollbackMessage contains
    */
    private void process(PBRollbackMessage request) {
        Debug.fine(Debug.MODULE_EXEC, "Process PBRollback: %s\n", request);
        if (request.getSeqNo() > this.lastSeqNoExecuted) {
            Debug.warning(Debug.MODULE_EXEC, "Discard future rollback lastExecuted = %d this = %d",
                    lastSeqNoExecuted,
                    request.getSeqNo());
            return;
        }
        try {
            if (parameters.pbRollback) {
                currentView++;
                reExecute(request.getSeqNo());
            } else {
                int[] indexes = MerkleTreeInstance.get().pbRollBack(
                        glue.getMerkleTreeVersionLinkedWithRequestNo(request.getSeqNo()));
                this.setLastSeqNoExecuted(request.getSeqNo());
                Debug.debug(Debug.MODULE_EXEC, "Setting lastSeqNoExecuted=%d to request.getVersionNo()", this.lastSeqNoExecuted);
                Debug.debug(Debug.MODULE_EXEC, "PB rollback to %d", request.getSeqNo());
                PBFetchStateMessage msg = new PBFetchStateMessage(parameters, request.getSeqNo(), indexes,
                        myIndex);
                this.sendToOtherExecutionReplicas(msg.getBytes(), myIndex);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void preProcess(PBFetchStateMessage request) {
        Debug.fine(Debug.MODULE_EXEC, "Received PBFetchState: %s\n", request);
        if (!parameters.primaryBackup) {
            Debug.warning(Debug.MODULE_EXEC, "This message should only happen in Primary Backup %s\n");
            return;
        }

        try {
            this.pendingRequests.put(request);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void process(PBFetchStateMessage request) {
        Debug.fine(Debug.MODULE_EXEC, "Process PBFetchState: %s\n", request);
        try {
            synchronized (statesToVerify) {
                this.statesToVerify.put(lastVersionNoExecuted, Digest.fromBytes(parameters, emptyHash));
            }

            byte[] state = MerkleTreeInstance.get().pbFetchStates(request.getCurrentSeqNo(),
                    lastVersionNoExecuted, request.getIndexes());
            if (state == null) {
                return;
            }

            AppStateMessage msg = new AppStateMessage(request.getCurrentSeqNo(), lastVersionNoExecuted,
                    state, myIndex);
            this.sendToOtherExecutionReplicas(msg.getBytes(), myIndex);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void preProcess(VerifyResponseMessage request) {
        Debug.fine(Debug.MODULE_EXEC, "Received VRM: %s\n", request);
        if (!parameters.primaryBackup && !this.validateVerifierMacArrayMessage(request, myIndex)) {
            Debug.warning(Debug.MODULE_EXEC, "Validation Failed %s\n", request);
            return;
        }
//        System.out.println("preprocess VRM1");
        if (!parameters.primaryBackup) {
//            System.out.println("preprocess VRM2");
            if (!verifierQuorum.addEntry(request.getVersionNo(), request)) {
                return;
            } else {
                verifierQuorum.removeQuorum(request.getVersionNo());
            }
        }
//        System.out.println("preprocess VRM3");
        try {
            this.pendingRequests.put(request);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void fakedVerification(long view, long versionNo, HistoryAndState historyAndState) {
        VerifyResponseMessage request = new VerifyResponseMessage(parameters, view, versionNo, historyAndState, false, 0);
        process(request);
    }

    private void process(VerifyResponseMessage request) {
//        System.out.println("1");
        Debug.fine(Debug.MODULE_EXEC, "Process VRM: %s %d\n", request, System.currentTimeMillis());

        long versionNo = request.getVersionNo();
        long view = request.getViewNo();
        Debug.debug(Debug.MODULE_EXEC, "VRM Contains: SeqNo=%d ViewNo=%d", versionNo, view);
        if (view == 1) {
            //Checking that view change VRM reaches exec nodes
            Debug.debug(Debug.MODULE_EXEC, "Y U NO WORK");
            Debug.debug(Debug.MODULE_EXEC, "VRM Contains: SeqNo=%d ViewNo=%d", versionNo, view);
        }
//        if (parameters.pipelinedBatchExecution && parameters.sendVerify) {
//            Debug.debug(Debug.MODULE_EXEC, "lastRequestedVerify: %d, seqNo: %d", lastRequestedVerify, versionNo);
//            if (versionNo != lastRequestedVerify) {
//                // FIXME: Think of a better way to do this!
//                // Could we use history and state?
//                vrmMap.put(versionNo, request);
//                Debug.debug(Debug.MODULE_EXEC, "VRM Queue (pendingRequests): %s", pendingRequests);
//                return;
//            }
//        }
        if (versionNo == 0) {
            try {
                MerkleTreeInstance.getShimInstance().rollBack(0);
                this.setLastSeqNoOrdered(0);
                this.setLastVersionNoVerified(0);
                this.setLastVersionNoExecuted(0);
            } catch (MerkleTreeException e) {
                e.printStackTrace();
            }
        }
//        System.out.println("2");
        if (view < this.currentView || versionNo < this.lastVersionNoVerified) {
            Debug.fine(Debug.MODULE_EXEC,
                    "Receive an old verify response: lastVerified=%d, currentView=%d, %s\n",
                    this.lastVersionNoVerified, this.currentView, request);
            return;
        }

//        System.out.println("3");
//        System.out.println("versionNo: " + versionNo + ", view: " + view);
        if (versionNo == this.lastVersionNoVerified && view == this.currentView) {
            Debug.fine(Debug.MODULE_EXEC, "Receive an old verify response: %s\n", request);
            return;
        }

//        System.out.println("4");
        Digest digest = null;
        //TODO if seqNo == this.lastSeqNoVerified it will return already, this logic seems to use statesToVerify directly
//        synchronized (statesToVerify) {
//            digest = (seqNo == this.lastSeqNoVerified) ? this.hashVerified : this.statesToVerify
//                    .remove(seqNo);
//        }

//        System.out.println("5");
        synchronized (statesToVerify) {
            digest = this.statesToVerify.remove(versionNo);
        }

        if (parameters.causeDivergence) {
            //This snippet of code makes it such that certain execution nodes need to fetch state
            Debug.debug(Debug.MODULE_EXEC, "SeqNo to force Rollback: %d", versionNo);
            if (versionNo - (myIndex + 1) * 1000 >= 0 && (versionNo - (myIndex + 1) * 1000) % 3000 == 0) {
                Debug.debug(Debug.MODULE_EXEC, "Inject error at %d", versionNo);
                digest = Digest.fromBytes(parameters, new byte[parameters.digestLength]);
            }
        }

        boolean fetch = false;
//        System.out.println("check verification response");
        if ((digest != null && digest.equals(request.getHistoryAndState().getState()))
                || (parameters.primaryBackup && Arrays.equals(request.getHistoryAndState().getState()
                .getBytes(), emptyHash))) {
            // Send replies
            Debug.fine(Debug.MODULE_EXEC, "SeqNo %d committed\n", versionNo);
            if (!isMiddle) {
                sendAllReplies(versionNo);
            } else if (versionNoToSeqNO.containsKey(versionNo) && (!parameters.primaryBackup || fd.primary() == myIndex)) {
                long seqNo = versionNoToSeqNO.get(versionNo);
                sendAllReplies(seqNo);
            }
            setLastVersionNoVerified(versionNo);
            synchronized (verificationLock) {
//                System.out.println("notify verifcation lock");
                verifyResponseReceived = true;
                sliceVerified = true;
                verificationLock.notifyAll();
            }
            this.hashVerified = request.getHistoryAndState().getState();
            this.execSequential = request.getNextSequential();
            Iterator<Long> iter = this.requestCache.keySet().iterator();
            while (iter.hasNext()) {
                long tmp = iter.next();
                if (tmp <= versionNo) {
                    iter.remove();
                } else {
                    break;
                }
            }

            if (versionNo > parameters.executionPipelineDepth) {
                if (parameters.parallelExecution && !isMiddle) {
                    long newStableNumber = glue.getMerkleTreeVersionLinkedWithRequestNo(versionNo
                            - parameters.executionPipelineDepth);
                    MerkleTreeInstance.getShimInstance().makeStable(newStableNumber);
                    Debug.debug(Debug.MODULE_EXEC, "About to notify that the verify succeeded %d", versionNo - parameters.executionPipelineDepth);
                } else {
                    MerkleTreeInstance.getShimInstance().makeStable(versionNo - parameters.executionPipelineDepth);
                }
            }


            if (versionNo % parameters.execSnapshotInterval == 0) {
                if (parameters.doLogging) {
                    logCleaner.takeSnapshotAndclean(versionNo);
                }
                // also gc statesToVerify
                synchronized (statesToVerify) {
                    Iterator<Long> viter = statesToVerify.keySet().iterator();
                    while (viter.hasNext()) {
                        Long tmp = viter.next();
                        if (tmp <= versionNo) {
                            viter.remove();
                        } else {
                            break;
                        }
                    }
                }
            }

//            if (parameters.sendVerify && parameters.pipelinedBatchExecution) {
//                glue.notifyVerifySucceeded(versionNo);
//            }
        } else if (!isMiddle) {
            Debug.debug(Debug.MODULE_EXEC, "VRM failed to match history and state in fetch");
            // We are incorrect in some way, but the verifiers reached consensus
            // We need to fetch state from another node
            if (digest == null) {
                if (versionNo != 0) {
                    if (requestCache.size() > 0 && requestCache.lastKey() != lastVersionNoExecuted) {
                        fetchState(request.getVersionNo(), request.getHistoryAndState().getState());
                    }
                    Debug.fine(Debug.MODULE_EXEC, "The VRM hash does not exists VRM=%d, current=%d\n", versionNo,
                            lastVersionNoExecuted);
                }
            } else {
                Debug.warning(Debug.MODULE_EXEC,
                        "The VRM hash for seqNo %d is different current=%s verified=%s\n", versionNo,
                        UnsignedTypes.bytesToHexString(digest.getBytes()),
                        UnsignedTypes.bytesToHexString(request.getHistoryAndState().getState().getBytes()));
                Debug.debug(Debug.MODULE_EXEC, "parameters.pbRollback is %s", parameters.pbRollback);
                if (!parameters.primaryBackup) {
                    fetchState(request.getVersionNo(), request.getHistoryAndState().getState());
                    fetch = true;
                    // This node should rollback when it receives a AppStateMessage based on its fetch request
                } else {
                    // For primary backup, if primary receives a diverged hash, it should
                    // send its state to backup
                    try {
                        currentView++;
                        PBRollbackMessage msg = new PBRollbackMessage(parameters, versionNo - 1, myIndex);
                        this.sendToOtherExecutionReplicas(msg.getBytes(), myIndex);
                        if (parameters.pbRollback)
                            Debug.debug(Debug.MODULE_EXEC, "parameters.pbRollback so reExecuting");
                        this.reExecute(versionNo - 1);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        // The verification nodes decided that a view change and possibly a rollback are warranted.
        if (view > currentView && !parameters.primaryBackup) {
            System.out.println("Verifiers decided to view change. New view: " + view);
            if (isMiddle) {
                setCurrentView(view);
                if (myIndex == primary) {
                    setLastSeqNoOrdered(lastSeqNoVerified);//set the last seq number properly since we will create a new batch as primary
                }

                //TODO think about replycache, execSequential
                ExecViewChangeMessage vcm = new ExecViewChangeMessage(parameters, view, myIndex);
                this.authenticateFilterMacArrayMessage(vcm);
                this.sendToAllFilterReplicas(vcm.getBytes());
                this.setLastVersionNoExecuted(this.lastVersionNoVerified);
                this.setLastSeqNoExecuted(lastSeqNoVerified);
//                replyCache.reset();//TODO does this harm if it is not first slice of the batch?
//                replyCache.getLastRequestId(this.lastRequestIdOrdered);//TODO does this harm if it is not first slice of the batch?

                synchronized (verificationLock) {
                    verifyResponseReceived = true;
                    sliceVerified = false;
                    verificationLock.notifyAll();
                }
            } else {
                Debug.info(Debug.MODULE_EXEC, "View change to %d\n", view);
                setCurrentView(view);
                requestCache.clear();
                if (myIndex == primary) {
                    setLastSeqNoOrdered(request.getVersionNo());//If it is backend replica we can use seqNo from the request directly
                }
                ExecViewChangeMessage vcm = new ExecViewChangeMessage(parameters, view, myIndex);
                this.authenticateFilterMacArrayMessage(vcm);
                this.sendToAllFilterReplicas(vcm.getBytes());
                this.execSequential = request.getNextSequential();
                Debug.debug(Debug.MODULE_EXEC, "execSequential=%s after ExecVRM View Change", this.execSequential);
                Debug.debug(Debug.MODULE_EXEC, "fetch=%s, lastSeqNoVerified=%d, lastSeqNoExecuted=%d",
                        fetch, this.lastVersionNoVerified, this.lastVersionNoExecuted);
                if (!fetch && this.lastVersionNoVerified != this.lastVersionNoExecuted) {
                    Debug.debug(Debug.MODULE_EXEC, "Passed the !fetch && verified != executed.");
                    try {
                        if (this.lastVersionNoVerified < this.lastVersionNoExecuted) {
                            Debug.info(Debug.MODULE_EXEC, "Sequential rollback\n");
                            Debug.debug(Debug.MODULE_EXEC, "Rolling back to %d", this.lastVersionNoVerified);
                            MerkleTreeInstance.getShimInstance().rollBack(lastVersionNoVerified);
                            //TODO?? glue.getMerkleTreeVersionLinkedWithRequestNo(this.lastSeqNoVerified));
                            replyCache.reset();
                            replyCache.getLastRequestId(this.lastRequestIdOrdered);
                            this.setLastVersionNoExecuted(this.lastVersionNoVerified);
                        } else {
                            Debug.info(Debug.MODULE_EXEC, "View change fetch state");
                            fetchState(request.getVersionNo(), request.getHistoryAndState().getState());
                        }
                    } catch (MerkleTreeException e) {
                        e.printStackTrace();
                    }
                } else if (this.lastVersionNoVerified < request.getVersionNo()) {
                    Debug.info(Debug.MODULE_EXEC, "View change fetch state");
                    fetchState(request.getVersionNo(), request.getHistoryAndState().getState());
                }
            }
        }
        Debug.debug(Debug.MODULE_EXEC, "End processing VRM at time %d", System.currentTimeMillis());
    }

    private void reExecute(long startSeqNo) {
        if (this.lastSeqNoExecuted <= startSeqNo) {
            return;
        }
        Debug.info(Debug.MODULE_EXEC, "Rollback to %d and reExecute\n", startSeqNo);
        try {
            MerkleTreeInstance.get().rollBack(glue.getMerkleTreeVersionLinkedWithRequestNo(startSeqNo));
            this.setLastSeqNoExecuted(startSeqNo);
            Debug.debug(Debug.MODULE_EXEC, "Setting lastSeqNoExecuted=%d to startSeqNo", startSeqNo);
            TreeMap<Long, ExecuteBatchMessage> tmp = (TreeMap<Long, ExecuteBatchMessage>) requestCache
                    .clone();
            execSequential = true;
            for (Long seqNo : tmp.keySet()) {
                ExecuteBatchMessage msg = tmp.get(seqNo);
                if (seqNo == this.lastSeqNoExecuted + 1) {
                    Debug.info(Debug.MODULE_EXEC, "ExecuteCache %d\n", seqNo);
                    this.process(msg);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void preProcess(FetchStateMessage request) {
        if (!this.validateExecMacArrayMessage(request, myIndex)) {
            Debug.warning(Debug.MODULE_EXEC, "Validate Failed3 %s\n", request);
            return;
        }

        try {
            this.pendingRequests.put(request);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void process(FetchStateMessage request) {
        try {
            byte[] state = MerkleTreeInstance.getShimInstance().fetchStates(request.getCurrentSeqNo(),
                    request.getTargetSeqNo());
            // TODO do something better
            if (state == null) {
                return;
            }
            AppStateMessage msg = new AppStateMessage(request.getCurrentSeqNo(),
                    request.getTargetSeqNo(), state, myIndex);
            this.authenticateExecMacMessage(msg, myIndex);
            this.sendToExecutionReplica(msg.getBytes(), (int) request.getSendingReplica());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void preProcess(AppStateMessage request) {
        if (!parameters.primaryBackup && !this.validateExecMacMessage(request)) {
            Debug.warning(Debug.MODULE_EXEC, "Validate Failed4 %s\n", request);
            return;
        }
        try {
            this.pendingRequests.put(request);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void process(AppStateMessage request) {
        if (!parameters.primaryBackup) {
            if (!this.statesToFetch.containsKey(request.getEndSeqNo())) {
                Debug.debug(Debug.MODULE_EXEC, "statesToFetch contains EndSeqNo");
                return;
            }
            if (request.getEndSeqNo() <= this.lastVersionNoExecuted) {
                Debug.info(Debug.MODULE_EXEC, "Already executed. No need to do merge state %d\n",
                        request.getEndSeqNo());
                this.statesToFetch.remove(request.getEndSeqNo());
                return;
            }
            try {
                MerkleTreeInstance.getShimInstance().mergeStates(request.getEndSeqNo(), request.getState());
                byte[] hash = MerkleTreeInstance.getShimInstance().getHash();

                Debug.debug(Debug.MODULE_EXEC, "Hash after merge %s\n",
                        UnsignedTypes.bytesToHexString(hash));
                Digest expectedDigest = this.statesToFetch.get(request.getEndSeqNo());
                if (!Arrays.equals(expectedDigest.getBytes(), hash)) {
                    Debug.warning(Debug.MODULE_EXEC, "Hash mismatch. A faulty state\n");
                    return;
                }
                replyCache.reset();
                replyCache.getLastRequestId(this.lastRequestIdOrdered);
                this.setLastVersionNoVerified(request.getEndSeqNo());
                Debug.debug(Debug.MODULE_EXEC, "Setting lastSeqNoVerified=%d to request.getEndSeqNo", this.lastVersionNoVerified);
                this.setLastVersionNoExecuted(request.getEndSeqNo());
                Debug.debug(Debug.MODULE_EXEC, "Setting lastSeqNoExecuted=%d to request.getEndSeqNo", this.lastSeqNoExecuted);
                this.statesToFetch.remove(request.getEndSeqNo());
                MerkleTreeInstance.getShimInstance().makeStable(
                        glue.getMerkleTreeVersionLinkedWithRequestNo(request.getEndSeqNo()));
                Debug.info(Debug.MODULE_EXEC, "Load state successfully %d %d\n", request.getEndSeqNo(),
                        System.currentTimeMillis());
                TreeMap<Long, ExecuteBatchMessage> tmp = (TreeMap<Long, ExecuteBatchMessage>) requestCache
                        .clone();
                for (Long seqNo : tmp.keySet()) {
                    ExecuteBatchMessage msg = tmp.get(seqNo);
                    Debug.info(Debug.MODULE_EXEC, "TryExecuteCache %d currentSeqNo=%d\n", seqNo,
                            this.lastVersionNoExecuted);
                    if (seqNo == this.lastVersionNoExecuted + 1 && msg.getView() == this.currentView) {
                        Debug.info(Debug.MODULE_EXEC, "ExecuteCache %d\n", seqNo);
                        this.process(msg);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            // This is for primaryBackup
            try {
                if (this.lastSeqNoExecuted != request.getStartSeqNo()) {
                    Debug.error(Debug.MODULE_EXEC, "Invalid AppState current = %d, start = %d",
                            lastSeqNoExecuted,
                            request.getStartSeqNo());
                    return;
                }
                MerkleTreeInstance.getShimInstance().mergeStates(request.getEndSeqNo(), request.getState());
                this.setLastSeqNoVerified(request.getEndSeqNo());
                Debug.debug(Debug.MODULE_EXEC, "Setting lastSeqNoVerified=%d to request.getEndSeqNo", this.lastSeqNoVerified);
                this.setLastSeqNoExecuted(request.getEndSeqNo());
                Debug.debug(Debug.MODULE_EXEC, "Setting lastSeqNoExecuted=%d to request.getEndSeqNo", this.lastSeqNoExecuted);
                byte[] historyHash = replyCache.getHistoryHash();

                // TODO possible safety risk
                VerifyResponseMessage vrm = new VerifyResponseMessage(parameters, currentView,
                        request.getEndSeqNo(), new HistoryAndState(parameters, Digest.fromBytes(parameters,
                        historyHash), Digest.fromBytes(parameters, emptyHash)), false, myIndex);
                this.sendToOtherExecutionReplicas(vrm.getBytes(), myIndex);

                Debug.info(Debug.MODULE_EXEC, "Load state successfully %d %d\n", request.getEndSeqNo(),
                        System.currentTimeMillis());
                TreeMap<Long, ExecuteBatchMessage> tmp = (TreeMap<Long, ExecuteBatchMessage>) requestCache
                        .clone();
                for (Long seqNo : tmp.keySet()) {
                    ExecuteBatchMessage msg = tmp.get(seqNo);
                    Debug.info(Debug.MODULE_EXEC, "TryExecuteCache %d currentSeqNo=%d\n", seqNo,
                            this.lastSeqNoExecuted);
                    if (seqNo == this.lastSeqNoExecuted + 1 && msg.getView() == this.currentView) {
                        Debug.info(Debug.MODULE_EXEC, "ExecuteCache %d\n", seqNo);
                        this.process(msg);
                    }
                }
                // requestCache.clear();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // FIXME: for debug purpose, can be removed once the code base is stable.
    transient private HashMap<ClientShimBaseNode, byte[]> replyCacheFromSecondService =
            new HashMap<ClientShimBaseNode, byte[]>();


    public byte[] execNestedRequest(byte[] request, ClientShimBaseNode[] csbns) {
        //assert (isMiddle); // Doesn't make sense to call this method if not middle
        // service;

//       System.err.println(1);

        Thread t = Thread.currentThread();
        int id = 0;
        if (t instanceof IndexedThread) {
            id = ((IndexedThread) t).getIndex();
        }

        if(parameters.CFTmode) {
            MerkleTreeInstance.getShimInstance().addOutput(request, id);
        }

        Debug.debug(Debug.MODULE_EXEC, "Thread %d: start of execNestedRequest\n", id);
        if (glue.isSequential()) {
            if (!parameters.pipelinedSequentialExecution) {
                Debug.debug(Debug.MODULE_EXEC, "Thread %d: sequential execution\n", id);
                byte[] replyFromSecondService = csbns[id].execute(request);
                return replyFromSecondService;
            } else {
                Debug.debug(Debug.MODULE_EXEC, "Thread %d: pipelined execution, will yield\n", id);
                glue.yieldPipeline();
                Debug.debug(Debug.MODULE_EXEC, "Thread %d: yielded, now csbn.execute()\n", id);
                byte[] replyFromSecondService = csbns[id].execute(request);
                Debug.debug(Debug.MODULE_EXEC, "Thread %d: reply is here, waiting for my turn\n", id);
                glue.waitForMyTurn();
                Debug.debug(Debug.MODULE_EXEC, "Thread %d: my turn! returning reply\n", id);
                return replyFromSecondService;
                //throw new RuntimeException("Pipelined execution not implemented yet");
            }
        } else {
            Debug.info(Debug.MODULE_EXEC, "Thread %d: reached the wall", id);
            //JIM-8/19
            //glue.acquireRunningSemaphore();
            Debug.info(Debug.MODULE_EXEC, "TIMINGS[EBN] Thread %d StartHitWallNested %d",
                    id, System.nanoTime());
            csbns[id].copyReplyFromSecondService = null;

//            System.out.println("batchSuggestion: " + parameters.batchSuggestion + ", isMiddle: " + isMiddle);
            if(parameters.batchSuggestion && isMiddle) {
                numberOfNestedRequestInTheSlice.incrementAndGet();
                newNestedGroupNo.compareAndSet(oldNestedGroupNo.get(), oldNestedGroupNo.get()+1);
                //System.out.println(id + ": new suggested batch size:1 " + numberOfNestedRequestInTheSlice + ", newGroupNo: " + newNestedGroupNo);
            }

            if(!parameters.normalMode) {
                glue.hitWall();

                //assert (isMiddle); //TODO remove isMiddle condition, it shouldn't come here if it is not middle
                if(parameters.batchSuggestion) {
                    //oldNestedGroupNo.compareAndSet(newNestedGroupNo.get()-1, newNestedGroupNo.get());
                    csbns[id].getParameters().newNestedGroupNo = oldNestedGroupNo.get();
                    csbns[id].getParameters().numberOfNestedRequestInTheSlice = finalNestedCount;
                    //System.out.println(id + ": new suggested batch size:2 " + finalNestedCount + ", newGroupNo: " + newNestedGroupNo);
                }
            }
            else {
//                System.out.println("In normal mode we shouldn't hit the wall");
            }

            //            System.err.println(4);
            Debug.info(Debug.MODULE_EXEC, "TIMINGS[EBN] Thread %d EndHitWallNested %d",
                    id, System.nanoTime());
            Debug.debug(Debug.MODULE_EXEC, "Finished hitting the wall");
            //glue.releaseRunningSemaphore();
            Debug.info(Debug.MODULE_EXEC, "Thread %d: after verification", id);
            if(!parameters.normalMode) {
                if (glue.isSequential()) {
                    // TODO OPTIMIZATION: meaning just recovered from rollback, no need to
                    // send request, use reply cache.
                    Debug.info(Debug.MODULE_EXEC, "Thread %d: recovered from rollback.", id);
                    if (csbns[id].copyReplyFromSecondService != null) {
                        return csbns[id].copyReplyFromSecondService;
                    } else {
                        byte[] replyFromSecondService = csbns[id].execute(request);
                        return replyFromSecondService;
                    }
                } else {
                    Debug.debug(Debug.MODULE_EXEC, "About to actually get the reply from the second service");
                    byte[] replyFromSecondService;
                    if (parameters.pipelinedBatchExecution) {
                        //System.out.println(id + " before nested: " + newGroupNo);
                        Debug.info(Debug.MODULE_EXEC, "TIMINGS[EBN] Thread %d StartYieldPipeline %d",
                                id, System.nanoTime());
                        glue.yieldPipeline();
                        Debug.info(Debug.MODULE_EXEC, "TIMINGS[EBN] Thread %d EndYieldPipeline %d",
                                id, System.nanoTime());

                        Debug.info(Debug.MODULE_EXEC, "TIMINGS[EBN] Thread %d StartCSBNSExecute %d",
                                id, System.nanoTime());
                        //System.out.println(id + " before nested2: " + newGroupNo);
                        replyFromSecondService = csbns[id].execute(request);
                        Debug.info(Debug.MODULE_EXEC, "TIMINGS[EBN] Thread %d EndCSBNSExecute %d",
                                id, System.nanoTime());
                        //System.out.println(id + " after nested1: " + newGroupNo);
                        Debug.info(Debug.MODULE_EXEC, "TIMINGS[EBN] Thread %d StartWaitTurnAfterNested %d",
                                id, System.nanoTime());
                        CRGlueThread myThread = (CRGlueThread) Thread.currentThread();
                        myThread.waitForMyTurn();
                        //System.out.println(id + " after nested: " + newGroupNo);
                        Debug.info(Debug.MODULE_EXEC, "TIMINGS[EBN] Thread %d EndWaitTurnAfterNested %d",
                                id, System.nanoTime());
                    } else {
                        if (parameters.sendNested) {
                            //System.out.println("before nested: " + newGroupNo);
                            replyFromSecondService = csbns[id].execute(request);
                            //System.out.println("after nested" + newGroupNo);
                        } else {
                            replyFromSecondService = null;
                        }
                        csbns[id].copyReplyFromSecondService = replyFromSecondService;
                    }

                    return replyFromSecondService;
                }
            }
            else {
                return sendNormalModeNestedRequest(csbns, id, request);
            }
        }
    }

    private byte[] sendNormalModeNestedRequest(ClientShimBaseNode[] csbns, int clientId, byte[] request) {
        byte[] replyFromSecondService = null;
//        System.out.println("sending nested request in normal mode");

        if (parameters.primaryBackup) {
            if(myIndex == fd.primary()) {
                replyFromSecondService = csbns[clientId].execute(request);
                ForwardReply fr = new ForwardReply(myIndex, clientId, replyFromSecondService);
                sendToOtherExecutionReplicas(fr.getBytes(), myIndex);
            }
            else {
                replyFromSecondService = waitForwardedReply(clientId);
            }
        } else {
            if (getCurrentView() % parameters.getExecutionCount() == myIndex) {
                replyFromSecondService = csbns[clientId].execute(request);
                ForwardReply fr = new ForwardReply(myIndex, clientId, replyFromSecondService);
//                System.out.println("send nested response to other execution replicas");
                sendToOtherExecutionReplicas(fr.getBytes(), myIndex);
            }
            else {
//                System.out.println("waiting resposne from the primary");
                replyFromSecondService = waitForwardedReply(clientId);
//                System.out.println("got the response from primary");
            }
        }

        return replyFromSecondService;
    }

    private byte[] waitForwardedReply(int clientId) {
        byte[] command = null;
//        System.out.println(40);

        synchronized (forwardReplyLocks[clientId]) {
//            System.out.println(41);

            while (!forwardReplyArrivalArray[clientId]) {
                try {
                    forwardReplyLocks[clientId].wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
//            System.out.println(42);

            command = forwardReplies[clientId].getCommand();
            forwardReplyArrivalArray[clientId] = false;
            forwardReplyLocks[clientId].notify();
        }
//        System.out.println(43);

        return command;
    }


    private void process(ForwardReply vmb) {
        int clientId = (int) vmb.getClientId();
//        System.out.println(50);

        synchronized (forwardReplyLocks[clientId]) {
//            System.out.println(51);

            while (forwardReplyArrivalArray[clientId]) {
                try {
                    forwardReplyLocks[clientId].wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
//            System.out.println(52);

            forwardReplyArrivalArray[clientId] = true;
            forwardReplies[clientId] = vmb;
            forwardReplyLocks[clientId].notify();
        }

//        System.out.println(53);

    }

    public class HeartBeatThread extends Thread {

        public void run() {
            int dead = fd.dead();
            while (true) {
                if (dead != fd.dead()) {
                    dead = fd.dead();
                    if (myIndex == fd.primary() && fd.dead() != -1) {
                        Debug.info(Debug.MODULE_EXEC, "Backup is dead");
                        sendAllReplies(getLastSeqNoExecuted());
                    }
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public class ExecutionThread extends Thread {

        @Override
        public void run() {
            while (true) {
                Debug.debug(Debug.MODULE_EXEC, "[lcosvse]: Heartbeat of ExecutionThread.run().");
                try {
                    Debug.debug(Debug.MODULE_EXEC, "ExecutionThread: before take()\n");
                    VerifiedMessageBase vmb = pendingRequests.take();
                    for (VerifiedMessageBase message : pendingRequests) {
                        Debug.debug(Debug.MODULE_EXEC, "Message:%s", message.toString());
                    }
                    Debug.debug(Debug.MODULE_EXEC, "ExecutionThread: after take()\n");
                    //Temporary solution for now to prevent problems if some replicas stay behind(keep the request until the corresponding
                    //slice is executed. It is very simple but happened to be hard to debug since middle and backend uses different numbering scheme for the verify
                    //messages. IsMiddle condition seems weird but just to not affect backend replicas. However, similar problems can exist there as well.
                    if (isMiddle && vmb.getTag() == MessageTags.VerifyResponse && ((VerifyResponseMessage) vmb).getVersionNo() > lastVersionNoExecuted) {
                        pendingRequests.put(vmb);
                    } else if (!isMiddle && vmb.getTag() == MessageTags.VerifyResponse && ((VerifyResponseMessage) vmb).getVersionNo() > lastSeqNoToBeVerified) {
//                        System.out.println("backend behind, lastSeqno: " + lastSeqNoExecuted + " versionNo: " + ((VerifyResponseMessage)vmb).getVersionNo() + "lastSeqNoToBeVerified: " + lastSeqNoToBeVerified);
                        pendingRequests.put(vmb);
                    } else {
                        synchronized (executeLock) {
                            handle2(vmb);
                        }
                    }
//                    System.out.println("now it's okay ");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return;
                }
            }
        }
    }

    public class ExecuteBatchThread extends Thread {
        @Override
        public void run() {
            while (true) {
                Debug.debug(Debug.MODULE_EXEC, "[lcosvse]: Heartbeat of ExecuteBatchThread.run().");
                try {
//                    System.out.println("try to take: " + executeBatchRequests.size());
                    VerifiedMessageBase vmb = executeBatchRequests.take();
//                    System.out.println("try to handle");
                    handle2(vmb);
                    if(parameters.batchSuggestion && executeBatchRequests.size() == 0) {
                        needBatch.set(true);
                    }
                    //System.out.println("handled");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return;
                }
            }
        }
    }

    public void handle2(VerifiedMessageBase vmb) {
        Debug.fine(Debug.MODULE_EXEC, "Exec Handle %s from sender %d\n", vmb, vmb.getSender());
        switch (vmb.getTag()) {
            case BFT.exec.messages.MessageTags.VerifyResponse:
                Debug.debug(Debug.MODULE_EXEC, "[lcosvse]: handle2 case VerifyResponse.");
//                System.out.println("bak sen execlere 2");
                process((VerifyResponseMessage) vmb);
                break;
            case BFT.exec.messages.MessageTags.ExecuteBatch:
                process((ExecuteBatchMessage) vmb);
                TreeMap<Long, ExecuteBatchMessage> tmp = new TreeMap<Long, ExecuteBatchMessage>();
                // Make sure this clone is only done with the lock
                synchronized (executeLock) {
                    tmp = (TreeMap<Long, ExecuteBatchMessage>) requestCache.clone();
                }
                for (Long seqNo : tmp.keySet()) { //Useful if a request arrives earlier and is cached I think
                    ExecuteBatchMessage msg = tmp.get(seqNo);
                    if (seqNo == this.lastSeqNoExecuted + 1 && msg.getView() == this.currentView) {
                        Debug.info(Debug.MODULE_EXEC, "ExecuteCache %d\n", seqNo);
                        this.process(msg);
                    }
                }
                break;
            case BFT.exec.messages.MessageTags.FetchState:
                process((FetchStateMessage) vmb);
                break;
            case BFT.exec.messages.MessageTags.AppState:
                process((AppStateMessage) vmb);
                break;
            case BFT.exec.messages.MessageTags.PBRollback:
                process((PBRollbackMessage) vmb);
                break;
            case BFT.exec.messages.MessageTags.PBFetchState:
                process((PBFetchStateMessage) vmb);
                break;
            default:
                Debug.kill("servershim does not handle message " + vmb.getTag());
        }
    }

    protected void process(ReadOnlyRequest req) {

        if (!validateClientMacArrayMessage(req, myIndex)) {
            Debug.warning(Debug.MODULE_EXEC, "Validation failed for %s \n", req);
        }

        // need to rate limit read only requests

        // its valid, so execute it
        requestHandler.execReadOnly(
                req.getCore().getCommand(),
                new BFT.exec.RequestInfo(true, (int) (req.getCore().getSendingClient()), (int) (req.getCore()
                        .getSubId()), -1, req.getCore().getRequestId(), 0, 0));
    }

    public void readOnlyResult(byte[] result, RequestInfo info) {
        // Debug.println("\t\treadonlyresult for "+clientId+" at "+reqId);
        ReadOnlyReply reply = new ReadOnlyReply(myIndex, info.getRequestId(), result);
        authenticateClientMacMessage(reply, info.getClientId(), info.getSubId());
        sendToClient(reply.getBytes(), info.getClientId(), info.getSubId());
    }

    protected synchronized void preProcess(ClientRequest req) {
        SignedRequestCore rc = (SignedRequestCore) req.getCore();
        int client = rc.getSendingClient();

        if(!parameters.isMiddleClient) {
            Debug.debug(Debug.MODULE_EXEC, "preProcessing ClientRequest");
            if (!parameters.primaryBackup || parameters.filtered) {
                throw new RuntimeException("Only primary backup can get client request");
            }
            Debug.debug(Debug.MODULE_EXEC, "In ClientRequest preProcess()");
            if (tryRetransmit(rc)) {
                return;
            }
            if (fd.primary() != myIndex) {
                return;
            }
//            System.out.println("client: " + client);
            if (this.lastRequestIdOrdered[client] >= rc.getRequestId()) {
                Debug.fine(Debug.MODULE_EXEC, "receiving old client request %d %d\n", rc.getSendingClient(),
                        rc.getRequestId());
                return;
            }
        }
        else {
            int newRequestId = (int) rc.getRequestId();

            GoodQuorum<ClientRequest> quorum = replicatedClients[client].get(newRequestId);
            if(quorum != null && quorum.isComplete()) {
                return;
            }
            else {
                if (quorum == null) {

                    quorum = new GoodQuorum<ClientRequest>(
                            this.members.getClientReplicationCount(client),
                            this.members.getShimLayerQuorumSize(client),
                            0);
                    replicatedClients[client].put(newRequestId, quorum);
                    while (newRequestId >  + lastRemovedQuorums[client] + maxPendingRequestPerClient) {
                        replicatedClients[client].remove(lastRemovedQuorums[client]++);
                    }
                }

                quorum.addEntry(req);
                if (!quorum.isComplete()) {
//                    System.out.println("not yet complete: " + newRequestId);
                    return;
                }

//                System.out.println("wow complete quourum: " + newRequestId + " now can create batch for client: " + client);
            }
        }

//        System.out.println("request id: " + rc.getRequestId());
        this.lastRequestIdOrdered[client] = rc.getRequestId();
        try {
            Debug.fine(Debug.MODULE_EXEC, "Adding a request to pendingClientRequests\n");
            this.pendingClientRequests.put(rc);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    //TODO think about locking granularity
    protected synchronized void process(BatchSuggestion bs) {
        int sender = bs.getSender();
        int subId = bs.getSubId();
//        System.out.println("processing BatchSuggestion from id: " + sender + ", subid: " + subId);
        GroupClientRequests gr =  (GroupClientRequests) bs.getCore();
//        System.out.println("Finally: " + gr);

        int newNestedGroupNo = gr.getNewNestedGroupNo();
        GoodQuorum<BatchSuggestion> quorum = groupQuorumMap.get(newNestedGroupNo);
        //TODO think about GC. I think it is also not implemented in previous version but since process(ClientRequest req) doesn't have condition with equality,
        //lastGC's stay zero and the client sends it as zero and continue without a problem and without GC

        //should use subId
        if(quorum != null && quorum.isComplete()) {
            return;
        }
        else {
            if (quorum == null) {

                quorum = new GoodQuorum<BatchSuggestion>(
                        this.members.getClientReplicationCount(sender),
                        this.members.getShimLayerQuorumSize(sender),
                        0);
                groupQuorumMap.put(newNestedGroupNo, quorum);
                while (newNestedGroupNo > lastRemovedGroupQuorumId + maxPendingRequestPerClient) {
                    groupQuorumMap.remove(lastRemovedGroupQuorumId++);
                }
            }

            quorum.addEntry(bs);
            if (!quorum.isComplete()) {
//                System.out.println("not yet complete: " + newNestedGroupNo);
                return;
            }

//            System.out.println("wow complete quourum: " + newNestedGroupNo + " now can create batch");
            int suggestedBatchSize = gr.getNumberOfNestedRequestInTheSlice();
            tmpPreferredBatchSize.set(suggestedBatchSize);
            LinkedBlockingQueue<RequestCore> tmp = new LinkedBlockingQueue<>();
            byte[][] requests = gr.getRequests();

            for(int i = 0; i < requests.length; i++) {
                if(requests[i] != null) {
                    SignedRequestCore src = new SignedRequestCore(requests[i], parameters);
                    int client = src.getSendingClient();
                    if (this.lastRequestIdOrdered[client] >= src.getRequestId()) {
                        System.out.println("old request");
                        continue;
                    }
                    this.lastRequestIdOrdered[client] = src.getRequestId();
                    //FilteredRequestCore frc = new FilteredRequestCore(parameters, src.getEntry());
                    tmp.add(src);//try frc if it does not work
                }
            }

            tmp.drainTo(pendingClientRequests);
            groupQuorumMap.remove(newNestedGroupNo);
         }

    }

    public void handle(byte[] vmbbytes) {
//        System.out.println("a message received");
        messageLogger.logEnd();
        messageLogger.logStart();
        MessageFactory factory = new MessageFactory(parameters);
        VerifiedMessageBase vmb = factory.fromBytes(vmbbytes);
        Debug.debug(Debug.MODULE_EXEC, "Handle %s\n", vmb);
        Debug.debug(Debug.MODULE_EXEC, "HOW IS THIS CALLED!!!!");
//        try {
//            throw new Exception();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        System.out.println("tag of message received: " + vmb.getTag());
        switch (vmb.getTag()) {
            case MessageTags.ForwardReply:
//                System.out.println("get forwarded reply");
                process((ForwardReply) vmb);
                return;
            case MessageTags.BatchSuggestion:
//                System.out.println("batch suggestion comes to execBaseNode directly in primary backup mode");
                BatchSuggestion bs = (BatchSuggestion) vmb;
                process(bs);
                return;
            case MessageTags.FilteredBatchSuggestion:
                preProcess((FilteredBatchSuggestion) vmb);
                break;
            case BFT.exec.messages.MessageTags.FilteredRequest:
                requestLogger.logEnd();
                preProcess((FilteredRequest) vmb);
                requestLogger.logStart();
                break;
            case BFT.exec.messages.MessageTags.VerifyResponse:
//                System.out.println("Received VerifyResponse");
                preProcess((VerifyResponseMessage) vmb);
                break;
            case BFT.exec.messages.MessageTags.ExecuteBatch:
                Debug.info(Debug.MODULE_EXEC, "handle next batch %d", ((ExecuteBatchMessage) vmb).getSeqNo());
//                System.out.println("Execute Batch");
                preProcess((ExecuteBatchMessage) vmb);
                break;
            case BFT.exec.messages.MessageTags.FetchState:
                preProcess((FetchStateMessage) vmb);
                break;
            case BFT.exec.messages.MessageTags.AppState:
                preProcess((AppStateMessage) vmb);
                break;
            case BFT.exec.messages.MessageTags.ReadOnlyRequest:
                process((ReadOnlyRequest) vmb);
                return;
            case BFT.exec.messages.MessageTags.ClientRequest:
                ClientRequest cr = (ClientRequest) vmb;
//                System.out.println("handle client: " + cr.getCore().getSendingClient() + " and reqId: " + cr.getCore().getRequestId() + ", subId: " + cr.getSubId());
                preProcess(cr);
                return;
            case BFT.exec.messages.MessageTags.PBRollback:
                preProcess((PBRollbackMessage) vmb);
                return;
            case BFT.exec.messages.MessageTags.PBFetchState:
                preProcess((PBFetchStateMessage) vmb);
                return;
            default:
                Debug.kill("servershim does not handle message " + vmb.getTag());
        }
    }

    public static void main(String[] args) {
        int id = Integer.parseInt(args[0]);
        String membership = args[1];
        ExecBaseNode shim = new ExecBaseNode(membership, id);
        shim.start();
    }

    public int getMyIndex() {
        return myIndex;
    }

    public void resetThreadIdToExecute() {
        glue.resetThreadIdToExecute();
    }

//    public synchronized void sendGroupClientRequest(byte[] req, ClientShimBaseNode csbn, int clientId, int groupSize) {
//        //TODO change for pipelined version
//        if(groupClientRequests == null) {
//            groupClientRequests = new GroupClientRequests(groupSize);
//        }
//
//        boolean isComplete = groupClientRequests.addRequest(req, clientId);
////        groupClientRequests[clientId % parameters.numWorkersPerBatch] = req;
////        currentGroupSize++;
//
//        if(isComplete) {
//            //create group request send, use csbn
//            //reset the variables
//            groupClientRequests = null;
//        }
//    }

    //TODO I can just use synchronized map instead of making the method synchronized. This could be more efficient.
    //TODO if there are more than one backend this method wouldn't work
    public synchronized void sendGroupClientRequestToFilter(byte[] req, ClientShimBaseNode csbn, int clientId, int groupSize, int newNestedGroupNo) {
        GroupClientRequests group = filterGroupMap.get(newNestedGroupNo);
        if(group == null) {
            group = new GroupClientRequests(groupSize, newNestedGroupNo, parameters.numWorkersPerBatch);
            filterGroupMap.put(newNestedGroupNo, group);
        }

        boolean isComplete = group.addRequest(req, clientId);
//        System.out.println("I have added for group no: " + newNestedGroupNo);

        if(isComplete) {
            //create group request send, use csbn
//            System.out.println("Try to send: " + group + " and hash of wall: " + Arrays.toString(wallStateHash));
            group.setStateHash(hashMap.get(newNestedGroupNo));
            hashMap.remove(newNestedGroupNo);
            BatchSuggestion bs = new BatchSuggestion(parameters, csbn.getMembers().getMyId(), csbn.getMembers().getMySubId(), group, false);
            if (parameters.primaryBackup || parameters.batchSuggestion) {
                ;//do nothing
            } else if (!parameters.filtered) {
                authenticateOrderMacArrayMessage(bs);
            } else {
                authenticateFilterMacArrayMessage(bs);
            }

            int fc = parameters.getFilterCount();
            int j = parameters.mediumFilterQuorumSize();
            int index = 0;//TODO make index dynamic
            for (int i = 0; i < j; i++) {
                index = (index + 1) % fc;
//                System.out.println("send to filter replica " + index);
                csbn.sendToFilterReplica(bs.getBytes(), index);
            }

            //reset the variables
            filterGroupMap.remove(newNestedGroupNo);
        }
    }

    //TODO I can just use synchronized map instead of making the method synchronized. This could be more efficient.
    //TODO if there are more than one backend this method wouldn't work
    public synchronized void sendGroupClientRequestToExecution(byte[] req, ClientShimBaseNode csbn, int clientId, int groupSize, int newNestedGroupNo, int primary) {
        //assert (parameters.primaryBackup); //this assertion is not valid anymore, because other modes can send to an unreplicated backend

        GroupClientRequests group = filterGroupMap.get(newNestedGroupNo);
        if(group == null) {
            group = new GroupClientRequests(groupSize, newNestedGroupNo, parameters.numWorkersPerBatch);
            filterGroupMap.put(newNestedGroupNo, group);
        }

        boolean isComplete = group.addRequest(req, clientId);
//        System.out.println("I have added for group no: " + newNestedGroupNo);

        if(isComplete) {
            //create group request send, use csbn
//            System.out.println("Try to send: " + group + " and hash of wall: " + Arrays.toString(wallStateHash));
            group.setStateHash(hashMap.get(newNestedGroupNo));
            hashMap.remove(newNestedGroupNo);
            BatchSuggestion bs = new BatchSuggestion(parameters, csbn.getMembers().getMyId(), csbn.getMembers().getMySubId(), group, false);

//            System.out.println("send group to execution primary");
            csbn.sendToExecutionReplica(bs.getBytes(), primary);

            //reset the variables
            filterGroupMap.remove(newNestedGroupNo);
        }
    }

    public void addHashForGroup(int i, byte[] hash) {
        hashMap.put(i, hash);
    }
}
