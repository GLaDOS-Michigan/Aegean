// $Id: ViewInfo.java 396 2010-06-02 00:03:43Z aclement $

package BFT.order.statemanagement;

import Applications.tpcw_webserver.rbe.args.Arg;
import BFT.Debug;
import BFT.Parameters;
import BFT.messages.CertificateEntry;
import BFT.messages.Digest;
import BFT.messages.HistoryDigest;
import BFT.messages.Quorum;
import BFT.order.OrderBaseNode;
import BFT.order.messages.*;
import BFT.order.statemanagement.viewchangetree.TreeNode;
import BFT.order.statemanagement.viewchangetree.TreeRoot;

import java.util.Iterator;
import java.util.Vector;

/**
 * Contains the state describing the new view..
 **/
public class ViewInfo {

    protected ViewChangeAck[][] pendingAcks;
    protected ViewChangeCertificate[] viewChanges;
    protected NewView newViewMsg;
    protected Quorum<ConfirmView> viewConfirmations;
    protected Quorum<CommitView> viewCommits;
    protected ConfirmView[] cvCache;
    protected CommitView[] comCache;
    protected ViewChange myViewChange;

    protected Certificate[][] certs;
    protected Digest[][] certDigests;
    protected HistoryDigest[][] histories;
    protected CheckPointState committedCP;
    protected CheckPointState stableCP;
    protected CheckPointState workingState;
    protected Digest committedCPDigest;
    protected Digest stableCPDigest;

    protected long viewNo;
    protected long CPSeqNo; // sequence number of first request post checkpoint
    protected long nextSeqNo;  // the next sequence number to be ordered


    protected boolean primary;

    protected boolean missingViews[];


    protected boolean isCommitted;

    protected Vector<TreeRoot> trees;
    protected Parameters parameters;


    public ViewInfo(Parameters param, long view, ViewChangeCertificate[] vcerts, boolean prim) {

        this.parameters = param;
        this.primary = prim;
        viewNo = view;
        viewChanges = vcerts;
        if (vcerts.length != parameters.getOrderCount()) {
            Debug.kill(new RuntimeException("problem in creating a viewinfo object"));
        }
        pendingAcks = new ViewChangeAck[vcerts.length][];
        missingViews = new boolean[vcerts.length];
        trees = new Vector<TreeRoot>();

        for (int i = 0; i < viewChanges.length; i++) {
            if (viewChanges[i] == null) {
                viewChanges[i] = new ViewChangeCertificate(parameters, view, primary);
            }
            pendingAcks[i] = new ViewChangeAck[vcerts.length];
        }

        viewConfirmations =
                new Quorum<ConfirmView>(parameters.getOrderCount(),
                        parameters.largeOrderQuorumSize(),
                        0);
        viewCommits =
                new Quorum<CommitView>(parameters.getOrderCount(),
                        parameters.largeOrderQuorumSize(),
                        0);


        cvCache = new ConfirmView[parameters.getOrderCount()];
        comCache = new CommitView[cvCache.length];

        for (int i = 0; i < viewChanges.length; i++) {
            cvCache[i] = viewChanges[i].getConfirmView();
            comCache[i] = viewChanges[i].getCommitView();
        }


        certs = new Certificate[BFT.order.Parameters.maxPeriods][];
        certDigests = new Digest[certs.length][];
        histories = new HistoryDigest[certs.length][];

        for (int i = 0; i < certs.length; i++) {
            certs[i] = new Certificate[BFT.order.Parameters.checkPointInterval];
            histories[i] = new HistoryDigest[certs[i].length];
            certDigests[i] = new Digest[certs[i].length];
            for (int j = 0; j < certs[i].length; j++) {
                boolean takecp = j == certs[i].length - 1;
                certs[i][j] = new Certificate(parameters, parameters.getOrderCount(), takecp);
                histories[i][j] = null;
                certDigests[i][j] = null;
            }
        }

        stableCP = null;
        committedCP = null;
        stableCPDigest = null;
        committedCPDigest = null;

        isCommitted = false;
    }


    public void setMyViewChange(ViewChange vc) {
        myViewChange = vc;
    }

