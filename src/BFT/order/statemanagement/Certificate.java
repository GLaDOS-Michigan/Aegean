// $Id: Certificate.java 728 2011-09-11 23:44:18Z aclement $

package BFT.order.statemanagement;

import BFT.Debug;
import BFT.Parameters;
import BFT.messages.*;
import BFT.order.OrderBaseNode;
import BFT.order.messages.Commit;
import BFT.order.messages.PrePrepare;
import BFT.order.messages.Prepare;

public class Certificate {

    public Certificate(Parameters param, int replicas) {
        this(param, replicas, false);
    }

    public Certificate(Parameters param, int replicas, boolean cp) {
        parameters = param;
        prepareMacs = new boolean[replicas];
        prepareCount = 0;
        commitMacs = new boolean[replicas];
        commitCount = 0;
        prepareCache = new Prepare[replicas];
        commitCache = new Commit[replicas];
        clear = true;
        takeCP = cp;
        certEntry = null;
        seqNo = -1;
    }

    protected Parameters parameters;

    // Preprepare that I sent
    protected PrePrepare preprepare;

    public PrePrepare getPrePrepare() {
        return preprepare;
    }

    protected PrePrepare cachedPP;

    public void cachePrePrepare(PrePrepare pp) {
        cachedPP = pp;
    }

    public PrePrepare getCachedPrePrepare() {
        PrePrepare p = cachedPP;
        cachedPP = null;
        return p;
    }

    // Digest of the preprepare
    public HistoryDigest getHistory() {
        if (certEntry == null) {
            return null;
        } else {
            return certEntry.getHistoryDigest();
        }
    }

    // collection of received prepares
    protected boolean[] prepareMacs;
    protected int prepareCount;
    // the prepare i sent for this sequence number
    protected Prepare prepare;

    public Prepare getPrepare() {
        return prepare;
    }

    // cache for prepares received out of order
    protected Prepare[] prepareCache;

    public Prepare[] getPrepareCache() {
        return prepareCache;
    }

    // collection of received commits
    protected boolean[] commitMacs;
    protected int commitCount;
    // the commit i sent for this sequence number
    protected Commit commit;

    public Commit getCommit() {
        return commit;
    }

    // cache for commits received out of order
    protected Commit[] commitCache;

    public Commit[] getCommitCache() {
        return commitCache;
    }

    // relevant operational pieces for this certificate
    public CommandBatch getCommandBatch() {
        if (certEntry == null)
            return null;
        else
            return certEntry.getCommandBatch();
    }

    public NonDeterminism getNonDeterminism() {
        if (certEntry == null)
            return null;
        else
            return certEntry.getNonDeterminism();
    }


    // identifying information about this certificate
    protected long seqNo;

    public long getSeqNo() {
        return seqNo;
    }

    protected boolean takeCP;

    public boolean takeCP() {
        return takeCP;
    }

    protected CertificateEntry certEntry;

    public CertificateEntry getCertEntry() {
        return certEntry;
    }

    protected Digest entryDigest;

    public Digest getEntryDigest() {
        if (entryDigest == null) {
            entryDigest = new Digest(parameters, certEntry.getBytes());
        }
        return entryDigest;
    }

    // nextbatch to be sent
    protected NextBatch nextBatch;

    public NextBatch getNextBatchMessage(OrderBaseNode obn, long currentView) {
        if (certEntry == null) {
            Debug.kill("nothing associated here yet");
        } else if (isCommitted()
                && (nextBatch == null
                || nextBatch.getTag() < MessageTags.CommittedNextBatch
                || nextBatch.getView() < currentView)) {

            // new next batch
            nextBatch = new CommittedNextBatch(parameters, currentView, seqNo,
                    certEntry, takeCP,
                    obn.getMyOrderIndex());
            obn.authenticateExecMacArrayMessage(nextBatch);

        } else if (isPrepared()
                && (nextBatch == null
                || nextBatch.getTag() < MessageTags.TentativeNextBatch
                || nextBatch.getView() < currentView)) {
            nextBatch = new TentativeNextBatch(parameters, currentView, seqNo,
                    certEntry, takeCP,
                    obn.getMyOrderIndex());
            obn.authenticateExecMacArrayMessage(nextBatch);

        } else if (nextBatch == null
                || nextBatch.getView() < currentView) {
            nextBatch = new SpeculativeNextBatch(parameters, currentView, seqNo,
                    certEntry, takeCP,
                    obn.getMyOrderIndex());
            obn.authenticateExecMacArrayMessage(nextBatch);

        }
        return nextBatch;
    }


