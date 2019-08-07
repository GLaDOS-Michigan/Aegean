/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package merkle;

import BFT.Debug;
import BFT.exec.glue.GeneralGlue;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedList;

/**
 * @author yangwang
 */
public class MerkleTreeThread extends IndexedThread {

    transient private static int threadCount = 0;
    transient private static ArrayList<MerkleTreeThread> allThreads = new ArrayList<MerkleTreeThread>();

    transient private int threadId;
    transient private MerkleTree tree;
    transient private MessageDigest digest = null;

    transient private boolean isRunning = true;
    transient private final Object lock = new Object();
    transient private LinkedList<Integer> hashTask = null;
    transient protected GeneralGlue glue = null;

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

    public MerkleTreeThread(MerkleTree tree) {
        //this.setPriority(Thread.MAX_PRIORITY);
        this.threadId = getAndIncreaseThreadCount();
        allThreads.add(this);
        this.tree = tree;
        try {
            digest = MessageDigest.getInstance(tree.getParameters().digestType);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int getIndex() {
        return this.threadId;
    }

    public void terminate() {
        synchronized (lock) {
            isRunning = false;
            lock.notifyAll();
        }
    }

    protected String doAppWork(Object task) {
        throw new RuntimeException("Not implemented yet");
    }

    public void startHashWork(LinkedList<Integer> task) {
        synchronized (lock) {
            hashTask = task;
            lock.notify();
            //lock.notifyAll();
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                LinkedList<Integer> localHashTask = null;
                synchronized (lock) {
                    while (isRunning && hashTask == null) {
                        try {
                            lock.wait();
                            //System.out.println(this+" gets signal "+System.currentTimeMillis());
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            return;
                        }
                    }
                    if (hashTask != null) {
                        localHashTask = hashTask;
                        hashTask = null;
                    }
                }
                //System.out.println(this+" gets signal "+System.currentTimeMillis());
                long startTime = System.currentTimeMillis();
                if (!isRunning) {
                    return;
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
                        tree.getHash(index, digest);
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