    public ViewChange getMyViewChange() {
        return myViewChange;
    }


    /**
     * Observe that a view change is for this view.
     **/
    public void observeViewChange(ViewChange vc) {
        if (viewDefined)
            return;
        if (vc.getView() != viewNo)
            Debug.kill(new RuntimeException("bad view"));
        int index = (int) (vc.getSendingReplica());
        if (viewChanges[index] != null) {
            if (newViewMsg == null
                    || (newViewMsg.getChosenVCs()[index] != null
                    && newViewMsg.getChosenVCs()[index].equals(new Digest(parameters, vc.getBytes())))) {
                viewChanges[index].observeViewChange(vc);
                missingViews[index] = false;
                boolean res = false;
                if (newViewMsg != null && newViewMsg.getChosenVCs()[index] != null) {
                    populateVCTrees(vc);
                }
                for (int i = 0; i < missingViews.length; i++) {
                    res = res || missingViews[i];
                }
                if (!res && newViewMsg != null) {
//                    System.err.println("defineView is called in observeViewChange");
                    defineView(true);
                }
            } else {
                return;
            }
        } else {
            Debug.kill(new RuntimeException("should have a certificate for " + vc.getSendingReplica()));
        }

        if (primary) {
            for (int i = 0; i < pendingAcks.length; i++) {
                if (pendingAcks[index][i] != null) {
                    addViewChangeAck(pendingAcks[index][i]);
                }
            }
        }

    }


    /**
     * return the viewchange from the specified replica
     **/
    public ViewChange getViewChange(long rep) {
        return viewChanges[(int) rep].getViewChange();
    }

    /**
     * add a vcack, but only if the primary for the view
     **/
    public void addViewChangeAck(ViewChangeAck vca) {
        if (!primary)
            Debug.kill(new RuntimeException("only the primary" +
                    " accepts vc acks"));
        if (viewDefined)
            return;

        if (vca.getView() != viewNo)
            Debug.kill(new RuntimeException("bad view"));

        int index = (int) (vca.getChangingReplica());
        if (viewChanges[index].getViewChange() != null) {
            viewChanges[index].addAck(vca);
        } else {
            pendingAcks[index][(int) (vca.getSendingReplica())] = vca;
        }
    }

    public ViewChangeAck getViewChangeAck(int sendingReplica, int ackFrom) {
        return viewChanges[sendingReplica].getViewChangeAck(ackFrom);
    }

    /**
     * Add the new view message
     **/
    public void addNewView(NewView nv) {
        if (nv.getView() != viewNo) {
            Debug.kill(new RuntimeException("bad view"));
        }
        clearTrees();
        if (viewDefined) {
            Debug.kill(new RuntimeException("already have a new view"));
        } else {
            newViewMsg = nv;
            Digest[] d = nv.getChosenVCs();
            int count = 0;
            for (int i = 0; i < viewChanges.length; i++) {
                if (d[i] == null) {
                    viewChanges[i].clear();
                }
                Digest vcd = viewChanges[i].getVCDigest();
                if (vcd == null || !d[i].equals(vcd)) {
                    viewChanges[i].clear();
                    if (d[i] == null) {
                        missingViews[i] = false;
                    } else {
                        missingViews[i] = true;
                        count++;
                    }
                } else {
                    viewChanges[i].markFinal();
                    missingViews[i] = false;
                    populateVCTrees(viewChanges[i].getViewChange());
                }
            }
            if (count == 0) {
//                System.err.println("defineView is called in addNewView");
                defineView(true);
            }
        }
    }


    public NewView getNewViewMessage() {
        return newViewMsg;
    }