    public void setNextBatch(NextBatch nb) {
        if (clear) {
            setEntry(nb.getCertificateEntry(), nb.getSeqNo());
        } else {
            if (nb.getSeqNo() != seqNo) {
                BFT.Debug.kill(nb.getSeqNo() + " should be " + seqNo);
            }
            if (nb.getView() > nextBatch.getView()) {
                clear();
                setNextBatch(nb);
            }
            if (nb.getView() < nextBatch.getView()) {
                return; // do nothing
            }
        }
        nextBatch = nb;
        if (nb.getTag() == MessageTags.TentativeNextBatch) {
            forcePrepared();
        }
        if (nb.getTag() == MessageTags.CommittedNextBatch) {
            forceCommitted();
        }
    }


    /************

     For the moment I am going to assume you discard things if you
     dont ahve the previous level filled in.  I'll deal with out of
     order processing later.

     **********/


    public void setEntry(CertificateEntry entry, long seq) {
        if (!clear) {
            Debug.kill("must have an empty certificate before adding");
        }
        certEntry = entry;
        seqNo = seq;
        clear = false;
    }


    /**
     * Add a preprepare that I sent.
     * <p>
     * Returns true if the preprepare is successfully added, false
     * otherwise.
     * <p>
     * Preprepare is added successfully if there is no existing
     * prepreparehash.  Side effect is setting the preprepare hash,
     * nondeterminism, commandbatch, and history for the certificate
     **/
    public boolean addMyPrePrepare(PrePrepare p) {
        if (addOtherPrePrepare(p)) {
            preprepare = p;
            return true;
        } else {
            return false;
        }
    }

    /**
     * Add a preprepare that somebody else sent
     **/
    public boolean addOtherPrePrepare(PrePrepare p) {
        if (!isClear()) {
            // gc p
            return false;
        }
        if (certEntry != null) {
            // gc p
            return false;
        }
        RequestCore[] reqs = p.getRequestBatch().getEntries();
        Entry[] entries = new Entry[reqs.length];
        for (int i = 0; i < entries.length; i++) {
            entries[i] = reqs[i].getEntry();
        }
        CommandBatch commands = new CommandBatch(entries);
        NonDeterminism nondet = p.getNonDeterminism();
        HistoryDigest history = new HistoryDigest(parameters, p.getHistory(), commands, nondet, p.getCPHash());
        certEntry = new CertificateEntry(parameters, history, commands, nondet, p.getCPHash());
        seqNo = p.getSeqNo();
        prepareMacs[index(p.getSendingReplica())] = true;
        prepareCount++;
        clear = false;
        return true;
    }


    public boolean addMyPrepare(Prepare p) {
        if (prepare != null) {
            return false;
        }
        if (getHistory() == null || !getHistory().equals(p.getPrePrepareHash())) {
            // gc p
            return false;
        }
        if (!p.getPrePrepareHash().equals(getHistory())) {
            // gc p
            return false;
        }
        prepareCount++;
        prepareMacs[index(p.getSendingReplica())] = true;
        prepare = p;
        return true;
    }

    public boolean addOtherPrepare(Prepare p) {
        if (getHistory() == null || !getHistory().equals(p.getPrePrepareHash())) {
            // gc p
            return false;
        }
        if (!p.getPrePrepareHash().equals(getHistory())) {
            // gc p
            return false;
        }
        if (prepareMacs[index(p.getSendingReplica())]) {
            return false;
        }
        prepareCount++;
        prepareMacs[index(p.getSendingReplica())] = true;
        return true;
    }


