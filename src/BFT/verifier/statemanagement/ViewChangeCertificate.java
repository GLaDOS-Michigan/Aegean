// $Id: ViewChangeCertificate.java 166 2010-04-04 17:27:16Z aclement $

package BFT.verifier.statemanagement;

import BFT.Debug;
import BFT.Parameters;
import BFT.messages.Digest;
import BFT.verifier.messages.ConfirmView;
import BFT.verifier.messages.ViewChange;
import BFT.verifier.messages.ViewChangeAck;

/**
 * The View change certificate gathers a view change message and the
 * associated pieces required to make it 'valid'.
 * <p>
 * A view change message is valid if either
 * (a) the process verified the view change message itself or
 * (b) the process has a quorum of view change ack messages that
 * validate the view change message
 **/
public class ViewChangeCertificate {

    protected ViewChange vc;
    protected ViewChangeAck[] vca;
    protected ViewChangeAck[] ackCache;
    protected ConfirmView confirmView;
    protected Digest vcDigest;
    protected long viewNumber;
    protected boolean primary;
    protected Parameters parameters;

    public ViewChangeCertificate(Parameters param, long view, boolean primary) {
        parameters = param;
        viewNumber = view;
        vc = null;
        vca = new ViewChangeAck[param.getVerifierCount()];
        ackCache = new ViewChangeAck[vca.length];
        confirmView = null;
        vcDigest = null;
        this.primary = primary;
    }


    /**
     * If I dont have a view change yet, add this one to the
     * certificate
     **/
    public void observeViewChange(ViewChange vc2) {
        addViewChange(vc2);
    }


    /**
     * Add the view change to the certificate.  throws an exception
     * if the view does not match.  if the view change matches the
     * digest, then keep the other data if it does not, then wipe the
     * other data and proceed.
     **/
    protected void addViewChange(ViewChange vc2) {
        if (vc2.getView() != viewNumber) {
            throw new RuntimeException("cant add vc for " + vc2.getView() + " to cert for " + viewNumber);
        }
        Digest tmp = new Digest(parameters, vc2.getBytes());
        if (!tmp.equals(vcDigest)) {
            clear();
        }
        vc = vc2;
        vcDigest = tmp;
        for (int i = 0; i < ackCache.length; i++) {
            if (ackCache[i] != null) {
                addAck(ackCache[i]);
            }
        }
    }

    public void addAck(ViewChangeAck vcack) {
        if (!primary) {
            throw new RuntimeException("only the primary accepts vc acks");
        }
        if (vcack.getView() != viewNumber) {
            throw new RuntimeException("cant add vc for " + vcack.getView() + " to cert for " + viewNumber);
        }
        if (vcDigest == null) {
            ackCache[(int) (vcack.getSendingReplica())] = vcack;
            return;
        }
        if (vc.getSendingReplica() != vcack.getChangingReplica()) {
            throw new RuntimeException("vcack not for the right replica");
        }
        if (vcack.getVCDigest().equals(vcDigest)) {
            vca[(int) (vcack.getSendingReplica())] = vcack;
            int count = 0;
            for (int i = 0; i < vca.length; i++)
                if (vca[i] != null)
                    count++;
            Debug.debug(Debug.VIEWCHANGE, "Added vcack, non null VCA count = %d\n", count);
        } else {
            throw new RuntimeException("something wrong with vcack");
        }
    }

    public ViewChangeAck getViewChangeAck(int sender) {
        return vca[sender];
    }

    public void addConfirmView(ConfirmView cv) {
        if (cv.getView() != viewNumber) {
            Debug.kill("view numbers should match");
        }
        confirmView = cv;
    }

    public ConfirmView getConfirmView() {
        return confirmView;
    }

    public ViewChange getViewChange() {
        return vc;
    }

    boolean finished = false;

    public void markFinal() {
        finished = true;
    }

    public boolean isFinal() {
        return finished;
    }

    public long getView() {
        return viewNumber;
    }

    public Digest getVCDigest() {
        return vcDigest;
    }

    boolean hasAcks = false;

    public boolean hasAcks() {
        if (hasAcks) {
            return hasAcks;
        }
        int count = 0;
        for (int i = 0; i < vca.length; i++) {
            if (vca[i] != null) {
                count++;
            }
        }
        hasAcks = vc != null && count >= parameters.largeVerifierQuorumSize();
        return hasAcks;
    }

    public void printAcks() {
        //Debug.debug(Debug.VIEWCHANGE, "Printing acks for VCCertificate, view = %d\n", viewNumber);
        for (int i = 0; i < vca.length; i++) {
            if (vca[i] != null) {
                Debug.debug(Debug.VIEWCHANGE, "X");
            } else {
                Debug.debug(Debug.VIEWCHANGE, "O");
            }
        }
        Debug.debug(Debug.VIEWCHANGE, "\n");
    }

    public void clear() {
        vcDigest = null;
        finished = false;
        vc = null;
        hasAcks = false;
        for (int i = 0; i < vca.length; i++)
            vca[i] = null;
    }

}