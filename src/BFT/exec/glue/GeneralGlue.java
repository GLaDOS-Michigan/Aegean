package BFT.exec.glue;

import BFT.Debug;
import BFT.Parameters;
import BFT.exec.*;
import BFT.exec.messages.ExecuteBatchMessage;
import BFT.exec.messages.RequestBatch;
import BFT.messages.Entry;
import BFT.messages.FilteredRequestCore;
import BFT.messages.NonDeterminism;
import merkle.IndexedThread;
import merkle.MerkleTreeInstance;
import merkle.MerkleTreeThread;
import org.apache.commons.javaflow.Continuation;

import java.util.*;

/**
 * Created by IntelliJ IDEA. User: iodine Date: Apr 14, 2010 Time: 10:55:46 PM
 * To change this template use File | Settings | File Templates.
 */
public class GeneralGlue {

    transient private Random rand = new Random(12345); // for false negative
    transient private RequestHandler handler;
    transient private RequestFilter filter;
    transient private CRGlueThread[] threads = null;
    transient private PipelinedSequentialGlueThread[] pipe_threads = null;
    LinkedList<GeneralGlueTuple> allRequests = new LinkedList<GeneralGlueTuple>();//This is not transient anymore to make the batch a part of the merkle tree
                                                                    //We need it to agree on the order of a batch. Otherwise, we need to rollback

    transient private static Parameters parameters;
    public transient BatchManager batchManager = null;
    transient private PipelinedSequentialManager pipelinedSequentialManager = null;
    transient private int requestCounter;

    transient private boolean isPipelinedBatch = false;
    transient private ParallelPipelineManager parallelPipelineManager = null;

    transient private boolean isMiddle;
    transient private int threadIDToExecute = 0;

    transient private HashMap<Object, Integer> readThreadMap = new HashMap<Object, Integer>();
    transient private HashMap<Object, Integer> writeThreadMap = new HashMap<Object, Integer>();

    transient private Object loopLock = new Object();

    public GeneralGlue(RequestHandler handler, RequestFilter filter, int noOfThreads,
                       Parameters param, boolean isMiddleServer, int myIndex, ExecBaseNode ebn) {
        this.parameters = param;
        this.handler = handler;
        this.filter = filter;
        this.isMiddle = isMiddleServer;
        Debug.debug(Debug.MODULE_EXEC, "xxxxx Point 1.");
        threads = new CRGlueThread[noOfThreads];
        if (parameters.pipelinedSequentialExecution) {
            //I think parallelExecution parameter is not necessart but I tried to make it correct at least in these three different case-Remzi
            parameters.parallelExecution = false;
            pipe_threads = new PipelinedSequentialGlueThread[noOfThreads];
            Debug.debug(Debug.MODULE_EXEC, "noOfThreads: %d", noOfThreads);
            for (int i = 0; i < noOfThreads; i++) {
                pipe_threads[i] = new PipelinedSequentialGlueThread(handler, this);
            }
            pipelinedSequentialManager = new PipelinedSequentialManager(noOfThreads);
            for (int i = 0; i < noOfThreads; i++) {
                pipe_threads[i].start();
            }
        } else if (parameters.pipelinedBatchExecution) {
            parameters.parallelExecution = true;
            isPipelinedBatch = true;
            int numPipelinedBatches = parameters.numPipelinedBatches;
            int numWorkersPerBatch = parameters.numWorkersPerBatch;
            CRGlueThread[][] allWorkerThreads = new CRGlueThread[numPipelinedBatches][numWorkersPerBatch];
            for (int i = 0; i < numPipelinedBatches; i++) {
                for (int j = 0; j < numWorkersPerBatch; j++) {
                    allWorkerThreads[i][j] = new CRGlueThread(handler, this);
                }
            }
            parallelPipelineManager = new ParallelPipelineManager(
                    numWorkersPerBatch, numPipelinedBatches,
                    allWorkerThreads, param, loopLock, myIndex, ebn);
            for (int i = 0; i < numPipelinedBatches; i++) {
                for (int j = 0; j < numWorkersPerBatch; j++) {
                    allWorkerThreads[i][j].start();
                }
            }
            threads = new CRGlueThread[numPipelinedBatches * numWorkersPerBatch];
            for (int i = 0; i < numPipelinedBatches * numWorkersPerBatch; i++) {
                int batch = i / numWorkersPerBatch;
                threads[i] =
                        allWorkerThreads[i / numWorkersPerBatch][(i - numWorkersPerBatch * batch) % numWorkersPerBatch];
            }
            parallelPipelineManager.waitForAllContinuationsToInitialize();
        } else {
            parameters.parallelExecution = true;
            threads = new CRGlueThread[noOfThreads];
            for (int i = 0; i < noOfThreads; i++) {
                threads[i] = new CRGlueThread(handler, this);
            }
            batchManager = new BatchManager(noOfThreads, isMiddleServer, threads);

            for (int i = 0; i < noOfThreads; i++) {
                threads[i].start();
            }

            batchManager.waitForContinuationToInitialize();
        }
    }

