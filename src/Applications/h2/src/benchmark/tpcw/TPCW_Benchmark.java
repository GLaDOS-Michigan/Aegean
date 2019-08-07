package tpcw;

import BFT.Parameters;
import merkle.MerkleTree;
import merkle.MerkleTreeInstance;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.Random;

public class TPCW_Benchmark extends TPCW_Populate {
    private final static String URL_H2 = "jdbc:h2:mem:testServer;MULTI_THREADED=1;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000;LOCK_MODE=3";
    private final static String URL_DERBY = "jdbc:derby:memory:testServer;create=true";
    private final static String USER = "sa";
    private final static String PASSWORD = "sa";

    public static boolean USE_H2 = false;
    public static boolean USE_DERBY = false;

    private static int NB_THREADS;
    public static int NB_TPCWS;
    private static int NB_USERS;
    private static int PROBA_TO_BUY;
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

    // args = NB_THREADS NB_TPCWS NB_USERS h2|derby merkleTree|dummyTree
    // PROBA_TO_BUY
    public static void main(String[] args) throws Exception {

/*		if(true){
            MerkleTreeInstance.init(8, 10000000, 4, true);
			MerkleTreeInstance.add(URL_H2);	
			Connection con = DriverManager.getConnection(URL_H2, USER, PASSWORD);
			TPCW_Populate.populate(con, 0);
			MerkleTreeInstance.getShimInstance().getHash();
			((MerkleTree)MerkleTreeInstance.getShimInstance()).printTree();
			return;
		}*/
        NB_THREADS = Integer.parseInt(args[0]);
        NB_TPCWS = Integer.parseInt(args[1]);
        NB_USERS = Integer.parseInt(args[2]);

        if (args[3].equals("h2")) {
            USE_H2 = true;
        } else if (args[3].equals("derby")) {
            USE_DERBY = true;
        } else {
            throw new RuntimeException("Unknown DB");
        }

        if (args[4].equals("dummyTree")) {
            Parameters.useDummyTree = true;
        } else if (args[4].equals("merkleTree")) {
            Parameters.useDummyTree = false;
        } else {
            throw new RuntimeException("Unknown tree type");
        }

        PROBA_TO_BUY = Integer.parseInt(args[5]);

        MerkleTreeInstance.init(8, 10000000, 4, true);

        MerkleTreeInstance.add(URL_H2);

        nbThreadsToWait = NB_THREADS;

        System.out.println("(// Everything)");
        System.out.println("Nb threads = " + NB_THREADS);
        System.out.println("Nb TPCWs = " + NB_TPCWS);
        System.out.println("Nb users = " + NB_USERS);
        if (USE_H2) {
            System.out.println("DB = H2");
        }
        if (USE_DERBY) {
            System.out.println("DB = DERBY");
        }
        if (Parameters.useDummyTree) {
            System.out.println("Tree = dummy");
        } else {
            System.out.println("Tree = merkle");
        }
        System.out.println("Proba to buy = " + PROBA_TO_BUY);

        try {
            Connection con = null;
            if (USE_H2) {
                con = DriverManager.getConnection(URL_H2, USER, PASSWORD);
            } else if (USE_DERBY) {
                con = DriverManager.getConnection(URL_DERBY);
            }
            con.setAutoCommit(false);
            for (int i = 0; i < NB_TPCWS; i++) {
                TPCW_Populate.populate(con, i);
                TPCW_Database.verifyDBConsistency(con, i);
            }

            for (int i = 0; i < NB_USERS; i++) {
                users.addLast(new TPCW_User(i, NB_TPCWS, PROBA_TO_BUY));
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
                Connection conn = null;
                if (USE_H2) {
                    conn = DriverManager.getConnection(URL_H2, USER, PASSWORD);
                } else if (USE_DERBY) {
                    conn = DriverManager.getConnection(URL_DERBY);
                }
                conn.setAutoCommit(false);
                while (true) {
                    if (BATCH) {
                        synchronized (BARRIER_LOCK) {
                            nbThreadsToWait--;
                            if (nbThreadsToWait == 0) {
                                nbBatches++;
                                // System.out.println("Batch #" + nbBatches);
                                MerkleTreeInstance.getShimInstance()
                                        .setVersionNo(nbBatches);
                                MerkleTreeInstance.getShimInstance().getHash();
                                MerkleTreeInstance.getShimInstance()
                                        .makeStable(nbBatches - 1);

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

                                    MerkleTreeInstance.nbCallsToAdd = 0;
                                    MerkleTreeInstance.nbCallsToRemove = 0;
                                    MerkleTreeInstance.nbCallsToUpdate = 0;
                                    MerkleTreeInstance.nbCallsToLeafHash = 0;
                                    MerkleTreeInstance.nbObjectsUpdated = 0;
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

                                    ((MerkleTree) MerkleTreeInstance.getShimInstance()).printTree();
                                    System.exit(0);

                                }
                            }
                            nbProcessedStatements++;
                            user = users.removeFirst();
                        }

                        if (TRACE) {
                            System.out.println("Thread #" + id
                                    + " executing user #" + user.id);
                        }
                        //MerkleTreeInstance.getShimInstance().getHash();
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
