package BFT.order;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;


//public class OrderWorkQueue extends OrderNetworkWorkQueue implements NBLogQueue, CPQueue, RequestCoreQueue {
public class NBQueue implements NBLogQueue {

    private ArrayBlockingQueue<NBLogWrapper> nbQueueA = null;
    private ArrayBlockingQueue<NBLogWrapper> nbQueueB = null;

    private OrderBaseNode osn;

    public NBQueue() {
        nbQueueA = new ArrayBlockingQueue<NBLogWrapper>(1024, true);
        nbQueueB = new ArrayBlockingQueue<NBLogWrapper>(1024, true);
    }

    /* (non-Javadoc)
     * @see BFT.order.NBLogQueue#addWork(int, BFT.order.NBLogWrapper)
     */
    public void addWork(int num, NBLogWrapper nb) {
        switch (num) {
            case 0:
                //			BFT.Debug.printQueue("NBQA ADD " + nbQueueA.size());
                nbQueueA.add(nb);
                break;
            case 1:
                //			BFT.Debug.printQueue("NBQB ADD " + nbQueueB.size());
                nbQueueB.add(nb);
                break;
            default:
                BFT.Debug.kill(new RuntimeException("Unsupported queue id " + num));
        }
    }

    /* (non-Javadoc)
     * @see BFT.order.NBLogQueue#getNBWork(int)
     */
    public NBLogWrapper getNBWork(int num, boolean block) {
        NBLogWrapper retNB = null;
        try {
            switch (num) {
                case 0:
                    retNB = nbQueueA.poll(block ? 20000 : 1, TimeUnit.MILLISECONDS);
                    //				BFT.Debug.printQueue("NBQA REM " + nbQueueA.size());
                    break;
                case 1:
                    retNB = nbQueueB.poll(block ? 20000 : 1, TimeUnit.MILLISECONDS);
                    //				BFT.Debug.printQueue("NBQB REM " + nbQueueB.size());
                    break;
                default:
                    BFT.Debug.kill(new RuntimeException("Unsupported queue id"));
            }
        } catch (InterruptedException e) {
        }
        return retNB;
    }


}
