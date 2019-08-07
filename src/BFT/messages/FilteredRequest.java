// $Id: FilteredRequest.java 722 2011-07-04 09:24:35Z aclement $

package BFT.messages;

import BFT.Parameters;


public class FilteredRequest extends FilteredRequestBase {


    public FilteredRequest(Parameters param, long sender, FilteredRequestCore[] pay) {
        super(param, MessageTags.FilteredRequest, sender, pay);
    }

    public FilteredRequest(byte[] bytes, Parameters param) {
        super(bytes, param);
    }

    public int getSendingReplica() {
        return (int) getSender();
    }
}