// $Id: PrePrepare.java 49 2010-02-26 19:33:49Z yangwang $

package BFT.verifier.messages;

import BFT.Parameters;
import BFT.messages.Digest;
import BFT.messages.HistoryAndState;
import BFT.messages.MacArrayMessage;
import BFT.messages.SignedRequestCore;
import BFT.util.UnsignedTypes;

/**

 **/
public class Commit extends MacArrayMessage {

    public Commit(Parameters param, long view, long seq, HistoryAndState has,
                  int sendingReplica) {
        super(param, MessageTags.Commit,
                computeSize(view, seq, has),
                sendingReplica,
                param.getVerifierCount());

        viewNo = view;
        seqNo = seq;
        historyAndState = has;

        int offset = getOffset();
        byte[] bytes = getBytes();
        // place the view number
        byte[] tmp = UnsignedTypes.longToBytes(viewNo);
        for (int i = 0; i < tmp.length; i++, offset++) {
            bytes[offset] = tmp[i];
        }

        // place the sequence number
        tmp = UnsignedTypes.longToBytes(seqNo);
        for (int i = 0; i < tmp.length; i++, offset++) {
            bytes[offset] = tmp[i];
        }

        // place the history and state
        // first the history
        tmp = historyAndState.getHistory().getBytes();
        for (int i = 0; i < tmp.length; i++, offset++) {
            bytes[offset] = tmp[i];
        }

        //now the state
        tmp = historyAndState.getState().getBytes();
        for (int i = 0; i < tmp.length; i++, offset++) {
            bytes[offset] = tmp[i];
        }
    }

    public Commit(byte[] bits, Parameters param) {
        super(bits, param);
        if (getTag() != MessageTags.Commit) {
            throw new RuntimeException("invalid message Tag: " + getTag());
        }
        int offset = getOffset();
        byte[] tmp;

        // read the view number;
        tmp = new byte[4];
        for (int i = 0; i < tmp.length; i++, offset++) {
            tmp[i] = bits[offset];
        }
        viewNo = UnsignedTypes.bytesToLong(tmp);

        // read the sequence number
        for (int i = 0; i < tmp.length; i++, offset++) {
            tmp[i] = bits[offset];
        }
        seqNo = UnsignedTypes.bytesToLong(tmp);

        // read the history digest
        tmp = new byte[param.digestLength];
        for (int i = 0; i < tmp.length; i++, offset++) {
            tmp[i] = bits[offset];
        }
        Digest hd = Digest.fromBytes(param, tmp);

        // read the state digest
        tmp = new byte[param.digestLength];
        for (int i = 0; i < tmp.length; i++, offset++) {
            tmp[i] = bits[offset];
        }
        Digest sd = Digest.fromBytes(param, tmp);

        historyAndState = new HistoryAndState(param, hd, sd);

        if (offset != getBytes().length - getAuthenticationSize()) {
            throw new RuntimeException("Invalid byte input");
        }

    }

    protected long viewNo;

    public long getView() {
        return viewNo;
    }

    protected long seqNo;

    public long getSeqNo() {
        return seqNo;
    }

    protected HistoryAndState historyAndState;

    public HistoryAndState getHistoryAndState() {
        return historyAndState;
    }

    public long getSendingReplica() {
        return getSender();
    }

    public boolean equals(Commit nb) {
        return super.equals(nb) && viewNo == nb.viewNo && seqNo == nb.seqNo &&
                historyAndState.equals(nb.historyAndState);
    }

    /**
     * computes the size of the bits specific to BatchToCommit
     **/
    private static int computeSize(long view, long seq, HistoryAndState has) {
        int size =
                MessageTags.uint32Size + // view
                        MessageTags.uint32Size + // seqNo
                        has.getSize(); // HistoryAndState
        return size;
    }


    @Override
    public String toString() {
        return "<C, " + getView() + ", " + getSeqNo() + ", " + historyAndState + ", " + ">";
    }

    public static void main(String args[]) {
        SignedRequestCore[] entries = new SignedRequestCore[1];
        byte[] tmp = new byte[2];
        tmp[0] = 1;
        tmp[1] = 23;
        Parameters param = new Parameters();
        entries[0] = new SignedRequestCore(param, 2, 0, 3, tmp);
        Digest hist = new Digest(param, tmp);
        Digest sd = new Digest(param, tmp);
        HistoryAndState has = new HistoryAndState(param, hist, sd);

        Commit vmb = new Commit(param, 43, 234, has, 3);
        //System.out.println("initial: "+vmb.toString());
        UnsignedTypes.printBytes(vmb.getBytes());
        Commit vmb2 = new Commit(vmb.getBytes(), param);
        //System.out.println("\nsecondary: "+vmb2.toString());
        UnsignedTypes.printBytes(vmb2.getBytes());

        vmb = new Commit(param, 134, 8, has, 1);
        //System.out.println("\ninitial: "+vmb.toString());
        UnsignedTypes.printBytes(vmb.getBytes());
        vmb2 = new Commit(vmb.getBytes(), param);
        //System.out.println("\nsecondary: "+vmb2.toString());
        UnsignedTypes.printBytes(vmb2.getBytes());

        //System.out.println("\nold = new: "+vmb.equals(vmb2));
    }
}