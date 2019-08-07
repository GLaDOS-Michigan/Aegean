// $Id: CPTokenMessage.java 722 2011-07-04 09:24:35Z aclement $

package BFT.messages;

import BFT.Parameters;
import util.UnsignedTypes;

/**
 * Message sent from the execution node to the order node containing a
 * cp token
 **/
public class CPTokenMessage extends MacArrayMessage {
    public CPTokenMessage(Parameters param, byte[] tok, long seq, long sender) {
        super(param, MessageTags.CPTokenMessage,
                computeSize(tok), sender,
                param.getOrderCount());
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
        for (int i = 0; i < tok.length; i++, offset++) {
            bytes[offset] = tok[i];
        }
    }


    public CPTokenMessage(byte[] bytes, Parameters param) {
        super(bytes, param);
        if (getTag() != MessageTags.CPTokenMessage) {
            throw new RuntimeException("invalid message Tag: " + getTag());
        }

        // pull the sequence number
        int offset = getOffset();
        seqNo = UnsignedTypes.bytesToLong(bytes, offset);
        offset += UnsignedTypes.uint32Size;

        // pull the token size out
        long size = UnsignedTypes.bytesToLong(bytes, offset);
        offset += UnsignedTypes.uint32Size;

        // pull the token out
        token = new byte[(int) size];
        for (int i = 0; i < size; i++, offset++) {
            token[i] = bytes[offset];
        }

        if (offset != bytes.length - getAuthenticationSize()) {
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


    private static int computeSize(byte[] tok) {
        return tok.length + MessageTags.uint32Size + MessageTags.uint32Size;
    }


    public boolean equals(CPTokenMessage cpt) {
        boolean res = cpt != null && super.equals(cpt) && matches(cpt);
        return res;
    }

    public boolean matches(VerifiedMessageBase vmb) {
        CPTokenMessage cpt = (CPTokenMessage) vmb;
        boolean res = cpt != null && token.length == cpt.token.length &&
                seqNo == cpt.seqNo;
        //System.out.println("first res: "+res);
        for (int i = 0; res && i < token.length; i++)
            res = res && token[i] == cpt.token[i];
        //System.out.println("second res: "+res);
        if (!res) {
            //BFT.util.UnsignedTypes.printBytes((token));
            //BFT.util.UnsignedTypes.printBytes((cpt.token));
        }
        return res;
    }


    public String toString() {
        String com = "";
        for (int i = 0; i < 8 && i < token.length; i++) {
            com += token[i] + ",";
        }
        return "<CPTOKEN, " + super.toString() + ", seqno:" + seqNo + ", size:" + token.length + ", bytes:" + com + ">";
    }


    public static void main(String args[]) {
        byte[] tmp = new byte[8];
        for (int i = 0; i < 8; i++) {
            tmp[i] = (byte) i;
        }
        Parameters param = new Parameters();
        CPTokenMessage vmb = new CPTokenMessage(param, tmp, 1, 2);
        //System.out.println("initial: "+vmb.toString());
        UnsignedTypes.printBytes(vmb.getBytes());
        CPTokenMessage vmb2 = new CPTokenMessage(vmb.getBytes(), param);
        //System.out.println("\nsecondary: "+vmb2.toString());
        UnsignedTypes.printBytes(vmb2.getBytes());

        byte[] tmp2 = new byte[4];
        for (int i = 0; i < 8; i++) {
            tmp[i] = (byte) (tmp[i] * tmp[i]);
            tmp2[i % 4] = (byte) (tmp[i] * i);
        }
        tmp = tmp2;

        vmb = new CPTokenMessage(param, tmp, 134, 8);
        //System.out.println("initial: "+vmb.toString());
        UnsignedTypes.printBytes(vmb.getBytes());
        vmb2 = new CPTokenMessage(vmb.getBytes(), param);
        //System.out.println("\nsecondary: "+vmb2.toString());
        UnsignedTypes.printBytes(vmb2.getBytes());

        //System.out.println("old = new: "+(vmb2.toString().equals(vmb.toString())));
    }
}