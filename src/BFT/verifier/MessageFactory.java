// $Id: MessageFactory.java 346 2010-05-02 16:56:03Z manos $

package BFT.verifier;

//import BFT.serverShim.messages.MessageTags;

import BFT.Parameters;
import BFT.messages.VerifiedMessageBase;
import BFT.messages.VerifyMessage;
import BFT.verifier.messages.*;

//import BFT.serverShim.messages.FetchCPMessage;
//import BFT.serverShim.messages.CPStateMessage;
//import BFT.serverShim.messages.FetchState;
//import BFT.serverShim.messages.AppState;

/**
 * Generates Messages that are used to communicate between
 * client/order/execution nodes from the specified byte array
 **/
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
        /*case (MessageTags.FetchCPMessage): return new FetchCPMessage(bytes);
		case (MessageTags.CPStateMessage): return new CPStateMessage(bytes);
		case (MessageTags.FetchState): return new FetchState(bytes);
		case (MessageTags.AppState): return new AppState(bytes);*/
            case MessageTags.Verify:
                return new VerifyMessage(bytes, parameters);
            case MessageTags.Prepare:
                return new Prepare(bytes, parameters);
            case MessageTags.Commit:
                return new Commit(bytes, parameters);
            case MessageTags.ViewChange:
                return new ViewChange(bytes, parameters);
            case MessageTags.ViewChangeAck:
                return new ViewChangeAck(bytes, parameters);
            case MessageTags.NewView:
                return new NewView(bytes, parameters);
            case MessageTags.ConfirmView:
                return new ConfirmView(bytes, parameters);
            default:
                return super.fromBytes(bytes);
        }
    }

    public static VerifiedMessageBase fromBytes(byte[] bytes, Parameters param) {
        MessageFactory factory = new MessageFactory(param);
        return factory.fromBytes(bytes);
    }

}