// $Id: OrderBaseNode.java 733 2011-09-12 23:30:35Z aclement $


// dont be an idiot
package BFT.order;

import Applications.tpcw_webserver.rbe.args.Arg;
import BFT.BaseNode;
import BFT.Debug;
import BFT.messages.*;
import BFT.network.PassThroughNetworkQueue;
import BFT.network.netty2.ReceiverWrapper;
import BFT.network.netty2.SenderWrapper;
import BFT.order.messages.*;
import BFT.order.messages.MessageTags;
import BFT.order.statemanagement.*;
import BFT.util.LogFilter;
import BFT.util.Role;

import java.io.File;
import java.io.FileInputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// receiving
// sending
// agreement
// view change
// resolving inconsistency
// order specific utility
// generic utility
// order node state
// network communication


public class OrderBaseNode extends BaseNode {


    boolean DISABLE_VC = false;
    long CPNOW = -299;

    long requestsOrdered = 0;

    // Maintain one copy of the working state
    protected CheckPointState workingState;
    // maintain a collection of previous checkpoints
    protected CheckPointState[] stateSnapshots;
    // index of the most recent stable snapshot
    protected int baseIndex;
    // incoming filtered client requests
    protected FilteredQuorum[] filteredQuorums;


    // Maintain a set of certificate lists
    // certificates[i] is requests executed starting from
    // stateSnapshots[i]
    protected Certificate[][] certificates;
    // index of the certificate list that we are currently working on
    protected int currentIndex;
    // collection of preprepares that are "in the future"
    protected PrePrepare[] futurePPs;
    protected Prepare[][] futurePs;
    protected Commit[][] futureCs;

    // flagging whether we are forcing prepareds or not
    private boolean forcingprepared = false;


    //    protected long currentSequenceNumber;
    protected long currentView;
    protected long maxPrepared = -1;
    protected long maxCommitted = -1;

    // certificate for gathering cptokens
    protected Quorum<CPTokenMessage> cpCert;


    // Cleaner
    protected RequestQueue rcQueue;


    // allowable to create batches
    protected boolean batchCreationAllowed = false;

    // a replica has initiated a view change
    protected boolean somebodyVotedForVC = false;

    // Retransmission backoffs
    protected long lastLastExecCheck[];
    protected int lastExecDelay[];
    protected boolean lastExecLoadCP[];
    protected long lastClientRetrans[];


    // view changes
    protected long lastPrepreparedView;
    protected ViewChangeRow[] viewChanges;
    protected ViewInfo vInfo = null;
    protected NewView[] nvCache = null;
    protected boolean changingView = false;
    protected boolean actedOnView = false;
    protected boolean actedOnDefinedView = false;
    protected PrePrepare[] vcPPs;
    protected Commit[] vcCommits;

    protected double[] observed; // observed throughputs
    protected double thresh;  // current throughput threshold
    protected long runningCount; // number of requests in the current
    // cp interval
    protected long viewTotal; // number of requests in the current view
    protected long oldTime; // start time of the current cp interval
    protected long viewStartTime; // time of the current view start
    protected int cpCount; // number of CPs since the last view change
    protected boolean scheduleViewChange; // true if its time to
    // schedule a view change
    protected boolean doViewChangeNow = false;
    protected boolean active[]; // list of active status for all clients
    protected int activeCount = 0; // number of clients active in the previous cp interval
    private int PREFERED_BATCH_SIZE;
    private long batchFillTime;
    private boolean dynamicBatchFillTime;
    private CreateBatchThread batchThread;

    // NB logging
    private final int NUM_LOGGERS = 2;
    private int currentLog;
    private NBLogQueue nbq;

    // CP logging
    private ExecutorService pool = null;
    private CPQueue cpwq = null;

    // cleaned request cores
    //    private RequestCoreQueue rcQueue = null;

    private int myIndex;
    int tmpCount = 0;

    public OrderBaseNode(String membership, String orderConfig, int myId) {
        super(membership, BFT.util.Role.ORDER, myId);
        if (parameters.toleratedOrderCrashes == 0) {
            DISABLE_VC = true;
        }
        myIndex = myId;
        System.out.println("creating order basenode");
        // do whatever else is necessary

        if (BFT.order.Parameters.maxPeriods != 2) {
            Debug.kill("maxPeriods must be 2");
            System.exit(0);
        }

        rcQueue = new RequestQueue(parameters);
        filteredQuorums =
                new FilteredQuorum[parameters.getNumberOfClients()];
        for (int i = 0; i < filteredQuorums.length; i++)
            filteredQuorums[i] =
                    new FilteredQuorum(parameters, parameters.smallFilterQuorumSize(),
                            parameters.mediumFilterQuorumSize(),
                            parameters.largeFilterQuorumSize(),
                            parameters.getFilterCount());
        certificates =
                new Certificate[BFT.order.Parameters.maxPeriods][BFT.order.Parameters.checkPointInterval];
        futurePPs = new PrePrepare[BFT.order.Parameters.checkPointInterval];
        futurePs = new Prepare[BFT.order.Parameters.checkPointInterval][];
        futureCs = new Commit[BFT.order.Parameters.checkPointInterval][];

        for (int i = 0; i < certificates.length; i++) {
            for (int j = 0; j < certificates[i].length; j++) {
                boolean cp = j == certificates[i].length - 1;
                certificates[i][j] =
                        new Certificate(parameters, parameters.getOrderCount(), cp);
            }
        }
        for (int i = 0; i < futurePs.length; i++) {
            futurePs[i] = new Prepare[parameters.getOrderCount()];
            futureCs[i] = new Commit[parameters.getOrderCount()];
        }
        stateSnapshots = new CheckPointState[BFT.order.Parameters.maxPeriods];
        stateSnapshots[0] = new CheckPointState(parameters, parameters.getNumberOfClients());
        workingState = new CheckPointState(stateSnapshots[0]);

        currentIndex = 0;
        baseIndex = 0;

        cpCert = new Quorum<CPTokenMessage>(parameters.getExecutionCount(),
                parameters.smallExecutionQuorumSize(),
                0);

        // exec retransmission backoffs
        lastLastExecCheck = new long[parameters.getExecutionCount()];
        lastExecDelay = new int[parameters.getExecutionCount()];
        lastExecLoadCP = new boolean[parameters.getExecutionCount()];
        for (int i = 0; i < lastExecDelay.length; i++) {
            lastLastExecCheck[i] = 0;
            lastExecDelay[i] = 100;
            lastExecLoadCP[i] = false;
        }

        // client retransmission control
        lastClientRetrans = new long[parameters.getNumberOfClients()];

        // active counting
        active = new boolean[parameters.getNumberOfClients()];
        activeCount = 0;
        for (int i = 0; i < active.length; i++)
            active[i] = false;


        //cleaner = new Cleaner(this);
        // view changes
        changingView = false;
        viewChanges = new ViewChangeRow[parameters.getOrderCount()];
        vcPPs = new PrePrepare[2 * BFT.order.Parameters.checkPointInterval];
        vcCommits = new Commit[parameters.getOrderCount()];
        nvCache = new NewView[viewChanges.length];
        for (int i = 0; i < viewChanges.length; i++)
            viewChanges[i] = new ViewChangeRow(parameters);

        observed = new double[parameters.getOrderCount()];
        for (int i = 0; i < observed.length; i++) {
            observed[i] = 100000.0;
        }
        thresh = observed[0];
        oldTime = System.currentTimeMillis();
        viewStartTime = System.currentTimeMillis();
        scheduleViewChange = false;
        //cleaner = new Cleaner(this);

        currentLog = 0;
        pool = Executors.newCachedThreadPool();
        obn = this;


        // Load checkpoints and logs from disk, if possible
        File cpFile1 = null, cpFile2 = null, nbFile1 = null, nbFile2 = null;

        System.out.println("reading cp files");

        try {
            String suffix = "_ORDER_CP.LOG";
            String suffix2 = "_" + myIndex + suffix;
            String nbsuffix = "_NB.LOG";
            String nbsuffix2 = "_" + myIndex + nbsuffix;
            cpFile1 = new File(0 + suffix2);
            File parent = cpFile1.getCanonicalFile().getParentFile();
            System.out.println("root directory " + parent);
            LogFilter lf = new LogFilter(suffix2);
            String[] files = parent.list(lf);
            if (files.length == 0) {
                files = parent.list(new LogFilter(suffix));
            } else {
                suffix = suffix2;
                nbsuffix = nbsuffix2;
            }
            long CP1 = -1, CP2 = -1;
            System.out.println("found " + files.length + " files with suffix: " + suffix);
            for (int i = 0; i < files.length; i++) {
                long tmp = new Long(files[i].replace(suffix, "")).longValue();
                if (tmp > CP1) {
                    System.out.println("found brand new cp at: " + tmp);
                    CP2 = CP1;
                    CP1 = tmp;
                } else if (tmp > CP2) {
                    System.out.println("could be the second oldest checkpoint " + tmp);
                    CP2 = tmp;
                } else {
                    System.out.println(files[i] + " is too old");
                }
            }
            if (CP1 != -1
                    && CP1 != CP2 + BFT.order.Parameters.checkPointInterval) {
                //		BFT.Debug.kill("Can't successfully load checkpoints b/c the numbers dont line up");
            }
            cpFile1 = null;
            if (CP1 != -1 && CP2 != -1) {
                System.out.println("I think i found files!");
                cpFile1 = new File(CP1 + suffix);
                cpFile2 = new File(CP2 + suffix);
                nbFile1 = new File(CP1 + nbsuffix);
                nbFile2 = new File(CP2 + nbsuffix);
            }

        } catch (Exception e) {
            System.out.println("File operation failed!");
            System.out.println(cpFile1);
            e.printStackTrace();
            cpFile1 = null;
        }
        if (cpFile1 != null)
            loadLogFiles(cpFile1, nbFile1, cpFile2, nbFile2);
        lastPrepreparedView = currentView;
        if(parameters.pipelinedSequentialExecution)
        {
            PREFERED_BATCH_SIZE =  ((parameters.execBatchSize + parameters.noOfThreads - 1) / parameters.noOfThreads) * parameters.noOfThreads;
        }
        else
        {
            PREFERED_BATCH_SIZE = parameters.execBatchSize;
        }
        batchFillTime = parameters.execBatchWaitTime;
        dynamicBatchFillTime = parameters.dynamicBatchFillTime;
        System.out.println("PreferredBatchSize: " + PREFERED_BATCH_SIZE + ", batchFillTime: " + batchFillTime + ", dynamicBatchFillTime: " + dynamicBatchFillTime);
        batchCreationAllowed = true;
        batchThread = new CreateBatchThread();
        batchThread.start();
    }


    protected void loadLogFiles(File cp1, File nb1, File cp2, File nb2) {
        try {
            System.out.println("loading " + cp1);
            System.out.println("loading " + cp2);
            int length = (int) cp2.length();
            FileInputStream fs = new FileInputStream(cp2);
            byte tmp[] = new byte[length];
            fs.read(tmp);
            fs.close();
            CheckPointState tmp1 = new CheckPointState(parameters, tmp);
            System.out.println(tmp1);

            fs = new FileInputStream(cp1);
            length = (int) cp1.length();
            tmp = new byte[length];
            fs.read(tmp);
            fs.close();
            CheckPointState tmp2 = new CheckPointState(parameters, tmp);
            System.out.println(tmp2);


            // // setting checkpoints as 0 and 1
            baseIndex = 0;
            stateSnapshots[0] = tmp1;
            stateSnapshots[baseIndex].markingStable();
            stateSnapshots[baseIndex].makeStable();
            stateSnapshots[baseIndex].commit();
            currentIndex = 1;
            stateSnapshots[1] = tmp2;
            stateSnapshots[1].markingStable();
            stateSnapshots[1].makeStable();
            //	     stateSnapshots[1].commit();
            workingState = new CheckPointState(stateSnapshots[1]);
            System.out.println("base checkpoint: ");
            System.out.println(stateSnapshots[0]);
            System.out.println("stable: " + stateSnapshots[0].isStable());
            System.out.println("not yet done checkpoint: ");
            System.out.println(stateSnapshots[1]);
            System.out.println("stable: " + stateSnapshots[1].isStable());
            System.out.println("working state: ");
            System.out.println(workingState);

            System.out.println("Need to convert a stream to a sequence of nextbatch messages");

            // base log of nbs, first to get GCed
            fs = new FileInputStream(nb2);
            tmp = new byte[4];
            byte btmp3[];
            System.out.println("log file 1: " + nb2);
            System.out.println("log file length: " + fs.available());
            int count = 0;
            NextBatch nb;
            while (fs.available() > 0) {
                fs.read(tmp);
                System.out.print(count + " ");
                util.UnsignedTypes.printBytes(tmp);
                length = (int) util.UnsignedTypes.bytesToLong(tmp);
                System.out.print(" " + length + " : ");
                btmp3 = new byte[length];
                fs.read(btmp3);
                nb = (NextBatch) MessageFactory.fromBytes(btmp3, parameters);
                System.out.println(nb);
                count++;
                int ind = (int) (nb.getSeqNo() % BFT.order.Parameters.checkPointInterval);
                certificates[baseIndex][ind].setNextBatch(nb);
                if (nb.getSeqNo() > maxPrepared &&
                        certificates[baseIndex][ind].isPrepared()) {
                    maxPrepared = nb.getSeqNo();
                    ind--;
                    while (ind >= 0 && !certificates[baseIndex][ind].isPrepared()) {
                        System.out.println("prepareing: " + certificates[baseIndex][ind].getSeqNo());
                        certificates[baseIndex][ind--].forcePrepared();
                    }
                }
                if (nb.getView() > currentView)
                    currentView = nb.getView();
            }

            System.out.println("maxPrepared after logfile 1: " + maxPrepared);

            // most recent log of nbs
            fs = new FileInputStream(nb1);
            tmp = new byte[4];
            System.out.println("log file 2: " + nb1);
            System.out.println("log file length: " + fs.available());
            count = 0;
            while (fs.available() > 0) {
                fs.read(tmp);
                System.out.print(count + " ");
                util.UnsignedTypes.printBytes(tmp);
                length = (int) util.UnsignedTypes.bytesToLong(tmp);
                System.out.print(" " + length + " : ");
                btmp3 = new byte[length];
                fs.read(btmp3);
                nb = (NextBatch) MessageFactory.fromBytes(btmp3, parameters);
                System.out.println(nb);
                count++;
                int ind = (int) (nb.getSeqNo() % BFT.order.Parameters.checkPointInterval);
                certificates[currentIndex][ind].setNextBatch(nb);

                if (nb.getSeqNo() > maxPrepared &&
                        certificates[currentIndex][ind].isPrepared()) {
                    maxPrepared = nb.getSeqNo();
                    ind--;
                    while (ind >= 0 && !certificates[currentIndex][ind].isPrepared()) {
                        System.out.println("preparing pass 2: " + certificates[currentIndex][ind].getSeqNo());
                        certificates[currentIndex][ind--].forcePrepared();
                    }
                }
            }
            count = 0;
            while (!certificates[currentIndex][count].isClear() && count < certificates[currentIndex].length) {
                Certificate cert = certificates[currentIndex][count];
                System.out.println(cert.getCommandBatch());
                System.out.println(cert.getSeqNo());
                System.out.println(cert.getHistory());
                System.out.println(cert.getNonDeterminism().getTime());
                workingState.addNextBatch(cert.getCommandBatch(), cert.getSeqNo(), cert.getHistory(),
                        cert.getNonDeterminism().getTime());
                count++;
            }

            System.out.println("We are in view: " + currentView);
            System.out.println("we are starting from " + getBaseSequenceNumber());
            System.out.println("and we are at        " + getCurrentSequenceNumber());
            System.out.println("base checkpoint");
            System.out.println(stateSnapshots[0]);
            System.out.println("log 1");
            for (int l = 0; l < certificates[0].length; l++)
                System.out.println(certificates[0][l].getCertEntry());
            System.out.println("secondary checkpoint");
            System.out.println(stateSnapshots[1]);
            for (int l = 0; l < certificates[1].length; l++)
                System.out.println(certificates[1][l].getCertEntry());
            System.out.println("working state:");
            System.out.println(workingState);
            // after rebooting, start a view change!
            //scheduleViewChange = true;
            updateHeartBeat();
        } catch (Exception e) {
            BFT.Debug.kill(e);
        }
    }


    public static OrderBaseNode obn;

    boolean started = false;

