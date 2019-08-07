// $id: CheckPointState.java 3073 2009-03-08 06:56:58Z aclement $

package BFT.order.statemanagement;

import BFT.Debug;
import BFT.Parameters;
import BFT.messages.*;
import util.UnsignedTypes;

import java.util.ArrayList;

public class CheckPointState {


    protected ArrayList<OrderedEntry>[] orderedRequestCache;
    protected long[] retransDelay;
    protected byte[] execCPToken; // 
    protected long baseSeqNo; // first sequence number of this checkpoint

    protected long currentSeqNo;  // next sequence number to be
    // executed in this checkpoint.  if
    // execcptoken is not null, then it
    // corresponds to executing
    // everything prior to this sequence
    // number
    protected HistoryDigest history; // should always reflect the
    // history up to and including
    // currentSeqNo - 1
    protected long currentTime;  // current time
    protected boolean committed;

    protected Digest stableDigest;
    protected Parameters parameters;

    public CheckPointState(Parameters param, int clients) {
        parameters = param;
        baseSeqNo = 0;
        orderedRequestCache = new ArrayList[clients];
        retransDelay = new long[clients];
        for (int i = 0; i < clients; i++) {
            orderedRequestCache[i] = new ArrayList<OrderedEntry>(BFT.Parameters.checkPointInterval);
            retransDelay[i] = baseDelay;
        }
        currentSeqNo = 0;
        history = new HistoryDigest(param);
        execCPToken = null;
        stableDigest = null;
        committed = false;
        currentTime = 0;
        if (parameters == null) {
            throw new RuntimeException("null parameters at end of constructor. dying!");
        }
    }

    public CheckPointState(Parameters param, byte[] bytes) {
        this(param, bytes, 0);
    }

    public CheckPointState(Parameters param, byte[] bytes, int offset) {
        parameters = param;
        // read the history
        byte tmp[] = new byte[HistoryDigest.size(param)];
        for (int i = 0; i < tmp.length; i++, offset++) {
            tmp[i] = bytes[offset];
        }
        history = HistoryDigest.fromBytes(tmp, param);

        currentTime = UnsignedTypes.bytesToLongLong(bytes, offset);
        offset += UnsignedTypes.uint64Size;

        // and now the sequence number

        currentSeqNo = UnsignedTypes.bytesToLong(bytes, offset);
        offset += UnsignedTypes.uint32Size;

        // and the base

        baseSeqNo = UnsignedTypes.bytesToLong(bytes, offset);
        offset += UnsignedTypes.uint32Size;
        if ((baseSeqNo) % BFT.order.Parameters.checkPointInterval != 0) {
            throw new RuntimeException("invalid base for a checkpoint");
        }


        // read the number of entries
        int size = UnsignedTypes.bytesToInt(bytes, offset);
        offset += UnsignedTypes.uint16Size;
        orderedRequestCache = new ArrayList[size];
        // read the entries
        for (int i = 0; i < orderedRequestCache.length; i++) {
            orderedRequestCache[i] = new ArrayList<OrderedEntry>(BFT.order.Parameters.checkPointInterval);
            int num = UnsignedTypes.bytesToInt(bytes, offset);
            offset += UnsignedTypes.uint16Size;
            for (int j = 0; j < num; j++) {
                long req = UnsignedTypes.bytesToLong(bytes, offset);
                offset += UnsignedTypes.uint32Size;
                long seqno = UnsignedTypes.bytesToLong(bytes, offset);
                offset += UnsignedTypes.uint32Size;
                orderedRequestCache[i].add(new OrderedEntry(req, seqno));
            }
            //	    System.out.println("\t client "+i+" " +req+" "+seqno);
        }

        execCPToken = new byte[bytes.length - offset];
        for (int i = 0; offset < bytes.length; i++, offset++) {
            execCPToken[i] = bytes[offset];
        }

        retransDelay = new long[orderedRequestCache.length];
        for (int i = 0; i < retransDelay.length; i++) {
            retransDelay[i] = baseDelay;
        }

        if (parameters == null) {
            throw new RuntimeException("null parameters at end of constructor. dying!");
        }
    }