    /**
     * Add the ConfirmView message
     **/
    public void addConfirmation(ConfirmView cv) {
        if (!viewDefined) {
            //System.out.println("caching the confirm view");
            cvCache[(int) cv.getSendingReplica()] = cv;
            return;
        }
        if (cv.getView() != viewNo) {
            Debug.kill("views should match");
        }
        if (cv.getSeqNo() != getNextSeqNo()) {
            Debug.kill("sequence numbers should match");
        }
        int index = (int) (cv.getSeqNo() - CPSeqNo);
        if (index > 2 * histories[0].length) {
            Debug.kill("invalid sequence number!");
        }

        // Either cv.getNextSeqNo has a null history or is the last index
        // in the CP interval
        if (index == -1
                || (histories[in1(index - 1)][in2(index - 1)] != null
                && (index == 2 * histories[0].length
                || histories[in1(index)][in2(index)] == null))) {
            viewConfirmations.addEntry(cv);
        } else {
            Debug.kill("confirm view does not match!");
        }
    }

    public void flushConfirmedCache() {
        for (int i = 0; i < cvCache.length; i++) {
            if (cvCache[i] != null) {
                addConfirmation(cvCache[i]);
            }
        }
    }

    public void addCommitView(CommitView cv) {
        if (!isConfirmed()) {
            comCache[(int) cv.getSendingReplica()] = cv;
            return;
        }
        if (cv.getView() != viewNo) {
            Debug.kill("views should match");
        }
        if (cv.getSeqNo() != getNextSeqNo()) {
            Debug.kill("sequence numbers should match: " + cv.getSeqNo() + " " + getNextSeqNo());
        }
        viewCommits.addEntry(cv);
    }

    public void flushCommitCache() {
        for (int i = 0; i < comCache.length; i++) {
            if (comCache[i] != null) {
                addCommitView(comCache[i]);
            }
        }
    }


    public boolean isConfirmed() {
        return newViewMsg != null && viewConfirmations.isComplete();
    }

    public boolean confirmedBy(int i) {
        return viewConfirmations.containsEntry(i);
    }

    public boolean isCommitted() {
        return viewCommits.isComplete();
    }


    /*************************************************/
    /**  Generate the new view message              **/

    /**
     * Sets viewDefined to true if the view can be adequately defined
     * (i.e. cp and request numbers and hashes)
     **/
    boolean viewDefined = false;