    public GeneralGlue(RequestHandler handler, RequestFilter filter, int noOfThreads, Parameters param, ExecBaseNode ebn) {
        this(handler, filter, noOfThreads, param, false, 0, ebn);
    }

    public void hitWall() {
        int id = 0;
        if ((Thread.currentThread()) instanceof IndexedThread) {
            id = ((IndexedThread) Thread.currentThread()).getIndex();
        }
        Debug.info(Debug.MODULE_EXEC, "TIMINGS[EBN] Thread %d StartHitWall %d",
                id, System.nanoTime());
        if (isPipelinedBatch) {
            parallelPipelineManager.hitWallAndCheckpoint();
        } else {
            batchManager.hitWallAndCheckpoint();
        }
        Debug.info(Debug.MODULE_EXEC, "TIMINGS[EBN] Thread %d EndHitWall %d",
                id, System.nanoTime());
    }

    public void finishCheckpoint() {
        if (isPipelinedBatch) {
            parallelPipelineManager.finishCheckpoint();
        } else {
            batchManager.finishCheckpoint();
        }
    }

    public void finishTask(boolean isLast) {
        //RLB: Rollback threads in sequential mode get here
        Debug.debug(Debug.MODULE_EXEC, "Where RLB somthing comment is");
        if (isPipelinedBatch) {
            parallelPipelineManager.finishTask(isLast);
        } else {
            batchManager.finishTask(isLast);
        }
    }

    public void finishThread() {
        if (isPipelinedBatch) {
            parallelPipelineManager.finishThread();
        } else {
            batchManager.finishThread();
        }
    }

    public void finishPipelinedThread() {
        if (isPipelinedBatch) {
            parallelPipelineManager.finishPipelinedThread();
        } else {
            pipelinedSequentialManager.finishThread();
        }
    }

    public boolean isMyTurn(int threadId) {
        //Can a thread that was rolled back, wake up and check it's turn.
        assert isPipelinedBatch;
        return parallelPipelineManager.isMyTurn(threadId);
    }

    public void waitForMyTurn() {
        assert parameters.pipelinedSequentialExecution;
        pipelinedSequentialManager.waitForMyTurn();
    }

    public void yieldPipeline() {
        if (isPipelinedBatch) {
            parallelPipelineManager.yieldPipeline();
        } else {
            pipelinedSequentialManager.yieldPipeline();
        }
    }

    public void stop() {
        if (parameters.pipelinedSequentialExecution) {
            for (PipelinedSequentialGlueThread pipe_thread : pipe_threads) {
                pipe_thread.terminate();
            }
        } else {
            for (CRGlueThread thread : threads) {
                thread.terminate();
                if (parallelPipelineManager != null) {
                    parallelPipelineManager.killAllLoopThreads();
                }
            }
        }
    }