    public boolean preparedBy(int i) {
        return prepareMacs[i];
    }

    public void cachePrepare(Prepare p) {
        prepareCache[index(p.getSendingReplica())] = p;
    }

    public boolean addMyCommit(Commit p) {
        if (commit == null && addOtherCommit(p)) {
            commit = p;
            return true;
        } else {
            return false;
        }
    }

    public boolean addOtherCommit(Commit p) {
        if (getHistory() == null || !getHistory().equals(p.getPrePrepareHash())) {
            // gc p
            return false;
        }
        if (!p.getPrePrepareHash().equals(getHistory())) {
            // gc p
            return false;
        }
        if (commitMacs[index(p.getSendingReplica())]) {
            return false;
        }
        if (prepareMacs[index(p.getSendingReplica())] == false) {
            prepareMacs[index(p.getSendingReplica())] = true;
            prepareCount++;
        }
        commitCount++;
        commitMacs[index(p.getSendingReplica())] = true;
        return true;
    }

    public void forceCommitted() {
        if (certEntry == null) {
            Debug.kill(new RuntimeException("can only commit something that has a nextbatch"));
        }
        for (int i = 0; i < commitMacs.length; i++) {
            commitMacs[i] = true;
            commitCount++;
        }
    }


    public void forcePreprepared() {
        if (certEntry == null) {
            throw new RuntimeException("can only preprepare something that exists");
        }
        for (int i = 0; i < commitMacs.length; i++) {
            prepareMacs[i] = false;
            commitMacs[i] = false;
        }
        nextBatch = null;
    }

    public void forcePrepared() {
        if (certEntry == null) {
            throw new RuntimeException("can only prepare a next batch");
        }
        for (int i = 0; i < commitMacs.length; i++) {
            prepareMacs[i] = true;
            prepareCount++;
        }
    }


    public void cacheCommit(Commit c) {
        commitCache[index(c.getSendingReplica())] = c;
    }

    public boolean isPrePrepared() {
        return getHistory() != null;
    }

    public boolean isPrepared() {
        //return prepareCount >= getQuorumSize() ||
        //commitCount >= getSmallQuorumSize();
        int count = 0;
        int count2 = 0;
        for (int i = 0; i < prepareMacs.length; i++) {
            if (prepareMacs[i] || commitMacs[i]) count++;
            if (commitMacs[i]) count2++;
        }
        return count >= getQuorumSize(parameters) || count2 >= getSmallQuorumSize(parameters);
    }

    public int getPreparedCount() {
        return prepareCount;
        // 	int count = 0;
        // 	for (int i = 0; i < prepareMacs.length; i++)
        // 	    if (prepareMacs[i])
        // 		count++;
        // 	return count;
    }

    public boolean isCommitted() {
        //	return commitCount >= getQuorumSize();
        int count = 0;
        int count2 = 0;
        for (int i = 0; i < commitMacs.length; i++) {
            if (commitMacs[i]) count++;
            if (prepareMacs[i]) count2++;
        }
        return count >= getQuorumSize(parameters); //||count2 >= getFastQuorumSize();
    }


    // clears all of the fields
    protected boolean clear;

    protected void clear() {
        clear = true;
        for (int i = 0; i < prepareMacs.length; i++) {
            prepareMacs[i] = false;
            commitMacs[i] = false;
            prepareCache[i] = null;
            commitCache[i] = null;
        }
        prepareCount = 0;
        commitCount = 0;
        // gc anything that is not already null
        certEntry = null;
        entryDigest = null;
        seqNo = -1;
        commit = null;
        prepare = null;
        preprepare = null;
        nextBatch = null;
    }


    public boolean isClear() {
        return clear;
    }

    final public static int getFastQuorumSize(Parameters param) {
        return param.fastOrderQuorumSize();
    }

    final public static int getQuorumSize(Parameters param) {
        return param.largeOrderQuorumSize();
    }

    final public static int getSmallQuorumSize(Parameters param) {
        return param.smallOrderQuorumSize();
    }

    final public static int index(long replica) {
        return (int) replica;
    }
}