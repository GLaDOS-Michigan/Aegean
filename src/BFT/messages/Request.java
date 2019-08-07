// $Id: Request.java 722 2011-07-04 09:24:35Z aclement $

package BFT.messages;

import BFT.Debug;
import BFT.Parameters;
import util.UnsignedTypes;

abstract public class Request extends MacArrayMessage {

    protected RequestCore core;

    // the log digest for filter node
    protected Digest digest;

    protected long digestId;

    /**
     * The following two constructors are for use only by ReadOnlyRequest as
     * it is designed to skip the order node entirely
     **/
    public Request(Parameters param, int tag, long sender, RequestCore pay, boolean b) {
        this(param, tag, sender, -1L, pay, b);
        digest = null;
        digestId = 0;
    }

    public Request(Parameters param, int tag, long sender, long subId, RequestCore pay, boolean b) {
        super(param, tag, pay.getTotalSize() + UnsignedTypes.uint32Size, sender, subId,
                b ? param.getExecutionCount() : param.getFilterCount());
        digestId = 0;
        digest = null;
        byte[] bytes = getBytes();

        int offset = getOffset();
        // add the digestid
        UnsignedTypes.longToBytes(digestId, bytes, offset);
        offset += UnsignedTypes.uint32Size;

        byte[] coreBytes = pay.getBytes();
        for (int i = 0; i < coreBytes.length; i++, offset++) {
            bytes[offset] = coreBytes[i];
        }

        core = pay;
    }


    public Request(Parameters param, int tag, long sender, long subId, RequestCore pay, boolean b, Digest d, long dId) {
        super(param, tag, pay.getTotalSize() + UnsignedTypes.uint32Size * 2 + d.getBytes().length
                , sender, subId, b ? param.getExecutionCount() : param.getFilterCount());
        digest = d;
        digestId = dId;
        byte[] bytes = getBytes();

        int offset = getOffset();
        // first add the digestid
        UnsignedTypes.longToBytes(dId, bytes, offset);
        offset += UnsignedTypes.uint32Size;

        // add the digest
        if (d != null && dId != -1) {
            byte[] coreBytes = d.getBytes();
            // add the length
            UnsignedTypes.intToBytes(coreBytes.length, bytes, offset);
            offset += UnsignedTypes.uint32Size;

            for (int i = 0; i < coreBytes.length; i++, offset++) {
                bytes[offset] = coreBytes[i];
            }
        }

        byte[] coreBytes = pay.getBytes();
        for (int i = 0; i < coreBytes.length; i++, offset++) {
            bytes[offset] = coreBytes[i];
        }

        core = pay;
    }

    public Request(Parameters param, int tag, long sender, RequestCore pay) {
        this(param, tag, sender, -1L, pay);
    }

    public Request(Parameters param, int tag, long sender, long subId, RequestCore pay) {
        super(param, tag, pay.getTotalSize() + UnsignedTypes.uint32Size, sender, subId, param.getOrderCount());
        digestId = 0;
        digest = null;
        byte[] bytes = getBytes();

        int offset = getOffset();
        // add the digestid
        UnsignedTypes.longToBytes(digestId, bytes, offset);
        offset += UnsignedTypes.uint32Size;

        byte[] coreBytes = pay.getBytes();
        for (int i = 0; i < coreBytes.length; i++, offset++) {
            bytes[offset] = coreBytes[i];
        }
        core = pay;
    }


    public Request(Parameters param, int tag, long sender, long subId, RequestCore pay, Digest d, long dId) {
        super(param, tag, pay.getTotalSize() + UnsignedTypes.uint32Size * 2 + d.getBytes().length,
                sender, subId, param.getOrderCount());
        byte[] bytes = getBytes();

        int offset = getOffset();
        // first add the digestid
        UnsignedTypes.longToBytes(dId, bytes, offset);
        offset += UnsignedTypes.uint32Size;

        // add the digest
        if (d != null && dId != -1) {
            byte[] coreBytes = d.getBytes();
            // add the length
            UnsignedTypes.intToBytes(coreBytes.length, bytes, offset);
            offset += UnsignedTypes.uint32Size;

            for (int i = 0; i < coreBytes.length; i++, offset++) {
                bytes[offset] = coreBytes[i];
            }
        }

        byte[] coreBytes = pay.getBytes();
        for (int i = 0; i < coreBytes.length; i++, offset++) {
            bytes[offset] = coreBytes[i];
        }
        core = pay;
    }

