// $Id

package BFT.messages;

import BFT.Debug;
import BFT.Parameters;
import util.UnsignedTypes;

public class BatchCompleted extends MacArrayMessage {

    protected CommandBatch commands;
    protected long view;
    protected long seqno;

    public BatchCompleted(Parameters param, NextBatch nb, int sendingReplica) {
        super(param, MessageTags.BatchCompleted,
                computeSize(nb),
                sendingReplica,
                param.getFilterCount());
        commands = nb.getCommands();
        view = nb.getView();
        seqno = nb.getSeqNo();
        // need to write to bytes


        int offset = getOffset();
        byte[] bytes = getBytes();

        // place the view number
        UnsignedTypes.longToBytes(view, bytes, offset);
        offset += UnsignedTypes.uint32Size;

        // place the sequence number
        UnsignedTypes.longToBytes(seqno, bytes, offset);
        offset += UnsignedTypes.uint32Size;
        // place the size of the batch
        UnsignedTypes.longToBytes(getCommands().getSize(), bytes, offset);
        offset += UnsignedTypes.uint32Size;

        // place the number of entries in the batch
        UnsignedTypes.intToBytes(getCommands().getEntries().length, bytes, offset);
        offset += UnsignedTypes.uint16Size;

        // place the batch bytes
        byte[] tmp = getCommands().getBytes();
        for (int i = 0; i < tmp.length; i++, offset++)
            bytes[offset] = tmp[i];


    }

    public BatchCompleted(byte[] bits, Parameters param) {
        super(bits, param);
        if (getTag() != MessageTags.BatchCompleted) {
            BFT.Debug.kill("Bad Tag; " + getTag());
        }

        int offset = getOffset();
        byte[] tmp;

        // read the view number;

        view = UnsignedTypes.bytesToLong(bits, offset);
        offset += UnsignedTypes.uint32Size;

        // read the sequence number

        seqno = UnsignedTypes.bytesToLong(bits, offset);
        offset += UnsignedTypes.uint32Size;


        // read the batch size
        int batchSize = (int) (UnsignedTypes.bytesToLong(bits, offset));
        offset += UnsignedTypes.uint32Size;


        // read the number of entries in the batch
        int count = (int) (UnsignedTypes.bytesToInt(bits, offset));
        offset += UnsignedTypes.uint16Size;

        // read the batch bytes
        tmp = new byte[batchSize];
        for (int i = 0; i < tmp.length; i++, offset++) {
            tmp[i] = bits[offset];
        }
        commands = new CommandBatch(tmp, count, param);
        if (offset != getBytes().length - getAuthenticationSize()) {
            Debug.kill(new RuntimeException("Invalid byte input"));
        }
    }


    public int getSendingReplica() {
        return (int) getSender();
    }

    public long getView() {
        return view;
    }

    public long getSeqNo() {
        return seqno;
    }

    public CommandBatch getCommands() {
        return commands;
    }

    public boolean matches(VerifiedMessageBase vmb) {
        boolean res = vmb.getTag() == getTag();
        if (!res) {
            return false;
        }
        BatchCompleted bc = (BatchCompleted) vmb;
        return res && getView() == bc.getView()
                && getSeqNo() == bc.getSeqNo()
                && getCommands().equals(bc.getCommands());
    }

    static public int computeSize(NextBatch nb) {
        return nb.getCommands().getSize() + 3 * MessageTags.uint32Size + MessageTags.uint16Size;
    }
}