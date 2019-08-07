import merkle.MerkleTreeInstance;

import java.sql.*;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

public class Benchmark {
    private final static boolean CREATE_OBJECT = false;
    private final static boolean INCREMENT_INTEGER = false;
    private final static boolean SELECT = true;
    private final static boolean INSERT_DELETE = false;
    private final static boolean UPDATE = false;
    private final static boolean READ_WRITE = false;

    private final static boolean BATCH = true;
    private final static boolean MERKLE_TREE = true;
    private final static boolean GET_HASH = true;
    private final static boolean ONE_TABLE_PER_THREAD = true;

    private final static int NB_TABLES = 8;
    private final static int NB_ROWS_PER_TABLE = 5000;
    private static int NB_THREADS;

    private final static int NB_REQUESTS_BETWEEN_COMMIT = 1;
    private final static int NB_REQUESTS_TO_PERFORM_IN_A_BATCH = 50;
    private final static int NB_REQUESTS_BETWEEN_STATS = 100000;

    private static final Object LOCK = new Object();
    private static final Object BARRIER_LOCK = new Object();

    private static long startTime;

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
    private static Set<Integer>[] keys_for_table = new TreeSet[NB_TABLES];
    private static Object[] LOCK_FOR_TABLE = new Object[NB_TABLES];

    public static void main(String... args) throws Exception {
        if (MERKLE_TREE) {
            MerkleTreeInstance.init(8, 100000, 4, true);

            MerkleTreeInstance.add(URL);
            MerkleTreeInstance.add(URL2);
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
            keys_for_table[i] = new TreeSet<Integer>();
            LOCK_FOR_TABLE[i] = new Object();
            stat
                    .execute("CREATE TABLE BRANCHES"
                            + i
                            + "(FIELD_0 INT NOT NULL PRIMARY KEY, BBALANCE INT, BBALANCE INT, BBALANCE INT, BBALANCE INT, BBALANCE INT, BBALANCE INT, BBALANCE INT, BBALANCE INT, BBALANCE INT)");

            for (int key = 0; key < NB_ROWS_PER_TABLE; key++) {
                stat.execute("INSERT INTO BRANCHES" + i
                        + "(BID, BBALANCE) VALUES(" + key + "," + (key + 2)
                        + ")");
                keys_for_table[i].add(key);
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

        startTime = System.currentTimeMillis();

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
                                long totalTime = System.currentTimeMillis()
                                        - startTime;
                                double statPerSec = (double) 1000
                                        * nbProcessedStatements / totalTime;
                                System.out.println("Thread #" + id
                                        + ": Nb statements = "
                                        + nbProcessedStatements
                                        + " Statements per second: "
                                        + statPerSec);

                            }
                            tableID = ++nbProcessedStatements % NB_TABLES;
                        }

                        if (CREATE_OBJECT) {
                            String str = new Object().toString();
                        }

                        if (INCREMENT_INTEGER) {
                            integerField = (integerField + 2)
                                    % Integer.MAX_VALUE;
                        }

                        if (SELECT) {
                            // ResultSet rs = stat
                            // .executeQuery("SELECT * FROM BRANCHES"
                            // + tableID);
                            ResultSet rs = selectBranchs[tableID]
                                    .executeQuery();
                            while (rs.next()) {
                                rs.getInt(1);
                                rs.getInt(2);
                            }
                            rs.close();
                        }

                        if (INSERT_DELETE) {
                            int key;
                            while (true) {
                                key = r.nextInt(2000) + NB_ROWS_PER_TABLE;
                                synchronized (LOCK_FOR_TABLE[tableID]) {
                                    if (keys_for_table[tableID].add(key)) {
                                        break;
                                    }
                                }
                            }

                            // BE CAREFUL
                            if (ONE_TABLE_PER_THREAD) {
                                tableID = id;
                            }

                            String str = "INSERT INTO BRANCHES"
                                    + tableID
                                    + " (BID, BBALANCE)"
                                    + " VALUES("
                                    + key
                                    + ", (SELECT AVG(BBALANCE+BID) from BRANCHES"
                                    + ((tableID + 10) % NB_TABLES) + ") ";

                            str = "INSERT INTO BRANCHES" + tableID
                                    + " (BID, BBALANCE)" + " VALUES(" + key
                                    + ", 0)";

                            if (MERKLE_TREE) {
                                System.out.println("Size of tree: before = "
                                        + MerkleTreeInstance.getShimInstance()
                                        .size());
                            }
                            stat.executeUpdate(str);
                            if (MERKLE_TREE) {
                                System.out.println("Size of tree: after = "
                                        + MerkleTreeInstance.getShimInstance()
                                        .size());
                            }
                            stat.executeUpdate("DELETE FROM BRANCHES" + tableID
                                    + " WHERE BID=" + key);

                            synchronized (LOCK_FOR_TABLE[tableID]) {
                                keys_for_table[tableID].remove(key);
                            }

                            nbLocallyProcessed++;
                            if (nbLocallyProcessed % NB_REQUESTS_BETWEEN_COMMIT == 0) {
                                conn.commit();
                            }
                        }

                        if (UPDATE) {
                            int key = r.nextInt(NB_ROWS_PER_TABLE);

                            // BE CAREFUL
                            if (ONE_TABLE_PER_THREAD) {
                                tableID = id;
                            }

                            String str = "UPDATE BRANCHES" + tableID
                                    + " SET BBALANCE=0 WHERE BID=" + key;

                            str = "UPDATE BRANCHES" + tableID
                                    + " SET BBALANCE=0 WHERE BID=" + key;
                            // if (MERKLE_TREE)
                            // {
                            // System.out.println("Size of tree: before = "
                            // + MerkleTreeInstance.getShimInstance()
                            // .size());
                            // }
                            stat.executeUpdate(str);
                            // MerkleTreeInstance.getShimInstance().setVersionNo(
                            // nbProcessedStatements);
                            // MerkleTreeInstance.getShimInstance().getHash();
                            //
                            // MerkleTreeInstance.getShimInstance().makeStable(
                            // nbProcessedStatements - 1);
                            // // MerkleTreeInstance.getShimInstance().gc();
                            // if (MERKLE_TREE)
                            // {
                            // System.out.println("Size of tree: (key = "
                            // + key
                            // + ") after = "
                            // + MerkleTreeInstance.getShimInstance()
                            // .size());
                            // }
                            nbLocallyProcessed++;
                            /*
							 * if (nbLocallyProcessed %
							 * NB_REQUESTS_BETWEEN_COMMIT == 0) { conn.commit();
							 * }
							 */
                        }

                        if (READ_WRITE) {
                            // ResultSet rs = stat
                            // .executeQuery("SELECT * FROM BRANCHES"
                            // + tableID);
                            ResultSet rs = selectBranchs[tableID]
                                    .executeQuery();
                            while (rs.next()) {
                                rs.updateInt(2, rs.getInt(2) + 1);
                                // stat.execute("UPDATE BRANCHES" + tableID
                                // + " SET BBALANCE=3 WHERE KEY=");
                            }
                            rs.close();
                        }
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
