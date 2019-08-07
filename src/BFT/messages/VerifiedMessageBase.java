// $Id: VerifiedMessageBase.java 722 2011-07-04 09:24:35Z aclement $


package BFT.messages;

import util.UnsignedTypes;

public abstract class VerifiedMessageBase {

    public VerifiedMessageBase(int _tag, int _size,
                               int _auth_size) {
        tag = _tag;
        size = _size;
        authenticationSize = _auth_size;
        bytes = new byte[getTotalSize()];
        UnsignedTypes.intToBytes(tag, bytes, 0);
        UnsignedTypes.longToBytes(size, bytes, 2);
    }

    public VerifiedMessageBase(byte[] bits) {
        bytes = bits;

        // decipher the tag
        tag = UnsignedTypes.bytesToInt(bits, 0);

        // decipher the payload size
        size = (int) UnsignedTypes.bytesToLong(bits, 2);

        authenticationSize = bits.length - getPayloadSize() -
                getOffset();

        bytes = bits;
    }

    private int tag;
    private int size;
    private int authenticationSize;
    private byte[] bytes;

    /**
     * returns the total size of the byte representation of the message
     **/
    final public int getTotalSize() {
        return getPayloadSize() + getOffset() + getAuthenticationSize();
    }

    /**
     * returns the offset that subclasses should use in order to start
     * modifying the underlying byte array
     **/
    final static public int getOffset() {
        return verificationBaseSize;
    }

    final public int getAuthenticationSize() {
        return authenticationSize;
    }


    public boolean equals(VerifiedMessageBase m) {
        boolean res = tag == m.tag && size == m.size &&
                bytes.length == m.bytes.length;
        for (int i = 0; i < bytes.length && res; i++) {
            res = res && bytes[i] == m.bytes[i];
        }
        return res;
    }

    public boolean matches(VerifiedMessageBase m) {
        //BFT.Debug.kill(new RuntimeException("Not Yet Implemented"));
        return true;
    }

    public boolean isValid() {
        return true;
    }

    final public byte[] getBytes() {
        return bytes;
    }

    abstract public int getSender();

    public int getAuthenticationStartIndex() {
        return 0;
    }

    public int getAuthenticationEndIndex() {
        return getOffset() + getPayloadSize();
    }

    public int getAuthenticationLength() {
        return getAuthenticationEndIndex() - getAuthenticationStartIndex();
    }

    public int getTag() {
        return tag;
    }

    public int getPayloadSize() {
        return size;
    }

    private final static int verificationBaseSize = MessageTags.uint16Size + MessageTags.uint32Size;

    public String toString() {
        return "<VMB, tag:" + getTag() + ", payloadSize:" + getPayloadSize() + ", sender:" + getSender() + ">";
    }
}



