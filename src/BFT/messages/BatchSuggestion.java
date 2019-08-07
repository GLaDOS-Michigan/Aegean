package BFT.messages;

import BFT.Parameters;

import java.util.Arrays;

public class BatchSuggestion extends MacArrayMessage {
    GroupClientRequests core;

    public BatchSuggestion(Parameters param, long sender, long subId, RequestCore pay, boolean primaryBackup) {
        //super(param, MessageTags.BatchSuggestion, sender, subId, pay, primaryBackup);
        super(param, MessageTags.BatchSuggestion, pay.getTotalSize(), sender, subId, primaryBackup ? param.getExecutionCount() : param.getFilterCount());
        byte[] bytes = getBytes();
        int offset = getOffset();

        byte[] coreBytes = pay.getBytes();
        for (int i = 0; i < coreBytes.length; i++, offset++) {
            bytes[offset] = coreBytes[i];
        }

        core = (GroupClientRequests) pay;

    }

    public BatchSuggestion(byte[] bytes, Parameters param) {
        super(bytes, param);

//        System.out.println("should try batchsuggestion");
        int offset = getOffset();
//        byte[] tmp = new byte[bytes.length - getOffset()];
//        for (int i = 0; i < tmp.length; i++, offset++) {
//            tmp[i] = bytes[offset];
//        }

        if(getTag() == MessageTags.BatchSuggestion) {
            core = GroupClientRequests.deserialize(Arrays.copyOfRange(bytes, offset, bytes.length-getAuthenticationSize()));
            //core = GroupClientRequests.deserialize(tmp);
//            System.out.println("tried batchsuggestion");
            getAuthenticationSize();
        }
        else {
            throw new RuntimeException("Invalid tag");
        }
    }

    public GroupClientRequests getCore() {
        return core;
    }

    @Override
    public boolean matches(VerifiedMessageBase m) {
        BatchSuggestion secondSuggestion = (BatchSuggestion) m;
        if( Arrays.equals(secondSuggestion.core.getStateHash(), core.getStateHash())) {
            return true;
        }
        else {
            throw new RuntimeException("Unexpectedly diffreent hashes");
        }
    }
}
