import merkle.MerkleTreeInstance;
import merkle.Tools;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class BenchmarkDeterminism {
    private final static boolean UPDATE = true;
    private final static boolean UPDATE_JOIN = false;

    private final static int NB_TABLES = 1;
    private final static int NB_ROWS_PER_TABLE = 1;
    private static int NB_THREADS;

    private final static int NB_REQUESTS_BETWEEN_COMMIT = 1;
    private final static int NB_REQUESTS_TO_PERFORM_IN_A_BATCH = 500;

    private static final Object LOCK = new Object();
    private static final Object BARRIER_LOCK = new Object();

    private static int nbBatches = 0;
    private static int nbProcessedStatements = 0;
    private static int nbProcessedStatementsInBatch = 0;

    private final static String URL = "jdbc:h2:mem:testServer;MULTI_THREADED=1;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000;LOCK_MODE=3";
    private final static String URL2 = "jdbc:h2:mem:testServer";
    private final static String USER = "sa";
    private final static String PASSWORD = "sa";

    private static int nbThreadsToWait;
    private static boolean[] locked = new boolean[NB_TABLES];

    public static void main(String... args) throws Exception {
        MerkleTreeInstance.init(8, 1000, 4, false);

        MerkleTreeInstance.add(URL);
        MerkleTreeInstance.add(URL2);

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
            locked[i] = false;
            stat.execute("CREATE TABLE BRANCHES" + i
                    + "(BID INT NOT NULL PRIMARY KEY, BBALANCE INT)");

            for (int key = 0; key < NB_ROWS_PER_TABLE; key++) {
                stat.execute("INSERT INTO BRANCHES" + i
                        + "(BID, BBALANCE) VALUES(" + key + "," + (key + 2)
                        + ")");
            }
        }

        conn.commit();

        for (int i = 0; i < NB_THREADS; i++) {
            String user = "USER" + i;
            String passwd = "PW" + i;

            MerkleTreeInstance.add(passwd);
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

        public BenchmarkThread(int id) {
            this.id = id;
        }

        public void run() {
            int nbLocallyProcessed = 0;

            try {
                Connection conn = DriverManager.getConnection(URL2,
                        ("USER" + id), ("PW" + id));
                conn.setAutoCommit(true);
                Statement stat = conn.createStatement();
                while (true) {

                    synchronized (BARRIER_LOCK) {
                        nbThreadsToWait--;
                        if (nbThreadsToWait == 0) {
                            nbBatches++;

                            MerkleTreeInstance.getShimInstance().setVersionNo(
                                    nbBatches);
                            byte[] hash = MerkleTreeInstance.getShimInstance()
                                    .getHash();

                            System.out.println("Batch #" + nbBatches
                                    + " (nb processed requests = "
                                    + nbProcessedStatements + " ) hash = ");
                            Tools.printHash(hash);
                            // System.out.println(MerkleTreeInstance
                            // .getShimInstance());

                            MerkleTreeInstance.getShimInstance().makeStable(
                                    nbBatches - 1);

                            nbProcessedStatementsInBatch = 0;
                            nbThreadsToWait = NB_THREADS;
                            BARRIER_LOCK.notifyAll();
                        } else {
                            BARRIER_LOCK.wait();
                        }
                    }
                    while (true) {

                        int tableID;
                        int key;
                        synchronized (LOCK) {
                            if (nbProcessedStatementsInBatch > NB_REQUESTS_TO_PERFORM_IN_A_BATCH) {
                                break;
                            }
                            nbProcessedStatementsInBatch++;
                            tableID = ++nbProcessedStatements % NB_TABLES;
                            key = nbProcessedStatements % NB_ROWS_PER_TABLE;
                            while (locked[tableID]) {
                                LOCK.wait();
                            }
                            locked[tableID] = true;
                            //MerkleTreeInstance.getShimInstance().setRequestID(
                            //		nbProcessedStatementsInBatch);
                        }

                        String str;

                        if (UPDATE) {
                            str = "UPDATE BRANCHES" + tableID
                                    + " SET BBALANCE=0 WHERE BID=" + key;
                        }
                        if (UPDATE_JOIN) {
                            str = "UPDATE BRANCHES1 SET BBALANCE=(SELECT BRANCHES2.BBALANCE FROM BRANCHES2 WHERE BRANCHES2.BBALANCE=BRANCHES1.BBALANCE)";
                        }
                        stat.executeUpdate(str);
                        synchronized (LOCK) {
                            locked[tableID] = false;
                            LOCK.notifyAll();
                        }
                        nbLocallyProcessed++;
                        if (nbLocallyProcessed % NB_REQUESTS_BETWEEN_COMMIT == 0) {
                            conn.commit();
                        }
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
