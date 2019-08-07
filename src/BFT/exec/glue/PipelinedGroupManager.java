package BFT.exec.glue;

import BFT.Debug;
import BFT.Parameters;
import BFT.exec.ExecBaseNode;
import BFT.messages.Digest;
import BFT.messages.HistoryAndState;
import BFT.messages.VerifyMessage;
import BFT.messages.VerifyResponseMessage;
import merkle.IndexedThread;
import merkle.MerkleTreeException;
import merkle.MerkleTreeInstance;
import org.apache.commons.javaflow.Continuation;
import util.UnsignedTypes;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;


/**
 * This object will have an array of threads that each will execute a request. These objects
 * are intended to be pipelined such that when all threads hit a wall, this object yields execution
 * to another pipelined batch object.
 *
 * @author david, jimmy
 */
public class PipelinedGroupManager {

    transient private final int workersPerBatch;
    transient private final boolean isMiddle;
    transient private final CRGlueThread[] workerThreads;

    transient private int taskCount;
    transient private int sliceCount;
    transient private int checkpointCount;
    transient private int resumedSliceCount;
    transient private int finishTaskCount;
    transient private int tasksFinishedInThisSlice;
    transient private int remainingThreadCount;

    transient private int rollbackCount;

    transient private int verifySent = 0;
    transient private int verifyReceived = 0;
    transient private int verifyToWait = 0;

    transient private boolean needRollback = false;
    transient private boolean sequential = false;
    transient private boolean sliceStarted = false;
    transient private boolean rollbackTestFlag = true;

    transient private int threadIDToExecute = 0;

    transient private long[] versionNumber;

    transient private Continuation[] previousContinuations;
    transient private Continuation[] currentContinuations;

    transient private int id;
    transient private int initializedCount;

    transient private Parameters params;

    transient private ParallelPipelineManager parallelPipelineManager;

    transient private LoopThread loopThread;

    transient ExecBaseNode singletonExec;
    transient boolean newNestedGroup = false; //if there is no new nested group, which means it is end of batch and we can't use backend
                                                //we should use verifiers


    public PipelinedGroupManager(int workersPerBatch, CRGlueThread[] workerThreads,
                                 ParallelPipelineManager parallelPipelineManager,
                                 long[] versionNumber,
                                 int id, Parameters params, int myIndex) {

        this.workersPerBatch = workersPerBatch;
        this.isMiddle = true;
        this.workerThreads = workerThreads;

        // Initialize the merkle tree related stuff
        this.previousContinuations = new Continuation[workersPerBatch];
        this.currentContinuations = new Continuation[workersPerBatch];

        this.versionNumber = versionNumber;

        this.id = id;
        //this.currentBatch = 0;
        this.initializedCount = 0;

        this.params = params;
        this.parallelPipelineManager = parallelPipelineManager;
        this.loopThread = new LoopThread();
        loopThread.start();
        singletonExec = ExecBaseNode.singletonExec;
    }

    /*public synchronized void setCurrentBatch(int currentBatch) {
        boolean different = this.currentBatch != currentBatch;
        this.currentBatch = currentBatch;
        if (this.id == currentBatch) {
            if (different) {
                Debug.debug(Debug.MODULE_EXEC, "Waking up all of the threads for this batch..");
                wakeUpThreads();
            }
            Debug.debug(Debug.MODULE_EXEC, "Waking up batch %d because it's now our turn", id);
            this.notify();
        }
    }*/

    public synchronized void wakeUpBatchManager() {
        this.notify();
    }

    public synchronized void finishInitializeContinuation() {
        initializedCount++;
        Debug.debug(Debug.MODULE_EXEC, "Thread %d: finished initializing continuation initializedCount=%d.\n",
                ((IndexedThread) Thread.currentThread()).getIndex(), initializedCount);
        if (initializedCount == workersPerBatch) {
            this.notify();
        }
    }

