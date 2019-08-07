// $Id: FetchStateMessage.java 107 2010-03-21 20:33:32Z yangwang $
package BFT.exec.messages;

import BFT.Parameters;
import BFT.messages.MacArrayMessage;
import BFT.util.UnsignedTypes;

import java.util.Arrays;

/**

 **/
public class PBFetchStateMessage extends MacArrayMessage {

    public PBFetchStateMessage(Parameters param, long currentSeqNo, int[] indexes, int sendingReplica) {
        super(param, MessageTags.PBFetchState,
                computeSize(currentSeqNo, indexes),
                sendingReplica,
                param.getExecutionCount());

        this.currentSeqNo = currentSeqNo;

        int offset = getOffset();
        byte[] bytes = getBytes();
        // place the current sequence number
        byte[] tmp = UnsignedTypes.longToBytes(currentSeqNo);
        for (int i = 0; i < tmp.length; i++, offset++) {
            bytes[offset] = tmp[i];
        }

        // copy the size of the indexes
        //System.out.println("size of state: "+state.length);
        tmp = UnsignedTypes.longToBytes(indexes.length);
        for (int i = 0; i < tmp.length; i++, offset++)
            bytes[offset] = tmp[i];
        // copy the token
        for (int j = 0; j < indexes.length; j++) {
            tmp = UnsignedTypes.longToBytes(indexes[j]);
            for (int i = 0; i < tmp.length; i++, offset++) {
                bytes[offset] = tmp[i];
            }
        }
    }

    public PBFetchStateMessage(byte[] bits, Parameters param) {
        super(bits, param);
        if (getTag() != MessageTags.PBFetchState) {
            throw new RuntimeException("invalid message Tag: " + getTag());
        }
        int offset = getOffset();
        byte[] tmp;

        // read the current sequence number;
        tmp = new byte[4];
        for (int i = 0; i < tmp.length; i++, offset++) {
            tmp[i] = bits[offset];
        }
        currentSeqNo = UnsignedTypes.bytesToLong(tmp);

        // read the indexes size
        for (int i = 0; i < tmp.length; i++, offset++) {
            tmp[i] = bits[offset];
        }
        indexes = new int[(int) UnsignedTypes.bytesToLong(tmp)];
        for (int j = 0; j < indexes.length; j++) {
            for (int i = 0; i < tmp.length; i++, offset++) {
                tmp[i] = bits[offset];
            }
            indexes[j] = (int) UnsignedTypes.bytesToLong(tmp);
        }
    }


    protected long currentSeqNo;

    public long getCurrentSeqNo() {
        return currentSeqNo;
    }

    protected int[] indexes;

    public int[] getIndexes() {
        return indexes;
    }


    public long getSendingReplica() {
        return getSender();
    }

    public boolean equals(PBFetchStateMessage nb) {
        return super.equals(nb) && currentSeqNo == nb.currentSeqNo && Arrays.equals(nb.indexes, this.indexes);
    }

    /**
     * computes the size of the bits specific to PrePrepare
     **/
    private static int computeSize(long currentSeqNo, int[] indexes) {
        int size = MessageTags.uint32Size + MessageTags.uint32Size + MessageTags.uint32Size * indexes.length;
        return size;
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("<PBFetchStateMessage, " + getCurrentSeqNo() + " indexes=");
        for (int i = 0; i < indexes.length; i++) {
            builder.append(" " + indexes[i]);
        }
        return builder.toString();
    }
}
