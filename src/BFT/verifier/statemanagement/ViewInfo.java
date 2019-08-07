// $Id: ViewInfo.java 166 2010-04-04 17:27:16Z aclement $

package BFT.verifier.statemanagement;

import BFT.Debug;
import BFT.Parameters;
import BFT.messages.Digest;
import BFT.messages.Quorum;
import BFT.verifier.VerifierBaseNode;
import BFT.verifier.messages.*;

/**
 * Contains the state describing the new view..
 **/
public class ViewInfo {

    protected ViewChangeAck[][] pendingAcks;
    protected ViewChangeCertificate[] viewChanges;
    protected NewView newViewMsg;
    protected Quorum<ConfirmView> viewConfirmations;
    protected ConfirmView[] cvCache;
    protected ViewChange myViewChange;

    protected Certificate[][] certs;
    ///protected Digest[][] certDigests;
    ///protected HistoryDigest[][] histories;

    protected long viewNo;
    protected long CPSeqNo; // sequence number of first request post checkpoint

    protected boolean primary;

    protected boolean missingViews[];

    protected boolean isCommitted;

    protected DigestId[] chosenVCs;
    protected HistoryStateSeqno historyStateSeqno;

    protected Parameters parameters;

    ///protected Vector<TreeRoot> trees;

    public ViewInfo(Parameters param, long view, ViewChangeCertificate[] vcerts, boolean prim) {
        parameters = param;
        this.primary = prim;
        viewNo = view;
        viewChanges = vcerts;
        if (vcerts.length != parameters.getVerifierCount())
            Debug.kill(new RuntimeException("problem in creating a " +
                    "viewinfo object"));
        pendingAcks = new ViewChangeAck[vcerts.length][];
        missingViews = new boolean[vcerts.length];
        chosenVCs = new DigestId[parameters.largeVerifierQuorumSize()];
        ///trees = new Vector<TreeRoot>();

        for (int i = 0; i < viewChanges.length; i++) {
            if (viewChanges[i] == null) {
                viewChanges[i] = new ViewChangeCertificate(parameters, view, primary);
            }
            pendingAcks[i] = new ViewChangeAck[vcerts.length];
        }

        viewConfirmations =
                new Quorum<ConfirmView>(parameters.getVerifierCount(),
                        parameters.largeVerifierQuorumSize(),
                        0);
        for (int i = 0; i < viewChanges.length; i++)
            if (viewChanges[i].getConfirmView() != null)
                viewConfirmations.addEntry(viewChanges[i].getConfirmView());
        cvCache = new ConfirmView[parameters.getVerifierCount()];

        ///certs = new Certificate[];
        ///certDigests = new Digest[certs.length][];
        ///histories = new HistoryDigest[certs.length][];

        ///for (int i = 0; i < certs.length; i++){
        ///certs[i] = new Certificate[BFT.order.Parameters.checkPointInterval];
        ///histories[i] = new HistoryDigest[certs[i].length];
        ///certDigests[i] = new Digest[certs[i].length];
        ///for (int j = 0; j < certs[i].length; j++){
        ///boolean takecp = j == certs[i].length -1 ;
        ///certs[i][j] = new Certificate(BFT.Parameters.getOrderCount(), takecp);
        ///histories[i][j] = null;
        ///certDigests[i][j] = null;
        ///}
        ///}

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
            if (newViewMsg == null || (newViewMsg.getChosenVCs()[index] != null
                    && newViewMsg.getChosenVCs()[index].getDigest().equals(new Digest(parameters, vc.getBytes())))) {
                viewChanges[index].observeViewChange(vc);
                missingViews[index] = false;
                boolean res = false;
                //if (newViewMsg != null && newViewMsg.getChosenVCs()[index] != null)
                //    populateVCTrees(vc);
                for (int i = 0; i < missingViews.length; i++)
                    res = res || missingViews[i];
                if (!res && newViewMsg != null)
                    defineView(true);
            } else {
                return;
            }
        } else
            Debug.kill(new RuntimeException("should have a certificate for " +
                    vc.getSendingReplica()));