    public void executeBatch(ExecuteBatchMessage request) {
        Debug.debug(Debug.MODULE_EXEC, "Start executeBatch\n");
        generateInfo(request);
        requestCounter = 0;
        if (!parameters.parallelExecution) {
            Debug.debug(Debug.MODULE_EXEC, "Using pipelined execution\n");
            Iterator<GeneralGlueTuple> iter = allRequests.iterator();
            LinkedList<LinkedList<GeneralGlueTuple>> batch = new LinkedList<LinkedList<GeneralGlueTuple>>();
            for (int i = 0; i < parameters.noOfThreads; i++) {
                LinkedList<GeneralGlueTuple> batchSlice = new LinkedList<GeneralGlueTuple>();
                batch.add(i, batchSlice);
            }
            while (iter.hasNext()) {
                GeneralGlueTuple tuple = iter.next();
                List<RequestKey> keys = null;
                Debug.debug(Debug.MODULE_EXEC, "Adding request %d to thread %d\n",
                        requestCounter, requestCounter % parameters.noOfThreads);
                batch.get(requestCounter % parameters.noOfThreads).add(tuple);
                requestCounter++;
                iter.remove();
            }

            int numThreadsInActiveBatch = Math.min(requestCounter, pipe_threads.length);
            pipelinedSequentialManager.setNumThreadsInActiveBatch(numThreadsInActiveBatch);
            pipelinedSequentialManager.startBatch();
            for (int i = 0; i < numThreadsInActiveBatch; i++) {
                Debug.debug(Debug.MODULE_EXEC, "Starting pipe_thread[%d]\n", i);
                pipe_threads[i].startAppWork(batch.get(i));
            }
            Debug.debug(Debug.MODULE_EXEC, "Waiting for threads to deactivate\n");
            pipelinedSequentialManager.waitForAllThreadsToDeactivate();
            Debug.debug(Debug.MODULE_EXEC, "All threads deactivated\n");
        } else {
            if (!parameters.pipelinedBatchExecution) {
//                System.out.println("Executing in p mode");
                /*
                Previously, firstSliceInBatch was becoming true when batch manager starts a new slice. However, this is
                not always correct. If runMixer cannot finish all the tasks in a single parallelBatch we may call startAppWork
                again. It can become true a few  times, although we run only one batch. Therefore, we have this firstSliceInBatch
                variable to reset the corresponding variable which is used in BatchManager and CRGlueThreads.
                 */
                boolean firstSliceInBatch = true;
//                System.err.println("batch size: " + allRequests.size());
                while (allRequests.size() > 0) {
                    //Debug.warning(Debug.MODULE_EXEC, "size: " + allRequests.size());
                    LinkedList<LinkedList<GeneralGlueTuple>> parallelBatch = runMixer(
                            allRequests, parameters.noOfThreads);

                    int workCount = 0;
//                    System.err.println("noOfThreads " + parameters.noOfThreads);
                    for (int i = 0; i < parameters.noOfThreads; i++) {
                        workCount += parallelBatch.get(i).size();
                    }
                    batchManager.startBatch(workCount);
//                    System.err.println("WorkCount: " + workCount);
                    for (int i = 0; i < threads.length; i++) {
//                        System.err.println("start app work: " + threads.length);
                        threads[i].startAppWork(parallelBatch.get(i), firstSliceInBatch);
                    }
                    batchManager.mainControlLoop();
                    this.readThreadMap.clear();
                    this.writeThreadMap.clear();
                    firstSliceInBatch = false;
                    if(batchManager.finishControlLoop)
                    {
                        break;
                    }
                }
            } else {
//                System.out.println("Executing in pp mode");
                // We are doing the pipelined batch management
                // We should put leftover requests back into the larger pool of requests
                //System.err.println("starts parallelPipelineManager");
                int tmpParallelBatchCounter = 0;
                while (allRequests.size() > 0) {
                    tmpParallelBatchCounter++;
                    //Normally runMixer method creates groups of parallelizable requests for exah thread. We give 1 as
                    //numberOfThreads because we just want one group since startBatches method makes the distribution afterwards
                    LinkedList<GeneralGlueTuple> parallelBatch = runMixer(allRequests,1).get(0);
                    //Debug.trace(Debug.MODULE_EXEC, parameters.level_trace, tmpParallelBatchCounter + ". parallelBatchSize is " + parallelBatch.size() + " while batch size: " + allRequests.size());
                    requestCounter += parallelBatch.size();
                    parallelPipelineManager.startBatches(parallelBatch);
//                    allRequests.clear();
                    parallelPipelineManager.mainControlLoop();

                    this.readThreadMap.clear();
                    this.writeThreadMap.clear();
                }
            }
        }
    }

