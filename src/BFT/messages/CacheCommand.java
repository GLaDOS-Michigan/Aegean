// $Id

package BFT.messages;

import BFT.Debug;
import BFT.Parameters;

public class CacheCommand extends MacArrayMessage {

    protected Entry entry;

    public CacheCommand(Parameters param, Entry ent, int sendingReplica) {
        super(param, MessageTags.CacheCommand,
                computeSize(ent),
                sendingReplica,
                param.getExecutionCount());
        entry = ent;
        // need to write to bytes


        int offset = getOffset();
        byte[] bytes = getBytes();
        // place the batch bytes
        byte[] tmp = entry.getBytes();
        for (int i = 0; i < tmp.length; i++, offset++)
            bytes[offset] = tmp[i];

    }

    public CacheCommand(byte[] bits, Parameters param) {
        super(bits, param);
        if (getTag() != MessageTags.CacheCommand) {
            BFT.Debug.kill("Bad Tag; " + getTag());
        }

        int offset = getOffset();

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

    public boolean matches(VerifiedMessageBase vmb) {
        boolean res = vmb.getTag() == getTag();
        if (!res) {
            return false;
        }
        CacheCommand bc = (CacheCommand) vmb;
        return res && getEntry().equals(bc.getEntry());
    }

    static public int computeSize(Entry ent) {
        return ent.getSize();
    }
}