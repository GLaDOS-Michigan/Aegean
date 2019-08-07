// $Id: ViewChangeAck.java 722 2011-07-04 09:24:35Z aclement $

package BFT.order.messages;

import BFT.Parameters;
import BFT.messages.Digest;
import BFT.messages.HistoryDigest;
import BFT.messages.MacArrayMessage;
import BFT.messages.MacBytes;
import util.UnsignedTypes;


/**
 * We rely on the MACArray of the veriied message to get the
 * appropriate MACs out.  The receiving replica must regenerate this
 * message and authenticate it with the appropriate MAC.
 **/
public class ViewChangeAck extends MacArrayMessage {

    public ViewChangeAck(Parameters param, ViewChangeCore vcc) {
        super(param, MessageTags.ViewChangeAck,
                computeSize(param, null),
                vcc.getSender(),
                param.getOrderCount());
        System.out.println("fix compute size.  make it a macmessage" +
                " only sent to the primary");
    }


    public ViewChangeAck(Parameters param, long view, long vcreplica, Digest vcd,
                         long sendingReplica) {
        super(param, MessageTags.ViewChangeAck,
                computeSize(param, vcd),
                sendingReplica,
                param.getOrderCount());

        viewNo = view;
        sourceReplica = vcreplica;
        vcDigest = vcd;

        int offset = getOffset();
        byte[] bytes = getBytes();
        // place the view number
        byte[] tmp;
        UnsignedTypes.longToBytes(viewNo, bytes, offset);
        offset += UnsignedTypes.uint32Size;

        // place the source replica
        UnsignedTypes.longToBytes(sourceReplica, bytes, offset);
        offset += UnsignedTypes.uint32Size;

        // place the view change digest
        tmp = vcDigest.getBytes();
        for (int i = 0; i < tmp.length; i++, offset++) {
            bytes[offset] = tmp[i];
        }
    }


    public ViewChangeAck(byte[] bits, Parameters param) {
        super(bits, param);
        if (getTag() != MessageTags.ViewChangeAck) {
            throw new RuntimeException("invalid message Tag: " + getTag());
        }

        int offset = getOffset();
        byte[] tmp;

        // read the view number;
        viewNo = UnsignedTypes.bytesToLong(bits, offset);
        offset += UnsignedTypes.uint32Size;
        // read the source replica
        sourceReplica = UnsignedTypes.bytesToLong(bits, offset);
        offset += UnsignedTypes.uint32Size;
        // read the VC digest
        tmp = new byte[Digest.size(param)];
        for (int i = 0; i < tmp.length; i++, offset++) {
            tmp[i] = bits[offset];
        }
        vcDigest = Digest.fromBytes(param, tmp);

        if (offset != getBytes().length - getAuthenticationSize()) {
            throw new RuntimeException("Invalid byte input");
        }
    }

    protected long viewNo;

    public long getView() {
        return viewNo;
    }

    protected long sourceReplica;

    public long getChangingReplica() {
        return sourceReplica;
    }

    protected Digest vcDigest;

    public Digest getVCDigest() {
        return vcDigest;
    }

    public long getSendingReplica() {
        return getSender();
    }

    public boolean equals(ViewChangeAck nb) {
        boolean res = super.equals(nb) && viewNo == nb.viewNo &&
                sourceReplica == nb.sourceReplica &&
                vcDigest.equals(nb.vcDigest);
        return res;
    }


    /**
     * computes the size of the bits specific to ViewChangeAck
     **/
    private static int computeSize(Parameters param, Digest vcd) {
        int size = MessageTags.uint32Size + MessageTags.uint32Size;
        size += Digest.size(param);
        return size;
    }


    public String toString() {
        return "<VC-ACK, " + super.toString() + ", v:" + viewNo + ", sourceRep:" +
                sourceReplica + ", vcDigest:" + vcDigest + ">";
    }

    public static void main(String args[]) {
        String stmp = "what are we doing today";
        Parameters param = new Parameters();
        HistoryDigest d = new HistoryDigest(param, stmp.getBytes());
        MacBytes m[] = new MacBytes[4];
        byte[] tmp = new byte[MacBytes.size()];
        for (int i = 0; i < tmp.length; i++) {
            tmp[i] = (byte) (i + MacBytes.size());
        }
        for (int i = 0; i < m.length; i++) {
            m[i] = new MacBytes(tmp);
        }

        ViewChangeAck vmb = new ViewChangeAck(param, 1, 2, d, 0);
        //System.out.println("initial: "+vmb.toString());
        UnsignedTypes.printBytes(vmb.getBytes());
        ViewChangeAck vmb2 = new ViewChangeAck(vmb.getBytes(), param);
        //System.out.println("\nsecondary: "+vmb2.toString());
        UnsignedTypes.printBytes(vmb2.getBytes());

        vmb = new ViewChangeAck(param, 23, 43, d, 1);
        //System.out.println("\ninitial: "+vmb.toString());
        UnsignedTypes.printBytes(vmb.getBytes());
        vmb2 = new ViewChangeAck(vmb.getBytes(), param);
        //System.out.println("\nsecondary: "+vmb2.toString());
        UnsignedTypes.printBytes(vmb2.getBytes());

        //System.out.println("\nold = new: "+vmb.equals(vmb2));
    }

}