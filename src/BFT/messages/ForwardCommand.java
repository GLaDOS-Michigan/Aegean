// $Id

package BFT.messages;

import BFT.Debug;
import BFT.Parameters;
import util.UnsignedTypes;

public class ForwardCommand extends MacMessage {

    protected Entry entry;
    protected long seqno;

    public ForwardCommand(long seqno, Entry ent, int sendingReplica) {
        super(MessageTags.ForwardCommand,
                computeSize(ent),
                sendingReplica);
        entry = ent;
        this.seqno = seqno;
        // need to write to bytes

        int offset = getOffset();
        byte[] bytes = getBytes();
        byte[] tmp;
        util.UnsignedTypes.longToBytes(seqno, bytes, offset);
        offset += UnsignedTypes.uint32Size;

        // place the batch bytes
        tmp = entry.getBytes();
        for (int i = 0; i < tmp.length; i++, offset++) {
            bytes[offset] = tmp[i];
        }
    }

    public ForwardCommand(byte[] bits, Parameters param) {
        super(bits);
        if (getTag() != MessageTags.ForwardCommand) {
            BFT.Debug.kill("Bad Tag; " + getTag());
        }

        int offset = getOffset();
        seqno = util.UnsignedTypes.bytesToLong(bits, offset);
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

    public Entry getEntry() {
        return entry;
    }

    public long getSeqNo() {
        return seqno;
    }

    public boolean matches(VerifiedMessageBase vmb) {
        boolean res = vmb.getTag() == getTag();
        if (!res) {
            return false;
        }
        ForwardCommand bc = (ForwardCommand) vmb;
        return res && getEntry().equals(bc.getEntry());
    }

    static public int computeSize(Entry ent) {
        return ent.getSize() + MessageTags.uint32Size;
    }
}