package Applications.jetty.eve_connector;

import BFT.exec.Info;
import BFT.exec.RequestInfo;

public class ServletReply {
    private Info requestInfo = null;
    private byte[] replyData = null;

    public ServletReply(byte[] replyData, Info requestInfo) {
        this.replyData = replyData;
        this.requestInfo = requestInfo;
    }

    public Info getRequestInfo() {
        return this.requestInfo;
    }

    public byte[] getReplyData() {
        return this.replyData;
    }
}
