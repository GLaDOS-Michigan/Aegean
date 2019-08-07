// $Id: Certificate.java 113 2010-03-24 00:00:54Z aclement $

package BFT.verifier.statemanagement;

import BFT.Debug;
import BFT.Parameters;
import BFT.messages.HistoryAndState;
import BFT.messages.VerifyMessage;
import BFT.verifier.messages.Commit;
import BFT.verifier.messages.Prepare;

import java.util.Enumeration;
import java.util.Hashtable;


public class Certificate {

    public Certificate(Parameters param, int execReplicas, int verifierReplicas) {
        parameters = param;
        verifyMessages = new Hashtable<HistoryAndState, Integer>(execReplicas);
        verifyMacs = new boolean[execReplicas];
        verifyCount = 0;
        prepareMacs = new boolean[verifierReplicas];
        prepareCount = 0;
        commitMacs = new boolean[verifierReplicas];
        commitCount = 0;

        verifyCache = new VerifyMessage[execReplicas];
        prepareCache = new Prepare[verifierReplicas];
        commitCache = new Commit[verifierReplicas];
        clear = true;
        historyAndState = null;
        seqNo = -1;
    }

    // collection of received verifyMessages
    protected Hashtable<HistoryAndState, Integer> verifyMessages;

    public Hashtable<HistoryAndState, Integer> getVerifyMessages() {
        return verifyMessages;
    }

    protected boolean[] verifyMacs;
    protected int verifyCount;
    protected Parameters parameters;

    // cache for prepares received out of order
    protected VerifyMessage[] verifyCache;

    public VerifyMessage[] getVerifyCache() {
        return verifyCache;
    }

    public void cacheVerify(VerifyMessage vm) {
        verifyCache[index(vm.getSendingReplica())] = vm;
    }

    // collection of received prepares
    // collection of received verifyMessages
    protected Hashtable<HistoryAndState, Integer> prepareMessages;

    public Hashtable<HistoryAndState, Integer> getPrepareMessages() {
        return prepareMessages;
    }

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
    // collection of received verifyMessages
    protected Hashtable<HistoryAndState, Integer> commitMessages;

    public Hashtable<HistoryAndState, Integer> getCommitMessages() {
        return commitMessages;
    }

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

    // identifying information about this certificate
    protected long seqNo;

    public long getSeqNo() {
        return seqNo;
    }

    protected HistoryAndState historyAndState;

    public HistoryAndState getHistoryAndState() {
        return historyAndState;
    }

    public void preprepare(HistoryAndState has) {
        historyAndState = has;
    }

    public boolean isPreprepared() {
        return historyAndState != null;
    }

    // returns true if the certificate becomes preprepared by adding this 
    // message, false otherwise
    public boolean addVerify(VerifyMessage vm) {
        Debug.fine(Debug.MODULE_VERIFIER, "Adding a vm for seqNo %d\n", vm.getVersionNo());
        seqNo = vm.getVersionNo();
        if (isPreprepared()) {
            Debug.debug(Debug.MODULE_VERIFIER, "Certificate is already preprepared\n");
//            System.err.println("Certificate is already preprepared");
            return false;
        }
        if (verifyMacs[index(vm.getSendingReplica())] == false) {
            Integer current = verifyMessages.get(vm.getHistoryAndState());
            if (current == null) {
                Debug.debug(Debug.MODULE_VERIFIER, "seqno %d: no entry for HAS %s yet. I will add one\n", vm.getVersionNo(), vm.getHistoryAndState().toString());
//                System.err.println("seqno %d: no entry for HAS %s yet. I will add one\n" +  vm.getVersionNo() + " " +  vm.getHistoryAndState().toString());
                verifyMessages.put(vm.getHistoryAndState(), 1);
            } else {
                Debug.debug(Debug.MODULE_VERIFIER, "seqno %d: existing number is %d for HAS %s. I will increase it\n", vm.getVersionNo(), current, vm.getHistoryAndState().toString());
//                System.err.println("seqno %d: existing number is %d for HAS %s. I will increase it\n" + vm.getVersionNo() + " " + current + " " + vm.getHistoryAndState().toString());
                verifyMessages.put(vm.getHistoryAndState(), new Integer(current + 1));
            }
            verifyMacs[index(vm.getSendingReplica())] = true;
            verifyCount++;
            if (canPreprepare()) {    // the only way this will succeed is this the
                // current message preprepares the certificate
                Debug.debug(Debug.MODULE_VERIFIER, "I will now preprepare\n");
                preprepare(vm.getHistoryAndState());
                return true;
            } else {
                Debug.debug(Debug.MODULE_VERIFIER, "not ready to PP yet\n");
//                System.err.println("not ready to PP yet");
                return false;
            }
        } else {
            Debug.warning(Debug.MODULE_VERIFIER, "Warning: tried to add a verify that already exists for seqno %d\n", vm.getVersionNo());
            System.err.println("couldn't add verify");
            return false;
        }
    }

    public void addPrepare(Prepare p) {
        if (prepareMacs[index(p.getSendingReplica())] == false) {
            prepareMacs[index(p.getSendingReplica())] = true;
            prepareCount++;
            seqNo = p.getSeqNo();        // WARNING: malicious behavior not handled here!
        } else {
            Debug.println("Warning: tried to add a prepare that already exists");
        }
    }

