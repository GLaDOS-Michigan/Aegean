// $Id: CheckPointState.java 3960 2009-07-24 09:30:04Z aclement $

package BFT.serverShim.statemanagement;


import BFT.Debug;
import BFT.Parameters;
import BFT.messages.Digest;
import BFT.messages.Reply;
import BFT.serverShim.ShimBaseNode;

public class CheckPointState {

    protected byte[] appCheckpoint; // state before anything
    // associated with this checkpoint
    // happens
    protected long sequenceNumber; // next sequence number to
    // process. i.e. one more than the
    // largest sequence number
    // contained in the state
    protected long maxSequenceNumber; // last sequence number
    // contained in the checkpoint.
    // Actually stored as that
    // number +1
    // -1 until we've reached the "end" of the checkpoint interval
    protected ReplyEntry[] replyCache;
    protected long baseSequenceNumber; // first thing to be included
    // in this checkpoint state
    protected Digest stableDigest;
    protected Parameters parameters;

    public CheckPointState(Parameters param, int clients) {
        this.parameters = param;
        replyCache = new ReplyEntry[parameters.getNumberOfClients()];
        baseSequenceNumber = 0;
        sequenceNumber = 0;
        maxSequenceNumber = -1;
        for (int i = 0; i < replyCache.length; i++) {
            replyCache[i] = new ReplyEntry(parameters);
        }
        appCheckpoint = null;
    }


    public CheckPointState(Parameters param, CheckPointState cps) {
        parameters = param;
        appCheckpoint = null;
        baseSequenceNumber = cps.sequenceNumber;
        sequenceNumber = baseSequenceNumber;
        maxSequenceNumber = -1;
        replyCache = new ReplyEntry[cps.replyCache.length];
        for (int i = 0; i < replyCache.length; i++) {
            replyCache[i] = cps.replyCache[i];
        }
    }

    public CheckPointState(Parameters param, byte[] bytes) {
        this.parameters = param;
        int offset = 0;
        // read the base seq no
        byte[] tmp = new byte[BFT.messages.MessageTags.uint32Size];
        for (int i = 0; i < tmp.length; i++, offset++)
            tmp[i] = bytes[offset];
        baseSequenceNumber = util.UnsignedTypes.bytesToLong(tmp);
        // read the current sequence number
        for (int i = 0; i < tmp.length; i++, offset++)
            tmp[i] = bytes[offset];
        sequenceNumber = util.UnsignedTypes.bytesToLong(tmp);
        // read the max sequence number
        for (int i = 0; i < tmp.length; i++, offset++)
            tmp[i] = bytes[offset];
        maxSequenceNumber = util.UnsignedTypes.bytesToLong(tmp);
        // read the size of the apcheckpoint
        tmp = new byte[BFT.messages.MessageTags.uint32Size];
        for (int i = 0; i < tmp.length; i++, offset++)
            tmp[i] = bytes[offset];
        int size = (int) util.UnsignedTypes.bytesToLong(tmp);
        // and the appcheckpoint itself
        appCheckpoint = new byte[size];
        for (int i = 0; i < appCheckpoint.length; i++, offset++)
            appCheckpoint[i] = bytes[offset];

        // and now for the reply cache
        replyCache = new ReplyEntry[parameters.getNumberOfClients()];
        for (int i = 0; i < replyCache.length; i++) {
            replyCache[i] = new ReplyEntry(parameters);
            offset = replyCache[i].fromBytes(bytes, offset);
        }


        if (offset != bytes.length) {
            System.out.println("offset: " + offset);
            System.out.println("bytes : " + bytes.length);
            BFT.Debug.kill("horrible mismatch in loading a reply cache from bytes");
        }
    }


    public Digest getStableDigest() {
        return stableDigest;
    }


    public void setStableDigest(Digest d) {
        stableDigest = d;
    }

    boolean stable = false;

    public void markStable() {
        //if (stable)
        //System.out.println(getMaxSequenceNumber() +" is already stable");
        //	    BFT.Debug.kill("its already stable!");
        stable = true;
    }

    public boolean isStable() {
        return stable;
    }


    public Reply getReply(int client, ShimBaseNode smb) {
        return replyCache[client].getReply(smb, client);
    }

