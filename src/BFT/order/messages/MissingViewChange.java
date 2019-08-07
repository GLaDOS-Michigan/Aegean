// $Id: MissingViewChange.java 722 2011-07-04 09:24:35Z aclement $

package BFT.order.messages;

import BFT.Parameters;
import BFT.messages.HistoryDigest;
import BFT.messages.MacMessage;
import util.UnsignedTypes;

/**

 **/
public class MissingViewChange extends MacMessage {


    public MissingViewChange(long view, long replicaId, int sendingReplica) {
        super(MessageTags.MissingViewChange, computeSize(), sendingReplica);
        viewNo = view;
        missingReplicaId = replicaId;

        int offset = getOffset();
        //System.out.println(offset);
        byte[] bytes = getBytes();
        //System.out.println(bytes.length);
        // place the view number
        byte[] tmp;
        UnsignedTypes.longToBytes(viewNo, bytes, offset);
        offset += UnsignedTypes.uint32Size;
        // place the missing replica id
        UnsignedTypes.longToBytes(missingReplicaId, bytes, offset);
        offset += UnsignedTypes.uint32Size;
    }

    public MissingViewChange(byte[] bits) {
        super(bits);
        int offset = getOffset();
        byte[] tmp;


        // read the view number;

        viewNo = UnsignedTypes.bytesToLong(bits, offset);
        offset += UnsignedTypes.uint32Size;
        // read the missing replica
        missingReplicaId = UnsignedTypes.bytesToLong(bits, offset);
        offset += UnsignedTypes.uint32Size;
        if (offset != getBytes().length - getAuthenticationSize()) {
            throw new RuntimeException("invalid byte array");
        }
    }

    protected long viewNo;

    public long getView() {
        return viewNo;
    }

    protected long missingReplicaId;

    public long getMissingReplicaId() {
        return missingReplicaId;
    }


    public int getSendingReplica() {
        return (int) getSender();
    }

    public boolean equals(MissingViewChange nb) {
        return super.equals(nb) && viewNo == nb.viewNo &&
                missingReplicaId == nb.missingReplicaId;
    }

    /**
     * computes the size of the bits specific to MissingViewChange
     **/
    private static int computeSize() {
        int size = MessageTags.uint32Size + MessageTags.uint32Size;
        return size;
    }


    public String toString() {
        return "<MISS-VC, " + super.toString() + ", view=" + viewNo +
                ", rep:" + missingReplicaId + ">";
    }

    public static void main(String args[]) {
        byte[] tmp = new byte[2];
        tmp[0] = 1;
        tmp[1] = 23;
        Parameters param = new Parameters();
        HistoryDigest hist = new HistoryDigest(param, tmp);

        MissingViewChange vmb = new MissingViewChange(123, 534, 1);
        //System.out.println("initial: "+vmb.toString());
        UnsignedTypes.printBytes(vmb.getBytes());
        MissingViewChange vmb2 = new MissingViewChange(vmb.getBytes());
        //System.out.println("\nsecondary: "+vmb2.toString());
        UnsignedTypes.printBytes(vmb2.getBytes());

        vmb = new MissingViewChange(42, 123, 2);
        //System.out.println("\ninitial: "+vmb.toString());
        UnsignedTypes.printBytes(vmb.getBytes());
        vmb2 = new MissingViewChange(vmb.getBytes());
        //System.out.println("\nsecondary: "+vmb2.toString());
        UnsignedTypes.printBytes(vmb2.getBytes());

        //System.out.println("\nold = new: "+vmb.equals(vmb2));
    }

}