    public void addCommit(Commit c) {
        if (commitMacs[index(c.getSendingReplica())] == false) {
            commitMacs[index(c.getSendingReplica())] = true;
            commitCount++;
            seqNo = c.getSeqNo();
        } else {
            Debug.println("Warning: tried to add a commit that already exists");
        }
    }

    /************

     For the moment I am going to assume you discard things if you
     dont ahve the previous level filled in.  I'll deal with out of
     order processing later.

     **********/


    public boolean preparedBy(int i) {
        return prepareMacs[i];
    }

    public void cachePrepare(Prepare p) {
        prepareCache[index(p.getSendingReplica())] = p;
    }


    public void forceCommitted() {
        if (historyAndState == null) {
            Debug.kill(new RuntimeException("can only commit something " +
                    "that has a historyAndState"));
        }
        for (int i = 0; i < commitMacs.length; i++) {
            commitMacs[i] = true;
            commitCount++;
        }
    }

    public void forcePrepared() {
        if (historyAndState == null) {
            Debug.kill(new RuntimeException("can only prepare something " +
                    "that has a historyAndState"));
        }
        for (int i = 0; i < prepareMacs.length; i++) {
            prepareMacs[i] = true;
            prepareCount++;
        }
    }

    // I need to do this when creating the maxCommitted certificate in VC
    public void setSeqNo(long sn) {
        this.seqNo = sn;
    }

    public void cacheCommit(Commit c) {
        commitCache[index(c.getSendingReplica())] = c;
    }

    public boolean canPreprepare() {
//        System.out.println("verifyCount: " + verifyCount +  ", ExecutionQuorumSize " + getExecutionQuorumSize(parameters)
//        + ", prepareCount " + prepareCount + ", commitCOunt " + commitCount + ", smallVerifierQuorumSize " + getSmallVerifierQuorumSize(parameters));
        if (verifyCount < getExecutionQuorumSize(parameters) && // shortcut to deciding false
                (prepareCount + commitCount) < getSmallVerifierQuorumSize(parameters)) {
            return false;
        }

        Enumeration<HistoryAndState> e = verifyMessages.keys();
        HistoryAndState tmp;
        while (e.hasMoreElements()) {
            tmp = e.nextElement();
            Integer value = verifyMessages.get(tmp);
            if (value >= getExecutionQuorumSize(parameters)) {
                Debug.debug(Debug.MODULE_VERIFIER, "I found a quorum of %d with key %s\n", value, tmp.toString());
//                System.out.println("I found a quorum of " + value + " with key, " + tmp.toString());
                return true;
            }
        }

        Debug.debug(Debug.MODULE_VERIFIER, "I didn't find a quorum of at least %d preprepares", getExecutionQuorumSize(parameters));
//        System.out.println("I didn't find a quorum of at least preprepares " + getExecutionQuorumSize(parameters));
        int count = 0;
        int count2 = 0;

        for (int i = 0; i < prepareMacs.length; i++) {
            if (prepareMacs[i] || commitMacs[i]) count++;
            if (commitMacs[i]) count2++;
        }
        return count >= getLargeVerifierQuorumSize(parameters) || count2 >= getSmallVerifierQuorumSize(parameters);
    }

    public boolean isVerifyQuorumFull() {
        //System.out.println("verifyCOunt: " + verifyCount + ", execution count: " + parameters.getExecutionCount());
        return verifyCount == parameters.getExecutionCount();
    }

    public boolean isPrepared() {
        int count = 0;
        int count2 = 0;
        for (int i = 0; i < prepareMacs.length; i++) {
            if (prepareMacs[i] || commitMacs[i]) count++;
            if (commitMacs[i]) count2++;
        }
        return count >= getLargeVerifierQuorumSize(parameters) || count2 >= getSmallVerifierQuorumSize(parameters);
    }

    public int getPreparedCount() {
        return prepareCount;
    }

    public boolean isCommitted() {
        return commitCount >= getLargeVerifierQuorumSize(parameters);
    }


    // clears all of the fields
    protected boolean clear;

    public void clear() {
        clear = true;
        for (int i = 0; i < verifyMacs.length; i++) {
            verifyMacs[i] = false;
            verifyCache[i] = null;
        }
        for (int i = 0; i < prepareMacs.length; i++) {
            prepareMacs[i] = false;
            commitMacs[i] = false;
            prepareCache[i] = null;
            commitCache[i] = null;
        }
        verifyMessages.clear();
        verifyCount = 0;
        prepareCount = 0;
        commitCount = 0;
        // gc anything that is not already null
        historyAndState = null;
        seqNo = -1;
        commit = null;
        prepare = null;
    }


    public boolean isClear() {
        return clear;
    }

    final public static int getExecutionQuorumSize(Parameters param) {
        return param.largeExecutionQuorumSize();
    }

    final public static int getLargeVerifierQuorumSize(Parameters param) {
        return param.largeVerifierQuorumSize();
    }

    final public static int getSmallVerifierQuorumSize(Parameters param) {
        return param.smallVerifierQuorumSize();
    }

    final public static int index(long replica) {
        return (int) replica;
    }
}
