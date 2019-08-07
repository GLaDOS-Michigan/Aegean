// $Id: Retransmit.java 722 2011-07-04 09:24:35Z aclement $

package BFT.messages;

import BFT.Parameters;
import util.UnsignedTypes;

/**
 * message instructing the execution node to retransmit result of the
 * last request executed for the specified client
 **/
public class Retransmit extends MacArrayMessage {

    public Retransmit(Parameters param, long clientId, long seqno, long reqId, long sender) {
        super(param, tag(), computeSize(), sender, param.getExecutionCount());
        client = clientId;
        this.seqno = seqno;
        this.reqId = reqId;
        byte[] bytes = getBytes();
        int offset = getOffset();
        // copy the client to the byte array
        UnsignedTypes.longToBytes(client, bytes, offset);
        offset += UnsignedTypes.uint32Size;
        // copy the seqno to the byte array
        UnsignedTypes.longToBytes(seqno, bytes, offset);
        offset += UnsignedTypes.uint32Size;
        // copy the reqId to the byte array
        UnsignedTypes.longToBytes(reqId, bytes, offset);
        offset += UnsignedTypes.uint32Size;
    }

    public Retransmit(byte[] bytes, Parameters param) {
        super(bytes, param);
        if (getTag() != MessageTags.Retransmit) {
            throw new RuntimeException("invalid message Tag: " + getTag());
        }

        int offset = getOffset();
        // pull the client id off the bytes
        client = UnsignedTypes.bytesToLong(bytes, offset);
        offset += UnsignedTypes.uint32Size;
        // pull the seqno
        seqno = UnsignedTypes.bytesToLong(bytes, offset);
        offset += UnsignedTypes.uint32Size;
        // pull the reqId 
        reqId = UnsignedTypes.bytesToLong(bytes, offset);
        offset += UnsignedTypes.uint32Size;

        if (offset != bytes.length - getAuthenticationSize()) {
            throw new RuntimeException("Invalid byte input");
        }
    }

    protected long client;
    protected long seqno;
    protected long reqId;

    public long getRequestId() {
        return reqId;
    }

    static private int computeSize() {
        return MessageTags.uint32Size + MessageTags.uint32Size + MessageTags.uint32Size;
    }

    public long getClient() {
        return client;
    }

    public long getSequenceNumber() {
        return seqno;
    }

    public long getSendingReplica() {
        return getSender();
    }

    public static int tag() {
        return MessageTags.Retransmit;
    }

    public boolean equals(Retransmit ret) {
        boolean res = super.equals(ret) && seqno == ret.seqno &&
                client == ret.client;
        return res;
    }

    public boolean matches(VerifiedMessageBase vmb) {
        Retransmit ret = (Retransmit) vmb;
        boolean res = seqno == ret.seqno && client == ret.client;
        return res;
    }

    public String toString() {
        return "< RETRANS, " + super.toString() + ", client:" + client + ", seqno: " + seqno + ", reqid=" + reqId + ">";
    }

    public static void main(String args[]) {
        Parameters param = new Parameters();
        Retransmit vmb = new Retransmit(param, 1, 2, 3, 4);
        //System.out.println("initial: "+vmb.toString());
        UnsignedTypes.printBytes(vmb.getBytes());
        Retransmit vmb2 = new Retransmit(vmb.getBytes(), param);
        //System.out.println("\nsecondary: "+vmb2.toString());
        UnsignedTypes.printBytes(vmb2.getBytes());

        vmb = new Retransmit(param, 134, 8, 3, 4);
        //System.out.println("initial: "+vmb.toString());
        UnsignedTypes.printBytes(vmb.getBytes());
        vmb2 = new Retransmit(vmb.getBytes(), param);
        //System.out.println("\nsecondary: "+vmb2.toString());
        UnsignedTypes.printBytes(vmb2.getBytes());

        //System.out.println("old = new: "+(vmb2.toString().equals(vmb.toString())));
    }
}
