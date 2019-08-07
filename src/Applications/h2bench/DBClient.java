package Applications.h2bench;

import BFT.clientShim.ClientShimBaseNode;
import BFT.network.PassThroughNetworkQueue;
import BFT.network.netty.NettyTCPReceiver;
import BFT.network.netty.NettyTCPSender;
import BFT.util.Role;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Random;


public class DBClient {

    private static String CREATE = "CREATE TABLE BRANCHES"
            + "(BID INT NOT NULL PRIMARY KEY, BBALANCE INT, FILLER VARCHAR(88))";
    private static String INSERT = "INSERT INTO BRANCHES"
            + "(BID, BBALANCE, FILLER) VALUES(1, 2, 'haha')";
    private static String SELECT = "SELECT * FROM BRANCHES";
    private static String UPDATE = "UPDATE BRANCHES SET BBALANCE=3 WHERE BID=1";


    private Random rand;
    private ClientShimBaseNode clientShim;
    private int noOfTables;

    public DBClient(String membership, int id, int noOfTables) {
        this.noOfTables = noOfTables;
        rand = new Random(id);
        clientShim = new ClientShimBaseNode(membership, id);
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

    public void execute() {
        byte[] req = new byte[1];
        req[0] = (byte) rand.nextInt(noOfTables);
        byte[] replyBytes = clientShim.execute(req);
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(replyBytes);
            ObjectInputStream ois = new ObjectInputStream(bis);
            ois.readInt();
            //System.out.println(ois.readInt());
        } catch (IOException e) {
            e.printStackTrace();
        }
    /*System.out.println(replyBytes.length);
        if(statement.startsWith("SELECT")){
            String result=new String(replyBytes);
	    System.out.println(result); 
        }*/
    }

    public static void main(String[] args) {
        if (args.length != 4) {
            System.out.println("Usage BenchClient <membership> <id> <noOfTables> <round>");
            return;
        }
        String membership = args[0];
        int id = Integer.parseInt(args[1]);
        int noOfTables = Integer.parseInt(args[2]);
        int round = Integer.parseInt(args[3]);

        DBClient client = new DBClient(membership, id, noOfTables);
        for (int i = 0; i < round; i++) {
            long startTime = System.currentTimeMillis();
            client.execute();
            long endTime = System.currentTimeMillis();
            System.out.println("#req" + i + " " + startTime + " " + endTime + " " + id);
        }
        long endTime = System.currentTimeMillis();

        System.exit(0);


    }

}
