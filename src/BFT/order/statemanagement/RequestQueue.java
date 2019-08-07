//$Id: RequestQueue.java 57 2010-02-27 22:19:03Z yangwang $

package BFT.order.statemanagement;

import BFT.Parameters;
import BFT.messages.ClientMessage;

public class RequestQueue {

    protected int head;
    protected int size;
    protected int order[];
    protected ClientMessage[] reqs;
    protected boolean initialized;
    int bSize = 0;

    int entries;

    protected Parameters parameters;

    public RequestQueue(Parameters param) {
        parameters = param;
    }

    protected void init() {
        initialized = true;
        size = 0;
        head = 0;
        entries = 0;
        order = new int[parameters.getNumberOfClients() + 1];
        reqs = new ClientMessage[order.length - 1];
    }

    public synchronized int byteSize() {
        return 0;
    }

    public int size() {
        return entries;
        // 	int a = size;
        // 	int b = head;
        // 	if (a >= b)
        // 	    return a - b;
        // 	return order.length - (b-a);
    }

    /**
     * Adds the request to the queue.  Returns true if there was
     * previously no request for the sending client.  Returns false if
     * an existing request is replaced.
     **/
    synchronized public boolean add(ClientMessage req) {
        //System.out.println("****adding to rc queue");
        if (!initialized) {
            init();
        }
        int i = (int) req.getSendingClient();
        if (i > order.length) {
            BFT.Debug.kill(new RuntimeException("invalid client id"));
        }
        boolean present = reqs[i] != null;
        if (present) {
            if (reqs[i].getRequestId() <= req.getRequestId()) {
                bSize -= reqs[i].getTotalSize();
                reqs[i] = req;
                bSize += req.getTotalSize();
            }
            //System.out.println("adding to rc queue with false");
            return false;
        }
        reqs[i] = req;
        bSize += req.getTotalSize();
        // update the entry for the sending client, but if he's
        // already got a place in line, dont put him in line again

        order[size] = i;
        size++;
        if (size == order.length) {
            size = 0;
        }
        if (size == head) {
            BFT.Debug.kill("");
        }
        //System.out.println("adding to RC queue with true");
        entries++;
        return true;
    }

    synchronized public ClientMessage poll() {
        //System.out.println("pulling from RC queue");
        if (!initialized) {
            init();
        }
        if (head == size) {
            return null;
        }
        ClientMessage tmp = reqs[order[head++]];
        reqs[order[head - 1]] = null;
        if (head == order.length) {
            head = 0;
        }
        //System.out.println("pulled from rc queue");
        bSize -= tmp.getTotalSize();
        entries--;
        return tmp;
    }

    public boolean hasWork() {
        return entries > 0;
        //return head != size;
    }
}