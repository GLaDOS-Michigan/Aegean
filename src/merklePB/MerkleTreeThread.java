/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package merklePB;

import BFT.Debug;

import java.util.ArrayList;
import java.util.LinkedList;

/**
 * @author yangwang
 */
public class MerkleTreeThread extends IndexedThread {

    private static int threadCount = 0;
    private static ArrayList<MerkleTreeThread> allThreads = new ArrayList<MerkleTreeThread>();

    public synchronized static int getThreadCount() {
        return threadCount;
    }

    private synchronized static int getAndIncreaseThreadCount() {
        int ret = threadCount;
        threadCount++;
        return ret;
    }

    public static ArrayList<MerkleTreeThread> getAllThreads() {
        return allThreads;
    }

    private int threadId;
    private MerkleTree tree;

    public MerkleTreeThread(MerkleTree tree) {
        //this.setPriority(Thread.MAX_PRIORITY);
        this.threadId = getAndIncreaseThreadCount();
        allThreads.add(this);
        this.tree = tree;
    }

    public int getIndex() {
        return this.threadId;
    }

    private boolean isRunning = true;
    private final Object lock = new Object();
    private Object appTask = null;
    private LinkedList<Integer> hashTask = null;

    public void terminate() {
        synchronized (lock) {
            isRunning = false;
            lock.notifyAll();
        }
    }

    protected String doAppWork(Object task) {
        throw new RuntimeException("Not implemented yet");
    }

    public void startAppWork(Object task) {
        synchronized (lock) {
            appTask = task;
            //System.out.println(this+" notifies at "+System.currentTimeMillis());
            lock.notify();
        }
    }

    public void startHashWork(LinkedList<Integer> task) {
        synchronized (lock) {
            hashTask = task;
            lock.notify();
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                Object localAppTask = null;
                LinkedList<Integer> localHashTask = null;
                long signalTime = 0;
                synchronized (lock) {
                    while (isRunning && appTask == null && hashTask == null) {
                        try {
                            lock.wait();
                            signalTime = System.currentTimeMillis();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            return;
                        }
                    }
                    if (appTask != null) {
                        localAppTask = appTask;
                        appTask = null;
                    }
                    if (hashTask != null) {
                        localHashTask = hashTask;
                        hashTask = null;
                    }

                }
                if (!isRunning) {
                    return;
                }
                long startTime = System.currentTimeMillis();
                if (localAppTask != null) {
                    String msg = this.doAppWork(localAppTask);
                    Debug.debug(Debug.MODULE_MERKLE, "%s app time =%d %d %d %s\n",
                            this, signalTime, (System.currentTimeMillis() - startTime), System.currentTimeMillis(), msg);
                    localAppTask = null;
                }
                if (localHashTask != null) {
                    while (true) {
                        Integer index = null;
                        synchronized (localHashTask) {
                            if (localHashTask.size() > 0)
                                index = localHashTask.removeFirst();
                        }
                        if (index == null)
                            break;
                        //System.out.println("get "+index);
                        tree.getHash(index, null);
                        //Thread.yield();
                        tree.finishGetHash();
                    }
                    localHashTask = null;
                    Debug.debug(Debug.MODULE_MERKLE, "%s hash time =%d\n",
                            this, (System.currentTimeMillis() - startTime));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public String toString() {
        return "MerkleTreeThread" + this.threadId;
    }
}
