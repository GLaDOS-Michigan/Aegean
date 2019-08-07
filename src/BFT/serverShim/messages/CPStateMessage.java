// $Id: CPStateMessage.java 2927 2009-03-03 20:02:38Z riche $

package BFT.serverShim.messages;

import BFT.messages.MacMessage;
import util.UnsignedTypes;

/**
 * Message sent from one execution node to another containing the
 * checkpoint for sequence number $k$
 **/
public class CPStateMessage extends MacMessage {
    public CPStateMessage(byte[] tok, long seq, long sender) {
        super(MessageTags.CPStateMessage,
                computeSize(tok), sender);


        seqNo = seq;
        token = tok;

        // now lets get the bytes
        byte[] bytes = getBytes();
        int offset = getOffset();
        // copy the sequence number over
        UnsignedTypes.longToBytes(seqNo, bytes, offset);
        offset += UnsignedTypes.uint32Size;

        // copy the token size over
        UnsignedTypes.longToBytes(tok.length, bytes, offset);
        offset += UnsignedTypes.uint32Size;

        // copy the token itself over
        for (int i = 0; i < tok.length; i++, offset++)
            bytes[offset] = tok[i];
    }


    public CPStateMessage(byte[] bytes) {
        super(bytes);
        if (getTag() != MessageTags.CPStateMessage)
            throw new RuntimeException("invalid message Tag: " + getTag());


        // pull the sequence number

        int offset = getOffset();

        seqNo = UnsignedTypes.bytesToLong(bytes, offset);
        offset += UnsignedTypes.uint32Size;

        // pull the token size out
        long size = UnsignedTypes.bytesToLong(bytes, offset);
        offset += UnsignedTypes.uint32Size;

        // pull the token out
        token = new byte[(int) size];
        for (int i = 0; i < size; i++, offset++)
            token[i] = bytes[offset];

        if (offset != bytes.length - getAuthenticationSize())
            throw new RuntimeException("Invalid byte input");
    }


    protected byte[] token;
    protected long seqNo;

    public long getSendingReplica() {
        return getSender();
    }

    public byte[] getState() {
        return token;
    }

    public long getSequenceNumber() {
        return seqNo;
    }


    private static int computeSize(byte[] tok) {
        return tok.length + MessageTags.uint32Size + MessageTags.uint32Size;
    }


    public boolean equals(CPStateMessage cpt) {
        boolean res = cpt != null && super.equals(cpt) && matches(cpt);
        return res;
    }


    public String toString() {
        String com = "";
        for (int i = 0; i < 8 && i < token.length; i++)
            com += token[i] + ",";
        return "<CPTOKEN, " + super.toString() + ", seqno:" + seqNo + ", size:" + token.length + ", bytes:" + com + ">";
    }


    public static void main(String args[]) {
        byte[] tmp = new byte[8];
        for (int i = 0; i < 8; i++)
            tmp[i] = (byte) i;
        CPStateMessage vmb =
                new CPStateMessage(tmp, 1, 2);
        //System.out.println("initial: "+vmb.toString());
        UnsignedTypes.printBytes(vmb.getBytes());
        CPStateMessage vmb2 =
                new CPStateMessage(vmb.getBytes());
        //System.out.println("\nsecondary: "+vmb2.toString());
        UnsignedTypes.printBytes(vmb2.getBytes());

        byte[] tmp2 = new byte[4];
        for (int i = 0; i < 8; i++) {
            tmp[i] = (byte) (tmp[i] * tmp[i]);
            tmp2[i % 4] = (byte) (tmp[i] * i);
        }
        tmp = tmp2;

        vmb = new CPStateMessage(tmp, 134, 8);
        //System.out.println("initial: "+vmb.toString());
        UnsignedTypes.printBytes(vmb.getBytes());
        vmb2 = new CPStateMessage(vmb.getBytes());
        //System.out.println("\nsecondary: "+vmb2.toString());
        UnsignedTypes.printBytes(vmb2.getBytes());

        //System.out.println("old = new: "+(vmb2.toString().equals(vmb.toString())));

    }

}