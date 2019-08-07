package BFT.exec.glue;

import BFT.Debug;
import BFT.exec.RequestHandler;
import merkle.IndexedThread;
import org.apache.commons.javaflow.Continuation;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Random;

public class CRGlueThread extends IndexedThread {
    transient private static int threadCount = 0;
    transient private static ArrayList<CRGlueThread> allThreads = new ArrayList<CRGlueThread>();

    transient private int threadId;

    transient private Object taskLock = new Object();
    transient private Object checkpointLock = new Object();
    transient private Object turnLock = new Object();

    transient private boolean isRunning = true;

    transient private RequestHandler handler;
    transient private GeneralGlue glue;

    transient private Continuation currentCheckpoint;
    transient private Continuation previousCheckpoint;

    transient private boolean needCheckpoint = false;
    transient private boolean needRollback = false;

    transient private CRWrapper wrapper;

    transient private LinkedList<GeneralGlueTuple> appTask = null;
    transient private LinkedList<GeneralGlueTuple> localAppTask = null;
    transient private LinkedList<GeneralGlueTuple> copyLocalAppTask = null;
    transient private GeneralGlueTuple tuple = null;
    transient private GeneralGlueTuple copyTuple = null;

    transient private boolean firstSliceInBatch = false;
    transient private boolean rolledBackInThisBatch = false;
    public Random generator;

    public CRGlueThread(RequestHandler handler, GeneralGlue glue) {
        this.threadId = getAndIncreaseThreadCount();
        allThreads.add(this);

        this.handler = handler;
        this.glue = glue;

        this.wrapper = new CRWrapper(this);

        Debug.info(Debug.MODULE_EXEC, "TIMINGS[CRGT]: Thread %d Allocation %d", getIndex(), System.nanoTime());
        generator = new Random(threadId);
    }

    public synchronized static int getThreadCount() {
        return threadCount;
    }

    private synchronized static int getAndIncreaseThreadCount() {
        int ret = threadCount;
        threadCount++;
        return ret;
    }

    public static ArrayList<CRGlueThread> getAllThreads() {
        return allThreads;
    }

    @Override
    public int getIndex() {
        return threadId;
    }

    public void startAppWork(LinkedList<GeneralGlueTuple> tasks, boolean firstSliceInBatch) {
        Debug.info(Debug.MODULE_EXEC, "TIMINGS[CRGT]: Thread %d startAppWork %d", getIndex(), System.nanoTime());
        synchronized (taskLock) {
            this.firstSliceInBatch = firstSliceInBatch;
            Debug.debug(Debug.MODULE_EXEC, "Setting tasks to this.appTask");
            this.appTask = tasks;
            taskLock.notifyAll();
        }
    }

    public void terminate() {
        synchronized (taskLock) {
            isRunning = false;
            taskLock.notifyAll();
        }
    }

    public void workerLoop() {
        // LinkedList<GeneralGlueTuple> localAppTask = null;
        Debug.info(Debug.MODULE_EXEC, "TIMINGS[CRGT]: Thread %d StartWorkerLoop %d", getIndex(), System.nanoTime());
        while (true) {
            synchronized (taskLock) {
                while (isRunning && appTask == null) {
                    try {
                        taskLock.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        return;
                    }
                }
            }

            if (!isRunning) {
                return;
            }

            if (appTask != null) {
                localAppTask = appTask;
                appTask = null;
            }

            if (localAppTask != null) {
//                System.err.println("doAppWork");
                doAppWork();
                Debug.debug(Debug.MODULE_EXEC, "[lcosvse]: finishing doAppWork()");
//                if (localAppTask == null) {
//                    System.err.println("just rollback first slice of batch.");
//                }
                localAppTask = null;
            }
        }
    }

    private boolean isLastTask() {
        return localAppTask.isEmpty();
    }