    private LinkedList<LinkedList<GeneralGlueTuple>> runMixer(
            LinkedList<GeneralGlueTuple> allRequests, int numberOfThreads) {

        Iterator<GeneralGlueTuple> iter = allRequests.iterator();
        LinkedList<LinkedList<GeneralGlueTuple>> parallelBatch = new LinkedList<LinkedList<GeneralGlueTuple>>();
        for (int i = 0; i < numberOfThreads; i++) {
            LinkedList<GeneralGlueTuple> parallelBatchSlice = new LinkedList<GeneralGlueTuple>();
            parallelBatch.add(i, parallelBatchSlice);
        }

        while (iter.hasNext()) {
            GeneralGlueTuple tuple = iter.next();
            List<RequestKey> keys = null;
            if (parameters.useRules) {
                keys = filter.generateKeys(tuple.request);
            }
            if (keys == null || keys.size() == 0) {
                parallelBatch.get(requestCounter % numberOfThreads).add(tuple);
                requestCounter++;
                iter.remove();
            } else {
                boolean conflict = false;
                int conflictNo = 0;
                for (RequestKey key : keys) {
                    if (writeThreadMap.containsKey(key.getKey())) {
                        conflict = true;
                        conflictNo += writeThreadMap.get(key.getKey());
                        break;
                    }
                    if (key.isWrite()) {
                        if (readThreadMap.containsKey(key.getKey())) {
                            conflictNo += readThreadMap.get(key.getKey());
                            conflict = true;
                            break;
                        }
                    }
                }
                if (conflict) {
                    if (parameters.falseNegative > 0) {
                        double ratio = Math.pow(1.0 / (double) parameters.falseNegative, conflictNo);
                        if (rand.nextDouble() > ratio) {
                            continue;
                        }
                    } else {
                        continue;
                    }
                }
                if (requestCounter > 0 && parameters.falsePositive > 0) {
                    double ratio = 1 - Math.pow(1.0 - 1.0 / (double) parameters.falsePositive,
                            requestCounter);
                    if (rand.nextDouble() < ratio) {
                        continue;
                    }
                }
                parallelBatch.get(requestCounter % numberOfThreads).add(tuple);
                requestCounter++;
                for (RequestKey key : keys) {
                    if (key.isRead()) {
                        int count = 1;
                        if (readThreadMap.containsKey(key.getKey())) {
                            count += readThreadMap.get(key.getKey());
                        }
                        readThreadMap.put(key.getKey(), count);
                    } else {
                        int count = 1;
                        if (writeThreadMap.containsKey(key.getKey())) {
                            count += writeThreadMap.get(key.getKey());
                        }
                        writeThreadMap.put(key.getKey(), count);
                    }
                }
                iter.remove();
            }
        }
        return parallelBatch;
    }

    private void generateInfo(ExecuteBatchMessage request) {
        RequestBatch batch = request.getRequestBatch();
        Random rand = new Random(request.getNonDeterminism().getSeed());
        for (int i = 0; i < batch.getEntries().length; i++) {
            Entry tmp = batch.getEntries()[i].getEntry();
//            System.err.println("future subId: " + tmp.getSubId());
            if (tmp.getCommand() == null) {
                throw new RuntimeException(i + " getCommand==null size=" + tmp.getBytes().length);
            }
            RequestInfo info = new RequestInfo(false, (int) tmp.getClient(), (int) tmp.getSubId(),
                    request.getSeqNo(), tmp.getRequestId(), request.getNonDeterminism().getTime(),
                    rand.nextLong());
            allRequests.add(new GeneralGlueTuple(tmp.getCommand(), info));
        }
    }

