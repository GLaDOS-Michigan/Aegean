// $Id: MissingCP.java 722 2011-07-04 09:24:35Z aclement $

package BFT.order.messages;

import BFT.Parameters;
import BFT.messages.Digest;
import BFT.messages.MacArrayMessage;
import util.UnsignedTypes;

/**

 **/
public class MissingCP extends MacArrayMessage {


    public MissingCP(Parameters param, long seqno, Digest cpDig, int sendingReplica) {
        super(param, MessageTags.MissingCP, computeSize(param), sendingReplica,
                param.getOrderCount());
        seqNo = seqno;
        cpDigest = cpDig;

        int offset = getOffset();
        byte[] bytes = getBytes();
        // place the sequence number
        byte[] tmp;
        UnsignedTypes.longToBytes(seqNo, bytes, offset);
        offset += UnsignedTypes.uint32Size;
        // place the cpDigest
        tmp = cpDig.getBytes();
        for (int i = 0; i < tmp.length; i++, offset++) {
            bytes[offset] = tmp[i];
        }
    }

    public MissingCP(byte[] bits, Parameters param) {
        super(bits, param);
        int offset = getOffset();
        byte[] tmp;

        // read the sequence number;
        seqNo = UnsignedTypes.bytesToLong(bits, offset);
        offset += UnsignedTypes.uint32Size;

        // read the digest
        tmp = new byte[Digest.size(param)];
        for (int i = 0; i < tmp.length; i++, offset++) {
            tmp[i] = bits[offset];
        }
        cpDigest = Digest.fromBytes(param, tmp);

        if (offset != getBytes().length - getAuthenticationSize()) {
            throw new RuntimeException("invalid byte array");
        }
    }

    protected long seqNo;

    public long getSeqNo() {
        return seqNo;
    }

    protected Digest cpDigest;

    public Digest getCPDigest() {
        return cpDigest;
    }


    public int getSendingReplica() {
        return (int) getSender();
    }


    /**
     * computes the size of the bits specific to MissingCP
     **/
    private static int computeSize(Parameters param) {
        int size = UnsignedTypes.uint32Size + Digest.size(param);
        return size;
    }
}