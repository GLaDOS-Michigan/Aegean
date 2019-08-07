package BFT.exec;


/**
 * Created by IntelliJ IDEA.
 * User: Yang Wang
 * Date: 2009-2-7
 * Time: 18:47:23
 * To change this template use File | Settings | File Templates.
 */
public class RequestInfo implements Comparable<RequestInfo> , Info {
    private boolean readonly = false;
    private int clientId;
    private int subId;
    private long seqNo;
    private long requestId;
    private long time;
    private long random;
    private boolean lastReqBeforeCP = false;

    public RequestInfo() {
    }

    public RequestInfo(boolean readonly, int clientId, long seqNo, long requestId, long time, long random) {
        this(readonly, clientId, -1, seqNo, requestId, time, random);
    }

    public RequestInfo(boolean readonly, int clientId, int subId, long seqNo, long requestId, long time, long random) {
        this.readonly = readonly;
        this.clientId = clientId;
        this.subId = subId;
        this.seqNo = seqNo;
        this.requestId = requestId;
        this.time = time;
        this.random = random;
    }

    public RequestInfo(RequestInfo info) {
        this.readonly = info.readonly;
        this.clientId = info.clientId;
        this.subId = info.subId;
        this.seqNo = info.seqNo;
        this.requestId = info.requestId;
        this.time = info.time;
        this.random = info.random;
        this.lastReqBeforeCP = info.lastReqBeforeCP;
    }

    public boolean isReadonly() {
        return readonly;
    }

    public int getClientId() {
        return clientId;
    }

    public int getSubId() {
        return subId;
    }

    public long getSeqNo() {
        return seqNo;
    }

    public long getRequestId() {
        return requestId;
    }

    public long getTime() {
        return time;
    }

    public long getRandom() {
        return random;
    }

    public boolean isLastReqBeforeCP() {
        return lastReqBeforeCP;
    }

    public void setLastReqBeforeCP() {
        lastReqBeforeCP = true;
    }

    public int compareTo(RequestInfo target) {
        if (this.clientId < target.clientId)
            return -1;
        else if (this.clientId > target.clientId)
            return 1;
        if (this.subId < target.subId)
            return -1;
        else if (this.subId > target.subId)
            return 1;
        else if (this.requestId < target.requestId)
            return -1;
        else if (this.requestId > target.requestId)
            return 1;
        else
            return 0;
    }


    public String toString() {
        return "clientId=" + clientId + "subId=" + subId + " seqNo=" + seqNo + " requestId=" + requestId;
    }
}
