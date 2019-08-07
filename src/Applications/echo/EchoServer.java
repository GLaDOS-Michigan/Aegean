// $Id: EchoServer.java 728 2011-09-11 23:44:18Z aclement $

package Applications.echo;

import BFT.Parameters;
import BFT.clientShim.ClientShimBaseNode;
import BFT.messages.CommandBatch;
import BFT.messages.Entry;
import BFT.messages.NonDeterminism;
import BFT.network.PassThroughNetworkQueue;
import BFT.network.netty2.ReceiverWrapper;
import BFT.network.netty2.SenderWrapper;
import BFT.serverShim.GlueShimInterface;
import BFT.serverShim.ServerShimInterface;
import BFT.serverShim.ShimBaseNode;
import BFT.util.Role;
import util.ThreadMonitor;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

//import BFT.network.netty.NettyTCPReceiver;
//import BFT.network.netty.NettyTCPSender;

public class EchoServer implements GlueShimInterface {

    int lossrate;
    long nextseqno;
    int count;
    Parameters parameters;

    // work queue for thread
    ArrayBlockingQueue<Runnable> pipeQueue = null;
    ThreadMonitor threadMonitor = null;
    ThreadPoolExecutor executor = null;

    public EchoServer(Parameters param) {
        parameters = param;
        nextseqno = 0;
        count = 0;
        pipeQueue = new ArrayBlockingQueue<Runnable>(100);
        executor = new ThreadPoolExecutor(param.getThreadPoolSize(), param.getThreadPoolSize(),
                60, TimeUnit.SECONDS, pipeQueue);
        threadMonitor = new ThreadMonitor(param.getThreadPoolSize());
        System.out.println("threadpool size " + param.getThreadPoolSize());
    }

    ServerShimInterface shim;
    EchoClient client;
    ClientShimBaseNode csbn;

    class PipeRequest implements Runnable {
        int id;
        byte[] req;
        byte[][] rep;
        long seqNo;
        ThreadMonitor tm;

        // type indicates how many execution parts of this request
        public PipeRequest(byte[] request, long seq, int tid, byte[][] replies, ThreadMonitor m) {
            req = request;
            rep = replies;
            id = tid;
            tm = m;
            seqNo = seq;
        }

        public void run() {

            tm.waitMyTurn(seqNo);

            // application logic
            System.out.println("First part execution: " + seqNo);
            byte[] res = csbn.pipedExecute(req, tm, seqNo);
            if (!tm.isMyTurn(seqNo)) {
                // explode loudly     
                System.out.println("pipeRequest: order break!");
            }


            // more application logic
            // more application logic
            System.out.println("Second part execution: " + seqNo);
            res = csbn.pipedExecute(req, tm, seqNo);

            if (!tm.isMyTurn(seqNo)) {
                // explode loudly
                System.out.println("pipeRequest: order break!");
            }

            //finish
            rep[id] = res;

            //add id to idle threads, special case for number of threads == batch size
            tm.skipTurns(seqNo);
            System.out.println("thread ends!");
        }
    }

    public void setShim(ServerShimInterface sh) {
        shim = sh;
    }

    public synchronized void exec(CommandBatch batch, long seqNo,
                                  NonDeterminism nd, boolean takeCP) {
        System.out.println("Sequence number: " + seqNo + " / " + nextseqno);
        nextseqno++;

        Entry[] entries = batch.getEntries();
        byte[][] replies = new byte[entries.length][];
        long start = executor.getCompletedTaskCount();
        threadMonitor.addRequest(entries.length);
        System.out.println("new Batch, nextseqno:" + nextseqno + " batchlength:" + entries.length
                + " threadpool " + executor.getActiveCount());
        for (int i = 0; i < entries.length; i++) {
            byte[] tmp2 = null;
            System.out.println(entries[i].getClient() + ":" + entries[i].getRequestId());

            if (csbn == null) {
                String str = new String(entries[i].getCommand());
                str = count + " " + str;
                shim.result(str.getBytes(), (int) entries[i].getClient(),
                        entries[i].getRequestId(), seqNo, true);
            } else {
                executor.execute(new PipeRequest(entries[i].getCommand(), count, i, replies, threadMonitor));
            }

            if (entries[i].getRequestId() % 100 == 0) {
                String tmp = "watch every 100: " +
                        (entries[i].getRequestId() / 100);
                //			System.out.println("sending a watch");
                shim.result(tmp.getBytes(),
                        (int) (entries[i].getClient()),
                        entries[i].getRequestId(),
                        seqNo,
                        false);
            }
            count++;
        }
        if (csbn != null) {
            while (executor.getCompletedTaskCount() - start != entries.length) {
                try {
                    Thread.sleep(50);
                } catch (Exception e) {
                    throw new RuntimeException("Interuppt?");
                }
            }
            for (int i = 0; i < entries.length; i++)
                shim.result(replies[i], (int) (entries[i].getClient()),
                        entries[i].getRequestId(), seqNo, true);
        }
        if (takeCP) {
            //			shim.pause();
            //shim.isPaused();
            shim.returnCP(fetchAppState(seqNo), seqNo);
            // 			Forker f = new Forker(shim, seqNo);
            // 			Thread t = new Thread(f);
            // 			t.start();
            // 			f.waitForFork();
        }
    }