    public NewView generateNewViewMessage(OrderBaseNode obn) {


        // only the primary generates new view messages
        if (!primary)
            return null;

        if (newViewMsg != null)
            return newViewMsg;

        long cpSeqNo = -1;
        long ppSeqNo = -1;

        int count = 0;
        Digest[] vcd = new Digest[viewChanges.length];
        for (int i = 0; i < viewChanges.length; i++) {
            if (viewChanges[i].hasAcks()) {
                vcd[i] = viewChanges[i].getVCDigest();
                populateVCTrees(viewChanges[i].getViewChange());
                count++;
            }
        }

        if (count < parameters.largeOrderQuorumSize()) {
            return null; // not enough view changes
        }


        // 	// identify a valid cp set
        // 	Iterator<TreeRoot> it = trees.iterator();
        // 	TreeRoot root = null;
        // 	TreeRoot subroot = null;
        // 	while(it.hasNext()){
        // 	    TreeRoot tmproot = it.next();
        // 	    if (tmproot.getSequenceNumber() > cpSeqNo
        // 		&& tmproot.getCommittedSupportCount() > 0
        // 		&& (tmproot.getCommittedSupportCount() +
        // 		    tmproot.getStableSupportCount()
        // 		    >= parameters.smallOrderQuorumSize())){
        // 		root = tmproot;
        // 		cpSeqNo = tmproot.getSequenceNumber();
        // 	    }
        // 	    if (tmproot.getSequenceNumber() > cpSeqNo
        // 		&& tmproot.getCommittedSupportCount() == 0
        // 		&& (tmproot.getStableSupportCount() >=
        // 		    parameters.smallOrderQuorumSize()))
        // 		subroot = tmproot;
        // 	}
        // 	if (root == null)
        // 	    return null; // no committed checkpoint meets the requirements

        // 	Digest vcd[] = new Digest[viewChanges.length];
        // 	boolean stab[] = root.getStableSupport();
        // 	boolean com[] = root.getCommittedSupport();
        // 	for (int i = 0; i < vcd.length; i++){
        // 	    if (stab[i] || com[i]){
        // 		vcd[i] = viewChanges[i].getVCDigest();
        // 		if (vcd[i] == null)
        // 		    Debug.kill("got a null view change from"+
        // 			       " somebody supporitn a cp");
        // 		com[i] = stab[i] || com[i];
        // 	    }
        // 	}


        // 	// identify a valid prepare set
        // 	ppSeqNo = cpSeqNo -1;
        // 	Iterator<TreeNode> it2 = root.getChildren();
        // 	TreeNode node = null;
        // 	while(it2.hasNext()){
        // 	    TreeNode tmpnode = it2.next();
        // 	    if (tmpnode.getSupportCount() >
        // 		parameters.smallOrderQuorumSize()){
        // 		node = tmpnode;
        // 		if (ppSeqNo +1 != node.getVersionNo())
        // 		    Debug.kill("out of order in the batch list?");
        // 		ppSeqNo = node.getVersionNo();
        // 		it2 = node.getChildren();
        // 	    }
        // 	}

        // 	if (node != null)
        // 	    com = node.getSupport();

        // 	count =0;
        // 	for (int i = 0; i < vcd.length; i++){
        // 	    if (!com[i]
        // 		|| (viewChanges[i].getViewChange() != null
        // 		    && ppSeqNo < viewChanges[i].getViewChange().getPSeqNo()))
        // 		vcd[i] = null;
        // 	    else if (com[i]) count++;
        // 	}

        // 	if (count < parameters.smallOrderQuorumSize())
        // 	    return null;

        // 	boolean keepTrying = true;
        // 	for(int i = 0;
        // 	    i < vcd.length && count < parameters.largeOrderQuorumSize();
        // 	    i++){
        // 	    if (vcd[i] == null && viewChanges[i].getViewChange() != null){
        // 		ViewChange vc = viewChanges[i].getViewChange();
        // 		if (vc.getCommittedCPSeqNo() <= cpSeqNo &&
        // 		    vc.getPSeqNo() <= ppSeqNo){
        // 		    count++;
        // 		    vcd[i] = viewChanges[i].getVCDigest();
        // 		} // if (vc.getcomm
        // 	    }// if (vcd...
        // 	} // for

        // for now, do the simple thing and make sure that we keep the
        // largest view change message

//        try{
//            throw new Exception();
//        }
//        catch (Exception e) {
//            e.printStackTrace();
//        }
//        System.err.println("New View Definition needs to be fixed!");
//        System.err.println("we're currently cheating by using the maximal VC " +
//                " out of 2f+1 that we have received.");

        int min = -1;
        long max = -1;
        ViewChange vc = null;
        while (count > parameters.largeOrderQuorumSize()) {
            for (int i = 0; i < vcd.length; i++) {
                if (count > parameters.largeOrderQuorumSize()) {
                    vc = viewChanges[i].getViewChange();
                    if (vc != null) {
                        if (vc.getPPSeqNo() <= max) {
                            min = i;
                        } else {
                            max = vc.getPPSeqNo();
                        }
                    }
                }
            }
            vcd[min] = null;
            min = -1;
        }


        newViewMsg = new NewView(parameters, viewNo, vcd, obn.getMyOrderIndex(), parameters.largeOrderQuorumSize());
        obn.authenticateOrderMacArrayMessage(newViewMsg);

        //	defineView(root, subroot);
        defineView(null, null, false);
        return newViewMsg;
    }


    public boolean definedView() {
        return viewDefined;
    }

    /**
     * Retrieve the set of missing view changes
     **/
    public boolean[] getMissingViewChanges() {
        return missingViews;
    }

    /**
     * retrieve the list of missing checkpoints
     **/
    public long[] getMissingCPs() {
        long[] cpIndex = new long[2];
        if (committedCP == null) {
            cpIndex[0] = CPSeqNo;
        }
        if (stableCP == null && stableCPDigest != null) {
            cpIndex[1] = CPSeqNo + BFT.order.Parameters.checkPointInterval;
        }
        return cpIndex;
    }

    /**
     * add a checkpoint state that was relayed to me
     **/
    public void addCheckPointState(CheckPointState cps) {
        // verify that cps matches the cp for the new view
        Debug.kill("not yet implemented");
    }

