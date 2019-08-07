// $Id: ConfirmView.java 49 2010-02-26 19:33:49Z yangwang $

package BFT.verifier.messages;

import BFT.Parameters;
import BFT.messages.HistoryDigest;
import BFT.messages.MacArrayMessage;
import BFT.messages.VerifiedMessageBase;
import BFT.util.UnsignedTypes;

/**

 **/
public class ConfirmView extends MacArrayMessage {


    public ConfirmView(Parameters param, long view, HistoryStateSeqno hss, int sendingReplica) {
        super(param, MessageTags.ConfirmView, computeSize(param, hss), sendingReplica, param.getVerifierCount());
        viewNo = view;
        historyStateSeqno = hss;

        int offset = getOffset();
        //System.out.println(offset);
        byte[] bytes = getBytes();
        //System.out.println(bytes.length);
        // place the view number
        byte[] tmp = UnsignedTypes.longToBytes(viewNo);
        for (int i = 0; i < tmp.length; i++, offset++) {
            bytes[offset] = tmp[i];
        }
        //System.out.println(tmp.length);

        System.arraycopy(hss.getBytes(), 0, bytes, offset, HistoryStateSeqno.size(param));
        offset += HistoryStateSeqno.size(param);

    }

    public ConfirmView(byte[] bits, Parameters param) {
        super(bits, param);
        if (getTag() != MessageTags.ConfirmView) {
            throw new RuntimeException("invalid message Tag: " + getTag());
        }

        int offset = getOffset();
        byte[] tmp;

        // read the view number;
        tmp = new byte[MessageTags.uint32Size];
        for (int i = 0; i < tmp.length; i++, offset++)
            tmp[i] = bits[offset];
        viewNo = UnsignedTypes.bytesToLong(tmp);

        // read the Preprepare hash
        tmp = new byte[HistoryStateSeqno.size(param)];
        System.arraycopy(bits, offset, tmp, 0, HistoryStateSeqno.size(param));
        offset += HistoryStateSeqno.size(param);
        historyStateSeqno = new HistoryStateSeqno(tmp, param);

        if (offset != getBytes().length - getAuthenticationSize()) {
            throw new RuntimeException("invalid byte array");
        }
    }

    protected long viewNo;

    public long getView() {
        return viewNo;
    }

    protected HistoryStateSeqno historyStateSeqno;

    public HistoryStateSeqno getHistoryStateSeqno() {
        return historyStateSeqno;
    }

    public long getSendingReplica() {
        return getSender();
    }

    @Override
    public boolean matches(VerifiedMessageBase vmb) {
        ConfirmView cv = (ConfirmView) vmb;
        boolean res = getHistoryStateSeqno().equals(cv.getHistoryStateSeqno()) && viewNo == cv.getView();
        //System.out.println("confirm view matches: "+res);
        return res;
    }

    public boolean equals(ConfirmView nb) {
        return matches(nb) && super.equals(nb);
    }

    /**
     * computes the size of the bits specific to ConfirmView
     **/
    private static int computeSize(Parameters param, HistoryStateSeqno hss) {
        return MessageTags.uint32Size + HistoryStateSeqno.size(param);
    }


    public String toString() {
        return "<CONF-VIEW, " + super.toString() + ", view=" + viewNo +
                ", hss=" + historyStateSeqno + ", send=" + getSender() + ">";
    }

    public static void main(String args[]) {
        String stmp = "what are we doing today";
        Parameters param = new Parameters();
        HistoryDigest d = new HistoryDigest(param, stmp.getBytes());
        long seqNo = 532;
        HistoryStateSeqno hss = new HistoryStateSeqno(param, d, d, seqNo);
        UnsignedTypes.printBytes(hss.getBytes());

        ConfirmView vmb = new ConfirmView(param, 123, hss, 1);
        byte[] firstBytes = vmb.getBytes();
        System.out.println("initial: " + vmb.toString());
        UnsignedTypes.printBytes(vmb.getBytes());
        ConfirmView vmb2 = new ConfirmView(vmb.getBytes(), param);
        byte[] secondBytes = vmb2.getBytes();
        System.out.println("\nsecondary: " + vmb2.toString());
        UnsignedTypes.printBytes(vmb2.getBytes());

	        /*
		vmb = new ConfirmView(42, 123, hist, 2);
		//System.out.println("\ninitial: "+vmb.toString());
		UnsignedTypes.printBytes(vmb.getBytes());
		vmb2 = new ConfirmView(vmb.getBytes());
		//System.out.println("\nsecondary: "+vmb2.toString());
		UnsignedTypes.printBytes(vmb2.getBytes());
		*/
        //System.out.println("\nold = new: "+vmb.equals(vmb2));

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
        if (res && vmb.equals(vmb2)) {
            System.out.println("Identical messages");
        } else {
            System.out.println("*******************************");
            System.out.println("Messages differ at position " + i);
            System.out.println("*******************************");
        }
    }

}