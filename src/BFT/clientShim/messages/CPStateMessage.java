// $Id: CPStateMessage.java 2927 2009-03-03 20:02:38Z riche $

package BFT.clientShim.messages;

import BFT.messages.MacMessage;
import util.UnsignedTypes;

/**
 * Message sent from one client node to another containing the
 * checkpoint for sequence number $k$
 **/
public class CPStateMessage extends MacMessage {

    public CPStateMessage(long seq, long subId, byte[] cp) {
        super(MessageTags.CPStateMessage, computeSize(cp),
                subId);
        seqNo = seq;
        state = cp;

        byte[] bytes = getBytes();
        int offset = getOffset();
        // copy the sequence number over
        UnsignedTypes.longToBytes(seq, bytes, offset);
        offset += UnsignedTypes.uint32Size;
        // copy the state size over  
        UnsignedTypes.longToBytes(state.length, bytes, offset);
        offset += UnsignedTypes.uint32Size;

        // copy the state itself over
        for (int i = 0; i < cp.length; i++, offset++) {
            bytes[offset] = cp[i];
        }
    }

    public CPStateMessage(byte[] bytes) {
        super(bytes);

        //pull the seqNo out
        int offset = getOffset();
        seqNo = UnsignedTypes.bytesToLong(bytes, offset);
        offset += UnsignedTypes.uint32Size;

        // pull the state size out 
        long length = UnsignedTypes.bytesToLong(bytes, offset);
        offset += UnsignedTypes.uint32Size;

        // pull the state out
        state = new byte[(int) length];
        for (int i = 0; i < length; i++, offset++) {
            state[i] = bytes[offset];
        }
    }

    protected long seqNo;
    private byte[] state;

    public byte[] getState() {
        return state;
    }

    public long getSeqNum() {
        return seqNo;
    }

    private static int computeSize(byte[] cp) {
        return MessageTags.uint32Size + MessageTags.uint32Size + cp.length;
    }

//    public static void main(String args[]){
//	byte[] tmp = new byte[8];
//	for (int i = 0; i < 8; i++)
//	    tmp[i] = (byte)i;
//	CPStateMessage vmb = 
//	    new CPStateMessage(tmp, 1, 2);
//	//System.out.println("initial: "+vmb.toString());
//	UnsignedTypes.printBytes(vmb.getBytes());
//	CPStateMessage vmb2 = 
//	    new CPStateMessage(vmb.getBytes());
//	//System.out.println("\nsecondary: "+vmb2.toString());
//	UnsignedTypes.printBytes(vmb2.getBytes());
//
//	byte[] tmp2 = new byte[4];
//	for (int i = 0; i < 8; i++){
//	    tmp[i] = (byte) (tmp[i] * tmp[i]);
//	    tmp2[i%4] = (byte)(tmp[i]*i);
//	}
//	tmp = tmp2;
//
//	vmb = new CPStateMessage(tmp, 134,8);
//	//System.out.println("initial: "+vmb.toString());
//	UnsignedTypes.printBytes(vmb.getBytes());
//	 vmb2 = new CPStateMessage(vmb.getBytes());
//	//System.out.println("\nsecondary: "+vmb2.toString());
//	UnsignedTypes.printBytes(vmb2.getBytes());
//	
//	//System.out.println("old = new: "+(vmb2.toString().equals(vmb.toString())));
//
//   }
//
}
