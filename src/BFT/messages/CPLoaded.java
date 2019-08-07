// $Id: CPLoaded.java 722 2011-07-04 09:24:35Z aclement $

package BFT.messages;

import BFT.Parameters;
import util.UnsignedTypes;

public class CPLoaded extends MacArrayMessage {
    public CPLoaded(Parameters param, long seq, long sender) {
        super(param, MessageTags.CPLoaded,
                computeSize(), sender,
                param.getOrderCount());

        seqNo = seq;

        // now lets get the bytes
        byte[] bytes = getBytes();
        int offset = getOffset();
        // copy the sequence number over
        UnsignedTypes.longToBytes(seqNo, bytes, offset);
        offset += UnsignedTypes.uint32Size;
    }


    public CPLoaded(byte[] bytes, Parameters param) {
        super(bytes, param);
        if (getTag() != MessageTags.CPLoaded) {
            throw new RuntimeException("invalid message Tag: " + getTag());
        }

        // pull the sequence number
        byte[] tmp = new byte[4];
        int offset = getOffset();
        seqNo = UnsignedTypes.bytesToLong(bytes, offset);
        offset += UnsignedTypes.uint32Size;

        if (offset != bytes.length - getAuthenticationSize()) {
            throw new RuntimeException("Invalid byte input");
        }
    }


    protected long seqNo;

    public long getSendingReplica() {
        return getSender();
    }

    public long getSequenceNumber() {
        return seqNo;
    }

    private static int computeSize() {
        return MessageTags.uint32Size;
    }


    public String toString() {
        return "<LAST-EXEC, " + super.toString() + ", seqNo:" + seqNo + ">";
    }

    public static void main(String args[]) {
        byte[] tmp = new byte[8];
        for (int i = 0; i < 8; i++) {
            tmp[i] = (byte) i;
        }
        Parameters param = new Parameters();
        CPLoaded vmb = new CPLoaded(param, 1, 2);
        //System.out.println("initial: "+vmb.toString());
        UnsignedTypes.printBytes(vmb.getBytes());
        CPLoaded vmb2 = new CPLoaded(vmb.getBytes(), param);
        //System.out.println("\nsecondary: "+vmb2.toString());
        UnsignedTypes.printBytes(vmb2.getBytes());

        for (int i = 0; i < 8; i++)
            tmp[i] = (byte) (tmp[i] * tmp[i]);

        vmb = new CPLoaded(param, 134, 8);
        //System.out.println("initial: "+vmb.toString());
        UnsignedTypes.printBytes(vmb.getBytes());
        vmb2 = new CPLoaded(vmb.getBytes(), param);
        //System.out.println("\nsecondary: "+vmb2.toString());
        UnsignedTypes.printBytes(vmb2.getBytes());

        //System.out.println("old = new: "+(vmb2.toString().equals(vmb.toString())));
    }

}