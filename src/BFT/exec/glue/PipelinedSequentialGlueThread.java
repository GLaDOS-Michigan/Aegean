package BFT.exec.glue;

import BFT.Debug;
import BFT.exec.RequestHandler;
import merkle.IndexedThread;

import java.util.ArrayList;
import java.util.LinkedList;

public class PipelinedSequentialGlueThread extends IndexedThread {
    transient private static int threadCount = 0;
    transient private static ArrayList<PipelinedSequentialGlueThread> allThreads = new ArrayList<PipelinedSequentialGlueThread>();

    transient private int threadId;

    transient private Object taskLock = new Object();
    transient private boolean isRunning = true;

    transient private RequestHandler handler;
    transient private GeneralGlue glue;

    transient private LinkedList<GeneralGlueTuple> appTask = null;
    transient private LinkedList<GeneralGlueTuple> localAppTask = null;
    transient private GeneralGlueTuple tuple = null;

    public PipelinedSequentialGlueThread(RequestHandler handler, GeneralGlue glue) {
        this.threadId = getAndIncreaseThreadCount();
        allThreads.add(this);

        this.handler = handler;
        this.glue = glue;

        //this.wrapper = new CRWrapper(this);
    }

    public synchronized static int getThreadCount() {
        return threadCount;
    }

    private synchronized static int getAndIncreaseThreadCount() {
        int ret = threadCount;
        threadCount++;
        return ret;
    }

    public static ArrayList<PipelinedSequentialGlueThread> getAllThreads() {
        return allThreads;
    }

    @Override
    public int getIndex() {
        return threadId;
    }

    public void startAppWork(LinkedList<GeneralGlueTuple> tasks) {
        synchronized (taskLock) {
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

    @Override
    public void run() {
        Debug.debug(Debug.MODULE_EXEC, "Thread %d: starting run\n", threadId);
        // LinkedList<GeneralGlueTuple> localAppTask = null;
        while (true) {
            Debug.debug(Debug.MODULE_EXEC, "Thread %d: before tasklock sync\n", threadId);
            synchronized (taskLock) {
                while (isRunning && appTask == null) {
                    try {
                        Debug.debug(Debug.MODULE_EXEC, "Thread %d: before tasklock.wait()\n", threadId);
                        taskLock.wait();
                        Debug.debug(Debug.MODULE_EXEC, "Thread %d: after tasklock.wait()\n", threadId);
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
                Debug.debug(Debug.MODULE_EXEC, "Thread %d: assigning localAppTask\n", threadId);
                localAppTask = appTask;
                appTask = null;
            }

            if (localAppTask != null) {
                Debug.debug(Debug.MODULE_EXEC, "Thread %d: calling doAppWork\n", threadId);
                doAppWork();
                Debug.debug(Debug.MODULE_EXEC, "Thread %d: after doAppWork\n", threadId);
                if (localAppTask == null) {
                    Debug.debug(Debug.MODULE_EXEC, "localAppTask is null. Huh?");
                }
                localAppTask = null;
            }
        }
    }

    private String doAppWork() {
        Debug.debug(Debug.MODULE_EXEC, "Thread %d: starting doAppWork\n", threadId);
        Debug.debug(Debug.MODULE_EXEC, "Thread %d: will wait for my turn\n", threadId);
        glue.waitForMyTurn();
        //firstSliceInBatch = true;
        while (true) {
            if (localAppTask != null) {
                tuple = localAppTask.poll();
            } else {
                Debug.debug(Debug.MODULE_EXEC, "localAppTask is null. Huh?");
            }

            if (tuple == null) {
                Debug.debug(Debug.MODULE_EXEC, "Thread %d: tuple = null, finishing\n", threadId);
                glue.finishPipelinedThread();
                return null;
            }
            //firstSliceOfRequest = true;

            Debug.debug(Debug.MODULE_EXEC, "Thread %d: will execRequest\n", threadId);
            handler.execRequest(tuple.request, tuple.info);
            Debug.debug(Debug.MODULE_EXEC, "Thread %d: execRequest finished\n", threadId);
            //glue.finishTask();
            if (tuple == null) {
                Debug.debug(Debug.MODULE_EXEC, "tuple is null. Huh?");
            }
        }
    }

    @Override
    public String toString() {
        return "PipelinedGlueThread" + this.threadId;
    }
}