    public void addReply(Reply rep, long seqno, int client) {
        if (seqno < baseSequenceNumber) {
            //Debug.kill(new RuntimeException("old reply does not belong "+
            //"in this certificate "+seqno+" < "+
            //baseSequenceNumber));
            //System.out.println("\t\t\t\t***** Discarding an old reply rather than caching -- there's an issue in the glue");
            return;
        }
        if (appCheckpoint != null) {
            Debug.kill(new RuntimeException("we've already checkpointed at " + sequenceNumber + " cant deposit " + seqno));
        }

        if (replyCache[client] == null) {
            throw new RuntimeException("something horribly wrong");
        }

        if (maxSequenceNumber != -1 && seqno >= maxSequenceNumber) {
            Debug.kill("cannot add " + seqno + " to a cp capped at " + maxSequenceNumber);
        }
        if (seqno >= sequenceNumber) {
            sequenceNumber = seqno + 1;
        }
        if (replyCache[client].getSequenceNumber() < baseSequenceNumber) {
            replyCache[client] = new ReplyEntry(parameters);
        }
        replyCache[client].setReply(rep, seqno);
    }


    public void addCheckpoint(byte[] bytes, long seqno) {
        if (appCheckpoint != null)
            throw new RuntimeException("there's already a checkpoint!");
        if (seqno != maxSequenceNumber - 1 && sequenceNumber != 0)
            throw new RuntimeException("missed some requests, cp for " + seqno +
                    " with maxseq at " + maxSequenceNumber);
        appCheckpoint = bytes;
        setStableDigest(new Digest(parameters, this.getBytes()));
    }

    public byte[] getCheckpoint() {
        return appCheckpoint;
    }


    public long getBaseSequenceNumber() {
        return baseSequenceNumber;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public long getMaxSequenceNumber() {
        return maxSequenceNumber;
    }

    public synchronized void setMaxSequenceNumber(long m) {
        if (m < baseSequenceNumber)
            Debug.kill("cant set max " + m +
                    " to be below base " + baseSequenceNumber);
        if (maxSequenceNumber == m)
            return;
        if (maxSequenceNumber != -1)
            Debug.kill(new RuntimeException("cannot change maxsequence number once it is set " + maxSequenceNumber + " -> " + m));
        maxSequenceNumber = m;
    }


    byte[] bytes = null;

    public byte[] getBytes() {
        if (bytes != null)
            return bytes;
        int sum = 0;
        // get the size of the reply cache
        for (int i = 0; i < replyCache.length; i++)
            sum += replyCache[i].getSize();
        // get the size of the cp token and its size (int)
        sum += appCheckpoint.length + BFT.messages.MessageTags.uint32Size;
        // and the three sequence numbers
        sum += BFT.messages.MessageTags.uint32Size * 3;
        bytes = new byte[sum];

        byte[] tmp;
        int offset = 0;
        // base sequence number
        tmp = util.UnsignedTypes.longToBytes(baseSequenceNumber);
        for (int i = 0; i < tmp.length; i++, offset++)
            bytes[offset] = tmp[i];
        // current sequence number
        tmp = util.UnsignedTypes.longToBytes(sequenceNumber);
        for (int i = 0; i < tmp.length; i++, offset++)
            bytes[offset] = tmp[i];
        // max sequence number
        if (maxSequenceNumber == -1)
            Debug.kill("cannot serialize a cp with seqno -1");
        tmp = util.UnsignedTypes.longToBytes(maxSequenceNumber);
        for (int i = 0; i < tmp.length; i++, offset++)
            bytes[offset] = tmp[i];
        // size of the app checkpoint
        if (appCheckpoint == null)
            Debug.kill("cannot serialize a CP that doe snot yet have an app checkpoint");
        tmp = util.UnsignedTypes.longToBytes(appCheckpoint.length);
        for (int i = 0; i < tmp.length; i++, offset++)
            bytes[offset] = tmp[i];
        if (util.UnsignedTypes.bytesToLong(tmp) != appCheckpoint.length) {
            System.out.println("length: " + appCheckpoint.length);
            System.out.println("temp  : " + util.UnsignedTypes.bytesToInt(tmp));
            BFT.Debug.kill("those two values should be teh same.  looks like the appcp is much larger than we planned");
        }
        // checkpoint
        for (int i = 0; i < appCheckpoint.length; i++, offset++)
            bytes[offset] = appCheckpoint[i];
        // now the reply cache
        for (int i = 0; i < replyCache.length; i++)
            offset = replyCache[i].copyBytes(bytes, offset);

        return bytes;
    }

    public long[] getCommandIndices() {
        long[] commands = new long[replyCache.length];
        for (int i = 0; i < commands.length; i++)
            commands[i] = replyCache[i].getRequestId();
        return commands;
    }

    public String toString() {
        String result;
        int sum = 0;
        result = "\t\t\tbase: " + baseSequenceNumber + "\n";
        result += "\t\t\tcurr: " + sequenceNumber + "\n";
        result += "\t\t\tbax : " + maxSequenceNumber + "\n";
        result += "\t\t\tapcp:";
        if (appCheckpoint == null)
            result += "" + appCheckpoint;
        else
            for (int i = 0; i < appCheckpoint.length; i++)
                result += " " + appCheckpoint[i] + ((i % 8 == 0) ? "\n" : "");
        result += "\n";
        //	result += new String(appCheckpoint);
        for (int i = 0; i < replyCache.length; i++) {
            result += "\t\t\t  " + i + ": " + replyCache[i] + "\n";
        }

        return result;
    }


}


class ReplyEntry {

