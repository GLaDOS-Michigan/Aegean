package BFT.exec.glue;

import BFT.Debug;
import BFT.Parameters;
import BFT.exec.ExecBaseNode;
import merkle.IndexedThread;
import merkle.MerkleTreeException;
import merkle.MerkleTreeInstance;
import org.apache.commons.javaflow.Continuation;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ParallelPipelineManager {

    transient private final int workersPerBatch;
    transient private final int numPipelinedBatches;
    transient private final CRGlueThread[][] allWorkerThreads;

    transient private boolean[] activeBatches;
    transient private boolean[] hasBeenActiveBatch;
    transient private int activeBatch;
    transient private PipelinedGroupManager[] groupManagers;

    transient private long[] versionNumber;
    transient private ConcurrentHashMap<Long, Long> requestNoToVersionNo = new ConcurrentHashMap<Long, Long>();
    transient private LinkedList<LinkedList<LinkedList<GeneralGlueTuple>>> allBatches;
    transient private boolean[][] yieldMap;
    transient private boolean[][] finishedMap;

    transient private Parameters params;

    transient private Object loopLock;
    transient private boolean notified = false;

    transient private ExecBaseNode ebn;

    /*
    /The number of threads that can run at anytime.
    /This number must be decremented when a thread begins/resumes execution
    /and incremented when a thread pauses execution. As well as it's lock.
    */
    /*
    transient private int concurrentExecutionCounter;
    transient private Object concurrentExecutionCounterLock;

    //The number of outstanding threads that have not reached a place to pause.
    transient private int remainingActiveThreads;
    //The number of threads that have finished and no longer need to be woken up.
    transient private int finishedThreads;
    //Their lock.
    transient private Object remainingActiveThreadsLock;

    */

    public ParallelPipelineManager(int workersPerBatch, int numPipelinedBatches, CRGlueThread[][] allGlueThreads, Parameters p,
                                   Object loopLock, int myIndex, ExecBaseNode ebn) {

        this.ebn = ebn;
        this.workersPerBatch = workersPerBatch;
        this.numPipelinedBatches = numPipelinedBatches;

        this.versionNumber = new long[]{0L};

        this.activeBatches = new boolean[numPipelinedBatches];
        this.hasBeenActiveBatch = new boolean[numPipelinedBatches];
        this.groupManagers = new PipelinedGroupManager[numPipelinedBatches];
        this.allWorkerThreads = allGlueThreads;
        for (int i = 0; i < numPipelinedBatches; i++) {
            // Initialize all the threads for this batch
            groupManagers[i] = new PipelinedGroupManager(workersPerBatch,
                    allWorkerThreads[i], this, versionNumber, i, p, myIndex);
            activeBatches[i] = false;
            hasBeenActiveBatch[i] = false;
        }
        activeBatch = -1;
        yieldMap = new boolean[numPipelinedBatches][workersPerBatch];
        finishedMap = new boolean[numPipelinedBatches][workersPerBatch];

        this.params = p;
        this.loopLock = loopLock;


        try {
            MerkleTreeInstance.get().addRoot(activeBatch);
            MerkleTreeInstance.get().addRoot(yieldMap);
            MerkleTreeInstance.get().addRoot(finishedMap);
            //MerkleTreeInstance.get().addRoot(activeBatches);
            MerkleTreeInstance.get().addRoot(loopLock);
        } catch (MerkleTreeException exception) {
            Debug.error(Debug.MODULE_EXEC, "Unable to add PPM objects to merkle tree", exception);
        }
    }

    public void finishTask(boolean isLast) {
        // Each CRGlueThread will call this. When all of them for the current batch are done, the
        // batch is done. When the batch is done, tell the scheduler.
        groupManagers[activeBatch].finishTask(isLast);
    }

    private void saveObjectToMerkle(Object o) {
        try {
            MerkleTreeInstance.get().update(o);
        } catch (MerkleTreeException exception) {
            Debug.error(Debug.MODULE_EXEC, "Unable to update PPM objects to merkle tree", exception);
        }
    }

    public synchronized void startBatches(LinkedList<GeneralGlueTuple> allWork) {

        Debug.info(Debug.MODULE_EXEC, "TIMINGS: ---------------BEGIN-----------------");
        Debug.info(Debug.MODULE_EXEC, "TIMINGS[PPM] Thread 0 gcStart %d", System.nanoTime());
        if (this.versionNumber[0] >= 4) {
            //MerkleTreeInstance.get().makeStable(this.versionNumber[0] - 4);
            try {
                Debug.info(Debug.MODULE_EXEC, "Garbage Collection!");
                MerkleTreeInstance.get().gcIfNeeded();
            } catch (MerkleTreeException e) {
                e.printStackTrace();
            }
        }

        //System.err.println("run batches with " + numPipelinedBatches + " numPipelinedBatches and " + workersPerBatch + " threads. It seems there is no mixer running");
        Debug.info(Debug.MODULE_EXEC, "TIMINGS[PPM] Thread 0 gcEnd %d", System.nanoTime());
        activeBatch = -1;
        saveObjectToMerkle(activeBatch);
        // We want to take the mixed batch and chop them up one more time
        allBatches = new LinkedList<LinkedList<LinkedList<GeneralGlueTuple>>>();
        for (int i = 0; i < numPipelinedBatches; i++) {
            LinkedList<LinkedList<GeneralGlueTuple>> threadWork = new LinkedList<LinkedList<GeneralGlueTuple>>();
            for (int j = 0; j < workersPerBatch; j++) {
                threadWork.add(new LinkedList<GeneralGlueTuple>());
            }
            allBatches.add(threadWork);
        }
        int counter = 0, grouping, batchNum, threadNum;
        for (GeneralGlueTuple theWork : allWork) {
            grouping = (counter % (workersPerBatch * numPipelinedBatches));
            //System.err.println("fillBatchesFIrst: " + params.fillBatchesFirst);
            if (params.fillBatchesFirst) { //TODO is there any difference-Remzi
                threadNum = grouping % workersPerBatch;
                batchNum = grouping / workersPerBatch;
            } else {
                threadNum = grouping / numPipelinedBatches;
                batchNum = grouping % numPipelinedBatches;
            }
            LinkedList<LinkedList<GeneralGlueTuple>> t1 = allBatches.get(batchNum);
            LinkedList<GeneralGlueTuple> t2 = t1.get(threadNum);
            t2.add(theWork);
            counter++;
        }

        for (int i = 0; i < numPipelinedBatches; i++) {
            int workCount = 0;
            for (LinkedList<GeneralGlueTuple> threadWorkList : allBatches.get(i)) {
                workCount += threadWorkList.size();
            }
            //System.err.println("starting batch with " + workCount + " tasks");
            groupManagers[i].startBatch(workCount);
            activeBatches[i] = true;
            hasBeenActiveBatch[i] = false;
        }
        yieldMap = new boolean[numPipelinedBatches][workersPerBatch];
        finishedMap = new boolean[numPipelinedBatches][workersPerBatch];
        Debug.debug(Debug.MODULE_EXEC, "All batches has finished");
        activeBatch = 0;
        saveObjectToMerkle(activeBatch);
        saveObjectToMerkle(yieldMap);
        saveObjectToMerkle(finishedMap);
        //saveObjectToMerkle(activeBatches);
        hasBeenActiveBatch[0] = true;
    }

    private synchronized boolean allBatchesDone() {
        boolean active = false;
        for (boolean b : activeBatches) {
            active |= b;
        }
        return !active;
    }

    private synchronized boolean allThreadsFinished() {
        boolean active = false;
        for (boolean[] finishedThreads : finishedMap) {
            for (boolean finished : finishedThreads) {
                active |= !finished;
            }
        }
        return !active;
    }

    public void mainControlLoop() {
        synchronized (loopLock) {
            // Actually start running all of the batches in the pipelined fashion
            Debug.debug(Debug.MODULE_EXEC, "Starting main control loop for the pbm");

            // Starting all the groupManagers
            for (int i = 0; i < numPipelinedBatches; i++) {
                groupManagers[i].setAllWork(allBatches.get(i));
            }
            for (PipelinedGroupManager batchManager : groupManagers) {
                batchManager.startLoopThread();
            }
        }

        // Wait for all the batches to be done.
        while (!allBatchesDone() || !allThreadsFinished()) {
            Debug.debug(Debug.MODULE_EXEC, "PPM about to sleep ab=" + activeBatch + " " + Arrays.toString(activeBatches));
            try {
                synchronized (loopLock) {
                    if (!notified) {
                        loopLock.wait();
                    }
                    notified = false;
                }
                Debug.debug(Debug.MODULE_EXEC, "PPM woke up! ab=" + activeBatch + " " + Arrays.toString(activeBatches));
                shouldYieldPipeline();
            } catch (InterruptedException ie) {
                ie.printStackTrace();
            }
        }
        Debug.debug(Debug.MODULE_EXEC, "All batches finished. PBM exitting mCL");
    }

    public void hitWallAndCheckpoint() {
        assert (groupManagers[activeBatch] != null);
        groupManagers[activeBatch].hitWallAndCheckpoint();
    }

    public void finishCheckpoint() {
        assert (groupManagers[activeBatch] != null);
        groupManagers[activeBatch].finishCheckpoint();
    }

    public int getThreadIDToExec() {
        return groupManagers[activeBatch].getThreadIDToExec();
    }

    public void nextThreadIDToExec() {
        groupManagers[activeBatch].nextThreadIDToExec();
    }

    public void finishRollback() {
        assert (groupManagers[activeBatch] != null);
        groupManagers[activeBatch].finishRollback();
    }

    public boolean needRollback() {
        assert (groupManagers[activeBatch] != null);
        Debug.debug(Debug.MODULE_EXEC, "Calling needRollback() on groupManagers");
        return groupManagers[activeBatch].needRollback();
    }

    public boolean isSequential() {
        if (activeBatch == -1) {
            return false;
        }
        assert (groupManagers[activeBatch] != null);
        return groupManagers[activeBatch].isSequential();
    }

    // We don't think this is ever used....
    public synchronized void finishPipelinedThread() {
        assert (groupManagers[activeBatch] != null);
        //groupManagers[activeBatch].finishPipelinedThread();
        yieldPipeline();
        assert false;
    }

    public void linkRequestNoToMerkleTreeVersion(long requestNo) {
        requestNoToVersionNo.put(requestNo, versionNumber[0]);
    }

    public long getMerkleTreeVersion() {
        return versionNumber[0];
    }

    public long getMerkleTreeVersionLinkedWithRequestNo(long requestNo) {
        return requestNoToVersionNo.get(requestNo);
    }

    public void passCheckpoint(int threadID, Continuation currentCheckpoint) {
        groupManagers[threadID / workersPerBatch].passCheckpoint(threadID, currentCheckpoint);
    }

    public boolean isMyTurn(int threadId) {
        //Debug.debug(Debug.MODULE_EXEC, "Thread %d: if I'm not active, I'll wait\n",
        //        ((IndexedThread) Thread.currentThread()).getIndex());

        // The thread id / num threads per batch is the batch number that's currently active
        int myBatch = threadId / workersPerBatch;

        //Debug.debug(Debug.MODULE_EXEC, "Thread %d: mybatch %d\n", threadId, myBatch);
        //Debug.debug(Debug.MODULE_EXEC, "Thread %d: My yield value %s\n", threadId, yieldMap[myBatch][threadId % workersPerBatch]);

        // If we had said we wanted to yield, we must do that before we say we no longer want to
        //Debug.debug(Debug.MODULE_EXEC, "ggg activeBatch=" + activeBatch + " my batch=" + myBatch);
        if (yieldMap[myBatch][threadId % workersPerBatch]) {
            //Debug.debug(Debug.MODULE_EXEC, "ggg returning false myBatch=" + myBatch + " threadid=" + threadId);
            return false;
        }
        return activeBatch == myBatch;
    }

    /**
     * This function determines if the currently active batch wants to yield the pipeline. If it does, and
     * updateActiveBatch() is called, true is returned. Otherwise it should return false.
     */
    public synchronized boolean shouldYieldPipeline() {
        // If all of the threads for the current batch have yielded, we need to move on to the next one.
        boolean anyFalse = false;
        for (int i = 0; i < workersPerBatch; i++) {
            anyFalse |= !yieldMap[activeBatch][i];
        }
        Debug.debug(Debug.MODULE_EXEC, "PPM inside SYP activeBAtches=" + Arrays.toString(activeBatches));
        if (!anyFalse) {
            Debug.debug(Debug.MODULE_EXEC, "All threads in ab=" + activeBatch + " have yielded. Updating active batch");
            updateActiveBatch();
            return true;
        }
        return false;
    }

    public void yieldPipeline() {
        Debug.debug(Debug.MODULE_EXEC, "Thread %d: I'm yielding the pipeline\n",
                ((IndexedThread) Thread.currentThread()).getIndex());
        int threadId = ((IndexedThread) Thread.currentThread()).getIndex();
        boolean notReady = false;
        synchronized (this) {
            assert (groupManagers[activeBatch] != null);

            // The thread id / num threads per batch is the batch number that's currently active
            int myBatch = threadId / workersPerBatch;

            // The batch of the thread that is yielding should be in the active batch
            assert myBatch == activeBatch;

            // We should not have yielded again before being swapped off
            assert !yieldMap[myBatch][threadId % workersPerBatch];

            yieldMap[myBatch][threadId % workersPerBatch] = true;
            for (int i = 0; i < workersPerBatch; i++) {
                notReady |= !yieldMap[myBatch][i];
            }
            saveObjectToMerkle(yieldMap);
        }

        if (!notReady) {
            Debug.debug(Debug.MODULE_EXEC, "Thread %d about to notify PPM to yield because " +
                    "everyone has", threadId);
            synchronized (loopLock) {
                notified = true;
                loopLock.notify();
            }
            Debug.debug(Debug.MODULE_EXEC, "Thread %d finished notifying PPM to yield", threadId);
        }
    }

    public void finishThread() {
        // Permanently mark as able to yield.
        int threadId = ((IndexedThread) Thread.currentThread()).getIndex();
        Debug.debug(Debug.MODULE_EXEC, "Thread is finishing with id=%d", threadId);
        boolean notReady = false;
        boolean someoneNotFinished = false;
        synchronized (this) {
            int myBatch = threadId / workersPerBatch;

            if (myBatch != activeBatch) {
                return;
            }

            Debug.debug(Debug.MODULE_EXEC, "Thread %d is about to assert mb %d ab %d", threadId, myBatch, activeBatch);
            assert myBatch == activeBatch;
            assert !finishedMap[myBatch][threadId % workersPerBatch];
            assert !yieldMap[myBatch][threadId % workersPerBatch];

            finishedMap[myBatch][threadId % workersPerBatch] = true;
            yieldMap[myBatch][threadId % workersPerBatch] = true;
            for (int i = 0; i < workersPerBatch; i++) {
                notReady |= !yieldMap[myBatch][i];
                someoneNotFinished |= !finishedMap[myBatch][i];
            }

            saveObjectToMerkle(finishedMap);
            saveObjectToMerkle(yieldMap);

            Debug.debug(Debug.MODULE_EXEC, "%d: yieldMap %s", threadId, Arrays.toString(yieldMap[myBatch]));
            Debug.debug(Debug.MODULE_EXEC, "%d: finishedMap%s", threadId, Arrays.toString(finishedMap[myBatch]));
        }

        if (!notReady) {
            Debug.debug(Debug.MODULE_EXEC, "Thread %d about to notify PPM to yield because " +
                    "everyone wants to yield and not everyone is done", threadId);
            synchronized (loopLock) {
                notified = true;
                loopLock.notify();
            }
            Debug.debug(Debug.MODULE_EXEC, "Thread %d finished notifying PPM to yield", threadId);
        }
    }

    private synchronized void updateActiveBatch() {
        Debug.debug(Debug.MODULE_EXEC, "inside updateActiveBatch without id");
        updateActiveBatch(activeBatch);
    }

    private synchronized void updateActiveBatch(int id) {
        Debug.debug(Debug.MODULE_EXEC, "inside updateActiveBatch with id");
        try {
            MerkleTreeInstance.get().gcIfNeeded();
        } catch (MerkleTreeException e) {
            e.printStackTrace();
        }
        int i = id;
        // Move the activeBatch pointer to the next active batch
        for (int j = 0; j < numPipelinedBatches; j++) {
            i = (i + 1) % numPipelinedBatches;

            boolean someThreadNotDone = false;
            for (int k = 0; k < workersPerBatch; k++) {
                someThreadNotDone |= !finishedMap[i][k];
            }
            if (someThreadNotDone || activeBatches[i]) {
                break;
            }
        }

        // The old activeBatch has now been active for a period.
        //hasBeenActiveBatch[activeBatch] = true;

        Debug.debug(Debug.MODULE_EXEC, "ActiveBatch: " + activeBatch + " -> " + i);
        activeBatch = i;

        // Mark everyone in the old active batch as not wanting to yield anymore
        for (int j = 0; j < workersPerBatch; j++) {
            // Only set the yieldMap value back to false if they aren't finished
            yieldMap[activeBatch][j] = finishedMap[activeBatch][j];
        }
        saveObjectToMerkle(activeBatch);
        saveObjectToMerkle(yieldMap);

        /*Debug.debug(Debug.MODULE_EXEC, "About to switch all of the PBM's active batch...");
        for (i = 0; i < numPipelinedBatches; i++) {
            groupManagers[i].setCurrentBatch(activeBatch);
        }*/
        groupManagers[activeBatch].wakeUpBatchManager();
        Debug.debug(Debug.MODULE_EXEC, "Finished setting all PBM's active batch...");

        // Only wake up the threads if the new activeBatch has been active before
        //if (hasBeenActiveBatch[activeBatch]) {
        Debug.debug(Debug.MODULE_EXEC, "batch %d has been active, notifying all waiting threads.", activeBatch);
        for (CRGlueThread t : allWorkerThreads[activeBatch]) {
            t.wakeUpThread();
        }
        //} else {
        //    Debug.debug(Debug.MODULE_EXEC, "batch %d has not been active", activeBatch);
        //}
    }

    public void waitForAllContinuationsToInitialize() {
        // Wait for each batch manager's continuations to initialize
        Debug.debug(Debug.MODULE_EXEC, "waiting for all continuations to initialize");

        for (PipelinedGroupManager bm : groupManagers) {
            bm.waitForContinuationToInitialize();
        }

        // Start the new merkle tree version
        groupManagers[0].startNextMerkleTreeVersion();
    }

    public synchronized void finishInitializeContinuation() {
        // Determine the right batch manager based off the thread id
        int threadId = ((IndexedThread) Thread.currentThread()).getIndex();
        groupManagers[threadId / workersPerBatch].finishInitializeContinuation();
    }

    public synchronized void startNextMerkleTreeVersion() {
        assert groupManagers[activeBatch] != null;
        groupManagers[activeBatch].startNextMerkleTreeVersion();
    }

    public void batchFinished(int id) {
        Debug.debug(Debug.MODULE_EXEC, "PBM %d is waking up PPM.", id);
        synchronized (loopLock) {

            if (isSequential()) {
                for (int i = 0; i < workersPerBatch; i++) {
                    yieldMap[id][i] = true;
                    finishedMap[id][i] = true;
                }
                saveObjectToMerkle(yieldMap);
                saveObjectToMerkle(finishedMap);
            }
            activeBatches[id] = false;
            //saveObjectToMerkle(activeBatches);
            notified = true;
            loopLock.notify();
        }
        Debug.debug(Debug.MODULE_EXEC, "PBM %d finished waking up PPM.", id);
    }

    public void killAllLoopThreads() {
        for (PipelinedGroupManager pbm : groupManagers) {
            pbm.killLoopThread();
        }
    }

    public void dumpThreads() {
        Debug.debug(Debug.MODULE_EXEC, "Starting a thread dump.");
        Map<Thread, StackTraceElement[]> map = Thread.getAllStackTraces();
        for (Map.Entry<Thread, StackTraceElement[]> threadEntry : map.entrySet()) {
            Debug.debug(Debug.MODULE_EXEC, "Thread:" + threadEntry.getKey().getName() + ":" + threadEntry.getKey().getState());
            for (StackTraceElement element : threadEntry.getValue()) {
                Debug.debug(Debug.MODULE_EXEC, "--> " + element);
            }
        }
        Debug.debug(Debug.MODULE_EXEC, "Ending thread dump");
    }

    transient private long lastSent = -1;

    public void notifyVerifySucceeded(long seqNo) {
        Debug.debug(Debug.MODULE_EXEC, "PPM About to nVS");
        assert groupManagers[activeBatch] != null;
        if (seqNo == lastSent) {
            groupManagers[activeBatch].notifyVerifySucceeded();
            lastSent = -1;
        } else {
            Debug.debug(Debug.MODULE_EXEC, "Verify should fail!!!");
        }
    }

    public int getActiveBatch() {
        return activeBatch;
    }
}
