package BFT.generalcp.glue;


import BFT.Debug;
import BFT.generalcp.AppCPInterface;

import java.util.List;


/**
 * Created by IntelliJ IDEA.
 * User: iodine
 * Date: Apr 14, 2010
 * Time: 11:00:44 PM
 * To change this template use File | Settings | File Templates.
 */
public class GlueThread extends Thread {
    private AppCPInterface handler;
    private GeneralGlue glue;
    private boolean isRunning = true;
    private final Object lock = new Object();
    private Object appTask = null;

    private int threadId;


    public int getThreadId() {
        return this.threadId;
    }


    public void terminate() {
        synchronized (lock) {
            isRunning = false;
            lock.notifyAll();
        }
    }

    public void startAppWork(Object task) {
        synchronized (lock) {
            appTask = task;
            lock.notifyAll();
        }
    }


    @Override
    public void run() {
        while (true) {
            try {
                Object localAppTask = null;
                synchronized (lock) {
                    while (isRunning && appTask == null) {
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            return;
                        }
                    }
                    if (appTask != null) {
                        localAppTask = appTask;
                        appTask = null;
                    }
                }
                long startTime = System.currentTimeMillis();
                if (!isRunning) {
                    return;
                }
                if (localAppTask != null) {
                    this.doAppWork(localAppTask);
                    Debug.debug(Debug.MODULE_MERKLE, "%s app time =%d\n",
                            this, (System.currentTimeMillis() - startTime));
                    localAppTask = null;
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public String toString() {
        return "GlueThread" + this.threadId;
    }

    public GlueThread(int threadId, AppCPInterface handler, GeneralGlue glue) {
        this.threadId = threadId;
        this.handler = handler;
        this.glue = glue;
    }

    protected void doAppWork(Object task) {
        //System.out.println(this+" glue do "+task);
        List<GeneralGlueTuple> tasks = (List<GeneralGlueTuple>) task;
        for (GeneralGlueTuple tuple : tasks) {
            handler.execAsync(tuple.request, tuple.info);
        }
        glue.finishTask();
    }


}
