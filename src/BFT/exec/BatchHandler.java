/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package BFT.exec;

/**
 * @author yangwang
 */
public class BatchHandler {
/*
    private RequestFilter filter;
    private RequestHandler handler;
    private LinkedList<Entry> entries = new LinkedList<Entry>();
    private HashMap<Object, Boolean> allKeys = new HashMap<Object, Boolean>();
    private HashMap<Entry, RequestInfo> requestInfos = new HashMap<Entry, RequestInfo>();
    private HashMap<RequestInfo, List<Object>> requestKeys = new HashMap<RequestInfo, List<Object>>();
    private boolean sequential;
    private int requestNo;
    private int replyNo;

    public BatchHandler(RequestFilter filter, RequestHandler handler) {
        this.filter = filter;
        this.handler = handler;
    }

    public void processBatch(CommandBatch batch, long seqNo, NonDeterminism nd, boolean sequential) {
        assert isFinished();
        this.sequential = sequential;
        this.requestNo = batch.getEntryCount();
        this.replyNo =0;
        entries.clear();
        requestInfos.clear();
        allKeys.clear();
        requestKeys.clear();
        Entry[] tmp = batch.getEntries();
        for (int i = 0; i < tmp.length; i++) {
            entries.add(tmp[i]);
	    if(tmp[i].getCommand()==null)
		throw new RuntimeException("Haha "+i);
        }
        generateInfoAndKeys(seqNo, nd);
        processAllPossibleRequests();
    }

    public void processReply(RequestInfo info) {
        Debug.println("Reply " + info);
        if (!sequential) {
            List<Object> keys = this.requestKeys.get(info);
            boolean isPending = false;
            for (Object key : keys) {
                if (allKeys.get(key) == true) {
                    isPending = true;
                }
                allKeys.remove(key);
            }
            if (isPending) {
                processAllPossibleRequests();
            }
        } else {
            processAllPossibleRequests();
        }
        synchronized(this){
            this.replyNo++;
            if(this.replyNo == this.requestNo)
                this.notifyAll();
        }
    }

    public synchronized void waitUntilFinished() throws InterruptedException{
        while(this.replyNo != this.requestNo)
            this.wait();
    }

    public synchronized boolean isFinished() {
        return this.replyNo == this.requestNo;
    }

    private void generateInfoAndKeys(long seqNo, NonDeterminism nd) {
        for (Entry e : entries) {
            //boolean readonly, int clientId, long seqNo, long requestId, long time, long random
            RequestInfo info = new RequestInfo(false, (int) e.getClient(), seqNo,
                    e.getRequestId(), nd.getTime(), nd.getSeed());
            requestInfos.put(e, info);
            if (!sequential) {
		if(e.getCommand()==null)
		   throw new RuntimeException("e.getCommand==null"); 
                List<Object> keys = filter.generateKeys(e.getCommand());
                requestKeys.put(info, keys);
            }
        }
    }

    private void processAllPossibleRequests() {
        Debug.println("start processAllPossibleRequests");
        if (!sequential) {
            Iterator<Entry> iter = this.entries.iterator();
            while (iter.hasNext()) {
                Entry e = iter.next();
                RequestInfo info = requestInfos.get(e);
                List<Object> keys = requestKeys.get(info);
                boolean isExecutable = true;
                for (Object key : keys) {
                    if (allKeys.containsKey(key)) {

                        isExecutable = false;
                        allKeys.put(key, true);
                        break;
                    }
                }
                if (isExecutable) {
                    for (Object key : keys) {
                        allKeys.put(key, false);
                    }
                    handler.execRequest(e.getCommand(), info);
                    iter.remove();
                }

            }
        } else {
            Iterator<Entry> iter = this.entries.iterator();
            if (iter.hasNext()) {
                Entry e = iter.next();
                RequestInfo info = requestInfos.get(e);
                handler.execRequest(e.getCommand(), info);
                iter.remove();

            }
        }

        Debug.println("end processAllPossibleRequests");
    }

    /*
    //For test
    LinkedList<RequestInfo> received =new LinkedList<RequestInfo>();
    private void sendRequestToApp(byte []request, RequestInfo info){
    handler.execRequest(request, info);
    }

    //For test
    private void replyAll(){
    LinkedList<RequestInfo> tmp=new LinkedList<RequestInfo>(received);
    received.clear();
    for(RequestInfo info:tmp){
    this.processReply(info);
    }
    }

    public static void main(String []args){
    Entry entry1=new Entry(0, 0, new byte[]{1,4});
    Entry entry2=new Entry(1, 0, new byte[]{3});
    Entry entry3=new Entry(2, 0, new byte[]{3,4});
    Entry entry4=new Entry(3, 0, new byte[]{4});
    CommandBatch batch = new CommandBatch(new Entry[]{entry1, entry2, entry3, entry4});
    BatchHandler handler = new BatchHandler(new TestFilter(), null);
    handler.processBatch(batch, 0, new NonDeterminism(1,2));
    while(!handler.isFinished()){
    handler.replyAll();
    }
    }

    static class TestFilter implements RequestFilter{
    public List<Object> generateKeys(byte []request){
    LinkedList<Object> ret=new LinkedList<Object>();
    for(int i=0;i<request.length;i++)
    ret.add(new Byte(request[i]));
    return ret;
    }
    }*/
}
