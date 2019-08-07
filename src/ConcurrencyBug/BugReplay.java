package ConcurrencyBug;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Random;

public class BugReplay {
    private final static int NB_ROWS_PER_TABLE = 5000;
    private final static int NB_THREADS = 2;

    private final static String URL = "jdbc:h2:mem:testServer;MULTI_THREADED=1;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000;LOCK_MODE=0";
    private final static String URL2 = "jdbc:h2:mem:testServer";
    private final static String USER = "sa";
    private final static String PASSWORD = "sa";

    public static void main(String... args) throws Exception {
        System.out.println("Starting bug replay with " + NB_THREADS
                + " threads");

        init();
        runTest();
    }

    public static void init() throws SQLException {
        Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
        Statement stat = conn.createStatement();

        conn.setAutoCommit(false);
        stat.execute("CREATE TABLE BRANCHES"
                + "(BID INT NOT NULL PRIMARY KEY, BBALANCE INT)");

        for (int key = 0; key < NB_ROWS_PER_TABLE; key++) {
            stat.execute("INSERT INTO BRANCHES" + "(BID, BBALANCE) VALUES("
                    + key + "," + (key + 2) + ")");
        }

        conn.commit();

        for (int i = 0; i < NB_THREADS; i++) {
            String user = "USER" + i;
            String passwd = "PW" + i;

            stat.execute("CREATE USER " + user + " IDENTIFIED BY " + passwd);
            stat.execute("GRANT SELECT ON BRANCHES" + " TO " + user);
            stat.execute("GRANT INSERT ON BRANCHES" + " TO " + user);
            stat.execute("GRANT DELETE ON BRANCHES" + " TO " + user);
            stat.execute("GRANT UPDATE ON BRANCHES" + " TO " + user);
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
        Random r = new Random();

        public BenchmarkThread(int id) {
            this.id = id;
        }

        public void run() {
            try {
                Connection conn = DriverManager.getConnection(URL2,
                        ("USER" + id), ("PW" + id));
                conn.setAutoCommit(true);
                Statement stat = conn.createStatement();

                while (true) {
                    int key = NB_ROWS_PER_TABLE + id;

                    String str = "INSERT INTO BRANCHES" + " (BID, BBALANCE)"
                            + " VALUES(" + key + ", 0)";

                    stat.executeUpdate(str);

                    conn.commit();

                    stat.executeUpdate("DELETE FROM BRANCHES" + " WHERE BID="
                            + key);

                    conn.commit();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
