import merkle.MerkleTreeInstance;

import java.sql.*;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

public class Benchmark4 {

    private final static int NB_TABLES = 2;
    private final static int NB_ROWS_PER_TABLE = 3;

    private final static int NB_REQUESTS_BETWEEN_COMMIT = 1;
    private final static int NB_REQUESTS_BETWEEN_STATS = 1;

    private static final Object LOCK = new Object();

    private static long startTime;

    private static int nbBatches = 0;
    private static int nbProcessedStatements = 0;

    private final static String URL = "jdbc:h2:mem:testServer;MULTI_THREADED=1;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000;LOCK_MODE=3";
    private final static String URL2 = "jdbc:h2:mem:testServer";
    // private final static String URL =
    // "jdbc:h2:mem:testServer;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000;LOCK_MODE=3";
    private final static String USER = "sa";
    private final static String PASSWORD = "sa";

    private static Set<Integer>[] keys_for_table = new TreeSet[NB_TABLES];

    public static void main(String... args) throws Exception {
        MerkleTreeInstance.init(8, 100000, 4, false);

        MerkleTreeInstance.add(URL);
        MerkleTreeInstance.add(URL2);

        init();
        runTest();
    }

    public static void init() throws SQLException {
        Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
        Statement stat = conn.createStatement();

        conn.setAutoCommit(false);
        for (int i = 0; i < NB_TABLES; i++) {
            keys_for_table[i] = new TreeSet<Integer>();
            stat
                    .execute("CREATE TABLE BRANCHES"
                            + i
                            + "(FIELD_0 INT NOT NULL PRIMARY KEY, FIELD_1 INT, FIELD_2 INT, FIELD_3 INT, FIELD_4 INT, FIELD_5 INT, FIELD_6 INT, FIELD_7 INT, FIELD_8 INT, FIELD_9 INT)");

            for (int key = 0; key < NB_ROWS_PER_TABLE; key++) {
                stat
                        .execute("INSERT INTO BRANCHES"
                                + i
                                + "(FIELD_0, FIELD_1, FIELD_2, FIELD_3, FIELD_4, FIELD_5, FIELD_6, FIELD_7, FIELD_8, FIELD_9) VALUES("
                                + key + "," + key + "," + key + "," + key + ","
                                + key + "," + key + "," + key + "," + key + ","
                                + key + "," + key + ")");
                keys_for_table[i].add(key);
            }
        }

        conn.commit();

        String user = "USER0";
        String passwd = "PW0";

        MerkleTreeInstance.add(passwd);
        stat.execute("CREATE USER " + user + " IDENTIFIED BY " + passwd);
        for (int j = 0; j < NB_TABLES; j++) {
            stat.execute("GRANT SELECT ON BRANCHES" + j + " TO " + user);
            stat.execute("GRANT INSERT ON BRANCHES" + j + " TO " + user);
            stat.execute("GRANT DELETE ON BRANCHES" + j + " TO " + user);
            stat.execute("GRANT UPDATE ON BRANCHES" + j + " TO " + user);

        }

        conn.commit();

        conn.close();
    }

    public static void runTest() throws Exception {
        Thread thread = new Thread(new BenchmarkThread(0));

        startTime = System.currentTimeMillis();

        thread.start();
        thread.join();
    }

    public static class BenchmarkThread implements Runnable {
        int id = -1;
        int integerField = 0;
        Random r = new Random();

        public BenchmarkThread(int id) {
            this.id = id;
        }

        public void run() {
            int nbLocallyProcessed = 0;

            PreparedStatement[] selectBranchs = new PreparedStatement[NB_TABLES];

            try {
                Connection conn = DriverManager.getConnection(URL2,
                        ("USER" + id), ("PW" + id));
                conn.setAutoCommit(true);
                Statement stat = conn.createStatement();

                for (int i = 0; i < NB_TABLES; i++) {
                    selectBranchs[i] = conn.prepareStatement(
                            "SELECT * FROM BRANCHES" + i,
                            ResultSet.TYPE_FORWARD_ONLY,
                            ResultSet.CONCUR_UPDATABLE);
                }

                while (true) {

                    nbBatches++;

                    MerkleTreeInstance.getShimInstance()
                            .setVersionNo(nbBatches);
                    MerkleTreeInstance.getShimInstance().getHash();
                    MerkleTreeInstance.getShimInstance().makeStable(
                            nbBatches - 1);

                    MerkleTreeInstance.getShimInstance().takeSnapshot("toto.txt", nbBatches);
                    int tableID;

                    synchronized (LOCK) {
                        if (nbProcessedStatements > 0
                                && nbProcessedStatements
                                % NB_REQUESTS_BETWEEN_STATS == 0) {
                            long totalTime = System.currentTimeMillis()
                                    - startTime;
                            double statPerSec = (double) 1000
                                    * nbProcessedStatements / totalTime;
                            System.out.println("Thread #" + id
                                    + ": Nb statements = "
                                    + nbProcessedStatements
                                    + " Statements per second: " + statPerSec);

                            System.out
                                    .println("Size of tree = "
                                            + MerkleTreeInstance
                                            .getShimInstance().size()
                                            + "  (nb calls to add = "
                                            + (float) MerkleTreeInstance.nbCallsToAdd
                                            / NB_REQUESTS_BETWEEN_STATS
                                            + ") (nb calls to remove = "
                                            + (float) MerkleTreeInstance.nbCallsToRemove
                                            / NB_REQUESTS_BETWEEN_STATS
                                            + ") (nb calls to update = "
                                            + (float) MerkleTreeInstance.nbCallsToUpdate
                                            / NB_REQUESTS_BETWEEN_STATS
                                            + ") (nb objects updated = "
                                            + (float) MerkleTreeInstance.nbObjectsUpdated
                                            / NB_REQUESTS_BETWEEN_STATS
                                            + ") (nb calls to leaf hash = "
                                            + (float) MerkleTreeInstance.nbCallsToLeafHash
                                            / NB_REQUESTS_BETWEEN_STATS + ")");
                            MerkleTreeInstance.nbCallsToAdd = 0;
                            MerkleTreeInstance.nbCallsToRemove = 0;
                            MerkleTreeInstance.nbCallsToUpdate = 0;
                            MerkleTreeInstance.nbCallsToLeafHash = 0;
                            MerkleTreeInstance.nbObjectsUpdated = 0;

                        }
                        tableID = ++nbProcessedStatements % NB_TABLES;
                    }

                    int key = r.nextInt(NB_ROWS_PER_TABLE);

                    String str;

                    str = "UPDATE BRANCHES" + tableID
                            + " SET FIELD_1=0 WHERE FIELD_0="
                            + (nbLocallyProcessed % NB_ROWS_PER_TABLE);

                    str = "UPDATE BRANCHES" + tableID
                            + " SET FIELD_1=0 WHERE FIELD_0=" + 0;

                    str = "UPDATE BRANCHES" + tableID + " SET FIELD_2=" + key
                            + " WHERE FIELD_1=" + key;
                    stat.executeUpdate(str);

                    nbLocallyProcessed++;
                    if (nbLocallyProcessed % NB_REQUESTS_BETWEEN_COMMIT == 0) {
                        conn.commit();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
