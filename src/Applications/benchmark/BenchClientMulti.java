package Applications.benchmark;

import BFT.clientShim.ClientGlueInterface;
import BFT.clientShim.ClientShimBaseNode;
import BFT.network.PassThroughNetworkQueue;
import BFT.network.netty.NettyTCPReceiver;
import BFT.network.netty.NettyTCPSender;
import BFT.util.Role;
import BFT.util.UnsignedTypes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Random;

public class BenchClientMulti implements ClientGlueInterface {

    transient private ClientShimBaseNode clientShim;

    public BenchClientMulti(String membership, int id) {
        this(membership, id, 0);
    }

    public BenchClientMulti(String membership, int id, int subId) {
        clientShim = new ClientShimBaseNode(membership, id, subId);
        clientShim.setGlue(this);
        NettyTCPSender sendNet = new NettyTCPSender(clientShim.getParameters(), clientShim.getMembership(), 1);
        clientShim.setNetwork(sendNet);

        Role[] roles = new Role[3];
        roles[0] = Role.VERIFIER;
        roles[1] = Role.EXEC;
        roles[2] = Role.FILTER;

        PassThroughNetworkQueue ptnq = new PassThroughNetworkQueue(clientShim);
        NettyTCPReceiver receiveNet = new NettyTCPReceiver(roles,
                clientShim.getMembership(), ptnq, 1);

        clientShim.start();
    }

    public ClientShimBaseNode getCSBN() {
        return clientShim;
    }

    public byte[] send(int index, int cpu, int reads, int writes, int dataSize, int prob, PrintStream out, int id, int i) {
        BenchmarkRequest bmrq = new BenchmarkRequest(index, cpu, reads, writes, dataSize, prob);
        byte[] replyBytes = null;
//        System.out.println("Right before clientShim.execute");
        long startTime = System.currentTimeMillis();
        replyBytes = clientShim.execute(bmrq.getBytes());
        long endTime = System.currentTimeMillis();
        out.println("#req" + i + " " + startTime + " " + endTime + " " + id);
//        System.out.println("Right after clientShim.execute");
        BenchReply rep = new BenchReply(replyBytes);
        if (rep.isError()) {
            throw new RuntimeException("Write failed");
        }

        return rep.getData();
    }

    /**
     * Function called when the connection between the client and the
     * server is determined to be broken.
     **/
    public void brokenConnection() {
        throw new RuntimeException("Not implemented yet");
    }

    /**
     * Returns a reply received from the server
     **/
    public void returnReply(byte[] reply) {
        throw new RuntimeException("Not implemented yet");
    }

    /**
     * Considers the set of possible replies options.  Returns a
     * canonical version of those replies if it exists, returns null
     * otherwise
     **/
    public byte[] canonicalEntry(byte[][] options) {
        byte[] result = null;
        for (int i = 0; i < options.length; i++) {
            if (options[i] == null)
                continue;
            //System.out.println(options[i].length);
            if (result == null) {
                result = options[i];
                continue;
            }
            if (!Arrays.equals(result, options[i])) {
                System.out.println("Unmatched replies");
                return null;
            }
        }
        if (result == null)
            System.out.println("Result is null");
        return result;
    }

    public static String outputDir;

    public static int finishedCount = 0;
    public static Object finishedLock = new Object();
    public static int[] progress;

