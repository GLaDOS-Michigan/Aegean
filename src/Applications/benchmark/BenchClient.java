package Applications.benchmark;

import BFT.clientShim.ClientGlueInterface;
import BFT.clientShim.ClientShimBaseNode;
import BFT.network.PassThroughNetworkQueue;
import BFT.network.netty.NettyTCPReceiver;
import BFT.network.netty.NettyTCPSender;
import BFT.util.Role;
import BFT.util.UnsignedTypes;

import java.util.Arrays;
import java.util.Random;

public class BenchClient implements ClientGlueInterface {

    private ClientShimBaseNode clientShim;

    public BenchClient(String membership, int id) {
        clientShim = new ClientShimBaseNode(membership, id);
        clientShim.setGlue(this);
        NettyTCPSender sendNet = new NettyTCPSender(clientShim.getParameters(), clientShim.getMembership(), 1);
        clientShim.setNetwork(sendNet);

        Role[] roles = new Role[3];
        roles[0] = Role.ORDER;
        roles[1] = Role.EXEC;
        roles[2] = Role.FILTER;

        PassThroughNetworkQueue ptnq = new PassThroughNetworkQueue(clientShim);
        NettyTCPReceiver receiveNet = new NettyTCPReceiver(roles,
                clientShim.getMembership(), ptnq, 1);

        clientShim.start();
    }

    public byte[] send(int index, int cpu, int reads, int writes, int dataSize, int prob) {
        BenchmarkRequest bmrq = new BenchmarkRequest(index, cpu, reads, writes, dataSize, prob);
        byte[] replyBytes = null;
//        System.out.println("Right before clientShim.execute in BenchClient");
        replyBytes = clientShim.execute(bmrq.getBytes());
//        System.out.println("Right after clientShim.execute in BenchClient");
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

    public static void main(String[] args) {
        if (args.length != 10) {
            System.out.println("Usage BenchClient <membership> <id> <dataNum> <locality> <dataSize> <requestCount> " +
                    "<spins> <reads> <writes> <prob>");
            return;
        }
        String membership = args[0];
        int id = Integer.parseInt(args[1]);
        int dataNum = Integer.parseInt(args[2]);
        int locality = Integer.parseInt(args[3]);
        int dataSize = Integer.parseInt(args[4]);
        int requestCount = Integer.parseInt(args[5]);
        int spins = Integer.parseInt(args[6]);
        int reads = Integer.parseInt(args[7]);
        int writes = Integer.parseInt(args[8]);
        int prob = Integer.parseInt(args[9]);

        byte[] data = new byte[1024];
        data[0] = 1;
        BenchClient client = new BenchClient(membership, id);
        Random rand = new Random();
        System.err.println("#start " + System.currentTimeMillis());
        long startTime;
        long endTime;
        for (int i = 0; i < requestCount; i++) {
            int index = rand.nextInt(locality);
            index = index * (dataNum / locality);
            startTime = System.currentTimeMillis();
            //client.write(index.data);
            byte[] reply = client.send(index, spins, reads, writes, dataSize, prob);
            //byte[] reply = client.read(index);
            endTime = System.currentTimeMillis();
            System.err.println("#req" + i + " " + startTime + " " + endTime + " " + id);
        }
        System.err.println("end " + System.currentTimeMillis());
        System.exit(0);
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
