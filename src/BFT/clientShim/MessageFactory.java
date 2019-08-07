// $Id$

package BFT.clientShim;

import BFT.Parameters;
import BFT.clientShim.messages.CPStateMessage;
import BFT.clientShim.messages.FetchCheckPointState;
import BFT.clientShim.messages.MessageTags;
import BFT.messages.VerifiedMessageBase;

/**
 * Generates Messages that are used to communicate between
 * client/order/execution nodes from the specified byte array
 **/
public class MessageFactory extends BFT.MessageFactory {

    public MessageFactory(Parameters param) {
        super(param);
        // TODO Auto-generated constructor stub
    }

    public VerifiedMessageBase fromBytes(byte[] bytes) {

        int tag = util.UnsignedTypes.bytesToInt(bytes, 0);
        switch (tag) {
            case (MessageTags.FetchCheckPointState):
                return new FetchCheckPointState(bytes);
            case (MessageTags.CPStateMessage):
                return new CPStateMessage(bytes);
            default:
                return BFT.MessageFactory.fromBytes(bytes, parameters);
        }
    }

    public static VerifiedMessageBase fromBytes(byte[] bytes, Parameters param) {
        MessageFactory factory = new MessageFactory(param);
        return factory.fromBytes(bytes);
    }
}

