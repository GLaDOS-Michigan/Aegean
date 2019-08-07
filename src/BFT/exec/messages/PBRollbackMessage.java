// $Id: VerifyMessage.java 158 2010-04-03 04:30:08Z manos $

package BFT.exec.messages;

import BFT.Parameters;
import BFT.messages.MacArrayMessage;
import BFT.util.UnsignedTypes;

/**
 * @author yangwang
 */
public class PBRollbackMessage extends MacArrayMessage {

    private long seqNo;

    public PBRollbackMessage(Parameters param, long seq, int sendingReplica) {
        super(param, MessageTags.PBRollback,
                computeSize(seq),
                sendingReplica,
                param.getVerifierCount());
        this.seqNo = seq;

        int offset = getOffset();
        byte[] bytes = getBytes();
        byte[] tmp;

        // place the sequence number
        tmp = UnsignedTypes.longToBytes(seqNo);
        for (int i = 0; i < tmp.length; i++, offset++) {
            bytes[offset] = tmp[i];
        }
    }


    public PBRollbackMessage(byte[] bits, Parameters param) {
        super(bits, param);
        if (getTag() != MessageTags.PBRollback) {
            throw new RuntimeException("invalid message Tag: " + getTag());
        }
        int offset = getOffset();
        byte[] tmp = new byte[4];


        // read the sequence number
        for (int i = 0; i < tmp.length; i++, offset++) {
            tmp[i] = bits[offset];
        }
        seqNo = UnsignedTypes.bytesToLong(tmp);

        if (offset != getBytes().length - getAuthenticationSize()) {
            throw new RuntimeException("Invalid byte input");
        }
    }

    public long getSeqNo() {
        return this.seqNo;
    }

    public int getSendingReplica() {
        return (int) sender;
    }

    private static int computeSize(long seq) {
        int size = MessageTags.uint32Size; // seqNo
        return size;
    }

    public boolean equals(PBRollbackMessage nb) {
        return super.equals(nb) && seqNo == nb.seqNo;
    }

    @Override
    public String toString() {
        return "<PBRollbackMessage, " + getSeqNo() + ">";
    }
}
