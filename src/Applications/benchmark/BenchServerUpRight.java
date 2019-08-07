// $Id: BenchServerUpRight.java 676 2011-03-10 03:05:54Z yangwang $
package Applications.benchmark;

import BFT.Parameters;
import BFT.clientShim.ClientShimBaseNode;
import BFT.exec.RequestFilter;
import BFT.exec.RequestKey;
import BFT.generalcp.*;
import BFT.util.UnsignedTypes;
import merkle.IndexedThread;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

//The server class must implement the two interfaces
public class BenchServerUpRight implements AppCPInterface, RequestFilter {

    byte[][] data;
    int[] spinArray;
    private int dataSize;
    private CPAppInterface generalCP;
    public BenchDigest digest;
    private String syncDir;
    private long lastSeqNo = 0;
    private Parameters parameters;
    transient private ClientShimBaseNode[] csbns = null;
    transient private double backendRatio;
    transient private BenchClientMulti[] clients = null;
    transient private Random generator = new Random(0);
    transient int tmp = 0;

    public BenchServerUpRight(Parameters param, int dataNum, int dataSize, String syncDir) throws IOException {
        parameters = param;
        this.dataSize = dataSize;
        this.syncDir = syncDir;

        //MerkleTreeInstance.init(2, dataNum, 5, false);
        data = new byte[dataNum][];
        spinArray = new int[parameters.numberOfClients];
        for (int i = 0; i < dataNum; i++) {
            data[i] = new byte[dataSize];
        }

    }

    public void setGenCP(CPAppInterface cp) {
        generalCP = cp;
    }

    public long doSpin(int spins) {
        long time = ((long) spins) * 1000;
        long start = System.nanoTime();
        long end = System.nanoTime();
        while (time > (end - start)) {
            end = System.nanoTime();
        }
//        System.out.println("spins: " + spins + ", time: " + time);
        return -1337L;
    }

    public void execAsync(byte[] request, RequestInfo info) {
        long startTime = System.currentTimeMillis();
        lastSeqNo = info.getSeqNo();
        BenchmarkRequest req = new BenchmarkRequest(request);
        BenchReply rep = null;

        int spins = req.getSpins();
        int reads = req.getReads();
        int writes = req.getWrites();
        int prob = req.getProb();

        int client = info.getClientId();
        int readOffset = req.getStartIndex();
        int writeOffset = req.getStartIndex();
        byte[] randomBytes = new byte[1024];


//        spinArray[client] = 0;
//        for (int i = 0; i < spins; i++) {
//            for (int j = 0; j < 10000; j++) {
//                spinArray[client]++;
//            }
//            Thread.yield();
//        }
//        System.err.println("Spins: " + spins);
        int backendSpins = 0;
        //int num = 0;
        //if(csbns != null)
        //{
        //    Random random = csbns[0].random;
        //    num  = (int) (random.nextInt(3));
            //System.err.println("noOfNestedRequests: " + noOfNestedRequests);
        //}
        
        if(csbns != null) {
//	    System.out.println("backendRatio: " + backendRatio + ", spins: " + spins);
	    int middleSpins =(int) ((spins * 2.0) / (1+backendRatio));
            backendSpins = (2 * spins - middleSpins);
            spins = middleSpins;
//	    System.out.println("middleSpins: " + middleSpins + ", backendSpins: " + backendSpins);
        }
        BenchmarkRequest nestedRequest = new BenchmarkRequest(req.getStartIndex(), (int) (backendSpins),
                req.getReads(), req.getWrites(), req.getData().length, req.getProb());
        if(csbns == null)
        {
            doSpin(spins);
            //System.err.println("do spins: " + spins);
        }
        else
        {
                doSpin(spins/2);
            //System.err.println("do spin1: " + spins/2);
        }
        int index;

        byte[] writeBytes = new byte[dataSize];
        for (int i = 0; i < writes; i++) {
            index = (writeOffset++) % data.length;
            synchronized (data[index]) {
                System.arraycopy(writeBytes, 0, data[index], 0, dataSize);
                data[index][0] = (byte) (info.getClientId() >> 8);
                data[index][1] = (byte) (info.getClientId() & 255);
            }
            rep = new BenchReply(false, null);
        }

        byte[] reply = new byte[dataSize];
        for (int i = 0; i < reads; i++) {
            index = (readOffset++) % data.length;

            synchronized (data[index]) {
                System.arraycopy(data[index], 0, reply, 0, dataSize);
            }
        }

//        System.err.println("middle or backend");
        if(csbns != null) {
//            System.err.println("nested request is created");
            byte[] replyFromSecondService = null;
//            System.err.println("about to send nested request in BSUR");
            long nestedStart = System.nanoTime();
            replyFromSecondService = generalCP.execNestedRequest(nestedRequest.getBytes(), csbns);
            doSpin(spins/2);
            //System.err.println("do spin2: " + spins/2);

            long nestedEnd = System.nanoTime();
//            System.err.println("nestedTime: " + (nestedEnd-nestedStart)/1000);

//            System.out.println("reply from second service");
            generalCP.execDone(replyFromSecondService, info);
        }
        else
        {
            rep = new BenchReply(false, reply);
            generalCP.execDone(rep.getBytes(), info);
        }
//        System.err.println("execDone returned");
    }

