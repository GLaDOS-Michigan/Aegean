// $Id: EchoClient.java 728 2011-09-11 23:44:18Z aclement $

package Applications.echo;


import BFT.clientShim.ClientGlueInterface;
import BFT.clientShim.ClientShimBaseNode;
import BFT.network.PassThroughNetworkQueue;
import BFT.network.netty2.ReceiverWrapper;
import BFT.network.netty2.SenderWrapper;
import BFT.util.Role;

import java.util.Random;

//import BFT.network.netty.NettyTCPReceiver;
//import BFT.network.netty.NettyTCPSender;

public class EchoClient implements ClientGlueInterface {

    public void returnReply(byte[] reply) {
        //	System.out.println("\t\t\t\t***Got a watch back: "+new String(reply));
    }


    public void brokenConnection() {
        System.out.println("\t\t\t\t***broken connection resulting from a" +
                " watch.  Echo doesnt care, but you might");
    }

    public byte[] canonicalEntry(byte[][] options) {
        for (int i = 0; i < options.length; i++) {
            if (options[i] != null) {
                return options[i];
            }
        }
        return null;
    }

    public static void main(String[] args) {

        if (args.length < 4) {
            System.out.println("Usage: java Applications.EchoClient <id> <subid> <config_file> <op_count> |<readratio>| |<requestsize>|");
            System.exit(0);
        }
        //Security.addProvider(new de.flexiprovider.core.FlexiCoreProvider());

        double readratio = 0.0;
        int reqsize = 0;
        int clientId = Integer.parseInt(args[0]);
        int subId = Integer.parseInt(args[1]);
        String membershipFile = args[2];
        int count = Integer.parseInt(args[3]);
        if (args.length >= 5)
            readratio = Float.parseFloat(args[4]);
        if (args.length >= 6)
            reqsize = Integer.parseInt(args[5]);


        BFT.clientShim.ClientShimBaseNode csbn = new ClientShimBaseNode(membershipFile, clientId, subId);
        //	csbn.setNetwork(new TCPNetwork(csbn));
        csbn.setGlue(new EchoClient());


        SenderWrapper sendNet = new SenderWrapper(csbn.getMembership(), 1);
        csbn.setNetwork(sendNet);

        Role[] roles = new Role[4];
        roles[0] = Role.ORDER;
        roles[1] = Role.EXEC;
        roles[2] = Role.FILTER;
        roles[3] = Role.CLIENT;

        PassThroughNetworkQueue ptnq = new PassThroughNetworkQueue(csbn);
        ReceiverWrapper receiveNet = new ReceiverWrapper(roles, csbn.getMembership(), ptnq, 1);

        System.out.println("count: " + count);
        System.err.println("#start " + System.currentTimeMillis());


        Random r = new Random();
        int readcount = 0;


        byte[] out = new byte[reqsize + 15 * (new Integer(0)).toString().getBytes().length];
        for (int i = 1; i <= count; i++) {
            long startTime = System.currentTimeMillis();
            byte[] tmp = null;
            byte[] intbyte = (new Integer(i)).toString().getBytes();
            for (int m = 0; m < intbyte.length; m++) {
                out[m] = intbyte[m];
            }
            //if (r.nextDouble() < readratio) {
            if (false) {
                tmp = csbn.executeReadOnlyRequest(out);
                long endTime = System.currentTimeMillis();
                System.err.println("#req" + i + " " + startTime + " " + endTime + " " + clientId);

                //System.err.println("#readreq"+i+"done " + clientId + " " + subId);
                readcount++;
            } else {
                tmp = csbn.execute(out);
                long endTime = System.currentTimeMillis();
                System.err.println("#req" + i + " " + startTime + " " + endTime + " " + clientId);
                //		System.out.println("latency:  "+(endTime-startTime));
                //System.err.println("#req"+i+"done " + clientId + " " + subId);
                //boolean res = true;
                //for (int k = 0; k < intbyte.length; k++)
                //	res = res && intbyte[k] == tmp[k];
                //if (!res)
                //	throw new RuntimeException("something is borked");
            }
        }
        System.err.println("end " + System.currentTimeMillis());
        System.out.println("reads: " + readcount + " total: " + count);
        //BFT.Parameters.println("finished the loop");
        System.exit(0);
    }

}
