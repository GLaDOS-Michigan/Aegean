// $Id

package BFT.messages;

import BFT.Debug;
import BFT.Parameters;
import util.UnsignedTypes;

public class CPUpdate extends MacArrayMessage {

    protected long[] commands;
    protected long seqno;

    public CPUpdate(Parameters param, long[] lastCommand, long seqNo, int sendingReplica) {
        super(param, MessageTags.CPUpdate,
                computeSize(lastCommand),
                sendingReplica,
                param.getFilterCount());
        if (lastCommand.length != param.getNumberOfClients()) {
            System.err.print("commandlength " + lastCommand.length);
            BFT.Debug.kill("Bad command list length");
        }
        commands = new long[lastCommand.length];
        for (int i = 0; i < commands.length; i++) {
            commands[i] = lastCommand[i];
        }
        seqno = seqNo;
        // need to write to bytes

        int offset = getOffset();
        byte[] bytes = getBytes();

        // place the sequence number
        UnsignedTypes.longToBytes(seqno, bytes, offset);
        offset += UnsignedTypes.uint32Size;
        // place the size of the batch
        for (int j = 0; j < commands.length; j++) {
            UnsignedTypes.longToBytes(commands[j], bytes, offset);
            offset += UnsignedTypes.uint32Size;
        }
    }

    public CPUpdate(byte[] bits, Parameters param) {
        super(bits, param);
        if (getTag() != MessageTags.CPUpdate) {
            BFT.Debug.kill("Bad Tag; " + getTag());
        }

        int offset = getOffset();

        // read the sequence number
        seqno = UnsignedTypes.bytesToLong(bits, offset);
        offset += UnsignedTypes.uint32Size;

        commands = new long[parameters.getNumberOfClients()];
        // read the batch size
        for (int j = 0; j < commands.length; j++) {
            commands[j] = UnsignedTypes.bytesToLong(bits, offset);
            offset += UnsignedTypes.uint32Size;
        }
        if (offset != getBytes().length - getAuthenticationSize()) {
            Debug.kill(new RuntimeException("Invalid byte input"));
        }
    }


    public int getSendingReplica() {
        return (int) getSender();
    }

    public long getSequenceNumber() {
        return seqno;
    }

    public long[] getCommands() {
        return commands;
    }

    public boolean matches(VerifiedMessageBase vmb) {
        boolean res = vmb.getTag() == getTag();
        if (!res) {
            return false;
        }
        CPUpdate bc = (CPUpdate) vmb;
        res = res && getSequenceNumber() == bc.getSequenceNumber();
        for (int i = 0; res && i < commands.length; i++) {
            res = commands[i] == bc.commands[i];
        }
        return res;
    }

    public boolean dominates(CPUpdate cpu) {
        boolean res = getSequenceNumber() > cpu.getSequenceNumber();
        for (int i = 0; res && i < commands.length; i++) {
            res = commands[i] >= cpu.commands[i];
        }
        return res;
    }

    public boolean weaklyDominates(CPUpdate cpu) {
        boolean res = getSequenceNumber() >= cpu.getSequenceNumber();
        for (int i = 0; res && i < commands.length; i++) {
            res = commands[i] >= cpu.commands[i];
        }
        return res;
    }

    static public int computeSize(long commands[]) {
        return MessageTags.uint32Size +
                commands.length * MessageTags.uint32Size;
    }


}