    /**
     * add checkpoints that i already have locally
     **/
    public boolean addMyCP(CheckPointState cps) {
        int index = -1;
        // committed seqno
        if (cps.getCurrentSequenceNumber() == CPSeqNo) {
            // if we dont ahve a committed cp
            //	    System.out.println("trying to add the committed cp");
            if (committedCP == null &&
                    // and the candidate matches the stable digest
                    committedCPDigest.equals(cps.getDigest())) {
                // then add the cp as committed cp
                committedCP = cps;
                //System.out.println("adding as the committed CP");
                //		System.out.println("success");
                return true;
            } else if (committedCP == null) {
                // 		System.out.println("failed to add committed cp");
                // 		System.out.println(committedCP);
                // 		System.out.println(committedCPDigest);
                // 		System.out.println(cps.getDigest());
                //		BFT.Debug.kill("WTF");

            }
        } else { // stable seqno
            // add the stable checkpoint if the histories match
            if (cps.getCurrentSequenceNumber() == CPSeqNo + BFT.order.Parameters.checkPointInterval) {
                //		System.out.println("Trying to add the stable cp");
                //System.out.println(stableCP);
                //System.out.println(stableCPDigest);
                //System.out.println(cps.getStableDigest());
                //System.out.println(histories[0][histories[0].length-1]);
                //System.out.println(cps.getHistory());

                // if we dont have a stable cp yet
                if (stableCP == null
                        // and the digest is null
                        && (stableCPDigest == null
                        // or the digest equals the candidate digest
                        || stableCPDigest.equals(cps.getDigest()))
                        // and the histories match the sequence
                        && histories[0][histories[0].length - 1].equals(cps.getHistory())) {
                    // then add the cp as stable cp
                    stableCP = cps;
                    //System.out.println("adding as the stable cp");
                    //		    System.out.println("success!");
                    return true;
                } else if (stableCP == null) {
                    System.out.println("stable cp not added");
                }
            }
        }
        //System.out.println("not adding");
        return false;
    }


    /**
     * retrieve the set of missing requests
     **/
    public boolean[] getMissingRequests() {
        boolean[] miss = new boolean[2 * certs[0].length];
        for (int i = 0; i < miss.length; i++) {
            if (certs[in1(i)][in2(i)].getCertEntry() == null
                    && certDigests[in1(i)][in2(i)] != null) {
                miss[i] = true;
            } else {
                miss[i] = false;
            }
        }
        return miss;
    }

    /**
     * add a request.  returns true if it is successfully added, false otherwise
     **/
    public boolean addEntry(CertificateEntry entry, long seqno) {
        if (seqno < CPSeqNo) {
            Debug.kill(seqno + " should be greater than " + CPSeqNo);
        }
        int index = (int) (seqno - CPSeqNo);
        if (entry != null
                && certs[in1(index)][in2(index)].getCertEntry() == null
                && entry.getDigest().equals(certDigests[in1(index)][in2(index)])
                && entry.getHistoryDigest().equals(histories[in1(index)][in2(index)])) {
            certs[in1(index)][in2(index)].setEntry(entry, seqno);
            return true;
        }
        return false;
    }

    public boolean setCertificate(Certificate cert, long seqno) {
        if (seqno < CPSeqNo) {
            Debug.kill(seqno + " should be greater than " + CPSeqNo);
        }
        int index = (int) (seqno - CPSeqNo);
        CertificateEntry entry = cert.getCertEntry();
        if (entry != null
                && certs[in1(index)][in2(index)].getCertEntry() == null
                && entry.getDigest().equals(certDigests[in1(index)][in2(index)])
                && entry.getHistoryDigest().equals(histories[in1(index)][in2(index)])) {
            certs[in1(index)][in2(index)].clear();
            certs[in1(index)][in2(index)] = cert;
            return true;
        }
        return false;
    }


    public CertificateEntry getEntry(long seqno) {
        if (seqno < CPSeqNo) {
            return null;
        }
        if (seqno - CPSeqNo > certs.length) {
            return null;
        }
        int index = (int) (seqno - CPSeqNo);
        return certs[in1(index)][in2(index)].getCertEntry();
    }

