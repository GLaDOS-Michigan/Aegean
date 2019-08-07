// $Id: FetchStateMessage.java 107 2010-03-21 20:33:32Z yangwang $
package BFT.exec.messages;

import BFT.Parameters;
import BFT.messages.MacArrayMessage;
import BFT.util.UnsignedTypes;

/**

 **/
public class FetchStateMessage extends MacArrayMessage {

    public FetchStateMessage(Parameters param, long currentSeqNo, long targetSeqNo, int sendingReplica) {
        super(param, MessageTags.FetchState,
                computeSize(currentSeqNo, targetSeqNo),
                sendingReplica,
                param.getExecutionCount());

        this.currentSeqNo = currentSeqNo;
        this.targetSeqNo = targetSeqNo;

        int offset = getOffset();
        byte[] bytes = getBytes();
        // place the current sequence number
        byte[] tmp = UnsignedTypes.longToBytes(currentSeqNo);
        for (int i = 0; i < tmp.length; i++, offset++) {
            bytes[offset] = tmp[i];
        }

        // place the target sequence number
        tmp = UnsignedTypes.longToBytes(targetSeqNo);
        for (int i = 0; i < tmp.length; i++, offset++) {
            bytes[offset] = tmp[i];
        }
    }

    public FetchStateMessage(byte[] bits, Parameters param) {
        super(bits, param);
        if (getTag() != MessageTags.FetchState) {
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

        // read the target sequence number
        for (int i = 0; i < tmp.length; i++, offset++) {
            tmp[i] = bits[offset];
        }
        targetSeqNo = UnsignedTypes.bytesToLong(tmp);
    }


    protected long currentSeqNo;

    public long getCurrentSeqNo() {
        return currentSeqNo;
    }

    protected long targetSeqNo;

    public long getTargetSeqNo() {
        return targetSeqNo;
    }

    public long getSendingReplica() {
        return getSender();
    }

    public boolean equals(FetchStateMessage nb) {
        return super.equals(nb) && currentSeqNo == nb.currentSeqNo && targetSeqNo == nb.targetSeqNo;
    }

    /**
     * computes the size of the bits specific to PrePrepare
     **/
    private static int computeSize(long currentSeqNo, long targetSeqNo) {
        int size = MessageTags.uint32Size + MessageTags.uint32Size;
        return size;
    }

    public String toString() {
        return "<FetchStateMessage, " + getCurrentSeqNo() + ", " + getTargetSeqNo() + ">";
    }
}
