package Applications.benchmark;

import BFT.Parameters;

/**
 * @author david
 */
public class BenchRequestTime {

    private static Parameters parameters = new Parameters();

    private static final int NUM_TRIALS = 1;

    private static long testRequest(long spins) {

        // Run this many times and take the average
        long[] trials = new long[NUM_TRIALS];
        for (int j = 0; j < NUM_TRIALS; j++) {
            //int [] spinArray = new int[100];
            // Chosen randomly by the JRNG
            //int index = (int)(Math.random() * 100);
            long start = System.nanoTime();
            /*
            byte[] randomBytes = new byte[1024];

            for (int i = 0; i < spins; i++) {
                BenchDigest digest = new BenchDigest(parameters, randomBytes);
            }*/
            /* for (int i = 0; i < spins; i++) {
                for (int k = 0; k < 100; k++) {
                    spinArray[index] = (spinArray[index] + 1) % 10000;
                }
            }
            for (long i = 0; i < spins; i++) {
                s = (s - i) % ((i + 1) * spins);
            }*/
            long time = spins;
            long instart = System.nanoTime();
            long inend = System.nanoTime();
            while (time > (inend - instart)) {
                inend = System.nanoTime();
            }
            long s = System.nanoTime();
            // Take the difference.
            trials[j] = System.nanoTime() - start;
        }

        double total = 0.0;
        for (long l : trials) {
            total += l;
        }
        return (long) (total / NUM_TRIALS);
    }

    /**
     * Finds the number of spins needed for a request of the specified target length
     * (to some epsilon)
     *
     * @param targetNanos the number of nanoseconds the request should take
     * @return the number of spins needed
     */
    private static long findSpins(long targetNanos) {
        // First exponentially try more spins until there are too many, then binary search
        long spins = 1;
        long time;
        do {
            spins <<= 1;
            time = testRequest(spins);
        } while (spins < Long.MAX_VALUE >> 1 && time < targetNanos);

        long l = 0;
        long r = spins;
        long m = r;
        long lastM = m - 1;
        while (time != targetNanos && l < r) {
            m = (l + r) >> 1;
            if (m == lastM) {
                break;
            }
            lastM = m;
            time = testRequest(m);
            System.out.println(m + " spins took " + time + "ns");
            if (time == targetNanos) {
                break;
            } else if (time < targetNanos) {
                l = m;
            } else if (time > targetNanos) {
                r = m;
            }
        }
        return m;
    }

    public static void main(String[] args) {
        System.out.println("Finding spins for a 10s request...");
        //System.out.println(findSpins(10 * 1000 * 1000000L));


        System.out.println("Finding spins for a 1s request...");
        //System.out.println(findSpins(1000 * 1000000));

        System.out.println("Finding spins for a 100ms request...");
        //System.out.println(findSpins(100 * 1000000));

        System.out.println("Finding spins for a 10ms request...");
        System.out.println(findSpins(10 * 1000000));

        System.out.println("Finding spins for a 1ms request...");
        System.out.println(findSpins(1000000));

        System.out.println("Finding spins for a 0.1ms request...");
        System.out.println(findSpins(100000));
    }
}