    Reply reply;
    byte[] command;
    long reqId;
    long sequenceNumber;
    Parameters parameters;

    public ReplyEntry(Parameters param) {
        parameters = param;
        reply = null;
        command = new byte[0];
        reqId = 0;
        sequenceNumber = 0;
    }

    public void setReply(Reply rep, long seqno) {
        reply = rep;
        sequenceNumber = seqno;
        reqId = rep.getRequestId();
        command = rep.getCommand();
    }


    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public long getRequestId() {
        return reqId;
    }

    public Reply getReply(ShimBaseNode sbn, int client) {
        if (reply == null) {
            reply = new Reply(sbn.getMyExecutionIndex(), reqId, command);
            // manos: authenticating the message with subId=0. I think this
            // is OK, since the message is re-authenticated 2 levels up, at
            // ShimBaseNode.acton(Retransmit)
            sbn.authenticateClientMacMessage(reply, client, 0);
        }
        return reply;
    }

    public int getSize() {
        return BFT.messages.MessageTags.uint32Size + // length of command
                command.length + //command
                BFT.messages.MessageTags.uint32Size + // request id
                BFT.messages.MessageTags.uint32Size; // sequence number

    }

    // copies the bytes of the entry into bytes[] starting at offset.
    // returns the next empty slot in the byte array
    public int copyBytes(byte[] bytes, int offset) {

        // the sequence number
        byte[] tmp = util.UnsignedTypes.longToBytes(sequenceNumber);
        for (int i = 0; i < tmp.length; i++, offset++)
            bytes[offset] = tmp[i];
        // the request id
        tmp = util.UnsignedTypes.longToBytes(reqId);
        for (int i = 0; i < tmp.length; i++, offset++)
            bytes[offset] = tmp[i];
        // the number of bytes in the command
        tmp = util.UnsignedTypes.longToBytes(command.length);
        for (int i = 0; i < tmp.length; i++, offset++)
            bytes[offset] = tmp[i];
        // the command itself
        for (int i = 0; i < command.length; i++, offset++)
            bytes[offset] = command[i];
        return offset;
    }


    public int fromBytes(byte[] bytes, int offset) {
        // read the sequence number
        byte[] tmp = new byte[BFT.messages.MessageTags.uint32Size];
        for (int i = 0; i < tmp.length; i++, offset++)
            tmp[i] = bytes[offset];
        sequenceNumber = util.UnsignedTypes.bytesToLong(tmp);
        //read the request id
        for (int i = 0; i < tmp.length; i++, offset++)
            tmp[i] = bytes[offset];
        reqId = util.UnsignedTypes.bytesToLong(tmp);
        // size of the command
        tmp = new byte[BFT.messages.MessageTags.uint32Size];
        for (int i = 0; i < tmp.length; i++, offset++)
            tmp[i] = bytes[offset];
        int size = (int) util.UnsignedTypes.bytesToLong(tmp);
        command = new byte[size];
        for (int i = 0; i < command.length; i++, offset++)
            command[i] = bytes[offset];
        return offset;
    }


    public String toString() {
        String out = "" + sequenceNumber + " : " + reqId + " : ";
        out += new Digest(parameters, command);
        //  	for (int i = 0; i < command.length; i++){
        // 	    if (i == 0) out +="\n\t\t\t\t";
        //  	    out += command[i]+", ";
        //  	    if (i %16 == 0)
        //  		out += "\n\t\t\t\t";
        //  	}
        return out;
    }

}
