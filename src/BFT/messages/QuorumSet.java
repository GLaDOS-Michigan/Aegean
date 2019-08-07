/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package BFT.messages;

import java.util.HashMap;

/**
 * @author yangwang
 */
public class QuorumSet<T extends VerifiedMessageBase> {
    private HashMap<Long, Quorum<T>> quorums = new HashMap<Long, Quorum<T>>();

    private int maxSize;
    private int targetSize;
    private int offsetBase;

    public QuorumSet(int maxSize, int targetSize, int offsetBase) {
        this.maxSize = maxSize;
        this.targetSize = targetSize;
        this.offsetBase = offsetBase;
    }

    public synchronized boolean addEntry(long seqNo, T entry) {
        if (!quorums.containsKey(seqNo)) {
            quorums.put(seqNo, new Quorum<T>(maxSize, targetSize, offsetBase));
        }
        Quorum<T> tmp = quorums.get(seqNo);
        tmp.addEntry(entry);
        return tmp.isComplete();
    }

    public synchronized void removeQuorum(long seqNo) {
        quorums.remove(seqNo);
    }

    public synchronized Quorum<T> getQuorum(long seqNo) {
        return quorums.get(seqNo);
    }
}
