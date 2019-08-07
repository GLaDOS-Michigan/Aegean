package Applications.web;

import BFT.clientShim.ClientGlueInterface;
import BFT.clientShim.ClientShimBaseNode;
import BFT.network.PassThroughNetworkQueue;
import BFT.network.netty.NettyTCPReceiver;
import BFT.network.netty.NettyTCPSender;
import BFT.util.Role;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Arrays;

public class WebClient implements ClientGlueInterface {

    private ClientShimBaseNode clientShim;

    public WebClient(String membership, int id) {
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

    public byte[] getPage(byte[] request) {
        byte[] replyBytes = null;
        replyBytes = clientShim.execute(request);

        return replyBytes;
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
        if (args.length != 6) {
            System.out.println("Usage Client <membership> <idStart> <idGap> <idEnd> <requestCount> <outputDir>");
            return;
        }
        String membership = args[0];
        int idStart = Integer.parseInt(args[1]);
        int idGap = Integer.parseInt(args[2]);
        int idEnd = Integer.parseInt(args[3]);
        int requestCount = Integer.parseInt(args[4]);
        outputDir = args[5];

        int noOfClients = 0;
        for (int id = idStart; id < idEnd; id += idGap) {
            noOfClients++;
        }

        progress = new int[noOfClients];
        int count = 0;
        for (int id = idStart; id < idEnd; id += idGap) {
            new WebClientThread(membership, id, requestCount, count++).start();
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

    private static final byte[] GET = "GET /test/index.html\r\n".getBytes();

    public static class WebClientThread extends Thread {
        String membership;
        int id;
        int requestCount;
        int thread_id;

        public WebClientThread(String membership, int id, int requestCount, int tid) {
            this.membership = membership;
            this.id = id;
            this.requestCount = requestCount;
            this.thread_id = tid;
        }

        public void run() {
            try {
                PrintStream out = new PrintStream(new FileOutputStream(new File(outputDir + File.separator + "client" + id + ".txt")));
                out.println("#start " + System.currentTimeMillis());
                WebClient client = new WebClient(membership, id);
                out.println("end_init " + System.currentTimeMillis());
                long startTime;
                long endTime;
                for (int i = 0; i < requestCount; i++) {
                    startTime = System.currentTimeMillis();
                    byte[] reply = client.getPage(GET);
                    endTime = System.currentTimeMillis();
                    out.println("#req" + i + " " + startTime + " " + endTime + " " + id);
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
}
