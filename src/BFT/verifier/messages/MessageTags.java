// $Id: MessageTags.java 49 2010-02-26 19:33:49Z yangwang $

package BFT.verifier.messages;

public class MessageTags extends BFT.messages.MessageTags {
    final public static int Prepare = 61;
    final public static int Commit = 62;
    final public static int ViewChange = 63;
    final public static int ViewChangeAck = 64;
    final public static int NewView = 65;
    final public static int ConfirmView = 66;
    final public static int MissingViewChange = 67;
    final public static int RelayViewChange = 68;
}