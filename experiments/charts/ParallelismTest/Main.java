import java.util.concurrent.CountDownLatch;

public class Main {

    public static void main(String[] args) {
        int numberOfThreads = Integer.parseInt(args[0]);
        long numberOfRounds = Long.parseLong(args[1]);
        long numberOfSpins = numberOfRounds * Long.parseLong(args[2]);
        boolean divideTask = false;

        if(args.length > 3)
        {
            divideTask = Boolean.parseBoolean(args[3]);
        }

        Thread[] threads = new Thread[numberOfThreads];
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(numberOfThreads);
        long spinsPerThread = divideTask ?  (long) numberOfSpins/numberOfThreads : numberOfSpins;

        for( int i = 0; i < numberOfThreads; i++) {
            threads[i] = new LoopThread(i, spinsPerThread, startSignal, doneSignal);
            threads[i].start();
        }
//        startSignal.countDown();
//        try {
//            doneSignal.await();
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
        long startTime = System.currentTimeMillis();

        for(Thread t: threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        long estimatedTime = System.currentTimeMillis() - startTime;
        System.out.println("time passed for " + numberOfThreads + " threads and " + numberOfSpins + " spins is " + estimatedTime/1000 + " seconds");
    }

    public static class LoopThread extends Thread {
        int index;
        long numberOfSpins;
        CountDownLatch startSignal;
        CountDownLatch doneSignal;

        public LoopThread(int index, long numberOfSpins, CountDownLatch startSignal, CountDownLatch doneSignal) {
            this.index = index;
            this.numberOfSpins = numberOfSpins;
            this.startSignal = startSignal;
            this.doneSignal = doneSignal;
        }

        public void run() {
//            try {
//                startSignal.await();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//            doneSignal.countDown();

            System.out.println("Thread " + index + " started for " + numberOfSpins + " spins");
            for(long j = 0; j < numberOfSpins; j++) {

            }
            System.out.println("Thread " + index + " ended: " + System.currentTimeMillis()/1000);
        }
    }
}