    public Certificate[] getEntries(int index) {
        if (0 > index || 1 < index) {
            Debug.kill("invalid entry index");
        }
        return certs[index];
    }


    public boolean isComplete() {
        boolean res = false;

        if (!definedView()) {
            //System.out.println("not defined");
            return false;
        }

        // the committed checkpoint is locally stable
        res = committedCP != null && committedCP.isStable();
        if (!res) {
            //System.out.println("committed cp not ready");
            return false;
        }
        // the stable checkpoint is locally stable
        res = stableCPDigest == null || (stableCP != null && stableCP.isStable());
        if (!res) {
            //System.out.println("stable cp not ready");
            return false;
        }
        // check that we have all of the requisite pieces.
        for (int i = 0; i < nextSeqNo - CPSeqNo; i++) {
            if (certDigests[in1(i)][in2(i)] != null
                    && certs[in1(i)][in2(i)].getCertEntry() == null) {
                //System.out.println("Missing a certificateentry");
                return false;
            }
        }
        return true;
    }


    public long getBaseSequenceNumber() {
        return CPSeqNo;
    }

    public CheckPointState getCommittedCP() {
        return committedCP;
    }

    public Digest getCommittedCPDigest() {
        return committedCPDigest;
    }


    public CheckPointState getStableCP() {
        return stableCP;
    }

    public Digest getStableCPDigest() {
        return stableCPDigest;
    }

    public HistoryDigest getStableHistory() {
        return histories[0][histories[0].length - 1];
    }

    public CheckPointState getWorkingState() {
        if (workingState != null) {
            return workingState;
        }
        workingState = new CheckPointState(getStableCP());
        long base = workingState.getBaseSequenceNumber();
        for (int i = 0; i + base < getNextSeqNo(); i++) {
            workingState.addNextBatch(certs[1][i].getCertEntry(), i + base);
        }

        if (workingState.getCurrentSequenceNumber() != getNextSeqNo()) {
            Debug.kill("misalignment!");
        }

        return workingState;
    }

    public long getNextSeqNo() {
        return nextSeqNo;
    }

    public HistoryDigest getLastHistory() {
        int ind = (int) (nextSeqNo - (CPSeqNo + 1));
        if (ind > 0) {
            return histories[in1(ind)][in2(ind)];
        } else {
            return committedCP.getHistory();
        }
    }


    protected int in1(int i) {
        if (i >= 2 * BFT.order.Parameters.checkPointInterval) {
            Debug.kill("invalid index " + i);
        }
        return i / BFT.order.Parameters.checkPointInterval;
    }

    protected int in2(int i) {
        if (i >= 2 * BFT.order.Parameters.checkPointInterval) {
            Debug.kill("invalid index " + i);
        }
        return i % BFT.order.Parameters.checkPointInterval;
    }


    /*******************************************************************************/
    /**
     * utility functions for populting the vc trees and identifying the new view
     **/


