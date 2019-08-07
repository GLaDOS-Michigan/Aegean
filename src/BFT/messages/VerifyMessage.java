// $Id: VerifyMessage.java 722 2011-07-04 09:24:35Z aclement $

package BFT.messages;

import BFT.Parameters;
import util.UnsignedTypes;

/**
 * @author yangwang
 */
public class VerifyMessage extends MacArrayMessage {

    private long viewNo;
    private long versionNo;
    private HistoryAndState historyAndState;

    public VerifyMessage(Parameters param, long view, long seq, HistoryAndState has,
                         int sendingReplica) {
        super(param, MessageTags.Verify,
                computeSize(view, seq, has),
                sendingReplica,
                param.getVerifierCount());
        this.viewNo = view;
        this.versionNo = seq;
        this.historyAndState = has;

        int offset = getOffset();
        byte[] bytes = getBytes();
        // place the view number
        UnsignedTypes.longToBytes(viewNo, bytes, offset);
        offset += UnsignedTypes.uint32Size;

        // place the sequence number
        UnsignedTypes.longToBytes(versionNo, bytes, offset);
        offset += UnsignedTypes.uint32Size;

        // place the history and state
        // first the history
        byte[] tmp = historyAndState.getHistory().getBytes();
        for (int i = 0; i < tmp.length; i++, offset++) {
            bytes[offset] = tmp[i];
        }

        //now the state
        tmp = historyAndState.getState().getBytes();
        for (int i = 0; i < tmp.length; i++, offset++) {
            bytes[offset] = tmp[i];
        }
    }


    public VerifyMessage(byte[] bits, Parameters param) {
        super(bits, param);
        if (getTag() != MessageTags.Verify) {
            throw new RuntimeException("invalid message Tag: " + getTag());
        }
        int offset = getOffset();
        byte[] tmp;

        // read the view number;
        viewNo = UnsignedTypes.bytesToLong(bits, offset);
        offset += UnsignedTypes.uint32Size;

        // read the sequence number
        versionNo = UnsignedTypes.bytesToLong(bits, offset);
        offset += UnsignedTypes.uint32Size;

        // read the history digest
        tmp = new byte[param.digestLength];
        for (int i = 0; i < tmp.length; i++, offset++) {
            tmp[i] = bits[offset];
        }
        Digest hd = Digest.fromBytes(param, tmp);

        // read the state digest
        //tmp = new byte[Digest.size()];
        for (int i = 0; i < tmp.length; i++, offset++) {
            tmp[i] = bits[offset];
        }
        Digest sd = Digest.fromBytes(param, tmp);

        historyAndState = new HistoryAndState(param, hd, sd);
        if (offset != getBytes().length - getAuthenticationSize()) {
            throw new RuntimeException("Invalid byte input");
        }
    }

    public long getView() {
        return this.viewNo;
    }

    public long getVersionNo() {
        return this.versionNo;
    }

    public int getSendingReplica() {
        return (int) sender;
    }

    public HistoryAndState getHistoryAndState() {
        return historyAndState;
    }

    private static int computeSize(long view, long seq, HistoryAndState has) {
        int size = MessageTags.uint32Size + // view
                MessageTags.uint32Size + // versionNo
                has.getSize();           // historyAndState
        return size;
    }

    public boolean equals(VerifyMessage nb) {
        return super.equals(nb) && viewNo == nb.viewNo && versionNo == nb.versionNo &&
                historyAndState.equals(nb.historyAndState);
    }

    @Override
    public String toString() {
        return "<VerifyMessage, " + getView() + ", " + getVersionNo() + ", " + historyAndState + ">";
    }

    @Override
    public boolean matches(VerifiedMessageBase vmb) {
        VerifyMessage target = (VerifyMessage) vmb;
        if (this.historyAndState == null || target.historyAndState == null) {
            return false;
        }
        boolean ret = this.versionNo == target.versionNo && this.viewNo == target.viewNo && this.historyAndState.equals(target.historyAndState);
        return ret;
    }

    public static void main(String args[]) {
        byte[] tmp = new byte[2];
        tmp[0] = 1;
        tmp[1] = 23;
        Parameters param = new Parameters();
        Digest hist = new Digest(param, tmp);

        HistoryAndState has = new HistoryAndState(param, hist, hist);
        VerifyMessage vmb = new VerifyMessage(param, 43, 23, has, 2);
        //System.out.println("initial: "+vmb.toString());
        UnsignedTypes.printBytes(vmb.getBytes());
        VerifyMessage vmb2 = new VerifyMessage(vmb.getBytes(), param);
        //System.out.println("\nsecondary: "+vmb2.toString());
        UnsignedTypes.printBytes(vmb2.getBytes());

        vmb = new VerifyMessage(param, 42, 123, has, 2);
        //System.out.println("\ninitial: "+vmb.toString());
        UnsignedTypes.printBytes(vmb.getBytes());
        vmb2 = new VerifyMessage(vmb.getBytes(), param);
        //System.out.println("\nsecondary: "+vmb2.toString());
        UnsignedTypes.printBytes(vmb2.getBytes());
        //System.out.println("\nold = new: "+vmb.equals(vmb2));
    }
}
