/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package BFT.network;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author manos
 */
public class ParallelPassThroughNetworkQueue implements NetworkQueue {

    ByteHandler bn;
    ThreadPoolExecutor pool;

    public ParallelPassThroughNetworkQueue(ByteHandler _bn) {
        this(_bn, 1);
    }

    public ParallelPassThroughNetworkQueue(ByteHandler _bn, int poolSize) {
        this(_bn, new ThreadPoolExecutor(poolSize / 2 + 1, //base size
                poolSize, // max size
                10000, // keep alive
                java.util.concurrent.TimeUnit.MILLISECONDS, //in ms
                new ArrayBlockingQueue<Runnable>(5), // queue placeholder
                new ThreadPoolExecutor.CallerRunsPolicy())); // caller runs if needed


    }

    public ParallelPassThroughNetworkQueue(ByteHandler _bn, ThreadPoolExecutor p) {
        this.bn = _bn;
        pool = p;
    }

    public void addWork(byte[] m) {
        pool.execute(new Task(m));
    }


    class Task implements Runnable {
        byte[] ms;

        Task(byte[] m) {
            ms = m;
        }

        public void run() {
            bn.handle(ms);
        }

    }
}

