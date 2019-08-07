// $Id: MessageFactory.java 722 2011-07-04 09:24:35Z aclement $

package BFT;

import BFT.messages.*;

import java.io.File;
import java.util.Vector;

/**
 * Generates Messages that are used to communicate between
 * client/order/execution nodes from the specified byte array
 */
public class MessageFactory {

    protected Parameters parameters;

    public MessageFactory(Parameters param) {
        parameters = param;
    }

    public VerifiedMessageBase fromBytes(byte[] bytes) {
        int tag = util.UnsignedTypes.bytesToInt(bytes, 0);
        VerifiedMessageBase vmb;
        Debug.debug(Debug.MODULE_EXEC, "\t<MessageFactory>: receive msg.tag=%s", tag);
        switch (tag) {
            case MessageTags.BatchSuggestion:
//                System.out.println("aha burda");
                return new BatchSuggestion(bytes, parameters);
            case MessageTags.ClientRequest:
                return new ClientRequest(bytes, parameters);
            case MessageTags.FilteredRequest:
                return new FilteredRequest(bytes, parameters);
            case MessageTags.SpeculativeNextBatch:
                return new SpeculativeNextBatch(bytes, parameters);
            case MessageTags.BatchCompleted:
                return new BatchCompleted(bytes, parameters);
            case MessageTags.TentativeNextBatch:
                return new TentativeNextBatch(bytes, parameters);
            case MessageTags.CommittedNextBatch:
                return new CommittedNextBatch(bytes, parameters);
            case MessageTags.ReleaseCP:
                return new ReleaseCP(bytes, parameters);
            case MessageTags.Retransmit:
                return new Retransmit(bytes, parameters);
            case MessageTags.LoadCPMessage:
                return new LoadCPMessage(bytes);
            case MessageTags.LastExecuted:
                return new LastExecuted(bytes, parameters);
            case MessageTags.CPLoaded:
                return new CPLoaded(bytes, parameters);
            case MessageTags.CPTokenMessage:
                return new CPTokenMessage(bytes, parameters);
            case MessageTags.Reply:
                return new Reply(bytes);
            case MessageTags.ForwardReply:
//                System.out.println("as planned");
                return new ForwardReply(bytes);
            case MessageTags.RequestCP:
                return new RequestCP(bytes, parameters);
            case MessageTags.WatchReply:
                return new WatchReply(bytes);
            case MessageTags.ReadOnlyRequest:
                return new ReadOnlyRequest(bytes, parameters);
            case MessageTags.ReadOnlyReply:
                return new ReadOnlyReply(bytes);
            case MessageTags.FetchCommand:
                return new FetchCommand(bytes, parameters);
            case MessageTags.ForwardCommand:
                return new ForwardCommand(bytes, parameters);
            case MessageTags.FetchDenied:
                return new FetchDenied(bytes, parameters);
            case MessageTags.CPUpdate:
                return new CPUpdate(bytes, parameters);
            case MessageTags.CacheCommand:
                return new CacheCommand(bytes, parameters);
            case MessageTags.Verify:
                return new VerifyMessage(bytes, parameters);
            case MessageTags.VerifyResponse:
                return new VerifyResponseMessage(bytes, parameters);
            case MessageTags.ExecViewChange:
                return new ExecViewChangeMessage(bytes, parameters);
            case MessageTags.NoOP:
                return new NoOPMessage(bytes, parameters);
            case MessageTags.OldRequestMessage:
                return new OldRequestMessage(bytes);
            default:
                BFT.Debug.kill(new RuntimeException("Invalid Message Tag: " + tag));
                return null;
        }
    }

    public static VerifiedMessageBase fromBytes(byte[] bytes, Parameters param) {
        MessageFactory factory = new MessageFactory(param);
        return factory.fromBytes(bytes);
    }

    /**
     * Reads from the file and returns a vector of messages from the file *
     */
    public static Vector<VerifiedMessageBase> fromFile(File file) {

        return null;
    }

}

