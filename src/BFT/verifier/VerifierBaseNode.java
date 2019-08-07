package BFT.verifier;

import BFT.BaseNode;
import BFT.Debug;
import BFT.messages.*;
import BFT.network.PassThroughNetworkQueue;
import BFT.network.netty.NettyTCPReceiver;
import BFT.network.netty.NettyTCPSender;
import BFT.order.NBLogQueue;
import BFT.util.Role;
import BFT.verifier.messages.*;
import BFT.verifier.messages.MessageTags;
import BFT.verifier.statemanagement.Certificate;
import BFT.verifier.statemanagement.ViewChangeCertificate;
import BFT.verifier.statemanagement.ViewChangeRow;
import BFT.verifier.statemanagement.ViewInfo;

import java.util.HashMap;
import java.util.HashSet;

// receiving
// sending
// agreement
// view change
//import BFT.verifier.statemanagement.ViewChangeRow;
// verifier specific utility
// generic utility
// verifier node state
// network communication


public class VerifierBaseNode extends BaseNode {


    boolean DISABLE_VC = false;
    long CPNOW = -299;

    long requestsOrdered = 0;
    long lastCommittedTimestamp;

    // Maintain a set of certificate lists
    // certificates[i] is requests executed starting from
    // stateSnapshots[i]
    protected Certificate[] certificates;
    // index of the certificate list that we are currently working on
    protected int currentIndex;
    // collection of preprepares that are "in the future"
    protected VerifyMessage[][] futureVMs;
    protected Prepare[][] futurePs;
    protected Commit[][] futureCs;

    //    protected long currentSequenceNumber;
    protected long currentView;
    protected long maxPreprepared = 0;
    protected long maxPrepared = 0;
    protected long maxCommitted = 0;

    // a replica has initiated a view change
    protected boolean somebodyVotedForVC = false;

    // view changes
    protected ViewChangeRow[] viewChanges;
    protected ViewInfo vInfo = null;
    protected NewView[] nvCache = null;
    protected boolean changingView = false;
    protected boolean actedOnView = false;
    protected boolean actedOnDefinedView = false;
    protected VerifyMessage[][] vcVMs;
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
    protected HashSet<Long> noCheckHistorySet = new HashSet<>();

    // NB logging
    private final int NUM_LOGGERS = 2;
    private int currentLog;
    private NBLogQueue nbq;

    private VerifierLogger logger = new VerifierLogger(this);

    private int myIndex;

    public VerifierBaseNode(String membership, String orderConfig, int myId) {
        super(membership, BFT.util.Role.VERIFIER, myId);
        myIndex = myId;
        // do whatever else is necessary

        certificates =
                new Certificate[2 * parameters.executionPipelineDepth];
        futureVMs = new VerifyMessage[parameters.executionPipelineDepth][];
        futurePs = new Prepare[parameters.executionPipelineDepth][];
        futureCs = new Commit[parameters.executionPipelineDepth][];

        for (int i = 0; i < certificates.length; i++) {
            certificates[i] = new Certificate(parameters, parameters.getExecutionCount(), parameters.getVerifierCount());
        }
        for (int i = 0; i < futurePs.length; i++) {
            futureVMs[i] = new VerifyMessage[parameters.getExecutionCount()];
            futurePs[i] = new Prepare[parameters.getVerifierCount()];
            futureCs[i] = new Commit[parameters.getVerifierCount()];
        }

        currentIndex = 0;

        // view changes
        changingView = false;
        viewChanges = new ViewChangeRow[parameters.getVerifierCount()];
        vcVMs = new VerifyMessage[2 * parameters.executionPipelineDepth][parameters.getExecutionCount()];
        vcCommits = new Commit[parameters.getVerifierCount()];
        nvCache = new NewView[viewChanges.length];
        for (int i = 0; i < viewChanges.length; i++) {
            viewChanges[i] = new ViewChangeRow(parameters);
        }

        observed = new double[parameters.getVerifierCount()];
        for (int i = 0; i < observed.length; i++) {
            observed[i] = 100000.0;
        }
        thresh = observed[0];
        observed[myIndex] = 0;
        oldTime = System.currentTimeMillis();
        viewStartTime = System.currentTimeMillis();
        scheduleViewChange = false;

        currentLog = 0;
        vbn = this;

        lastCommittedTimestamp = System.currentTimeMillis();
        logger.start();

    }

    public static VerifierBaseNode vbn;