    private static void printAllThreads() {
        ThreadGroup rootGroup = Thread.currentThread().getThreadGroup();
        ThreadGroup parentGroup;
        while ((parentGroup = rootGroup.getParent()) != null) {
            rootGroup = parentGroup;
        }

        Thread[] threads = new Thread[rootGroup.activeCount()];
        while (rootGroup.enumerate(threads, true) == threads.length)
            threads = new Thread[threads.length * 2];
        for (int i = 0; i < threads.length; i++) {
            if (threads[i] != null)
                System.out.println(threads[i] + " " + threads[i].getState());
        }
    }

    private static class TestHandler implements RequestHandler, RequestFilter {

        GeneralGlue glue;
        boolean isPipelined;
        int spins;
        int spinTime;

        public TestHandler(boolean isPipelined) {
            this.isPipelined = isPipelined;
        }

        public void setGlue(GeneralGlue glue) {
            this.glue = glue;
        }

        public void setSpins(String s) {
            if (s.equals("l")) {
                this.spins = 919974;
                this.spinTime = 10;
            } else if (s.equals("m")) {
                this.spins = 92583;
                this.spinTime = 1;
            } else if (s.equals("s")) {
                this.spins = 9257;
                this.spinTime = -1;
            } else if (s.equals("xl")) {
                this.spins = 92144064;
                this.spinTime = 1000;
            } else if (s.equals("xxl")) {
                this.spins = 921862432;
                this.spinTime = 10000;
            }
        }

