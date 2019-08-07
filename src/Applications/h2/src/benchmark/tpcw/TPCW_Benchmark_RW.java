package tpcw;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.LinkedList;
import java.util.Random;

public class TPCW_Benchmark_RW extends TPCW_Populate {
    private final static String URL = "jdbc:h2:mem:testServer;MULTI_THREADED=1;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000;LOCK_MODE=3";
    private final static String USER = "sa";
    private final static String PASSWORD = "sa";

    private static int NB_THREADS;
    public static int NB_TPCWS;
    private static int NB_USERS;
    private static int PROBA_TO_BUY;
    private static final Object LOCK = new Object();
    private static int nbProcessedStatements = 0;
    private final static int NB_REQUESTS_BETWEEN_STATS = 10000;
    private static long startTime = -1;

    protected static Random clientRandom = new Random(123456789);

    public final static boolean TRACE = false;

    private static LinkedList<TPCW_User> users_READ = new LinkedList<TPCW_User>();
    private static LinkedList<TPCW_User> users_WRITE = new LinkedList<TPCW_User>();

    // args = NB_THREADS NB_TPCWS NB_USERS
    public static void main(String[] args) {
        NB_THREADS = Integer.parseInt(args[0]);
        NB_TPCWS = Integer.parseInt(args[1]);
        NB_USERS = Integer.parseInt(args[2]);

        PROBA_TO_BUY = Integer.parseInt(args[3]);

        System.out.println("(Seq. Writes) // (// Reads)");
        System.out.println("Nb threads = " + NB_THREADS);
        System.out.println("Nb TPCWs = " + NB_TPCWS);
        System.out.println("Nb users = " + NB_USERS);

        try {
            Connection con = DriverManager.getConnection(URL, USER, PASSWORD);
            con.setAutoCommit(false);
            for (int i = 0; i < NB_TPCWS; i++) {
                TPCW_Populate.populate(con, i);
                TPCW_Database.verifyDBConsistency(con, i);
            }

            for (int i = 0; i < NB_USERS; i++) {
                TPCW_User user = new TPCW_User(i, NB_TPCWS, PROBA_TO_BUY);
                if (user.isNextARead()) {
                    users_READ.addLast(user);
                } else {
                    users_WRITE.addLast(user);
                }
            }
            Thread[] threads = new Thread[NB_THREADS];
            for (int i = 0; i < NB_THREADS; i++) {
                threads[i] = new TPCW_BenchmarkThread(i);
            }

            for (Thread t : threads) {
                t.start();
            }
            for (Thread t : threads) {
                t.join();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static class TPCW_BenchmarkThread extends merkle.IndexedThread {
        int id = -1;
        int integerField = 0;
        Random r;

        public TPCW_BenchmarkThread(int id) {
            this.id = id;
            r = new Random(id);
        }

        public int getIndex() {
            return id;
        }

        public void run() {
            int nbLocallyProcessed = 0;
            try {
                Connection conn = DriverManager.getConnection(URL, USER,
                        PASSWORD);
                conn.setAutoCommit(false);
                TPCW_User user = null;
                while (true) {
                    synchronized (LOCK) {

                        if (nbProcessedStatements > 0
                                && nbProcessedStatements
                                % NB_REQUESTS_BETWEEN_STATS == 0) {
                            if (startTime == -1) {
                                startTime = System.currentTimeMillis();
                                nbProcessedStatements = 0;
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
                            }
                        }
                        nbProcessedStatements++;
                    }
                    if (user != null) {
                        if (user.isNextARead()) {
                            synchronized (users_READ) {
                                users_READ.addLast(user);
                                users_READ.notifyAll();
                            }
                        } else {
                            synchronized (users_WRITE) {
                                users_WRITE.addLast(user);
                                users_WRITE.notifyAll();
                            }
                        }
                    }

                    if (id == 0) {
                        // WRITE thread
                        synchronized (users_WRITE) {
                            while (users_WRITE.isEmpty()) {
                                users_WRITE.wait();
                            }

                            user = users_WRITE.removeFirst();
                        }
                    } else {
                        // READ threads
                        synchronized (users_READ) {
                            while (users_READ.isEmpty()) {
                                users_READ.wait();
                            }

                            user = users_READ.removeFirst();
                        }
                    }

                    if (TRACE) {
                        System.out.println("Thread #" + id
                                + " executing user #" + user.id);
                    }
                    user.execute(conn);
                    // executeStatement(conn);
                    nbLocallyProcessed++;
                }

            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }
    }
}
