// $Id: MacMessage.java 722 2011-07-04 09:24:35Z aclement $

package BFT.messages;

abstract public class MacMessage extends VerifiedMessageBase {

    public MacMessage(int _tag, int _size, long send) {
        super(_tag, _size, computeAuthenticationSize());
        sender = send;
        byte[] tmp;
        int offset = getTotalSize() - getAuthenticationSize();
        byte[] bytes = getBytes();
        util.UnsignedTypes.longToBytes(send, bytes, offset);
    }

    long sender;

    final public int getSender() {
        return (int) sender;
    }

    public MacMessage(byte[] bytes) {
        super(bytes);
        int offset = getTotalSize() - getAuthenticationSize();
        byte[] tmp = new byte[MessageTags.uint32Size];
        sender = util.UnsignedTypes.bytesToLong(bytes, offset);
        offset += util.UnsignedTypes.uint32Size;
        if (offset != getTotalSize() - actualAuthenticationSize()) {
            BFT.Debug.kill("BAD MACMESSAGE CONSTRUCTION");
        }
    }


    public static int computeAuthenticationSize() {
        return actualAuthenticationSize() +
                BFT.messages.MessageTags.uint32Size;
    }

    public int getAuthenticationEndIndex() {
        return getOffset() + getPayloadSize() + MessageTags.uint32Size;
    }

    private static int actualAuthenticationSize() {
        return MacBytes.size();
    }

//	public void generateMac(String key){ 
//		throw new RuntimeException("unimplemented");
//	}

    public MacBytes getMacBytes() {
        byte[] dst = new byte[actualAuthenticationSize()];
        System.arraycopy(getBytes(),
                getTotalSize() - actualAuthenticationSize(),
                dst, 0, actualAuthenticationSize());
        return new MacBytes(dst);
    }

    public void setMacBytes(MacBytes mb) {
        System.arraycopy(mb.getBytes(), 0, getBytes(), getTotalSize() - actualAuthenticationSize(), actualAuthenticationSize());
    }

//	public boolean authenticateMac(String key){
//		throw new RuntimeException("unimplemented");
//	}
//
//	public boolean containsMac(){ 
//		throw new RuntimeException("unimplemented");
//	}
}
