// $Id$

package BFT.serverShim;

import BFT.Parameters;
import BFT.messages.VerifiedMessageBase;
import BFT.serverShim.messages.*;

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
//        System.err.println("ShimBaseNode: messageFactory tags=" + tag);
        switch (tag) {
            case (MessageTags.FetchCPMessage):
                return new FetchCPMessage(bytes, parameters);
            case (MessageTags.CPStateMessage):
                return new CPStateMessage(bytes);
            case (MessageTags.FetchState):
                return new FetchState(bytes, parameters);
            case (MessageTags.AppState):
                return new AppState(bytes);
            default:
                return BFT.MessageFactory.fromBytes(bytes, parameters);
        }
    }

    public static VerifiedMessageBase fromBytes(byte[] bytes, Parameters param) {
        MessageFactory factory = new MessageFactory(param);
        return factory.fromBytes(bytes);
    }

}

