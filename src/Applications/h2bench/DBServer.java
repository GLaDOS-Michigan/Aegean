package Applications.h2bench;

import BFT.Parameters;
import BFT.exec.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.sql.*;
import java.util.List;
import java.util.Random;

public class DBServer implements RequestHandler, RequestFilter {
    private static String URL = "jdbc:h2:mem:testServer;LOCK_TIMEOUT=10000;QUERY_TIMEOUT=10000;DB_CLOSE_DELAY=-1;LOCK_MODE=3;MULTI_THREADED=1";
    private static String USER = "sa";
    private static String PASSWORD = "sa";


    private static String CREATE = "CREATE TABLE BRANCHES"
            + "(BID INT NOT NULL PRIMARY KEY, BBALANCE INT, FILLER VARCHAR(88))";
    private static String INSERT = "INSERT INTO BRANCHES"
            + "(BID, BBALANCE, FILLER) VALUES(1, 2, 'haha')";
    private static String SELECT = "SELECT * FROM BRANCHES";
    private static String UPDATE = "UPDATE BRANCHES SET BBALANCE=3 WHERE BID=1";


    private ReplyHandler replyHandler;
    private Connection[] connections;
    //PreparedStatement []selects;
    //PreparedStatement []updates;

    private final int noOfTables;
    private final int noOfRows;
    Random rand = new Random(123456);

    private PreparedStatement[][] selects;
    private Parameters parameters;

    public DBServer(Parameters param, ReplyHandler replyHandler, int noOfTables, int noOfRows) throws SQLException {
        parameters = param;
        this.replyHandler = replyHandler;
        this.noOfTables = noOfTables;
        this.noOfRows = noOfRows;

        connections = new Connection[parameters.numberOfClients];
        selects = new PreparedStatement[parameters.numberOfClients][];
        //selects = new PreparedStatement[BFT.Parameters.numberOfClients];
        //updates = new PreparedStatement[BFT.Parameters.numberOfClients];

        createTables();


        for (int i = 0; i < connections.length; i++) {
            connections[i] = DriverManager.getConnection(URL, USER, PASSWORD);
            connections[i].setAutoCommit(false);
            try {
                selects[i] = new PreparedStatement[noOfTables];
                for (int j = 0; j < noOfTables; j++) {
                    selects[i][j] = connections[i].prepareStatement("SELECT BID from BRANCHES" + j + " where BBALANCE=?");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            //selects[i] = connections[i].prepareStatement("SELECT BID from BRANCHES"+i%noOfTables+" where BBALANCE=(SELECT MAX(BBALANCE) FROM BRANCHES" + i%noOfTables + ")");
            //updates[i] = connections[i].prepareStatement("UPDATE BRANCHES"+i%noOfTables+" SET BBALANCE=? WHERE BID=?" );
        }
    }

    private void createTables() throws SQLException {
        Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
        for (int i = 0; i < noOfTables; i++) {
            PreparedStatement stat = conn.prepareStatement("CREATE TABLE BRANCHES" + i
                    + "(BID INT NOT NULL PRIMARY KEY, BBALANCE INT, FILLER VARCHAR(88))");
            stat.execute();
            PreparedStatement insert = conn.prepareStatement("INSERT INTO BRANCHES" + i
                    + "(BID, BBALANCE, FILLER) VALUES(?, ?, ?)");
            for (int j = 0; j < noOfRows; j++) {
                insert.setInt(1, j);
                insert.setInt(2, noOfRows - j);
                insert.setString(3, "hahahahahahahahahaha");
                insert.execute();
            }

        }
    }

    public void execRequest(byte[] request, RequestInfo info) {
        try {
            long start = System.currentTimeMillis();
            Random rand = new Random(info.getRandom());
            int bid = 0;
            Connection conn = connections[info.getClientId()];
            int i = request[0];
            for (int k = 0; k < 20; k++) {
                PreparedStatement select = selects[info.getClientId()][i];
                select.setInt(1, rand.nextInt(noOfRows));
                ResultSet rs = select.executeQuery();
                conn.commit();
            }
            //rs.next();
            //int l = rs.getInt(1);

	    /*int totalBid = 0;
        while (rs.next())
            {
            	totalBid += rs.getInt("BBALANCE");
            }*/

	    /*PreparedStatement select = conn.prepareStatement("SELECT BID from BRANCHES"+i+" where BBALANCE=(SELECT MAX(BBALANCE) FROM BRANCHES" + i + ")");
	    ResultSet rs = select.executeQuery();
	    rs.first();
	    bid = rs.getInt("BID");
	    /*PreparedStatement update = conn.prepareStatement("UPDATE BRANCHES"+i+" SET BBALANCE=? WHERE BID=?" );
	    update.setInt(1, rand.nextInt());
	    update.setInt(2, rand.nextInt(noOfRows));
	    update.execute();*/
            //conn.commit();

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeInt(1);
            oos.flush();
            replyHandler.result(bos.toByteArray(), info);
            //Thread.yield();
            //System.out.println("time="+(System.currentTimeMillis()-start));
        } catch (SQLException e) {
            e.printStackTrace();
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos);
                oos.writeInt(-1);
                oos.flush();
                replyHandler.result(bos.toByteArray(), info);
            } catch (IOException e2) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void execReadOnly(byte[] request, RequestInfo info) {
        throw new RuntimeException("Not implemented yet");
    }

    public List<RequestKey> generateKeys(byte[] request) {
//	if(true) return null;
        //ArrayList<RequestKey> ret= new ArrayList<RequestKey>();
        //ret.add(new RequestKey(false, request[0]));
        return null;//ret;
    }


    public static void main(String args[]) throws Exception {
        if (args.length != 4) {
            System.out.println("Usage DBServer <membership> <id> <noOfTable> <noOfRows>");
            return;
        }
        ExecBaseNode exec = new ExecBaseNode(args[0], Integer.parseInt(args[1]));
        DBServer main = new DBServer(exec.getParameters(), exec, Integer.parseInt(args[2]), Integer.parseInt(args[3]));
        exec.start(main, main);
    }

}
