import java.util.Random;

public class SimpleBenchmarkXen {
    private static int NB_THREADS;
    private final static int NB_REQUESTS_BETWEEN_STATS = 100;

    private static final Object LOCK = new Object();

    private static long startTime = -1;

    private static int nbProcessedStatements = 0;

    public static void main(String... args) throws Exception {
        NB_THREADS = Integer.parseInt(args[0]);
        System.out.println("Nb threads = " + NB_THREADS);

        runTest();
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
                                System.out.println("Thread #" + id + " (time="
                                        + System.currentTimeMillis() / 1000 + ")"
                                        + ": Nb statements = "
                                        + nbProcessedStatements
                                        + " Statements per second: "
                                        + statPerSec);
                            }
                        }
                        nbProcessedStatements++;
                    }

                    for (int i = 0; i < 1000000; i++) {
                        int j = r.nextInt();
                        j++;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
