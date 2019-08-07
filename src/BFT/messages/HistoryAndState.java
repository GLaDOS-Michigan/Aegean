/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package BFT.messages;

import BFT.Parameters;
import util.UnsignedTypes;

/**
 * @author manos
 */
public class HistoryAndState {
    private Digest history;
    private Digest state;
    private Parameters parameters;

    public HistoryAndState(Parameters param, Digest hd, Digest sd) {
        parameters = param;
        history = hd;
        state = sd;
    }

    public int getSize() {
        return parameters.digestLength + parameters.digestLength;
    }

    public Digest getHistory() {
        return history;
    }

    public Digest getState() {
        return state;
    }

    @Override
    public boolean equals(Object obj) {
        HistoryAndState target = (HistoryAndState) obj;
        return this.history.equals(target.history) && this.state.equals(target.state);
    }

    @Override
    public String toString() {
        return "<" + UnsignedTypes.bytesToHexString(getHistory().getBytes()) +
                ", " + UnsignedTypes.bytesToHexString(getState().getBytes()) + ">";
    }

    @Override
    public int hashCode() {
        return history.hashCode() + state.hashCode();
    }
}