    public void execReadonly(byte[] request, int clientId, long requestId) {
        throw new RuntimeException("Not implemented");
    }

    // Write all states into a snapshot file
    public void sync() {
        try {
            if (!parameters.uprightInMemory) {
                File syncFile = new File(this.syncDir + "/ht_sync_" + lastSeqNo);
                ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(syncFile));
                oos.writeLong(lastSeqNo);
                oos.writeInt(dataSize);
                oos.writeInt(data.length);
                for (int i = 0; i < data.length; i++)
                    oos.write(data[i]);
                oos.close();
            } else {
                File syncFile = new File(this.syncDir + "/ht_sync_" + lastSeqNo);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos);
                oos.writeLong(lastSeqNo);
                oos.writeInt(dataSize);
                oos.writeInt(data.length);
                for (int i = 0; i < data.length; i++)
                    oos.write(data[i]);
                oos.close();
                MemoryFS.write(syncFile.getAbsolutePath(), bos.toByteArray());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        generalCP.syncDone(this.syncDir + "/ht_sync_" + lastSeqNo);
    }

    // Load all states from a snapshot file
    public synchronized void loadSnapshot(String fileName) {
        try {
            if (!parameters.uprightInMemory) {
                ObjectInputStream ois = new ObjectInputStream(new FileInputStream(fileName));
                lastSeqNo = ois.readLong();
                dataSize = ois.readInt();
                int dataNum = ois.readInt();
                data = new byte[dataNum][];
                for (int i = 0; i < dataNum; i++) {
                    data[i] = new byte[dataSize];
                    ois.readFully(data[i]);
                }
                ois.close();
            } else {
                File f = new File(fileName);
                ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(MemoryFS.read(f.getAbsolutePath())));
                lastSeqNo = ois.readLong();
                dataSize = ois.readInt();
                int dataNum = ois.readInt();
                data = new byte[dataNum][];
                for (int i = 0; i < dataNum; i++) {
                    data[i] = new byte[dataSize];
                    ois.readFully(data[i]);
                }
                ois.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public List<RequestKey> generateKeys(byte[] request) {
        /* This is the old benchmark
        BenchRequest req = (BenchRequest) (Convert.bytesToObject(request));
        ArrayList<Object> ret=new ArrayList<Object>(1);
        ret.add(req.getIndex());
        return ret;
         */
        //System.out.println("generate keys");
        BenchmarkRequest req = new BenchmarkRequest(request);
        int reads = req.getReads();
        int writes = req.getWrites();
        int ops = Math.max(reads, writes);

        ArrayList<RequestKey> ret = new ArrayList<RequestKey>(ops);
        for (int i = 0; i < ops; i++) {
            ret.add(new RequestKey(false, req.getStartIndex() + i));
        }
        return ret;

    }

    public static void main(String args[]) throws Exception {
        if (args.length < 6) {
            System.err.println("Usage BenchServerUpRight <membership> <id> <dataNum> <dataSize> <log_path> <snapshot_path> [<clientMembership> <clientId> <clientSubId> [<backendRatio]]");
            return;
        }

        String membershipFile = args[0];
        int id = Integer.parseInt(args[1]);
        int dataNum = Integer.parseInt(args[2]);
        int dataSize = Integer.parseInt(args[3]);
        String logPath = args[4];
        String snapshotPath = args[5];
        System.out.println("Membership: " + membershipFile + ",id " + id + ",dataNum: " + dataNum + ",dataSize " + dataSize + ", logPath: " + logPath + ",snapshotPath: " + snapshotPath);
        boolean isMiddle = args.length > 6;

        GeneralCP generalCP = new GeneralCP(id, membershipFile, logPath, snapshotPath, isMiddle);
        BenchServerUpRight main = new BenchServerUpRight(generalCP.getShimParameters(), dataNum, dataSize, snapshotPath);
        main.setGenCP(generalCP);
        generalCP.setupApplication(main);


        if(args.length > 6) {
            System.err.println("Really About to setup the client: " + args.length);
            String clientMembership = args[6];
            int clientId = Integer.parseInt(args[7]);
            int subId = Integer.parseInt(args[8]);
            double backendRatio = Double.parseDouble(args[9]);//TODO use
            main.setupClient(clientMembership, clientId, subId, backendRatio);
            System.err.println("clientMembership " + clientMembership + ",clientId: " + clientId + ", subId: "  + subId + " ,backendRatio: " + backendRatio);
        }

    }

    private void setupClient(String clientMembership, int clientId, int subId, double backendRatio) {
        int numClients = 1;
        if(parameters.pipelinedSequentialExecution)
        {
            numClients = parameters.noOfThreads;
        }

        clients = new BenchClientMulti[numClients];
        csbns = new ClientShimBaseNode[numClients];
        this.backendRatio = backendRatio;

        for (int i = 0; i < numClients; i++) {
            clients[i] = new BenchClientMulti(clientMembership, clientId + i, subId);
            csbns[i] = clients[i].getCSBN();
        }
        System.err.println(numClients + " clients and csbns are set up");
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
