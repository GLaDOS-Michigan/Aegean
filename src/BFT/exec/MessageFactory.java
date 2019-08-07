// $Id: MessageFactory.java 645 2011-02-22 21:30:28Z yangwang $

package BFT.exec;

import BFT.Parameters;
import BFT.exec.messages.*;
import BFT.messages.BatchSuggestion;
import BFT.messages.FilteredBatchSuggestion;
import BFT.messages.ForwardReply;
import BFT.messages.VerifiedMessageBase;

/**
 * Generates Messages that are used to communicate between
 * client/order/execution nodes from the specified byte array
 */
public class MessageFactory extends BFT.MessageFactory {

    public MessageFactory(Parameters param) {
        super(param);
    }

    public VerifiedMessageBase fromBytes(byte[] bytes) {
        byte[] tmp = new byte[2];
        tmp[0] = bytes[0];
        tmp[1] = bytes[1];
        int tag = BFT.util.UnsignedTypes.bytesToInt(tmp);
        switch (tag) {
            case BFT.messages.MessageTags.ForwardReply:
//                System.out.println("returning forward reply");
                return new ForwardReply(bytes);
            case BFT.messages.MessageTags.FilteredBatchSuggestion:
//                System.out.println("aha burda");
                return new FilteredBatchSuggestion(bytes, parameters);
            case (MessageTags.ExecuteBatch):
                return new ExecuteBatchMessage(bytes, parameters);
            case (MessageTags.FetchState):
                return new FetchStateMessage(bytes, parameters);
            case (MessageTags.AppState):
                return new AppStateMessage(bytes);
            case (MessageTags.PBRollback):
                return new PBRollbackMessage(bytes, parameters);
            case (MessageTags.PBFetchState):
                return new PBFetchStateMessage(bytes, parameters);
            default:
                return super.fromBytes(bytes);
        }

    }

}

