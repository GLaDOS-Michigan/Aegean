package Applications.tpcw_ev;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.Random;

public class TPCW_Benchmark extends TPCW_Populate {
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
    private final static int NB_REQUESTS_TO_PERFORM_IN_A_BATCH = 50;
    private final static int NB_REQUESTS_BETWEEN_STATS = 10000;
    private static long startTime = -1;

    private final static boolean BATCH = true;

    protected static Random clientRandom = new Random(123456789);

    public final static boolean TRACE = false;

    private static LinkedList<TPCW_User> users = new LinkedList<TPCW_User>();

    // args = NB_THREADS NB_TPCWS NB_USERS
    public static void main(String[] args) {
        NB_THREADS = Integer.parseInt(args[0]);
        NB_TPCWS = Integer.parseInt(args[1]);
        NB_USERS = Integer.parseInt(args[2]);
        TPCW_Database.split = NB_TPCWS;
        nbThreadsToWait = NB_THREADS;

        System.out.println("(// Everything)");
        System.out.println("Nb threads = " + NB_THREADS);
        System.out.println("Nb TPCWs = " + NB_TPCWS);
        System.out.println("Nb users = " + NB_USERS);

        try {
            Connection con = DriverManager.getConnection(URL, USER, PASSWORD);
            con.setAutoCommit(false);
            TPCW_Populate.populate(con, NB_TPCWS);
            TPCW_Database.verifyDBConsistency(con);


            for (int i = 0; i < NB_USERS; i++) {
                users.addLast(new TPCW_User(i, NB_TPCWS));
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
                                BARRIER_LOCK.notifyAll();
                            } else {
                                BARRIER_LOCK.wait();
                            }
                        }
                    }
                    TPCW_User user = null;
                    while (true) {
                        synchronized (LOCK) {

                            if (user != null) {
                                users.addLast(user);
                            }

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
                                }
                            }
                            nbProcessedStatements++;
                            user = users.removeFirst();
                        }

                        if (TRACE) {
                            System.out.println("Thread #" + id
                                    + " executing user #" + user.id);
                        }
                        user.execute(conn);
                        // executeStatement(conn);
                        nbLocallyProcessed++;
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }

        protected void executeStatement(Connection con) throws SQLException {

            // if (TRACE)
            // {
            // System.out.println("Retrieving password for customer "
            // + customerName);
            // }
            // String password = TPCW_Database.GetPassword(customerName, con);
            // if (TRACE)
            // {
            // System.out.println("Password for customer " + customerName
            // + " = " + password);
            // }

            // if (TRACE)
            // {
            // System.out.println("Retrieving stock for book id " + bookId);
            // }
            // int stock = TPCW_Database.getStock(con, bookId);
            // if (TRACE)
            // {
            // System.out.println("Stock for book id " + bookId + " = "
            // + stock);
            // }

            // System.out.println("cart = " + cart);

            // int bookToAdd = bestSellers.get(0).i_id;
            // TPCW_Database.addItem(con, shoppingID, bookToAdd);
            // con.commit();

            // ids.add(bookToAdd);
            // quantities.add(2);

            // System.out.println("doCart shoppingID = " + shoppingID);
            // cart = TPCW_Database.doCart(shoppingID, null, ids, quantities,
            // con,
            // r);
            // System.out.println("cart after = " + cart);

            // TPCW_Database.clearCart(con, shoppingID);
            // con.commit();

            // String country = TPCW_Populate.countries[r
            // .nextInt(TPCW_Populate.NUM_COUNTRIES)];
            // TPCW_Database.doBuyConfirm(shoppingID, customer.c_id, "VISA",
            // 121234450, "CREDIT AGRICOLE", new Date(2010, 04, 27),
            // "4, avenue Rhin et Danube, 38100 GRENOBLE", "street_1",
            // "street_2", "city", "state", "zip", country, con, r);
        }
    }
}
