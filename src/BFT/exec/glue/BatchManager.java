package BFT.exec.glue;

import BFT.Debug;
import BFT.Parameters;
import BFT.exec.ExecBaseNode;
import BFT.messages.Digest;
import BFT.messages.HistoryAndState;
import BFT.messages.VerifyMessage;
import BFT.messages.VerifyResponseMessage;
import merkle.IndexedThread;
import merkle.MerkleTree;
import merkle.MerkleTreeException;
import merkle.MerkleTreeInstance;
import org.apache.commons.javaflow.Continuation;
import util.UnsignedTypes;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

public class BatchManager {

    transient private final int workerThreadCount;
    transient private final boolean isMiddle;
    transient private final CRGlueThread[] workerThreads;

    transient private int taskCount;
    transient private int sliceCount;
    transient private int checkpointCount;
    transient private int resumedSliceCount;
    transient private int finishTaskCount;
    transient private int tasksFinishedInThisSlice;
    transient private int remainingThreadCount;
    transient static public int copyRemainingThreadCount;

    transient private int rollbackCount;

    //We don't use these variable anymore since the related logic exist in ExecBaseNode already
//    transient private int verifySent = 0;
//    transient private int verifyReceived = 0;
//    transient private int verifyToWait = 0;

    transient private boolean needRollback = false;
    transient private boolean sequential = false;
    transient private boolean sliceStarted = false;

    // transient private boolean justRollback = false;

    // transient private boolean notMyTurn = false;

    transient private long versionNumber = 0;

    // transient private PipelineManager pm;
    // transient private boolean stageFinished = false;

    transient private Continuation[] previousContinuations;
    transient private Continuation[] currentContinuations;

    transient private HashMap<Long, Long> requestNoToVersionNo = new HashMap<Long, Long>();

    transient private int initializedCount = 0;
    transient ExecBaseNode singletonExec;
    transient public boolean finishControlLoop = false;
    transient private boolean forceRollback = false;

    public BatchManager(int workerThreadCount, boolean isMiddle, CRGlueThread[] workerThreads) {
        this.workerThreadCount = workerThreadCount;
        this.isMiddle = isMiddle;
        this.workerThreads = workerThreads;

        previousContinuations = new Continuation[workerThreadCount];
        currentContinuations = new Continuation[workerThreadCount];

        initializedCount = 0;
        singletonExec = ExecBaseNode.singletonExec;
        forceRollback = singletonExec.getParameters().forceExecutionRollback;
    }

    public synchronized void finishInitializeContinuation() {
        initializedCount++;
        Debug.debug(Debug.MODULE_EXEC, "Thread %d: finished initializing continuation initializedCount=%d.\n",
                ((IndexedThread) Thread.currentThread()).getIndex(), initializedCount);
        if (initializedCount == workerThreadCount) {
            this.notify();
        }
    }

