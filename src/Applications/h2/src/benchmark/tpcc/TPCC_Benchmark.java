/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package tpcc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class TPCC_Benchmark {
    public static int warehouses;
    public static int items;
    public static int districtsPerWarehouse;
    public static int customersPerDistrict;
    public static int ordersPerDistrict;
    public static int SIZE = 1000;

    public static AtomicInteger nbCommits = new AtomicInteger();
    public static AtomicInteger nbRollbacks = new AtomicInteger();
    public static AtomicInteger nbUpdates = new AtomicInteger();
    public static AtomicInteger nbSelects = new AtomicInteger();

    private final static String URL = "jdbc:h2:mem:testServer;MULTI_THREADED=1;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000;LOCK_MODE=3";
    private final static String USER = "sa";
    private final static String PASSWORD = "sa";

    private static int NB_THREADS;
    public static int NB_TPCCS;
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

    private static LinkedList<TPCC_User> users = new LinkedList<TPCC_User>();

    // args = NB_THREADS NB_TPCCS NB_USERS
    public static void main(String... args) throws Exception {
        NB_THREADS = Integer.parseInt(args[0]);
        NB_TPCCS = Integer.parseInt(args[1]);
        NB_USERS = Integer.parseInt(args[2]);

        nbThreadsToWait = NB_THREADS;

        System.out.println("Nb threads = " + NB_THREADS);
        System.out.println("Nb TPCCs = " + NB_TPCCS);
        System.out.println("Nb users = " + NB_USERS);

        items = SIZE * 10;
        warehouses = 10;
        districtsPerWarehouse = Math.max(1, SIZE / 100);
        customersPerDistrict = Math.max(1, SIZE / 100);
        ordersPerDistrict = Math.max(1, SIZE / 1000);

        System.out.println("size = " + SIZE);
        System.out.println("items = " + items);
        System.out.println("warehouses = " + warehouses);
        System.out.println("districtsPerWarehouse = " + customersPerDistrict);
        System.out.println("customersPerDistrict = " + items);
        System.out.println("ordersPerDistrict = " + ordersPerDistrict);

        try {
            Connection con = DriverManager.getConnection(URL, USER, PASSWORD);
            con.setAutoCommit(false);
            for (int i = 0; i < NB_TPCCS; i++) {
                TPCC_Populate.populate(con, i);
            }
            System.out.println("Finished populating the database");

            for (int i = 0; i < NB_USERS; i++) {
                users.addLast(new TPCC_User(i, NB_TPCCS));
            }
            Thread[] threads = new Thread[NB_THREADS];
            for (int i = 0; i < NB_THREADS; i++) {
                threads[i] = new Thread(new TPCC_BenchmarkThread(i));
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

    public static class TPCC_BenchmarkThread implements Runnable {
        int id = -1;
        int integerField = 0;
        Random r;

        public TPCC_BenchmarkThread(int id) {
            this.id = id;
            r = new Random(id);
        }

        public void run() {
            int nbLocallyProcessed = 0;
            try {
                Connection conn = DriverManager.getConnection(URL, USER,
                        PASSWORD);
                conn.setAutoCommit(false);
                Statement stat = conn.createStatement();
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
                    TPCC_User user = null;
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
                        user.execute(conn, stat);
                        // executeStatement(conn);
                        nbLocallyProcessed++;
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }
    }
}
