/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package BFT.verifier;

import BFT.Debug;

import java.util.TimerTask;

/**
 * @author manos
 */
class ViewChangeTask extends TimerTask {

    VerifierBaseNode vbn;

    public ViewChangeTask(VerifierBaseNode node) {
        this.vbn = node;
    }

    @Override
    public void run() {
        Debug.info(Debug.VIEWCHANGE, "Timed out, triggering view change. Current view is %d\n", vbn.currentView);
        vbn.doViewChange();
    }
}