    public synchronized void waitForContinuationToInitialize() {
        if (!isMiddle)
            return;

        while (initializedCount < workerThreadCount) {
            try {
                this.wait();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        //try {
            //MerkleTreeInstance.get().addRoot(previousContinuations);
            //MerkleTreeInstance.get().addRoot(currentContinuations);
        //} catch (MerkleTreeException e) {
        //    e.printStackTrace();
        //}

        Debug.debug(Debug.MODULE_EXEC, "BatchManager - All continuations initialized.");

        MerkleTreeInstance.get().finishThisVersion();

        this.startNextMerkleTreeVersion();
    }

    public synchronized void startBatch(int taskCount) {
        this.taskCount = taskCount;
        this.remainingThreadCount = this.workerThreadCount > taskCount ?
                taskCount : this.workerThreadCount;
        this.finishTaskCount = 0;

        // sequential should be reset at the end of a parallel batch.
        //sequential = false; //we may start with sequential run-Remzi
        needRollback = false;
    }

    public synchronized void mainControlLoop() {
        Debug.debug(Debug.MODULE_EXEC, "Main thread: entering mainControlLoop().");
        // Add stack into the merkle tree
        Parameters parameters = singletonExec.getParameters();
        finishControlLoop = false;
//        System.out.println("isMiddle in batchManager: " + isMiddle);
        while (finishTaskCount < taskCount) {
            if (isMiddle) {
                boolean newNestedGroup = false; //if there is no new nested group, which means it is end of batch and we can't use backend
                                        //we should use verifiers
                // the main control loop first notifies all worker threads that a slice
                // is started.

                startSlice(remainingThreadCount);

                byte[] hash = null;

                // then it waits until all worker threads to hits the wall. a worker
                // thread hits wall by either reaching the end of a task or reaching a
                // point in the task where a nested request needs to be sent.
                // when a worker thread reaches the wall, it also takes a checkpoint.
                // the main control loop waits for all worker thread to finish
                // checkpointing.
                // at this point, it should send a message for verification.
                byte[] historyHash = singletonExec.getHistoryHash();

                waitForHitWallAndCheckpoint();
                if(parameters.batchSuggestion) {    
                    singletonExec.finalNestedCount = singletonExec.numberOfNestedRequestInTheSlice.get();
                    if(singletonExec.oldNestedGroupNo.get() == singletonExec.newNestedGroupNo.get()) {
                        newNestedGroup = false;
                    }
                    else{
                        singletonExec.oldNestedGroupNo.set(singletonExec.newNestedGroupNo.get());
                        newNestedGroup = true;
                    }
                    singletonExec.numberOfNestedRequestInTheSlice.set(0);
                }
//                if (singletonExec.getParameters().batchSuggestion) {
//                    singletonExec.tmpCountNested = singletonExec.numberOfNestedRequestInTheSlice.get();
//                    singletonExec.numberOfNestedRequestInTheSlice.set(0);
//                }

                // then the main control loop sends verification and waits for
                // verification result to come back.

                synchronized (singletonExec.executeLock) {
                    hash = MerkleTreeInstance.getShimInstance().getHash();
                    long versionNo = MerkleTreeInstance.getShimInstance().getVersionNo();
                    singletonExec.setLastVersionNoExecuted(versionNo);
                    //System.err.println("Hash: " + Arrays.toString(hash));
                   //((MerkleTree)MerkleTreeInstance.getShimInstance()).printTree();
                    if (forceRollback && (versionNo % 371 == 0)) {
                        hash[0] = (byte) (singletonExec.getMyIndex());
                        forceRollback = false;
                    }


                    VerifyMessage vm = null;
                    VerifyResponseMessage vrm = null;
                    long verifyView = singletonExec.getCurrentView();
                    long verifyVersion = versionNo;
                    HistoryAndState hasToSend = new HistoryAndState(parameters, Digest.fromBytes(parameters, historyHash),
                            Digest.fromBytes(parameters, hash));


                    if(parameters.sendVerify && (!parameters.backendAidedVerification || !newNestedGroup)) {
                        if(parameters.primaryBackup) {
//                            System.out.println("no need for verify message in primary backup mode");

                            if (singletonExec.myIndex != singletonExec.fd.primary()) {
                                singletonExec.lastSeqNoOrdered = verifyVersion;
                                singletonExec.replyCache.getLastRequestId(singletonExec.lastRequestIdOrdered);
                                vrm = new VerifyResponseMessage(parameters, singletonExec.currentView,
                                        verifyVersion, new HistoryAndState(parameters, Digest.fromBytes(parameters,
                                        historyHash), Digest.fromBytes(parameters, hash)), false, singletonExec.myIndex);
//                                System.out.println("send to other execution replicas");

                                Iterator<Long> iter = singletonExec.requestCache.keySet().iterator();
                                while (iter.hasNext()) {
                                    long tmp = iter.next();
                                    if (tmp <= verifyVersion - parameters.executionPipelineDepth) {
                                        iter.remove();
                                    } else {
                                        break;
                                    }
                                }

                                Debug.fine(Debug.MODULE_EXEC, "send vrm=%s\n", vrm);
                            }
                        }
                        else {
                            Debug.fine(Debug.MODULE_EXEC, "Hash after Executing %d = %s\n", verifyVersion,
                                    UnsignedTypes.bytesToHexString(hash));
                            vm = new VerifyMessage(parameters, verifyView, verifyVersion, hasToSend, singletonExec.getMyIndex());
                            singletonExec.authenticateVerifierMacArrayMessage(vm);
                        }
                    }

                    synchronized (singletonExec.getStatesToVerify()) {
                        singletonExec.getStatesToVerify().put(versionNo, Digest.fromBytes(parameters, hash));
                    }


                    if (parameters.doLogging) {
                        singletonExec.getLogger().enqueue(vm);
                    } else {
                        if (parameters.sendVerify && (!parameters.backendAidedVerification || !newNestedGroup)) {
                            if(parameters.primaryBackup && singletonExec.myIndex != singletonExec.fd.primary()) {
                                singletonExec.sendToOtherExecutionReplicas(vrm.getBytes(), singletonExec.myIndex);
                            }
                            else if (!parameters.primaryBackup)
                            {
//                                System.out.println("send verification request for group no: " + singletonExec.newNestedGroupNo.get());
                                singletonExec.sendToAllVerifierReplicas(vm.getBytes());
                            }
                        }
                        else if(parameters.backendAidedVerification && newNestedGroup) {
                            singletonExec.addHashForGroup(singletonExec.oldNestedGroupNo.get(), hash);
                            singletonExec.wallStateHash = hash;
//                            System.out.println("hash of wall: " + Arrays.toString(hash) + " for group: " + singletonExec.oldNestedGroupNo.get());
                        }

                        if (finishTaskCount == taskCount) {
                            long seqNo = singletonExec.getLastSeqNoExecuted() + 1;
                            singletonExec.versionNoToSeqNO.put(versionNo, seqNo);
                        }
                        Debug.fine(Debug.MODULE_EXEC, "send vm=%s\n", vm);
                    }
                    singletonExec.verifyResponseReceived = false;
                    singletonExec.sliceVerified = false;
                    if (!parameters.sendVerify || (parameters.backendAidedVerification && newNestedGroup) ||
                            (parameters.primaryBackup && singletonExec.myIndex != singletonExec.fd.primary()) ) {
//                        System.out.println("need fake verification");
                        singletonExec.fakedVerification(verifyView, verifyVersion, hasToSend);
                    }
                }

                //TODO wait on a lock and notify in process(VRM)
                waitForVerificationResponse();

                // based on the decision, the main control loop decides to continue
                // execution or rollback.
                rollbackOrContinue(hash);
                if (finishControlLoop) {
                    return;
                }
                // make sure all threads are either resumed or rolled back before the
                // next slice can be started.
                // waitForSliceToResume();
            } else {
                try {
//                    System.out.println("yangwang waitForBatchToComplete " + sliceCount);
                    this.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace(System.out);
                }
            }
        }
    }

    private synchronized void startSlice(int remainingThreadCount) {
        // task count can be more than number of threads.
        // in this case, only number of threads slices should be started.
        // others should wait until next stage

        try {
            MerkleTreeInstance.get().gcIfNeeded();
        } catch (MerkleTreeException e) {
            e.printStackTrace();
        }

        sliceCount = remainingThreadCount;

        sliceStarted = true;
        checkpointCount = 0;
        resumedSliceCount = 0;
        tasksFinishedInThisSlice = 0;

        // sequential = false;
        // needRollback = false;

        Debug.debug(Debug.MODULE_EXEC,
                "Main Thread: Starting a slice. taskCount = %d sliceCount = %d\n", remainingThreadCount,
                sliceCount);

        this.notifyAll();
    }

    private synchronized void waitForSliceToStart() {
        //These debugs cause and error when rolling back because merkle threads are not indexed threads
        //Debug.debug(Debug.MODULE_EXEC, "Thread %d: waiting for slice to start.\n",
        //((IndexedThread) Thread.currentThread()).getIndex());
        while (!sliceStarted) {
            try {
                this.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        //Debug.debug(Debug.MODULE_EXEC, "Thread %d: notified for slice start.\n",
        //((IndexedThread) Thread.currentThread()).getIndex());
    }

    private synchronized void waitForHitWallAndCheckpoint() {
        Debug.debug(Debug.MODULE_EXEC, "Main thread: will wait for all threads to checkpoint.\n");
        while (checkpointCount < sliceCount) {
            try {
                this.wait();
                Debug.debug(Debug.MODULE_EXEC,
                        "Main thread: notified for checkpoint. checkpointCount=%d\n", checkpointCount);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        Debug.debug(Debug.MODULE_EXEC, "Main thread: all threads checkpointed.\n");
        sliceStarted = false;

        Debug.debug(Debug.MODULE_EXEC, "Main thread: finish this version %d.\n", versionNumber);
        MerkleTreeInstance.get().finishThisVersion();
    }

//    private synchronized void sendVerification() {
//        // TODO
//        verifyToWait = verifySent;
//        Debug.debug(Debug.MODULE_EXEC, "Main thread: sending verification message.\n");
//        // if (hitWallCount == sliceCount) {
//        // Throwable t = new Throwable();
//        // t.printStackTrace(System.out);
//        // TODO send verification
//        verifySent++;
//        // verifySucceed();
//        // }
//    }

    private synchronized void waitForVerificationResponse() {
        synchronized (singletonExec.verificationLock) {
            while (!singletonExec.verifyResponseReceived) {
                try {
                    singletonExec.verificationLock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        Debug.debug(Debug.MODULE_EXEC, "Main thread: wait for verification response.\n");
        verifySucceed();
//        while (verifyReceived <= verifyToWait) {
//            try {
//                this.wait();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }

        Debug.debug(Debug.MODULE_EXEC, "Main thread: response received. needRollback=%s\n",
                needRollback);
    }

    private synchronized void rollbackOrContinue(byte[] hash) {
        if (needRollback) {
            Debug.debug(Debug.MODULE_EXEC, "Main thread: decide to rollback. needRollback=%s\n",
                    needRollback);
            // rollback the merkle tree.
            Debug.debug(Debug.MODULE_EXEC,
                    "Main thread: currentVersionNumber=%d rollback versionNumber=%d\n", versionNumber,
                    versionNumber - 1);

            Debug.debug(Debug.MODULE_EXEC,
                    "Main thread: finishTaskCount=%d rollback finishTaskCount=%d\n", finishTaskCount,
                    finishTaskCount - tasksFinishedInThisSlice);

            // rollback version number;
            finishTaskCount -= tasksFinishedInThisSlice;
            versionNumber--;
            remainingThreadCount = copyRemainingThreadCount;
            singletonExec.resetThreadIdToExecute();

            // rollback merkle tree
            try {
                MerkleTreeInstance.get().rollBack(versionNumber);
            } catch (MerkleTreeException e) {
                e.printStackTrace();
            }

            this.startNextMerkleTreeVersion();

            // rollback continuation, request and batch
            rollbackCount = 0;
            for (int i = 0; i < workerThreads.length; i++) {
                ((CRGlueThread) workerThreads[i]).rollback();
            }

            waitForSliceToRollback();
        } else {
            Debug.debug(Debug.MODULE_EXEC, "Main thread: decide to continue. needRollback=%s\n",
                    needRollback);
            //TODO hash shouldn't be used in case of rollback
            forceRollback = singletonExec.getParameters().forceExecutionRollback;
            singletonExec.setHistoryHash(hash);
            this.startNextMerkleTreeVersion();

            for (int i = 0; i < workerThreads.length; i++) {
                ((CRGlueThread) workerThreads[i]).continueExecution();
            }

        }
    }

    public synchronized void finishRollback() {
        rollbackCount++;
        Debug.debug(Debug.MODULE_EXEC, "Thread %d: rollback finished. rollbackCount=%d\n",
                ((IndexedThread) Thread.currentThread()).getIndex(), rollbackCount);
        if (rollbackCount == sliceCount) {
            this.notify();
        }
    }

    private synchronized void waitForSliceToRollback() {
        Debug.debug(Debug.MODULE_EXEC, "Main thread: wait for slice to rollback. rollbackCount=%d\n",
                rollbackCount);
        while (rollbackCount < sliceCount) {
            try {
                this.wait();
                Debug.debug(Debug.MODULE_EXEC,
                        "Main thread: wait for slice to rollback. rollbackCount=%d\n", rollbackCount);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        needRollback = false;
    }

    public synchronized void hitWallAndCheckpoint() {
        if (!isMiddle)
        {
            throw new RuntimeException(" the backend shouldn't come here");
        }

        if (!sequential) {
            waitForSliceToStart();
            CRGlueThread crThread = ((CRGlueThread) Thread.currentThread());
            Debug.debug(Debug.MODULE_EXEC,
                    "Thread %d: reached wall and will be checkpointed. checkpointCount=%d\n",
                    crThread.getIndex(), checkpointCount);
            Debug.debug(Debug.MODULE_EXEC, "Thread %d: before checkpoint %d\n", crThread.getIndex(),
                    System.currentTimeMillis());
            Debug.info(Debug.MODULE_EXEC, "TIMINGS[PBM] Thread %d StartCheckpoint %d",
                    ((IndexedThread) Thread.currentThread()).getIndex(), System.nanoTime());
            crThread.checkpoint();
            copyRemainingThreadCount = remainingThreadCount;
            Debug.info(Debug.MODULE_EXEC, "TIMINGS[PBM] Thread %d EndCheckpoint %d",
                    ((IndexedThread) Thread.currentThread()).getIndex(), System.nanoTime());

            // Execution for the worker will not reach this point until it hears back
            // the verification result.
            Debug.debug(Debug.MODULE_EXEC, "Thread %d: after resumed %d\n", crThread.getIndex(),
                    System.currentTimeMillis());

            Debug.debug(Debug.MODULE_EXEC,
                    "Thread %d: Slice verification result comes back. needRollback=%s\n",
                    ((IndexedThread) Thread.currentThread()).getIndex(), needRollback);
        } else {
            waitForSliceToStart();
            finishCheckpoint();
        }

    }

    public synchronized void finishCheckpoint() {
        checkpointCount++;
        Debug.debug(Debug.MODULE_EXEC, "Thread %d: finished checkpoint. checkpointCount=%d\n",
                ((IndexedThread) Thread.currentThread()).getIndex(), checkpointCount);
        if (checkpointCount == sliceCount) {
            this.notify();
        }
    }

    public synchronized void sliceResumed() {
        resumedSliceCount++;
        if (resumedSliceCount == sliceCount) {
            this.notify();
        }
        Debug.debug(Debug.MODULE_EXEC, "Thread %d: slice resumed. resumedSliceCount=%d\n",
                ((IndexedThread) Thread.currentThread()).getIndex(), resumedSliceCount);
    }

    public synchronized void finishTask(boolean isLast) {
        Debug.debug(Debug.MODULE_EXEC, "Thread %d: task is about to finish.\n",
                ((IndexedThread) Thread.currentThread()).getIndex());

        if (isMiddle) {
            if (isLast) {
                waitForSliceToStart();
                remainingThreadCount--;
                Debug.debug(Debug.MODULE_EXEC, "finishing one task, remainingThreadCount = %d",
                        this.remainingThreadCount);
            }

            finishTaskCount++;
            tasksFinishedInThisSlice++;

            CRGlueThread crThread = ((CRGlueThread) Thread.currentThread());
            Debug.debug(Debug.MODULE_EXEC,
                    "Thread %d: reached wall and will be checkpointed. checkpointCount=%d\n",
                    crThread.getIndex(), checkpointCount);
            Debug.debug(Debug.MODULE_EXEC, "Thread %d: before checkpoint %d\n", crThread.getIndex(),
                    System.currentTimeMillis());

            if (isLast) {
                crThread.checkpoint();
                copyRemainingThreadCount = remainingThreadCount;
            }

            // Execution for the worker will not reach this point until it hears back
            // the verification result.
            Debug.debug(Debug.MODULE_EXEC, "Thread %d: after resumed %d\n", crThread.getIndex(),
                    System.currentTimeMillis());

            Debug.debug(Debug.MODULE_EXEC,
                    "Thread %d: Slice verification result comes back. needRollback=%s\n",
                    ((IndexedThread) Thread.currentThread()).getIndex(), needRollback);
        } else {
            finishTaskCount++;
            tasksFinishedInThisSlice++;
//            System.out.println("finish task count is incremented");
	}

        if (finishTaskCount == taskCount)
            this.notify();
        Debug.debug(Debug.MODULE_EXEC,
                "Thread %d: task finished. finishTaskCount=%d finishedInThisSlice=%d taskCount=%d\n",
                ((IndexedThread) Thread.currentThread()).getIndex(), finishTaskCount,
                tasksFinishedInThisSlice, taskCount);
    }

    public synchronized void finishThread() {
        //Debug.debug(Debug.MODULE_EXEC, "Main Thread: finishThread(), remainingThreadCount = %d",
        //    this.remainingThreadCount);
    }

    public synchronized void verifySucceed() {
//        verifyReceived++;

        // FIXME: This is for testing purpose.
        // on the second slice of the first batch, choose to rollback.
        if (!singletonExec.sliceVerified) {//(versionNumber == 13 && rollbackTestFlag) {
            // uncomment this to test rollback.
            needRollback = true;
            sequential = true;

            //	 needRollback = false;
            //	 sequential = false;
        } else {
            needRollback = false;
            sequential = false;
        }
        this.notifyAll();
    }

    public boolean isSequential() {
        return sequential;
    }

    public synchronized void startNextMerkleTreeVersion() {
        versionNumber++;
        Debug.debug(Debug.MODULE_EXEC, "Main thread: start a new version %d.\n", versionNumber);
        MerkleTreeInstance.get().setVersionNo(versionNumber);
    }

    public synchronized long getMerkleTreeVersion() {
        return versionNumber;
    }

    public synchronized void linkRequestNoToMerkleTreeVersion(long requestNo) {
        requestNoToVersionNo.put(requestNo, versionNumber);
    }

    public synchronized long getMerkleTreeVersionLinkedWithRequestNo(long requestNo) {
        Long l = requestNoToVersionNo.get(requestNo);
        return l;
    }

    public synchronized void passCheckpoint(int threadID, Continuation currentCheckpoint) {
        Debug.debug(Debug.MODULE_EXEC, "Thread %d: pass checkpoint to Main Thread at version %d.\n",
                threadID, versionNumber);

        try {
            Debug.debug(Debug.MODULE_EXEC, "Main thread: update continuations in version %d.\n", versionNumber);
            MerkleTreeInstance.get().update(previousContinuations);
            MerkleTreeInstance.get().update(currentContinuations);
        } catch (MerkleTreeException e) {
            e.printStackTrace();
        }

//		synchronized (currentContinuations) {
        previousContinuations[threadID] = currentContinuations[threadID];
        currentContinuations[threadID] = currentCheckpoint;
//		}
    }

    public boolean needRollback() {
        return needRollback;
    }
}