    public void waitForMyTurn() {
        Debug.debug(Debug.MODULE_EXEC, "Before turnLock in waitForMyTurn %d", getIndex());
        synchronized (turnLock) {
            while (!glue.isMyTurn(threadId)) {
                try {
                    Debug.debug(Debug.MODULE_EXEC, "CRGlue thread %d waiting for my turn", getIndex());
                    turnLock.wait();
                    Debug.debug(Debug.MODULE_EXEC, "CRGlue thread %d woke up while waiting " +
                            "for my turn", getIndex());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        Debug.debug(Debug.MODULE_EXEC, "CRGlue thread %d done waiting for my turn", getIndex());
    }

    private String doAppWork() {
        Debug.debug(Debug.MODULE_EXEC, "[lcosvse]: entering doAppWork().");
        Debug.info(Debug.MODULE_EXEC, "TIMINGS[CRGT]: Thread %d StartDoAppWork %d", getIndex(), System.nanoTime());
        if (glue.isPipelinedBatchExecution()) {
            waitForMyTurn();
        }
        Debug.info(Debug.MODULE_EXEC, "TIMINGS[CRGT]: Thread %d FinishedWaitForMyTurnInDoAppWork %d", getIndex(), System.nanoTime());

        while (true) {
            if (localAppTask != null) {
                tuple = localAppTask.poll();
                Debug.info(Debug.MODULE_EXEC, "[alex]: localAppTask not null");
            }

            if (tuple == null) {
                if (!glue.isSequential()) {
                    glue.finishThread();
                }
                return null;
            }

            Debug.info(Debug.MODULE_EXEC, "TIMINGS[CRGT]: Thread %d StartExecRequest %d", getIndex(), System.nanoTime());
//            System.out.println("handler.execRequest");
            handler.execRequest(tuple.request, tuple.info);
//	    System.out.println("request executed");
            Debug.info(Debug.MODULE_EXEC, "TIMINGS[CRGT]: Thread %d EndExecRequest %d", getIndex(), System.nanoTime());

            // Debug.debug(Debug.MODULE_EXEC, "[lcosvse]: finished handler.execRequest().");
            boolean isLastTask = isLastTask();
            glue.finishTask(isLastTask);
            //  Debug.debug(Debug.MODULE_EXEC, "[lcosvse]: finished glue.execRequest().");

            if(localAppTask == null) {
                return null;
            }
//            else if (tuple == null) {
//                //	Debug.debug(Debug.MODULE_EXEC, "just rollback first slice of a request.");
//            }
        }
    }

    @Override
    public void run() {
        if (glue.isMiddle()) {
            Debug.info(Debug.MODULE_EXEC, "TIMINGS[CRGT]: Thread %d StartRun %d", getIndex(), System.nanoTime());

            Debug.debug(Debug.MODULE_EXEC, "Thread %d: initialize continuation", getIndex());
            previousCheckpoint = currentCheckpoint = Continuation.startSuspendedWith(wrapper);
            if (currentCheckpoint != null) {
                glue.passCheckpoint(getIndex(), currentCheckpoint);
            }
            Debug.debug(Debug.MODULE_EXEC, "Thread %d: has done passCheckpoint", getIndex());

            // take a snapshot of version 0, which is the base case before we
            // enter the infinite loop.
            glue.finishInitializeContinuation();
            Debug.debug(Debug.MODULE_EXEC, "Thread %d: has finished initialize continuation", getIndex());

            while (currentCheckpoint != null) {
                if (glue.isPipelinedBatchExecution()) {
                    waitForMyTurn();
                }
                synchronized (checkpointLock) {
                    if (needCheckpoint) {
                        this.glue.finishCheckpoint();
                    }

                    while (needCheckpoint) {
                        try {
                            // if a checkpoint is requested, the execution will be paused here
                            // until either a rollback or a continueExecution is requested.
                            checkpointLock.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }

                if (needRollback) {
                    assert (glue.isSequential());
                    Debug.debug(Debug.MODULE_EXEC, "Thread %d: notified to rollback.\n", getIndex());
                    // rollback continuation.
                    currentCheckpoint = previousCheckpoint;
                    needRollback = false;

                    // rollback batch and request in case of first slice.
                    assert (appTask == null);
                    assert (localAppTask != null);
                    assert (tuple != null);

                    //Old Code
//                    if (firstSliceInBatch) {
//                        assert (firstSliceOfRequest);
//                    }

//                    if (firstSliceOfRequest) {
//                        Debug.debug(Debug.MODULE_EXEC, "rollback of first slice. localAppTask.size()=%d",
//                                localAppTask.size());
//                        localAppTask.addFirst(tuple);
//                        tuple = null;
//                        Debug.debug(Debug.MODULE_EXEC,
//                                "finish rollback of first slice. localAppTask.size()=%d", localAppTask.size());
                    if (firstSliceInBatch) {
                        Debug.debug(Debug.MODULE_EXEC,
                                "rollback of first slice in a batch. appTask=%s localAppTask=%s", appTask,
                                localAppTask);
                        appTask = null;
                        localAppTask = null;//If localAppTask is null, this is a sign for us to finish the threads for
                                            // this batch, and disregard the batch completely.
                        glue.batchManager.finishControlLoop = true;
                        Debug.debug(Debug.MODULE_EXEC,
                                "finish rollback of first slice in a batch. appTask=%s localAppTask=%s", appTask,
                                localAppTask);
                    } else {
                        // TODO need to test this branch more.
                        // yes, this branch does nothing. I mean we want to test rollback
                        // first slice of a request but this request is not the first
                        // request for this thread in this batch.
                        localAppTask = copyLocalAppTask;
                        tuple = copyTuple;
                    }
//                    }

                    glue.finishRollback();
                } else {
                    firstSliceInBatch = false;
                    Debug.debug(Debug.MODULE_EXEC, "Thread %d: notified to resume.\n", getIndex());
                }

                if (glue.isSequential() && !rolledBackInThisBatch) {
                    Debug.debug(Debug.MODULE_EXEC, "Thread %d about to enter sync glue", getIndex());
                    synchronized (glue) {
                        Debug.debug(Debug.MODULE_EXEC, "Thread %d entered sync glue", getIndex());
                        Debug.debug(Debug.MODULE_EXEC, "Thread %d: waiting. To execute thread %d\n",
                                getIndex(), glue.getThreadIDToExec());
                        while (getIndex() != glue.getThreadIDToExec()) {
                            try {
                                glue.wait();
                                Debug.debug(Debug.MODULE_EXEC, "Thread %d: waiting. To execute thread %d.\n",
                                        getIndex(), glue.getThreadIDToExec());
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }

                        Debug.debug(Debug.MODULE_EXEC, "Thread %d: start sequential execution.\n", getIndex());
                        previousCheckpoint = currentCheckpoint;
                        currentCheckpoint = Continuation.continueWith(previousCheckpoint);
                        Debug.debug(Debug.MODULE_EXEC, "Thread %d: finished sequential execution.\n",
                                getIndex());
                        // TODO This is problematic if the batch size is larger than number
                        // of threads
                        glue.nextThreadIDToExec();
                        glue.notifyAll();
                        rolledBackInThisBatch = true;
                    }
                } else {
                    previousCheckpoint = currentCheckpoint;
                    Debug.debug(Debug.MODULE_EXEC, "Thread %d: start parallel execution %d\n", getIndex(),
                            System.currentTimeMillis());
                    copyLocalAppTask = copyLocalTasks(localAppTask);
                    copyTuple = copyLocalTuple(tuple);
                    currentCheckpoint = Continuation.continueWith(previousCheckpoint);


                    Debug.debug(Debug.MODULE_EXEC, "Thread %d: finished parallel execution %d\n", getIndex(),
                            System.currentTimeMillis());
                    if (currentCheckpoint != null) {
                        Debug.debug(Debug.MODULE_EXEC, "###### About to pass checkpoint.");
                        glue.passCheckpoint(getIndex(), currentCheckpoint);
                        Debug.debug(Debug.MODULE_EXEC, "###### Finished pass checkpoint.");
                    } else {
                        Debug.debug(Debug.MODULE_EXEC, "###### currentCheckpoint is null.");
                    }
                }
                Debug.debug(Debug.MODULE_EXEC, "###### finish one loop.");
            }
        } else {
            System.out.println("comes here probebly due to normal mode");
            wrapper.run();
        }

        Debug.debug(Debug.MODULE_EXEC, "###### exiting CRGlueThread.run()");
    }

    private GeneralGlueTuple copyLocalTuple(GeneralGlueTuple tuple) {
        if(tuple == null)
        {
            return null;
        }
        else
        {
            return new GeneralGlueTuple(tuple);
        }
    }

    private LinkedList<GeneralGlueTuple> copyLocalTasks(LinkedList<GeneralGlueTuple> localAppTask) {
        LinkedList<GeneralGlueTuple> newList =  new LinkedList<GeneralGlueTuple>();
        if(localAppTask == null) {
            return null;
        }
        for(GeneralGlueTuple t:localAppTask) {
            if(t == null)
            {
                newList.addLast(null);
            }
            else
            {
                newList.addLast(new GeneralGlueTuple(t));
            }
        }
        return newList;
    }

    /**
     * This method must be called in the thread in which GlueThread is running (in
     * which the continuation is started).
     */
    public void checkpoint() {
        synchronized (checkpointLock) {
            needCheckpoint = true;
        }
        Debug.debug(Debug.MODULE_EXEC, "[Thread %d]: About to suspend", getIndex());
        Debug.info(Debug.MODULE_EXEC, "TIMINGS[CRGT] Thread %d StartSuspend %d", getIndex(), System.nanoTime());
        Continuation.suspend();
        Debug.info(Debug.MODULE_EXEC, "TIMINGS[CRGT] Thread %d EndSuspend %d", getIndex(), System.nanoTime());
        Debug.debug(Debug.MODULE_EXEC, "[Thread %d]: Finished suspend", getIndex());
    }

    /**
     * This method can be invoked from any thread.
     */
    public void continueExecution() {
        Debug.debug(Debug.MODULE_EXEC, "[Thread %d]: continueExecution start", getIndex());
        synchronized (checkpointLock) {
            needCheckpoint = false;
            needRollback = false;
            checkpointLock.notify();
        }
        Debug.debug(Debug.MODULE_EXEC, "[Thread %d]: continueExecution end", getIndex());
    }

    public void wakeUpThread() {
        Debug.debug(Debug.MODULE_EXEC, "Thread %d: is being woken up..", getIndex());
        synchronized (turnLock) {
            turnLock.notify();
        }
        Debug.debug(Debug.MODULE_EXEC, "Thread %d: Finished waking thread up..", getIndex());
    }

    /**
     * This method can be invoked from any thread.
     */
    public void rollback() {
        Debug.debug(Debug.MODULE_EXEC, "Thread %d: in rollback()", this.threadId);
        this.rolledBackInThisBatch = false;
        synchronized (checkpointLock) {
            needCheckpoint = false;
            needRollback = true;
            checkpointLock.notify();
        }
        Debug.debug(Debug.MODULE_EXEC, "Thread %d: end rollback()", this.threadId);
    }

    @Override
    public String toString() {
        return "CRGlueThread" + this.threadId;
    }
}