        private void spin() {
            if (Thread.currentThread() instanceof IndexedThread) {
                Debug.info(Debug.MODULE_EXEC, "TIMINGS[BS] Thread %d StartBenchNestedRequest %d",
                        ((IndexedThread) Thread.currentThread()).getIndex(), System.nanoTime());
            }
            try {
                if (this.spinTime == -1) {
                    Thread.sleep(0, 100000);
                } else {
                    Thread.sleep(this.spinTime);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (Thread.currentThread() instanceof IndexedThread) {
                Debug.info(Debug.MODULE_EXEC, "TIMINGS[BS] Thread %d EndBenchNestedRequest %d",
                        ((IndexedThread) Thread.currentThread()).getIndex(), System.nanoTime());
            }
        }

        public void execRequest(byte[] request, RequestInfo info) {
            //System.out.println(Thread.currentThread() + " executes "
            //    + UnsignedTypes.bytesToString(request));
            int id = 0;
            if (Thread.currentThread() instanceof IndexedThread) {
                id = ((IndexedThread) Thread.currentThread()).getIndex();
            }
            if (Thread.currentThread() instanceof IndexedThread) {
                Debug.info(Debug.MODULE_EXEC, "TIMINGS[BS] Thread %d StartBenchExecRequest %d",
                        id, System.nanoTime());
            }

            Debug.info(Debug.MODULE_EXEC, "asdf %d is running first", id);
            Debug.info(Debug.MODULE_EXEC, "TIMINGS[BS] Thread %d StartBenchLocalRequest %d",
                    id, System.nanoTime());
            spin();
            Debug.info(Debug.MODULE_EXEC, "TIMINGS[BS] Thread %d EndBenchLocalRequest %d",
                    id, System.nanoTime());
            Debug.info(Debug.MODULE_EXEC, "asdf %d finished first", id);
            if (parameters.pipelinedSequentialExecution) {
                glue.yieldPipeline();
                spin();
                glue.waitForMyTurn();
                return;
            }
            glue.hitWall();
            if (!isPipelined || glue.isSequential()) {
                spin();
            } else {
                Debug.info(Debug.MODULE_EXEC, "asdf %d is yielding", id);
                glue.yieldPipeline();
                Debug.info(Debug.MODULE_EXEC, "asdf %d is running nested", id);
                spin();
                Debug.info(Debug.MODULE_EXEC, "asdf %d finished nested", id);
                ((CRGlueThread) Thread.currentThread()).waitForMyTurn();
                Debug.info(Debug.MODULE_EXEC, "asdf %d has turn again", id);
            }
            Debug.info(Debug.MODULE_EXEC, "TIMINGS[BS] Thread %d EndBenchExecRequest %d",
                    id, System.nanoTime());
        }

        public void execReadOnly(byte[] request, RequestInfo info) {
            // TODO implement
            Debug.kill("Not Implemented!!!");
        }

        public ArrayList<RequestKey> generateKeys(byte[] request) {
            ArrayList<RequestKey> ret = new ArrayList<RequestKey>();
            for (int i = 0; i < request.length; i++) {
                ret.add(new RequestKey(false, request[i]));
            }
            return ret;
        }
    }

    public static void main(String[] args) throws Exception {
        Debug.debug(Debug.MODULE_EXEC, "Executing a main method that shouldn't exist!!!");
        Parameters param = new Parameters();
        boolean sequential = false;
        if (args.length > 0) {
            System.out.println("setting = " + args[0]);
            if (args[0].equals("s")) {
                sequential = true;
                param.parallelExecution = false;
                param.pipelinedBatchExecution = false;
                param.pipelinedSequentialExecution = true;
            } else if (args[0].equals("p")) {
                sequential = false;
                param.parallelExecution = true;
                param.pipelinedBatchExecution = false;
                param.noOfThreads = 1;
                MerkleTreeInstance.init(param, 8, param.noOfObjects, 3, true);
            } else if (args[0].equals("pb")) {
                sequential = false;
                param.parallelExecution = true;
                param.pipelinedBatchExecution = true;
                param.numWorkersPerBatch = 1;
                param.numPipelinedBatches = 2;
                MerkleTreeInstance.init(param, 8, param.noOfObjects, 3, true);
            }
        }
        int numGroups = 64;
        if (args.length > 3) {
            String[] amounts = args[3].split(",");
            if (amounts.length == 1) {
                param.noOfThreads = Integer.parseInt(amounts[0]);
                param.numWorkersPerBatch = 1;
                param.numPipelinedBatches = param.noOfThreads;
            } else {
                param.noOfThreads = Integer.parseInt(amounts[1]);
                param.numWorkersPerBatch = param.noOfThreads;
                param.numPipelinedBatches = Integer.parseInt(amounts[0]);
            }
        }
        // Dynamically set the number of requests / batch
        if (args.length > 4) {
            numGroups = Integer.parseInt(args[4]);
        }
        param.doLogging = false;
        param.level_fine = false;
        param.level_debug = false;
        param.level_info = false;
        param.level_warning = false;
        param.level_error = true;

        if (args.length > 5) {
            if (args[5].equals("dummy")) {
                param.useDummyTree = true;
            } else {
                param.useDummyTree = false;
            }
        }

        TestHandler handler = new TestHandler(param.pipelinedBatchExecution);
        GeneralGlue glue = new GeneralGlue(handler, handler, param.noOfThreads, param, true, 0, null);
        handler.setGlue(glue);
        if (args.length > 2) handler.setSpins(args[2]);

        int numBatches = (args.length > 1) ? Integer.parseInt(args[1]) : 1;
        long start = System.nanoTime();

        System.out.println("About to execute first request.");
        for (int j = 0; j < numBatches; j++) {
            int requestLength = 1;
            byte[][] requests = new byte[numGroups][requestLength];
            for (int k = 0; k < numGroups; k++) {
                for (int l = 0; l < requestLength; l++) {
                    requests[k][l] = (byte) (k * requestLength + l);
                }
            }
            FilteredRequestCore[] core = new FilteredRequestCore[requests.length];
            for (int i = 0; i < requests.length; i++) {
                core[i] = new FilteredRequestCore(param, 0, 0, 0, requests[i]);
            }

            RequestBatch batch = new RequestBatch(param, core);
            ExecuteBatchMessage msg = new ExecuteBatchMessage(new Parameters(), 1, 2, batch,
                    new NonDeterminism(0, 0), 0);
            glue.executeBatch(msg);
            System.out.println("Finished batch: " + j);
        }
        double totalTime = (System.nanoTime() - start) / 1000000000.0;
        double totalRequests = numGroups * numBatches;

        System.out.println("\n== Results ==\n");
        System.out.println("Throughput: " + (totalRequests / totalTime));
        glue.stop();
        for (MerkleTreeThread t : MerkleTreeThread.getAllThreads()) {
            t.terminate();
        }
    }

    // TODO move this to batchManager
    public int getThreadIDToExec() {
        if (isPipelinedBatch) {
            return parallelPipelineManager.getThreadIDToExec();
        }
        return threadIDToExecute;
    }

    public void nextThreadIDToExec() {
        if (isPipelinedBatch) {
            parallelPipelineManager.nextThreadIDToExec();
            return;
        }
        threadIDToExecute++;
        threadIDToExecute %= parameters.noOfThreads;
    }

    public boolean isSequential() {
        if (!parameters.parallelExecution) {
            return true;
        } else {
            if (isPipelinedBatch) {
                return parallelPipelineManager.isSequential();
            } else {
                return batchManager.isSequential();
            }
        }
    }

    public boolean isMiddle() {
        return isMiddle;
    }

    public void startNextMerkleTreeVersion() {
        if (isPipelinedBatch) {
            parallelPipelineManager.startNextMerkleTreeVersion();
        } else {
            batchManager.startNextMerkleTreeVersion();
        }
    }

    public void linkRequestNoToMerkleTreeVersion(long requestNo) {
        if (isPipelinedBatch) {
            parallelPipelineManager.linkRequestNoToMerkleTreeVersion(requestNo);
        } else {
            batchManager.linkRequestNoToMerkleTreeVersion(requestNo);
        }
    }

    public long getMerkleTreeVersionLinkedWithRequestNo(long requestNo) {
        if (isPipelinedBatch) {
            return parallelPipelineManager.getMerkleTreeVersionLinkedWithRequestNo(requestNo);
        } else {
            return batchManager.getMerkleTreeVersionLinkedWithRequestNo(requestNo);
        }
    }

    public long getCurrentMerkleTreeVersion() {
        if (isPipelinedBatch) {
            return parallelPipelineManager.getMerkleTreeVersion();
        } else {
            return batchManager.getMerkleTreeVersion();
        }
    }

    public void passCheckpoint(int threadID, Continuation currentCheckpoint) {
        if (isPipelinedBatch) {
            parallelPipelineManager.passCheckpoint(threadID, currentCheckpoint);
        } else {
            batchManager.passCheckpoint(threadID, currentCheckpoint);
        }
    }

    public void finishRollback() {
        if (isPipelinedBatch) {
            parallelPipelineManager.finishRollback();
        } else {
            batchManager.finishRollback();
        }
    }

    public boolean needRollback() {
        Debug.debug(Debug.MODULE_EXEC, "Setting need rollback flag!!!");
        if (isPipelinedBatch) {
            Debug.debug(Debug.MODULE_EXEC, "calling needRollback() on parallelPipelineManager");
            return parallelPipelineManager.needRollback();
        } else {
            return batchManager.needRollback();
        }
    }

    public void finishInitializeContinuation() {
        if (isPipelinedBatch) {
            parallelPipelineManager.finishInitializeContinuation();
        } else {
            batchManager.finishInitializeContinuation();
        }
    }

    public void notifyVerifySucceeded(long seqNo) {
        if (isPipelinedBatch && parameters.sendVerify) {
            parallelPipelineManager.notifyVerifySucceeded(seqNo);
        }
    }

    public boolean isPipelinedBatchExecution() {
        return isPipelinedBatch;
    }

    public void resetThreadIdToExecute() {
        threadIDToExecute = 0;
    }
}
