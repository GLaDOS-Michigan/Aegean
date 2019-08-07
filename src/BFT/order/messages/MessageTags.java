// $Id: MessageTags.java 396 2010-06-02 00:03:43Z aclement $

package BFT.order.messages;

public class MessageTags extends BFT.messages.MessageTags {
    final public static int PrePrepare = 40;
    final public static int Prepare = 41;
    final public static int Commit = 42;
    final public static int ViewChange = 43;
    final public static int ViewChangeAck = 44;
    final public static int NewView = 45;
    final public static int ConfirmView = 46;
    final public static int MissingViewChange = 47;
    final public static int RelayViewChange = 48;
    final public static int MissingOps = 49;
    final public static int RelayOps = 30;
    final public static int MissingCP = 32;
    final public static int RelayCP = 33;
    final public static int CPMessage = 34;
    final public static int OrderStatus = 35;
    final public static int ForwardedRequest = 36;
    final public static int StartView = 37;
    final public static int CommitView = 38;

}