    /**
     * Handle a byte array, conver the byte array to the appropriate
     * message and call the requisite process method
     **/
    public void handle(byte[] vmbbytes) {
        int tmpCount = 0;
        while (!started) {
            tmpCount++;
            try {
                wait(500);
            } catch (Exception e) {
            }

            if (tmpCount % 100 == 0) {
                System.out.println("waiting for start");
            }
        }
        //long start = System.currentTimeMillis();
        //System.out.println("START " + start);
        VerifiedMessageBase vmb = MessageFactory.fromBytes(vmbbytes, parameters);
        System.out.println(vmb);
        switch (vmb.getTag()) {
            // 	case MessageTags.ClientRequest:
            // 	    //Debug.profileStart("CLIENT_REQUEST");
            // 	    BFT.Debug.kill("not handling straight client requests");
            // 	    //	    process((ClientRequest)vmb);
            // 	    //Debug.profileFinis("CLIENT_REQUEST");
            // 	    break;
            case MessageTags.FilteredRequest:
                Debug.profileStart("FILTERED_REQ");
//                System.err.println("filtered request received from " + ((FilteredRequest) vmb).getSender());
                process((FilteredRequest) vmb);
                Debug.profileFinis("FILTERED_REQ");
                checkHeartBeat();
                break;
            case MessageTags.LastExecuted:
                Debug.profileStart("LAST_EXEC");
                process((LastExecuted) vmb);
                Debug.profileStart("LAST_EXEC");
                break;
            case MessageTags.CPLoaded:
                process((CPLoaded) vmb);
                break;
            case MessageTags.CPTokenMessage:
                Debug.profileStart("CPTOKEN");
                process((CPTokenMessage) vmb);
                Debug.profileFinis("CPTOKEN");
                break;
            case MessageTags.PrePrepare:
                Debug.profileStart("PREPREPARE");
                //System.err.println("received pp");
                process((PrePrepare) vmb);
                Debug.profileFinis("PREPREPARE");
                break;
            case MessageTags.Prepare:
                Debug.profileStart("PREPARE");
                process((Prepare) vmb);
                Debug.profileFinis("PREPARE");
                break;
            case MessageTags.Commit:
                Debug.profileStart("COMMIT");
                process((Commit) vmb);
                Debug.profileFinis("COMMIT");
                break;
            case MessageTags.ViewChange:
                process((ViewChange) vmb);
                break;
            case MessageTags.ViewChangeAck:
                process((ViewChangeAck) vmb);
                break;
            case MessageTags.NewView:
                process((NewView) vmb);
                break;
            case MessageTags.MissingViewChange:
                process((MissingViewChange) vmb);
                break;
            case MessageTags.ConfirmView:
                process((ConfirmView) vmb);
                break;
            case MessageTags.CommitView:
                process((CommitView) vmb);
                break;
            case MessageTags.RelayViewChange:
                process((RelayViewChange) vmb);
                break;
            case MessageTags.OrderStatus:
                process((OrderStatus) vmb);
                break;
            case MessageTags.MissingOps:
                process((MissingOps) vmb);
                break;
            case MessageTags.RelayOps:
                process((RelayOps) vmb);
                break;
            case MessageTags.StartView:
                process((StartView) vmb);
                break;
            case MessageTags.ForwardedRequest:
                process((ForwardedRequest) vmb);
                break;
            case MessageTags.MissingCP:
                process((MissingCP) vmb);
                break;
            case MessageTags.RelayCP:
                process((RelayCP) vmb);
                break;
            default:
                Debug.kill(new RuntimeException("order ndoe does not handle message " + vmb.getTag()));
        }

        //System.out.println("FINIS " + (System.currentTimeMillis()-start));

        //			checkHeartBeat();
    }

    protected void process(ForwardedRequest req) {
        //Debug.println("process forwarded request @ seqno "+getCurrentSequenceNumber() + " from "+req.getSendingReplica());
        if (!amIPrimary()) {
            return;
        }
        if (!validateOrderMacArrayMessage(req, myIndex)) {
            BFT.Debug.kill(new RuntimeException("bad validate"));
        }
        RequestCore c = req.getCore();
        if (c.getRequestId() <=
                getWorkingState().getLastOrdered(c.getSendingClient()))
            return;

        BFT.Debug.kill("not doing jack with a forwarded request");
        //Debug.println("ok, its new.  do something with it ");
        //	cleaner.clean(c);
    }

    long start;
    long filterStart;
    long count = 0;
    long filtercount;
    boolean procFirstReq = false;
    boolean procFirstFiltReq = false;
    long filtersmall = 0;
    long filtermed = 0;
    long filterlarge = 0;

    protected void process(FilteredRequest req) {
        int sender = req.getSendingReplica();
        //System.err.println("filtered request: " + req);
        if (!validateFilterMacArrayMessage(req, getMyOrderIndex()))
        {
            BFT.Debug.kill("broke the fitlered request authetnication");
        }
//        else{
//            System.err.println("Succesfull validation");
//        }
        for (int i = 0; i < req.getCore().length; i++) {
            process(req.getCore()[i], sender);
        }
    }

    protected void process(FilteredRequestCore req, int send) {
        //System.err.println("process FRC from send=" + send);
        req.setSendingReplica(send);
        FilteredRequestCore rc = req;
        int sender = (int) req.getSendingReplica();

//        System.err.println("\t1");
        if (!validatePartialFilterMacSignatureMessage(rc, sender))
        {
            System.err.println("macisgnature failed");//Debug.kill("WTF");
        }
//        else
//        {
//            System.err.println("succesful validation2");
//        }
//        System.err.println("\t2");
        FilteredQuorum fq = filteredQuorums[rc.getSendingClient()];
        //System.err.println("processing core: " + rc);
        synchronized (fq) {
//            System.err.println("order recieved request from sending replica " + sender + " add to quorum!");
            fq.add(rc, req.getSendingReplica());

            if (fq.small() && tryRetransmit(rc)) {
                //System.err.println("retransmitting to " + rc.getSendingClient() + " @time " + System.currentTimeMillis() + " " + rc.getRequestId());
                fq.clear();
                return;
            }

            // if the quorum is large then process the "cleaned" requestcore
            if (fq.medium() && amIPrimary()) {
//                System.err.println("medium");
                processClean(fq.getEntry());
                fq.clear();
                return;
            }
            // if the quorum is really big, start breaking bones
            if (fq.large()) {
//                System.err.println("large");

                processClean(fq.getEntry());
                fq.clear();
                if (true) {
                    return;
                }
            }
        }
    }


    /**
     * returns true if the request has already been ordered and should
     * be processed through (potential) retransmission.  return false
     * otherwise.
     **/
    protected boolean tryRetransmit(RequestCore c) {

//        System.out.println("trying a retransmit");
        long lastOrdered =
                getWorkingState().getLastOrdered(c.getSendingClient());
//        System.out.println("\tretransmit? " + lastOrdered + " " + c.getRequestId());
        if (lastOrdered >= c.getRequestId()) {
            // impose the rate limiting steps

            //if (true) return false;
            int client = (int) c.getSendingClient();
            if (System.currentTimeMillis() - lastClientRetrans[client]
                    < workingState.getRetransmitDelay(client)) {
                System.out.println("rejecting retransmit because its too soon");
                return true;
            }
            workingState.updateRetransmitDelay(client);
            //Retransmit ret = new Retransmit(parameters, client, getCurrentSequenceNumber(), myIndex);
            long orderId = getOrderedSequenceNumber(c.getSendingClient(), c.getRequestId());

            // I haven't seen this msg before plz order these!
            if (orderId == -1) {
                return false;
            }
            Retransmit ret = new Retransmit(parameters, client, orderId, c.getRequestId(), myIndex);
            authenticateExecMacArrayMessage(ret);
            // Perform some authentication
            lastClientRetrans[client] = System.currentTimeMillis();
            System.out.println("retransmitting: " + orderId +
                    " for client " + client);
            sendToAllExecutionReplicas(ret.getBytes());
            // and we're done

            // Now checking to see if the primary should
            // initate a prepare path
            long mp = maxPrepared;
            // 			// bad compare and swap
            // 			while (mp != maxPrepared)
            // 				mp = maxPrepared;
            // if the last ordered for this guy is greater than
            // maxprepared and im not changing view
            lastOrdered =
                    getWorkingState().getLastOrderedSeqNo(client);
            //Debug.println("lastOrdered: "+lastOrdered+" last prepared: "
            //+mp+" reall lastprepared: "+maxPrepared);

            synchronized (this) {
                if (lastOrdered > mp && !changingView && amIPrimary()) {
                    Certificate cert =
                            certificates[stateIndex(lastOrdered)][certificateIndex(lastOrdered)];

                    // If I havent prepared yet, then prepare
                    long cs = getCurrentSequenceNumber() - 1;
                    while (cs != getCurrentSequenceNumber() - 1)
                        cs = getCurrentSequenceNumber() - 1;
                    cert =
                            certificates[stateIndex(cs)][certificateIndex(cs)];
                    if (cert.getPrepare() == null) {
                        System.out.println("prepare (b)");
                        Prepare p = new Prepare(parameters, currentView, cs, cert.getHistory(), myIndex);
                        authenticateOrderMacArrayMessage(p);
                        cert.addMyPrepare(p);
                        sendToOtherOrderReplicas(p.getBytes(), myIndex);
                        forcingprepared = true;
                        if (cert.isPrepared())
                            actOnPrepared(p.getSeqNo());
                    }
                }

                return true;
            }
        } else {
            return false;
        }
    }

