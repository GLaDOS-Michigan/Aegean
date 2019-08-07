
package BFT.messages;

import util.UnsignedTypes;

public class OldRequestMessage extends MacMessage {

    private long seqNo;
    private byte[] digest;

    public OldRequestMessage(long filterReplica, long seq, byte[] d) {
        super(MessageTags.OldRequestMessage, computeSize(d), filterReplica);
        seqNo = seq;
        digest = d;
        byte[] bytes = getBytes();
        int offset = getOffset();

        UnsignedTypes.longToBytes(seq, bytes, offset);
        offset += UnsignedTypes.uint32Size;

        UnsignedTypes.longToBytes(d.length, bytes, offset);
        offset += UnsignedTypes.uint32Size;

        for (int i = 0; i < d.length; i++, offset++) {
            bytes[offset] = d[i];
        }
    }

    public OldRequestMessage(byte[] bytes) {
        super(bytes);
        if (getTag() != MessageTags.OldRequestMessage) {
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
        // pull the digest out        
        digest = new byte[(int) size];
        for (int i = 0; i < size; i++, offset++) {
            digest[i] = bytes[offset];
        }
    }

    public long getRequestId() {
        return seqNo;
    }

    public long getSendingReplica() {
        return getSender();
    }

    public byte[] getDigest() {
        return digest;
    }

    static private int computeSize(byte[] com) {
        return MessageTags.uint32Size + com.length + MessageTags.uint32Size;
    }
}
