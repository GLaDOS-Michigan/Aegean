import BFT.Parameters;
import merkle.MerkleTreeInstance;

import java.sql.*;
import java.util.Random;

public class Benchmark3 {
    private final static boolean BATCH = true;
    private final static boolean MERKLE_TREE = true;
    private final static boolean MERKLE_TREE_PRINT = true;
    private final static boolean GET_HASH = true;

    private final static int NB_TABLES = 16;
    private final static int NB_ROWS_PER_TABLE = 5000;
    private static int NB_THREADS;
    private final static int NB_TABLES_TO_UPDATE_WITHIN_TRANSACTION = 4;
    private final static int NB_REQUESTS_TO_PERFORM_IN_A_BATCH = 50;
    private final static int NB_REQUESTS_BETWEEN_STATS = 10000;

    private static final Object LOCK = new Object();
    private static final Object BARRIER_LOCK = new Object();

    private static long startTime = -1;

    private static int nbBatches = 0;
    private static int nbProcessedStatements = 0;
    private static int nbProcessedStatementsInBatch = 0;

    private final static String URL = "jdbc:h2:mem:testServer;MULTI_THREADED=1;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000;LOCK_MODE=3";
    private final static String URL2 = "jdbc:h2:mem:testServer";
    // private final static String URL =
    // "jdbc:h2:mem:testServer;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000;LOCK_MODE=3";
    private final static String USER = "sa";
    private final static String PASSWORD = "sa";

    private static int nbThreadsToWait;
    private static Object[] LOCK_FOR_TABLE = new Object[NB_TABLES];

    public static void main(String... args) throws Exception {
        if (MERKLE_TREE) {
            MerkleTreeInstance.init(8, 100000, 4, false);

            MerkleTreeInstance.add(URL);
            MerkleTreeInstance.add(URL2);
        } else {
            Parameters.useDummyTree = true;
        }

        NB_THREADS = Integer.parseInt(args[0]);
        nbThreadsToWait = NB_THREADS;

        System.out.println("Nb threads = " + NB_THREADS);

        init();
        runTest();
    }

    public static void init() throws SQLException {
        Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
        Statement stat = conn.createStatement();

        conn.setAutoCommit(false);
        for (int i = 0; i < NB_TABLES; i++) {
            LOCK_FOR_TABLE[i] = new Object();
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
            }
        }

        conn.commit();

        for (int i = 0; i < NB_THREADS; i++) {
            String user = "USER" + i;
            String passwd = "PW" + i;

            if (MERKLE_TREE) {
                MerkleTreeInstance.add(passwd);
            }
            stat.execute("CREATE USER " + user + " IDENTIFIED BY " + passwd);
            for (int j = 0; j < NB_TABLES; j++) {
                stat.execute("GRANT SELECT ON BRANCHES" + j + " TO " + user);
                stat.execute("GRANT INSERT ON BRANCHES" + j + " TO " + user);
                stat.execute("GRANT DELETE ON BRANCHES" + j + " TO " + user);
                stat.execute("GRANT UPDATE ON BRANCHES" + j + " TO " + user);
            }
        }

        conn.commit();

