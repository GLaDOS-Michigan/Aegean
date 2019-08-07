// $Id: FetchCheckPointState.java 2927 2009-03-03 20:02:38Z riche $

package BFT.clientShim.messages;

import BFT.messages.MacMessage;
import util.UnsignedTypes;

public class FetchCheckPointState extends MacMessage {

    public FetchCheckPointState(long seq, long subId) {
        super(MessageTags.FetchCheckPointState, computeSize(), subId);

        System.out.println("\tFetchCP: tag=" + MessageTags.FetchCheckPointState);
        seqNo = seq;

        // now lets get the bytes
        byte[] bytes = getBytes();
        int offset = getOffset();
        // copy the sequence number over
        UnsignedTypes.longToBytes(seqNo, bytes, offset);
        offset += UnsignedTypes.uint32Size;
    }

    public FetchCheckPointState(byte[] bytes) {
        super(bytes);

        if (getTag() != MessageTags.FetchCheckPointState) {
            throw new RuntimeException("invalid Message tag");
        }
        // pull the seq out
        int offset = getOffset();
        seqNo = UnsignedTypes.bytesToLong(bytes, offset);
        offset += UnsignedTypes.uint32Size;
    }

    protected long seqNo;

    public int getSendingReplica() {
        return getSender();
    }

    public long getSeqNum() {
        return seqNo;
    }

    private static int computeSize() {
        return MessageTags.uint32Size;
    }


    public String toString() {
        return "<LAST-EXEC, " + super.toString() + ", seqNo:" + seqNo + ">";
    }
//
//    public static void main(String args[]){
//	byte[] tmp = new byte[8];
//	for (int i = 0; i < 8; i++)
//	    tmp[i] = (byte)i;
//	FetchCPMessage vmb = 
//	    new FetchCPMessage( 1, 2);
//	//System.out.println("initial: "+vmb.toString());
//	UnsignedTypes.printBytes(vmb.getBytes());
//	FetchCPMessage vmb2 = 
//	    new FetchCPMessage(vmb.getBytes());
//	//System.out.println("\nsecondary: "+vmb2.toString());
//	UnsignedTypes.printBytes(vmb2.getBytes());
//
//	for (int i = 0; i < 8; i++)
//	    tmp[i] = (byte) (tmp[i] * tmp[i]);
//
//	vmb = new FetchCPMessage( 134,8);
//	//System.out.println("initial: "+vmb.toString());
//	UnsignedTypes.printBytes(vmb.getBytes());
//	 vmb2 = new FetchCPMessage(vmb.getBytes());
//	//System.out.println("\nsecondary: "+vmb2.toString());
//	UnsignedTypes.printBytes(vmb2.getBytes());
// 
//	//System.out.println("old = new: "+(vmb2.toString().equals(vmb.toString())));
//
//   }

}
