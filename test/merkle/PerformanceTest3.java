package merkle;

import java.util.Random;
import java.security.MessageDigest;
import java.util.*;

public class PerformanceTest3 {

    static int datasize;
    private static int datanum;
    private static int locality;
    private static int round;
    private static int appWork;
    private static int noOfThreads;
    private static int batchSize;
    private static int noOfChildren;
    private static TreeMap<Integer, String>[] data;
    private static boolean doParallel = false;
    private static int workFinished = 0;
    private static final Object workFinishedSig = new Object();
    private static PerformanceTestThread[] threads;


    private static void init() throws Exception {
        workFinished = 0;
        threads = new PerformanceTestThread[noOfThreads];
        for (int i = 0; i < noOfThreads; i++) {
            threads[i] = new PerformanceTestThread();
        }

        for (int i = 0; i < noOfThreads; i++) {
            threads[i].start();
        }
        data = new TreeMap[datanum];
        //tree.add(data);
        for (int i = 0; i < datanum; i++) {
            data[i] = new TreeMap<Integer, String>();
	    for(int j=0;j<datasize;j++)
		data[i].put(j,"hahahaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        }
    }

    private static void startBatch() {
        synchronized (workFinishedSig) {
            workFinished = 0;
        }
    }

    private static void finishBatch() {
        synchronized (workFinishedSig) {
            workFinished++;
            if (workFinished == noOfThreads) {
                workFinishedSig.notify();
            }
        }
    }

    private static void waitForBatch() {
        synchronized (workFinishedSig) {
            while (workFinished != noOfThreads) {
                try {
                    workFinishedSig.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static void clear() throws Exception {
        data = null;
        //System.gc();
    }

    private static int doOperation(TreeMap<Integer, String> obj) {
	int ret = 0;
	try{
	for(Integer i:obj.keySet()){
	    ret+=obj.get(i).length();
	}
	return ret;
	}
	catch(Exception e){
	    return -1;
	}
    }

    public static void testMultipleMerkle() throws Exception {

        
        boolean startup = true;
        long startTime = System.currentTimeMillis();
        long seqNo = 0;
        int batchCount = 0;
        while (true) {
	    long tmp = System.currentTimeMillis();
            seqNo++;
            //System.out.println("Before "+seqNo);
            startBatch();
            for (int i = 0; i < noOfThreads; i++) {
                threads[i].startAppWork(123);
            }
	    /*synchronized(MerkleTreeThread.lock){
                MerkleTreeThread.lock.notifyAll();
            }*/
	    //System.out.println("notify "+System.currentTimeMillis());

            waitForBatch();
	    //System.out.println("time="+(System.currentTimeMillis()-tmp));
            long time = System.currentTimeMillis();
            //System.out.println("After "+seqNo);
            if (startup && time - startTime >= 1000 * 20) {
                startTime = time;
                startup = false;
            }
            if (!startup) {
                batchCount++;
                if (time - startTime >= 1000 * 60) {
                    break;
                }
            }

        }
        printConfiguration();
        System.out.println((batchCount * batchSize)/60);
    }

    public static class OtherThread extends Thread {
	public int i;
	public void run(){
	    while(true){
		i++;
	    }
	}
    }

    public static class PerformanceTestThread extends MerkleTreeThread {

        private Random rand = new Random(getIndex());
        private int workCount;

        public PerformanceTestThread() {
            super((MerkleTree) MerkleTreeInstance.getAppInstance());
            this.workCount = batchSize / noOfThreads + (getIndex() < batchSize % noOfThreads ? 1 : 0);
            //System.out.println("workCount " + getThreadId() + " " + workCount);
        }

        @Override
        protected String doAppWork(Object task) {
	    long tmp = System.currentTimeMillis();
            for (int i = 0; i < workCount; i++) {
                int index = rand.nextInt(datanum);
		synchronized(data[index]){
		    doOperation(data[index]);
		}	    
            }
            finishBatch();
	    return null;
	    //System.out.println("haha "+(System.currentTimeMillis()-tmp)+" "+tmp);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 6) {
            System.out.println("Usage: PerformanceTest "
                    + "datasize datanum  noOfThreads noOfChildren batchsize appWork");
            return;
        }

        datasize = Integer.parseInt(args[0]);
        datanum = Integer.parseInt(args[1]);
        noOfThreads = Integer.parseInt(args[2]);
        noOfChildren = Integer.parseInt(args[3]);
        batchSize = Integer.parseInt(args[4]);
	appWork = Integer.parseInt(args[5]);
        BFT.Parameters.noOfThreads = noOfThreads;
	if(datanum/noOfChildren/noOfChildren<noOfChildren)
	    BFT.Parameters.loadBalanceDepth =2;
	if(datanum/noOfChildren/noOfChildren<1)
	    BFT.Parameters.loadBalanceDepth =1;
	try{
           init();
           testMultipleMerkle();
           clear();
	}
	catch(Exception e){
	    e.printStackTrace();
	}
	finally{
            System.exit(0);
	}

    }

    private static void printConfiguration() {
        System.out.print(datasize + " " + datanum
                + " " + noOfThreads + " " + noOfChildren + " " + batchSize+"=");
    }
}