    public Request(byte[] bytes, Parameters param) {
        super(bytes, param);

        byte[] tmp = new byte[getPayloadSize()];
        int offset = getOffset();
        for (int i = 0; i < tmp.length; i++, offset++) {
            tmp[i] = bytes[offset];
        }

        if (!parameters.filtered/*&&!BFT.Parameters.primaryBackup*/) {

            // derive the digest from this
            int offset2 = 0;
            byte[] tmp2;
            digestId = UnsignedTypes.bytesToLong(tmp, offset2);
            offset2 += UnsignedTypes.uint32Size;

            if (digestId != 0) {
                int length = UnsignedTypes.bytesToInt(tmp, offset2);
                offset2 += UnsignedTypes.uint32Size;
                tmp2 = new byte[length];
                for (int i = 0; i < length; i++, offset2++) {
                    tmp2[i] = tmp[offset2];
                }
                digest = Digest.fromBytes(param, tmp2);
                tmp2 = new byte[tmp.length - length - UnsignedTypes.uint32Size * 2];
                for (int i = 0; i < tmp2.length; i++) {
                    tmp2[i] = tmp[length + UnsignedTypes.uint32Size * 2 + i];
                }
            } else {
                tmp2 = new byte[tmp.length - UnsignedTypes.uint32Size];
                for (int i = 0; i < tmp2.length; i++) {
                    tmp2[i] = tmp[UnsignedTypes.uint32Size + i];
                }
                digest = null;
                digestId = 0;
            }
            core = new SignedRequestCore(tmp2, parameters);
        } else if (getTag() == MessageTags.ClientRequest || getTag() == MessageTags.ReadOnlyRequest) {
            // derive the digest from this
            int offset2 = 0;
            byte[] tmp2;
            digestId = UnsignedTypes.bytesToLong(tmp, offset2);
            offset2 += UnsignedTypes.uint32Size;
            Debug.debug(Debug.MODULE_MESSAGE, "\tRequest: digestId %d length = %d",
                    digestId,
                    (tmp.length - UnsignedTypes.uint32Size));

            if (digestId != 0) {
                int length = UnsignedTypes.bytesToInt(tmp, offset2);
                offset2 += UnsignedTypes.uint32Size;
                tmp2 = new byte[length];
                for (int i = 0; i < length; i++, offset2++) {
                    tmp2[i] = tmp[offset2];
                }
                digest = Digest.fromBytes(param, tmp2);
                tmp2 = new byte[tmp.length - length - UnsignedTypes.uint32Size * 2];
                for (int i = 0; i < tmp2.length; i++) {
                    tmp2[i] = tmp[length + UnsignedTypes.uint32Size * 2 + i];
                }
            } else {
                tmp2 = new byte[tmp.length - UnsignedTypes.uint32Size];
                for (int i = 0; i < tmp2.length; i++) {
                    tmp2[i] = tmp[UnsignedTypes.uint32Size + i];
                }
                digest = null;
                digestId = 0;
            }

            core = new SimpleRequestCore(param, tmp2);
        }
        else {
            core = new FilteredRequestCore(tmp, parameters);
        }

        if (offset != getBytes().length - getAuthenticationSize()) {
            throw new RuntimeException("Invalid byte input");
        }
    }

    public RequestCore getCore() {
        return core;
    }

    public Digest getDigest() {
        return digest;
    }

    public long getDigestId() {
        return digestId;
    }

    public boolean equals(Request r) {
        boolean res = r != null && super.equals(r);
        res = core.equals(r.core);
        return res;
    }

    public String toString() {
        return core.toString();
    }
}
