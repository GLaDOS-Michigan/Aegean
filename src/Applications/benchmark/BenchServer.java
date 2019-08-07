// $Id: BenchServer.java 722 2011-07-04 09:24:35Z aclement $
package Applications.benchmark;

import BFT.Debug;
import BFT.Parameters;
import BFT.clientShim.ClientShimBaseNode;
import BFT.exec.*;
import BFT.exec.TimingLogger.RealTimingLogger;
import BFT.exec.TimingLogger.TimingLogger;
import merkle.IndexedThread;
import merkle.MerkleTree;
import merkle.MerkleTreeInstance;
import util.UnsignedTypes;

import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

//The server class must implement the two interfaces
public class BenchServer implements RequestHandler, RequestFilter, Serializable {
    boolean doneRollback = true;
    byte[][] data;
    transient public int[] spinArray;
    transient private int dataSize;

    transient public BenchDigest digest;
    transient private int id;

    transient private long maxMessedUpSeqno = 0;
    transient private Parameters parameters;

    transient private ReplyHandler replyHandler;
    transient private ClientShimBaseNode[] csbns;
    transient private BenchClientMulti[] clients;
    transient private ExecBaseNode ebn;
    transient private double backendRatio;
    transient private long startTime;

    transient private int numThreads;
    transient private TimingLogger execLogger[];
    transient private TimingLogger spinLogger[];
    transient private TimingLogger nestedRequestLogger[];

