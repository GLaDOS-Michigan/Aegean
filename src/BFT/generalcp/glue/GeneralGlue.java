package BFT.generalcp.glue;

import BFT.Debug;
import BFT.Parameters;
import BFT.exec.RequestFilter;
import BFT.exec.RequestKey;
import BFT.exec.glue.PipelinedSequentialManager;
import BFT.generalcp.AppCPInterface;
import BFT.generalcp.RequestInfo;
import BFT.messages.CommandBatch;
import BFT.messages.Entry;
import BFT.messages.NonDeterminism;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: iodine
 * Date: Apr 14, 2010
 * Time: 10:55:46 PM
 * To change this template use File | Settings | File Templates.
 */
public class GeneralGlue {

    private AppCPInterface handler;
    private RequestFilter filter;
    private GlueThread[] threads = null;
    ArrayList<GeneralGlueTuple> allRequests = new ArrayList<GeneralGlueTuple>();
    ArrayList<GeneralGlueTuple>[] requestsPerThread;

    private static Parameters parameters;
    private boolean isMiddle = false;
    private PipelinedSequentialGlueThread[] pipe_threads = null;
    private PipelinedSequentialManager pipelinedSequentialManager = null;
    public static double totalTime = 0;
    public static double numExecution = 0;

    public GeneralGlue(AppCPInterface handler, RequestFilter filter, int noOfThreads, Parameters parameters, boolean isMiddle) {
        this.parameters = parameters;
        this.isMiddle = isMiddle;
        this.handler = handler;
        this.filter = filter;
        threads = new GlueThread[noOfThreads];

        if(parameters.pipelinedSequentialExecution) {
            assert (isMiddle);
            pipe_threads = new PipelinedSequentialGlueThread[noOfThreads];
            pipelinedSequentialManager = new PipelinedSequentialManager(noOfThreads);

            for (int i = 0; i < noOfThreads; i++) {
                pipe_threads[i] = new PipelinedSequentialGlueThread(handler, this);
            }
            for (int i = 0; i < noOfThreads; i++) {
                pipe_threads[i].start();
            }
        }
        else
        {
            for (int i = 0; i < noOfThreads; i++) {
                threads[i] = new GlueThread(i, handler, this);
                threads[i].start();
            }
            requestsPerThread = new ArrayList[noOfThreads];
            for (int i = 0; i < noOfThreads; i++) {
                requestsPerThread[i] = new ArrayList<GeneralGlueTuple>();
            }
        }
    }

    private int threadCount = 0;

    private synchronized void startTask(int threadCount) {
        //System.out.println("StartTask "+threadCount);
        this.threadCount = threadCount;
    }

    public synchronized void finishTask() {
        //System.out.println("FinishTask "+Thread.currentThread());
        threadCount--;
        this.notify();
    }

