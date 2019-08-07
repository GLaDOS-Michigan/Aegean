// $Id: VerifyResponseMessage.java 722 2011-07-04 09:24:35Z aclement $

package BFT.messages;

import BFT.Parameters;
import util.UnsignedTypes;

/**
 * @author yangwang
 */
public class VerifyResponseMessage extends MacArrayMessage {

    private long viewNo;
    private long versionNo;
    private HistoryAndState historyAndState;
    private boolean nextSequential;

    public VerifyResponseMessage(Parameters param, long view, long seq, HistoryAndState has, boolean nextSequential,
                                 int sendingReplica) {
        super(param, MessageTags.VerifyResponse,
                computeSize(view, seq, has, nextSequential),
                sendingReplica,
                param.getExecutionCount());
        this.viewNo = view;
        this.versionNo = seq;
        this.historyAndState = has;

        int offset = getOffset();
        byte[] bytes = getBytes();
        // place the view number
        byte[] tmp;
        UnsignedTypes.longToBytes(viewNo, bytes, offset);
        offset += UnsignedTypes.uint32Size;
        // place the sequence number
        UnsignedTypes.longToBytes(versionNo, bytes, offset);
        offset += UnsignedTypes.uint32Size;

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

        // place nextSequential
        if (nextSequential) {
            bytes[offset++] = 1;
        } else {
            bytes[offset++] = 0;
        }
    }


    public VerifyResponseMessage(byte[] bits, Parameters param) {
        super(bits, param);
        if (getTag() != MessageTags.VerifyResponse) {
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
        tmp = new byte[parameters.digestLength];
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

        historyAndState = new HistoryAndState(parameters, hd, sd);

        // read nextSequential
        if (bits[offset] == 1) {
            nextSequential = true;
        } else {
            nextSequential = false;
        }
        offset++;

        if (offset != getBytes().length - getAuthenticationSize()) {
            throw new RuntimeException("Invalid byte input");
        }
    }

    public long getViewNo() {
        return this.viewNo;
    }

    public long getVersionNo() {
        return this.versionNo;
    }

    public HistoryAndState getHistoryAndState() {
        return historyAndState;
    }

    private static int computeSize(long view, long seq, HistoryAndState has, boolean nextSequential) {
        int size = MessageTags.uint32Size + // view
                MessageTags.uint32Size + // versionNo
                has.getSize() +          // historyAndState
                1;                         // boolean
        return size;
    }

    public boolean getNextSequential() {
        return this.nextSequential;
    }

    public boolean equals(VerifyResponseMessage nb) {
        return super.equals(nb) && viewNo == nb.viewNo && versionNo == nb.versionNo &&
                historyAndState.equals(nb.historyAndState) && nextSequential == nb.nextSequential;
    }

    @Override
    public String toString() {
        return "<VerifyResponseMessage, " + getViewNo() + ", " + getVersionNo() + ", " + historyAndState + ", " + nextSequential + ">";
    }

    @Override
    public boolean matches(VerifiedMessageBase vmb) {
        VerifyResponseMessage target = (VerifyResponseMessage) vmb;
        if (this.historyAndState == null || target.historyAndState == null) {
            return false;
        }
        boolean ret = this.versionNo == target.versionNo && this.viewNo == target.viewNo &&
                this.historyAndState.equals(target.historyAndState) && this.nextSequential == target.nextSequential;
        return ret;
    }

    public static void main(String args[]) {
        byte[] tmp = new byte[2];
        tmp[0] = 1;
        tmp[1] = 23;
        Parameters param = new Parameters();
        Digest hist = new Digest(param, tmp);


        HistoryAndState has = new HistoryAndState(param, hist, hist);
        VerifyResponseMessage vmb = new VerifyResponseMessage(param, 43, 23, has, false, 2);
        //System.out.println("initial: "+vmb.toString());
        UnsignedTypes.printBytes(vmb.getBytes());
        VerifyResponseMessage vmb2 = new VerifyResponseMessage(vmb.getBytes(), param);
        //System.out.println("\nsecondary: "+vmb2.toString());
        UnsignedTypes.printBytes(vmb2.getBytes());

        vmb = new VerifyResponseMessage(param, 42, 123, has, false, 2);
        //System.out.println("\ninitial: "+vmb.toString());
        UnsignedTypes.printBytes(vmb.getBytes());
        vmb2 = new VerifyResponseMessage(vmb.getBytes(), param);
        //System.out.println("\nsecondary: "+vmb2.toString());
        UnsignedTypes.printBytes(vmb2.getBytes());

        //System.out.println("\nold = new: "+vmb.equals(vmb2));
    }

}
