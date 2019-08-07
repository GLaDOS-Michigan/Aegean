// $Id

package BFT.messages;

import BFT.Debug;
import BFT.Parameters;
import util.UnsignedTypes;

public class FetchCommand extends MacArrayMessage {

    protected Entry entry;
    protected long seqno;

    public FetchCommand(Parameters param, long seq, Entry ent, int sendingReplica) {
        super(param, MessageTags.FetchCommand,
                computeSize(ent),
                sendingReplica,
                param.getFilterCount());
        seqno = seq;
        entry = ent;
        // need to write to bytes

        int offset = getOffset();
        byte[] bytes = getBytes();

        // place the sequence number
        byte[] tmp;
        UnsignedTypes.longToBytes(seqno, bytes, offset);
        offset += UnsignedTypes.uint32Size;

        // place the batch bytes
        tmp = entry.getBytes();
        for (int i = 0; i < tmp.length; i++, offset++) {
            bytes[offset] = tmp[i];
        }
    }

    public FetchCommand(byte[] bits, Parameters param) {
        super(bits, param);
        if (getTag() != MessageTags.FetchCommand) {
            BFT.Debug.kill("Bad Tag; " + getTag());
        }

        int offset = getOffset();

        // read the sequence number
        seqno = UnsignedTypes.bytesToLong(bits, offset);
        offset += UnsignedTypes.uint32Size;

        // read the batch bytes
        entry = Entry.fromBytes(param, bits, offset);
        offset += entry.getSize();
        if (offset != getBytes().length - getAuthenticationSize()) {
            Debug.kill(new RuntimeException("Invalid byte input"));
        }
    }


    public int getSendingReplica() {
        return (int) getSender();
    }


    public long getSeqNo() {
        return seqno;
    }

    public Entry getEntry() {
        return entry;
    }

    public boolean matches(VerifiedMessageBase vmb) {
        boolean res = vmb.getTag() == getTag();
        if (!res) {
            return false;
        }
        FetchCommand bc = (FetchCommand) vmb;
        return res
                && getSeqNo() == bc.getSeqNo()
                && getEntry().equals(bc.getEntry());
    }

    static public int computeSize(Entry ent) {
        return ent.getSize() + MessageTags.uint32Size;
    }
}