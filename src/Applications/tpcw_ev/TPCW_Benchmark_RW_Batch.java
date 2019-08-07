package Applications.tpcw_ev;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.LinkedList;
import java.util.Random;

public class TPCW_Benchmark_RW_Batch extends TPCW_Populate {
    private final static String URL = "jdbc:h2:mem:testServer;MULTI_THREADED=1;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000;LOCK_MODE=3";
    private final static String USER = "sa";
    private final static String PASSWORD = "sa";

    private static int NB_THREADS;
    public static int NB_TPCWS;
    private static int NB_USERS;
    private static int nbThreadsToWait;
    private static final Object BARRIER_LOCK = new Object();
    private static final Object LOCK = new Object();
    private static int nbBatches = 0;
    private static int nbProcessedStatements = 0;
    private static int nbProcessedStatementsInBatch = 0;
    private final static int NB_REQUESTS_BETWEEN_STATS = 10000;
    private static long startTime = -1;

    private final static boolean BATCH = true;
    private final static int NB_REQUESTS_TO_PERFORM_IN_A_BATCH = 50;

    protected static Random clientRandom = new Random(123456789);

    public final static boolean TRACE = false;

    private static LinkedList<TPCW_User> users_READ = new LinkedList<TPCW_User>();
    private static LinkedList<TPCW_User> users_WRITE = new LinkedList<TPCW_User>();

    private static LinkedList<TPCW_User> users_READ_next = new LinkedList<TPCW_User>();
    private static LinkedList<TPCW_User> users_WRITE_next = new LinkedList<TPCW_User>();

    // args = NB_THREADS NB_TPCWS NB_USERS
    public static void main(String[] args) {
        NB_THREADS = Integer.parseInt(args[0]);
        NB_TPCWS = Integer.parseInt(args[1]);
        NB_USERS = Integer.parseInt(args[2]);
        TPCW_Database.split = NB_TPCWS;
        nbThreadsToWait = NB_THREADS;

        System.out.println("(Seq. Writes) seq. (// Reads)");
        System.out.println("Nb threads = " + NB_THREADS);
        System.out.println("Nb TPCWs = " + NB_TPCWS);
        System.out.println("Nb users = " + NB_USERS);

        try {
            Connection con = DriverManager.getConnection(URL, USER, PASSWORD);
            con.setAutoCommit(false);
            TPCW_Populate.populate(con, NB_TPCWS);
            TPCW_Database.verifyDBConsistency(con);

            for (int i = 0; i < NB_USERS; i++) {
                TPCW_User user = new TPCW_User(i, NB_TPCWS);
                if (user.isNextARead()) {
                    users_READ_next.addLast(user);
                } else {
                    users_WRITE_next.addLast(user);
                }
            }
            Thread[] threads = new Thread[NB_THREADS];
            for (int i = 0; i < NB_THREADS; i++) {
                threads[i] = new Thread(new TPCW_BenchmarkThread(i));
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

    public static class TPCW_BenchmarkThread implements Runnable {
        int id = -1;
        int integerField = 0;
        Random r;
        private static int writeThreadCount = 0;

        public TPCW_BenchmarkThread(int id) {
            this.id = id;
            r = new Random(id);
        }

        public void run() {
            int nbLocallyProcessed = 0;
            try {
                Connection conn = DriverManager.getConnection(URL, USER,
                        PASSWORD);
                conn.setAutoCommit(false);
                while (true) {
                    if (BATCH) {
                        synchronized (BARRIER_LOCK) {
                            nbThreadsToWait--;
                            if (nbThreadsToWait == 0) {
                                nbBatches++;
                                nbProcessedStatementsInBatch = 0;
                                nbThreadsToWait = NB_THREADS;
                                if (!users_READ.isEmpty()) {
                                    throw new IllegalStateException(
                                            "users_READ is not empty size = "
                                                    + users_READ.size());
                                }
                                if (!users_WRITE.isEmpty()) {
                                    throw new IllegalStateException(
                                            "users_WRITE is not empty");
                                }
                                users_READ = users_READ_next;
                                users_WRITE = users_WRITE_next;
                                users_READ_next = new LinkedList<TPCW_User>();
                                users_WRITE_next = new LinkedList<TPCW_User>();
                                BARRIER_LOCK.notifyAll();
                            } else {
                                BARRIER_LOCK.wait();
                            }
                        }
                    }
                    TPCW_User user = null;
                    boolean write = false;
                    while (true) {
                        synchronized (LOCK) {
                            if (user != null) {
                                if (user.isNextARead()) {
                                    users_READ_next.addLast(user);
                                } else {
                                    users_WRITE_next.addLast(user);
                                }
                            }

                            user = null;

                            if (BATCH) {
                                if (nbProcessedStatementsInBatch >= NB_REQUESTS_TO_PERFORM_IN_A_BATCH) {
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
                                }
                            }
                            nbProcessedStatements++;

                            if (user == null) {
                                if (!users_WRITE.isEmpty()) {
                                    writeThreadCount++;
                                    write = true;
                                    user = users_WRITE.removeFirst();
                                }
                            }

                            if (user == null) {
                                if (users_READ.isEmpty()) {
                                    break;
                                }
                                write = false;
                                user = users_READ.removeFirst();
                                while (writeThreadCount > 0)
                                    LOCK.wait();
                            }

							/*while (!users_WRITE.isEmpty())
                                                        {
                                                                if (id == 0)
                                                                {
                                                                        user = users_WRITE.removeFirst();
                                                                        break;
                                                                }
                                                                LOCK.wait();
                                                        }
                                                        if (user == null)
                                                        {
                                                                if (users_READ.isEmpty())
                                                                {
                                                                        break;
                                                                }
                                                                user = users_READ.removeFirst();
                                                        }*/

                        }
                        user.execute(conn);

                        if (TRACE) {
                            System.out.println("Statement #"
                                    + nbProcessedStatements + ": Thread #" + id
                                    + " executing user #" + user.id);
                        }
                        //System.out.println(user.isNextARead());
                        //user.execute(conn);
                        // executeStatement(conn);
                        nbLocallyProcessed++;
                        if (write) {
                            synchronized (LOCK) {
                                writeThreadCount--;
                                LOCK.notifyAll();
                            }
                        }
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }
    }
}
