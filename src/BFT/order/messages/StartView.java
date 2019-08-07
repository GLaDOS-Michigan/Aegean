// $Id: StartView.java 722 2011-07-04 09:24:35Z aclement $

package BFT.order.messages;

import BFT.Parameters;
import BFT.messages.HistoryDigest;
import BFT.messages.MacMessage;
import util.UnsignedTypes;

/**

 **/
public class StartView extends MacMessage {


    public StartView(Parameters param, long view, long seq,
                     HistoryDigest hist, int sendingReplica) {
        super(MessageTags.StartView, computeSize(param, hist), sendingReplica);

        viewNo = view;
        seqNo = seq;
        history = hist;

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
        tmp = history.getBytes();

        for (int i = 0; i < tmp.length; i++, offset++) {
            bytes[offset] = tmp[i];
        }
    }

    public StartView(byte[] bits, Parameters param) {
        super(bits);
        if (getTag() != MessageTags.StartView) {
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
        for (int i = 0; i < tmp.length; i++, offset++)
            tmp[i] = bits[offset];
        history = HistoryDigest.fromBytes(tmp, param);

        if (offset != getBytes().length - getAuthenticationSize()) {
            throw new RuntimeException("invalid byte array");
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

    protected HistoryDigest history;

    public HistoryDigest getHistory() {
        return history;
    }

    public int getSendingReplica() {
        return (int) (getSender());
    }


    /**
     * computes the size of the bits specific to StartView
     **/
    private static int computeSize(Parameters param, HistoryDigest h) {
        int size = MessageTags.uint32Size + MessageTags.uint32Size + HistoryDigest.size(param);
        return size;
    }


    public String toString() {
        return "<CONF-VIEW, " + super.toString() + ", view=" + viewNo +
                ", seqNo=" + seqNo + ", history=" + history + ", send=" + getSender() + ">";
    }

    public static void main(String args[]) {
        byte[] tmp = new byte[2];
        tmp[0] = 1;
        tmp[1] = 23;
        Parameters param = new Parameters();
        HistoryDigest hist = new HistoryDigest(param, tmp);

        StartView vmb = new StartView(param, 123, 534, hist, 1);
        //System.out.println("initial: "+vmb.toString());
        UnsignedTypes.printBytes(vmb.getBytes());
        StartView vmb2 = new StartView(vmb.getBytes(), param);
        //System.out.println("\nsecondary: "+vmb2.toString());
        UnsignedTypes.printBytes(vmb2.getBytes());

        vmb = new StartView(param, 42, 123, hist, 2);
        //System.out.println("\ninitial: "+vmb.toString());
        UnsignedTypes.printBytes(vmb.getBytes());
        vmb2 = new StartView(vmb.getBytes(), param);
        //System.out.println("\nsecondary: "+vmb2.toString());
        UnsignedTypes.printBytes(vmb2.getBytes());

        //System.out.println("\nold = new: "+vmb.equals(vmb2));
    }
}