    public BenchServer(Parameters param, int dataNum, int dataSize, ReplyHandler replyHandler, int id, ExecBaseNode ebn) {
        this.parameters = param;
        this.dataSize = dataSize;
        this.replyHandler = replyHandler;
        this.id = id;
        this.ebn = ebn; //Set execBaseNode

        this.numThreads = param.pipelinedBatchExecution ?  //TODO what are these loggers? Where do they log? Threads?
                param.numPipelinedBatches * param.numWorkersPerBatch : param.noOfThreads;

        this.execLogger = new TimingLogger[numThreads];
        this.spinLogger = new TimingLogger[numThreads];
        this.nestedRequestLogger = new TimingLogger[numThreads];
        for (int i = 0; i < numThreads; ++i) {
            this.execLogger[i] = new RealTimingLogger("exec", 1000);
            this.spinLogger[i] = new RealTimingLogger("doSpin", 1000);
            this.nestedRequestLogger[i] = new RealTimingLogger("nestedRequest", 1000);
        }

        data = new byte[dataNum][]; //TODO What is data and dataNum? Could it be number of total operations
        spinArray = new int[parameters.numberOfClients]; //TODO What is spinArray?

        MerkleTreeInstance.addRoot(data);//TODO can be very important part to understand MerkleTree interaction
        for (int i = 0; i < dataNum; i++) {
            data[i] = new byte[dataSize];
        }
        try {
            MerkleTreeInstance.get().scanNewObjects();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void execReadOnly(byte[] request, RequestInfo info) {
        BenchmarkRequest req = new BenchmarkRequest(request);
        BenchReply rep = null;

        int spins = req.getSpins();
        int reads = req.getReads();
        int writes = req.getWrites();
        int prob = req.getProb();

        int readOffset = req.getStartIndex();
        int writeOffset = req.getStartIndex();

        if (writes != 0) {
            throw new RuntimeException("Not a readonly request");
        }
        byte[] randomBytes = new byte[1024];

        for (int i = 0; i < spins; i++) {
            digest = new BenchDigest(parameters, randomBytes);
        }
        int index;

        byte[] reply = new byte[dataSize];
        for (int i = 0; i < reads; i++) {
            index = (readOffset++) % data.length;

            synchronized (data[index]) {
                System.arraycopy(data[index], 0, reply, 0, dataSize);
            }
        }


        rep = new BenchReply(false, reply);


        replyHandler.readOnlyResult(rep.getBytes(), info);
    }

    public void execRequest(byte[] request, RequestInfo info) {
//        System.err.println("Request Info: ");
        int id = 0;
        if (Thread.currentThread() instanceof IndexedThread) {
            id = ((IndexedThread) Thread.currentThread()).getIndex() % numThreads;
        }
        if (Thread.currentThread() instanceof IndexedThread) {
            Debug.info(Debug.MODULE_EXEC, "TIMINGS[BS] Thread %d StartBenchExecRequest %d",
                    id, System.nanoTime());
        }
        Debug.info(Debug.MODULE_EXEC, "TIMINGS[BS] Thread %d StartBenchLocalRequest %d",
                id, System.nanoTime());
        execLogger[id].logStart();

        BenchmarkRequest req = new BenchmarkRequest(request);
//        System.err.println("BenchmarkRequest is created: " + req.toString());

        BenchReply rep = null;
//        System.err.println("Bench Reply is created as null");
        int spins = req.getSpins();
        int reads = req.getReads();
        int writes = req.getWrites();


        int readOffset = req.getStartIndex();
        int writeOffset = req.getStartIndex();
        int client = info.getClientId();
        int backendSpins = 0;
//        System.out.println("spins1: " + spins);
	if(csbns != null) {
            int middleSpins =(int) ((spins * 2.0) / (1+backendRatio));
            backendSpins = (2 * spins - middleSpins);
            spins = middleSpins;
//	    System.out.println("spins2: " + spins + ", backendRatio: " + backendRatio + ", backendSpins: " + backendSpins);
        }
        spinLogger[id].logStart();
        long s;
        if (csbns != null) {
            s = doSpin(spins / 2, client);
        } else {
            s = doSpin(spins, client);
        }
//        spinLogger[id].logEnd();
//        Debug.debug(Debug.MODULE_EXEC, "this is the spin result %d", s);
        int index;

        if (!parameters.clientReadOnly) {
            byte[] writeBytes = new byte[dataSize];
            for (int i = 0; i < writes; i++) {
                index = (writeOffset++) % data.length;
                MerkleTreeInstance.update(data[index]);
                synchronized (data[index]) {
                    for (int j = 0; j < dataSize; j++) {
                        data[index][j] = writeBytes[j];
                        int t = writeBytes[j];//TODO seems unnecessary
                    }
                    data[index][0] = (byte) (info.getClientId() >> 8);
                    data[index][1] = (byte) (info.getClientId() & 255);
                }
                rep = new BenchReply(false, null);
//                System.out.println("rep is initialized");
            }
        }
        byte[] reply = new byte[dataSize];
        for (int i = 0; i < reads; i++) {
            index = (readOffset++) % data.length;

            synchronized (data[index]) {
                for (int j = 0; j < dataSize; j++)
                    reply[j] = data[index][j];
            }
        }
        rep = new BenchReply(false, new byte[1]);

        Debug.info(Debug.MODULE_EXEC, "TIMINGS[BS] Thread %d EndBenchLocalRequest %d",
                id, System.nanoTime());
        BenchmarkRequest nestedRequest = new BenchmarkRequest(req.getStartIndex(), (int) (backendSpins),
                req.getReads(), req.getWrites(), req.getData().length, req.getProb());
//        System.err.println("nestedRequest is created: " + nestedRequest.toString());

        byte[] replyFromSecondService = null;

        try {
            if (csbns != null) {//csbn's nullity indicates whether we are also client-in case of middle- or not

                if (Thread.currentThread() instanceof IndexedThread) {
                    Debug.info(Debug.MODULE_EXEC, "TIMINGS[BS] Thread %d StartBenchNestedRequest %d",
                            ((IndexedThread) Thread.currentThread()).getIndex(), System.nanoTime());
                }

                replyFromSecondService = ebn.execNestedRequest(nestedRequest.getBytes(), csbns);
                s = doSpin(spins/2, client);


                if (Thread.currentThread() instanceof IndexedThread) {
                    Debug.info(Debug.MODULE_EXEC, "TIMINGS[BS] Thread %d EndBenchNestedRequest %d",
                            ((IndexedThread) Thread.currentThread()).getIndex(), System.nanoTime());
                }

                if (parameters.sendNested)
                    replyHandler.result(replyFromSecondService, info);
                else
                    replyHandler.result(rep.getBytes(), info);
            } else {
                replyHandler.result(rep.getBytes(), info);
            }
        } catch (Exception e) {
            System.out.println("Exception " + e);
            StringWriter errors = new StringWriter();
            e.printStackTrace(new PrintWriter(errors));
            System.out.println(errors.toString());
        }


        if (Thread.currentThread() instanceof IndexedThread) {
            Debug.info(Debug.MODULE_EXEC, "TIMINGS[BS] Thread %d EndBenchExecRequest %d",
                    id, System.nanoTime());
        }
        //execLogger[id].logEnd();
    }

    public long doSpin(int spins, int client) {
        long time = ((long) spins) * 1000;
        long start = System.nanoTime();
        long end = System.nanoTime();
        while (time > (end - start)) {
            end = System.nanoTime();
        }

//	System.out.println("spins: " + spins + ", time: " + time);
        return -1337L;
    }


    public List<RequestKey> generateKeys(byte[] request) {
        BenchmarkRequest req = new BenchmarkRequest(request);
        int reads = req.getReads();
        int writes = req.getWrites();
        int ops = Math.max(reads, writes);

        ArrayList<RequestKey> ret = new ArrayList<RequestKey>(ops);
        for (int i = 0; i < ops; i++) {
            ret.add(new RequestKey(parameters.clientReadOnly, (req.getStartIndex() + i) % data.length));
        }
        return ret;

    }

    public void setupClient(String membershipFile, int clientId, int subId) {
        setupClient(membershipFile, clientId, subId, 1);
    }

    public void setupClient(String membershipFile, int clientId, int subId, double backendRatio) {
        //TODO should backendRatio be used here?
        //Here is test code for setting up client configure
        //csbn = new ClientShimBaseNode(membershipFile, clientId, subId);


        //	csbn.setNetwork(new TCPNetwork(csbn));
        int numClients = parameters.pipelinedBatchExecution ? //TODO I think each thread is a client to the backend server?
                parameters.numPipelinedBatches * parameters.numWorkersPerBatch :
                parameters.noOfThreads;

        clients = new BenchClientMulti[numClients];
        csbns = new ClientShimBaseNode[numClients];
        this.backendRatio = backendRatio; //TODO I think this is unnecessary, it , Benchserver already updates
        //its backend ratio in its constructor. or We may remove backendRatio of BenchServer
        for (int i = 0; i < numClients; i++) {
            clients[i] = new BenchClientMulti(membershipFile, clientId + i, subId);
            csbns[i] = clients[i].getCSBN();
        }

        //csbn.setGlue(client);
        //csbn.setFlagForPara(1);


        //SenderWrapper sendNet = new SenderWrapper(csbn.getMembership(), 1);
        //csbn.setNetwork(sendNet);


		/*Role[] roles = new Role[2];
        roles[0] = Role.EXEC;
		roles[1] = Role.FILTER;
		//roles[2] = Role.FILTER;

		PassThroughNetworkQueue ptnq = new PassThroughNetworkQueue(csbn);
		ReceiverWrapper receiveNet = new ReceiverWrapper(roles, csbn.getMembership(), ptnq, 1);*/
        // Test code over
    }

    public static void main(String args[]) throws Exception {
        if (args.length < 4) {
            System.out.println("Usage: BenchServer <id> <membershipFile> <dataNum> <dataSize> [<clientMembership> <clientId> <clientSubId> [<backendRatio]]");
            return;
        }
        int id = Integer.parseInt(args[0]);
        String membershipFile = args[1];
        int dataNum = Integer.parseInt(args[2]);
        int dataSize = Integer.parseInt(args[3]);
        boolean isMiddle = args.length > 4;
        ExecBaseNode exec = new ExecBaseNode(membershipFile, id, isMiddle);
        System.out.println("In BenchServer main, isMiddle: " + exec.getParameters().isMiddleClient);
        BenchServer main = new BenchServer(exec.getParameters(), dataNum, dataSize, exec, id, exec);

		/* manos: TODO seems an important explanation
         * The middle service will now use one ClientShimBaseNode
		 * per GlueThread. As such, I will use the clientId as a 
		 * "base" clientId, and assign increasing ids to each client. 
		 */
        if (args.length > 4) { //This part is just for middle service
            String clientMembership = args[4];
            int clientId = Integer.parseInt(args[5]); //TODO always zero from start_middle.py?
            int subId = Integer.parseInt(args[6]); //TODO str(i) like id from start_middle.py?
            if (args.length > 7) {
                double backendRatio = Double.parseDouble(args[7]);
                Debug.debug(Debug.MODULE_EXEC, "clientMembership: %s, clientId: %s, subId: %s, backendRatio: %s", clientMembership, clientId, subId, backendRatio); //TODO where is this written or not written?
                main.setupClient(clientMembership, clientId, subId, backendRatio);
            } else {
                main.setupClient(clientMembership, clientId, subId);
            }
        }
        exec.start(main, main);
    }

    private int[] getIntArrayFromBytes(byte[] request) {
        int[] ret = new int[4];
        byte[] temp = new byte[4]; // 4 bytes for an int
        System.arraycopy(request, 0, temp, 0, 4);
        ret[0] = UnsignedTypes.bytesToInt(temp);
        System.arraycopy(request, 4, temp, 0, 4);
        ret[1] = UnsignedTypes.bytesToInt(temp);
        System.arraycopy(request, 8, temp, 0, 4);
        ret[2] = UnsignedTypes.bytesToInt(temp);
        System.arraycopy(request, 12, temp, 0, 4);
        ret[3] = UnsignedTypes.bytesToInt(temp);

        return ret;

    }
}
