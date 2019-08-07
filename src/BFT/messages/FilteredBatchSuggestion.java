package BFT.messages;

import BFT.Parameters;

import java.util.Arrays;

public class FilteredBatchSuggestion extends MacArrayMessage {
    GroupClientRequests gcr;

    public FilteredBatchSuggestion(Parameters param, int sender,GroupClientRequests gcr) {
        super(param, MessageTags.FilteredBatchSuggestion, gcr.getTotalSize(), sender, param.useVerifier ? param.getExecutionCount() : param.getOrderCount());
        byte[] bytes = getBytes();
        int offset = getOffset();
        byte[] tmp = gcr.getBytes();
    //    System.out.println("bytes in first constructor: " + Arrays.toString(tmp));
        for (int i = 0; i < tmp.length; i++, offset++)
            bytes[offset] = tmp[i];
        this.gcr = gcr;
    }

    public FilteredBatchSuggestion(byte[] bytes, Parameters param) {
        super(bytes, param);
        if (getTag() != MessageTags.FilteredBatchSuggestion) {
            throw new RuntimeException("invalid message Tag: " + getTag());
        }

        int offset = getOffset();

        byte[] tmp = Arrays.copyOfRange(bytes, offset, bytes.length - getAuthenticationSize());
 //       System.out.println("bytes in second constructor: " + Arrays.toString(tmp));
        GroupClientRequests gcr = GroupClientRequests.deserialize(tmp);
        this.gcr = gcr;
  //     System.out.println("after deserialization: " + gcr);
        offset += gcr.getTotalSize();

        if (offset != getBytes().length - getAuthenticationSize()) {
            throw new RuntimeException("Invalid byte input");
        }
    }

    public GroupClientRequests getGcr() {
        return gcr;
    }
}