        if (primary)
            for (int i = 0; i < pendingAcks.length; i++)
                if (pendingAcks[index][i] != null) {
                    addViewChangeAck(pendingAcks[index][i]);
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
        if (nv.getView() != viewNo)
            Debug.kill(new RuntimeException("bad view"));
        //clearTrees();
        if (viewDefined)
            Debug.kill(new RuntimeException("already have a new view"));
        else {
            newViewMsg = nv;
            defineView(true);

            /*** This code implements the correctness check for accepting the
             *   NewView message. It should be enabled eventually

             newViewMsg = nv;
             DigestId[] digestIds = nv.getChosenVCs();
             int count = 0;
             for (int i = 0; i < digestIds.length; i++){
             int replicaId = digestIds[i].getId();
             Digest digest = digestIds[i].getDigest();

             //if (digestIds[i] == null)
             //    viewChanges[i].clear();
             Digest vcd = viewChanges[replicaId].getVCDigest();
             if (vcd == null || !digest.equals(vcd)){
             viewChanges[i].clear();
             if (digestIds[i] == null)
             missingViews[i] = false;
             else{
             missingViews[i] = true;
             count++;
             }
             }else{
             viewChanges[i].markFinal();
             missingViews[i] = false;
             //populateVCTrees(viewChanges[i].getViewChange());
             }
             }
             if (count == 0)
             defineView(true);
             * */
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
        if (cv.getView() != viewNo)
            Debug.kill("views should match");

        if (cv.getHistoryStateSeqno().equals(historyStateSeqno))
            viewConfirmations.addEntry(cv);
        else
            Debug.kill("confirm view does not match! " + cv.getHistoryStateSeqno() + " " + historyStateSeqno);
    }

    public void flushConfirmedCache() {
        for (int i = 0; i < cvCache.length; i++) {
            if (cvCache[i] != null) {
                addConfirmation(cvCache[i]);
            }
        }
    }


    public boolean isConfirmed() {
        return newViewMsg != null && viewConfirmations.isComplete();
    }

    public boolean confirmedBy(int i) {
        return viewConfirmations.containsEntry(i);
    }


    /*************************************************/
    /**  Generate the new view message              **/

    /**
     * Sets viewDefined to true if the view can be adequately defined
     * (i.e. cp and request numbers and hashes)
     **/
    boolean viewDefined = false;

    public NewView generateNewViewMessage(VerifierBaseNode vbn) {
        Debug.debug(Debug.MODULE_VERIFIER, "I will try to generate a NewView message\n");

        // only the primary generates new view messages
        if (!primary)
            return null;

        if (newViewMsg != null)
            return newViewMsg;

        int count = 0;
        Digest[] vcd = new Digest[viewChanges.length];
        for (int i = 0; i < viewChanges.length; i++) {
            Debug.debug(Debug.VIEWCHANGE, "View change certificate %d\n", i);
            viewChanges[i].printAcks();
            if (viewChanges[i].hasAcks()) {
                vcd[i] = viewChanges[i].getVCDigest();
                ///populateVCTrees(viewChanges[i].getViewChange());
                count++;
            }
        }
        /*byte[] tmp = new byte[10];
        Digest tmpDigest = new Digest(tmp);
        DigestId[] chosenvcs = new DigestId[3];
        for(int i=0; i<3; i++) {
            chosenvcs[i] = new DigestId(tmpDigest, i);
        }
        HistoryStateSeqno hss = new HistoryStateSeqno(tmpDigest, tmpDigest, 22);
        */
        if (count < parameters.largeVerifierQuorumSize()) {
            Debug.debug(Debug.MODULE_VERIFIER, "\t\t\t\t\tNot enough acked vcs to form a NewView\n");
            return null; // not enough view changes
        } else {
            Debug.debug(Debug.MODULE_VERIFIER, "\t\t\t\t\tI have enough acked vcs to form a NewView\n");
            defineView(false);
            newViewMsg = new NewView(parameters, viewNo, chosenVCs, getHSS(), vbn.getMyVerifierIndex());
            vbn.authenticateVerifierMacArrayMessage(newViewMsg);
            Debug.debug(Debug.MODULE_VERIFIER, "NewView message: %s\n", newViewMsg);
            return newViewMsg;
        }

        // for now, do the simple thing and make sure that we keep the
        // largest view change message


        //System.err.println("New View Definition needs to be fixed!");
        //System.err.println("we're currently cheating by using the maximal VC "+
        //		   " out of 2f+1 that we have received.");

        /**********
         int min = -1;
         long max = -1;
         ViewChange vc = null;
         while (count > BFT.Parameters.largeVerifierQuorumSize()){
         for (int i = 0; i < vcd.length; i++){
         if (count > BFT.Parameters.largeVerifierQuorumSize()){
         vc = viewChanges[i].getViewChange();
         if (vc != null)
         if (vc.getPPSeqNo() <= max)
         min = i;
         else
         max = vc.getPPSeqNo();
         }
         }
         vcd[min] = null;
         min = -1;
         }


         newViewMsg = new NewView(viewNo, vcd, vbn.getMyVerifierIndex(),
         BFT.Parameters.largeVerifierQuorumSize());
         vbn.authenticateVerifierMacArrayMessage(newViewMsg);

         //	defineView(root, subroot);
         defineView(null, null, false);
         return newViewMsg;
         ***********************/
    }

    public HistoryStateSeqno getHSS() {
        return historyStateSeqno;
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

    /*
    public boolean setCertificate(Certificate cert, long seqno){
	if (seqno < CPSeqNo)
	    Debug.kill(seqno +" should be greater than "+ CPSeqNo);
	int index = (int) (seqno - CPSeqNo);
	CertificateEntry entry = cert.getCertEntry();
	if (entry != null
	    && certs[in1(index)][in2(index)].getCertEntry() == null
	    && entry.getDigest().equals(certDigests[in1(index)][in2(index)])
	    && entry.getHistoryDigest().equals(histories[in1(index)][in2(index)])){
	    certs[in1(index)][in2(index)].clear();
	    certs[in1(index)][in2(index)] = cert;
	    return true;
	}
	return false;
    }


    public CertificateEntry getEntry(long seqno){
	if (seqno < CPSeqNo)
	    return null;
	if (seqno -CPSeqNo > certs.length)
	    return null;
	int index = (int) (seqno - CPSeqNo);
	return certs[in1(index)][in2(index)].getCertEntry();
    }

    public Certificate[] getEntries(int index){
	if (0 > index || 1 < index)
	    Debug.kill("invalid entry index");
	return certs[index];
    }
    */


    public boolean isComplete() {

        if (!definedView()) {
            return false;
        }

        return true;
    }

    /*******************************************************************************/
    /**
     * utility functions for populting the vc trees and identifying the new view
     **/


    protected void defineView(boolean notPrimary) {

        long max = -1L;
        int ind = -1;
        int chosenIndex = 0;
        for (int i = 0; i < viewChanges.length; i++) {
            ViewChange vc = viewChanges[i].getViewChange();
            if (vc != null && (notPrimary || viewChanges[i].hasAcks())) {
                if (chosenIndex < parameters.largeVerifierQuorumSize()) {
                    chosenVCs[chosenIndex++] = new DigestId(parameters, viewChanges[i].vcDigest, i);
                }
                if (vc.getMaxPrepared().getSeqno() > max) {
                    ind = i;
                    max = vc.getMaxPrepared().getSeqno();
                }
            }
        }

        if (chosenIndex != parameters.largeVerifierQuorumSize()) {
            Debug.warning(Debug.MODULE_VERIFIER, "Invalid number of chosenVCs: %d\n", chosenIndex);
        }

        historyStateSeqno = viewChanges[ind].getViewChange().getMaxPrepared();
        if (historyStateSeqno == null) {
            Debug.kill("null maxPrepared HSS");
        }

        viewDefined = true;
    }

    /*
    protected void defineView(boolean notPrimary){
	boolean test = false;
	for (int i = 0; i < missingViews.length; i++)
	    test = test || missingViews[i];
	if (test)
	    Debug.kill("should not be definign a view if we're still missing view changes");

	int ind = -1;
	long max = -1;
	int count = 0;
	for(int i = 0; i < viewChanges.length; i++){
	    ViewChange vc = viewChanges[i].getViewChange();
	    if (vc != null && (notPrimary || viewChanges[i].hasAcks())){
		if (vc.getPPSeqNo() > max){
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
	if (stableCPDigest.equals(committedCPDigest))
	    stableCPDigest = null;
	nextSeqNo = vc.getPPSeqNo();



	HistoryDigest[] hist = vc.getHistories();
	Digest[] dig = vc.getBatchHashes();
	for (int i = 0; i < histories.length * histories[0].length; i++){
	    if (i < hist.length){
		histories[in1(i)][in2(i)] = hist[i];
		certDigests[in1(i)][in2(i)] = dig[i];
	    }else{
		histories[in1(i)][in2(i)] = null;
		certDigests[in1(i)][in2(i)] = null;
	    }
	}

	// if we have two full checkpoint intervals of requests, then
	// the new view should start with only one such interval
	if (nextSeqNo == CPSeqNo+2*BFT.order.Parameters.checkPointInterval){
	    CPSeqNo = CPSeqNo + BFT.order.Parameters.checkPointInterval;
	    committedCPDigest = stableCPDigest;
	    stableCPDigest = null;
	    histories[0] = histories[1];
	    certDigests[0] = certDigests[1];
	    histories[1] = new HistoryDigest[histories[0].length];
	    certDigests[1] = new Digest[histories[0].length];
	    if (committedCPDigest == null)
		Debug.kill("we've got a problem with a full view change");
	    
	}

	
	viewDefined = true;
    }*/

}


