package Applications.benchmark;

import BFT.Parameters;
import BFT.exec.ReplyHandler;
import BFT.exec.RequestInfo;
import BFT.exec.glue.GeneralGlue;
import BFT.exec.messages.ExecuteBatchMessage;
import BFT.exec.messages.RequestBatch;
import BFT.messages.FilteredRequestCore;
import BFT.messages.NonDeterminism;
import merkle.MerkleTreeInstance;

import java.util.Random;


/**
 * Created by IntelliJ IDEA.
 * User: iodine
 * Date: Apr 24, 2010
 * Time: 8:48:21 PM
 * To change this template use File | Settings | File Templates.
 */
public class BenchLocal implements ReplyHandler {
    private BenchServer server;
    private GeneralGlue glue;
    private int dataSize;
    private int dataNum;
    byte[][] replies = new byte[256][];
    private Parameters parameters;

    public BenchLocal(int dataNum, int dataSize) {
        parameters = new Parameters();
        parameters.useDummyTree = false;
        MerkleTreeInstance.init(parameters, 8, 10000, 4, true);
        MerkleTreeInstance.add(replies);
        for (int i = 0; i < replies.length; i++) {
            replies[i] = new byte[dataSize];
            MerkleTreeInstance.add(replies[i]);
        }
        this.dataNum = dataNum;
        this.dataSize = dataSize;
        server = new BenchServer(parameters, dataNum, dataSize, this, 0, null);
        glue = new GeneralGlue(server, server, 4, parameters, null);
    }

    public void testbench(int spin, int read, int write, int noofRequests, int batchsize) {
        long startTime = System.currentTimeMillis();
        Random rand = new Random();
        for (int i = 0; i < noofRequests / batchsize; i++) {
            FilteredRequestCore[] cores = new FilteredRequestCore[batchsize];
            for (int j = 0; j < batchsize; j++) {
                int index = rand.nextInt(dataNum - write - 1);
                int client = rand.nextInt(255);
                BenchmarkRequest request = new BenchmarkRequest(index, spin, read, write, dataSize, 0);
                cores[j] = new FilteredRequestCore(parameters, client, 0, 0, request.getBytes());
            }
            ExecuteBatchMessage msg = createBatch(cores);
            glue.executeBatch(msg);
            MerkleTreeInstance.getShimInstance().getHash();
        }
        long endTime = System.currentTimeMillis();
        System.out.println("Throughput =" + (double) (noofRequests) / (endTime - startTime));

    }

    private ExecuteBatchMessage createBatch(FilteredRequestCore[] reqs) {
        RequestBatch batch = new RequestBatch(parameters, reqs);
        NonDeterminism nondt = new NonDeterminism(System.currentTimeMillis(), 1);
        ExecuteBatchMessage msg = new ExecuteBatchMessage(parameters, 0, 0, batch, nondt, 0);
        return msg;

    }

    public void result(byte[] reply, RequestInfo info) {
        //System.out.println("result "+info.getClientId());
        MerkleTreeInstance.update(replies[info.getClientId()]);
    }

    public void readOnlyResult(byte[] reply, RequestInfo info) {
        throw new RuntimeException("Not implemented yet");
    }

    public static void main(String[] args) {
        if (args.length != 7) {
            System.out.println("BenchLocal <dataNum> <dataSize> <spin> <read> <write> <noOfRequests> <batchsize>");
            return;
        }
        int dataNum = Integer.parseInt(args[0]);
        int dataSize = Integer.parseInt(args[1]);
        int spin = Integer.parseInt(args[2]);
        int read = Integer.parseInt(args[3]);
        int write = Integer.parseInt(args[4]);
        int noOfRequests = Integer.parseInt(args[5]);
        int batchsize = Integer.parseInt(args[6]);
        BenchLocal bench = new BenchLocal(dataNum, dataSize);
        bench.parameters.numberOfClients = 255;
        bench.testbench(spin, read, write, noOfRequests, batchsize);
    }
}
