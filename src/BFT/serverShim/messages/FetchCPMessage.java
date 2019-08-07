// $Id: FetchCPMessage.java 2927 2009-03-03 20:02:38Z riche $

package BFT.serverShim.messages;

import BFT.Parameters;
import BFT.messages.MacArrayMessage;
import util.UnsignedTypes;

public class FetchCPMessage extends MacArrayMessage {

    public FetchCPMessage(Parameters param, long seq, long sender) {
        super(param, MessageTags.FetchCPMessage,
                computeSize(), sender,
                param.getExecutionCount());

        seqNo = seq;

        // now lets get the bytes
        byte[] bytes = getBytes();
        int offset = getOffset();
        // copy the sequence number over
        UnsignedTypes.longToBytes(seqNo, bytes, offset);
        offset += UnsignedTypes.uint32Size;
    }


    public FetchCPMessage(byte[] bytes, Parameters param) {
        super(bytes, param);
        if (getTag() != MessageTags.FetchCPMessage) {
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
        return "<FetchCP, tags=" + MessageTags.FetchCPMessage + super.toString() + ", seqNo:" + seqNo + ">";
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
