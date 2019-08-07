package Applications.benchmark;

import java.util.LinkedList;

/**
 * Created by david on 3/21/15.
 */
public class MessageDigestTest {

    public static void main(String[] args) {
        int threads = Integer.parseInt(args[0]);
        int loops = Integer.parseInt(args[1]);

        LinkedList<Thread> threadList = new LinkedList<Thread>();
        long start = System.nanoTime();
        for (int i = 0; i < threads; i++) {
            DigestThread dt = new DigestThread(loops);
            dt.start();
            threadList.addLast(dt);
        }
        for (Thread t : threadList) {
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.out.println((System.nanoTime() - start) / 1e6 + "ms");
    }

    public static class DigestThread extends Thread {

        int loops;

        public DigestThread(int l) {
            loops = l;
        }

        @Override
        public void run() {
            int[] spinArray = new int[100];

            byte[] randomBytes = new byte[1024];
            for (int i = 0; i < loops; i++) {
                for (int j = 0; j < 100; j++) {
                    spinArray[j] = (spinArray[j] + 1) % 10000;
                }
                /*
                MessageDigest m = null;
                try {
                    m = MessageDigest.getInstance("SHA-256");
                    m.update(randomBytes, 0, randomBytes.length);
                    randomBytes = m.digest();
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                }*/
            }
        }
    }
}