    public void execReadOnly(int client, long reqid, byte[] op) {
        //Debug.println("executing read only request: "+reqid+" from "+client);
        String tmp = "current seqno is " + nextseqno;

        //block till second server return
        if (csbn != null) {
            byte[] out = (new Integer(1)).toString().getBytes();
            byte[] tmp2 = csbn.executeReadOnlyRequest(out);
            //System.out.println("middle server received read");
        }

        shim.readOnlyResult(tmp.getBytes(),
                client,
                reqid);
    }

    public void loadCP(byte[] appCPToken, long seqNo) {
        //nextseqno = seqNo;
        if (csbn == null) {
            return;
        }
        byte[] tmp = new byte[4];
        int offset = 0;
        for (int i = 0; i < tmp.length; i++, offset++) {
            tmp[i] = appCPToken[offset];
        }
        nextseqno = util.UnsignedTypes.bytesToLong(tmp);
        for (int i = 0; i < tmp.length; i++, offset++) {
            tmp[i] = appCPToken[offset];
        }
        csbn.loadState(tmp);
    }

    public void releaseCP(byte[] appCPToken) {
        // again, a no-op
    }

    public void fetchState(byte[] stateToken) {
        // noop due to no state in the app
    }

    public byte[] fetchAppState(long seq) {
        if (csbn == null) {
            return new byte[8];
        }
        //FIXME: actually should use the nextseqno 
        //      but here they are the same
        byte[] tmp = util.UnsignedTypes.longToBytes(seq);
        byte[] tmp2 = csbn.fetchState(seq);
        byte[] tmp3 = new byte[8];
        int offset = 0;
        for (int i = 0; i < tmp.length; i++, offset++) {
            tmp3[offset] = tmp[i];
        }
        for (int i = 0; i < tmp2.length; i++, offset++) {
            tmp3[offset] = tmp2[i];
        }
        return tmp3;
    }

    public void loadState(byte[] stateToken, byte[] state) {
        // noop
    }

    public void setupClient(String membershipFile, int clientId, int subId) {
        //Here is test code for setting up client configure
        csbn = new ClientShimBaseNode(membershipFile, clientId, subId);

        //	csbn.setNetwork(new TCPNetwork(csbn));
        client = new EchoClient();
        csbn.setGlue(client);
        //csbn.setFlagForPara(1);

        SenderWrapper sendNet = new SenderWrapper(csbn.getMembership(), 1);
        csbn.setNetwork(sendNet);

        Role[] roles = new Role[4];
        roles[0] = Role.ORDER;
        roles[1] = Role.EXEC;
        roles[2] = Role.FILTER;
        roles[3] = Role.CLIENT;

        PassThroughNetworkQueue ptnq = new PassThroughNetworkQueue(csbn);
        ReceiverWrapper receiveNet = new ReceiverWrapper(roles, csbn.getMembership(), ptnq, 1);
        // Test code over
    }

    public static void main(String[] args) {

        //if(args.length != 2) {
        //	System.out.println("Usage: java Applications.EchoServer <id> <config_file> ");
        //	System.exit(0);
        //}
        //Security.addProvider(new de.flexiprovider.core.FlexiCoreProvider());
        ShimBaseNode sbn = new ShimBaseNode(args[1],
                Integer.parseInt(args[0]),
                new byte[0]);

        EchoServer es = new EchoServer(sbn.getParameters());
        if (args.length >= 5) {
            String membershipFile = args[2];
            int clientId = Integer.parseInt(args[3]);
            int subId = Integer.parseInt(args[4]);
            es.setupClient(membershipFile, clientId, subId);
            //BFT.ParametersForBackup.copy();
        }

        //Using configure file setting up client
        sbn.setGlue(es);
        es.setShim(sbn);

        SenderWrapper sendNet = new SenderWrapper(sbn.getMembership(), 1);
        sbn.setNetwork(sendNet);

        Role[] roles = new Role[4];
        roles[0] = Role.ORDER;
        roles[1] = Role.CLIENT;
        roles[2] = Role.FILTER;
        roles[3] = Role.EXEC;

        PassThroughNetworkQueue ptnq = new PassThroughNetworkQueue(sbn);
        ReceiverWrapper receiveNet = new ReceiverWrapper(roles,
                sbn.getMembership(), ptnq, 2);

        sbn.start();
    }

}