    public byte[] getBytes() {
        System.out.println("parameters " + parameters);
        int num = 0;
        for (int i = 0; i < orderedRequestCache.length; i++) {
            num += orderedRequestCache[i].size();
        }
        int byteSize = -1;
        try {
            byteSize = MessageTags.uint16Size +
                    num * // size of cache
                            (MessageTags.uint32Size + MessageTags.uint32Size) +
                    MessageTags.uint16Size * orderedRequestCache.length +
                    execCPToken.length + // size of exec token
                    parameters.digestLength + // size of the history
                    MessageTags.uint64Size + // current time
                    MessageTags.uint32Size + // base sequence number
                    MessageTags.uint32Size; // last sequence number
        } catch (NullPointerException e) {
            e.printStackTrace();
            System.exit(-1);
        }
        int offset = 0;
        byte[] tmp = new byte[byteSize];
        byte[] tmplong;
        // dump the history
        tmplong = history.getBytes();
        for (int i = 0; i < tmplong.length; i++, offset++) {
            tmp[offset] = tmplong[i];
        }

        // dump the time
        UnsignedTypes.longlongToBytes(currentTime, tmp, offset);
        offset += UnsignedTypes.uint64Size;

        // dump the current sequence number
        UnsignedTypes.longToBytes(currentSeqNo, tmp, offset);
        offset += UnsignedTypes.uint32Size;

        // dump the current base sequence number
        UnsignedTypes.longToBytes(baseSeqNo, tmp, offset);
        offset += UnsignedTypes.uint32Size;
        // client count
        UnsignedTypes.intToBytes(orderedRequestCache.length, tmp, offset);
        offset += UnsignedTypes.uint16Size;

        // dump each of the entries in cache
        for (int i = 0; i < orderedRequestCache.length; i++) {
            //dump the size of each hashtable   
            UnsignedTypes.intToBytes(orderedRequestCache[i].size(), tmp, offset);
            offset += UnsignedTypes.uint16Size;
            for (int j = 0; j < orderedRequestCache[i].size(); j++) {
                OrderedEntry to = orderedRequestCache[i].get(j);
                UnsignedTypes.longToBytes(to.getReqId(), tmp, offset);
                offset += UnsignedTypes.uint32Size;
                UnsignedTypes.longToBytes(to.getSeqNo(), tmp, offset);
                offset += UnsignedTypes.uint32Size;
            }
        }

        // dump the execcptoken
        for (int i = 0; i < execCPToken.length; i++, offset++) {
            tmp[offset] = execCPToken[i];
        }

        return tmp;
    }


    public CheckPointState(CheckPointState cps) {
        parameters = cps.parameters;
        baseSeqNo = cps.currentSeqNo;
        if ((baseSeqNo) % BFT.order.Parameters.checkPointInterval != 0)
            throw new RuntimeException("invalid base sequence number");
        currentSeqNo = cps.currentSeqNo;
        history = cps.history;
        currentTime = cps.currentTime;
        stableDigest = null;
        execCPToken = null;
        orderedRequestCache =
                new ArrayList[cps.orderedRequestCache.length];
        retransDelay = new long[orderedRequestCache.length];
        for (int i = 0; i < orderedRequestCache.length; i++) {
            orderedRequestCache[i] = new ArrayList<OrderedEntry>(BFT.order.Parameters.checkPointInterval);
            // keep all these caches
            for (int j = 0; j < cps.orderedRequestCache[i].size(); j++) {
                orderedRequestCache[i].add(cps.orderedRequestCache[i].get(j));
            }
            System.out.println("\t keep all caches in orderRequest size=" + orderedRequestCache[i].size());
            retransDelay[i] = cps.retransDelay[i];
        }
    }

    public int getSize() {
        if (!isStable())
            Debug.kill("can only get size of a stable checkpoint");
        return getBytes().length;
    }

    public HistoryDigest getHistory() {
        return history;
    }

