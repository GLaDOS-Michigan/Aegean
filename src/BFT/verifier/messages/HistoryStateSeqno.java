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
public class HistoryStateSeqno {
    private Digest history;
    private Digest state;
    private long seqno;
    Parameters parameters;

    public HistoryStateSeqno(Parameters param, Digest hd, Digest sd, long sn) {
        parameters = param;
        history = hd;
        state = sd;
        seqno = sn;
    }

    public HistoryStateSeqno(Parameters param, HistoryAndState has, long sn) {
        parameters = param;
        history = has.getHistory();
        state = has.getState();
        seqno = sn;
    }

    public HistoryStateSeqno(byte[] bytes, Parameters param) {
        parameters = param;
        //System.out.println("bytes length = "+bytes.length);
        int offset = 0;
        byte[] tmp = new byte[parameters.digestLength];
        System.arraycopy(bytes, offset, tmp, 0, parameters.digestLength);
        offset += parameters.digestLength;
        history = Digest.fromBytes(parameters, tmp);

        System.arraycopy(bytes, offset, tmp, 0, parameters.digestLength);
        offset += parameters.digestLength;
        state = Digest.fromBytes(parameters, tmp);

        byte[] tmp2 = new byte[MessageTags.uint32Size];
        System.arraycopy(bytes, offset, tmp2, 0, MessageTags.uint32Size);
        offset += MessageTags.uint32Size;
        seqno = UnsignedTypes.bytesToLong(tmp2);

    }

    public static int size(Parameters param) {
        return param.digestLength + param.digestLength + MessageTags.uint32Size;
    }

    public int size() {
        return parameters.digestLength + parameters.digestLength + MessageTags.uint32Size;
    }

    public Digest getHistory() {
        return history;
    }

    public Digest getState() {
        return state;
    }

    public long getSeqno() {
        return seqno;
    }

    public byte[] getBytes() {
        byte[] ret = new byte[size()];

        int offset = 0;
        System.arraycopy(history.getBytes(), 0, ret, offset, parameters.digestLength);
        offset += parameters.digestLength;
        System.arraycopy(state.getBytes(), 0, ret, offset, parameters.digestLength);
        offset += parameters.digestLength;
        System.arraycopy(UnsignedTypes.longToBytes(seqno), 0, ret, offset, MessageTags.uint32Size);
        offset += MessageTags.uint32Size;

        return ret;
    }

    @Override
    public boolean equals(Object obj) {
        HistoryStateSeqno target = (HistoryStateSeqno) obj;
        //Debug.debug(Debug.MODULE_VERIFIER, "Comparing:\n%s\n%s\n", this, target);
        return this.history.equals(target.history) && this.state.equals(target.state) && this.seqno == target.seqno;
    }

    @Override
    public String toString() {
        return "<" + UnsignedTypes.bytesToHexString(getHistory().getBytes()) + ", " +
                UnsignedTypes.bytesToHexString(getState().getBytes()) + ", " + seqno + ">";
    }

    public static void main(String[] args) {
        String stmp = "what are we doing today";
        Parameters param = new Parameters();
        HistoryDigest d = new HistoryDigest(param, stmp.getBytes());
        long seqNo = 532;
        HistoryStateSeqno hss1 = new HistoryStateSeqno(param, d, d, seqNo);

        byte[] firstBytes = hss1.getBytes();
        System.out.println("initial: " + hss1.toString());
        UnsignedTypes.printBytes(hss1.getBytes());
        HistoryStateSeqno hss2 = new HistoryStateSeqno(hss1.getBytes(), param);
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
