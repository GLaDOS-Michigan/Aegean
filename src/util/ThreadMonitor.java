package util;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ThreadMonitor {

    // current turn
    int currentId;
    // Threads count
    int count;
    // request id assigned to each thread
    long[] threadRequest;
    // threads id call skip
    int[] skipArray;
    // next request id to run
    long nextReqId;
    // threads available
    int threads;
    // how many reqs wait to run
    int reqNum;

    public ThreadMonitor(int num) {
        count = num;
        skipArray = new int[count];
        threadRequest = new long[count];
        currentId = 0;
        nextReqId = 0;
        threads = count;
        reqNum = 0;
        for (int i = 0; i < count; i++) {
            threadRequest[i] = -1;
            skipArray[i] = 1;
        }
    }


    public synchronized void addRequest(int num) {
        reqNum += num;
        for (int i = 0; i < count && reqNum > 0 && threads > 0; i++) {
            if (threadRequest[i] == -1) {
                System.out.println("   assign " + nextReqId + " to thread " + threads);
                threadRequest[i] = nextReqId++;
                skipArray[i] = 0;
                threads--;
                reqNum--;
            }
        }
    }


    /**
     * returns true if id can execute, false otherwise
     **/
    public synchronized boolean isMyTurn(long id) {
        return threadRequest[currentId] == id;
    }

    public synchronized void waitMyTurn(long id) {
        while (threadRequest[currentId] != id) {
            try {
                wait();
            } catch (Exception e) {
                throw new RuntimeException("Interuppted?");
            }
        }
        System.out.println("wait my turn " + id);
    }

    /**
     * release the current turn if it belongs to id
     **/
    public synchronized void releaseTurn(long id) {
        int tid = 0;
        for (int i = 0; i < count; i++) {
            if (threadRequest[i] == id) {
                tid = i;
                break;
            }
        }
        System.out.println("    release turn:" + id + " " + tid);
        int start = tid;
        tid = tid + 1;
        while (skipArray[tid % count] == 1 && threadRequest[tid % count] == -1) {
            tid = (tid + 1) % count;

            // all threads have released turn 
            if (start == tid) {
                break;
            }
        }
        if (threadRequest[tid % count] == -1)
            tid = 0;
        currentId = tid % count;
        System.out.println("    next to run:" + threadRequest[tid % count] + " " + tid);
        notifyAll();
    }

    /**
     * skip future turns from id
     **/
    public synchronized void skipTurns(long id) {
        int tid = 0;
        System.out.println("    skipTurn:" + id);
        for (int i = 0; i < count; i++) {
            if (threadRequest[i] == id) {
                tid = i;
            }
        }
        if (reqNum > 0) {
            threadRequest[tid] = nextReqId++;
            skipArray[tid] = 0;
            reqNum--;
            System.out.println("    next req:" + threadRequest[tid] + " at thread " + tid);
        } else {
            skipArray[tid] = 1;
            threadRequest[tid] = -1;
            System.out.println("no more req");
            threads++;
        }
        releaseTurn(id);
    }

    /**
     * reset to the base state where no id is skipped and current turn is the default
     **/
    public void reset() {
        for (int i = 0; i < skipArray.length; i++) {
            skipArray[i] = 0;
        }
        currentId = 0;
    }

    public class Test implements Runnable {
        ThreadMonitor m;
        long rid;

        public Test(ThreadMonitor tm, long id) {
            m = tm;
            rid = id;
        }

        public void run() {
            for (int i = 0; i < rid % 3 + 1; i++) {
                synchronized (m) {
                    while (!m.isMyTurn(rid)) {
                        try {
                            m.wait(50);
                        } catch (Exception e) {
                            throw new RuntimeException("interuppt?");
                        }
                    }
                    System.out.println(rid + " running " + i);
                    try {
                        Thread.sleep(5);
                    } catch (Exception e) {
                        throw new RuntimeException("interuppt?");
                    }
                }
                synchronized (m) {
                    m.releaseTurn(rid);
                    m.notifyAll();
                }
            }
            synchronized (m) {
                m.skipTurns(rid);
                m.notifyAll();
            }
        }
    }

    public static void main(String[] args) {
        ArrayBlockingQueue<Runnable> pipeQueue = new ArrayBlockingQueue<Runnable>(500);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS, pipeQueue);
        ThreadMonitor threadMonitor = new ThreadMonitor(1);
        threadMonitor.addRequest(10);
        for (long i = 0; i < 10; i++) {
            executor.execute(threadMonitor.new Test(threadMonitor, i));
        }
        synchronized (threadMonitor) {
            threadMonitor.addRequest(10);
        }
        for (long i = 10; i < 20; i++) {
            executor.execute(threadMonitor.new Test(threadMonitor, i));
        }
    }
}
