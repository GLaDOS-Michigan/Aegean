// $Id: NullAuthenticatedMessage.java 722 2011-07-04 09:24:35Z aclement $

package BFT.messages;

import util.UnsignedTypes;

abstract public class NullAuthenticatedMessage extends VerifiedMessageBase {

    public NullAuthenticatedMessage(int _tag, int _size, long send) {
        super(_tag, _size, 0);
        sender = send;
        int offset = getTotalSize();
        byte[] bytes = getBytes();
        util.UnsignedTypes.longToBytes(send, bytes, offset);
    }

    long sender;

    final public int getSender() {
        return (int) sender;
    }

    public NullAuthenticatedMessage(byte[] bytes) {
        super(bytes);
        int offset = getTotalSize() - getAuthenticationSize();
        sender = util.UnsignedTypes.bytesToLong(bytes, offset);
        offset += UnsignedTypes.uint32Size;
        if (offset != getTotalSize() - actualAuthenticationSize()) {
            BFT.Debug.kill("BAD nullauthenticatedmessage CONSTRUCTION");
        }
    }


    public static int computeAuthenticationSize() {
        return 0;
    }

    public int getAuthenticationEndIndex() {
        return getTotalSize();
    }

    private static int actualAuthenticationSize() {
        return 0;
    }
}
