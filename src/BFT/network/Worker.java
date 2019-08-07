// $Id: Worker.java 67 2010-03-05 21:22:02Z yangwang $

package BFT.network;

import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.List;


public class Worker implements Runnable {
    private List<DataEvent> queue = new LinkedList<DataEvent>();
    protected TCPNetwork net;

    public Worker(TCPNetwork n) {
        this.net = n;
        net.setWorker(this);
    }

    public void processData(SocketChannel socket, byte[] data, int count) {
        byte[] dataCopy = new byte[count];
        System.arraycopy(data, 0, dataCopy, 0, count);
        synchronized (queue) {
            queue.add(new DataEvent(socket, dataCopy));
            queue.notify();
        }
    }

    public void run() {
        DataEvent dataEvent;

        while (true) {
            // Wait for data to become available
            synchronized (queue) {
                while (queue.isEmpty()) {
                    try {
                        queue.wait();
                    } catch (InterruptedException e) {
                    }
                }
                dataEvent = queue.remove(0);
            }

            net.handleRawBytes(dataEvent.socket, dataEvent.data);
            // Return to sender
            //this.baseNode.send(dataEvent.socket, dataEvent.data);
        }
    }
} 



