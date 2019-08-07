// $Id: MissingOps.java 722 2011-07-04 09:24:35Z aclement $

package BFT.order.messages;

import BFT.Parameters;
import BFT.messages.MacArrayMessage;
import util.UnsignedTypes;

/**

 **/
public class MissingOps extends MacArrayMessage {


    public MissingOps(Parameters param, long view, long[] missingops, int sendingReplica) {
        super(param, MessageTags.MissingOps, computeSize(missingops), sendingReplica, param.getOrderCount());
        viewNo = view;
        missingOps = missingops;

        int offset = getOffset();
        //System.out.println(offset);
        byte[] bytes = getBytes();
        //System.out.println(bytes.length);
        // place the view number
        byte[] tmp;
        UnsignedTypes.longToBytes(viewNo, bytes, offset);
        offset += UnsignedTypes.uint32Size;
        // place the number of missing ops
        UnsignedTypes.longToBytes(missingOps.length, bytes, offset);
        offset += UnsignedTypes.uint32Size;
        // place the list of missing ops
        for (int j = 0; j < missingOps.length; j++) {
            UnsignedTypes.longToBytes(missingOps[j], bytes, offset);
            offset += UnsignedTypes.uint32Size;
        }
    }

    public MissingOps(byte[] bits, Parameters param) {
        super(bits, param);
        int offset = getOffset();

        // read the view number;
        viewNo = UnsignedTypes.bytesToLong(bits, offset);
        offset += UnsignedTypes.uint32Size;

        // read the number of missing ops

        int ln = (int) (UnsignedTypes.bytesToLong(bits, offset));
        offset += UnsignedTypes.uint32Size;
        missingOps = new long[ln];
        for (int j = 0; j < ln; j++) {
            missingOps[j] = UnsignedTypes.bytesToLong(bits, offset);
            offset += UnsignedTypes.uint32Size;
        }

        if (offset != getBytes().length - getAuthenticationSize()) {
            throw new RuntimeException("invalid byte array");
        }
    }

    protected long viewNo;

    public long getView() {
        return viewNo;
    }

    protected long[] missingOps;

    public long[] getMissingOps() {
        return missingOps;
    }


    public int getSendingReplica() {
        return (int) getSender();
    }

    public boolean equals(MissingOps nb) {
        boolean res = super.equals(nb) && nb != null &&
                viewNo == nb.viewNo && missingOps.length == nb.missingOps.length;
        for (int i = 0; i < missingOps.length && res; i++) {
            res = res && missingOps[i] == nb.missingOps[i];
        }
        return res;
    }

    /**
     * computes the size of the bits specific to MissingOps
     **/
    private static int computeSize(long b[]) {
        int size = UnsignedTypes.uint32Size + UnsignedTypes.uint32Size + UnsignedTypes.uint32Size * b.length;
        return size;
    }


    public String toString() {
        return "<MISS-VC, " + super.toString() + ", view=" + viewNo + ", ops:" + missingOps.length + ">";
    }

    public static void main(String args[]) {
        long list[] = new long[5];
        for (int i = 0; i < list.length; i++) {
            list[i] = i + 2342 + i * i * i * 23;
        }

        Parameters param = new Parameters();
        MissingOps vmb = new MissingOps(param, 123, list, 1);
        //System.out.println("initial: "+vmb.toString());
        UnsignedTypes.printBytes(vmb.getBytes());
        MissingOps vmb2 = new MissingOps(vmb.getBytes(), param);
        //System.out.println("\nsecondary: "+vmb2.toString());
        UnsignedTypes.printBytes(vmb2.getBytes());


        list[2] = 234;

        vmb = new MissingOps(param, 42, list, 2);
        //System.out.println("\ninitial: "+vmb.toString());
        UnsignedTypes.printBytes(vmb.getBytes());
        vmb2 = new MissingOps(vmb.getBytes(), param);
        //System.out.println("\nsecondary: "+vmb2.toString());
        UnsignedTypes.printBytes(vmb2.getBytes());

        //System.out.println("\nold = new: "+vmb.equals(vmb2));
    }
}