        conn.close();
    }

    public static void runTest() throws Exception {
        Thread[] threads = new Thread[NB_THREADS];
        for (int i = 0; i < NB_THREADS; i++) {
            threads[i] = new Thread(new BenchmarkThread(i));
        }

        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }
    }

    public static class BenchmarkThread implements Runnable {
        int id = -1;
        int integerField = 0;
        Random r;

        public BenchmarkThread(int id) {
            this.id = id;
            r = new Random(id);
        }

        public void run() {
            int nbLocallyProcessed = 0;

            PreparedStatement[] selectBranchs = new PreparedStatement[NB_TABLES];

            try {
                Connection conn = DriverManager.getConnection(URL2,
                        ("USER" + id), ("PW" + id));
                conn.setAutoCommit(false);
                Statement stat = conn.createStatement();

                for (int i = 0; i < NB_TABLES; i++) {
                    // SELECT-1
                    // String str = "SELECT * FROM BRANCHES" + i +
                    // " WHERE FIELD_0 > 10 AND FIELD_0 < 20";

                    // SELECT-2
                    // String str = "SELECT * FROM BRANCHES" + i;

                    // SELECT-4
                    String str = "SELECT * FROM BRANCHES" + i
                            + " WHERE FIELD_1 = 10";

                    selectBranchs[i] = conn.prepareStatement(str,
                            ResultSet.TYPE_FORWARD_ONLY,
                            ResultSet.CONCUR_UPDATABLE);
                }

                while (true) {
                    if (BATCH) {
                        synchronized (BARRIER_LOCK) {
                            nbThreadsToWait--;
                            if (nbThreadsToWait == 0) {
                                nbBatches++;

                                if (MERKLE_TREE) {
                                    MerkleTreeInstance.getShimInstance()
                                            .setVersionNo(nbBatches);
                                    if (GET_HASH) {
                                        MerkleTreeInstance.getShimInstance()
                                                .getHash();
                                    }
                                    MerkleTreeInstance.getShimInstance()
                                            .makeStable(nbBatches - 1);
                                }

                                nbProcessedStatementsInBatch = 0;
                                nbThreadsToWait = NB_THREADS;
                                BARRIER_LOCK.notifyAll();
                            } else {
                                BARRIER_LOCK.wait();
                            }
                        }
                    }
                    while (true) {

                        int tableID;

                        synchronized (LOCK) {
                            if (BATCH) {
                                if (nbProcessedStatementsInBatch > NB_REQUESTS_TO_PERFORM_IN_A_BATCH) {
                                    break;
                                }
                                nbProcessedStatementsInBatch++;
                            }

                            if (nbProcessedStatements > 0
                                    && nbProcessedStatements
                                    % NB_REQUESTS_BETWEEN_STATS == 0) {
                                if (startTime == -1) {
                                    startTime = System.currentTimeMillis();
                                    nbProcessedStatements = 0;
                                    nbProcessedStatementsInBatch = 0;
                                } else {
                                    long totalTime = System.currentTimeMillis()
                                            - startTime;
                                    double statPerSec = (double) 1000
                                            * nbProcessedStatements / totalTime;
                                    System.out.println("Thread #" + id
                                            + ": Nb statements = "
                                            + nbProcessedStatements
                                            + " Statements per second: "
                                            + statPerSec);

                                    if (MERKLE_TREE_PRINT) {
                                        System.out
                                                .println("Size of tree = "
                                                        + MerkleTreeInstance
                                                        .getShimInstance()
                                                        .size()
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
                                                        / NB_REQUESTS_BETWEEN_STATS
                                                        + ")");
                                        MerkleTreeInstance.nbCallsToAdd = 0;
                                        MerkleTreeInstance.nbCallsToRemove = 0;
                                        MerkleTreeInstance.nbCallsToUpdate = 0;
                                        MerkleTreeInstance.nbCallsToLeafHash = 0;
                                        MerkleTreeInstance.nbObjectsUpdated = 0;
                                    }
                                }
                            }
                            tableID = ++nbProcessedStatements % NB_TABLES;
                        }

                        // if (tableID / 2 == 0)
                        // {
                        // tableID = 0;
                        // } else
                        // {
                        // tableID = NB_TABLES / 2;
                        // }

                        // SELECT-3
                        // ResultSet rs =
                        // stat.executeQuery("SELECT * FROM BRANCHES"+tableID +
                        // " WHERE FIELD_0 > 10 AND FIELD_0 < 20");

                        // SELECT-5
                        // ResultSet rs =
                        // stat.executeQuery("SELECT * FROM BRANCHES" + tableID
                        // + " WHERE FIELD_1 = 10");

                        // SELECT-1, SELECT-2, SELECT-4
                        // ResultSet rs = selectBranchs[tableID].executeQuery();
                        // while (rs.next())
                        // {
                        // rs.getInt(1);
                        // rs.getInt(2);
                        // rs.getInt(3);
                        // rs.getInt(4);
                        // rs.getInt(5);
                        // rs.getInt(6);
                        // rs.getInt(7);
                        // rs.getInt(8);
                        // rs.getInt(9);
                        // }
                        // rs.close();

                        int key = r.nextInt(NB_ROWS_PER_TABLE);
                        String str;

                        // UPDATE-1
                        // str = "UPDATE BRANCHES" + tableID + " SET FIELD_2=" +
                        // key + " WHERE FIELD_0 = " + key;

                        // UPDATE-2
                        // str = "UPDATE BRANCHES" + tableID + " SET FIELD_2=" +
                        // key + " WHERE FIELD_1 = " + key;

                        // UPDATE-3
                        // str = "UPDATE BRANCHES" + tableID + " SET FIELD_2=" +
                        // key + " WHERE FIELD_0 BETWEEN " + key + " AND " +
                        // (key + 10);

                        // UPDATE-4
                        // str = "UPDATE BRANCHES"
                        // + tableID
                        // + " SET FIELD_2 = (SELECT SUM(FIELD_0) FROM BRANCHES"
                        // + tableID + ") WHERE FIELD_0 = " + key;

                        // UPDATE-5
                        if (tableID > 7) {
                            tableID = tableID - 8;
                        }
                        int tableID2 = ((tableID + 8));
                        str = "UPDATE BRANCHES"
                                + tableID
                                + " SET FIELD_2=(SELECT SUM(FIELD_0) FROM BRANCHES"
                                + tableID + " WHERE BRANCHES" + tableID2
                                + ".FIELD_0=BRANCHES" + tableID
                                + ".FIELD_0) WHERE FIELD_0=" + key;

                        stat.executeUpdate(str);

                        // for (int i = 0; i <
                        // NB_TABLES_TO_UPDATE_WITHIN_TRANSACTION; i++)
                        // {
                        // tableID = (tableID + 1) % NB_TABLES;
                        // str = "UPDATE BRANCHES" + tableID + " SET FIELD_2="
                        // + key + " WHERE FIELD_0=" + key;

                        // stat.executeUpdate(str);
                        // }

                        nbLocallyProcessed++;
                        conn.commit();
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
