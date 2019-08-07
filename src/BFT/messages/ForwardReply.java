package BFT.messages;

import util.UnsignedTypes;

public class ForwardReply extends MacMessage {
    private long clientId;
    private byte[] command;

    public ForwardReply() {
        this(-1, -1, new byte[0]);
    }

    public ForwardReply(long execReplica, long sequence,
                 byte[] com) {
        super(tag(), computeSize(com), execReplica);
        clientId = sequence;
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

    public ForwardReply(byte[] bytes) {
        super(bytes);
        if (getTag() != MessageTags.ForwardReply) {
            throw new RuntimeException("invalid message Tag: " + getTag());
        }

        int offset = getOffset();

        // pull the request id out.
        byte[] tmp;
        clientId = UnsignedTypes.bytesToLong(bytes, offset);
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

    public static int tag() {
//        System.out.println("using forward reply methods");
        return MessageTags.ForwardReply;
    }

    static private int computeSize(byte[] com) {
        return MessageTags.uint32Size + MessageTags.uint32Size + com.length;
    }

    public byte[] getCommand() {
        return command;
    }

    public long getClientId() {
        return clientId;
    }
}