    public synchronized void waitForContinuationToInitialize() {
        if (!isMiddle) {
            initializedCount = workersPerBatch;
            return;
        }

        while (initializedCount < workersPerBatch) {
            try {
                this.wait();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        try {
            MerkleTreeInstance.get().addRoot(previousContinuations);
            MerkleTreeInstance.get().addRoot(currentContinuations);
        } catch (MerkleTreeException e) {
            e.printStackTrace();
        }

        Debug.debug(Debug.MODULE_EXEC, "PGM - All continuations initialized.");

        Debug.info(Debug.MODULE_EXEC, "TIMINGS[PBM] Thread %d finishThisVersionStart %d", id, System.nanoTime());

        Debug.info(Debug.MODULE_EXEC, "TIMINGS[PBM] Thread %d finishThisVersionEnd %d", id, System.nanoTime());
    }

    public synchronized void passCheckpoint(int threadID, Continuation currentCheckpoint) {
        Debug.debug(Debug.MODULE_EXEC, "Thread %d: pass checkpoint to Main Thread at version %d.\n",
                threadID, versionNumber[0]);

        threadID %= workersPerBatch;
        try {
            Debug.debug(Debug.MODULE_EXEC, "Main thread: update continuations in version %d.\n", versionNumber[0]);
            MerkleTreeInstance.get().update(previousContinuations);
            MerkleTreeInstance.get().update(currentContinuations);
        } catch (MerkleTreeException e) {
            e.printStackTrace();
        }

        previousContinuations[threadID] = currentContinuations[threadID];
        currentContinuations[threadID] = currentCheckpoint;
    }

    public synchronized void setAllWork(LinkedList<LinkedList<GeneralGlueTuple>> allWork) {
        Debug.debug(Debug.MODULE_EXEC, "PB is setting all app work to this list of lists:");
        Debug.debug(Debug.MODULE_EXEC, allWork.toString());
        // Assigns out the work for this batch to the threads.
        //System.err.println("set all work");
        for (int i = 0; i < workersPerBatch; i++) {
            //assert(allWork.get(i).size() == 1);
            workerThreads[i].startAppWork(allWork.get(i), true);
        }
    }

    public synchronized void startBatch(int taskCount) {
        this.taskCount = taskCount;
        this.remainingThreadCount = this.workersPerBatch > taskCount ?
                taskCount : this.workersPerBatch;
        this.finishTaskCount = 0;

        // sequential should be reset at the end of a parallel batch.
        sequential = false;
        needRollback = false;

        verifySent = 0;
        verifyReceived = 0;
        verifyToWait = 0;
        threadIDToExecute = 0;
        //currentBatch = 0;
    }

    public void startLoopThread() {
        synchronized (loopThread) {
            loopThread.notify();
        }
    }

    public synchronized void mainControlLoop() {
        Debug.debug(Debug.MODULE_EXEC, "Main thread: entering mainControlLoop(). id=%d", id);
        // Add stack into the merkle tree
        if (isMiddle) {
            waitForBatchManagerTurn();
        }

        Parameters parameters = singletonExec.getParameters();

        Debug.debug(Debug.MODULE_EXEC, "ftc=" + finishTaskCount + " tc=" + taskCount);
        //System.err.println("ftc=" + finishTaskCount + " tc=" + taskCount);
        while (finishTaskCount < taskCount) {
            //System.err.println("ftc=" + finishTaskCount);
            Debug.debug(Debug.MODULE_EXEC, "ftc=" + finishTaskCount + " tc=" + taskCount);
            if (isMiddle) {
                // the main control loop first notifies all worker threads that a slice
                // is started.
                Debug.debug(Debug.MODULE_EXEC, "Main thread: about to startSlice. id=%d", id);
                //System.err.println("before start slice");
                waitForBatchManagerTurn();
                startSlice(remainingThreadCount);
                byte[] hash = null;
                Debug.debug(Debug.MODULE_EXEC, "Main thread: finished startingSlice. id=%d", id);

                byte[] historyHash = singletonExec.getHistoryHash();

                // then it waits until all worker threads to hits the wall. a worker
                // thread hits wall by either reaching the end of a task or reaching a
                // point in the task where a nested request needs to be sent.
                // when a worker thread reaches the wall, it also takes a checkpoint.
                // the main control loop waits for all worker thread to finish
                // checkpointing.
                // at this point, it should send a message for verification.
                Debug.debug(Debug.MODULE_EXEC, "Main thread: about to waitForHitWallAndCheckpoint. id=%d", id);
                //System.err.println("before wait for hit wall and checkpoint");
                waitForBatchManagerTurn();
                waitForHitWallAndCheckpoint();

                // then the main control loop sends verification and waits for
                // verification result to come back.
                Debug.debug(Debug.MODULE_EXEC, "Main thread: about to sendVerification. id=%d", id);
                //System.err.println("before execution lock");
                waitForBatchManagerTurn();
                synchronized (singletonExec.executeLock) {
                    hash = MerkleTreeInstance.getShimInstance().getHash();
                    long versionNo = MerkleTreeInstance.getShimInstance().getVersionNo();
                    singletonExec.setLastVersionNoExecuted(versionNo);
                    HistoryAndState hasToSend = new HistoryAndState(parameters, Digest.fromBytes(parameters, historyHash),
                            Digest.fromBytes(parameters, hash));
                    long verifyView = singletonExec.getCurrentView();
                    long verifyVersion = versionNo;

//                    System.out.println("oldno1: " + singletonExec.oldNestedGroupNo.get() + " newno1: " + singletonExec.newNestedGroupNo.get());

                    VerifyResponseMessage vrm = null;
                    VerifyMessage vm = null;
                    synchronized (singletonExec.getStatesToVerify()) {
                        singletonExec.getStatesToVerify().put(versionNo, Digest.fromBytes(parameters, hash));
                    }

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

                                Debug.fine(Debug.MODULE_EXEC, "Send vrm=%s\n", vrm);
//                                System.out.println(31);

                            }
                        }
                        else {
                            Debug.fine(Debug.MODULE_EXEC, "Hash After executing %d = %s\n", verifyVersion,
                                    UnsignedTypes.bytesToHexString(hash));
                            vm = new VerifyMessage(parameters, verifyView, verifyVersion, hasToSend, singletonExec.getMyIndex());
                            singletonExec.authenticateVerifierMacArrayMessage(vm);
                        }
                    }

//                    System.out.println(32);
                    if (parameters.doLogging) {
                        singletonExec.getLogger().enqueue(vm);
                    } else {
                        if(parameters.sendVerify && (!parameters.backendAidedVerification || !newNestedGroup)) {
                            if(parameters.primaryBackup && singletonExec.myIndex != singletonExec.fd.primary()) {
//                                System.out.println(33);
                                singletonExec.sendToOtherExecutionReplicas(vrm.getBytes(), singletonExec.myIndex);
                            }
                            else if (!parameters.primaryBackup)
                            {
//                                System.out.println("send verification for group no: " + singletonExec.newNestedGroupNo.get());
                                singletonExec.sendToAllVerifierReplicas(vm.getBytes());
                            }
                        }
                        else if(parameters.backendAidedVerification && newNestedGroup) {
                            singletonExec.addHashForGroup(singletonExec.oldNestedGroupNo.get(), hash);
                            singletonExec.wallStateHash = hash;
//                            System.out.println("hash of wall: " + Arrays.toString(hash) + " for group: " + singletonExec.oldNestedGroupNo.get());
                        }

                        if(finishTaskCount == taskCount) {
                            long seqNo = singletonExec.getLastSeqNoExecuted()+1;
                            singletonExec.versionNoToSeqNO.put(versionNo, seqNo);
                        }
                        Debug.fine(Debug.MODULE_EXEC, "send vm=%s\n", vm);
                    }
                    singletonExec.verifyResponseReceived = false;
                    singletonExec.sliceVerified = false;

                    if (!parameters.sendVerify || (parameters.backendAidedVerification && newNestedGroup) ||
                            (parameters.primaryBackup && singletonExec.myIndex != singletonExec.fd.primary()) ) {
//                        System.out.println("need fake verification");
                        newNestedGroup = false;
                        singletonExec.fakedVerification(verifyView, verifyVersion, hasToSend);
                    }
                }
//                sendVerification();
                Debug.debug(Debug.MODULE_EXEC, "Main thread: about to waitForVerificationResponse. id=%d", id);
                //System.err.println("before wait for verification response");
                waitForBatchManagerTurn();
                waitForVerificationResponse();

                // based on the decision, the main control loop decides to continue
                // execution or rollback.
                Debug.debug(Debug.MODULE_EXEC, "Main thread: about to rollbackOrContinue. id=%d", id);
                waitForBatchManagerTurn();
                //System.err.println("before rollbackOrContinue");
                rollbackOrContinue(hash);
                // make sure all threads are either resumed or rolled back before the
                // next slice can be started.
                // waitForSliceToResume();
                Debug.debug(Debug.MODULE_EXEC, "Main thread: finished rollbackOrContinue. id=%d", id);
                //System.err.println("done");
            } else {
                try {
                    Debug.debug(Debug.MODULE_EXEC, "PB %d loop thread waiting...", id);
                    this.wait();
                    Debug.debug(Debug.MODULE_EXEC, "PB %d loop thread woke up!", id);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        //System.err.println("while loop ended in main control loop");

        parallelPipelineManager.batchFinished(this.id);
        //System.err.println("batch finished");

        Debug.debug(Debug.MODULE_EXEC, "Main thread: batchIsAllDone! Loop thread will be deleted");
    }

    private synchronized void waitForBatchManagerTurn() {
        Debug.debug(Debug.MODULE_EXEC, "PB Loop waiting for turn id=" + id);
//		Map<Thread,StackTraceElement[]> map = Thread.getAllStackTraces();
//		for (Map.Entry<Thread, StackTraceElement[]> threadEntry : map.entrySet()) {
//			Debug.debug(Debug.MODULE_EXEC, "Thread:"+threadEntry.getKey().getName()+":"+threadEntry.getKey().getState());
//			for (StackTraceElement element : threadEntry.getValue()) {
//				Debug.debug(Debug.MODULE_EXEC, "--> "+element);
//			}
//		}
        while (parallelPipelineManager.getActiveBatch() != id) {
            try {
                Debug.debug(Debug.MODULE_EXEC, "PB about to wait because it's not our turn.");
                this.wait();
                Debug.debug(Debug.MODULE_EXEC, "PB Loop woke up! id=" + id);
            } catch (InterruptedException ie) {
                ie.printStackTrace();
            }
        }
        Debug.debug(Debug.MODULE_EXEC, "PB Loop finished waiting for turn id=" + id);
        this.notifyAll();
    }

    private synchronized void startSlice(int remainingThreadCount) {
        // task count can be more than number of threads.
        // in this case, only number of threads slices should be started.
        // others should wait until next stage
        sliceCount = remainingThreadCount;

        sliceStarted = true;
        checkpointCount = 0;
        resumedSliceCount = 0;
        tasksFinishedInThisSlice = 0;

        Debug.debug(Debug.MODULE_EXEC,
                "Main Thread: Starting a slice. taskCount = %d sliceCount = %d\n", remainingThreadCount,
                sliceCount);
        //RLB: We are waking up all threads in rollback sequential mode.
        wakeUpThreads();
        Debug.debug(Debug.MODULE_EXEC,
                "Finished waking up the threads...");
    }

    public synchronized void wakeUpThreads() {
        Debug.debug(Debug.MODULE_EXEC, "Notifying all threads waiting on the turn lock");
        for (CRGlueThread thread : workerThreads) {
            thread.wakeUpThread();
        }
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

        Debug.debug(Debug.MODULE_EXEC, "Main thread: finish this version %d.\n", versionNumber[0]);
        /*if (finishTaskCount == taskCount && id != lastPBM && (firstPBMForLastSlice != lastPBM)) {
            return;
        }*/
        Debug.info(Debug.MODULE_EXEC, "TIMINGS[PBM] Thread %d startFinishThisVersion %d", id,
                System.nanoTime());

        Debug.info(Debug.MODULE_EXEC, "TIMINGS[PBM] Thread %d endFinishThisVersion %d", id,
                System.nanoTime());
    }

    private synchronized void sendVerification() {
        verifyToWait = verifySent;
//
//        if (params.sendVerify) {
//            parallelPipelineManager.sendVerify(lastHash, hash, versionNumber[0]);
//        }

        verifySent++;
    }

    private synchronized void waitForVerificationResponse() {
        Debug.debug(Debug.MODULE_EXEC, "Main thread: wait for verification response.\n");
        synchronized (singletonExec.verificationLock) {
            while (!singletonExec.verifyResponseReceived) {
                try {
                    singletonExec.verificationLock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

//        if (!params.sendVerify) {
//            verifyReceived++;
//        }
//
//        System.err.println("VerifyReceived: " + verifyReceived + " verifyToWait: " + verifyToWait);
//        while (verifyReceived <= verifyToWait) {
//            System.err.println("try");
//            try {
//                this.wait();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }

        if (!singletonExec.sliceVerified || (!params.rollbackDisabled && versionNumber[0] == 13 && rollbackTestFlag)) {
            // uncomment this to test rollback.
            Debug.debug(Debug.MODULE_EXEC, "Creating artificial rollback now!!!!");
            needRollback = true;
            sequential = true;
            rollbackTestFlag = false;

            threadIDToExecute = 0;

            // Increment the version number so that we don't send verify messages with the same
            // versions again
            versionNumber[0]++;
            //	 needRollback = false;
            //	 sequential = false;
        } else {
            needRollback = false;
            sequential = false;
        }

        Debug.debug(Debug.MODULE_EXEC, "Main thread: response received. needRollback=%s\n",
                needRollback);
    }

    public synchronized void notifyVerifySucceeded() {
        verifyReceived++;
        Debug.debug(Debug.MODULE_EXEC, "PBM About to notify because we got vrm");
        this.notify();
    }


    private synchronized void rollbackOrContinue(byte[] hash) {
        Debug.debug(Debug.MODULE_EXEC, "In rollbackOrContinue()");
        if (needRollback) {
            Debug.debug(Debug.MODULE_EXEC, "Main thread: decide to rollback. needRollback=%s\n",
                    needRollback);
            // rollback the merkle tree.
            Debug.debug(Debug.MODULE_EXEC,
                    "Main thread: currentVersionNumber=%d rollback versionNumber=%d\n", versionNumber[0],
                    versionNumber[0] - 1);

            Debug.debug(Debug.MODULE_EXEC,
                    "Main thread: finishTaskCount=%d rollback finishTaskCount=%d\n", finishTaskCount,
                    finishTaskCount - tasksFinishedInThisSlice);

            // rollback version number;
            finishTaskCount -= tasksFinishedInThisSlice;
            versionNumber[0]--;

            // rollback merkle tree
            try {
                MerkleTreeInstance.get().rollBack(versionNumber[0]);
            } catch (MerkleTreeException e) {
                e.printStackTrace();
            }

            this.startNextMerkleTreeVersion();

            // rollback continuation, request and batch
            // TODO What is the scope of rollbackCount? What is it used for?
            rollbackCount = 0;
            threadIDToExecute = id * params.numWorkersPerBatch;
            for (int i = 0; i < workerThreads.length; i++) {
                ((CRGlueThread) workerThreads[i]).rollback();
            }

            waitForSliceToRollback();
        } else {
            Debug.debug(Debug.MODULE_EXEC, "Main thread: decide to continue. needRollback=%s\n",
                    needRollback);

            //if (finishTaskCount != taskCount || id == firstPBMForLastSlice) {
            singletonExec.setHistoryHash(hash);
            this.startNextMerkleTreeVersion();
            //}

            Debug.debug(Debug.MODULE_EXEC, "PBM about to continue the threads");

            int i = 0;
            for (CRGlueThread workerThread : workerThreads) {
                Debug.debug(Debug.MODULE_EXEC, "about to continueExecution of i=%d", i);
                workerThread.continueExecution();
            }
        }
    }

    public synchronized void startNextMerkleTreeVersion() {
        versionNumber[0]++;
        Debug.debug(Debug.MODULE_EXEC, "PBM startNMTV: start a new version %d.\n", versionNumber[0]);
        MerkleTreeInstance.get().setVersionNo(versionNumber[0]);
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

    public void hitWallAndCheckpoint() {
        Debug.debug(Debug.MODULE_EXEC, "Thread %d: inside HWAC...\n",
                ((IndexedThread) Thread.currentThread()).getIndex());
        if (!isMiddle)
            return;
        //System.out.println("hitWallAndCheckpoint");
        if (!sequential) {
            Debug.debug(Debug.MODULE_EXEC, "About to wait for slice to start");
            waitForSliceToStart();
            Debug.debug(Debug.MODULE_EXEC, "Finished waiting for slice to start");
            CRGlueThread crThread = ((CRGlueThread) Thread.currentThread());
            Debug.debug(Debug.MODULE_EXEC,
                    "Thread %d: reached wall and will be checkpointed. checkpointCount=%d\n",
                    crThread.getIndex(), checkpointCount);
            Debug.debug(Debug.MODULE_EXEC, "Thread %d: before checkpoint %d\n", crThread.getIndex(),
                    System.currentTimeMillis());
            Debug.info(Debug.MODULE_EXEC, "TIMINGS[PBM] Thread %d StartCheckpoint %d",
                    ((IndexedThread) Thread.currentThread()).getIndex(), System.nanoTime());
            crThread.checkpoint();
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
           // System.out.println("hitWallAndCheckpoint1");
            waitForSliceToStart();
           // System.out.println("hitWallAndCheckpoint2");
            finishCheckpoint();
           // System.out.println("hitWallAndCheckpoint3");
        }
        Debug.debug(Debug.MODULE_EXEC, "Thread %d: leaving HWAC...\n",
                ((IndexedThread) Thread.currentThread()).getIndex());
    }

    public synchronized void finishCheckpoint() {
        checkpointCount++;
        //System.out.println("finish checkpoint");
        Debug.debug(Debug.MODULE_EXEC, "Thread %d: finished checkpoint. checkpointCount=%d\n",
                ((IndexedThread) Thread.currentThread()).getIndex(), checkpointCount);
        if (checkpointCount == sliceCount) {
            ExecBaseNode ebn = singletonExec;
            if(ebn.getParameters().batchSuggestion) {
                ebn.finalNestedCount = ebn.numberOfNestedRequestInTheSlice.get();
                //System.out.println("zero: " + ebn.numberOfNestedRequestInTheSlice.get());
//                System.out.println("oldno: " + singletonExec.oldNestedGroupNo.get() + " newno: " + singletonExec.newNestedGroupNo.get());
                if(singletonExec.oldNestedGroupNo.get() != singletonExec.newNestedGroupNo.get()) {
                    newNestedGroup = true;
                }
                ebn.oldNestedGroupNo.set(ebn.newNestedGroupNo.get());
                ebn.numberOfNestedRequestInTheSlice.set(0);
                //System.out.println("final count: " + ebn.finalNestedCount);
            }
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

    /**
     * This version is called by threads when they are done with their task
     */
    private synchronized void waitForSliceToStart() {
        Debug.debug(Debug.MODULE_EXEC, "Thread %d: waiting for slice to start.\n",
                ((IndexedThread) Thread.currentThread()).getIndex());
        while (!sliceStarted) {
            try {
                this.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        Debug.debug(Debug.MODULE_EXEC, "Thread %d: notified for slice start.\n",
                ((IndexedThread) Thread.currentThread()).getIndex());
    }

    /**
     * This accessor is used from PPM when threads are waiting to checkpoint
     *
     * @return the value of sliceStarted
     */
    public synchronized boolean getSliceStarted() {
        return this.sliceStarted;
    }

    transient private int numCheckpointing = 0;

    public void finishTask(boolean isLast) {
        Debug.debug(Debug.MODULE_EXEC, "Finish task called");
        Debug.debug(Debug.MODULE_EXEC, "Thread %d: task is about to finish.\n",
                ((IndexedThread) Thread.currentThread()).getIndex());

        if (isMiddle) {
            Debug.debug(Debug.MODULE_EXEC, "[asdf] in isMiddle");
            if (isLast) {
                waitForSliceToStart();
                Debug.debug(Debug.MODULE_EXEC, "finishing one task, remainingThreadCount = %d",
                        this.remainingThreadCount);
            }

            CRGlueThread crThread = ((CRGlueThread) Thread.currentThread());
            Debug.debug(Debug.MODULE_EXEC,
                    "Thread %d: reached wall and will be checkpointed. checkpointCount=%d\n",
                    crThread.getIndex(), checkpointCount);
            Debug.debug(Debug.MODULE_EXEC, "Thread %d: before checkpoint %d\n", crThread.getIndex(),
                    System.currentTimeMillis());
            boolean shouldNotify = false;
            synchronized (this) {
                if (isLast) {
                    remainingThreadCount--;
                }
                finishTaskCount++;
                tasksFinishedInThisSlice++;
                numCheckpointing++;
            }
            if (isLast) {
                crThread.checkpoint();
            }
            synchronized (this) {
                numCheckpointing--;
                if (numCheckpointing == 0 && finishTaskCount == taskCount) {
                    this.notify();
                }
            }
            Debug.debug(Debug.MODULE_EXEC, "[asdf] remainingThreadCount:" + remainingThreadCount);

            Debug.debug(Debug.MODULE_EXEC, "[asdf] finishTaskCount: " + finishTaskCount);
            Debug.debug(Debug.MODULE_EXEC, "[asdf] tasksFinishedInThisSlice: " + tasksFinishedInThisSlice);

            // Execution for the worker will not reach this point until it hears back
            // the verification result.
            Debug.debug(Debug.MODULE_EXEC, "Thread %d: after resumed %d\n", crThread.getIndex(),
                    System.currentTimeMillis());

            Debug.debug(Debug.MODULE_EXEC,
                    "Thread %d: Slice verification result comes back. needRollback=%s\n",
                    ((IndexedThread) Thread.currentThread()).getIndex(), needRollback);
        } else {
            synchronized (this) {
                finishTaskCount++;
                tasksFinishedInThisSlice++;
                if (finishTaskCount == taskCount) {
                    this.notify();
                }
            }
        }
        Debug.debug(Debug.MODULE_EXEC, "is equal? " + finishTaskCount + ", " + taskCount);
        Debug.debug(Debug.MODULE_EXEC,
                "Thread %d: task finished. finishTaskCount=%d finishedInThisSlice=%d taskCount=%d\n",
                ((IndexedThread) Thread.currentThread()).getIndex(), finishTaskCount,
                tasksFinishedInThisSlice, taskCount);
    }

    public boolean isSequential() {
        return sequential;
    }

    public int getThreadIDToExec() {
        return threadIDToExecute;
    }

    public void nextThreadIDToExec() {
        Debug.debug(Debug.MODULE_EXEC, "Thread %d called ntite", threadIDToExecute);
        threadIDToExecute++;
        int offset = id * params.numWorkersPerBatch;
        threadIDToExecute = (threadIDToExecute - offset) % params.numWorkersPerBatch + offset;
    }

    public boolean needRollback() {
        needRollback = true;
        Debug.debug(Debug.MODULE_EXEC, "Calling needRollback() in PipelinedGroupManager");
        return needRollback;
    }

    public void killLoopThread() {
        loopThread.notDead = false;
        synchronized (loopThread) {
            loopThread.notify();
        }
    }

    public class LoopThread extends Thread {

        public transient boolean notDead = true;

        @Override
        public synchronized void run() {
            while (notDead) {
                try {
                    this.wait();
                    Debug.debug(Debug.MODULE_EXEC, "PipelinedBatch LoopThread %d entering the mainControlLoop.", id);
                    if (notDead) {
                        mainControlLoop();
                    }
                    Debug.debug(Debug.MODULE_EXEC, "PipelinedBatch LoopThread %d leaving. the mainControlLoop.", id);
                } catch (Exception e) {

                }
            }
        }
    }
}