    public long getBaseSequenceNumber() {
        return baseSeqNo;
    }

    public long getCurrentTime() {
        return currentTime;
    }

    public void setCurrentTime(long t) {
        currentTime = t;
    }

    public long getCurrentSequenceNumber() {
        return currentSeqNo;
    }

    public long getLastOrdered(long client) {
        if (orderedRequestCache[(int) (client)].size() == 0) {
            return 0;
        }
        return orderedRequestCache[(int) (client)].get(
                orderedRequestCache[(int) client].size() - 1).getReqId();
    }

    public long getLastOrderedSeqNo(long client) {
        if (orderedRequestCache[(int) (client)].size() == 0) {
            return baseSeqNo;
        }
        return orderedRequestCache[(int) (client)].get(
                orderedRequestCache[(int) client].size() - 1).getSeqNo();
    }

    public long getOrderedSeqNo(long client, long reqId) {
        if (orderedRequestCache[(int) (client)].size() == 0) {
            BFT.Debug.kill("No ordered");
        }
        if (reqId - orderedRequestCache[(int) (client)].get(0).getReqId() >=
                orderedRequestCache[(int) (client)].size()) {
            //BFT.Debug.kill("No ordered");
            return -1;
        }
        return orderedRequestCache[(int) (client)].get(
                (int) (reqId - orderedRequestCache[(int) (client)].get(0).getReqId())).getSeqNo();
    }


    public void addExecCPToken(byte[] cp, long seqNo) {
        if (execCPToken != null) {
            Debug.kill("Already have a checkpoint");
        }
        if (seqNo % BFT.order.Parameters.checkPointInterval != 0) {
            Debug.kill("Not a checkpoint interval " + seqNo);
        }

        //	System.out.println("adding: "+seqNo+" mybase: "+getBaseSequenceNumber()+" current: "+getCurrentSequenceNumber());
        execCPToken = cp;
    }

    public byte[] getExecCPToken() {
        return execCPToken;
    }


    /**
     * updates the ordered cache for each client that has an operation
     * in this set
     **/
    public void addNextBatch(CommandBatch b, long seq, HistoryDigest h,
                             long time) {
        if (seq != currentSeqNo) {
            System.out.println(seq + " " + currentSeqNo);
            Debug.kill("invalid batch update");
        }
        if (seq >= getBaseSequenceNumber() + BFT.order.Parameters.checkPointInterval) {
            Debug.kill("invalid addition of a batch");
        }
        Entry[] entries = b.getEntries();
        // copy on write to make a in memory copy
        for (int i = 0; i < entries.length; i++) {

            // if time for client to make GC, clear all the before cache
            if (orderedRequestCache[(int) (entries[i].getClient())].size() ==
                    BFT.Parameters.checkPointInterval) {
//                System.out.println("\tGC in request cache, size=" + orderedRequestCache[(int) (entries[i].getClient())].size());
                orderedRequestCache[(int) (entries[i].getClient())].clear();
            }

            orderedRequestCache[(int) (entries[i].getClient())].add(new OrderedEntry(entries[i].getRequestId(), seq));
//            System.out.println("\t add new entries" + " client=" + entries[i].getClient() + " req=" + entries[i].getRequestId() + " seq=" + seq
//                    + " size=" + orderedRequestCache[(int) (entries[i].getClient())].size());
            retransDelay[(int) (entries[i].getClient())] = baseDelay;
            //System.out.println("retransmit delay: "+retransDelay[(int)(entries[i].getClient())]);
        }
        history = h;
        setCurrentTime(time);
        currentSeqNo++;
    }

    long baseDelay = 1000;

    public long getRetransmitDelay(int client) {
        return retransDelay[client];
    }

    public void updateRetransmitDelay(int client) {
        retransDelay[client] *= 2;
        if (retransDelay[client] > baseDelay) {
            retransDelay[client] = baseDelay;
        }
        //System.out.println("retransmit delay: "+retransDelay[client]);
    }

