import merkle.MerkleTreeInstance;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class MerkleTreeStep2 {
    /**
     * @param args the command line parameters
     */
    public static void main(String... args) throws Exception {
        MerkleTreeInstance.init(8, 100000, 4, false);
        MerkleTreeInstance.getShimInstance().loadSnapshot("jdbcConnection.txt");

        Connection conn = (Connection) MerkleTreeInstance.getShimInstance()
                .getObject(6);

        if (true) {
            PreparedStatement prep = conn
                    .prepareStatement("SELECT * FROM BRANCHES");

            ResultSet rs = prep.executeQuery();

            System.out.println("State of the DB");
            int i = 0;
            while (rs.next()) {
                System.out.println("row #" + i + ": " + rs.getInt(1) + " ; "
                        + rs.getInt(2) + " ; " + rs.getString(3));
                i++;
            }
            rs.close();

            // conn.setAutoCommit(false);
            // prep = conn.prepareStatement("INSERT INTO BRANCHES"
            // + "(BID, BBALANCE, FILLER) VALUES(?, 0, ?)");
            //
            // prep.setInt(1, 3);
            // String s4 = "THIS IS A STRING (STEP 2)";
            // MerkleTreeInstance.add(s4);
            // prep.setString(2, s4);
            // prep.executeUpdate();
            //
            // conn.commit();
            //
            // prep = conn.prepareStatement("SELECT * FROM BRANCHES");
            // rs = prep.executeQuery();
            //
            // System.out.println("State of the DB");
            // i = 0;
            // while (rs.next())
            // {
            // System.out.println("row #" + i + ": " + rs.getInt(1) + " ; "
            // + rs.getInt(2) + " ; " + rs.getString(3));
            // i++;
            // }
            // rs.close();
            //
            // prep = conn.prepareStatement("INSERT INTO BRANCHES"
            // + "(BID, BBALANCE, FILLER) VALUES(?, 0, ?)");
            // prep.setInt(1, 4);
            // String s5 = "THIS IS ANOTHER STRING (STEP 2)";
            // MerkleTreeInstance.add(s5);
            // prep.setString(2, s5);
            // prep.executeUpdate();
            //
            // conn.commit();
            //
            // prep = conn.prepareStatement("SELECT * FROM BRANCHES");
            // rs = prep.executeQuery();
            //
            // System.out.println("State of the DB");
            // i = 0;
            // while (rs.next())
            // {
            // System.out.println("row #" + i + ": " + rs.getInt(1) + " ; "
            // + rs.getInt(2) + " ; " + rs.getString(3));
            // i++;
            // }
            // rs.close();
        }

        conn.close();

    }
}
