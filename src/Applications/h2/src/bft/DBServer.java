import BFT.exec.*;
import merkle.MerkleTreeInstance;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DBServer implements RequestHandler, RequestFilter {
    private static String URL = "jdbc:h2:mem:testServer;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000;LOCK_MODE=3";
    private static String USER = "sa";
    private static String PASSWORD = "sa";

    private ReplyHandler replyHandler;
    private Connection conn;

    public DBServer(ReplyHandler replyHandler) throws SQLException {
        this.replyHandler = replyHandler;

        MerkleTreeInstance.add(URL);
        conn = DriverManager.getConnection(URL, USER, PASSWORD);
    }

    public void execRequest(byte[] request, RequestInfo info) {
        String statStr = new String(request);
        try {
            Statement stat = conn.createStatement();
            if (!statStr.startsWith("SELECT")) {
                stat.execute(statStr);
                replyHandler.result(new byte[1], info);
            } else {
                ResultSet rs = stat.executeQuery(statStr);
                StringBuilder builder = new StringBuilder();
                int row = 0;
                int columnNo = 3;
                while (rs.next()) {
                    builder.append("Row[").append(row).append("]:");
                    for (int i = 1; i <= columnNo; i++) {
                        builder.append(rs.getObject(i)).append(" ");
                    }
                    builder.append("\n");
                    row++;
                }
                replyHandler.result(builder.toString().getBytes(), info);
            }
        } catch (Exception e) {
            e.printStackTrace();
            replyHandler.result(new byte[2], info);
        }

    }

    public void execReadOnly(byte[] request, BFT.exec.RequestInfo info) {
        throw new RuntimeException("Not implemented yet");
    }

    public List<RequestKey> generateKeys(byte[] request) {
        return new ArrayList<RequestKey>();
    }


    public static void main(String args[]) throws Exception {
        if (args.length != 2)
            System.out.println("Usage BenchServer <membership> <id>");
        ExecBaseNode exec = new ExecBaseNode(args[0], Integer.parseInt(args[1]));
        DBServer main = new DBServer(exec);
        exec.start(main, main);
    }

}
