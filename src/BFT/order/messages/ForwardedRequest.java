// $Id: ForwardedRequest.java 49 2010-02-26 19:33:49Z yangwang $

package BFT.order.messages;

import BFT.Parameters;
import BFT.messages.Request;
import BFT.messages.SignedRequestCore;

public class ForwardedRequest extends Request {

    public ForwardedRequest(Parameters param, long sender, SignedRequestCore pay) {
        super(param, MessageTags.ForwardedRequest, sender, pay);
    }

    public ForwardedRequest(byte[] bytes, Parameters param) {
        super(bytes, param);
    }

    public int getSendingReplica() {
        return (int) getSender();
    }
}