    public static void main(String[] args) {
        if (args.length != 13) {
            System.out.println("Usage BenchClient <membership> <idStart> <idGap> <idEnd> <dataNum> <locality> <dataSize> <requestCount> " +
                    "<spins> <reads> <writes> <prob> <outputDir>");
            return;
        }
        //NUM_OBJECTS, LOCALITY, REQUEST_SIZE, TOTAL_OPS, LOOP_NUM, FILEPATH, LOG_DIR
        String membership = args[0];
        int idStart = Integer.parseInt(args[1]);
        int idGap = Integer.parseInt(args[2]);
        int idEnd = Integer.parseInt(args[3]);
        int dataNum = Integer.parseInt(args[4]); //(1000) NUM_OBJECTS in start_client.py
        int locality = Integer.parseInt(args[5]); //(1000) LOCALITY "
        int dataSize = Integer.parseInt(args[6]); //(1000) REQUEST_SIZE "
        int requestCount = Integer.parseInt(args[7]); //(1000) TOTAL_OPS in start_client.py
        int spins = Integer.parseInt(args[8]); //LOOP_NUM in start_client.py
        int reads = Integer.parseInt(args[9]);
        int writes = Integer.parseInt(args[10]);
        int prob = Integer.parseInt(args[11]);
        outputDir = args[12];

        int noOfClients = 0;
        for (int id = idStart; id < idEnd; id += idGap) {
            noOfClients++;
        }

        progress = new int[noOfClients];
        int count = 0;
        for (int id = idStart; id < idEnd; id += idGap) {
            System.out.println("Starting BenchClientThread for id = " + id);
            new BenchClientThread(membership, id, dataNum, locality, dataSize, requestCount, spins, reads, writes, prob, count++).start();
        }
        synchronized (finishedLock) {
            while (finishedCount != noOfClients) {
                try {
                    finishedLock.wait();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        System.exit(0);
    }

    public static class BenchClientThread extends Thread {
        String membership;
        int id;
        int dataNum;
        int locality;
        int dataSize;
        int requestCount;
        int spins;
        int reads;
        int writes;
        int prob;
        int thread_id;

        public BenchClientThread(String membership, int id, int dataNum, int locality, int dataSize, int requestCount,
                                 int spins, int reads, int writes, int prob, int tid) {
            this.membership = membership;
            this.id = id;
            this.dataNum = dataNum;
            this.locality = locality;
            this.dataSize = dataSize;
            this.requestCount = requestCount;
            this.spins = spins;
            this.reads = reads;
            this.writes = writes;
            this.prob = prob;
            this.thread_id = tid;
        }

        public void run() {
            try {
                PrintStream out = new PrintStream(new FileOutputStream(new File(outputDir + File.separator + "client" + id + ".txt")));
                byte[] data = new byte[1024];
                data[0] = 1;
                out.println("#start " + System.currentTimeMillis());
                BenchClientMulti client = new BenchClientMulti(membership, id);
                Random rand = new Random();
                out.println("end_init " + System.currentTimeMillis());
                boolean clientNoConflict = client.getCSBN().getParameters().clientNoConflict;
                for (int i = 0; i < requestCount; i++) {
                    int index;
                    if (!clientNoConflict) {
                        index = rand.nextInt(locality);
                        index = index * (dataNum / locality);
                    } else {
                        index = id;
                    }
                    byte[] reply = client.send(index, spins, reads, writes, dataSize, prob, out, id, i);
                    progress[thread_id] = i;
                }
                out.println("end " + System.currentTimeMillis());
                synchronized (finishedLock) {
                    finishedCount++;
                    for (int i = 0; i < progress.length; i++) {
                        if (progress[i] == 0) {
                            progress[i] = requestCount;
                            finishedCount++;
                        }
                    }
                    finishedLock.notify();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    private byte[] getByteArrayFromIntegers(int cpuSpins, int objReads,
                                            int objWrites, int prob) {

        byte[] byteCpu = UnsignedTypes.intToBytes(cpuSpins);
        byte[] byteReads = UnsignedTypes.intToBytes(objReads);
        byte[] byteWrites = UnsignedTypes.intToBytes(objWrites);
        byte[] byteProb = UnsignedTypes.intToBytes(prob);

        int size = byteCpu.length + byteReads.length +
                byteWrites.length + byteProb.length;

        byte[] ret = new byte[size];

        int offset = 0;
        System.arraycopy(byteCpu, 0, ret, offset, byteCpu.length);
        offset += byteCpu.length;
        System.arraycopy(byteReads, 0, ret, offset, byteReads.length);
        offset += byteReads.length;
        System.arraycopy(byteWrites, 0, ret, offset, byteWrites.length);
        offset += byteWrites.length;
        System.arraycopy(byteProb, 0, ret, offset, byteProb.length);
        offset += byteProb.length;

        return ret;
    }
}