    /**
     * A clean request core is processed following the following steps:
     * <p>
     * (1) If the request core has been ordered, then discard it
     * <p>
     * (2) If I am not the primary and the requestcore has not appeared in
     * a preprepare, then forward to the primary
     * <p>
     * (3) If I am the primary and the request core has not yet
     * appeared in a preprepare then add the request core to the
     * request queue and attempt to form a new batch.  if a new batch
     * can be formed, then send the resulting pre prepare.
     * <p>
     * (4) Initial setup step: use the first request as a hint to go
     * fetch the intiail state.  This is a short term fix that NEEDS
     * to be addressed
     **/
    protected void processClean(RequestCore rc) {
//        System.err.println("ProcessClean " + rc);
        if (!isNextClientRequest(rc)) {
            return;
        }
        // (2) If I am not the primary then forward to the primary
        if (!amIPrimary()) {
            if (parameters.filtered)
                return;
            if (true)
                return;
            // dont want to deal with forwarded requests yet
            ForwardedRequest fr = new ForwardedRequest(parameters, myIndex, (SignedRequestCore) rc);
            authenticateOrderMacArrayMessage(fr);
            sendToOrderReplica(fr.getBytes(), primary(currentView));
        } else {
            rcQueue.add(rc);
//            System.err.println("changingView: " + changingView + ", primary: " + amIPrimary() + " ,BatchCreationAllowed1: " + batchCreationAllowed);
//            synchronized (batchLock)
//            {
//                while (!batchCreationAllowed) {
//                    try {
//                        batchLock.wait();
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                }
//            }
//            System.err.println("changingView: " + changingView + ", primary: " + amIPrimary() + " ,BatchCreationAllowed2: " + batchCreationAllowed);
            tryToCreateBatch(false);
        }
        //Debug.println("end of process clean request core");
    }

    public boolean canCreateBatch() {
        // cant create a batch if we're changing view

//        System.err.println("changingView: " + changingView + ", primary: " + amIPrimary() + " ,BatchCreationAllowed3: " + batchCreationAllowed + ", seqNo: " + getCurrentSequenceNumber());
        if (changingView || !amIPrimary() || !batchCreationAllowed) {
//            System.err.println("Inside: changingView: " + changingView + ", primary: " + amIPrimary() + " ,BatchCreationAllowed4: " + batchCreationAllowed + ", seqNo: " + getCurrentSequenceNumber());

            // 	    System.out.println(changingView+" "+!amIPrimary() +"  "+!batchCreationAllowed);
            // 	    System.out.println(getCurrentSequenceNumber());
            return false;
        }
        // cant create a batch if im not the primary


        int base = baseIndex;
        if (!stateSnapshots[base].isCommitted())
            if (getCurrentSequenceNumber() == 0) {
//                System.err.println(!stateSnapshots[base].isCommitted());
//                System.err.println(getCurrentSequenceNumber());
                return false;
            } else {
                Debug.kill(new RuntimeException("base should always be stable"));
            }

        long seqno = getCurrentSequenceNumber();
        if (seqno % BFT.order.Parameters.checkPointInterval == 0 &&
                seqno > 0 &&
                !certificates[stateIndex(seqno - 1)][certificateIndex(seqno - 1)].isCommitted()) {
//            System.err.println("need a committed seqno "+seqno);
            return false;
        }
        int baseplusone = (base + 1) % stateSnapshots.length;
        int baseminusone = (base + stateSnapshots.length - 1) % stateSnapshots.length;

        if (currentIndex == baseminusone
                && (seqno + 1) % BFT.order.Parameters.checkPointInterval == 0
                // (4)  If the requisit cp is not stable, then give up
                && !stateSnapshots[baseplusone].isStable()) {
//            System.err.println("need a stable cp " + seqno);
//            System.err.println(stateSnapshots[baseplusone]);
//            System.err.println(stateSnapshots[baseplusone].isStable());
            return false;
        }
        return true;
    }

    public boolean isNextClientRequest(RequestCore rc) {
        return workingState.getLastOrdered(rc.getSendingClient()) + 1 == rc.getRequestId();
    }

    /**
     * Returns true if the next batch was created and a preprepare
     * message sent to the other replicas, false otherwise.
     * <p>
     * Batch creation takes the following steps
     * <p>
     * (1)  If I'm not the primary, return false
     * (2)  If the base state is not stable, return false
     * * NOTE ** should only happen at start up
     * (3)  Check if we're the first sequence number in a checkpoint
     * o  If the previous seqno is not commited, return false
     * o  if seqno is not 0 (**START UPHACK**), return false
     * (4)  Get the appropriate stable checkpoint digest
     * (5)  Create the next preprepare
     * (5a)  return false if there are no requests to order
     * (7)  send the preprepare to the order replicas, including self
     **/
    long nonDetcounter = System.currentTimeMillis();

    synchronized protected void tryToCreateBatch(boolean timeout) {
        if (!canCreateBatch()) {
            //System.err.println("cant create batch 1");
            return;
        }
        synchronized (rcQueue) {
            if (!canCreateBatch()) {
                //System.err.println("cant create batch 2");
                return;
            }
            RequestCore cores[] = getCoresForBatch(timeout);
            if (cores == null) {
                //System.err.println("null cores");
                return;
            }
            long seqno = getCurrentSequenceNumber();

            // (1) not the primary
            if (!amIPrimary()) {
                Debug.kill(new RuntimeException("should not make it here"));
            }

            boolean cp = (getCurrentSequenceNumber() + 1) % BFT.order.Parameters.checkPointInterval == 0;

            // (2) If the base is not stable, return false
            if (!stateSnapshots[baseIndex].isStable()) {
                if (getCurrentSequenceNumber() == 0) {
                    Debug.kill(new RuntimeException("should not make it here"));
                } else {
                    Debug.kill(new RuntimeException("base should *always* be stable"));
                }
            }
            // (3)  First sequence number in a checkpoint
            if (seqno % BFT.order.Parameters.checkPointInterval == 0 && seqno > 0 &&
                    !certificates[stateIndex(seqno - 1)][certificateIndex(seqno - 1)].isCommitted()) {
                Debug.kill(new RuntimeException("should not make it here"));
            }

            // (5) create the preprepare

            // (5a) cant do anything if there are no request to make into a batch!
            if (cores.length == 0) {
//                System.err.println("cores.length = 0");
                return;
            }
            //		Debug.kill( new RuntimeException("cant make an empty batch!"));

            Digest d = stateSnapshots[baseIndex].getStableDigest();
            int baseplusone = (baseIndex + 1) % BFT.order.Parameters.maxPeriods;
            int baseminusone =
                    (baseIndex + stateSnapshots.length - 1) % stateSnapshots.length;
            if (currentIndex == baseminusone &&
                    (seqno + 1) % BFT.order.Parameters.checkPointInterval == 0)
                // (4)  If the requisit cp is not stable, then give up
                if (!stateSnapshots[baseplusone].isStable())
                    Debug.kill(new RuntimeException("should not make it here"));
                else
                    d = stateSnapshots[baseplusone].getStableDigest();

            for (int i = 0; i < cores.length; i++)
                activeClient(cores[i].getSendingClient());
            RequestBatch rb = new RequestBatch(parameters, cores);
            long time = System.currentTimeMillis();
            long lasttime = getWorkingState().getCurrentTime();
            if (time > lasttime)
                lasttime = time;
            else
                lasttime++;
            //	getWorkingState().setCurrentTime(lasttime);
            PrePrepare pp =
                    new PrePrepare(parameters, currentView, seqno,
                            workingState.getHistory(),
                            rb,
                            new NonDeterminism(lasttime, nonDetcounter++),
                            d,
                            myIndex);
            requestsOrdered += cores.length;
//            double batchsize = requestsOrdered;
//            //	    runningCount += cores.length;
//            batchsize = batchsize / (seqno + 1);
            if (getCurrentSequenceNumber() % 5000 == 0)
                ;//	    System.out.println("average batch size: "+batchsize+" "+ cores.length);
            authenticateOrderMacArrayMessage(pp);

            int state = stateIndex(seqno);
            int cert = certificateIndex(seqno);

            // (7) send the preprepare
            //	System.out.println("Sending PP: "+pp.getView()+" "+pp.getVersionNo());
            batchThread.resetBatchCreation(cores.length);
            batchCreationAllowed = false;
            sendToAllOrderReplicas(pp.getBytes());

//            System.err.println("sent to all order replicas " + pp);


            // cannot create a next batch until this one is processed!


            // created a batch, so can update the heartbeat now.
            // actually, gonna do that with the process(pp)
            // updateHeartBeat();

            // 	if (CPNOW > 0
            // 	    && getCurrentSequenceNumber() > 2*BFT.order.Parameters.checkPointInterval
            // 	    && getCurrentSequenceNumber() % (CPNOW-myIndex) == 0){
            // 	    //	    && getCurrentSequenceNumber() % BFT.order.Parameters.checkPointInterval != 0){
            // 	    //System.out.println("************ starting a view change******");
            // 	    startViewChange(currentView+1);
            // 	    return;
            // 	}
        }
    }

    private RequestQueue _tmpQueue;

    private RequestCore[] getCoresForBatch(boolean timeout) {
        synchronized (rcQueue) {
            if (_tmpQueue == null) {
                _tmpQueue = new RequestQueue(parameters);
            }
            if (rcQueue.size() < minimumBatchSize() && !timeout)
                return null;
            RequestCore tmp;
            while (rcQueue.size() > 0 && _tmpQueue.byteSize() < Parameters.maxBatchSize && _tmpQueue.size() != minimumBatchSize()) {
                tmp = (RequestCore) rcQueue.poll();
                if (isNextClientRequest(tmp)) {
                    _tmpQueue.add(tmp);
                }
            }
            System.out.println("batch size: " + _tmpQueue.size());
            
            RequestCore[] list = new RequestCore[_tmpQueue.size()];
            for (int i = 0; i < list.length; i++) {
                list[i] = (RequestCore) _tmpQueue.poll();
            }
//            System.err.println("batch size: " + list.length);
            return list;
        }
    }


    /****************************************************
     all functions above this point are part of forming batches and
     handling client requests.  previous functions may *read* state
     but will never write it.  these functions have no persistent effect
     all functions below this point are part of the "core" protocol
     i.e. may result in modifications to protocol state
     ****************************************************/


    /**
     * Process a PrePrepare.
     * Check that the message is
     * (1) part of this view
     * otherwise discard it
     * (2) sent by the primary
     * otherwise discard it
     * (3) the next request
     * (a) if it is old then discard it
     * (b) if it is in the 'near' future then cache it
     * for later processing
     * (c) otherwise discard it
     * (4) cache it if we're waiting for the previous commit
     * (5) check the MAC
     * (6) the time/nondeterminism are consistent
     * (7) The histories check out appropriately
     * i.e. the base history is consistent with the history
     * defined by the previous sequence number
     * (8) it contains an appropriate order CP Digest
     * (a)  last request in a cp interval has the
     * about to be commited CP associated with it.
     * and that cp is stable
     * (b)  other sequence numbers have the
     * last committed sequence number
     * and that cp is stable
     * (c)  if a cp is necessary but is not yet stable,
     * then cache the preprepare
     * (9) Check the signatures on the request cores
     **/
    synchronized protected void process(PrePrepare pp) {
        //    	System.out.println("\tprocess preprepare: "+pp.getVersionNo()+"/"+
        //   		      getCurrentSequenceNumber()+":"+
        //    		      getBaseSequenceNumber()+" view: "+pp.getView());

        // 	if ( pp.getSender() == myIndex)
        //	    Debug.kill("WTF");

        //	System.out.println("rcvd: "+pp);

        // (1) discard if its not part of this view
        if (pp.getView() != currentView) {
            //System.out.println("\tdiscarding pp not part of current view: "+
            //pp.getView()+" "+currentView);
            return;
        }

        // (2) drop the message if its not from the appropriate leader
        if (pp.getSendingReplica() !=
                primary(pp.getView()) % parameters.getOrderCount())
            Debug.kill(new RuntimeException(pp.getSendingReplica() +
                    "sender should not be sending in " +
                    pp.getView()));
        // (2a) if we're changing view, then cache it
        long seqno = pp.getSeqNo();
        if (changingView) {
            int index = (int) (seqno % vcPPs.length);
            vcPPs[index] = pp;
            //System.out.println("changing view");
            return;
        }

        //System.err.println("b");

        // (3a) Drop if it is old
        if (seqno < getCurrentSequenceNumber()) {
            System.out.println("old preprepare");
            return;
        }
        // (3)
        if (seqno >= getBaseSequenceNumber() +
                BFT.order.Parameters.maxPeriods *
                        BFT.order.Parameters.checkPointInterval) {
            // (3b) if its in the near enough future then cache it
            if (seqno < getBaseSequenceNumber() +
                    (BFT.order.Parameters.maxPeriods + 1) *
                            BFT.order.Parameters.checkPointInterval) {
                int ind =
                        (int) (seqno % BFT.order.Parameters.checkPointInterval);
                //Debug.println("\tcaching a future pp@"+seqno);
                futurePPs[ind] = pp;
                System.out.println("future pp");
                return;
            } else {
                // (3c) otherwise drop it (start a new view?)
                if (!changingView) {
//                    System.err.println("starting a view change within process preprepare");
                    startViewChange(currentView + 1);

                }
                return;
                //		Debug.kill(new RuntimeException("completely invalide sequence"+
                //				" number"+ seqno+":"+
                //				getBaseSequenceNumber()));
            }
        }
        //System.err.println("c");

        Certificate cert =
                certificates[stateIndex(seqno)][certificateIndex(seqno)];
        // (3b) cache it in the near future
        if (pp.getSeqNo() != getCurrentSequenceNumber()) {
            cert.cachePrePrepare(pp);
            //Debug.println("Cached for near future "+pp.getVersionNo()+" "+
            //getCurrentSequenceNumber()+" "+ changingView);
            System.out.println("out of order pp");
            return;
        }


        //System.err.println("d");

        // (4) cache the message if we're waiting for the commit
        if (seqno % BFT.order.Parameters.checkPointInterval == 0 &&
                seqno > 0 &&
                !certificates[stateIndex(seqno - 1)][certificateIndex(seqno - 1)].isCommitted()) {
            cert.cachePrePrepare(pp);
            //Debug.println("Cached b/c we're waiting for a commit");
            System.out.println("waiting on commit");
            return;
        }

        // (5) check the mac
        if (!validateOrderMacArrayMessage(pp, myIndex)
                || !pp.checkAuthenticationDigest()) {
            Debug.kill("MacArray didn't validate");
            return;
        }

        // (6) check that nondet time is consistent
        //System.err.println("e");

        long time = pp.getNonDeterminism().getTime();
        long current = System.currentTimeMillis();
        long lasttime = getWorkingState().getCurrentTime();
        if (time < lasttime
                || (current < time
                && time - current > BFT.order.Parameters.timeVariance)
                || (time < current
                && current - time > BFT.order.Parameters.timeVariance)) {
            Debug.kill("time doesnt work, local: " + current +
                    " last: " + lasttime + " new: " + time + " delta: " + (time - current) + " allowed: " +
                    BFT.order.Parameters.timeVariance);
            System.out.println("time screwy");
            return;
        }


        boolean cp = (getCurrentSequenceNumber() + 1) %
                BFT.order.Parameters.checkPointInterval == 0;

        // (7)  Check that the initial history matches the final
        //      history of the preceding sequence number
        HistoryDigest hist;
        if (stateIndex(seqno) == baseIndex && certificateIndex(seqno) == 0)
            hist = stateSnapshots[baseIndex].getHistory();
        else
            hist = certificates[stateIndex(seqno - 1)][certificateIndex(seqno - 1)].getHistory();
        if (!pp.getHistory().equals(hist))
            Debug.kill(new RuntimeException("Messed up histories!: " +
                    pp.getHistory() + " : " + hist));

        // (8)  Check that the checkpoint snapshots match up

        // (8b) not the last sequencenumber matches with the base cp index
        if (!cp &&
                !pp.getCPHash().equals(stateSnapshots[baseIndex].getStableDigest()))
            Debug.kill(new RuntimeException("cphashes dont match up properly"));
        int baseplusone = (baseIndex + 1) % BFT.order.Parameters.maxPeriods;

        //System.err.println("f");
        // (8a) the last sequencenumber in a cpinterval matches the *next* hash
        if (baseIndex != currentIndex && cp) {
            // (8c)  if the checkpoint is not stable, then return
            if (!stateSnapshots[baseplusone].isStable()) {
                cert.cachePrePrepare(pp);
                System.out.println("caching pp");
                return;
            }
            // (8a) if hte hashes dont match then explode
            if (!pp.getCPHash().equals(stateSnapshots[baseplusone].getStableDigest())) {
                System.out.println(pp.getCPHash());
                System.out.println(stateSnapshots[baseplusone].getStableDigest());
                Debug.kill(new RuntimeException("cphashes dont match"));
            }
        }
        //System.err.println("g");

        // (9) check the signatures on the request cores
        //Debug.println("\tsending preprepare to cleaner: "+pp.getVersionNo()+"/"+
        //		getCurrentSequenceNumber()+":"+
        //		getBaseSequenceNumber());
        if (!parameters.filtered)
            BFT.Debug.kill("should clean a preprepare now");
            // 	    cleaner.clean(pp);
        else {
            RequestCore[] reqs = pp.getRequestBatch().getEntries();
//            System.err.println("reqs.length: " + reqs.length);
            for (int i = 0; i < reqs.length; i++)
            {
                if (!validateFullFilterMacSignatureMessage((FilteredRequestCore) reqs[i],
                        parameters.mediumFilterQuorumSize(), myIndex))
                {
//                    System.err.println("WHYYYY! " + i );
                    Debug.kill("failed verification");
                }
            }
            if (pp.checkAuthenticationDigest())
                processClean(pp);
            else
                BFT.Debug.kill("bad things!");

        }
        //System.err.println("batchLock1 in process(pp)");
        batchCreationAllowed = true;
        //System.err.println("batchLock2 in process(pp)");
//        System.err.println("finish processing preprepare");
    }

    /**
     * Processes a 'cleaned' preprepare.  The initial assumption is
     * that all of the authentication on the preprepare has been
     * checked out and all contained request cores have valid
     * signatures and the primary has correctly authenticated the mac
     * <p>
     * (1)  construct the next batch from the prepare
     * (2)  add the nextbatch to the order state
     * (3) construct the prepare
     * (4)  add it to the order state
     * (5)  send the prepare out
     * (6)  flush all the cached prepares and commits for seqno
     * if they match the newly generated prepare then
     * add them to the certificate
     * (7)  If the sequence number became prepared, then act on it
     **/
    synchronized protected void processClean(PrePrepare pp) {
        // these first two are going to get oomphed thanks to view
        // changes and asynchrony with verification
        if (pp.getView() != currentView())
            return; //Debug.kill("should not be a different view");
        if (pp.getSeqNo() != getCurrentSequenceNumber())
            return; //Debug.kill("no longer the right sequence number to process!");
        boolean cp = (getCurrentSequenceNumber() + 1) %
                BFT.order.Parameters.checkPointInterval == 0;
        long seqno = pp.getSeqNo();
        Certificate cert =
                certificates[stateIndex(seqno)][certificateIndex(seqno)];


        // (0) if adding the preprepare failed for some reason (duplicate?)
        // throw an exceptoin
        if ((pp.getSender() == myIndex
                && !cert.addMyPrePrepare(pp))
                || (pp.getSender() != myIndex
                && !cert.addOtherPrePrepare(pp))) {
            System.out.println(pp.getSender() + " " + myIndex);
            Debug.kill(new RuntimeException("add prepreare should not have failed"));

        }
        //	getWorkingState().setCurrentTime(pp.getNonDeterminism().getTime());


        updateHeartBeat();
        // successfully added the preprepare
        boolean wasPrepped = cert.isPrepared();

        // (1) Construct the next batch from the preprepare
        CommandBatch cb = cert.getCommandBatch();
        NonDeterminism non = cert.getNonDeterminism();
        HistoryDigest hist = cert.getHistory();
        NextBatch nb =
                cert.getNextBatchMessage(this, currentView);
        authenticateExecMacArrayMessage(nb);
        //	if(!validateOrderMacArrayMessage(nb)) {
        //		BFT.Debug.kill(new RuntimeException("Bad nb mac"));
        //	}

        runningCount += cb.getEntryCount();

        // (2)  add the next batch to the order state
        addNextBatch(nb);


        // only need to do prepare and commit on checkpointintervals
        if (cp || ((somebodyVotedForVC || forcingprepared) && amIPrimary())) {
            //Debug.println("\tConstructing a Prepare for: "+pp.getVersionNo());
            // (3) construct the prepare
            //	    //System.out.println("new prepare (c)");
            Prepare p = new Prepare(parameters, pp.getView(), pp.getSeqNo(),
                    cert.getHistory(), myIndex);
            authenticateOrderMacArrayMessage(p);
            // (4) add it to the order state
            cert.addMyPrepare(p);
            // (5)  send it out
            //System.err.println("send prepare to other order replicas");
            sendToOtherOrderReplicas(p.getBytes(), myIndex);
            Prepare pcache[] = cert.getPrepareCache();
            Commit ccache[] = cert.getCommitCache();
            if (cert.isPrepared())
                actOnPrepared(p.getSeqNo());
            // (6)  flush the cached prepares and commits
            for (int i = 0; i < pcache.length; i++) {
                if (p.matches(pcache[i])) {
                    process(pcache[i]);
                    //Debug.println("\t\tPulling out cached prepare "+i);
                }
                if (p.matches(ccache[i])) {
                    process(ccache[i]);
                    //Debug.println("\t\t\tPulling out cahced commit "+i);
                }
            }
        } else { // if its not a checkpoint interval rely on speculation
            //Debug.println(Debug.COMMIT, "Sending spec next batch"+nb.getVersionNo());
            //sendToAllExecutionReplicas(nb.getBytes());
            writeAndSend(nb);
            // (4) try to process a cached preprepare if it exists
            PrePrepare pp2 =
                    certificates[stateIndex(pp.getSeqNo() + 1)][certificateIndex(pp.getSeqNo() + 1)].getCachedPrePrepare();
            if (pp2 != null)
                process(pp2);
            Prepare pcache[] = cert.getPrepareCache();
            Commit ccache[] = cert.getCommitCache();
            // (6)  flush the cached prepares and commits
            for (int i = 0; i < pcache.length; i++) {
                if (pcache[i] != null &&
                        pcache[i].getPrePrepareHash().equals(cert.getHistory())) {
                    process(pcache[i]);
                    //Debug.println("pulling a cached prepare from "+ i);
                }
                if (ccache[i] != null &&
                        ccache[i].getPrePrepareHash().equals(cert.getHistory()))
                    process(ccache[i]);
            }

        }


        if (doViewChangeNow &&
                getCurrentSequenceNumber() % BFT.order.Parameters.checkPointInterval > 5)
            startViewChange(currentView + 1);

        else if (CPNOW > 0
                && getCurrentSequenceNumber() > 2 * BFT.order.Parameters.checkPointInterval
                && getCurrentSequenceNumber() % (CPNOW + myIndex) == 0
            //+(BFT.order.Parameters.checkPointInterval+1) * myIndex) == 0 ){
                ) {
            startViewChange(currentView + 1);
            return;
        }
    }

    /**
     * Process a prepare messages
     * <p>
     * (1)  Check that it is for the current view
     * (3)  discard it if the sequence number is old
     * (4)  if its for the mid range future then cache it
     * (5)  if it is already committed then discard
     * (6)  if it does not match the preprepare then discard
     * (7)  validat ethe authentication
     * (8) if validating local state prepares, then act on it
     **/
    synchronized protected void process(Prepare p) {
        //System.err.println("start process(prepare");
        //	Debug.println("\t\tProcessing Prepare "+p.getVersionNo()+" from: "+p.getSendingReplica());
        // (1) check for the current view
        if (p.getView() != currentView || changingView) {
            return;//	     Debug.kill("invalid view");
        }
        long seqno = p.getSeqNo();
        // discard old seqno
        // (3) if its old, discard it
        if (seqno < getBaseSequenceNumber()) {
            return;
        }
        // (4) if its not next, then cache it
        if (seqno >= getBaseSequenceNumber() +
                BFT.order.Parameters.maxPeriods * BFT.order.Parameters.checkPointInterval) {
            // catch near future seqnos and cache them
            if (seqno < getBaseSequenceNumber() +
                    (BFT.order.Parameters.maxPeriods + 1) *
                            BFT.order.Parameters.checkPointInterval) {
                int ind =
                        (int) (seqno % BFT.order.Parameters.checkPointInterval);
                //Debug.println("\t\tcaching a future p@"+seqno);
                futurePs[ind][(int) (p.getSendingReplica())] = p;
                return;

            } else {
                // (4a) if its distant future discard it
                System.out.println("prepare from the future.  so do a view change");
                startViewChange(currentView + 1);
                return;
            }


        }
        // (4)  caching not yet ready
        Certificate cert =
                certificates[stateIndex(p.getSeqNo())][certificateIndex(p.getSeqNo())];
        if (!cert.isPrePrepared()) {
            cert.cachePrepare(p);
            //Debug.println("\t\tcaching a prepare "+p.getSendingReplica());
            return;
        }
        if (cert.preparedBy((int) p.getSendingReplica())
                && p.getSendingReplica() != primary(currentView)) {
            return;
        }

        //(5)  if its already committed then disard it
        if (cert.isCommitted() &&
                (!cert.preparedBy((int) (p.getSendingReplica()))
                        && (p.getSendingReplica() == primary(currentView)
                        || amIPrimary()))) {
            //Debug.println("\t\tdropping a prepare b/c its already committed");
            // its already committed so drop it
            return;
        }
        // (6)  check that it matches the corresponding preprepare
        if (!p.getPrePrepareHash().equals(cert.getHistory())) {
            System.out.println(p);
            System.out.println(p.getPrePrepareHash());
            System.out.println(cert.getHistory());

            Debug.kill(new RuntimeException("histories dont match"));
        }
        // (7) validate authentiation
        if (!validateOrderMacArrayMessage(p, myIndex))
            Debug.kill("invalid macarray");//return;
        // (7a) if it came from the primary, send a prepare
        boolean wasPrepped = cert.isPrepared();
        boolean added = false;
        if (p.getSendingReplica() == primary(currentView)
                && p.getSendingReplica() != myIndex) {
            if (cert.getPrepare() == null) {
                //		//System.out.println("prepare (a)");
                Prepare p2 = new Prepare(parameters, p.getView(), p.getSeqNo(),
                        p.getPrePrepareHash(), myIndex);
                authenticateOrderMacArrayMessage(p2);
                sendToOtherOrderReplicas(p2.getBytes(), myIndex);
                added = cert.addMyPrepare(p2);
            }
        }

        if (p.getSendingReplica() == myIndex)
            added = cert.addMyPrepare(p);
        else
            added = added || cert.addOtherPrepare(p);
        // (8) add it to local state.  If that prepares then act on it
        if (/*added && */!wasPrepped && cert.isPrepared())
            actOnPrepared(p.getSeqNo());
        else if (!cert.isPrepared()) {
            //Debug.println("\t\tNot yet prepared");
        } else if (wasPrepped) {
            //Debug.println("\t\talready prepared");
        }
        //System.err.println("end of process(prepare)");
    }

    /**
     * Processes a prepared sequence number
     * <p>
     * (1)  assert if not prepared
     * (2) create a new commit message
     * (3)  add it to the state
     * (4) send it out
     * (5) if that commits the request, act on the commitment
     **/
    synchronized protected void actOnPrepared(long seqNo) {
        //	Debug.println("\t\tact on prepared: "+seqNo);
        Certificate cert =
                certificates[stateIndex(seqNo)][certificateIndex(seqNo)];
        // (1)  assert that the sequence number is in fact prepared
        if (!cert.isPrepared())
            Debug.kill("its not prepared!");
        if (seqNo > maxPrepared)
            maxPrepared = seqNo;

        // (2)  create the new commit
        Commit c = new Commit(parameters, currentView(), seqNo, cert.getHistory(), myIndex);
        authenticateOrderMacArrayMessage(c);
        // (3) add it to the state
        if (!cert.addMyCommit(c)) {
            Debug.kill("should not have failed");
        }
        // (4) send it out
        sendToOtherOrderReplicas(c.getBytes(), myIndex);
        // (5) check to see if committed
        if (cert.isCommitted())
            actOnCommitted(seqNo);

        if (certificateIndex(seqNo) != 0)
            for (int i = certificateIndex(seqNo - 1);
                 i >= 0 && !certificates[stateIndex(seqNo)][i].isPrepared();
                 i--) {
                certificates[stateIndex(seqNo)][i].forcePrepared();
            }
    }

    /**
     * Process a commit
     * <p>
     * (1)  check that it is for the current view
     * (2)  discard old sequence numbers
     * (3)  cache mid range future requests
     * (4)  discard if it is alrady committed
     * (5)  discard if it does not match the preprepare
     * (6)  validate authentication
     * (7)  if it makes the certificate complete, then act on it
     **/
    synchronized protected void process(Commit c) {
        //System.err.println("start process(commit)");
        // 	System.out.println("\t\t\tProcessing Commit "+c.getVersionNo()+
        // 		      " in view "+c.getView()+" from: "+c.getSendingReplica());

        //	if (changingView)
        //	    System.out.println("trying to do a viewchange! into "+currentView);
        //	System.out.println(c);
        // (1)  check the current view
        if (c.getView() != currentView)
            return;//Debug.kill("invalid view");

        long seqno = c.getSeqNo();

        //System.err.println("1");
        if (changingView) {
            if (seqno % BFT.order.Parameters.checkPointInterval == 0)
                vcCommits[(int) c.getSendingReplica()] = c;
            return;
        }

        //System.err.println("2");
        // (2) discard old requests
        if (seqno < getBaseSequenceNumber()) {
            //Debug.println(Debug.COMMIT, "\t\t\tThrowing out an old"+
            //		" commit, base: "+getBaseSequenceNumber());
            return;
        }
        // (3) cache mid range future requests, or requests if we're changing views
        //System.err.println("3");
        if (seqno >= getBaseSequenceNumber() +
                BFT.order.Parameters.maxPeriods * BFT.order.Parameters.checkPointInterval) {
            if (seqno < getBaseSequenceNumber() +
                    (BFT.order.Parameters.maxPeriods + 1) *
                            BFT.order.Parameters.checkPointInterval) {
                int ind =
                        (int) (seqno % BFT.order.Parameters.checkPointInterval);
                //Debug.println(Debug.COMMIT,"\t\t\tcaching a future c@"+seqno);
                futureCs[ind][(int) (c.getSendingReplica())] = c;
                int count = 0;
                for (int i = 0; i < parameters.getOrderCount(); i++)
                    if (futureCs[ind][i] != null)
                        count++;
                if (count >= parameters.smallOrderQuorumSize()) {
                    System.out.println("commit from the future, so view change");
                    startViewChange(currentView + 1);
                }
                return;
            } else {  // discard distant future
                System.out.println("I'm way behind on commits.  give up and go for a view change");
                startViewChange(currentView + 1);
                return;
            }
            //		Debug.kill("Im way behind");
        }

        // (3) cache mid range future
        //System.err.println("4");
        Certificate cert =
                certificates[stateIndex(c.getSeqNo())][certificateIndex(c.getSeqNo())];
        if (!cert.isPrePrepared()) {
            cert.cacheCommit(c);
            //Debug.println(Debug.COMMIT, "\t\t\tcaching commit: "+
            //	c.getSendingReplica());
            return;
        }
        // (4)  discard already committed things
        //System.err.println("5");
        if (cert.isCommitted()) {
            //Debug.println(Debug.COMMIT, "\t\t\tcert is committed"+
            //", aborting handling");
            // its already committed so drop it
            return;
        }
        // (5) check that it matches teh preprepare
        //System.err.println("6");
        if (!c.getPrePrepareHash().equals(cert.getHistory())) {
            System.out.println(c);
            System.out.println(cert.getCertEntry());
            System.out.println(getCurrentSequenceNumber());

            Debug.kill("histories dont match");

        }
        // (6) authenticate the request
        //System.err.println("7");
        if (!validateOrderMacArrayMessage(c, myIndex))
            Debug.kill("invliad macarray");//return;
        // (7) if it completest he certificate, then act on it
        boolean wasPrepared = cert.isPrepared();
        boolean added = cert.addOtherCommit(c);
        if (added && cert.isCommitted())
            actOnCommitted(c.getSeqNo());
        else if (added && !wasPrepared && cert.isPrepared())
            actOnPrepared(c.getSeqNo());
        //System.err.println("end of process(commit)");
    }


    /**
     * Act on committed requests.
     * <p>
     * (1)  fetch the nextbatch message for this sequence number
     * (2)  send it out
     * (3)  if this is the last seqno before the next checkpointinterval
     * (3b1)  create a new working state
     * (3b2)  create a next batch if possible
     * (3bc)  make the checkpoint stable if it has the app cp token
     * (4)  process cached preprepare if it exists
     * (5)  if a checkpoint is complete, mark previous entries as committed
     **/
    synchronized protected void actOnCommitted(long seqNo) {
        //	Debug.println("\t\t\tacton commmitted: "+seqNo);
        Certificate cert =
                certificates[stateIndex(seqNo)][certificateIndex(seqNo)];
        // assert that the request is committed
        if (!cert.isCommitted())
            Debug.kill("uncommitted certificate!");

        if (seqNo > maxCommitted)
            maxCommitted = seqNo;

        // (1) fetch the next batch message for this sequence number
        NextBatch nb = cert.getNextBatchMessage(this, currentView);
        authenticateExecMacArrayMessage(nb);
        //Debug.println("sending next batch: "+nb.getVersionNo());
        // (2)  send it out to everybody
        //sendToAllExecutionReplicas(nb.getBytes());
        writeAndSend(nb);
        // (3) if this is a checkpoint Intervalth request,
        if ((seqNo + 1) %
                BFT.order.Parameters.checkPointInterval == 0) {
            // i've committed the checkpoint intervalth request.
            // pre-req to this step is having made the checkpoint
            // stable, otherwise I have not yet sent the prepare
            // (i.e. not yet accepted teh preprepare)
            //	    System.out.println(stateSnapshots[currentIndex]);
            stateSnapshots[currentIndex].commit();
            CheckPointState cps = workingState;
            // (3b1) create the new state
            newWorkingState();
            // (3b2) try to create the next batch
            //	     createNextBatch();  // Deprecated out!
            // (3bc) make the checkpoint stable if it has the app
            //       token
            if (cps.hasExecCP()) {
                makeCPStable(cps);
                forcingprepared = false;
            }
            // NOW DO THROUGHPUT CHECKING
            long time = System.currentTimeMillis();
            long time2 = time;
            time = time - oldTime;
            double rate = (double) runningCount / (double) time;
            oldTime = System.currentTimeMillis();
            viewTotal += runningCount;
            double size = (double) runningCount / (double) BFT.order.Parameters.checkPointInterval;
            runningCount = 0;
            double realRate = (double) viewTotal / (double) (time2 - viewStartTime);
            double batchRate = BFT.order.Parameters.checkPointInterval / (double) time;
            activeCount = 0;
            for (int i = 0; i < active.length; i++)
                active[i] = false;

            //   	    System.out.println("We had a instant rate of "+rate+
            // 			       "\n     with a real rate of "+ realRate+
            // 			       "\n       and a  minimum of "+ threshold(cpCount)+
            // 			       "\n              batch rate "+batchRate+
            // 			       "\n     batch size of : "+size);

            if (cpCount > 0 && rate < threshold(cpCount) && realRate < threshold(cpCount)) {
                // 		long viewTime = System.currentTimeMillis() - viewStartTime;
                //  		System.out.println(viewTime+" "+viewTotal);
                // 		double v_rate = (double)viewTotal/(double)viewTime;
                //  		System.out.println("view rate is: "+v_rate);
                //		updateThreshold(v_rate);
                scheduleViewChange = true;
            }
            cpCount++;
        }


        // (3c)  commit all requests in the current set of commit certificates
        if (certificateIndex(seqNo) != 0)
            for (int i = certificateIndex(seqNo - 1);
                 i >= 0 && !certificates[stateIndex(seqNo)][i].isCommitted();
                 i--) {
                certificates[stateIndex(seqNo)][i].forceCommitted();
            }

        // (4) try to process a cached preprepare if it exists
        PrePrepare pp =
                certificates[stateIndex(seqNo + 1)][certificateIndex(seqNo + 1)].getCachedPrePrepare();
        if (pp != null)
            process(pp);
    }


    /**
     * Accept checkpoint token messages
     * <p>
     * (1)  discard the token if it is old
     * (2) authenticate the verification
     * (3)  Add the token to the current certificate.
     * (4) if it is complete, act on it
     **/
    synchronized protected void process(CPTokenMessage cptok) {
        Debug.println("process cp token message " +
                cptok.getSequenceNumber() + " from " +
                cptok.getSendingReplica());
        //		System.out.println(cptok);
        // (1) discard the token if it is old
        if (cptok.getSequenceNumber() <= getBaseSequenceNumber() &&
                (cptok.getSequenceNumber() != 0 ||
                        getBaseSequenceNumber() != 0)) { // start up hack
            System.out.println("Discarding outdated CP Token from " + cptok.getSender() + " @ " + getBaseSequenceNumber() + "/" + cptok.getSequenceNumber());
            return;
        }
        // (2) authenticate verification
        if (!validateExecMacArrayMessage(cptok, myIndex))
            Debug.kill("invalid macarray");//return;
        // (3) add it to the certificate


        CPTokenMessage tmp = cpCert.getEntry();
        if (tmp != null && !tmp.matches(cptok)
                && cptok.getSequenceNumber() == tmp.getSequenceNumber()) {
            System.out.println("old is " + tmp);
            System.out.println("new is " + cptok);
            BFT.Debug.kill("UH OH");
            // if the cp is inconsistent, then lets ask everybody
            // for it again.  we really ought to cache cps rather
            // than do this, but for now this will do
            RequestCP rcp = new RequestCP(parameters, tmp.getSequenceNumber(), myIndex);
            authenticateExecMacArrayMessage(rcp);
            sendToAllExecutionReplicas(rcp.getBytes());

        }


        cpCert.addEntry(cptok);

        //Debug.println("Complete cp cert: "+cpCert.isComplete());
        // (4) act on it if it is complete
        if (cpCert.isComplete())
            actonCPToken();
    }


    /**
     * Act on a completed cpcertificate.
     * <p>
     * if we've got access to the checkpointstate, then a
     **/
    synchronized protected void actonCPToken() {
        if (!cpCert.isComplete())
            Debug.kill(new RuntimeException("cpcert should be complete!"));
        CPTokenMessage cptok = cpCert.getEntry();
        System.out.println("acting on cptoken for: " + cptok.getSequenceNumber());
        //BFT.util.UnsignedTypes.printBytes(cptok.getToken());
        //Debug.println("acting on cptok: "+cptok.getSequenceNumber());

        if (cptok.getSequenceNumber() <= getBaseSequenceNumber() &&
                // nasty start up hack
                cptok.getSequenceNumber() != 0)
            Debug.kill("cptoken from the distant past " +
                    cptok.getSequenceNumber() + " : " +
                    getBaseSequenceNumber());
        //	//System.out.println("** changing view?"+changingView);
        if (!changingView) {
            System.out.println("Not changing view");
            long seqno = cptok.getSequenceNumber();
            // Get the checkpoint state we are adding to.
            int state = stateIndex(seqno);
            CheckPointState cps;
            if (workingState.getBaseSequenceNumber() < seqno) {
                cps = workingState;
                System.out.println("###looking at working state");
            } else
                cps = stateSnapshots[state];

            if (seqno > cps.getBaseSequenceNumber() +
                    BFT.order.Parameters.maxPeriods *
                            BFT.order.Parameters.checkPointInterval) {
                //startViewChange(currentView+1);
                System.out.println("checkpoint for: " + seqno);
                System.out.println("im at: " + cps.getBaseSequenceNumber());
                startViewChange(currentView + 1);
                //Debug.kill("way off in the future: "+seqno+" : "+
                //	   cps.getBaseSequenceNumber());
                return;
            }
            if (cps.getBaseSequenceNumber() +
                    BFT.order.Parameters.checkPointInterval != seqno &&
                    seqno != 0)
                Debug.kill(new RuntimeException(cps.getBaseSequenceNumber() +
                        " is Not the right state to" +
                        " add " + seqno + " to"));

            if (cps.hasExecCP()) {
                //Debug.kill("####already has cptok");
                return;
            }

            cps.addExecCPToken(cptok.getToken(), cptok.getSequenceNumber());

            // If the last sequence number before the checkpoint is
            // committed, then mark the checkpoint stable
            Certificate cert = null;
            if (seqno > 0)
                cert = certificates[stateIndex(seqno - 1)][certificateIndex(seqno - 1)];
            if (seqno == 0 || cert.isCommitted()) {
                //System.out.println("\t\tmaking stable in normal operation");
                makeCPStable(cps);
            } else {
                //System.out.println("\t\tnot making stable: "+seqno+" " +cert.isCommitted());
            }
        } else { /// changing view!
            System.out.println("\tChanging view");
            if (!changingView) Debug.kill("should be changing view now");
            CheckPointState cps = vInfo.getStableCP();
            //System.out.println("playing with the stable CP "+cps);
            // if it fits into the new stable cp, then add it there
            if (cps != null
                    && cps.getCurrentSequenceNumber() == cptok.getSequenceNumber()
                    && !cps.isStable() && !cps.isMarkingStable()) {
                cps.addExecCPToken(cptok.getToken(), cptok.getSequenceNumber());
                //		System.out.println("\t\tmaking stable in view change as stablecp");
                makeCPStable(cps);
            } else {
                // if it fits into the committed cp then put it there
                cps = vInfo.getCommittedCP();
                if (cps != null &&
                        cps.getCurrentSequenceNumber() == cptok.getSequenceNumber()
                        && !cps.isStable() && !cps.isMarkingStable()) {
                    cps.addExecCPToken(cptok.getToken(), cptok.getSequenceNumber());
                    //		    System.out.println("\t\tmaking stable in chaving view 2 as committedcp");
                    makeCPStable(cps);
                } else if (cps == null) { //if its neither the commited
                    //nor stable, check and see
                    //if it fits into our
                    //'active' memory region
                    long seqno = cptok.getSequenceNumber();
                    // Get the checkpoint state we are adding to.
                    int state = stateIndex(seqno);

                    if (workingState.getBaseSequenceNumber() < seqno) {
                        cps = workingState;
                        //System.out.println("###looking at working state");
                    } else
                        cps = stateSnapshots[state];

                    if (seqno >= cps.getBaseSequenceNumber() +
                            BFT.order.Parameters.maxPeriods *
                                    BFT.order.Parameters.checkPointInterval) {
                        if (!changingView)
                            BFT.Debug.kill("WTF> already in a view change!");
                        return;
                    }

                    if (cps.getBaseSequenceNumber() +
                            BFT.order.Parameters.checkPointInterval != seqno &&
                            seqno != 0)
                        Debug.kill(new RuntimeException(cps.getBaseSequenceNumber() +
                                " is Not the right state to" +
                                " add " + seqno + " to"));

                    if (cps.hasExecCP()) {
                        //Debug.kill("####already has cptok");
                        return;
                    }

                    cps.addExecCPToken(cptok.getToken(),
                            cptok.getSequenceNumber());

                    // If the last sequence number before the checkpoint is
                    // committed, then mark the checkpoint stable
                    Certificate cert = null;
                    if (seqno > 0)
                        cert = certificates[stateIndex(seqno - 1)][certificateIndex(seqno - 1)];
                    if (seqno == 0 || cert.isCommitted()) {
                        System.out.println("\t\tmaking stable in vc as working");
                        makeCPStable(cps);
                    } else {
                        System.out.println("\t\tnot making stable: " + seqno + " " + cert.isCommitted() + "  in view change");
                    }

                } // if (cps == null)
            } // else if its vinfo.committed
        } // if vinfo.stable
        cpCert.clear();
    }


    /***********************************************
     ** State Management
     ***********************************************/

    synchronized protected void makeCPStable(CheckPointState cps) {
        //	//System.out.println("### making cp stable: "+cps.getCurrentSequenceNumber());
        if (cps.isStable() || cps.isMarkingStable())
            return;
        //cps.makeStable();
        //cpStable(cps);`
        if (parameters.doLogging) {
            cps.markingStable();
            CPLogger cpl = new CPLogger(cpwq, cps, myIndex, this);
            pool.execute(cpl);
        } else {
            cps.markingStable();
            cps.makeStable();
            cpStable(cps);
        }
    }

    synchronized public void cpStable(CheckPointState cp) {

        //	System.out.println("====STABLE CP\n"+cp);

        if (!cp.isStable())
            Debug.kill(new RuntimeException("cp ought ot be stable now"));
        long seqno = cp.getCurrentSequenceNumber();
        //	if (seqno % (BFT.order.Parameters.checkPointInterval *20) == 0){
        //  System.out.println("making "+seqno+" into stable CP.  current is: "+getCurrentSequenceNumber());
        // 	    System.out.println(cp);
        //	}
        int state = stateIndex(seqno);
        int cert = BFT.order.Parameters.checkPointInterval - 1;


        if (seqno == 0)
            cp.commit();

        if (!changingView) {
            Certificate certificate = certificates[state][cert];
            PrePrepare pp = certificate.getCachedPrePrepare();
            if (pp != null)
                process(pp);
        } else {
            if (vInfo.definedView())
                actOnDefinedView();
            else
                ; // do nothing if the view isnt defined yet
        }
        if (scheduleViewChange)
            doViewChangeNow = true;


    }


    /**
     * Adds a next batch to the current state.
     * (1) update the working state with the new next batch information.
     * fails if it is not the next sequence number to be entered
     * (2)  update the time to reflect the time associated with the batch
     **/
    synchronized protected void addNextBatch(NextBatch nb) {
        //Debug.println("adding next batch: "+nb.getVersionNo());
        // update the working state

		/*TODO: iterate over comands and set the client
         * retransmission time
		 */

        long time = System.currentTimeMillis();
        Entry[] ents = nb.getCommands().getEntries();
        for (int i = 0; i < ents.length; i++) {
            lastClientRetrans[(int) (ents[i].getClient())] = time;
        }
        workingState.addNextBatch(nb.getCommands(), nb.getSeqNo(), nb.getHistory(), nb.getNonDeterminism().getTime());


        //	    workingState.setCurrentTime(nb.getNonDeterminism().getTime());
        // update the certificate
        // 	int stateindex = stateIndex(nb.getVersionNo());
        // 	int certindex = certificateIndex(nb.getVersionNo());
        // 	certificates[stateindex][certindex].addNextBatch(nb);

        // Deprecated out: adding the certificateentry takes care of
        // this during view changes
        // during normal operation, placing the pp in is sufficient
    }


    /**
     * move on to a new working state
     **/
    synchronized protected void newWorkingState() {
        // 	Debug.println("\t\t\t\tNew Working State @ "+getCurrentSequenceNumber());
        // 	Debug.println("\t\t\t\tBaseindex: "+baseIndex+" curIndex: "+currentIndex);
        if ((getCurrentSequenceNumber()) %
                BFT.order.Parameters.checkPointInterval != 0)
            Debug.kill(new RuntimeException("trying to get a new working " +
                    "state prematurely " +
                    getCurrentSequenceNumber()));

        int baseplusone = (baseIndex + 1) % BFT.order.Parameters.maxPeriods;
        if (stateSnapshots[baseplusone] != null)
            //Debug.println("\t\t\t\t"+baseplusone+" is committed: "+stateSnapshots[baseplusone].isCommitted());
            if (currentIndex != baseIndex &&
                    !stateSnapshots[baseplusone].isCommitted()) {

                //Debug.println("currentIndex: "+currentIndex);
                //Debug.println("baseIndex   : "+baseIndex);
                //Debug.println(stateSnapshots[baseplusone].getBaseSequenceNumber());
                //Debug.println(stateSnapshots[baseplusone].getCurrentSequenceNumber());
                //Debug.println(stateSnapshots[baseplusone].isCommitted());
                Debug.kill(new RuntimeException("wtf?"));
                // return;

            }
        // garbage collect the first index
        if (currentIndex != baseIndex)
            garbageCollect(stateSnapshots[baseplusone].getCurrentSequenceNumber());

        if ((currentIndex + 1) % BFT.order.Parameters.maxPeriods ==
                baseIndex)
            Debug.kill("need to garbage collect first");

        currentIndex =
                (currentIndex + 1) % BFT.order.Parameters.maxPeriods;
        stateSnapshots[currentIndex] = workingState;
        workingState = new CheckPointState(workingState);
        newLog(workingState.getCurrentSequenceNumber() + "_" + myIndex + "_");
        System.out.println("\tCP currentIndex:" + currentIndex + " baseIndex:" + baseIndex);
        for (int i = 0; i < futurePPs.length; i++) {
            if (futurePPs[i] != null)
                process(futurePPs[i]);
            futurePPs[i] = null;
            for (int j = 0; j < futurePs[i].length; j++) {
                if (futurePs[i][j] != null)
                    process(futurePs[i][j]);
                if (futureCs[i][j] != null)
                    process(futureCs[i][j]);
                futurePs[i][j] = null;
                futureCs[i][j] = null;
            }
        }

        //Debug.println("\t\t\t\tBase: "+getBaseSequenceNumber()+" cur: "+
        //		getCurrentSequenceNumber());
    }

    /**
     * garbage collect checkpoints less than seqno
     **/
    synchronized protected void garbageCollect(long seqno) {
        if (seqno >= getCurrentSequenceNumber())
            Debug.kill(new RuntimeException("trying to garbage collect the future"));
        if (seqno % BFT.order.Parameters.checkPointInterval != 0) {
            garbageCollect(seqno -
                    (seqno % BFT.order.Parameters.checkPointInterval));
            return;
        }
        while (getBaseSequenceNumber() < seqno &&
                baseIndex != currentIndex) {
            int baseplusone = (baseIndex + 1) % BFT.order.Parameters.maxPeriods;
            if (!stateSnapshots[baseplusone].isCommitted()) {
                Debug.kill(new RuntimeException("Garbage collecting when the next snapshot is not stable"));
            }
            if (!stateSnapshots[baseIndex].isCommitted())
                Debug.kill(new RuntimeException("garbage collecting something thats not stable"));
            for (int i = 0; i < certificates[baseIndex].length; i++) {
                certificates[baseIndex][i]
                        = new Certificate(parameters, parameters.getOrderCount(),
                        (i == BFT.order.Parameters.checkPointInterval - 1) ? true : false);

            }
            CheckPointState cps = stateSnapshots[baseIndex];

            ReleaseCP rcp = new ReleaseCP(parameters, cps.getExecCPToken(), cps.getCurrentSequenceNumber(), myIndex);
            authenticateExecMacArrayMessage(rcp);

            sendToAllExecutionReplicas(rcp.getBytes());

            gcLog(cps.getCurrentSequenceNumber() + "_" + myIndex + "_");
            File cpfile = new File(cps.getCurrentSequenceNumber() + "_ORDER_CP.LOG");
            if (!cpfile.exists()) {
                cpfile = new File(cps.getCurrentSequenceNumber() + "_" + myIndex + "_ORDER_CP.LOG");
            }
            if (cpfile.exists()) {
                cpfile.delete();
                //BFT.//Debug.println("DELETED CP LOG: " + cpfile.getName());
            }
            stateSnapshots[baseIndex] = null;
            baseIndex = (baseIndex + 1) % BFT.order.Parameters.maxPeriods;
        }
    }

    /**
     * Maintains the required throughputs
     **/
    protected double threshold(int cpCount) {
        double base = thresh / 2 / BFT.order.Parameters.baseDuration;
        //	System.out.println("base: "+base+" thresh: "+thresh);
        base *= (cpCount - 10);
        return thresh / 2 + base;
    }

    protected void updateThreshold(double rate) {
        observed[primary(currentView)] = rate;
        //System.out.println(currentView+" "+primary(currentView)+ " observed rate: "+rate);
        thresh = 0;
        for (int i = 0; i < observed.length; i++)
            if (observed[i] > thresh)
                thresh = observed[i];
        //	thresh = observed[myIndex];
    }

    protected long heartBeat = System.currentTimeMillis();

    //    long max = 0;
    protected void updateHeartBeat() {
        long tmp = System.currentTimeMillis();
        //	if (max > 1000)
        //	    max = -1;
        //	if (tmp-heartBeat > max)
        //	    max = tmp-heartBeat;
        heartBeat = tmp;

    }

    protected void activeClient(int id) {
        if (active[id])
            return;
        activeCount++;
        active[id] = true;
    }

    public int minimumBatchSize() {
        return PREFERED_BATCH_SIZE;
//        boolean res = !amIPrimary() ||
//                System.currentTimeMillis() - heartBeat > BFT.order.Parameters.heartBeat / 2;
//        if (res) {
//            // 	    if (amIPrimary())
//            // 		System.out.println("heartbeat triggered batch!");
//            activeCount = 0;
//            for (int i = 0; i < active.length; i++)
//                active[i] = false;
//            // unpile the pp and comm
//            return 1;
//        } else {
//            int bias = 4 + parameters.getFilterCount();
//            return (activeCount + bias) / bias;
//        }
    }

    int hbcheckCount = 0;
    int _vcCount = 1;

    public synchronized void checkHeartBeat() {
        if (DISABLE_VC)
            return;
        hbcheckCount++;

        if (//!changingView &&
                !amIPrimary() &&
                        getBaseSequenceNumber() > 3 * BFT.order.Parameters.checkPointInterval &&
                        System.currentTimeMillis() - heartBeat > BFT.order.Parameters.heartBeat * _vcCount) {

            System.out.println("view changing off of the heartbeat");
            System.out.println(BFT.order.Parameters.heartBeat);
            System.out.println(_vcCount);
            System.out.println(heartBeat);
            System.out.println(System.currentTimeMillis());
            System.out.println(currentView);
            startViewChange(currentView + 1);
        }
    }

    /**
     * Returns true if the primary should prepare.  Currently this is
     * true if somebody is already voting for a new primary
     **/
    protected boolean primaryShouldPrepare() {
        for (int i = 0; i < viewChanges.length; i++) {
            if (viewChanges[i].getSmallestView() > currentView)
                return true;
        }
        return false;
    }

    /**********************************
     **  View Changes
     *********************************/


    /**
     * Start a new view change.
     * <p>
     * (1) change the current view
     * (2) set the inviewchange flag
     * (3) create a view change message and send it to the world
     **/
    synchronized protected void startViewChange(long newView) {
//        try{
//            throw new Exception();
//        }
//        catch(Exception e) {
//            e.printStackTrace();
//        }
        if (DISABLE_VC)
            return;
        //	System.out.println("starting view change: "+newView);

        updateHeartBeat();
        // 	Debug.println(Debug.VIEWCHANGE,
//        System.err.println("\t\t\t\t\t\tStarting view change for " + newView +
//                " at " + getCurrentSequenceNumber());
        _vcCount++;
        if (_vcCount > 10)
            _vcCount = 10;
        // record the actual view rate and time
        long viewTime = System.currentTimeMillis() - viewStartTime;
        System.out.println(viewTime + " " + viewTotal);
        double v_rate = (double) viewTotal / (double) viewTime;
        System.out.println(myIndex + " view rate " + currentView +
                " is: " + v_rate + " of " + threshold(cpCount)
                + " for " + viewTotal);
        updateThreshold(v_rate);

        if (currentView >= newView)
            Debug.kill("view numbers should be" +
                    " monotonically increasing! " +
                    currentView + ":" + newView);
        scheduleViewChange = false;
        doViewChangeNow = false;
        requestedThings = false;
        currentView = newView;
        actedOnView = false;
        actedOnDefinedView = false;
        ViewChangeCertificate vcCerts[] =
                new ViewChangeCertificate[viewChanges.length];


        // Check the cache of view change messages to see if we want
        // to be in a larger view
        boolean lookForLargerView = true;
        while (lookForLargerView) {
            // get the current view change
            for (int i = 0; i < viewChanges.length; i++) {
                vcCerts[i] = viewChanges[i].getCertificate(currentView);
            }
            int count = 0;
            long small = currentView;
            // look to see if sufficient future view changes exist
            for (int i = 0; i < viewChanges.length; i++) {
                if (viewChanges[i].getSmallestView() > currentView)
                    count++;
                if (viewChanges[i].getSmallestView() < small ||
                        small == currentView)
                    small = viewChanges[i].getSmallestView();
            }
            //System.out.println("\tcur view: "+currentView+" small: "+small);
            // if there is a future valid view, then switch again
            if (count >= parameters.smallOrderQuorumSize()
                    // if the current primary has a larger entry
                    || viewChanges[primary(small)].getLastReceived() > small) {
                currentView = small;
            } else
                // otherwise stop looking
                lookForLargerView = false;
        }


        // establish the necessary information for the view change
        // message
        long cpSeqno = getBaseSequenceNumber(); //highest committed cp sequence number
        long stableCPSeqno = cpSeqno;
        long prepareSeqno = getBaseSequenceNumber(); // last prepared +1
        long preprepareSeqno = getBaseSequenceNumber(); // last preprepared+1
        System.out.println("#####cp: " + cpSeqno + " scp: " + stableCPSeqno +
                " p: " + prepareSeqno + " pp: " + preprepareSeqno);
        Digest cphash = null; // hash of the committed CP
        Digest stableCPhash = null; // hash of stable cp
        Digest[] batchHashes = null; // hash of batches
        HistoryDigest[] histories = null; // histories

        // compute cpseqno
        cpSeqno = getBaseSequenceNumber();
        System.out.println("base cp: " + cpSeqno);
        int cpIndex = baseIndex;
        System.out.println(stateSnapshots[baseIndex]);
        int tmp = (cpIndex + 1) % stateSnapshots.length;
        System.out.println(stateSnapshots[tmp]);
        while (tmp != baseIndex && stateSnapshots[tmp].isCommitted()) {

            cpIndex = tmp;
            tmp = (cpIndex + 1) % stateSnapshots.length;
            System.out.println(stateSnapshots[tmp]);
        }
        cpSeqno = stateSnapshots[cpIndex].getCurrentSequenceNumber();

        System.out.println("@####cp: " + cpSeqno + " scp: " + stableCPSeqno +
                " p: " + prepareSeqno + " pp: " + preprepareSeqno);


        // compute stableCPSeqno
        stableCPSeqno = cpSeqno;
        while (tmp != baseIndex && stateSnapshots[tmp].isStable()) {
            cpIndex = tmp;
            tmp = (cpIndex + 1) % stateSnapshots.length;
        }
        stableCPSeqno = stateSnapshots[cpIndex].getCurrentSequenceNumber();

        System.out.println("$####cp: " + cpSeqno + " scp: " + stableCPSeqno +
                " p: " + prepareSeqno + " pp: " + preprepareSeqno);

        // compute pseqno
        prepareSeqno = cpSeqno;
        int stateIndex = stateIndex(prepareSeqno);
        int certIndex = certificateIndex(prepareSeqno);
        while (prepareSeqno < getCurrentSequenceNumber() &&
                certificates[stateIndex][certIndex].isPrepared()) {
            prepareSeqno = prepareSeqno + 1;
            stateIndex = stateIndex(prepareSeqno);
            certIndex = certificateIndex(prepareSeqno);
        }

        System.out.println("%####cp: " + cpSeqno + " scp: " + stableCPSeqno +
                " p: " + prepareSeqno + " pp: " + preprepareSeqno);

        // get pp seqno
        preprepareSeqno = prepareSeqno;
        stateIndex = stateIndex(preprepareSeqno);
        certIndex = certificateIndex(preprepareSeqno);

        while (preprepareSeqno < getCurrentSequenceNumber() &&
                certificates[stateIndex][certIndex].isPrePrepared()) {
            if (certificates[stateIndex][certIndex].isPrepared())
                Debug.kill("should not be prepared" +
                        "out of order!" + stateIndex + " " + certIndex +
                        " " + certificates[stateIndex][certIndex].getSeqNo());
            preprepareSeqno = preprepareSeqno + 1;
            stateIndex = stateIndex(preprepareSeqno);
            certIndex = certificateIndex(preprepareSeqno);
        }

        System.out.println("!####cp: " + cpSeqno + " scp: " + stableCPSeqno +
                " p: " + prepareSeqno + " pp: " + preprepareSeqno);

        if (preprepareSeqno != getCurrentSequenceNumber()) {
            //System.out.println(getCurrentSequenceNumber());
            //System.out.println(preprepareSeqno);
            Debug.kill("wtf");
        }

        //System.out.println("cp: "+cpSeqno+" scp: "+stableCPSeqno+
        //		" p: "+prepareSeqno+" pp: "+preprepareSeqno);

        cphash = stateSnapshots[stateIndex(cpSeqno)].getStableDigest();
        //System.out.println("commit Hash: "+cpSeqno+" :  "+cphash);
        stableCPhash =
                stateSnapshots[stateIndex(stableCPSeqno)].getStableDigest();
        //System.out.println("stable hash: "+stableCPSeqno+" :  "+stableCPhash);
        batchHashes = new Digest[(int) (preprepareSeqno - cpSeqno)];
        histories = new HistoryDigest[(int) (preprepareSeqno - cpSeqno)];


        for (int i = 0; i < histories.length; i++) {
            long seqno = cpSeqno + i;
            Certificate cert =
                    certificates[stateIndex(seqno)][certificateIndex(seqno)];
            batchHashes[i] = cert.getEntryDigest();
            histories[i] = cert.getHistory();
        }

        System.out.println("&####cp: " + cpSeqno + " scp: " + stableCPSeqno +
                " p: " + prepareSeqno + " pp: " + preprepareSeqno);

        ViewChange vc = new ViewChange(parameters,
                currentView,
                lastPrepreparedView,
                cpSeqno,
                stableCPSeqno,
                prepareSeqno,
                preprepareSeqno,
                cphash,
                stableCPhash,
                batchHashes,
                histories,
                myIndex);
        System.out.println("my vc message: " + vc);
        authenticateOrderMacArrayMessage(vc);
        sendToOtherOrderReplicas(vc.getBytes(), myIndex);

        // create the new view information
        vInfo = new ViewInfo(parameters, currentView, vcCerts,
                primary(currentView) == myIndex);
        changingView = true;
        vInfo.observeViewChange(vc);
        vInfo.setMyViewChange(vc);

        // create a vcack for my vc
        Digest d = new Digest(parameters, vc.getBytes());
        ViewChangeAck vcack = new ViewChangeAck(parameters, vc.getView(),
                vc.getSendingReplica(),
                d, myIndex);
        authenticateOrderMacArrayMessage(vcack);
        if (!amIPrimary()) {
            sendToOrderReplica(vcack.getBytes(), primary(currentView()));
        } else {
            vInfo.addViewChangeAck(vcack);
            if (vInfo.getNewViewMessage() == null) {
                NewView nv = vInfo.generateNewViewMessage(this);
                if (nv != null) {
                    if (!vInfo.definedView())
                        Debug.kill("Primary better have a defined view" +
                                " if it creates a newview msg");
                    else
                        actOnDefinedView();
                }
            }

        }
        // look for the cached nv
        if (nvCache[primary(currentView)] != null &&
                nvCache[primary(currentView)].getView() == currentView()) {
            //System.out.println("****using a cached new view");
            process(nvCache[primary(currentView)]);
            nvCache[primary(currentView)] = null;
        }

    }

    synchronized protected void process(ViewChange vc) {
        // 	Debug.println(Debug.VIEWCHANGE,
        // 			"\t\t\t\t\t\tprocessing view change for "+vc.getView()+
        // 		      " from "+vc.getSendingReplica());

        //	System.out.println(vc);
        // (1) if the view is old, or smaller than the last received
        // from the sender, or its for the current view but we're done
        // view changing already, then dont do anything.
        if (vc.getView() < currentView
                || (vc.getView() <
                viewChanges[(int) vc.getSendingReplica()].getLastReceived())
                || (vc.getView() == currentView && !changingView)) {
            //System.out.println("discarding on the first criteria!");
            return;  // discard deprecated messages
        }
        if (!validateOrderMacArrayMessage(vc, myIndex))
            Debug.kill("invalid maccarray");//return; // discard messages with bad authentication

        Digest d = new Digest(parameters, vc.getBytes());
        ViewChangeAck vcack = new ViewChangeAck(parameters, vc.getView(),
                vc.getSendingReplica(),
                d, myIndex);
        authenticateOrderMacArrayMessage(vcack);

        // mark that somebody started a vc so we know to prepare
        somebodyVotedForVC = true;

        if (primary(vc.getView()) != myIndex)
            sendToOrderReplica(vcack.getBytes(), primary(vcack.getView()));

        // if the view change is for the current view, then add it to
        // the view info
        if (currentView == vc.getView()) {
            vInfo.observeViewChange(vc);
            // if im the primary, record the ack
            if (primary(currentView()) == myIndex) {
                vInfo.addViewChangeAck(vcack);
                if (vInfo.getNewViewMessage() == null) {
                    NewView nv = vInfo.generateNewViewMessage(this);
                    if (nv != null) {
                        if (!vInfo.definedView())
                            Debug.kill("Primary better have a defined view" +
                                    " if it creates a newview msg");
                        else
                            actOnDefinedView();

                    }
                }
            } else if (vInfo.definedView()) {
                //System.out.println("I'm not the primary, acting on the defined iew");
                actOnDefinedView();
                return; // view is defined, so we clearly dont need to
                // do anything as a result of hte view change
            }
        } else {
            //System.out.println("\t\t\t future view change");

            // the view change is for something in the future
            ViewChangeRow vcr = viewChanges[(int) vc.getSendingReplica()];
            vcr.observeViewChange(vc,
                    primary(vc.getView()) == myIndex);
            // if im the primary record the ack
            if (primary(vc.getView()) == myIndex)
                vcr.addViewChangeAck(vcack);

            // if there are f+1 distinct replicas advocating a view
            // larger than currentView, then go to the smallest of
            // those views
            long small = currentView;
            int count = 0;
            for (int i = 0; i < viewChanges.length; i++) {
                //System.out.println(i+" smallest: "+viewChanges[i].getSmallestView());
                if (viewChanges[i].getSmallestView() > currentView)
                    count++;
                if ((viewChanges[i].getSmallestView() < small ||
                        (small == currentView))
                        && viewChanges[i].getSmallestView() > currentView)
                    small = viewChanges[i].getSmallestView();
            }
            if (count >= parameters.smallOrderQuorumSize()
                    || viewChanges[primary(currentView)].getSmallestView() >= small) {
                System.out.println("starting the view change b/c it" +
                        " came in the mail");
                startViewChange(small);
            }
        }

    }

    synchronized protected void process(ViewChangeAck vcack) {
        // 	Debug.println(Debug.VIEWCHANGE,
        // 			"\t\t\t\t\t\tVCAck for "+vcack.getChangingReplica()+
        // 			" from "+vcack.getSendingReplica());
        //	System.out.println(vcack);

        if (vcack.getView() < currentView)
            return;
        else if (primary(vcack.getView()) != myIndex)
            Debug.kill("wrong view");//return; // only the primary needs vcacks
        else if (!validateOrderMacArrayMessage(vcack, myIndex))
            Debug.kill("invalid msg");//return;// reject bad macs
        else if (vcack.getView() > currentView) {
            //System.out.println("caching the vcack for a future view");
            viewChanges[(int) (vcack.getChangingReplica())].addViewChangeAck(vcack);
        } else if (vcack.getView() == currentView) {
            //System.out.println("processing the vack for this view");
            vInfo.addViewChangeAck(vcack);
            if (vInfo.getNewViewMessage() != null) {
                //System.out.println("already did the new view thing");
            } else {
                //System.out.println("\t\tlooking for a new view message");
                NewView nv = vInfo.generateNewViewMessage(this);
                if (nv != null) {
                    actOnDefinedView();
                }
            }
        } else
            Debug.kill("Should never reach this point!");

    }

    synchronized protected void process(NewView nv) {
        // 	Debug.println(Debug.VIEWCHANGE,
        // 			"\t\t\t\t\t\tprocessing new view for "+nv.getView()+
        // 			" from "+nv.getSendingReplica());
        //	System.out.println(nv);

        if (nv.getView() < currentView())
            return;

        if (!validateOrderMacArrayMessage(nv, myIndex))
            Debug.kill("invalid macarray");//return;

        if (nv.getView() > currentView()) {
            //System.out.println("need to cache the future new view!");
            int index = (int) (nv.getSendingReplica());
            if (nvCache[index] == null ||
                    nvCache[index].getView() < nv.getView())
                nvCache[index] = nv;
            return;
        }

        if (!changingView)
            return;  // we're no longer changing view!

        if (vInfo.getNewViewMessage() == null) {
            vInfo.addNewView(nv);
            // if the view is defined, then act on it
            if (vInfo.definedView()) {
                actOnDefinedView();
            } else {
                boolean[] mv = vInfo.getMissingViewChanges();
                for (int i = 0; i < mv.length; i++) {
                    if (mv[i]) {
                        MissingViewChange mvc =
                                new MissingViewChange(currentView, i,
                                        myIndex);
                        authenticateOrderMacMessage(mvc,
                                primary(currentView));
                        sendToOrderReplica(mvc.getBytes(),
                                primary(currentView));
                        //Debug.println("fetching vc for : "+i);
                    }
                }
            }
        }
    }

    /**
     * The purpose of this function is to ensure that the replica has
     * all of the state specified by the new view message.
     **/
    protected boolean requestedThings;

    synchronized protected void actOnDefinedView() {
        //  	Debug.println(Debug.VIEWCHANGE,"\t\t\t\t\t\tactOnDefinedView() "+
        //		      vInfo.getBaseSequenceNumber()+"->"+
        //		      vInfo.getNextSeqNo());
        System.out.println("\t\t\t\t\t\tactOnDefinedView() " +
                vInfo.getBaseSequenceNumber() + "->" +
                vInfo.getNextSeqNo());
        if (!vInfo.definedView())
            Debug.kill("vInfo should be defined");

        boolean allInformation = true;
        // check that the committed checkpoint is present
        int ind = baseIndex;
        int indplusone = (baseIndex + 1) % stateSnapshots.length;
        //System.out.println("commitcp DIGEST: "+vInfo.getCommittedCPDigest());
        //	if (!requestedThings){
        vInfo.addMyCP(stateSnapshots[ind]);
        vInfo.addMyCP(stateSnapshots[indplusone]);
        //	}

        // check that all specified pieces are present
        boolean[] missing = vInfo.getMissingRequests();
        long base = vInfo.getBaseSequenceNumber();

        int missingop = 0;
        // we can only build the cp if we dont need the cp to be
        // stable
        boolean canbuildcp = vInfo.getNextSeqNo() -
                vInfo.getBaseSequenceNumber() < BFT.order.Parameters.maxPeriods *
                BFT.order.Parameters.checkPointInterval
                && vInfo.getStableCPDigest() == null;
        //System.out.println("initial can build: "+canbuildcp);

        // for all mising requests, see if we have it locally, if so
        // load it up
        for (int i = 0; i < missing.length; i++) {
            //System.out.println(i+" is missing: "+missing[i]);
            if (missing[i]) {
                Certificate cert =
                        certificates[stateIndex(base + i)][certificateIndex(base + i)];
                //System.out.println("checking on: "+(base+i));
                if (!vInfo.setCertificate(cert, base + i)) {
                    missingop++;
                    //System.out.println("failed to add the entry at : "+(base+i));
                    canbuildcp = false;
                } else {
                    //System.out.println("added "+(base+i));
                    missing[i] = false;
                }
            }
            allInformation = allInformation && !missing[i];
        }
        //	if (!allInformation)
        //	    System.out.println("missing requests");

        // check to see if we need to fetch the committed cp
        CheckPointState committed = vInfo.getCommittedCP();
        if (committed != null)
            makeCPStable(committed);
        // If I dont have a committed CP, or the committed CP is not
        // stable or being marked as stable, then fetch a new one
        if (committed == null
                || (!committed.isStable() && !committed.isMarkingStable())) {
            //System.out.println("sending missing cp for "+vInfo.getCommittedCPDigest());
            if (!requestedThings) {
                MissingCP mcp = new MissingCP(parameters, vInfo.getBaseSequenceNumber(),
                        vInfo.getCommittedCPDigest(),
                        myIndex);
                authenticateOrderMacArrayMessage(mcp);
                if (amIPrimary())
                    sendToOtherOrderReplicas(mcp.getBytes(), myIndex);
                else
                    sendToOrderReplica(mcp.getBytes(), primary(currentView));
            }
            //	    System.out.println("missing a cp at "+vInfo.getBaseSequenceNumber());
            allInformation = false;
            //System.out.println("cant build it up b/c the committed cp is not stable");
            canbuildcp = false;
        }

        if (committed != null && !committed.isCommitted() && committed.isStable())
            committed.commit();

        // check to see if we need to fetch the stable cp
        CheckPointState stable = vInfo.getStableCP();
        //	if (vInfo.getStableCPDigest() != null
        //  && stable != null)
        //  makeCPStable(stable);
        if (vInfo.getStableCP() == null) {
            // if we have the committed checkpoint and sufficient entries,
            // build up the stable cp
            //System.out.println("can build up the cp?"+canbuildcp);
            if (canbuildcp) {
                //System.out.println(vInfo.getCommittedCP());
                stable = new CheckPointState(committed);
                // 		System.out.println("making new cp for: "+
                // 				   stable.getBaseSequenceNumber());
                Certificate entries[] = vInfo.getEntries(0);

                for (int i = 0; i < entries.length; i++) {
                    if (entries[i].getCertEntry() == null) {
                        for (int j = 0; j < entries.length; j++)
                            System.out.println(entries[j].getCertEntry());
                        entries = vInfo.getEntries(1);
                        for (int j = 0; j < entries.length; j++)
                            System.out.println(entries[j].getCertEntry());
                        Debug.kill("Entry sould not be null!  " +
                                "The missing check said it was not " + i);
                    }
                    stable.addNextBatch(entries[i].getCertEntry(),
                            stable.getBaseSequenceNumber() + i);
                }
                vInfo.addMyCP(stable);
                //System.out.println("added stableCP: "+vInfo.getStableCP());
                // 		System.out.println("requesting cp token from exec: "+stable.getCurrentSequenceNumber());
                RequestCP rcp = new RequestCP(parameters, stable.getCurrentSequenceNumber(),
                        myIndex);
                authenticateExecMacArrayMessage(rcp);
                sendToAllExecutionReplicas(rcp.getBytes());
                if (vInfo.getStableCP() == null)
                    Debug.kill("WTF");
            } else if (vInfo.getStableCPDigest() != null) {
                if (!requestedThings) {
                    MissingCP mcp = new MissingCP(parameters, vInfo.getBaseSequenceNumber() +
                            BFT.order.Parameters.checkPointInterval,
                            vInfo.getStableCPDigest(),
                            myIndex);
                    authenticateOrderMacArrayMessage(mcp);
                    //System.out.println("sending missing cp for "+
                    //		mcp.getVersionNo());
                    if (amIPrimary())
                        sendToOtherOrderReplicas(mcp.getBytes(), myIndex);
                    else
                        sendToOrderReplica(mcp.getBytes(), primary(currentView));
                }
                //		System.out.println("missing a cp2");
                allInformation = false;
            }
        }

        // fetch the requisit operations
        if (missingop > 0 && !requestedThings) {
            long ops[] = new long[missingop];
            int k = 0;
            for (int j = 0; j < missing.length; j++)
                if (missing[j]) {
                    ops[k++] = j + base;
                }
            //System.out.println(missingop+" ops are missing "+ ops.length);
            MissingOps mo = new MissingOps(parameters, currentView, ops, myIndex);
            ;
            authenticateOrderMacArrayMessage(mo);

            if (amIPrimary(currentView)) {
                sendToOtherOrderReplicas(mo.getBytes(), myIndex);
            } else {
                sendToOrderReplica(mo.getBytes(), primary(currentView));
            }
        }

        requestedThings = true;

        // we already have it due to allinformation.  its good if its committed
        boolean goodCommitted = vInfo.getCommittedCP() != null && vInfo.getCommittedCP().isCommitted();
        // its good if we have it.  if its stable OR there is no expected digest and sequence number is not a CP interval

        boolean goodStable = vInfo.getStableCP() != null &&
                ((vInfo.getStableCPDigest() == null || vInfo.getStableCP().isStable()));


        // if we have everything
        if (allInformation
                && goodCommitted
                && goodStable) {
            // 	    && vInfo.getCommittedCP().isCommitted()
            // 	    && vInfo.getStableCP() != null
            // 	    && (vInfo.getStableCPDigest() == null
            // 		|| vInfo.getStableCP().isStable())){
            actOnCompleteView();
        } else {
            // 	    System.out.println("not a complete view");
            // 	    System.out.println(allInformation);
            if (allInformation) {

                // 		System.out.println(vInfo.getCommittedCP().isCommitted());
                // 		System.out.println(vInfo.getCommittedCP());
                if (vInfo.getCommittedCP().isCommitted())
                    //		    System.out.println(vInfo.getStableCP()!=null);
                    if (vInfo.getStableCP() != null) {
                        // 		    System.out.println("stable");
                        // 		    System.out.println(vInfo.getStableCP());
                        // 		    System.out.println(vInfo.getStableCPDigest() == null);
                        // 		    System.out.println( vInfo.getStableCP().isStable());
                        // 		    System.out.println(vInfo.getNextSeqNo() % BFT.order.Parameters.checkPointInterval != 0);
                        ;
                    }
            }
        }
        //else if (!allInformation)
        //Debug.println(Debug.VIEWCHANGE, "missing some bit of information");
        //else if (vInfo.getCommittedCP().isCommitted())
        //Debug.println(Debug.VIEWCHANGE, "committed CP is not stable");
        //else if (vInfo.getStableCP() == null)
        //Debug.println(Debug.VIEWCHANGE, "stable cp is null");
        //else
        //Debug.println(Debug.VIEWCHANGE, "stable cp is not stable");
    }


    /**
     * Once the defined view is entirely present,
     **/
    synchronized protected void actOnCompleteView() {
        Debug.println(Debug.VIEWCHANGE, "\t\t\t\t\t\tactOnCompleteView()");
        System.out.println("\t\t\t\t\t\tactOnCompleteView()");
        if (!vInfo.isComplete())
            Debug.kill("ERROR.  should only come in here with a view that is complete!");

        // if i've already sent a confirmation, no need to do it again
        if (vInfo.confirmedBy(myIndex)) {
            //	    System.out.println("ive already confirmed");
            return;
        }

        if (amIPrimary()) {
            NewView nv = vInfo.getNewViewMessage();
            if (nv == null)
                Debug.kill("Should never be null");
            sendToOtherOrderReplicas(nv.getBytes(), myIndex);
        }

        //System.out.println("#*#*#*#*#*creating confirm view: "+currentView+", "
        //		+vInfo.getNextSeqNo()+", "+vInfo.getLastHistory());

        // copy all of the state over from our caches into the 'live'
        // realm and mark all requests as prepared.
        long cpseqno = vInfo.getBaseSequenceNumber();
        int baseplusone = (baseIndex + 1) % stateSnapshots.length;
        stateSnapshots[baseIndex] = vInfo.getCommittedCP();
        if (!stateSnapshots[baseIndex].isStable())
            Debug.kill("should be stable already!");
        // AGC: why am i committing the committed snapshot?  shouldnt
        // it be committed already?
        if (!stateSnapshots[baseIndex].isCommitted())
            Debug.kill("should be committed already");
        //	stateSnapshots[baseIndex].commit();
        certificates[baseIndex] = vInfo.getEntries(0);
        stateSnapshots[baseplusone] = vInfo.getStableCP();
        certificates[baseplusone] = vInfo.getEntries(1);
        //System.out.println(vInfo.getStableCP());
        if (vInfo.getStableCP() == null)
            Debug.kill("wtf");
        workingState = vInfo.getWorkingState();


        // mark everything as preprepared
        for (long i = getBaseSequenceNumber();
             i < getCurrentSequenceNumber();
             i++) {
            Certificate cert = certificates[stateIndex(i)][certificateIndex(i)];
            cert.forcePrepared();
            NextBatch nb = cert.getNextBatchMessage(this, currentView);
            writeAndSend(nb);
        }
        lastPrepreparedView = currentView;
        maxPrepared = getCurrentSequenceNumber() - 1;

        // make stable cp stable if possible
        if (vInfo.getStableCP().hasExecCP()
                && (!vInfo.getStableCP().isStable()
                || !vInfo.getStableCP().isMarkingStable()))
            makeCPStable(vInfo.getStableCP());
        // AGC technically need to write all of the new entries down
        // to disc before i do any sending here!


        ConfirmView cv = new ConfirmView(parameters, currentView,
                vInfo.getNextSeqNo(),
                vInfo.getLastHistory(),
                myIndex);
        authenticateOrderMacArrayMessage(cv);
        vInfo.addConfirmation(cv);


        sendToOtherOrderReplicas(cv.getBytes(), myIndex);
        vInfo.flushConfirmedCache();

        //	System.out.println("sent the confirm out");
        if (vInfo.isConfirmed())
            actOnConfirmedView();

    }

    synchronized protected void process(ConfirmView cv) {
        //	System.out.println(cv);
        Debug.println(Debug.VIEWCHANGE,
                "\t\t\t\t\t\t process confirm view from "
                        + cv.getSendingReplica() + " for view: " + cv.getView() +
                        " at seqno: " + cv.getSeqNo());

        if (cv.getView() < currentView)
            return;


        if (!validateOrderMacArrayMessage(cv, myIndex)) {
            Debug.kill("verification should never fail");
            return;
        }

        if (cv.getView() != currentView) {
            System.out.println(cv);
            System.out.println(currentView);
            System.out.println(cv.getView());
            //	    Debug.kill("not dealing with out of order confirm views yet");
            viewChanges[(int) cv.getSendingReplica()].addConfirmView(cv,
                    amIPrimary(cv.getView()));
            // and i think thats it?  though it might entail doing the
            // 'view change' check as well -- i.e. process(view
            // change) looks to see if this message will trigger
            // another future vc
            return;
        }


        if (vInfo.isConfirmed())
            return;

        vInfo.addConfirmation(cv);
        if (vInfo.definedView() && vInfo.isComplete() && vInfo.isConfirmed())
            actOnConfirmedView();
        //System.out.println("complete: "+vInfo.isComplete());
        //System.out.println("confirmed:"+vInfo.isConfirmed());
        //System.out.println("defined:  "+vInfo.definedView());

    }


    synchronized protected void actOnConfirmedView() {

        Debug.println(Debug.VIEWCHANGE, "\t\t\t\t\t\tactOnConfirmedView");
        if (!vInfo.isConfirmed())
            Debug.kill("view should be confirmed if you're getting here");

        if (stateSnapshots[baseIndex].getStableDigest() == null)
            Debug.kill("cant confirm a view with an unstable base");

        int baseplusone = (baseIndex + 1) % stateSnapshots.length;

        int lastIndex = BFT.order.Parameters.checkPointInterval - 1;
        if (stateSnapshots[baseplusone].getStableDigest() == null
                && certificates[baseplusone][lastIndex].getHistory() != null)
            Debug.kill("cant confirm a view change if the second checkpoint" +
                    " must be stable and is not");

        if (workingState.getCurrentSequenceNumber() != vInfo.getNextSeqNo()
                || !workingState.getHistory().equals(vInfo.getLastHistory()))
            Debug.kill("cant confirm a view change if the working state does" +
                    " not match the current sequence of requests");


        // mark everything as confirmed (aka prepared)
        long ln = getCurrentSequenceNumber() - 1;
        while (ln > getBaseSequenceNumber()) {
            certificates[stateIndex(ln)][certificateIndex(ln--)].forcePrepared();
        }
        // send a prepared version of the most recent batch
        ln = getCurrentSequenceNumber() - 1;
        if (ln % BFT.order.Parameters.checkPointInterval == 0 &&
                !vInfo.getStableCP().isStable() && !vInfo.getStableCP().isMarkingStable() &&
                vInfo.getStableCP().hasExecCP())
            makeCPStable(vInfo.getStableCP());
        NextBatch nb = certificates[stateIndex(ln)][certificateIndex(ln)].getNextBatchMessage(this, currentView);
        writeAndSend(nb);


        CommitView cv = new CommitView(parameters, currentView,
                vInfo.getNextSeqNo(),
                vInfo.getLastHistory(),
                myIndex);
        authenticateOrderMacArrayMessage(cv);
        vInfo.addCommitView(cv);
        // AGC there is likely a bunch of shit here that should be written to disc
        sendToOtherOrderReplicas(cv.getBytes(), myIndex);
        vInfo.flushCommitCache();

        //	System.out.println("sent the confirm out");
        if (vInfo.isCommitted())
            actOnCommittedView();


    }


    synchronized protected void process(CommitView cv) {
        // Debug.println(Debug.VIEWCHANGE,
        // 		"\t\t\t\t\t\t process commit view from "
        // 		+cv.getSendingReplica() +" for view: "+cv.getView()+
        // 		" at seqno: "+cv.getVersionNo());

        if (cv.getView() < currentView)
            return;

        if (!validateOrderMacArrayMessage(cv, myIndex)) {
            Debug.kill("verification should never fail");
            return;
        }

        if (cv.getView() != currentView) {
            Debug.kill("not dealing with out of order confirm views yet");
            viewChanges[(int) cv.getSendingReplica()].addCommitView(cv,
                    amIPrimary(cv.getView()));

            // and i think thats it?  though it might entail doing the
            // 'view change' check as well -- i.e. process(view
            // change) looks to see if this message will trigger
            // another future vc
            return;
        }


        if (vInfo.isCommitted())
            return;


        vInfo.addCommitView(cv);
        if (vInfo.definedView() && vInfo.isComplete() && vInfo.isConfirmed() &&
                vInfo.isCommitted())
            actOnCommittedView();
        //System.out.println("complete: "+vInfo.isComplete());
        //System.out.println("confirmed:"+vInfo.isConfirmed());
        //System.out.println("defined:  "+vInfo.definedView());

    }

    synchronized protected void actOnCommittedView() {
        Debug.println(Debug.VIEWCHANGE, "\t\t\t\t\t\tactOnCommittedView");
        System.out.println("\t\t\t\t\t\tactOnCommittedView");
        if (!vInfo.isConfirmed())
            Debug.kill("view should be confirmed if you're getting here");

        if (!vInfo.isCommitted())
            Debug.kill("view should be committed if you're here!");

        if (stateSnapshots[baseIndex].getStableDigest() == null)
            Debug.kill("cant confirm a view with an unstable base");

        int baseplusone = (baseIndex + 1) % stateSnapshots.length;

        int lastIndex = BFT.order.Parameters.checkPointInterval - 1;
        if (stateSnapshots[baseplusone].getStableDigest() == null
                && certificates[baseplusone][lastIndex].getHistory() != null)
            Debug.kill("cant confirm a view change if the second checkpoint" +
                    " must be stable and is not");

        if (workingState.getCurrentSequenceNumber() != vInfo.getNextSeqNo()
                || !workingState.getHistory().equals(vInfo.getLastHistory()))
            Debug.kill("cant confirm a view change if the working state does" +
                    " not match the current sequence of requests");


        // mark everything as committed
        long ln = getCurrentSequenceNumber() - 1;
        while (ln > getBaseSequenceNumber()) {
            certificates[stateIndex(ln)][certificateIndex(ln--)].forceCommitted();
        }
        // send a committed version of the most recent batch
        ln = getCurrentSequenceNumber() - 1;
        if (ln % BFT.order.Parameters.checkPointInterval == 0 &&
                !vInfo.getStableCP().isStable() && !vInfo.getStableCP().isMarkingStable() &&
                vInfo.getStableCP().hasExecCP())
            makeCPStable(vInfo.getStableCP());
        NextBatch nb = certificates[stateIndex(ln)][certificateIndex(ln)].getNextBatchMessage(this, currentView);
        writeAndSend(nb);

        changingView = false;

        //	System.out.println("starting the new view now!!!");
        for (int i = 0; i < parameters.getExecutionCount(); i++) {
            forwardNextBatches(getCurrentSequenceNumber() - 1, i);
            lastExecDelay[i] = 20;
        }


        // reset the throughput thresholds
        runningCount = 0;
        viewTotal = 0;
        oldTime = System.currentTimeMillis();
        viewStartTime = oldTime;
        cpCount = 0;
        scheduleViewChange = false;
        batchCreationAllowed = true;
        somebodyVotedForVC = false;
        // reset the active count
        activeCount = 0;
        for (int i = 0; i < active.length; i++)
            active[i] = false;
        // unpile the pp and commit caches
        for (int i = 0; i < vcPPs.length; i++)
            if (vcPPs[i] != null) {
                process(vcPPs[i]);
                vcPPs[i] = null;
                //		System.out.println("$%@#$%@#$%@#$%@#$%@#$%@#$%@#%@$%#@$%");
            }
        for (int i = 0; i < vcCommits.length; i++)
            if (vcCommits[i] != null) {
                process(vcCommits[i]);
                vcCommits[i] = null;
            }

        //System.out.println("***** need to send nextbatch messages");
        _vcCount = 1;
        updateHeartBeat();
        System.out.println("starting view " + currentView + " @ " +
                getCurrentSequenceNumber());
    }


    synchronized protected void process(StartView sv) {
        Debug.println(Debug.VIEWCHANGE,
                "\t\t\t\t\t\tprocessing start view: " + sv.getView() +
                        " from " + sv.getSendingReplica());
        // if its not for this view or im not the primary, drop the
        // message
        if (sv.getView() != currentView || !amIPrimary())
            return;

        // the sender is just too far behind to be helped!
        if (sv.getSeqNo() < getBaseSequenceNumber())
            return;

        //System.err.println("******rate limiting goes here*******");

        if (!validateOrderMacMessage(sv))
            return;

        long ind = vInfo.getNextSeqNo();
        while (ind < getCurrentSequenceNumber()) {
            System.out.println("sending a late message to " + sv.getSendingReplica() + " " + ind);
            PrePrepare pp =
                    certificates[stateIndex(ind)][certificateIndex(ind)].getPrePrepare();
            if (pp == null)
                Debug.kill("pp should not be null in this view!");
            //System.out.println("**sending pp "+pp.getVersionNo()+" to "+
            //		sv.getSendingReplica());
            sendToOrderReplica(pp.getBytes(), sv.getSendingReplica());
            ind++;
        }

    }

    synchronized protected void process(MissingCP mcp) {
        // 	Debug.println(Debug.VIEWCHANGE,
        // 			"\t\t\t\t\t\tMissing CP from "+mcp.getSendingReplica()
        // 		      +" @ "+mcp.getVersionNo());
        if (mcp.getSeqNo() < getBaseSequenceNumber()
                || mcp.getSeqNo() >= getCurrentSequenceNumber()) {
            //System.out.println("bailing on missing cp b/c its not in range");
            return;
        }
        if (mcp.getSeqNo() % BFT.order.Parameters.checkPointInterval != 0) {
            //System.out.println("bailing b/c its not a cp interval");
            Debug.kill("It's not a checkpoint interval, thats bad");
            return;
        }
        int base = stateIndex(mcp.getSeqNo());
        CheckPointState cps = stateSnapshots[base];
        if (cps.getCurrentSequenceNumber() != mcp.getSeqNo()) {
            Debug.kill("badness in checkpoint list -- found " +
                    cps.getCurrentSequenceNumber() + " while looking for " +
                    mcp.getSeqNo());
            return;
        }
        if (!validateOrderMacArrayMessage(mcp, myIndex))
            Debug.kill("authetnication should not fail");

        if (!cps.isStable()) {
            System.out.println("Cant relay a checkpoint that is not stable");
            return;
        }
        RelayCP rcp =
                new RelayCP(cps,
                        myIndex);

        //	System.out.println("Sending relaycp! to " +mcp.getSendingReplica());
        authenticateOrderMacMessage(rcp, mcp.getSendingReplica());
        sendToOrderReplica(rcp.getBytes(), mcp.getSendingReplica());

    }

    synchronized protected void process(RelayCP rcp) {
        // 	Debug.println(Debug.VIEWCHANGE,
        // 			"\t\t\t\t\t\trelay cp from : "+rcp.getSendingReplica()+
        // 			" for "+rcp.getSequenceNumber());
        if (!changingView)
            return;
        // 	if (!amIPrimary()){
        // 	    Debug.kill("dropping relaycp b/c im not the pimary?");
        // 	    return;
        // 	}
        long[] mcps = vInfo.getMissingCPs();
        boolean found = false;
        for (int i = 0; i < mcps.length; i++) {
            //System.out.println("missing cp: "+mcps[i]);
            if (mcps[i] == rcp.getSequenceNumber())
                found = true;
        }

        // 	if (!found){
        // 	    System.out.println(" missing this cp");
        // 	    return;
        // 	}

        if (!vInfo.definedView()) {
            Debug.kill("view should be defined");
            return;
        }
        if (!validateOrderMacMessage(rcp)) {
            Debug.kill("invalid message?!");
            return;
        }

        //System.out.println(rcp.getSequenceNumber());
        //System.out.println(vInfo.getBaseSequenceNumber());
        //System.out.println(vInfo.getCommittedCPDigest());
        //System.out.println(rcp.getCheckPoint().getDigest());
        //System.out.println(vInfo.getStableCP());
        //System.out.println(rcp.getCheckPoint().getDigest());
        //System.out.println(vInfo.getStableCPDigest());
        if (rcp.getSequenceNumber() == vInfo.getBaseSequenceNumber()) {
            if (vInfo.getCommittedCP() == null &&
                    vInfo.getCommittedCPDigest().equals(rcp.getCheckPoint().getDigest())) {
                makeCPStable(rcp.getCheckPoint());
                vInfo.addMyCP(rcp.getCheckPoint());
                System.out.println("\t\tadded committed CP: " + rcp.getSequenceNumber());
                //		System.out.println(vInfo.getCommittedCP());
            } else
                System.out.println("failed to add stable cp");

            actOnDefinedView();
        } else if (rcp.getSequenceNumber()
                == vInfo.getBaseSequenceNumber() + BFT.order.Parameters.checkPointInterval) {
            if (vInfo.getStableCP() == null &&
                    rcp.getCheckPoint().getDigest().equals(vInfo.getStableCPDigest())) {
                makeCPStable(rcp.getCheckPoint());
                vInfo.addMyCP(rcp.getCheckPoint());
                System.out.println("\t\tadded stable cp: " + rcp.getSequenceNumber());
                //		System.out.println(vInfo.getStableCP());
            } else
                System.out.println("failed to add stable cp");

            actOnDefinedView();
        } else
            System.out.println("added neither cp!");
        //Debug.println(Debug.VIEWCHANGE,
        //"\t\t\t\t\t\tFinished handling relaycp");

    }

    synchronized protected void process(MissingViewChange mv) {
        Debug.println(Debug.VIEWCHANGE,
                "\t\t\t\t\t\tMissing view change: " + mv.getView() + " from " +
                        mv.getMissingReplicaId() + ", sent by " + mv.getSendingReplica());
        if (currentView != mv.getView())
            return;
        if (primary(currentView) != mv.getSendingReplica()
                && !amIPrimary())
            return;
        if (primary(currentView) == mv.getSendingReplica())
            return;

        boolean[] mvcs = vInfo.getMissingViewChanges();
        if (!mvcs[(int) (mv.getSendingReplica())])
            return;

        if (!validateOrderMacMessage(mv))
            return;


        MacBytes[] macs = new MacBytes[parameters.getOrderCount()];
        for (int i = 0; i < macs.length; i++)
            macs[i] = new MacBytes();
        RelayViewChange rvc =
                new RelayViewChange(vInfo.getViewChange(mv.getSendingReplica()),
                        macs, myIndex);
        authenticateOrderMacMessage(rvc, (int) (mv.getSendingReplica()));
        sendToOrderReplica(rvc.getBytes(), mv.getSendingReplica());

        Debug.kill("Not handling missing view changes yet");
    }

    synchronized protected void process(RelayViewChange rvc) {
        Debug.println(Debug.VIEWCHANGE,
                "\t\t\t\t\t\tRelay view change from " +
                        rvc.getSendingReplica() +
                        "contains vc from " +
                        rvc.getViewChange().getSendingReplica());

        Debug.kill("Not handling missing relay view changes yet");
    }


    synchronized protected void process(MissingOps mo) {
        Debug.println(Debug.VIEWCHANGE,
                "\t\t\t\t\t\tMissingOps from " + mo.getSendingReplica());

        //System.err.println("&&&&**** RateLimiting applies in" +
        //        " process(missingOps)");


        if (mo.getView() != currentView)
            return; // discard if its not for this view
        if (!amIPrimary() && primary(currentView) != mo.getSendingReplica())
            return; // discard if im not hte primary and this is not
        // coming from the primary
        if (!validateOrderMacArrayMessage(mo, myIndex))
            Debug.kill("macarrayr");//return; // discard if it is not valid

        long[] miss = mo.getMissingOps();
        for (int i = 0; i < miss.length; i++)
            if (miss[i] >= getBaseSequenceNumber()
                    && i < getCurrentSequenceNumber()) {
                CertificateEntry certentry =
                        certificates[stateIndex(miss[i])][certificateIndex(miss[i])].getCertEntry();
                if (certentry != null) {
                    //		    System.out.println("sending a relay ops for "+miss[i]+
                    //				       " to "+mo.getSendingReplica());
                    RelayOps rops = new RelayOps(certentry,
                            miss[i],
                            myIndex);
                    authenticateOrderMacMessage(rops,
                            mo.getSendingReplica());
                    sendToOrderReplica(rops.getBytes(),
                            mo.getSendingReplica());
                }
            }
    }


    synchronized protected void process(RelayOps rops) {
        // 	 	Debug.println(Debug.VIEWCHANGE, "\t\t\t\t\t\tRelay OP "+
        // 			       		      rops.getSequenceNumber()+" from "+
        // 			       		      rops.getSendingReplica());

        if (rops.getSequenceNumber() < vInfo.getBaseSequenceNumber()
                || rops.getSequenceNumber() >= vInfo.getNextSeqNo())
            return;
        if (!vInfo.definedView())
            return;
        if (!validateOrderMacMessage(rops))
            Debug.kill("invalid macarray");//return;
        if (vInfo.addEntry(rops.getEntry(), rops.getSequenceNumber()))
            actOnDefinedView();

    }


    /*****************************
     Help me, i'm lost!
     *******************************/
    synchronized protected void process(OrderStatus os) {
        //System.err.println("****RATE LIMITING GOES HERE****");
        Debug.kill("not using order status yet");
        //Debug.println(Debug.STATUS, "\t\t\t\tStatus "+os.getView()+" from "+
        //		os.getSendingReplica());

        if (!validateOrderMacArrayMessage(os, myIndex))
            Debug.kill("validation should not fail yet");

        // if its from the future, respond with an orderstatus
        if (os.getView() > currentView()) {
            OrderStatus os2 = new OrderStatus(parameters, currentView, maxCommitted,
                    maxPrepared,
                    getCurrentSequenceNumber(),
                    myIndex);
            authenticateOrderMacArrayMessage(os2);
            sendToOrderReplica(os2.getBytes(), (int) (os2.getSendingReplica()));
            Debug.kill("shouldnt be hitting this yet");
            return;
        }

        if (os.getView() < currentView()) {
            if (amIPrimary()) {
                sendToOrderReplica(vInfo.getNewViewMessage().getBytes(),
                        (int) (os.getSendingReplica()));
                Debug.kill("ought to send forwarded view changes as well");
                return;
            } else {
                sendToOrderReplica(vInfo.getMyViewChange().getBytes(),
                        (int) (os.getSendingReplica()));
                Debug.kill("I really hope we're not doing this piece yet");
                return;
            }
        }

        if (os.getView() == currentView()) {
            if (amIPrimary()) {
                long pp = os.getLastPrePrepared();
                Certificate cert = null;
                while (pp < getCurrentSequenceNumber()
                        && pp >= getBaseSequenceNumber()) {
                    cert = certificates[stateIndex(pp)][certificateIndex(pp)];
                    //System.out.println("*****************forwarding preprepares"+pp);
                    if (cert.getPrePrepare() != null)
                        sendToOrderReplica(cert.getPrePrepare().getBytes(),
                                (int) (os.getSendingReplica()));
                    else
                        Debug.kill("shouldnt happen");
                    pp++;
                }
                pp = os.getLastCommitted();
                if (pp < maxCommitted) {
                    pp = maxCommitted;
                    cert = certificates[stateIndex(pp)][certificateIndex(pp)];
                    if (cert.getPrepare() != null)
                        sendToOrderReplica(cert.getPrepare().getBytes(),
                                (int) (os.getSendingReplica()));
                    else
                        Debug.kill("shouldnt happen");
                }
                pp = os.getLastPrepared();
                if (pp > maxCommitted && pp < maxPrepared) {
                    cert = certificates[stateIndex(pp)][certificateIndex(pp)];
                    if (cert.getCommit() != null)
                        sendToOrderReplica(cert.getCommit().getBytes(),
                                (int) (os.getSendingReplica()));
                    else
                        Debug.kill("shouldnt happen");
                }


            } else {


            }
        }

    }

    /*******************************
     Interactions with the execution node
     *******************************/

    /**
     * resend nextbatch messages from last through currentseqno -1
     * Since the lastexecuted message is sent by a single replica
     * (rather than a quorum), it must be rate limited.
     **/
    protected void process(LastExecuted last) {
        System.out.println("process last executed " + last.getSequenceNumber() + " from " + last.getSendingReplica());
        System.out.println("current seqno: " + getCurrentSequenceNumber() + " base: " + getBaseSequenceNumber());
        System.out.println("can exec batch: " + canCreateBatch());
        // validate the message

        long cur = System.currentTimeMillis();
        int senderIndex = (int) last.getSendingReplica();
        if (cur < lastLastExecCheck[senderIndex] +
                lastExecDelay[senderIndex]) {
            return;
        } else {
            lastLastExecCheck[senderIndex] = cur;
            if (cur - lastLastExecCheck[senderIndex] < 4 * lastExecDelay[senderIndex])
                lastExecDelay[senderIndex] *= 2;
            else
                lastExecDelay[senderIndex] /= 2;
            if (lastExecDelay[senderIndex] > 1000 || lastExecDelay[senderIndex] < 300)
                lastExecDelay[senderIndex] = 1000;

        }
        long baseline = last.getSequenceNumber();
        if (baseline >= getCurrentSequenceNumber())
            return;

        if (!validateExecMacArrayMessage(last, myIndex))
            Debug.kill("invalid macarray");//return;


        //Debug.kill(new RuntimeException("last executed is in the future: "+
        //baseline +" > "+getCurrentSequenceNumber()));

        // if its too old, send a load checkpoint
        if (baseline < getBaseSequenceNumber()) {
            System.out.println("sending " + senderIndex + " load cp message for: " + getBaseSequenceNumber());
            LoadCPMessage lcp =
                    new LoadCPMessage(stateSnapshots[baseIndex].getExecCPToken(),
                            getBaseSequenceNumber(),
                            myIndex);
            authenticateExecMacMessage(lcp, senderIndex);
            baseline = getBaseSequenceNumber();
            sendToExecutionReplica(lcp.getBytes(), senderIndex);
            lastExecLoadCP[senderIndex] = true;
            return;
        }

        forwardNextBatches(baseline, senderIndex);
    }

    protected void process(CPLoaded cpl) {
        int senderIndex = (int) (cpl.getSendingReplica());
        System.out.println("\t" + senderIndex + " loaded a cp at " + cpl.getSequenceNumber());
        if (!lastExecLoadCP[senderIndex])
            return;

        long baseline = cpl.getSequenceNumber();
        if (baseline >= getCurrentSequenceNumber())
            return;
        if (baseline < getBaseSequenceNumber())
            return;

        if (!validateExecMacArrayMessage(cpl, myIndex))
            Debug.kill("invalid macarray");//return;

        forwardNextBatches(baseline, senderIndex);
        lastExecLoadCP[senderIndex] = false;
    }

    protected void forwardNextBatches(long baseline, int sendingReplica) {
        System.out.println("forwarding to " + sendingReplica + "from " + baseline + " through " + getCurrentSequenceNumber());

        int count = 0;

        // if its in the range then send nextbatches up to the
        // getCurrentSequenceNumber()
        if (baseline < getCurrentSequenceNumber()) {
            int index = stateIndex(baseline);
            while (index != currentIndex) {
                for (int i = certificateIndex(baseline);
                     i < certificates[index].length;
                     i++, baseline++) {
                    NextBatch nb =
                            certificates[index][i].getNextBatchMessage(this,
                                    currentView);
                    count++;
                    authenticateExecMacArrayMessage(nb);
                    sendToExecutionReplica(nb.getBytes(),
                            (int) sendingReplica);
                    //		    System.out.println("resending "+nb.getVersionNo()+" to "+sendingReplica);
                }
                index = (index + 1) % BFT.order.Parameters.maxPeriods;
            }
            // we're now into the working certificate list
            for (int i = certificateIndex(baseline);
                 baseline < getCurrentSequenceNumber();
                 i++, baseline++) {
                NextBatch nb =
                        certificates[index][i].getNextBatchMessage(this,
                                currentView);
                authenticateExecMacArrayMessage(nb);
                count++;
                sendToExecutionReplica(nb.getBytes(),
                        (int) sendingReplica);
                System.out.println("resending (2) " + nb.getSeqNo() + " to " + sendingReplica);
            }
        }
        //Debug.println("*****sent "+ count+" messages  starting at "+
        //		baseline +
        //" in respose to last executed!");
    }


    /**************
     Utility management
     **************/


    protected boolean inRange(long seqNo) {
        return seqNo >= getBaseSequenceNumber() &&
                seqNo < (getBaseSequenceNumber() +
                        (BFT.order.Parameters.maxPeriods *
                                BFT.order.Parameters.checkPointInterval));

    }

    /**
     * Returns a mapping of a sequence number to an index in the
     * certificate list
     **/
    protected int certificateIndex(long seqNo) {
        return (int) (seqNo % BFT.order.Parameters.checkPointInterval);
    }

    /**
     * returns a mapping of a sequence number to an index in the state list
     **/
    protected int stateIndex(long seqNo) {
        long base = getBaseSequenceNumber();
        base = seqNo - base;
        base = base / BFT.order.Parameters.checkPointInterval;
        return (int) ((baseIndex + base) % BFT.order.Parameters.maxPeriods);
    }


    protected int getBaseIndex() {
        return baseIndex;
    }

    protected int workingStateIndex() {
        return currentIndex;
    }

    /**
     * Returns the first sequence number executed since the checkpoint
     * stored in stateSnapshots[baseIndex].
     **/
    protected long getBaseSequenceNumber() {
        return stateSnapshots[baseIndex].getCurrentSequenceNumber();
    }

    /**
     * Returns the ordered sequence number by clientid and req id,
     * if not found, either i am in recovery or something wroing,
     * return the current sequence number
     **/
    public long getOrderedSequenceNumber(long client, long reqid) {
        return workingState.getOrderedSeqNo(client, reqid);
    }

    /**
     * Returns the sequence number of the next batch to be added.  All
     * sequence numbers < current sequence number have a nextbatch
     * contained in a certificate.
     **/
    public long getCurrentSequenceNumber() {
        return workingState.getCurrentSequenceNumber();
    }

    /**
     * Return the index of the primary for the current view
     **/
    protected int primary(long viewNo) {
        return (int) (viewNo % parameters.getOrderCount());
    }

    protected boolean amIPrimary() {
        return amIPrimary(currentView);
    }

    protected boolean amIPrimary(long view) {
        return primary(view) == myIndex;
    }

    protected HistoryDigest getHistory() {
        return workingState.getHistory();
    }

    protected long currentView() {
        return currentView;
    }


    protected CheckPointState getWorkingState() {
        return workingState;
    }

    private void authenticateHere() {
        System.err.println("      BFT/order/obn authenticate here");
    }

    // ** LOGGING METHODS **

    public void setNBQueue(NBLogQueue nbq) {
        this.nbq = nbq;
    }

    public void writeAndSend(NextBatch nb) {
        if (parameters.doLogging) {
            NBLogWrapper nbw = new NBLogWrapper(nb);
            nbq.addWork(currentLog, nbw);
        } else {
            andSend(nb);
        }
    }

    public void andSend(NextBatch nb) {
//        System.err.println("send to all execution replicas: " + nb.getSeqNo());
        sendToAllExecutionReplicas(nb.getBytes());
        //	System.out.println("sending: "+nb);
    }

    public void resumeLog(String label) {
        if (parameters.doLogging) {
            currentLog = (currentLog + 1) % NUM_LOGGERS;
            NBLogWrapper nbw = new NBLogWrapper(LogFileOps.RESUME, label);
            nbq.addWork(currentLog, nbw);
        }
    }

    public void newLog(String label) {
        //1) send new file message
        if (parameters.doLogging) {
            currentLog = (currentLog + 1) % NUM_LOGGERS;
            NBLogWrapper nbw = new NBLogWrapper(LogFileOps.CREATE, label);
            nbq.addWork(currentLog, nbw);
        }
        //2) flip the switch
    }

    public void gcLog(String label) {
        if (parameters.doLogging) {
            // send delete file message
            NBLogWrapper nbw = new NBLogWrapper(LogFileOps.DELETE, label);
            nbq.addWork(currentLog, nbw);
        }
    }

    // *********************

    public void start() {
        super.start();
        init();
        RequestCP rcp = new RequestCP(parameters, getBaseSequenceNumber(), myIndex);
        authenticateExecMacArrayMessage(rcp);
        sendToAllExecutionReplicas(rcp.getBytes());
        if (scheduleViewChange) {
            scheduleViewChange = false;
            startViewChange(currentView + 1);
        }
        started = true;
    }

    public void init() {
        System.out.println("starting from: " + currentView);
        System.out.println("seqno: " + getCurrentSequenceNumber());
        if (getCurrentSequenceNumber() == 0)
            newLog(0 + "_" + myIndex + "_");
        else {
            String logname = "" + (getCurrentSequenceNumber() - getCurrentSequenceNumber() % BFT.order.Parameters.checkPointInterval);
            logname = logname + "_" + myIndex + "_";
            System.out.println(logname);
            System.out.println("need to pick up from the old stream");
            resumeLog(logname);
            System.out.println("going to a new view following a restart");
            //	    startViewChange(currentView+1);
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            Debug.println("Usage: java Applications.EchoClient <id> <config_file>");
            System.exit(0);
        }
        //Security.addProvider(new de.flexiprovider.core.FlexiCoreProvider());
        OrderBaseNode osn = new OrderBaseNode(args[1], "", Integer.parseInt(args[0]));

        // the sending network
        //	NettyTCPSender sendNet = new NettyTCPSender(osn.getMembership(), 1);
        SenderWrapper sendNet = new SenderWrapper(osn.getMembership(), 1);
        osn.setNetwork(sendNet);


        // "service" processing
        Role[] roles = new Role[2];
        roles[0] = Role.EXEC;
        roles[1] = Role.ORDER;

        PassThroughNetworkQueue ptnwq = new PassThroughNetworkQueue(osn);
        // NettyTCPReceiver receiveNet = new NettyTCPReceiver(roles,
        // 						   osn.getMembership(),
        // 						   ptnwq,
        // 						   1);
        ReceiverWrapper receiveNet = new ReceiverWrapper(roles,
                osn.getMembership(),
                ptnwq,
                1);

        // Setting up the Client/filte request processing
        PassThroughNetworkQueue cwq = new PassThroughNetworkQueue(osn);

        Role[] clientRole = new Role[1];
        if (!obn.getParameters().filtered) {
            clientRole[0] = Role.CLIENT;
        } else {
            clientRole[0] = Role.FILTER;
        }
        int thCount = obn.getParameters().filtered ? obn.getParameters().getFilterCount() : 4;
        thCount = thCount > 4 ? 4 : thCount;
        // NettyTCPReceiver clientNet = new NettyTCPReceiver(clientRole,
        // 						  osn.getMembership(),
        // 						  cwq, thCount);
        ReceiverWrapper clientNet = new ReceiverWrapper(clientRole,
                osn.getMembership(),
                cwq, thCount);

        if (obn.getParameters().doLogging) {
            // logging next batch messages
            NBQueue nbq = new NBQueue();
            osn.setNBQueue(nbq);

            NBLogger l1 = new NBLogger(0, nbq, osn);
            NBLogger l2 = new NBLogger(1, nbq, osn);

            Thread l1t = new Thread(l1);
            Thread l2t = new Thread(l2);
            l1t.start();
            l2t.start();
        }
        // and start the orderbasenode
        //	osn.init();
        osn.start();
    }

    /**
     * @param cpwq the cpwq to set
     */
    public void setCpwq(CPQueue cpwq) {
        this.cpwq = cpwq;
    }

    /**
     *
     **/
    public int getMyOrderIndex() {
        return myIndex;
    }

    private class CreateBatchThread extends Thread {
        public long lastBatchTime = -1;
        private int lastBatchSize = -1;
        private int thisBatchSize = -1;
        double realBatchFillTime = batchFillTime;
        double increment = batchFillTime / 10;
        long MAX_FILL_TIME = parameters.execBatchWaitTime + 50;
        boolean decreasedLast = true;

        public void run() {

            while(true) {
                long remainingTime;
                long knownBatchtime;

                synchronized (this) {
                    knownBatchtime = lastBatchTime;
                    batchFillTime = (long) realBatchFillTime;
                    remainingTime = batchFillTime - (System.currentTimeMillis() - lastBatchTime);
                    //System.err.println("remaing time: " + remainingTime);
                }

                try {
                    if(remainingTime <= 0) {
                        remainingTime = batchFillTime;
                    }
                    Thread.sleep(remainingTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
//                long startTime = System.currentTimeMillis();
//                while(remainingTime >= System.currentTimeMillis() - startTime) {
//
//                }

                if(knownBatchtime >= lastBatchTime && batchCreationAllowed)
                {
                    //System.err.println("trigger create batch");
                    tryToCreateBatch(true);
                }
            }
        }

        public synchronized void resetBatchCreation(int batchSize) {
            if (parameters.dynamicBatchFillTime) {
                lastBatchSize = thisBatchSize;
                thisBatchSize = batchSize;

                if (thisBatchSize < PREFERED_BATCH_SIZE) {

                    realBatchFillTime = Math.min(realBatchFillTime + increment, MAX_FILL_TIME);
                    if (decreasedLast) {
                        increment *= .95;
                    }
                    decreasedLast = false;
                } else if (lastBatchSize >= PREFERED_BATCH_SIZE && thisBatchSize >= PREFERED_BATCH_SIZE) {

                    realBatchFillTime = Math.max(5, realBatchFillTime - increment);
                    if (!decreasedLast) {
                        increment *= .95;
                    }
                    decreasedLast = true;
                } else {
                    decreasedLast = false;
                }
            }

            lastBatchTime = System.currentTimeMillis();
            //System.err.println("lastBatchTime");
        }
    }

}
