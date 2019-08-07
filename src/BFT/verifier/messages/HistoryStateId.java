/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package BFT.verifier.messages;

import BFT.Parameters;
import BFT.messages.Digest;
import BFT.messages.HistoryAndState;
import BFT.messages.HistoryDigest;
import BFT.util.UnsignedTypes;

/**
 * @author manos
 */
public class HistoryStateId {
    private Digest history;
    private Digest state;
    private int id;
    private Parameters parameters;

    public HistoryStateId(Parameters param, Digest hd, Digest sd, int _id) {
        parameters = param;
        history = hd;
        state = sd;
        id = _id;
    }

    public HistoryStateId(Parameters param, HistoryAndState has, int _id) {
        parameters = param;
        history = has.getHistory();
        state = has.getState();
        id = _id;
    }

    public HistoryStateId(byte[] bytes, Parameters param) {
        //System.out.println("bytes length = "+bytes.length);
        parameters = param;
        int offset = 0;
        byte[] tmp = new byte[parameters.digestLength];
        System.arraycopy(bytes, offset, tmp, 0, parameters.digestLength);
        offset += parameters.digestLength;
        history = Digest.fromBytes(parameters, tmp);

        System.arraycopy(bytes, offset, tmp, 0, parameters.digestLength);
        offset += parameters.digestLength;
        state = Digest.fromBytes(parameters, tmp);

        byte[] tmp2 = new byte[MessageTags.uint16Size];
        System.arraycopy(bytes, offset, tmp2, 0, MessageTags.uint16Size);
        offset += MessageTags.uint16Size;
        id = UnsignedTypes.bytesToInt(tmp2);

    }

    public static int size(Parameters param) {
        return param.digestLength + param.digestLength + MessageTags.uint16Size;
    }

    public int size() {
        return parameters.digestLength + parameters.digestLength + MessageTags.uint16Size;
    }

    public Digest getHistory() {
        return history;
    }

    public Digest getState() {
        return state;
    }

    public int getId() {
        return id;
    }

    public byte[] getBytes() {
        byte[] ret = new byte[size()];

        int offset = 0;
        System.arraycopy(history.getBytes(), 0, ret, offset, parameters.digestLength);
        offset += parameters.digestLength;
        System.arraycopy(state.getBytes(), 0, ret, offset, parameters.digestLength);
        offset += parameters.digestLength;
        System.arraycopy(UnsignedTypes.intToBytes(id), 0, ret, offset, MessageTags.uint16Size);
        offset += MessageTags.uint16Size;

        return ret;
    }

    @Override
    public boolean equals(Object obj) {
        HistoryStateId target = (HistoryStateId) obj;
        return this.history.equals(target.history) && this.state.equals(target.state) && this.id == target.id;
    }

    @Override
    public String toString() {
        return "<" + UnsignedTypes.bytesToHexString(getHistory().getBytes()) + ", " +
                UnsignedTypes.bytesToHexString(getState().getBytes()) + ", " + id + ">";
    }

    public static void main(String[] args) {
        String stmp = "what are we doing today";
        Parameters param = new Parameters();
        HistoryDigest d = new HistoryDigest(param, stmp.getBytes());
        int testId = 2;
        HistoryStateId hss1 = new HistoryStateId(param, d, d, testId);

        byte[] firstBytes = hss1.getBytes();
        System.out.println("initial: " + hss1.toString());
        UnsignedTypes.printBytes(hss1.getBytes());
        HistoryStateId hss2 = new HistoryStateId(hss1.getBytes(), param);
        byte[] secondBytes = hss2.getBytes();
        System.out.println("\nsecondary: " + hss2.toString());
        UnsignedTypes.printBytes(hss2.getBytes());

        if (firstBytes.length != secondBytes.length) {
            System.out.println("Messages have different length");
        }
        boolean res = true;
        int i = 0;
        for (; i < firstBytes.length; i++) {
            if (firstBytes[i] != secondBytes[i]) {
                res = false;
                break;
            }
        }
        System.out.println();
        if (res) {
            System.out.println("Identical messages");
        } else {
            System.out.println("*******************************");
            System.out.println("Messages differ at position " + i);
            System.out.println("*******************************");
        }

    }

}
