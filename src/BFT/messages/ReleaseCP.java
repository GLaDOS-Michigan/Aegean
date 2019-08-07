// $Id: ReleaseCP.java 722 2011-07-04 09:24:35Z aclement $

package BFT.messages;

import BFT.Parameters;
import util.UnsignedTypes;

/**
 * message sent by the order node to the execution node instructing
 * the execution node to release a checkpoint
 **/
public class ReleaseCP extends MacArrayMessage {
    public ReleaseCP(Parameters param, byte[] tok, long seq, long sender) {
        super(param, MessageTags.ReleaseCP,
                computeSize(tok), sender,
                param.getExecutionCount());

        seqNo = seq;
        token = tok;

        // now lets get the bytes
        byte[] bytes = getBytes();
        int offset = getOffset();

        // copy the sequence number over
        UnsignedTypes.longToBytes(seqNo, bytes, offset);
        offset += UnsignedTypes.uint32Size;

        // copy the token size over
        UnsignedTypes.longToBytes((tok == null) ? 0 : tok.length, bytes, offset);
        offset += UnsignedTypes.uint32Size;

        // copy the token itself over
        for (int i = 0; tok != null && i < tok.length; i++, offset++) {
            bytes[offset] = tok[i];
        }
    }


    public ReleaseCP(byte[] bytes, Parameters param) {
        super(bytes, param);
        if (getTag() != MessageTags.ReleaseCP) {
            throw new RuntimeException("invalid message Tag: " + getTag());
        }

        // pull the sequence number

        int offset = getOffset();

        seqNo = UnsignedTypes.bytesToLong(bytes, offset);
        offset += UnsignedTypes.uint32Size;

        // pull the token size out
        long size = UnsignedTypes.bytesToLong(bytes, offset);
        offset += UnsignedTypes.uint32Size;

        // pull the command out
        token = new byte[(int) size];
        for (int i = 0; i < size; i++, offset++) {
            token[i] = bytes[offset];
        }

        if (offset != getBytes().length - getAuthenticationSize()) {
            throw new RuntimeException("Invalid byte input");
        }
    }


    protected byte[] token;
    protected long seqNo;

    public long getSendingReplica() {
        return getSender();
    }

    public byte[] getToken() {
        return token;
    }

    public long getSequenceNumber() {
        return seqNo;
    }

    public boolean equals(ReleaseCP r) {
        boolean res = super.equals(r) &&
                getSendingReplica() == r.getSendingReplica() &&
                getSequenceNumber() == r.getSequenceNumber() &&
                getToken().length == r.getToken().length;
        for (int i = 0; i < getToken().length && res; i++) {
            res = res && getToken()[i] == r.getToken()[i];
        }

        return res;
    }

    public boolean matches(VerifiedMessageBase vmb) {
        ReleaseCP r = (ReleaseCP) vmb;
        boolean res = getSequenceNumber() == r.getSequenceNumber() &&
                getToken().length == r.getToken().length;
        for (int i = 0; i < getToken().length && res; i++) {
            res = res && getToken()[i] == r.getToken()[i];
        }
        return res;
    }

    private static int computeSize(byte[] tok) {
        return ((tok == null) ? 0 : tok.length) + MessageTags.uint32Size + MessageTags.uint32Size;
    }

    public String toString() {
        String com = "";
        for (int i = 0; i < 8 && i < token.length; i++) {
            com += token[i] + ",";
        }
        return "<RELEASECP, " + super.toString() + ", seqno:" + seqNo + ", size:" + token.length + ", bytes:" + com + ">";
    }

    public static void main(String args[]) {
        byte[] tmp = new byte[8];
        for (int i = 0; i < 8; i++) {
            tmp[i] = (byte) i;
        }
        Parameters param = new Parameters();
        ReleaseCP vmb = new ReleaseCP(param, tmp, 1, 2);
        //System.out.println("initial: "+vmb.toString());
        UnsignedTypes.printBytes(vmb.getBytes());
        ReleaseCP vmb2 = new ReleaseCP(vmb.getBytes(), param);
        //System.out.println("\nsecondary: "+vmb2.toString());
        UnsignedTypes.printBytes(vmb2.getBytes());

        for (int i = 0; i < 8; i++) {
            tmp[i] = (byte) (tmp[i] * tmp[i]);
        }

        vmb = new ReleaseCP(param, tmp, 134, 8);
        //System.out.println("initial: "+vmb.toString());
        UnsignedTypes.printBytes(vmb.getBytes());
        vmb2 = new ReleaseCP(vmb.getBytes(), param);
        //System.out.println("\nsecondary: "+vmb2.toString());
        UnsignedTypes.printBytes(vmb2.getBytes());

        //System.out.println("old = new: "+vmb.equals(vmb2));
    }
}