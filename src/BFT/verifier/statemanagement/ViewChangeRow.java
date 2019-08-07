// $Id: ViewChangeRow.java 57 2010-02-27 22:19:03Z yangwang $

package BFT.verifier.statemanagement;

import BFT.Debug;
import BFT.Parameters;
import BFT.verifier.messages.ConfirmView;
import BFT.verifier.messages.ViewChange;
import BFT.verifier.messages.ViewChangeAck;

public class ViewChangeRow {

    long smallestView; // smallest view > last one returned that this
    // row contains information for
    long lastReceived; // maximum view that this row has received
    // information related to
    long lastRemoved;
    ViewChangeCertificate[] viewChanges;
    ViewChangeAck[] pendingAcks;
    Parameters parameters;

    public ViewChangeRow(Parameters param) {
        parameters = param;
        smallestView = 0;
        lastRemoved = 0;
        viewChanges = new ViewChangeCertificate[parameters.getVerifierCount()];
        pendingAcks = new ViewChangeAck[parameters.getVerifierCount()];
    }


    /**
     * Add a view change to the row.
     * <p>
     * the view change is only added if it is for a view larger than
     * the current view held for the specified new primary.
     * <p>
     * After adding a view change message, it pulls over any pending
     * vcacks as aprpopriate.
     * <p>
     * Updates the smallestView value as appropriate.
     **/
    public void observeViewChange(ViewChange vc, boolean amprimary) {
        //System.out.println("**Observing view change: "+vc.getView()+" from "+
        //		   vc.getSendingReplica());
        int index = index(vc.getView());
        if (vc.getView() < lastReceived)
            return;
        lastReceived = vc.getView();
        if (viewChanges[index] == null ||
                viewChanges[index].getView() < vc.getView())
            viewChanges[index] =
                    new ViewChangeCertificate(parameters, vc.getView(), amprimary);
        if (viewChanges[index].getViewChange() != null) {
            //System.out.println("already have a view change!");
            return;
        }
        viewChanges[index].observeViewChange(vc);
        for (int i = 0; i < pendingAcks.length; i++) {
            if (pendingAcks[i] != null &&
                    pendingAcks[i].getView() == vc.getView()) {
                viewChanges[index].addAck(pendingAcks[i]);
                pendingAcks[i] = null;
            }
        }
        //System.out.println("old small view: "+smallestView);
        //System.out.println("vc view: "+vc.getView());
        if (smallestView == lastRemoved || vc.getView() < smallestView) {
            smallestView = vc.getView();
        }
        //System.out.println("new small view: "+smallestView);
    }

    /**
     * Adds a view change Ack to the row.
     * <p>
     * If the view change ack is for the a certificat that currently
     * has a view change, then it is added to that certificate.
     * <p>
     * If the view change ack is more recent than the last view
     * change ack sent by the sending replica, then replace the old
     * one and cache the current view change ack
     **/
    public void addViewChangeAck(ViewChangeAck vca) {
        int index = index(vca.getView());
        int send = index(vca.getSendingReplica());
        if (viewChanges[index] != null &&
                viewChanges[index].getView() == vca.getView()) {
            Debug.debug(Debug.VIEWCHANGE, "This certificate (view " + vca.getView() + ")has a view change. Adding ack");
            viewChanges[index].addAck(vca);
        } else if (pendingAcks[send] == null ||
                pendingAcks[send].getView() < vca.getView()) {
            Debug.debug(Debug.VIEWCHANGE, "This certificate (view " + vca.getView() +
                    ") does NOT have a VC. Adding pending ack for sender " + send);
            pendingAcks[send] = vca;
        }
    }

    public void addConfirmView(ConfirmView cv, boolean amprimary) {
        int index = index(cv.getView());
        if (cv.getView() < lastReceived)
            return;
        lastReceived = cv.getView();
        if (viewChanges[index] == null || cv.getView() > viewChanges[index].getView()) {
            viewChanges[index] = new ViewChangeCertificate(parameters, cv.getView(), amprimary);
        }
        viewChanges[index].addConfirmView(cv);

        //System.out.println("old small view: "+smallestView);
        //System.out.println("my view: "+cv.getView());
        if (smallestView == lastRemoved || cv.getView() < smallestView)
            smallestView = cv.getView();
        //System.out.println("new small view: "+smallestView);

    }

    /**
     * Return view changecertificate for view.
     * <p>
     * Returns null if no such certificate exists.
     * <p>
     * If a non-null value is returned, then future calls will
     * return null
     **/
    public ViewChangeCertificate getCertificate(long view) {
        int index = index(view);

        ViewChangeCertificate vc = viewChanges[index];
        if (vc == null || vc.getView() != view)
            vc = null;
        else
            viewChanges[index] = null;

        lastRemoved = view;
        if (smallestView <= view) {
            long tmp = view;
            for (int i = 0; i < viewChanges.length; i++) {
                if (viewChanges[i] != null
                        && (viewChanges[i].getView() < tmp
                        || tmp == view))
                    if (viewChanges[i].getView() <= smallestView)
                        BFT.Debug.kill(new RuntimeException("something is horribly wrong!"));
                    else
                        tmp = viewChanges[i].getView();
            }
            smallestView = tmp;
        }
        //if (vc != null)
        //System.out.println("pulled out vccert with "+vc.getView()+" leaving us with smallest of "+smallestView);
        return vc;
    }


    public long getSmallestView() {
        return smallestView;
    }

    public long getLastReceived() {
        return lastReceived;
    }

    /**
     * get the index into the viewchanges array
     **/
    protected int index(long sn) {
        return (int) (sn % parameters.getVerifierCount());
    }
}