    protected void defineView(TreeRoot root, TreeRoot subroot,
                              boolean notPrimary) {
        if (BFT.order.Parameters.maxPeriods != 2) {
            Debug.kill("maxperiods must == 2");
        }

        boolean test = false;
        for (int i = 0; i < missingViews.length; i++) {
            test = test || missingViews[i];
        }
        if (test) {
            Debug.kill("should not be definign a view if we're still missing view changes");
        }

        int ind = -1;
        long max = -1;
        int count = 0;
        for (int i = 0; i < viewChanges.length; i++) {
            ViewChange vc = viewChanges[i].getViewChange();
            if (vc != null && (notPrimary || viewChanges[i].hasAcks())) {
                if (vc.getPPSeqNo() > max) {
                    ind = i;
                    max = vc.getPPSeqNo();
                }
                count++;
            }
        }

        //System.out.println("looked at "+count+" vcs");

        ViewChange vc = viewChanges[ind].getViewChange();
        committedCPDigest = vc.getCommittedCPHash();
        CPSeqNo = vc.getCommittedCPSeqNo();
        stableCPDigest = vc.getStableCPHash();
        if (stableCPDigest.equals(committedCPDigest)) {
            stableCPDigest = null;
        }
        nextSeqNo = vc.getPPSeqNo();

        HistoryDigest[] hist = vc.getHistories();
        Digest[] dig = vc.getBatchHashes();
        for (int i = 0; i < histories.length * histories[0].length; i++) {
            if (i < hist.length) {
                histories[in1(i)][in2(i)] = hist[i];
                certDigests[in1(i)][in2(i)] = dig[i];
            } else {
                histories[in1(i)][in2(i)] = null;
                certDigests[in1(i)][in2(i)] = null;
            }
        }

        // if we have two full checkpoint intervals of requests, then
        // the new view should start with only one such interval
        if (nextSeqNo == CPSeqNo + 2 * BFT.order.Parameters.checkPointInterval) {
            CPSeqNo = CPSeqNo + BFT.order.Parameters.checkPointInterval;
            committedCPDigest = stableCPDigest;
            stableCPDigest = null;
            histories[0] = histories[1];
            certDigests[0] = certDigests[1];
            histories[1] = new HistoryDigest[histories[0].length];
            certDigests[1] = new Digest[histories[0].length];
            if (committedCPDigest == null) {
                Debug.kill("we've got a problem with a full view change");
            }
        }


        viewDefined = true;

        // 	committedCPDigest = root.getDigest();
        // 	TreeNode node = null;
        // 	TreeNode parent = null;
        // 	Iterator<TreeNode> it = root.getChildren();
        // 	int i = 0;
        // 	while(it.hasNext()){
        // 	    node = it.next();
        // 	    if (node.getSupportCount() >=
        // 		parameters.smallOrderQuorumSize()){
        // 		parent = node;
        // 		it = node.getChildren();
        // 		certDigests[in1(i)][in2(i)] = node.getDigest();
        // 		histories[in1(i)][in2(i)] = node.getHistory();
        // 		i++;
        // 	    }
        // 	}
        // 	if (subroot != null){
        // 	    stableCPDigest = subroot.getDigest();
        // 	    it = subroot.getChildren();
        // 	    i = (int)(subroot.getSequenceNumber() - root.getSequenceNumber());
        // 	    if (i != BFT.order.Parameters.checkPointInterval)
        // 		Debug.kill("invalid subroot and root!");
        // 	    while (it.hasNext()){
        // 		node = it.next();
        // 		if (node.getSupportCount() >=
        // 		    parameters.smallOrderQuorumSize()){
        // 		    parent = node;
        // 		    it = node.getChildren();
        // 		    if (!certDigests[in1(i)][in2(i)].equals(node.getDigest()) ||
        // 			!histories[in1(i)][in2(i)].equals(node.getHistory()))
        // 			Debug.kill("stable requests should match committed requests");
        // 		    i++;
        // 		}
        // 	    }
        // 	} else subroot = root;

        // 	//System.out.println("View is valid");
        // 	this.CPSeqNo = root.getSequenceNumber();
        // 	if (parent != null)
        // 	    nextSeqNo = parent.getSequenceNumber()+1;
        // 	else
        // 	    nextSeqNo = subroot.getSequenceNumber();
        // 	viewDefined = true;
        // 	for ( i = 0; i < cvCache.length; i++){
        // 	    if (cvCache[i] != null){
        // 		addConfirmation(cvCache[i]);
        // 		//System.out.println("$$ flushing the cv cache: "+i);
        // 	    }

        // 	}
        // 	Iterator<TreeRoot> it2 = trees.iterator();
        // 	while(it2.hasNext())
        // 	    //System.out.println(it2.next());
    }