    public void addNextBatch(CertificateEntry cert, long seq) {
        addNextBatch(cert.getCommandBatch(), seq, cert.getHistoryDigest(), cert.getNonDeterminism().getTime());
    }

    public void addNextBatch(NextBatch nb) {
        addNextBatch(nb.getCommands(), nb.getSeqNo(), nb.getHistory(), nb.getNonDeterminism().getTime());
    }


    protected boolean marking = false;

    public void makeStable() {
        if (!marking) {
            Debug.kill("should be marking stable before making stable");
        }

        if (isStable()) {
            Debug.kill("should not be making stable if its already stable");
        }

        if (getCurrentSequenceNumber() % BFT.order.Parameters.checkPointInterval != 0) {
            Debug.kill(new RuntimeException("Must be at a checkpoint " +
                    "interval to be made stable"));
        }
        // in 'stable' systems this requires a write to disk
//        System.err.println("will make stable");

        if (hasExecCP()) {
            stableDigest = new Digest(parameters, getBytes());
//            System.err.println("**********making the cp stable! ");
            System.err.println(this);
        } else {
            Debug.kill(new RuntimeException("attempting to mark a checkpoint stable without an app cp token"));
        }
    }


    public void commit() {
        if (!isStable()) {
            Debug.kill("Can only commit a CP if its stable!");
        }
//     	while(!isStable()) {
// 	    try {
// 		System.out.println("going to wait in commit()");
// 		try{
// 		    throw new RuntimeException("uh oh");
// 		}catch(Exception e){System.out.println(e);
// 		    e.printStackTrace();}
// 		wait();
// 		System.out.println("finished waitin gin commit()");
// 	    } catch (InterruptedException e) {
// 	    }
//     	}
        committed = true;
    }

    public boolean hasExecCP() {
        return execCPToken != null;
    }


    public boolean isStable() {
        return stableDigest != null;
    }


    public boolean isCommitted() {
        if (committed && !isStable())
            Debug.kill(new RuntimeException("cannot be committed " +
                    "without beign stable"));
        return committed;
    }

    public Digest getStableDigest() {
        return stableDigest;
    }

    public Digest getDigest() {
        Digest tmp = stableDigest;
        if (tmp == null && execCPToken != null) {
            tmp = new Digest(parameters, getBytes());
        }
        return tmp;
    }

    public boolean isMarkingStable() {
        return marking;
    }

    public void markingStable() {
        if (marking) {
            Debug.kill("already marking stable");
        }
        marking = true;
    }

    public String toString() {
        String ret = "base: " + getBaseSequenceNumber() + " next:" + getCurrentSequenceNumber() + " @ " + currentTime + "\n";
        for (int i = 0; i < orderedRequestCache.length; i++) {
            ret += "\t" + i + ":\t" + orderedRequestCache[i];
            if ((i + 1) % 4 == 0) {
                ret += "\n";
            }
        }
        ret += "\n\texeccp:";
        for (int i = 0; execCPToken != null && i < +execCPToken.length; i++) {
            ret += execCPToken[i] + " ";
        }
        ret += "\n\thistory:";
        for (int i = 0; i < history.getBytes().length; i++) {
            ret += +history.getBytes()[i] + " ";
        }
        if (stableDigest != null) {
            ret += "\n\tstableDig:" + stableDigest;
        }
        ret += "\n\tcommitted: " + committed;
        return ret;
    }

    protected void finalize() {
        //System.out.println("Garbage collecting checkpoint state");
    }

}

class OrderedEntry {
    protected long reqId;
    protected long seqNo;

    OrderedEntry() {
        reqId = 0;
        seqNo = 0;
    }

    ;

    public OrderedEntry(long r, long s) {
        reqId = r;
        seqNo = s;
    }

    public void set(long r, long s) {
        reqId = r;
        seqNo = s;
    }

    public long getReqId() {
        return reqId;
    }

    public long getSeqNo() {
        return seqNo;
    }

    public String toString() {
        return "" + getReqId() + " @ " + getSeqNo();
    }
}
