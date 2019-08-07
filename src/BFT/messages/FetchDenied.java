// $Id

package BFT.messages;

import BFT.Debug;
import BFT.Parameters;
import util.UnsignedTypes;

public class FetchDenied extends MacMessage {

    protected Entry entry;
    protected long seqno;

    public FetchDenied(long seq, Entry ent, int sendingReplica) {
        super(MessageTags.FetchDenied,
                computeSize(ent),
                sendingReplica);
        seqno = seq;
        entry = ent;
        // need to write to bytes


        int offset = getOffset();
        byte[] bytes = getBytes();

        // place the sequence number
        UnsignedTypes.longToBytes(seqno, bytes, offset);
        offset += UnsignedTypes.uint32Size;

        // place the batch bytes
        byte[] tmp = entry.getBytes();
        for (int i = 0; i < tmp.length; i++, offset++) {
            bytes[offset] = tmp[i];
        }
    }

    public FetchDenied(byte[] bits, Parameters param) {
        super(bits);
        if (getTag() != MessageTags.FetchDenied) {
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
        FetchDenied bc = (FetchDenied) vmb;
        return res
                && getSeqNo() == bc.getSeqNo()
                && getEntry().equals(bc.getEntry());
    }

    static public int computeSize(Entry ent) {
        return ent.getSize() + MessageTags.uint32Size;
    }
}