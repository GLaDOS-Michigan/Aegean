// $Id: MacArrayMessage.java 722 2011-07-04 09:24:35Z aclement $

package BFT.messages;

import BFT.Debug;
import BFT.Parameters;
import util.UnsignedTypes;

import java.util.Arrays;

public class MacArrayMessage extends ParameterizedVerifiedMessageBase {

    public MacArrayMessage(Parameters param, int _tag, int _size, long send, int arraySize) {
        this(param, _tag, _size, send, -1L, arraySize);
    }

    public MacArrayMessage(Parameters param, int _tag, int _size, long send, long subid, int arraySize) {
        super(param, _tag, _size, computeAuthenticationSize(arraySize, param));
        byte[] bytes = getBytes();
        int offset = getOffset() + getPayloadSize();
        if (offset != getTotalSize() - getAuthenticationSize()) {
            BFT.Debug.kill("something horribly broken");
        }
        sender = send;
        util.UnsignedTypes.longToBytes(send, bytes, offset);
        offset += UnsignedTypes.uint32Size;
        //if(subId>0) {
        //	System.out.println("while creating MacArrayMessage, subId offset = "+offset);
        //}
        subId = subid;
        util.UnsignedTypes.longToBytes(subid, bytes, offset);
        offset += UnsignedTypes.uint32Size;
    }

    public MacArrayMessage(byte[] bytes, Parameters param) {
        super(bytes);
        parameters = param;
        int offset = getTotalSize() - getAuthenticationSize();
        byte tmp[];
        sender = UnsignedTypes.bytesToLong(bytes, offset);
        offset += UnsignedTypes.uint32Size;
        subId = UnsignedTypes.bytesToLong(bytes, offset);
        offset += UnsignedTypes.uint32Size;

        tmp = new byte[param.digestLength];
        for (int i = 0; i < param.digestLength; i++, offset++) {
            tmp[i] = bytes[offset];
        }
        authenticationDigest = Digest.fromBytes(param, tmp);
    }

    protected Digest authenticationDigest;
    protected long sender;
    protected long subId;

    final public int getSender() {
        return (int) sender;
    }

    final public int getSubId() {
        return (int) subId;
    }

    public static int computeAuthenticationSize(int i, Parameters param) {
        return i * MacBytes.size() + param.digestLength + 2 * MessageTags.uint32Size;
    }

    protected int digestStart() {
        return getOffset() + getPayloadSize() + 2 * MessageTags.uint32Size;
    }

    protected int macStart() {
        return digestStart() + parameters.digestLength;
    }

    public int getAuthenticationStartIndex() {
        return digestStart();
    }

    public int getAuthenticationEndIndex() {
        return macStart();
    }

    public Digest getAuthenticationDigest() {
        if (authenticationDigest == null) {
            if (parameters.insecure) {
                authenticationDigest = new Digest(parameters);
            } else {
                authenticationDigest = new Digest(parameters, getBytes(), 0, getTotalSize() - getAuthenticationSize());
            }
            // add to byte array
            byte tmp[] = getBytes();
            int offset = digestStart();
            //System.out.println("Inside getAuthenticationDigest(), offset = "+offset+" and length = "+
            //			authenticationDigest.getBytes().length);
            //BFT.util.UnsignedTypes.printBytes(authenticationDigest.getBytes());
            //System.out.println();
            for (int i = 0; i < authenticationDigest.getBytes().length; i++, offset++) {
                tmp[offset] = authenticationDigest.getBytes()[i];
            }
        }
        return authenticationDigest;
    }

    public boolean isValid() {
        return checkAuthenticationDigest();
    }

    public boolean checkAuthenticationDigest() {
        if (authenticationDigest == null) {
            Debug.kill("authentication must be non-null to be checked");
        }
        Digest tmp = authenticationDigest;
        authenticationDigest = null;
        return tmp.equals(getAuthenticationDigest());
    }

    /**
     * return types of following two are subject to change
     **/
    public MacBytes getMacBytes(int index) {
        byte[] dst = new byte[MacBytes.size()];
        int offset = macStart() + index * MacBytes.size();
        byte[] bytes = getBytes();
        for (int i = 0; i < dst.length; i++, offset++) {
            dst[i] = bytes[offset];
        }
//        System.err.println("bytes: " + Arrays.toString(dst) + ", offset: " + offset);
        return new MacBytes(dst);
    }

    public void setMacBytes(int index, MacBytes mb) {
        byte[] bytes = getBytes();
        byte[] tmp = mb.getBytes();
        int offset = macStart() + index * MacBytes.size();
        //System.out.println("While setting macBytes initial offset = "+offset+" and length = "+tmp.length);
        for (int i = 0; i < tmp.length; i++, offset++) {
            bytes[offset] = tmp[i];
        }
    }


    public MacBytes[] getMacArray() {
        BFT.Debug.kill("NYI");
        return null;
    }


    public boolean equals(MacArrayMessage m) {
        return super.equals(m);
    }
}
