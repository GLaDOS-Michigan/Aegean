// $Id

package BFT.order.messages;

import BFT.Debug;
import BFT.Parameters;
import BFT.messages.*;
import util.UnsignedTypes;


public class RequestBatch {

    public RequestBatch(Parameters param, RequestCore[] _entries) {
        parameters = param;
        entries = _entries;
        bytes = null;
    }

    public RequestBatch(byte[] bytes, int count, Parameters param) {
        parameters = param;
        entries = new RequestCore[count];
        int offset = 0;
        int num = 0;

        while (num < count) {
            byte[] tmp = new byte[2];
            tmp[0] = bytes[offset];
            tmp[1] = bytes[offset + 1];
            int tag = UnsignedTypes.bytesToInt(tmp);
            tmp = new byte[4];
            tmp[0] = bytes[offset + 2];
            tmp[1] = bytes[offset + 3];
            tmp[2] = bytes[offset + 4];
            tmp[3] = bytes[offset + 5];
            int size = (int) UnsignedTypes.bytesToLong(tmp);

            if (tag == MessageTags.SignedRequestCore)
                tmp = new byte[size + SignedMessage.computeAuthenticationSize() +
                        VerifiedMessageBase.getOffset()];
            else if (tag == MessageTags.FilteredRequestCore) {
                tmp = new byte[size
                        + MacSignatureMessage.computeAuthenticationSize(parameters.getFilterCount(), parameters.getOrderCount(), parameters)
                        + VerifiedMessageBase.getOffset()];
            } else
                Debug.kill("WTF, invalid request core tag: " + tag);
            for (int i = 0;
                 i < tmp.length;
                 i++, offset++) {
                tmp[i] = bytes[offset];
            }
            if (tag == MessageTags.SignedRequestCore) {
                entries[num++] = new SignedRequestCore(tmp, parameters);
            } else if (tag == MessageTags.FilteredRequestCore) {
                entries[num++] = new FilteredRequestCore(tmp, parameters);
            }
        }
        if (bytes.length != offset) {
            System.out.println("offset: " + offset);
            System.out.println("bytes.lenth: " + bytes.length);
            Debug.kill(new RuntimeException("unimplemented"));
        }
        this.bytes = bytes;
    }

    protected RequestCore[] entries;
    protected byte[] bytes;
    protected Parameters parameters;

    public RequestCore[] getEntries() {
        return entries;
    }

    public int getSize() {
        return getBytes().length;
    }

    public byte[] getBytes() {
        if (bytes == null) {
            int totalSize = 0;
            for (int i = 0; i < entries.length; i++) {
                totalSize += entries[i].getTotalSize();
            }
            bytes = new byte[totalSize];
            int offset = 0;

            for (int i = 0; i < entries.length; i++) {
                byte[] tmp = entries[i].getBytes();
                for (int j = 0; j < tmp.length; j++, offset++) {
                    bytes[offset] = tmp[j];
                }
            }
            if (offset != totalSize) {
                throw new RuntimeException("size is off");
            }
        }
        return bytes;
    }

    public boolean equals(RequestBatch b) {
        boolean res = b.getEntries().length == getEntries().length;
        for (int i = 0; res && i < entries.length; i++) {
            res = res && getEntries()[i].equals(b.getEntries()[i]);
        }
        return res;
    }

    public String toString() {
        String res = "[";
        int i = 0;
        for (i = 0; i < getEntries().length - 1; i++) {
            res += getEntries()[i] + ", ";
        }
        if (i < getEntries().length) {
            res += getEntries()[i];
        }
        res += "]";
        return res;
    }

}