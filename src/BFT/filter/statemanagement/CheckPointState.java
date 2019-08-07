// $Id
package BFT.filter.statemanagement;

import BFT.Parameters;
import BFT.messages.BatchCompleted;
import BFT.messages.Entry;
import BFT.messages.Quorum;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.PriorityQueue;


public class CheckPointState {

    //    protected Hashtable<Long, Hashtable<Long, Entry>> clientTable;
    protected ArrayList<Hashtable<Long, Entry>> clientTable;
    //    protected Hashtable<Long, Quorum<BatchCompleted>> quorums;
    protected ArrayList<Quorum<BatchCompleted>> quorums;
    protected long base;

    public CheckPointState(Parameters param, long baseSeqNo) {
        base = baseSeqNo;
        //	clientTable = new Hashtable<Long, Hashtable<Long, Entry>>();
        clientTable = new ArrayList<Hashtable<Long, Entry>>(param.getNumberOfClients());
        for (int i = 0; i < param.getNumberOfClients(); i++) {
            clientTable.add(i, new Hashtable<Long, Entry>());
        }
        //	quorums = new Hashtable<Long, Quorum<BatchCompleted>>();
        quorums = new ArrayList<Quorum<BatchCompleted>>(BFT.order.Parameters.checkPointInterval);
        for (int i = 0; i < BFT.order.Parameters.checkPointInterval; i++) {
            quorums.add(i,
                    //	    quorums.put(baseSeqNo+i,
                    new Quorum<BatchCompleted>(param.getExecutionCount(), param.smallExecutionQuorumSize(), 0));
        }
    }

    public long getBaseSequenceNumber() {
        return base;
    }

    public void setBaseSequenceNumber(long b) {
        base = b;
    }

    public long getMaxSeqNo() {
        return base + BFT.order.Parameters.checkPointInterval;
    }

    public Quorum<BatchCompleted> getQuorum(long seqno) {
        if (seqno < base)
            BFT.Debug.kill("too low");
        if (seqno >= getMaxSeqNo())
            BFT.Debug.kill("too high");
        return quorums.get((int) (seqno - base));
    }

    public void addRequest(Entry entry) {
        Hashtable<Long, Entry> table = clientTable.get((int) entry.getClient());
        if (table == null) {
            BFT.Debug.kill("BAD THINGS");
        }
        table.put(entry.getRequestId(), entry);
    }

    public Entry getRequest(long clientId, long requestId) {
        Hashtable<Long, Entry> table = clientTable.get((int) clientId);
        if (table == null) {
            BFT.Debug.kill("BAD THINGS");
        }
        return table.get(requestId);
    }


    public void clear(PriorityQueue<Entry> heap) {
        for (int i = 0; i < clientTable.size(); i++) {
            Hashtable<Long, Entry> k = clientTable.get(i);
            for (Enumeration<Entry> f = k.elements(); f.hasMoreElements(); )
                heap.remove(f.nextElement());
            k.clear();
        }

        for (int i = 0; i < quorums.size(); i++) {
            quorums.get(i).clear();
        }
    }
}