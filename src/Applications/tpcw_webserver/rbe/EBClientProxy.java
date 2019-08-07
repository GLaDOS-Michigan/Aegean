package Applications.tpcw_webserver.rbe;

import BFT.clientShim.ClientGlueInterface;
import BFT.clientShim.ClientShimBaseNode;
import BFT.network.PassThroughNetworkQueue;
import BFT.network.netty.NettyTCPReceiver;
import BFT.network.netty.NettyTCPSender;
import BFT.util.Role;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.util.Arrays;

/**
 * @author lcosvse
 */
public class EBClientProxy implements EBClientProxyInterface, ClientGlueInterface {
    int id;
    int requestCounter;
    PrintStream outStream = null;

    public EBClientProxy(String membership, int id, int subid) {
        this.id = id;
        requestCounter = 0;
        this.clientShim = new ClientShimBaseNode(membership, id, subid);
        this.clientShim.setGlue(this);
        NettyTCPSender sendNet = new NettyTCPSender(clientShim.getParameters(), clientShim.getMembership(), 1);
        clientShim.setNetwork(sendNet);

        Role[] roles = new Role[3];
        roles[0] = Role.VERIFIER;
        roles[1] = Role.EXEC;
        roles[2] = Role.FILTER;

        PassThroughNetworkQueue ptnq = new PassThroughNetworkQueue(clientShim);
        NettyTCPReceiver receiveNet = new NettyTCPReceiver(roles, clientShim.getMembership(), ptnq, 1);

        clientShim.start();
    }

    public byte[] getHTMLText(String req) {
        System.out.println("Right before clientShim.execute");
        long startTime = System.currentTimeMillis();
        byte[] replyBytes = this.clientShim.execute(req.getBytes());
        long endTime = System.currentTimeMillis();
        outStream.println("#req" + requestCounter + " " + startTime + " " + endTime + " " + id);
        requestCounter++;
        System.out.println("Right after clientShim.execute");
        return replyBytes;
    }

    public int getImgs(URL rd) {
        // TODO TODO TODO
        throw new RuntimeException("Not yet implemented!!!");
    }

    @Override
    public void brokenConnection() {
        throw new RuntimeException("EBClientProxy.brokenConnection() is not yet implemented.");
    }

    @Override
    public void returnReply(byte[] reply) {
        throw new RuntimeException("EBClientProxy.returnReply() is not yet implemented.");
    }

    public void setOutStream(PrintStream outStream) {
        this.outStream = outStream;
    }

    @Override
    public int getCompletedRequestCount() {
        return requestCounter;
    }

    @Override
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
                System.err.println("Unmatched replies");
                return null;
            }
        }
        if (result == null)
            System.err.println("Result is null");
        return result;
    }

    private ClientShimBaseNode clientShim;
}
