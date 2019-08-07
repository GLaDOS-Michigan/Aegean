// $Id: Commit.java 728 2011-09-11 23:44:18Z aclement $

package BFT.order.messages;

import BFT.Parameters;
import BFT.messages.HistoryDigest;
import BFT.messages.MacArrayMessage;
import util.UnsignedTypes;

/**

 **/
public class Commit extends MacArrayMessage {


    public Commit(Parameters param, long view, long seq, HistoryDigest pphash, int sendingReplica) {
        super(param, MessageTags.Commit, computeSize(param, pphash), sendingReplica, param.getOrderCount());
        viewNo = view;
        seqNo = seq;
        ppHash = pphash;

        int offset = getOffset();
        byte[] bytes = getBytes();
        // place the view number
        byte[] tmp;
        UnsignedTypes.longToBytes(viewNo, bytes, offset);
        offset += UnsignedTypes.uint32Size;
        // place the sequence number
        UnsignedTypes.longToBytes(seqNo, bytes, offset);
        offset += UnsignedTypes.uint32Size;
        // place the history
        tmp = ppHash.getBytes();

        for (int i = 0; i < tmp.length; i++, offset++) {
            bytes[offset] = tmp[i];
        }
    }

    public Commit(Parameters param, Prepare pp, int sendingReplica) {
        this(param, pp.getView(), pp.getSeqNo(), pp.getPrePrepareHash(), sendingReplica);
    }

    public Commit(byte[] bits, Parameters param) {
        super(bits, param);
        if (getTag() != MessageTags.Commit) {
            throw new RuntimeException("invalid message Tag: " + getTag());
        }

        int offset = getOffset();
        byte[] tmp;

        // read the view number;
        viewNo = UnsignedTypes.bytesToLong(bits, offset);
        offset += UnsignedTypes.uint32Size;

        // read the sequence number
        seqNo = UnsignedTypes.bytesToLong(bits, offset);
        offset += UnsignedTypes.uint32Size;

        // read the Preprepare hash
        tmp = new byte[HistoryDigest.size(param)];
        for (int i = 0; i < tmp.length; i++, offset++) {
            tmp[i] = bits[offset];
        }
        ppHash = HistoryDigest.fromBytes(tmp, param);

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

    protected HistoryDigest ppHash;

    public HistoryDigest getPrePrepareHash() {
        return ppHash;
    }

    public long getSendingReplica() {
        return getSender();
    }

    public boolean equals(Commit nb) {
        return super.equals(nb) && viewNo == nb.viewNo && seqNo == nb.seqNo &&
                ppHash.equals(nb.ppHash);
    }

    /**
     * computes the size of the bits specific to Commit
     **/
    private static int computeSize(Parameters param, HistoryDigest h) {
        int size = UnsignedTypes.uint32Size + UnsignedTypes.uint32Size +
                HistoryDigest.size(param);
        return size;
    }


    public String toString() {
        return "<C, view=" + viewNo + ", seqNo=" + seqNo + ", ppHash=" + ppHash + ", send=" + getSender() + ">";
    }

    public static void main(String args[]) {
        BFT.messages.SignedRequestCore[] entries = new BFT.messages.SignedRequestCore[1];
        byte[] tmp = new byte[2];
        tmp[0] = 1;
        tmp[1] = 23;
        Parameters param = new Parameters();
        entries[0] = new BFT.messages.SignedRequestCore(param, 2, 0, 3, tmp);
        HistoryDigest hist = new HistoryDigest(param, tmp);
        RequestBatch batch = new RequestBatch(param, entries);
        BFT.messages.NonDeterminism non = new BFT.messages.NonDeterminism(12341, 123456);

        PrePrepare tmp2 = new PrePrepare(param, 43, 234, hist, batch, non, hist, 3);
        Commit vmb = new Commit(param, 43, 23, hist, 1);
        //System.out.println("initial: "+vmb.toString());
        UnsignedTypes.printBytes(vmb.getBytes());
        Commit vmb2 = new Commit(vmb.getBytes(), param);
        //System.out.println("\nsecondary: "+vmb2.toString());
        UnsignedTypes.printBytes(vmb2.getBytes());

        vmb = new Commit(param, 42, 123, hist, 2);
        //System.out.println("\ninitial: "+vmb.toString());
        UnsignedTypes.printBytes(vmb.getBytes());
        vmb2 = new Commit(vmb.getBytes(), param);
        //System.out.println("\nsecondary: "+vmb2.toString());
        UnsignedTypes.printBytes(vmb2.getBytes());

        //System.out.println("\nold = new: "+vmb.equals(vmb2));
    }

}