    /**
     * Handle a byte array, convert the byte array to the appropriate
     * message and call the requisite process method
     **/
    synchronized public void handle(byte[] vmbbytes) {
        if (System.currentTimeMillis() - lastCommittedTimestamp > parameters.verifierTimeout) {
            lastCommittedTimestamp = System.currentTimeMillis();
        }
        VerifiedMessageBase vmb = MessageFactory.fromBytes(vmbbytes, parameters);
        Debug.debug(Debug.MODULE_VERIFIER, "Received message with tag: %d\n", vmb.getTag());
        switch (vmb.getTag()) {
            case MessageTags.Verify:
//                System.out.println("bak sen execlere");
                process((VerifyMessage) vmb);
                break;
            case MessageTags.Prepare:
//                System.out.println("prepare");
                process((Prepare) vmb);
                break;
            case MessageTags.Commit:
//                System.out.println("commit");
                process((Commit) vmb);
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
            case MessageTags.ConfirmView:
                process((ConfirmView) vmb);
                break;
            case MessageTags.NoOP:
                process((NoOPMessage) vmb);
                break;
            default:
                Debug.kill(new RuntimeException("verifier node does not handle message " + vmb.getTag()));
        }
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
     * -----(2) sent by the primary
     * otherwise discard it
     * (3) the next request
     * (a) if it is old then discard it
     * (b) if it is in the 'near' future then cache it
     * for later processing
     * (c) otherwise discard it
     * -----(4) cache it if we're waiting for the previous commit
     * (5) check the MAC
     * -----(6) the time/nondeterminism are consistent
     * (7) The histories check out appropriately
     * i.e. the base history is consistent with the history
     * defined by the previous sequence number
     * -----(8) it contains an appropriate order CP Digest
     * -----(a)  last request in a cp interval has the
     * about to be commited CP associated with it.
     * and that cp is stable
     * -----(b)  other sequence numbers have the
     * last committed sequence number
     * and that cp is stable
     * -----(c)  if a cp is necessary but is not yet stable,
     * then cache the preprepare
     * -----(9) Check the signatures on the request cores
     **/
    // If we're going to have a view change due to divergence, it starts in this method
    synchronized protected void process(VerifyMessage vm) {
        Debug.fine(Debug.MODULE_VERIFIER, "\tprocess verifyMessage from %d: %d/%d:%d:%d view: %d has: %s\n",
                vm.getSendingReplica(), vm.getVersionNo(), maxCommitted, maxPrepared, maxPreprepared,
                vm.getView(), vm.getHistoryAndState());

        // (1) discard if its not part of this view
        if (vm.getView() != currentView) {
            Debug.println("\tdiscarding pp not part of current view: " +
                    vm.getView() + " " + currentView);
            return;
        }

        // Could I just trick someone into changing views when they aren't supposed to?
        // (2a) if we're changing view, then cache it
        long seqno = vm.getVersionNo();
        if(seqno > maxPrepared + 1 && parameters.backendAidedVerification) {
            synchronized (noCheckHistorySet) {
                noCheckHistorySet.add(seqno);
//                System.out.println("seqNo is added: " + seqno + " mi:" + noCheckHistorySet.contains(seqno));
            }
//            System.out.println("backendAidedVerification should be false if there is no nested requests seqNo: " + seqno + " maxPrepared: " + maxPrepared);
            maxPrepared = seqno - 1;
            maxPreprepared = seqno - 1;
            maxCommitted = seqno - 1;
        }

        if (changingView) {
            int index = (int) (seqno % vcVMs.length);
            vcVMs[index][vm.getSendingReplica()] = vm;
            Debug.debug(Debug.MODULE_VERIFIER, "cached VM from %d and seqno %d while changing view\n",
                    vm.getSendingReplica(), vm.getVersionNo());
            return;
        }

//        System.out.println(1);
        if (parameters.forceRollback && vm.getVersionNo() == 1000) {
            parameters.forceRollback = false;
            doViewChange();
            return;
        }

        // (3a) Drop if it is old
        if (seqno <= maxPreprepared) {
            Debug.debug(Debug.MODULE_VERIFIER, "old verifyMessage\n");
            return;
        }

        // (3)
        if (seqno > maxCommitted +
                parameters.executionPipelineDepth) {
            // (3b) if its in the near enough future then cache it
            if (seqno < maxCommitted +
                    2 * parameters.executionPipelineDepth) {
                int ind = (int) (seqno % parameters.executionPipelineDepth);
                futureVMs[ind][vm.getSendingReplica()] = vm;
                Debug.debug(Debug.MODULE_VERIFIER, "future pp\n");
                return;
            } else {
                // (3c)TODO otherwise drop it (start a new view?)
                return;
            }
        }
//        System.out.println(1.5);
        Certificate cert = certificates[certificateIndex(seqno)];
        if (cert.getSeqNo() < maxCommitted && cert.getSeqNo() >= 0) {
//            System.out.println(1.55);
            cert.clear();
        }

//        System.out.println(1.6);
        // (3b) cache it in the near future
        if (seqno != maxPreprepared + 1) {
            cert.cacheVerify(vm);
            Debug.debug(Debug.MODULE_VERIFIER, "out of order vm\n");
            return;
        }

//        System.out.println(2);
        // (4) cache the message if we're waiting for the commit
        //TODO we should probably cache the message

        // (5) check the mac
        if (!validateExecMacArrayMessage(vm, myIndex)
                || !vm.checkAuthenticationDigest()) {
            Debug.kill("MacArray didn't validate");
            return;
        }


        // (7)  Check that the previousState matches the currentState
        // of the preceding sequence number
//        System.out.println("seqno exist: " + seqno + " mi: " + noCheckHistorySet.contains(seqno));
        if (seqno > 1 && (!parameters.backendAidedVerification || !noCheckHistorySet.contains(seqno))) {
            HistoryAndState has = certificates[certificateIndex(seqno - 1)].getHistoryAndState();
            Digest oldCurrentState = has.getState();
            if (!vm.getHistoryAndState().getHistory().equals(oldCurrentState)) {
                System.out.println("the history hash is not consistent");
                Debug.fine(Debug.MODULE_VERIFIER, "Messed up histories!: %s : %s %s",
                        vm.getHistoryAndState().getHistory(), oldCurrentState, vm);
                System.out.println("but it does not affect the progress");

            }
        }

//        System.out.println(3);

        // done with security checks. Now I will add the message to the certificate
        Debug.debug(Debug.MODULE_VERIFIER, "About to add message to a certificate with seqNo=%d!", seqno);
        if (cert.addVerify(vm)) {
//            System.out.println("normal run");
            maxPreprepared = seqno;
            actOnPreprepared(vm);
            noCheckHistorySet.remove(seqno);
        } else if (cert.isVerifyQuorumFull()) { // if is not prepreraed, check if it's full
            // WARNING TODO: the proper check would be
            // to see if any of the candidates has
            // potential to get a quorum
            System.out.println("problematic run, will do view change");
            cert.clear();
//            System.err.println("Verifier clearing");
            doViewChange();
//            System.err.println("after doViewChange");

        }

    }

    /**
     * Processes a 'cleaned' preprepare.  The initial assumption is
     * that all of the authentication on the preprepare has been
     * checked out and all contained request cores have valid
     * signatures and the primary has correctly authenticated the mac
     * <p>
     * -----(1)  construct the next batch from the prepare
     * -----(2)  add the nextbatch to the order state
     * (3) construct the prepare
     * (4)  add it to the order state
     * (5)  send the prepare out
     * (6)  flush all the cached prepares and commits for seqno
     * if they match the newly generated prepare then
     * add them to the certificate
     * (7)  If the sequence number became prepared, then act on it
     **/
    synchronized protected void actOnPreprepared(VerifyMessage vm) {
        // these first two are going to get oomphed thanks to view
        // changes and asynchrony with verification
        if (vm.getView() != currentView())
            return;
        if (vm.getVersionNo() != maxPreprepared) {
            Debug.debug(Debug.MODULE_VERIFIER, "%d is no longer the right sequence number to process!", vm.getVersionNo());
            return;
        }
        long seqno = vm.getVersionNo();
        Certificate cert =
                certificates[certificateIndex(seqno)];

        updateHeartBeat();

        // successfully added the preprepare
        boolean wasPrepped = cert.isPrepared();

        // only need to do prepare and commit on checkpointintervals
        // (3) construct the prepare
        Prepare p = new Prepare(parameters, vm.getView(), vm.getVersionNo(),
                vm.getHistoryAndState(),
                myIndex);
        authenticateVerifierMacArrayMessage(p);
        // (4) add it to the order state
        cert.addPrepare(p);
        // (5)  send it out for logging and sending
        sendToOtherVerifierReplicas(p.getBytes(), myIndex);

        Prepare pcache[] = cert.getPrepareCache();
        Commit ccache[] = cert.getCommitCache();
        if (cert.isPrepared())
            actOnPrepared(p.getSeqNo());
        // (6)  flush the cached prepares and commits
        for (int i = 0; i < pcache.length; i++) {
            if (p.matches(pcache[i])) {
                process(pcache[i]);
            }
            if (p.matches(ccache[i])) {
                process(ccache[i]);
            }
        }

        // need to process any VMs that may be cached in the next seqNo
        Certificate nextCert = certificates[certificateIndex(seqno + 1)];
        VerifyMessage vmCache[] = nextCert.getVerifyCache();
        Debug.debug(Debug.MODULE_VERIFIER, "I will now flush the vmCache of seqNo %d\n", (seqno + 1));
        for (int i = 0; i < vmCache.length; i++) {
            if (vmCache[i] != null) {
                Debug.debug(Debug.MODULE_VERIFIER, "Found a message in vmCache[%d] with SN = %d\n", i, vmCache[i].getVersionNo());
                process(vmCache[i]);
            }
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
        Debug.debug(Debug.MODULE_VERIFIER, "\t\tProcessing Prepare %d from: %d\n", p.getSeqNo(), p.getSendingReplica());
        // (1) check for the current view
        if (p.getView() != currentView || changingView) {
            return;
        }
        long seqno = p.getSeqNo();
        // discard old seqno
        // (3) if its old, discard it
        if (seqno <= maxCommitted) {
            return;
        }
        // (4) if its not next, then cache it
        if (seqno > maxCommitted +
                parameters.executionPipelineDepth) {
            // (3b) if its in the near enough future then cache it
            if (seqno < maxCommitted +
                    2 * parameters.executionPipelineDepth) {
                int ind = (int) (seqno % parameters.executionPipelineDepth);
                futurePs[ind][(int) (p.getSendingReplica())] = p;
                return;

            } else { // discard distant future
                Debug.warning(Debug.MODULE_VERIFIER, "I'm way behind.\n");
                return;
            }
        }
        // (4)  caching not yet ready
        Certificate cert =
                certificates[certificateIndex(p.getSeqNo())];
        Debug.debug(Debug.MODULE_VERIFIER, "\t\tI retrieved a certificate with seqNo %d from position %d\n",
                cert.getSeqNo(), certificateIndex(p.getSeqNo()));
        if (cert.getSeqNo() < maxCommitted && cert.getSeqNo() >= 0) {
            Debug.debug(Debug.MODULE_VERIFIER, "\t\twill clear the certificate, has previous SN in it\n");
            cert.clear();
        }
        if (!cert.isPreprepared()) {
            cert.cachePrepare(p);
            Debug.debug(Debug.MODULE_VERIFIER, "\t\tcaching a prepare from %d\n", p.getSendingReplica());
            return;
        }
        if (cert.preparedBy((int) p.getSendingReplica())) {
            Debug.debug(Debug.MODULE_VERIFIER, "\t\tseqno %d already prepared by %d\n", cert.getSeqNo(), (int) p.getSendingReplica());
            return;
        }
        if (cert.isPrepared()) {
            Debug.debug(Debug.MODULE_VERIFIER, "\t\tcert with seqno %d already prepared\n", cert.getSeqNo());
            return;
        }

        // (6)  check that it matches the corresponding preprepare
        if (!p.getHistoryAndState().equals(cert.getHistoryAndState())) {
            return;
        }
        // (7) validate authentiation
        if (!validateVerifierMacArrayMessage(p, myIndex)) {
            return;
        }
        cert.addPrepare(p);
        if (cert.isPrepared()) {
            actOnPrepared(p.getSeqNo());
        }

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
        Debug.debug(Debug.MODULE_VERIFIER, "\t\tact on prepared: %d\n", seqNo);
        Certificate cert =
                certificates[certificateIndex(seqNo)];
        // (1)  assert that the sequence number is in fact prepared
        if (!cert.isPrepared())
            Debug.kill("its not prepared!");
        if (seqNo > maxPrepared)
            maxPrepared = seqNo;

        // (2)  create the new commit
        Commit c = new Commit(parameters, currentView(), seqNo, cert.getHistoryAndState(),
                myIndex);
        authenticateVerifierMacArrayMessage(c);
        // (3) add it to the state
        cert.addCommit(c);

        // (4) send it out for logging and sending
        sendToOtherVerifierReplicas(c.getBytes(), myIndex);

        // (5) check to see if committed
        if (cert.isCommitted())
            actOnCommitted(seqNo);

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
        Debug.debug(Debug.MODULE_VERIFIER, "\t\t\tProcessing Commit %d in view %d from: %d\n",
                c.getSeqNo(), c.getView(), c.getSendingReplica());
        // (1)  check the current view
        if (c.getView() != currentView)
            return;

        long seqno = c.getSeqNo();


        if (changingView) {
            if (seqno % BFT.order.Parameters.checkPointInterval == 0)
                vcCommits[(int) c.getSendingReplica()] = c;
            return;
        }

        // (2) discard old requests
        if (seqno <= maxCommitted) {
            return;
        }
        // (3) cache mid range future requests, or requests if we're changing views
        if (seqno > maxCommitted +
                parameters.executionPipelineDepth) {
            // (3b) if its in the near enough future then cache it
            if (seqno < maxCommitted +
                    2 * parameters.executionPipelineDepth) {
                int ind =
                        (int) (seqno % parameters.executionPipelineDepth);
                futureCs[ind][(int) (c.getSendingReplica())] = c;
                return;
            } else { // discard distant future
                Debug.warning(Debug.MODULE_VERIFIER, "I'm way behind. " +
                        "received commit with SN=%d while maxCommitted is %d\n", seqno, maxCommitted);
                return;
            }
        }

        // (3) cache mid range future
        Certificate cert =
                certificates[certificateIndex(c.getSeqNo())];
        if (cert.getSeqNo() < maxCommitted && cert.getSeqNo() >= 0) {
            cert.clear();
        }
        if (!cert.isPreprepared()) {
            cert.cacheCommit(c);
            return;
        }
        // (4)  discard already committed things
        if (cert.isCommitted()) {
            // its already committed so drop it
            return;
        }
        // (5) check that it matches teh preprepare
        if (!c.getHistoryAndState().equals(cert.getHistoryAndState()))
            Debug.kill("histories dont match");
        // (6) authenticate the request
        if (!validateVerifierMacArrayMessage(c, myIndex))
            Debug.kill("invalid macarray");//return;
        // (7) if it completest he certificate, then act on it
        boolean wasPrepared = cert.isPrepared();
        cert.addCommit(c);
        if (cert.isCommitted()) {
            actOnCommitted(c.getSeqNo());
        } else if (!wasPrepared && cert.isPrepared()) {
            actOnPrepared(c.getSeqNo());
        }
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
        Debug.debug(Debug.MODULE_VERIFIER, "\t\t\tacton commmitted: %d\n", seqNo);
        lastCommittedTimestamp = System.currentTimeMillis();
        Certificate cert =
                certificates[certificateIndex(seqNo)];
        // assert that the request is committed
        if (!cert.isCommitted())
            Debug.kill("uncommitted certificate!");

        if (seqNo > maxCommitted) {
            maxCommitted = seqNo;
            Debug.debug(Debug.MODULE_VERIFIER, "Changing maxCommitted to %d\n", maxCommitted);
        }

        VerifyResponseMessage vrm = new VerifyResponseMessage(parameters, currentView(), seqNo, cert.getHistoryAndState(), false, myIndex);
        this.authenticateExecMacArrayMessage(vrm);
        Debug.info(Debug.MODULE_VERIFIER,
                "Verifier %d: Sending VRM to all Exec Replicas. view %d seqno %d\n%s\n",
                myIndex, currentView(), seqNo, vrm.toString());
        sendToAllExecutionReplicas(vrm.getBytes());

    }


    /**
     * Maintains the required throughputs
     **/
    protected double threshold(int cpCount) {
        double base = thresh / 2 / BFT.order.Parameters.baseDuration;
        base *= (cpCount - 10);
        return thresh / 2 + base;
    }

    protected void updateThreshold(double rate) {
        observed[primary(currentView)] = rate;
        thresh = 0;
        for (int i = 0; i < observed.length; i++)
            if (observed[i] > thresh)
                thresh = observed[i];
    }

    protected long heartBeat = System.currentTimeMillis();
    long max = 0;

    protected void updateHeartBeat() {
        long tmp = System.currentTimeMillis();
        if (max > 1000)
            max = -1;
        if (tmp - heartBeat > max)
            max = tmp - heartBeat;
        heartBeat = tmp;
    }

    public int minimumBatchSize() {
        boolean res = !amIPrimary() ||
                System.currentTimeMillis() - heartBeat > BFT.order.Parameters.heartBeat / 2;
        if (res) {
            activeCount = 0;
            for (int i = 0; i < active.length; i++)
                active[i] = false;
            // unpile the pp and comm
            return 1;
        } else {
            int bias = 4 + parameters.getFilterCount();
            return (activeCount + bias) / bias;
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
        // There was a disable vc flag here, but it isn't related to test.properties so I removed it
        // if someone wants it back, put it in test.properties and parameters.java


        Debug.debug(Debug.MODULE_VERIFIER, "\t\t\t\t\t\tStarting view change for %d\n", newView);
        if (currentView >= newView) {
            Debug.warning(Debug.VIEWCHANGE, "view numbers should be" +
                    " monotonically increasing! " +
                    currentView + ":" + newView);
            return;
        }

        scheduleViewChange = false;
        doViewChangeNow = false;
        setCurrentView(newView);
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
                if (viewChanges[i].getSmallestView() > currentView) {
                    count++;
                }
                if (viewChanges[i].getSmallestView() < small ||
                        small == currentView)
                    small = viewChanges[i].getSmallestView();
            }
            // if there is a future valid view, then switch again
            if (count >= parameters.smallOrderQuorumSize()
                    // if the current primary has a larger entry
                    || viewChanges[primary(small)].getLastReceived() > small) {
                currentView = small;
            } else
                // otherwise stop looking
                lookForLargerView = false;
        }

        HistoryStateSeqno[] PPtemp = new HistoryStateSeqno[parameters.executionPipelineDepth];
        int PPindex = 0;
        for (long m = maxPrepared + 1; m <= maxCommitted + parameters.executionPipelineDepth; m++) {
            Certificate cert = certificates[certificateIndex(m)];
            if (cert.getSeqNo() < maxPrepared || !cert.isPreprepared()) {
                break;
            } else {
                PPtemp[PPindex++] = new HistoryStateSeqno(parameters, cert.getHistoryAndState(), cert.getSeqNo());
            }
        }

        HistoryStateSeqno[] PPs = new HistoryStateSeqno[PPindex];
        for (int i = 0; i < PPindex; i++) {
            PPs[i] = PPtemp[i];
        }

        Debug.info(Debug.MODULE_VERIFIER, "maxPrepared = %d\n", maxPrepared);
        Certificate maxPcert = certificates[certificateIndex(maxPrepared)];
        HistoryStateSeqno maxP = new HistoryStateSeqno(parameters, maxPcert.getHistoryAndState(), maxPrepared);

        ViewChange vc = new ViewChange(parameters, newView, maxP, PPs, getMyVerifierIndex());
        this.authenticateVerifierMacArrayMessage(vc);
        Debug.debug(Debug.MODULE_VERIFIER, "Sending view change %s\n", vc.toString());
        sendToOtherVerifierReplicas(vc.getBytes(), getMyVerifierIndex());

        // create the new view information
        vInfo = new ViewInfo(parameters, currentView, vcCerts, primary(currentView) == myIndex);
        Debug.debug(Debug.MODULE_VERIFIER, "changingView set to True");
        changingView = true;
        vInfo.observeViewChange(vc);
        vInfo.setMyViewChange(vc);

        // create a vcack for my vc
        Digest d = new Digest(parameters, vc.getBytes());
        ViewChangeAck vcack = new ViewChangeAck(parameters, vc.getView(),
                vc.getSendingReplica(),
                d, myIndex);
        authenticateVerifierMacArrayMessage(vcack);
        if (!amIPrimary()) {
            sendToVerifierReplica(vcack.getBytes(), primary(currentView()));
        } else {
            vInfo.addViewChangeAck(vcack);
            if (vInfo.getNewViewMessage() == null) {
                NewView nv = vInfo.generateNewViewMessage(this);
                if (nv != null) {
                    if (!vInfo.definedView()) {
                        Debug.kill("Primary better have a defined view if it creates a newview msg");
                    } else {
                        actOnDefinedView();
                    }
                }
            }
        }
        //System.out.println("Finished view change!!!");
        Debug.debug(Debug.MODULE_VERIFIER, "Finished view change!!!");
    }

    synchronized protected void setCurrentView(long newView) {
        currentView = newView;
    }


    synchronized protected void process(ViewChange vc) {
        Debug.debug(Debug.VIEWCHANGE,
                "\t\t\t\t\t\tprocessing view change for view %d from %d\n", vc.getView(), vc.getSendingReplica());

        Debug.debug(Debug.MODULE_VERIFIER, "VC: %s\n", vc);

        // (1) if the view is old, or smaller than the last received
        // from the sender, or its for the current view but we're done
        // view changing already, then dont do anything.
        if (vc.getView() < currentView
                || (vc.getView() <
                viewChanges[(int) vc.getSendingReplica()].getLastReceived())
                || (vc.getView() == currentView && !changingView)) {
            Debug.debug(Debug.MODULE_VERIFIER, "discarding on the first criteria!");
            return;  // discard deprecated messages
        }
        if (!validateVerifierMacArrayMessage(vc, myIndex))
            Debug.kill("invalid maccarray");//return; // discard messages with bad authentication

        Digest d = new Digest(parameters, vc.getBytes());
        ViewChangeAck vcack = new ViewChangeAck(parameters, vc.getView(),
                vc.getSendingReplica(),
                d, myIndex);
        authenticateVerifierMacArrayMessage(vcack);

        // mark that somebody started a vc so we know to prepare
        somebodyVotedForVC = true;

        if (primary(vc.getView()) != myIndex)
            sendToVerifierReplica(vcack.getBytes(), primary(vcack.getView()));

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
                        else {
                            actOnDefinedView();
                        }
                    }
                }
            } else if (vInfo.definedView()) {
                actOnDefinedView();
                return; // view is defined, so we clearly dont need to
                // do anything as a result of hte view change
            }
        } else {
            Debug.debug(Debug.MODULE_VERIFIER, "\t\t\t future view change\n");

            // the view change is for something in the future
            ViewChangeRow vcr = viewChanges[(int) vc.getSendingReplica()];
            vcr.observeViewChange(vc,
                    primary(vc.getView()) == myIndex);
            // if im the primary record the ack
            if (primary(vc.getView()) == myIndex) {
                vcr.addViewChangeAck(vcack);
            }

            // if there are f+1 distinct replicas advocating a view
            // larger than currentView, then go to the smallest of
            // those views
            long small = currentView;
            int count = 0;
            for (int i = 0; i < viewChanges.length; i++) {
                if (viewChanges[i].getSmallestView() > currentView)
                    count++;
                if ((viewChanges[i].getSmallestView() < small ||
                        (small == currentView))
                        && viewChanges[i].getSmallestView() > currentView)
                    small = viewChanges[i].getSmallestView();
            }
            if (count >= parameters.smallVerifierQuorumSize()
                    || viewChanges[primary(currentView)].getSmallestView() >= small) {
                System.out.println("starting the view change b/c it" +
                        " came in the mail. New view will be " + small);
                startViewChange(small);
            }
        }

    }

    synchronized protected void process(ViewChangeAck vcack) {
        Debug.debug(Debug.VIEWCHANGE, "\t\t\t\t\t\tVCAck for %d from %d\n",
                vcack.getChangingReplica(), vcack.getSendingReplica());

        Debug.debug(Debug.MODULE_VERIFIER, "vcack: %s\n", vcack);

        if (vcack.getView() < currentView)
            return;
        else if (primary(vcack.getView()) != myIndex)
            Debug.kill("wrong view");// only the primary needs vcacks
        else if (!validateVerifierMacArrayMessage(vcack, myIndex))
            Debug.kill("invalid msg");// reject bad macs
        else if (vcack.getView() > currentView) {
            Debug.info(Debug.MODULE_VERIFIER, "caching the vcack from %d for a future view %d\n",
                    vcack.getChangingReplica(), vcack.getView());
            viewChanges[(int) (vcack.getChangingReplica())].addViewChangeAck(vcack);
        } else if (vcack.getView() == currentView) {
            Debug.fine(Debug.MODULE_VERIFIER, "processing the vack for this view\n");
            vInfo.addViewChangeAck(vcack);
            if (vInfo.getNewViewMessage() != null) {
                Debug.fine(Debug.MODULE_VERIFIER, "already did the new view thing\n");
            } else {
                NewView nv = vInfo.generateNewViewMessage(this);
                if (nv != null) {
                    actOnDefinedView();
                }
            }
        } else
            Debug.kill("Should never reach this point!");

    }


    synchronized protected void process(NewView nv) {
        Debug.fine(Debug.VIEWCHANGE,
                "\t\t\t\t\t\tprocessing new view for %d from %d\n",
                nv.getView(), nv.getSendingReplica());

        if (nv.getView() < currentView())
            return;

        if (!validateVerifierMacArrayMessage(nv, myIndex))
            Debug.kill("invalid macarray");

        if (nv.getView() > currentView()) {
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
            }
        }
    }

    /**
     * The purpose of this function is to ensure that the replica has
     * all of the state specified by the new view message.
     **/

    protected boolean requestedThings;

    synchronized protected void actOnDefinedView() {
        Debug.fine(Debug.VIEWCHANGE, "\t\t\t\t\t\tactOnDefinedView()\n");
        if (!vInfo.definedView()) {
            Debug.kill("vInfo should be defined");
        }
        actOnCompleteView();
    }

    /**
     * Once the defined view is entirely present,
     **/

    synchronized protected void actOnCompleteView() {
        Debug.fine(Debug.VIEWCHANGE, "\t\t\t\t\t\tactOnCompleteView()\n");
        if (!vInfo.isComplete())
            Debug.kill("ERROR.  should only come in here with a view that is complete!");

        // if i've already sent a confirmation, no need to do it again
        if (vInfo.confirmedBy(myIndex)) {
            Debug.debug(Debug.MODULE_VERIFIER, "ive already confirmed\n");
            return;
        }

        if (amIPrimary()) {
            Debug.debug(Debug.VIEWCHANGE, "I AM THE PRIMARY! %d", myIndex);
            NewView nv = vInfo.getNewViewMessage();
            if (nv == null)
                Debug.kill("Should never be null");
            sendToOtherVerifierReplicas(nv.getBytes(), myIndex);
        }

        ConfirmView cv = new ConfirmView(parameters, currentView, vInfo.getHSS(), myIndex);
        Debug.debug(Debug.MODULE_VERIFIER, "ConfirmView: %s\n", cv);
        authenticateVerifierMacArrayMessage(cv);
        vInfo.addConfirmation(cv);
        // AGC there is likely a bunch of shit here that should be written to disc
        sendToOtherVerifierReplicas(cv.getBytes(), myIndex);
        vInfo.flushConfirmedCache();

        if (vInfo.isConfirmed())
            actOnConfirmedView();

    }

    synchronized protected void process(ConfirmView cv) {
        Debug.fine(Debug.VIEWCHANGE,
                "\t\t\t\t\t\t process confirm view from %d for view: %d at seqno: %d\n",
                cv.getSendingReplica(), cv.getView(), cv.getHistoryStateSeqno().getSeqno());

        if (cv.getView() < currentView)
            return;

        if (vInfo.isConfirmed())
            return;

        if (!validateVerifierMacArrayMessage(cv, myIndex)) {
            Debug.kill("verification should never fail");
            return;
        }

        if (cv.getView() != currentView) {
            Debug.warning(Debug.VIEWCHANGE,
                    "\t\t\t\t\t\tnot dealing with out of order confirm views yet. currentView: %d confirmView: %d\n",
                    currentView, cv.getView());
            viewChanges[(int) cv.getSendingReplica()].addConfirmView(cv,
                    amIPrimary(cv.getView()));
            // and i think thats it?  though it might entail doing the
            // 'view change' check as well -- i.e. process(view
            // change) looks to see if this message will trigger
            // another future vc
            return;
        }

        vInfo.addConfirmation(cv);
        if (vInfo.definedView() && vInfo.isComplete() && vInfo.isConfirmed()) {
            actOnConfirmedView();
        }
    }


    synchronized protected void actOnConfirmedView() {
        Debug.fine(Debug.VIEWCHANGE, "\t\t\t\t\t\tactOnConfirmedView\n");
        if (!vInfo.isConfirmed()) {
            Debug.kill("view should be confirmed if you're getting here");
        }

        changingView = false;
        // mark everything as committed


        // reset the throughput thresholds
        runningCount = 0;
        viewTotal = 0;
        oldTime = System.currentTimeMillis();
        viewStartTime = oldTime;
        cpCount = 0;
        scheduleViewChange = false;
        updateHeartBeat();
        somebodyVotedForVC = false;
        // reset the active count
        activeCount = 0;
        HistoryStateSeqno newstate = vInfo.getHSS();
        maxCommitted = newstate.getSeqno();
        maxPrepared = maxCommitted;
        maxPreprepared = maxCommitted;

        clearCertificates();
        Certificate cert = certificates[certificateIndex(maxCommitted)];
        HistoryAndState currentHAS = new HistoryAndState(parameters, newstate.getHistory(), newstate.getState());
        cert.preprepare(currentHAS);
        cert.setSeqNo(maxCommitted);
        cert.forcePrepared();
        cert.forceCommitted();

        VerifyResponseMessage vrm = new VerifyResponseMessage(parameters, currentView, maxCommitted, currentHAS, true, myIndex);
        this.authenticateExecMacArrayMessage(vrm);
        Debug.info(Debug.MODULE_VERIFIER,
                "actOnConfirmedView() Verifier %d: Sending VRM to all Exec Replicas. view %d seqno %d\n",
                myIndex, currentView, maxCommitted);
        sendToAllExecutionReplicas(vrm.getBytes());

        // unpile the pp and commit caches
        for (int i = 0; i < vcVMs.length; i++) {
            for (int j = 0; j < vcVMs[i].length; j++) {
                if (vcVMs[i][j] != null) {
                    process(vcVMs[i][j]);
                    vcVMs[i][j] = null;
                }
            }
        }


        for (int i = 0; i < vcCommits.length; i++) {
            if (vcCommits[i] != null) {
                process(vcCommits[i]);
                vcCommits[i] = null;
            }
        }
        System.out.println("starting view " + currentView + " @ " +
                vInfo.getHSS().getSeqno());
    }


    /**************
     Utility management
     **************/

    /**
     * Returns a mapping of a sequence number to an index in the
     * certificate list
     **/
    protected int certificateIndex(long seqNo) {
        return (int) (seqNo % (2 * parameters.executionPipelineDepth));
    }

    /**
     * Return the index of the primary for the current view
     **/
    protected int primary(long viewNo) {
        return (int) (viewNo % parameters.getVerifierCount());
    }

    protected boolean amIPrimary() {
        return amIPrimary(currentView);
    }

    protected boolean amIPrimary(long view) {
        return primary(view) == myIndex;
    }

    protected long currentView() {
        return currentView;
    }

    // ** LOGGING METHODS **

    public void setNBQueue(NBLogQueue nbq) {
        this.nbq = nbq;
    }


    // *********************

    @Override
    public void start() {
        super.start();
    }


    public static void main(String[] args) {

        if (args.length < 2) {
            Debug.println("Usage: java Applications.EchoClient <id> <config_file>");
            System.exit(0);
        }
        //Security.addProvider(new de.flexiprovider.core.FlexiCoreProvider());
        VerifierBaseNode vbn = new VerifierBaseNode(args[1], "", Integer.parseInt(args[0]));

        // the sending network
        NettyTCPSender sendNet = new NettyTCPSender(vbn.getParameters(), vbn.getMembership(), 1);
        vbn.setNetwork(sendNet);


        // "service" processing
        Role[] roles = new Role[2];
        roles[0] = Role.EXEC;
        roles[1] = Role.VERIFIER;

        PassThroughNetworkQueue ptnwq = new PassThroughNetworkQueue(vbn);
        NettyTCPReceiver receiveNet = new NettyTCPReceiver(roles,
                vbn.getMembership(),
                ptnwq,
                1);

        // and start the orderbasenode
        vbn.start();
    }

    /**
     *
     **/
    public int getMyVerifierIndex() {
        return myIndex;
    }

    private void clearCertificates() {
        for (int i = 0; i < certificates.length; i++) {
            certificates[i].clear();
        }
    }

    public synchronized void doViewChange() {
        Debug.debug(Debug.MODULE_VERIFIER, "In doViewChange()");
        //System.out.println("in doViewChange");
        if (parameters.getVerifierCount() > 1) {
            startViewChange(currentView + 1);
        } else {
            setCurrentView(currentView + 1);
            HistoryAndState has = null;
            byte[] bits = new byte[10];
            if (maxCommitted == 0) {
                has = new HistoryAndState(parameters, new Digest(parameters, bits), new Digest(parameters, bits));
            } else {
                has = certificates[certificateIndex(maxCommitted)].getHistoryAndState();
                clearCertificates();
                Certificate cert = certificates[certificateIndex(maxCommitted)];
                cert.preprepare(has);
                cert.setSeqNo(maxCommitted);
                cert.forcePrepared();
                cert.forceCommitted();
            }

            VerifyResponseMessage vrm = new VerifyResponseMessage(parameters, currentView,
                    maxCommitted, has, true, myIndex);
            this.authenticateExecMacArrayMessage(vrm);
            Debug.info(Debug.MODULE_VERIFIER,
                    "Verifier %d: Quorum full. Sending VRM to all Exec Replicas. view %d seqno %d\n%s\n",
                    myIndex, currentView, (maxCommitted), vrm.toString());
            sendToAllExecutionReplicas(vrm.getBytes());
        }
    }

    protected synchronized void process(NoOPMessage noOPMessage) {
        Debug.debug(Debug.VIEWCHANGE, "\tprocess verifyMessage from %d\n", noOPMessage.getSender());
    }

}
