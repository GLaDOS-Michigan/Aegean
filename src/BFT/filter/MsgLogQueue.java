package BFT.filter;

import java.util.Vector;
import java.util.concurrent.ArrayBlockingQueue;

public class MsgLogQueue {

    Vector<ArrayBlockingQueue<MsgLogWrapper>> queues;
    int size[];

    public MsgLogQueue() {
        queues = new Vector<ArrayBlockingQueue<MsgLogWrapper>>();
        size = new int[3];
        for (int i = 0; i < 3; i++) {
            queues.add(new ArrayBlockingQueue<MsgLogWrapper>(1024));
            size[i] = 0;
        }
    }

    public void addWork(int num, MsgLogWrapper nb) {
        synchronized (queues.get(num)) {
            boolean in = false;
            while (in == false) {
                try {
                    queues.get(num).put(nb);
                    in = true;
                    size[num]++;
                } catch (InterruptedException e) {
                }
            }
            queues.get(num).notifyAll();
        }
    }

    public int size(int i) {
        return size[i];
    }


    public void hasWork(int i) {
        synchronized (queues.get(i)) {
            while (size[i] == 0)
                try {
                    queues.get(i).wait(10000);
                } catch (Exception e) {
                }
        }
    }


    public MsgLogWrapper getNBWork(int num) {
        MsgLogWrapper ret = null;
//     	boolean out = false;
//     	while (out == false) {
// 			try {
        synchronized (queues.get(num)) {
            ret = queues.get(num).poll();//block?20000:1, TimeUnit.MILLISECONDS);
            if (ret != null)
                size[num]--;
        }
        // 				out = true;
// 				//System.out.println("got something");
// 			} catch (InterruptedException e) {
// 			}
// 		}
        return ret;
    }

}