import BFT.Parameters;
import merkle.MerkleTreeInstance;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Random;

public class BenchmarkXen {
    private final static int NB_TABLES = 100;
    private final static int NB_ROWS_PER_TABLE = 500;
    private static int NB_THREADS;
    private final static int NB_REQUESTS_BETWEEN_STATS = 10000;

    private static final Object LOCK = new Object();

    private static long startTime = -1;
    private static long lastTime = -1;

    private static int nbProcessedStatements = 0;

    private final static String URL = "jdbc:h2:mem:testServer;MULTI_THREADED=1;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000;LOCK_MODE=3";
    private final static String URL2 = "jdbc:h2:mem:testServer";
    // private final static String URL =
    // "jdbc:h2:mem:testServer;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000;LOCK_MODE=3";
    private final static String USER = "sa";
    private final static String PASSWORD = "sa";

    private static Object[] LOCK_FOR_TABLE = new Object[NB_TABLES];

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
            LOCK_FOR_TABLE[i] = new Object();
            stat.execute("CREATE TABLE BRANCHES" + i
                    + "(FIELD_0 INT NOT NULL, FIELD_1 INT)");

            for (int key = 0; key < NB_ROWS_PER_TABLE; key++) {
                stat.execute("INSERT INTO BRANCHES" + i
                        + "(FIELD_0, FIELD_1) VALUES(" + key + "," + key + ")");
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
                Statement stat = conn.createStatement();

                while (true) {
                    int tableID;

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
                                long iterationStatPerSec = 1000
                                        * NB_REQUESTS_BETWEEN_STATS
                                        / iterationTime;
                                long statPerSec = 1000 * nbProcessedStatements
                                        / totalTime;
                                lastTime = currentTime;
                                System.out.println("Thread #" + id
                                        + ": Nb statements = "
                                        + nbProcessedStatements
                                        + " Statements per second: "
                                        + iterationStatPerSec + " avg: "
                                        + statPerSec);
                            }
                        }
                        tableID = ++nbProcessedStatements % NB_TABLES;
                    }

                    int key = r.nextInt(NB_ROWS_PER_TABLE);
                    String str = "UPDATE BRANCHES" + tableID + " SET FIELD_1="
                            + (key + nbProcessedStatements % (tableID + 100))
                            + " WHERE FIELD_0=" + key;
                    stat.executeUpdate(str);

                    key = r.nextInt(NB_ROWS_PER_TABLE);
                    str = "UPDATE BRANCHES" + tableID + " SET FIELD_1="
                            + (key + nbProcessedStatements % (tableID + 100))
                            + " WHERE FIELD_0=" + key;
                    stat.executeUpdate(str);

                    key = r.nextInt(NB_ROWS_PER_TABLE);
                    str = "UPDATE BRANCHES" + tableID + " SET FIELD_1="
                            + (key + nbProcessedStatements % (tableID + 100))
                            + " WHERE FIELD_0=" + key;
                    stat.executeUpdate(str);

                    conn.commit();

                    //key = r.nextInt(NB_ROWS_PER_TABLE);
                    //str = "SELECT * FROM BRANCHES" + tableID;
                    //stat.executeQuery(str);

                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
