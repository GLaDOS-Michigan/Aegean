package BFT.exec.glue;

import BFT.Debug;
import merkle.IndexedThread;

public class PipelinedSequentialManager {

    transient private final int workerThreadCount;
    transient private int numThreadsInActiveBatch;
    transient private boolean[] isActiveThread;
    transient private int activeThread = 0;
    transient private int numActiveThreads = 0;

    public PipelinedSequentialManager(int workerThreadCount) {
        this.workerThreadCount = workerThreadCount;

        activeThread = 0;
        numThreadsInActiveBatch = 0;
        numActiveThreads = workerThreadCount;
        isActiveThread = new boolean[workerThreadCount];
        for (int i = 0; i < workerThreadCount; i++) {
            isActiveThread[i] = true;
        }
    }


    public synchronized void startBatch() {
        //this.taskCount = taskCount;
        //finishTaskCount = 0;
        numActiveThreads = numThreadsInActiveBatch;
        for (int i = 0; i < numActiveThreads; i++) {
            isActiveThread[i] = true;
        }
        activeThread = 0;
        this.notifyAll();
    }

    public synchronized void yieldPipeline() {
//        Debug.debug(Debug.MODULE_EXEC, "Thread %d: yielding. Active thread = %d workerThreadCount = %d\n",
//                ((IndexedThread) Thread.currentThread()).getIndex(), activeThread, workerThreadCount);
        assert (activeThread == ((IndexedThread) Thread.currentThread()).getIndex());

        int currentThread = activeThread;
        if (numActiveThreads > 0) {
            while (true) {
                currentThread = (currentThread + 1) % numThreadsInActiveBatch;
//                System.err.println("current thread " + currentThread + ", threadsInthisbatch: " + numThreadsInActiveBatch);

                if (isActiveThread[currentThread]) {
                    activeThread = currentThread;
//                    System.err.println("thread " + currentThread + " is active");
//                    Debug.debug(Debug.MODULE_EXEC, "Next active thread is %d\n", activeThread);
                    notifyAll();
                    break;
                }
            }
        }
    }

    public synchronized void waitForMyTurn() {
//        Debug.debug(Debug.MODULE_EXEC, "Thread %d: if I'm not active, I'll wait\n",
//                ((IndexedThread) Thread.currentThread()).getIndex());
        try {
            int threadId = ((IndexedThread) Thread.currentThread()).getIndex();
            while (activeThread != threadId) {
//                Debug.debug(Debug.MODULE_EXEC, "Thread %d: not active, waiting\n",
//                        ((IndexedThread) Thread.currentThread()).getIndex());
                this.wait();
            }
        } catch (InterruptedException ie) {
            ie.printStackTrace();
        }
//        Debug.debug(Debug.MODULE_EXEC, "Thread %d: finished waiting!\n",
//                ((IndexedThread) Thread.currentThread()).getIndex());
    }

    public synchronized void finishThread() {
        int threadId = ((IndexedThread) Thread.currentThread()).getIndex();
        isActiveThread[threadId] = false;
        numActiveThreads--;
        yieldPipeline();
        if (numActiveThreads == 0) {
            this.notifyAll();
        }
    }

    public synchronized void waitForAllThreadsToDeactivate() {
        try {
            while (numActiveThreads > 0) {
                this.wait();
            }
        } catch (InterruptedException ie) {
            ie.printStackTrace();
        }
    }

    public void setNumThreadsInActiveBatch(int numThreadsInActiveBatch) {
        this.numThreadsInActiveBatch = numThreadsInActiveBatch;
    }
}