    private synchronized void waitForTask() {
        while (threadCount != 0) {
            try {
                this.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        Debug.debug(Debug.MODULE_EXEC, "Finish a parallel batch");
    }

    public void stop() {
        for (int i = 0; i < threads.length; i++) {
            threads[i].terminate();
        }
    }

    private HashMap<RequestKey, Integer> readThreadMap = new HashMap<RequestKey, Integer>();
    private HashMap<RequestKey, Integer> writeThreadMap = new HashMap<RequestKey, Integer>();

    public void executeBatch(CommandBatch request, long seqNo, NonDeterminism nd, boolean sequential, boolean takeCP) {
        long startTime = System.nanoTime();

        Debug.debug(Debug.MODULE_EXEC, "Start executeBatch\n");
        generateInfo(request, seqNo, nd, takeCP);

        if(parameters.pipelinedSequentialExecution) {
//            System.err.println("psbatch size: " + allRequests.size());
            assert(isMiddle);

            int requestCounter = 0;
//            Debug.debug(Debug.MODULE_EXEC, "Using pipelined execution\n");
            Iterator<GeneralGlueTuple> iter = allRequests.iterator();
            LinkedList<LinkedList<GeneralGlueTuple>> batch = new LinkedList<LinkedList<GeneralGlueTuple>>();
            int numThreadsInActiveBatch = Math.min(allRequests.size(), pipe_threads.length);

            for (int i = 0; i < numThreadsInActiveBatch; i++) {
                LinkedList<GeneralGlueTuple> batchSlice = new LinkedList<GeneralGlueTuple>();
                batch.add(i, batchSlice);
            }

            while (iter.hasNext()) {
                GeneralGlueTuple tuple = iter.next();
//                Debug.debug(Debug.MODULE_EXEC, "Adding request %d to thread %d\n",
//                        requestCounter, requestCounter % parameters.noOfThreads);
                batch.get(requestCounter % numThreadsInActiveBatch).add(tuple);
                requestCounter++;
                iter.remove();
            }

            pipelinedSequentialManager.setNumThreadsInActiveBatch(numThreadsInActiveBatch);
            pipelinedSequentialManager.startBatch();
            long firstStop = System.nanoTime();
            long timeSpent = firstStop - startTime;
//            System.err.println("timeSPent at 1: " + timeSpent/1000);

            for (int i = 0; i < numThreadsInActiveBatch; i++) {
//                Debug.debug(Debug.MODULE_EXEC, "Starting pipe_thread[%d]\n", i);
                pipe_threads[i].startAppWork(batch.get(i));
            }

            long secondStop = System.nanoTime();
            timeSpent = secondStop - firstStop;
//            System.err.println("timeSPent at 2: " + timeSpent/1000);
//            Debug.debug(Debug.MODULE_EXEC, "Waiting for threads to deactivate\n");
            pipelinedSequentialManager.waitForAllThreadsToDeactivate();

            long thirdStop = System.nanoTime();
            timeSpent = thirdStop - secondStop;
//            System.err.println("timeSPent at 3: " + timeSpent/1000);
//            Debug.debug(Debug.MODULE_EXEC, "All threads deactivated\n");

        }
        else if (sequential) {
//            System.err.println("running sequential without pipelining");
//            System.err.println("sbatch size: " + allRequests.size());

            for (GeneralGlueTuple tuple : this.allRequests) {
                this.handler.execAsync(tuple.request, tuple.info);
            }
            this.allRequests.clear();
        } else {
            while (allRequests.size() > 0) {
                Iterator<GeneralGlueTuple> iter = allRequests.iterator();
                while (iter.hasNext()) {
                    GeneralGlueTuple tuple = iter.next();
                    List<RequestKey> keys = null;
                    if (filter != null) {
                        keys = filter.generateKeys(tuple.request);
                    }
                    if (keys == null || keys.size() == 0) {
                        int id = findLeastOverheadThread();
                        requestsPerThread[id].add(tuple);
                        iter.remove();
                    } else {
                        boolean conflict = false;
                        for (RequestKey key : keys) {
                            if (writeThreadMap.containsKey(key)) {
                                conflict = true;
                                break;
                            }
                            if (key.isWrite()) {
                                if (readThreadMap.containsKey(key)) {
                                    conflict = true;
                                    break;
                                }
                            }
                        }
                        if (conflict) {
                            continue;
                        }
                        int id = findLeastOverheadThread();

                        requestsPerThread[id].add(tuple);
                        for (RequestKey key : keys) {
                            if (key.isRead())
                                readThreadMap.put(key, id);
                            else
                                writeThreadMap.put(key, id);
                        }
                        iter.remove();
                    }
                }
                this.startTask(this.threads.length);
                for (int i = 0; i < threads.length; i++) {
                    //System.out.println("app work "+i+":"+requestsPerThread[i].size());
                    threads[i].startAppWork(requestsPerThread[i]);
                }
                this.waitForTask();
                for (int i = 0; i < threads.length; i++) {
                    requestsPerThread[i].clear();
                }
                this.readThreadMap.clear();
                this.writeThreadMap.clear();
            }
        }
        long timeSpent = System.nanoTime() - startTime;
//        System.err.println("timeSPent: " + timeSpent/1000);
        totalTime += timeSpent/1000;
        numExecution++;
//        System.err.println("average run time: " + totalTime/(double) numExecution);

        //System.err.println("time = " + (System.currentTimeMillis() - startTime));
    }

    private int findLeastOverheadThread() {
        int tasks = 10000;
        int id = -1;
        for (int i = 0; i < this.threads.length; i++) {
            if (this.requestsPerThread[i].size() < tasks) {
                id = i;
                tasks = requestsPerThread[i].size();
            }
        }
        return id;
    }

    private void generateInfo(CommandBatch request, long seqNo, NonDeterminism nd, boolean takeCP) {
        Random rand = new Random(nd.getSeed());
        for (int i = 0; i < request.getEntries().length; i++) {
            Entry tmp = request.getEntries()[i];
            if (tmp.getCommand() == null) {
                throw new RuntimeException(i + " getCommand==null size=" + tmp.getBytes().length);
            }
            RequestInfo info = new RequestInfo(false, (int) tmp.getClient(), (int) tmp.getSubId(), seqNo,
                    tmp.getRequestId(), nd.getTime(), rand.nextLong());
            if (takeCP && i == request.getEntries().length - 1) {
                info.setLastReqBeforeCP();
            }
            allRequests.add(new GeneralGlueTuple(tmp.getCommand(), info));
        }
    }

    public PipelinedSequentialManager getPipelinedSequentialManager() {
        return pipelinedSequentialManager;
    }
}
