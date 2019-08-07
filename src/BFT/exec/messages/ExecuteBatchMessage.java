// $Id: ExecuteBatchMessage.java 573 2010-09-16 20:25:58Z yangwang $
package BFT.exec.messages;

import BFT.Parameters;
import BFT.messages.MacArrayMessage;
import BFT.messages.NonDeterminism;
import BFT.util.UnsignedTypes;

/**

 **/
public class ExecuteBatchMessage extends MacArrayMessage {

    public ExecuteBatchMessage(Parameters param, long view, long seq,
                               RequestBatch batch, NonDeterminism non,
                               int sendingReplica) {
        super(param, MessageTags.ExecuteBatch,
                computeSize(view, seq, batch, non),
                sendingReplica,
                param.getExecutionCount());

        viewNo = view;
        seqNo = seq;
        reqBatch = batch;
        nondet = non;

        int offset = getOffset();
        byte[] bytes = getBytes();
        // place the view number
        byte[] tmp = UnsignedTypes.longToBytes(viewNo);
        for (int i = 0; i < tmp.length; i++, offset++) {
            bytes[offset] = tmp[i];
        }

        // place the sequence number
        tmp = UnsignedTypes.longToBytes(seqNo);
        for (int i = 0; i < tmp.length; i++, offset++) {
            bytes[offset] = tmp[i];
        }

        // place the nondeterminism
        // size first
        tmp = UnsignedTypes.longToBytes(nondet.getSize());
        for (int i = 0; i < tmp.length; i++, offset++) {
            bytes[offset] = tmp[i];
        }

        // now the nondet bytes
        tmp = non.getBytes();
        for (int i = 0; i < tmp.length; i++, offset++) {
            bytes[offset] = tmp[i];
        }

        // place the size of the batch
        tmp = UnsignedTypes.longToBytes(batch.getSize());
        for (int i = 0; i < tmp.length; i++, offset++) {
            bytes[offset] = tmp[i];
        }

        // place the number of entries in the batch
        tmp = UnsignedTypes.intToBytes(batch.getEntries().length);
        for (int i = 0; i < tmp.length; i++, offset++) {
            bytes[offset] = tmp[i];
        }

        // place the batch bytes
        tmp = batch.getBytes();
        for (int i = 0; i < tmp.length; i++, offset++) {
            bytes[offset] = tmp[i];
        }
    }

    public ExecuteBatchMessage(byte[] bits, Parameters param) {
        super(bits, param);
        if (getTag() != MessageTags.ExecuteBatch) {
            throw new RuntimeException("invalid message Tag: " + getTag());
        }
        int offset = getOffset();
        byte[] tmp;

        // read the view number;
        tmp = new byte[4];
        for (int i = 0; i < tmp.length; i++, offset++) {
            tmp[i] = bits[offset];
        }
        viewNo = UnsignedTypes.bytesToLong(tmp);

        // read the sequence number
        for (int i = 0; i < tmp.length; i++, offset++) {
            tmp[i] = bits[offset];
        }
        seqNo = UnsignedTypes.bytesToLong(tmp);


        // read the non det size
        tmp = new byte[4];
        for (int i = 0; i < tmp.length; i++, offset++) {
            tmp[i] = bits[offset];
        }
        int nondetSize = (int) (UnsignedTypes.bytesToLong(tmp));
        // read the nondeterminism
        tmp = new byte[nondetSize];
        for (int i = 0; i < tmp.length; i++, offset++) {
            tmp[i] = bits[offset];
        }
        nondet = new NonDeterminism(tmp);


        // read the batch size
        tmp = new byte[4];
        for (int i = 0; i < tmp.length; i++, offset++) {
            tmp[i] = bits[offset];
        }
        int batchSize = (int) (UnsignedTypes.bytesToLong(tmp));

        // read the number of entries in the batch
        tmp = new byte[2];
        for (int i = 0; i < tmp.length; i++, offset++) {
            tmp[i] = bits[offset];
        }
        int count = (int) (UnsignedTypes.bytesToInt(tmp));

        // read the batch bytes
        tmp = new byte[batchSize];
        for (int i = 0; i < tmp.length; i++, offset++) {
            tmp[i] = bits[offset];
        }
        reqBatch = new RequestBatch(tmp, count, param);

        if (offset != getBytes().length - getAuthenticationSize()) {
            throw new RuntimeException("Invalid byte input");
        }

    }

    protected long viewNo;

    public long getView() {
        return viewNo;
    }

    protected long seqNo;

    public long getSeqNo() {
        return seqNo;
    }

    protected RequestBatch reqBatch;

    public RequestBatch getRequestBatch() {
        return reqBatch;
    }

    protected NonDeterminism nondet;

    public NonDeterminism getNonDeterminism() {
        return nondet;
    }

    public long getSendingReplica() {
        return getSender();
    }

    // THis is only used in primary backup and not in serialization
    private boolean needBackup = true;

    public void setNeedBackup(boolean value) {
        this.needBackup = value;
    }

    public boolean needBackup() {
        return this.needBackup;
    }

    public boolean equals(ExecuteBatchMessage nb) {
        return super.equals(nb) && viewNo == nb.viewNo && seqNo == nb.seqNo
                && nondet.equals(nb.nondet) && reqBatch.equals(nb.reqBatch);
    }

    /**
     * computes the size of the bits specific to PrePrepare
     **/
    private static int computeSize(long view, long seq,
                                   RequestBatch batch, NonDeterminism non) {
        int size = MessageTags.uint32Size + MessageTags.uint32Size
                + MessageTags.uint32Size + non.getSize() + MessageTags.uint16Size
                + MessageTags.uint32Size + batch.getSize();
        return size;
    }

    public String toString() {
        return "<ExecuteBatch, " + getView() + ", " + getSeqNo() + ", "
                + nondet + ", " + reqBatch + ">";
    }
}
