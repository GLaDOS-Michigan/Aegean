// $Id: RequestCP.java 722 2011-07-04 09:24:35Z aclement $

package BFT.messages;

import BFT.Parameters;
import util.UnsignedTypes;

public class RequestCP extends MacArrayMessage {
    public RequestCP(Parameters param, long seq, long sender) {
        super(param, MessageTags.RequestCP,
                computeSize(), sender,
                param.getExecutionCount());

        seqNo = seq;

        // now lets get the bytes
        byte[] bytes = getBytes();
        int offset = getOffset();

        // copy the sequence number over
        UnsignedTypes.longToBytes(seqNo, bytes, offset);
    }

    public RequestCP(byte[] bytes, Parameters param) {
        super(bytes, param);
        if (getTag() != MessageTags.RequestCP) {
            throw new RuntimeException("invalid message Tag: " + getTag());
        }

        // pull the sequence number
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
        return "<REQ-CP, " + super.toString() + ", seqNo:" + seqNo + ">";
    }

    public static void main(String args[]) {
        byte[] tmp = new byte[8];
        for (int i = 0; i < 8; i++) {
            tmp[i] = (byte) i;
        }
        Parameters param = new Parameters();
        RequestCP vmb = new RequestCP(param, 1, 2);
        //System.out.println("initial: "+vmb.toString());
        UnsignedTypes.printBytes(vmb.getBytes());
        RequestCP vmb2 = new RequestCP(vmb.getBytes(), param);
        //System.out.println("\nsecondary: "+vmb2.toString());
        UnsignedTypes.printBytes(vmb2.getBytes());

        for (int i = 0; i < 8; i++) {
            tmp[i] = (byte) (tmp[i] * tmp[i]);
        }

        vmb = new RequestCP(new Parameters(), 134, 8);
        //System.out.println("initial: "+vmb.toString());
        UnsignedTypes.printBytes(vmb.getBytes());
        vmb2 = new RequestCP(vmb.getBytes(), param);
        //System.out.println("\nsecondary: "+vmb2.toString());
        UnsignedTypes.printBytes(vmb2.getBytes());

        //System.out.println("old = new: "+(vmb2.toString().equals(vmb.toString())));
    }
}