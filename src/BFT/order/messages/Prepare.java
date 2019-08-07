// $Id: Prepare.java 722 2011-07-04 09:24:35Z aclement $

package BFT.order.messages;

import BFT.Parameters;
import BFT.messages.HistoryDigest;
import BFT.messages.MacArrayMessage;
import BFT.messages.VerifiedMessageBase;
import util.UnsignedTypes;

/**

 **/
public class Prepare extends MacArrayMessage {


    public Prepare(Parameters param, long view, long seq, HistoryDigest pphash, int sendingReplica) {
        super(param, MessageTags.Prepare, computeSize(param, pphash), sendingReplica,
                param.getOrderCount());
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


    public Prepare(byte[] bits, Parameters param) {
        super(bits, param);
        if (getTag() != MessageTags.Prepare) {
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

    public boolean equals(Prepare nb) {
        return super.equals(nb) && viewNo == nb.viewNo && seqNo == nb.seqNo &&
                ppHash.equals(nb.ppHash);
    }

    /**
     * computes the size of the bits specific to Prepare
     **/
    private static int computeSize(Parameters param, HistoryDigest h) {
        int size = UnsignedTypes.uint32Size + UnsignedTypes.uint32Size + param.digestLength;
        return size;
    }

    public boolean matches(VerifiedMessageBase vmb) {
        if (vmb != null && vmb.getTag() == MessageTags.Prepare) {
            Prepare p = (Prepare) vmb;
            return p.ppHash.equals(ppHash) && seqNo == p.getSeqNo() && p.viewNo == viewNo;
        } else if (vmb != null && vmb.getTag() == MessageTags.Commit) {
            Commit c = (Commit) vmb;
            return c.ppHash.equals(ppHash) && seqNo == c.getSeqNo() && c.getView() == getView();
        } else {
            return false;
        }
    }

    public String toString() {
        return "<P, view=" + viewNo + ", seqNo=" + seqNo + ", ppHash=" + ppHash + ", send=" + getSender() + ">";
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
        BFT.messages.NonDeterminism non =
                new BFT.messages.NonDeterminism(12341, 123456);

        PrePrepare tmp2 = new PrePrepare(param, 43, 234, hist, batch, non, hist, 3);
        Prepare vmb = new Prepare(param, 43, 23, hist, 2);
        //System.out.println("initial: "+vmb.toString());
        UnsignedTypes.printBytes(vmb.getBytes());
        Prepare vmb2 = new Prepare(vmb.getBytes(), param);
        //System.out.println("\nsecondary: "+vmb2.toString());
        UnsignedTypes.printBytes(vmb2.getBytes());

        vmb = new Prepare(param, 42, 123, hist, 2);
        //System.out.println("\ninitial: "+vmb.toString());
        UnsignedTypes.printBytes(vmb.getBytes());
        vmb2 = new Prepare(vmb.getBytes(), param);
        //System.out.println("\nsecondary: "+vmb2.toString());
        UnsignedTypes.printBytes(vmb2.getBytes());

        //System.out.println("\nold = new: "+vmb.equals(vmb2));
    }
}