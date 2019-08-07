import BFT.Parameters;
import merkle.MerkleTreeInstance;

import java.sql.*;
import java.util.Random;

public class BenchmarkXenSELECT_YANG {
    private final static int NB_TABLES = 16;
    private final static int NB_ROWS_PER_TABLE = 5000;
    private static int NB_THREADS;
    private final static int NB_REQUESTS_BETWEEN_STATS = 500000;

    private static final Object LOCK = new Object();

    private static boolean SELECT_PRIMARY = true;
    private static boolean SELECT_NON_PRIMARY = false;
    private static boolean UPDATE_PRIMARY = false;
    private static boolean UPDATE_NON_PRIMARY = false;
    private static boolean JOIN_PRIMARY = false;
    private static boolean JOIN_NON_PRIMARY = false;

    private static long startTime = -1;
    private static long lastTime = -1;

    private static int nbProcessedStatements = 0;

    private final static String URL = "jdbc:h2:mem:testServer;MULTI_THREADED=1;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000;LOCK_MODE=3";
    private final static String URL2 = "jdbc:h2:mem:testServer";
    private final static String USER = "sa";
    private final static String PASSWORD = "sa";

    public static void main(String... args) throws Exception {
        Parameters.useDummyTree = true;
        MerkleTreeInstance.init(8, 100000, 4, false);

        NB_THREADS = Integer.parseInt(args[0]);

        System.out.println("Nb threads = " + NB_THREADS);

        init();
        runTest();
    }

    public static void init() throws SQLException {
        Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
        Statement stat = conn.createStatement();
        conn.setAutoCommit(false);
        for (int i = 0; i < NB_TABLES; i++) {
            stat
                    .execute("CREATE TABLE BRANCHES"
                            + i
                            + "(BID INT NOT NULL PRIMARY KEY, BBALANCE INT, FILLER VARCHAR(88))");
            PreparedStatement insert = conn
                    .prepareStatement("INSERT INTO BRANCHES" + i
                            + "(BID, BBALANCE, FILLER) VALUES(?, ?, ?)");
            for (int j = 0; j < NB_ROWS_PER_TABLE; j++) {
                insert.setInt(1, j);
                insert.setInt(2, NB_ROWS_PER_TABLE - j);
                insert.setString(3, "hahahahahahahahahaha");
                insert.execute();
            }

        }

        conn.commit();

        for (int i = 0; i < NB_THREADS; i++) {
            String user = "USER" + i;
            String passwd = "PW" + i;

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
            try {
                Connection conn = DriverManager.getConnection(URL2,
                        ("USER" + id), ("PW" + id));
                conn.setAutoCommit(false);

                PreparedStatement[] selects_non_primary = new PreparedStatement[NB_TABLES];
                PreparedStatement[] selects_primary = new PreparedStatement[NB_TABLES];
                PreparedStatement[] updates_non_primary = new PreparedStatement[NB_TABLES];
                PreparedStatement[] updates_primary = new PreparedStatement[NB_TABLES];
                PreparedStatement[] joins_non_primary = new PreparedStatement[NB_TABLES];
                PreparedStatement[] joins_primary = new PreparedStatement[NB_TABLES];
                // selects = new
                // PreparedStatement[BFT.Parameters.numberOfClients];
                // updates = new
                // PreparedStatement[BFT.Parameters.numberOfClients];

                for (int j = 0; j < NB_TABLES; j++) {
                    selects_primary[j] = conn
                            .prepareStatement("SELECT BBALANCE from BRANCHES"
                                    + j + " where BID=?");

                    selects_non_primary[j] = conn
                            .prepareStatement("SELECT BID from BRANCHES" + j
                                    + " where BBALANCE=?");

                    updates_primary[j] = conn
                            .prepareStatement("UPDATE BRANCHES" + j
                                    + " SET FILLER=? WHERE BID=?");

                    updates_non_primary[j] = conn
                            .prepareStatement("UPDATE BRANCHES" + j
                                    + " SET FILLER=? WHERE BBALANCE=?");

                    joins_primary[j] = conn
                            .prepareStatement("SELECT * from BRANCHES" + j
                                    + " JOIN BRANCHES" + ((j + 1) % NB_TABLES)
                                    + " ON BRANCHES" + j + ".BID=BRANCHES"
                                    + ((j + 1) % NB_TABLES)
                                    + ".BID where BRANCHES" + j + ".BID=?");

                    joins_non_primary[j] = conn
                            .prepareStatement("SELECT * from BRANCHES" + j
                                    + " JOIN BRANCHES" + ((j + 1) % NB_TABLES)
                                    + " ON BRANCHES" + j + ".BBALANCE=BRANCHES"
                                    + ((j + 1) % NB_TABLES)
                                    + ".BBALANCE where BRANCHES" + j
                                    + ".BBALANCE=?");
                }

                while (true) {
                    synchronized (LOCK) {
                        if (nbProcessedStatements > 0
                                && nbProcessedStatements
                                % NB_REQUESTS_BETWEEN_STATS == 0) {
                            if (startTime == -1) {
                                startTime = System.currentTimeMillis();
                                lastTime = startTime;
                                nbProcessedStatements = 0;
                            } else {
                                long currentTime = System.currentTimeMillis();
                                long totalTime = currentTime - startTime;
                                long iterationTime = currentTime - lastTime;
                                double iterationStatPerSec = 1000 * ((double) NB_REQUESTS_BETWEEN_STATS / (double) iterationTime);
                                double statPerSec = 1000 * ((double) nbProcessedStatements / (double) totalTime);
                                lastTime = currentTime;
                                System.out.println("Thread #" + id
                                        + ": Nb statements = "
                                        + nbProcessedStatements
                                        + " Statements per second: "
                                        + iterationStatPerSec + " avg: "
                                        + statPerSec);
                            }
                        }
                        nbProcessedStatements++;
                    }

                    Random rand = new Random();
                    int tableID = rand.nextInt(NB_TABLES);

                    if (SELECT_PRIMARY) {
                        PreparedStatement select = selects_primary[tableID];
                        select.setInt(1, rand.nextInt(NB_ROWS_PER_TABLE));
                        select.executeQuery();
                        conn.commit();
                    }
                    if (SELECT_NON_PRIMARY) {
                        PreparedStatement select = selects_non_primary[tableID];
                        select.setInt(1, rand.nextInt(NB_ROWS_PER_TABLE));
                        select.executeQuery();
                        conn.commit();
                    }
                    if (UPDATE_PRIMARY) {
                        PreparedStatement update = updates_primary[tableID];
                        update.setString(1, "hihi" + nbProcessedStatements);
                        update.setInt(2, rand.nextInt(NB_ROWS_PER_TABLE));
                        update.executeUpdate();
                        conn.commit();
                    }
                    if (UPDATE_NON_PRIMARY) {
                        PreparedStatement update = updates_non_primary[tableID];
                        update.setString(1, "hihi" + nbProcessedStatements);
                        update.setInt(2, rand.nextInt(NB_ROWS_PER_TABLE));
                        update.executeUpdate();
                        conn.commit();
                    }
                    if (JOIN_PRIMARY) {
                        PreparedStatement join = joins_primary[tableID];
                        join.setInt(1, rand.nextInt(NB_ROWS_PER_TABLE));
                        join.executeQuery();
                        conn.commit();
                    }
                    if (JOIN_NON_PRIMARY) {
                        PreparedStatement join = joins_non_primary[tableID];
                        join.setInt(1, rand.nextInt(NB_ROWS_PER_TABLE));
                        join.executeQuery();
                        conn.commit();
                    }

                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
