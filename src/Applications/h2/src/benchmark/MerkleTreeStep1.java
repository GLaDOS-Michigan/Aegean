import merkle.MerkleTreeInstance;

import java.sql.*;

public class MerkleTreeStep1 {

    private static String URL = "jdbc:h2:mem:testServer;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000;LOCK_MODE=3";
    private static String USER = "sa";
    private static String PASSWORD = "sa";

    private static String CREATE = "CREATE TABLE BRANCHES"
            + "(BID INT NOT NULL PRIMARY KEY, BBALANCE INT, FILLER VARCHAR(88))";
    private static String SELECT = "SELECT * FROM BRANCHES";
    private static String INSERT = "INSERT INTO BRANCHES"
            + "(BID, BBALANCE, FILLER) VALUES(1, 0, ?)";

    /**
     * @param args the command line parameters
     */
    public static void main(String... args) throws Exception {
        MerkleTreeInstance.init(8, 100000, 4, false);
        MerkleTreeInstance.add(URL);

        Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
        Statement stat = conn.createStatement();

        MerkleTreeInstance.getShimInstance().setVersionNo(1);

        stat.execute(CREATE);

        conn.setAutoCommit(false);
        PreparedStatement prep = conn.prepareStatement("INSERT INTO BRANCHES"
                + "(BID, BBALANCE, FILLER) VALUES(?, 0, ?)");

        prep.setInt(1, 1);
        String s = "THIS IS A 1ST STRING (STEP 1)";
        MerkleTreeInstance.add(s);
        prep.setString(2, s);
        prep.executeUpdate();

        prep.setInt(1, 2);
        String s2 = "THIS IS A 2ND STRING (STEP 1)";
        MerkleTreeInstance.add(s2);
        prep.setString(2, s2);
        prep.executeUpdate();

        prep.setInt(1, 3);
        String s3 = "THIS IS A 3RD STRING (STEP 1)";
        MerkleTreeInstance.add(s3);
        prep.setString(2, s3);
        prep.executeUpdate();

        conn.commit();

        prep = conn.prepareStatement("DELETE FROM BRANCHES WHERE BID=3");
        prep.executeUpdate();

        conn.commit();

        prep = conn.prepareStatement("DELETE FROM BRANCHES WHERE BID=1");
        prep.executeUpdate();

        conn.commit();

        // prep = conn.prepareStatement("DELETE FROM BRANCHES WHERE BID=2");
        // prep.executeUpdate();

        // conn.commit();

        // prep = conn.prepareStatement("INSERT INTO BRANCHES"
        // + "(BID, BBALANCE, FILLER) VALUES(?, 0, ?)");

        // prep.setInt(1, 3);
        // prep.setString(2, s3);
        // prep.executeUpdate();

        // prep.setInt(1, 1);
        // prep.setString(2, s);
        // prep.executeUpdate();

        conn.commit();

        prep = conn.prepareStatement("SELECT * FROM BRANCHES");
        ResultSet rs = prep.executeQuery();

        System.out.println("State of the DB");
        int i = 0;
        while (rs.next()) {
            System.out.println("row #" + i + ": " + rs.getInt(1) + " ; "
                    + rs.getInt(2) + " ; " + rs.getString(3));
            i++;
        }
        rs.close();

		/*
         * PreparedStatement prep = conn.prepareStatement(SELECT);
		 * 
		 * ResultSet rs = prep.executeQuery();
		 * 
		 * System.out.println("State of the DB"); int i = 0; while (rs.next()) {
		 * System.out.println("row #" + i + ": " + rs.getInt(1) + " ; " +
		 * rs.getInt(2) + " ; " + rs.getString(3)); i++; } rs.close();
		 * 
		 * conn.setAutoCommit(false);
		 * 
		 * prep = conn.prepareStatement(INSERT); String s1 = "THIS IS A STRING";
		 * prep.setString(1, s1); MerkleTreeInstance.add(s1);
		 * prep.executeUpdate();
		 * 
		 * conn.commit();
		 * 
		 * prep = conn.prepareStatement(SELECT);
		 * 
		 * rs = prep.executeQuery();
		 * 
		 * System.out.println("State of the DB"); i = 0; while (rs.next()) {
		 * System.out.println("row #" + i + ": " + rs.getInt(1) + " ; " +
		 * rs.getInt(2) + " ; " + rs.getString(3)); i++; } rs.close();
		 */
        // MerkleTreeInstance.getShimInstance().rollBack(0);
        // stat = conn.createStatement();
        // stat.execute(CREATE);
        MerkleTreeInstance.getShimInstance().setVersionNo(2);

        MerkleTreeInstance.getShimInstance().takeSnapshot("jdbcConnection.txt",
                2);

        int index = MerkleTreeInstance.getShimInstance().getIndex(conn);

        System.out.println("Index of the connection object = " + index);

    }
}
