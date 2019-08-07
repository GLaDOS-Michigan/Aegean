import BFT.clientShim.ClientShimBaseNode;
import BFT.network.PassThroughNetworkQueue;
import BFT.network.netty.NettyTCPReceiver;
import BFT.network.netty.NettyTCPSender;
import BFT.util.Role;


public class DBClient {

    private static String CREATE = "CREATE TABLE BRANCHES"
            + "(BID INT NOT NULL PRIMARY KEY, BBALANCE INT, FILLER VARCHAR(88))";
    private static String INSERT = "INSERT INTO BRANCHES"
            + "(BID, BBALANCE, FILLER) VALUES(1, 2, 'haha')";
    private static String SELECT = "SELECT * FROM BRANCHES";
    private static String UPDATE = "UPDATE BRANCHES SET BBALANCE=3 WHERE BID=1";
    private ClientShimBaseNode clientShim;

    public DBClient(String membership, int id) {
        clientShim = new ClientShimBaseNode(membership, id);
        NettyTCPSender sendNet = new NettyTCPSender(clientShim.getMembership(), 1);
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

    public void execute(String statement) {
        byte[] replyBytes = clientShim.execute(statement.getBytes());
        System.out.println(replyBytes.length);
        if (statement.startsWith("SELECT")) {
            String result = new String(replyBytes);
            System.out.println(result);
        }
    }

    public static void main(String[] args) {
        if (args.length != 2)
            System.out.println("Usage BenchClient <membership> <id>");
        String membership = args[0];
        int id = Integer.parseInt(args[1]);
        DBClient client = new DBClient(membership, id);
        client.execute(CREATE);
        client.execute(INSERT);
        client.execute(UPDATE);
    /*for(int i=0;i<1000;i++){
	    String insert = INSERT.replace("1",Integer.toString(i));
	    System.out.println(insert);
	    client.execute(insert);
	    client.execute(SELECT);
	}*/
        System.exit(0);


    }

}