    protected void defineView(boolean notPrimary) {
        Iterator<TreeRoot> it = trees.iterator();
        TreeRoot root = null;
        long cpSeqNo = -1;
        TreeRoot subroot = null;

//        System.err.println("need to fix definition of view changes. currently " +
//                "defaulting to the maximal VC out of 2f+1 that we " +
//                " have received");

        // 	while(it.hasNext()){
        // 	    TreeRoot tmproot = it.next();
        // 	    if (tmproot.getSequenceNumber() > cpSeqNo
        // 		&& tmproot.getCommittedSupportCount() > 0
        // 		&& (tmproot.getCommittedSupportCount() +
        // 		    tmproot.getStableSupportCount()
        // 		    >= parameters.smallOrderQuorumSize())){
        // 		root = tmproot;
        // 		cpSeqNo = tmproot.getSequenceNumber();
        // 	    }
        // 	    if (tmproot.getSequenceNumber() > cpSeqNo
        // 		&& tmproot.getCommittedSupportCount() == 0
        // 		&& (tmproot.getStableSupportCount() >=
        // 		    parameters.smallOrderQuorumSize()))
        // 		subroot = tmproot;
        // 	}

        //	defineView(root, subroot);
        defineView(null, null, notPrimary);
    }


    protected void populateVCTrees(ViewChange vc) {
        // using a simpler NV definition for now to get all of the
        // functionality in place
        if (true)
            return;

        // populate the view change trees
        //System.out.println("populating trees with vc from "+vc.getSendingReplica());

        if (vc == null) {
            return;
        }


        if (primary || (newViewMsg != null
                && new Digest(parameters, vc.getBytes()).equals(newViewMsg.getChosenVCs()[(int) (vc.getSendingReplica())]))) {

            Iterator<TreeRoot> it = trees.iterator();
            TreeRoot root = null;
            boolean com = false;
            boolean stab = false;
            while (it.hasNext()) {
                root = it.next();
                if (root.matchesCommitted(vc)) {
                    root.addCommittedSupport((int)
                            (vc.getSendingReplica()));
                    TreeNode node = null;
                    Digest p[] = vc.getBatchHashes();
                    HistoryDigest h[] = vc.getHistories();
                    int k = 0;
                    int send = (int) (vc.getSendingReplica());
                    if (p.length > 0 && vc.getPPSeqNo() >= root.getSequenceNumber()) {
                        node = root.getChild(p[k], h[k]);
                        node.addSupport(send);
                    }
                    for (k = 1; k < p.length; k++) {
                        node = node.getChild(p[k], h[k]);
                        node.addSupport(send);
                    }
                    com = true;
                } else if (root.matchesStable(vc)) {
                    root.addStableSupport((int) (vc.getSendingReplica()));
                    TreeNode node = null;
                    Digest p[] = vc.getBatchHashes();
                    HistoryDigest h[] = vc.getHistories();
                    int k = (int) (vc.getStableCPSeqNo() - vc.getCommittedCPSeqNo());
                    int send = (int) (vc.getSendingReplica());
                    if (p.length > 0
                            && vc.getPPSeqNo() >= root.getSequenceNumber()) {
                        node = root.getChild(p[k], h[k]);
                        node.addSupport(send);
                        k++;
                    }
                    for (; k < p.length; k++) {
                        node = node.getChild(p[k], h[k]);
                        node.addSupport(send);
                    }
                    stab = true;
                }
            }
            if (root == null) {
                if (vc.getStableCPSeqNo() == vc.getCommittedCPSeqNo())
                    // just add the committed root
                    trees.add(new TreeRoot(vc, true, parameters.getOrderCount()));

                else {
                    // add both roots
                    trees.add(new TreeRoot(vc, true, parameters.getOrderCount()));
                    trees.add(new TreeRoot(vc, false, parameters.getOrderCount()));
                }
                populateVCTrees(vc);
            } else if (!com) {
                // i missed the committed root
                trees.add(new TreeRoot(vc, true, parameters.getOrderCount()));
            } else if (!stab) {
                //missed the stable root, and stable != committed
                if (vc.getCommittedCPSeqNo() != vc.getStableCPSeqNo()) {
                    trees.add(new TreeRoot(vc, false, parameters.getOrderCount()));
                }
            } else if (com && stab) {
                ;// expected outcomes complete
            } else {
                Debug.kill(new RuntimeException("OOPS"));
            }
        } else {
            ;
        }
    }


    protected void clearTrees() {
        trees = new Vector<TreeRoot>();
    }

}


