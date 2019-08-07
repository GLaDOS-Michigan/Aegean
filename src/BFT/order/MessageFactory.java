// $Id: MessageFactory.java 722 2011-07-04 09:24:35Z aclement $

package BFT.order;

import BFT.messages.VerifiedMessageBase;
import BFT.order.messages.*;

/**
 * Converts a byte array to an appropriate message that is either
 * handled or sent by an order replica
 **/
public class MessageFactory {


    static public VerifiedMessageBase fromBytes(byte[] bytes, BFT.Parameters param) {
        int tag = util.UnsignedTypes.bytesToInt(bytes, 0);
        VerifiedMessageBase vmb;
        switch (tag) {
            case MessageTags.ForwardedRequest:
                return new ForwardedRequest(bytes, param);
            case MessageTags.PrePrepare:
                return new PrePrepare(bytes, param);
            case MessageTags.Prepare:
                return new Prepare(bytes, param);
            case MessageTags.Commit:
                return new Commit(bytes, param);
            case MessageTags.ViewChange:
                return new ViewChange(bytes, param);
            case MessageTags.ViewChangeAck:
                return new ViewChangeAck(bytes, param);
            case MessageTags.NewView:
                return new NewView(bytes, param.largeOrderQuorumSize(),
                        param.getOrderCount(), param);
            case MessageTags.ConfirmView:
                return new ConfirmView(bytes, param);
            case MessageTags.MissingViewChange:
                return new MissingViewChange(bytes);
            case MessageTags.RelayViewChange:
                return new RelayViewChange(bytes,
                        param.getOrderCount(), param);
            case MessageTags.MissingOps:
                return new MissingOps(bytes, param);
            case MessageTags.RelayOps:
                return new RelayOps(bytes, param);
            //case MessageTags.OpUpdate: return new OpUpdate(bytes);
            //	case MessageTags.CPToken: return new CPToken(bytes);
            case MessageTags.MissingCP:
                return new MissingCP(bytes, param);
            case MessageTags.RelayCP:
                return new RelayCP(bytes, param);
            case MessageTags.OrderStatus:
                return new OrderStatus(bytes, param);
            case MessageTags.StartView:
                return new StartView(bytes, param);
            case MessageTags.CommitView:
                return new CommitView(bytes, param);
            default:
                BFT.MessageFactory factory = new BFT.MessageFactory(param);
                return factory.fromBytes(bytes);
        }
    }
}

