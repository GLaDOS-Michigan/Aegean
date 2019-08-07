package merkle;

import java.util.Random;
import java.security.MessageDigest;

public class PerformanceTest {

    static int datasize;
    private static int datanum;
    private static int locality;
    private static int round;
    private static int appWork;
    private static int noOfThreads;
    private static int batchSize;
    private static int noOfChildren;
    private static byte[][] data;
    private static boolean doParallel = false;
    private static int workFinished = 0;
    private static final Object workFinishedSig = new Object();
    private static PerformanceTestThread[] threads;


    private static void init() throws Exception {
	BFT.Parameters.useDummyTree = true;
	//BFT.Parameters.loadBalanceDepth=2;
        MerkleTreeInstance.init(noOfChildren, datanum, noOfThreads, doParallel);
        workFinished = 0;
        threads = new PerformanceTestThread[noOfThreads];
        for (int i = 0; i < noOfThreads; i++) {
            threads[i] = new PerformanceTestThread();
        }
	new OtherThread().start();
	new OtherThread().start();
	new OtherThread().start();

        for (int i = 0; i < noOfThreads; i++) {
            threads[i].start();
        }
        Random rand = new Random(System.currentTimeMillis());
        data = new byte[datanum][];
        MerkleTreeInstance.add(data); 
        //tree.add(data);
        for (int i = 0; i < datanum; i++) {
            data[i] = new byte[datasize];
            rand.nextBytes(data[i]);
            MerkleTreeInstance.add(data[i]);
        }
        MerkleTreeInstance.getShimInstance().getHash();
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
        MerkleTreeInstance.clear();
        data = null;
        //System.gc();
    }

    private static void doOperation(byte[] obj) {
	    long start = System.currentTimeMillis();
	int ret=0;
	try{
	    //while(System.currentTimeMillis() - start < appWork ){}
        //System.out.println(tmp.length+" "+obj.length);
        //System.arraycopy(tmp, 0, obj, 0, datasize);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (int i = 0; i < appWork; i++) {
	    
            //digest.reset();
            digest.digest(obj);
            //for(int j=0;j<10000;j++) obj[0]++;
        }
	}
	catch(Exception e){e.printStackTrace();}
	//System.out.println("time="+(System.currentTimeMillis()-start));
    }

    public static void testSingleBase() throws Exception {
        Random rand = new Random(System.currentTimeMillis());
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < round; i++) {
            int index = rand.nextInt(locality);
            doOperation(data[index]);
        }
        long endTime = System.currentTimeMillis();
        System.out.println("testSingleBase");
        printConfiguration();
        System.out.println("time=" + (endTime - startTime) + " throughput=" + ((double) round / (endTime - startTime)));
    }

    public static void testSingleMerkle() throws Exception {
        Random rand = new Random(System.currentTimeMillis());
        long versionNo = 0;
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < round; i++) {
            int index = rand.nextInt(locality);
            MerkleTreeInstance.update(data[index]);
            doOperation(data[index]);
            if (i % batchSize == 0) {
                MerkleTreeInstance.getShimInstance().getHash();
                versionNo++;
                MerkleTreeInstance.getShimInstance().setVersionNo(versionNo);
                //System.out.println(i);
            }
        }
        long endTime = System.currentTimeMillis();
        System.out.println("testSingleMerkle");
        printConfiguration();
        System.out.println("time=" + (endTime - startTime) + " throughput=" + ((double) round / (endTime - startTime)));
    }

    public static void testMultipleBase() throws Exception {

        /*WorkerThread[] threads = new WorkerThread[noOfThreads];
        for (int i = 0; i < noOfThreads - 1; i++) {
        threads[i] = new WorkerThread(false, batchSize / noOfThreads);
        }
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < noOfThreads - 1; i++) {
        threads[i].start();
        }
        for (int i = 0; i < round / batchSize; i++) {
        synchronized (workFinishedSig) {
        workFinished = 0;
        }
        for (int j = 0; j < noOfThreads - 1; j++) {
        threads[j].doWork();
        }
        Random rand = new Random(System.currentTimeMillis());
        for (int j = 0; j < batchSize / noOfThreads; j++) {
        int index = rand.nextInt(locality);
        //}
        try {
        doOperation(data[index]);
        } catch (Exception e) {
        e.printStackTrace();
        }

        }
        synchronized (workFinishedSig) {
        while (workFinished < batchSize - batchSize / noOfThreads) {
        workFinishedSig.wait();
        }
        }
        }
        for (int j = 0; j < noOfThreads - 1; j++) {
        threads[j].finish();
        }
        long endTime = System.currentTimeMillis();
        System.out.println("testMultipleBase");
        printConfiguration();
        System.out.println("time=" + (endTime - startTime) + " throughput=" + ((double) round / (endTime - startTime)));*/
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
            MerkleTreeInstance.getShimInstance().setVersionNo(seqNo);
            startBatch();
            for (int i = 0; i < noOfThreads; i++) {
                threads[i].startAppWork(123);
            }
	    /*synchronized(MerkleTreeThread.lock){
                MerkleTreeThread.lock.notifyAll();
            }*/
	    //System.out.println("notify "+System.currentTimeMillis());

            waitForBatch();
            MerkleTreeInstance.getShimInstance().getHash();
            MerkleTreeInstance.getShimInstance().makeStable(seqNo - 1);
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
		//synchronized(data[index]){
		    doOperation(data[index]);
		//}	    
                MerkleTreeInstance.update(data[index]);
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
