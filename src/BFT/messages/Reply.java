// $Id: Reply.java 722 2011-07-04 09:24:35Z aclement $

package BFT.messages;

import BFT.Parameters;
import util.UnsignedTypes;

/**
 * Reply sent to a client after its request has been executed
 **/
public class Reply extends MacMessage {


    /**
     * Construct that accepts specific message fields.  This
     * constructor builds up the message byte representation starting
     * from where VerifiedMessageBase leaves off.
     **/
    public Reply(long execReplica, long sequence,
                 byte[] com) {
        super(tag(), computeSize(com), execReplica);
        seqNo = sequence;
        command = com;

        // now lets get the bytes
        byte[] bytes = getBytes();
        int offset = getOffset();

        // copy the sequence number over
        UnsignedTypes.longToBytes(sequence, bytes, offset);
        offset += UnsignedTypes.uint32Size;

        // copy the command size over
        UnsignedTypes.longToBytes(com.length, bytes, offset);
        offset += UnsignedTypes.uint32Size;

        // copy the command itself over
        for (int i = 0; i < com.length; i++, offset++) {
            bytes[offset] = com[i];
        }
    }

    /**
     * Constructor accepting a byte representation of the message.
     * Parses the byte representation to populate the class fields.
     **/
    public Reply(byte[] bytes) {
        super(bytes);
        if (getTag() != MessageTags.Reply) {
            throw new RuntimeException("invalid message Tag: " + getTag());
        }

        int offset = getOffset();

        // pull the request id out.
        byte[] tmp;
        seqNo = UnsignedTypes.bytesToLong(bytes, offset);
        offset += UnsignedTypes.uint32Size;

        // pull the command size out
        long size = UnsignedTypes.bytesToLong(bytes, offset);
        offset += UnsignedTypes.uint32Size;

        // pull the command out
        command = new byte[(int) size];
        for (int i = 0; i < size; i++, offset++) {
            command[i] = bytes[offset];
        }

        if (offset != bytes.length - getAuthenticationSize()) {
            throw new RuntimeException("Invalid byte input");
        }
    }

    private long seqNo;
    private byte[] command;

    /**
     * gets the identifier of the sending client
     **/
    public long getSendingReplica() {
        return getSender();
    }


    /**
     * gets the request identifier/sequence number
     **/
    public long getRequestId() {
        return seqNo;
    }

    /**
     * retrieves the byte representation of the command
     **/
    public byte[] getCommand() {
        return command;
    }

    /**
     * Total size of the reply message based on the definition in
     * reply.hh and verifiable_msg.hh
     **/
    static private int computeSize(byte[] com) {
        return MessageTags.uint32Size + MessageTags.uint32Size + com.length;
    }

    public static int tag() {
//        System.out.println("using reply methods");
        return MessageTags.Reply;
    }

    public boolean equals(Reply rep) {
        boolean res = rep != null && super.equals(rep);
        res = res && matches(rep);
        return res;
    }

    public boolean matches(VerifiedMessageBase vmb) {
        Reply rep = (Reply) vmb;
        boolean res = rep != null &&
                command.length == rep.command.length &&
                rep.seqNo == seqNo;
        for (int i = 0; res && i < command.length; i++) {
            res = res && command[i] == rep.command[i];
        }
        return res;
    }


    public String toString() {
        String com = "";
        com += new Digest(new Parameters(), command);
        // 	for (int i = 0; i < command.length; i++){
        // 	    com += command[i]+",";
        // 	}
        return "< REP, " + super.toString() + ", reqId:" + seqNo + ", commandDigest: " + com + ">";
    }

    public static void main(String args[]) {
        byte[] tmp = new byte[8];
        for (int i = 0; i < 8; i++) {
            tmp[i] = (byte) i;
        }
        Reply vmb = new Reply(1, 0, tmp);
        //System.out.println("initial: "+vmb.toString());
        UnsignedTypes.printBytes(vmb.getBytes());
        Reply vmb2 = new Reply(vmb.getBytes());
        //System.out.println("\nsecondary: "+vmb2.toString());
        UnsignedTypes.printBytes(vmb2.getBytes());

        for (int i = 0; i < 8; i++) {
            tmp[i] = (byte) (tmp[i] * tmp[i]);
        }

        vmb = new Reply(134, 8, tmp);
        //System.out.println("initial: "+vmb.toString());
        UnsignedTypes.printBytes(vmb.getBytes());
        vmb2 = new Reply(vmb.getBytes());
        //System.out.println("\nsecondary: "+vmb2.toString());
        UnsignedTypes.printBytes(vmb2.getBytes());

        //System.out.println("old = new: "+(vmb2.toString().equals(vmb.toString())));
    }
}
