// $Id: FilteredRequestBase.java 722 2011-07-04 09:24:35Z aclement $

package BFT.messages;

import BFT.Parameters;
import util.UnsignedTypes;

abstract public class FilteredRequestBase extends MacArrayMessage {

    protected FilteredRequestCore[] core;

    public FilteredRequestBase(Parameters param, int tag, long sender, FilteredRequestCore[] pay) {
        super(param, tag, computeSize(pay), sender, param.useVerifier ? param.getExecutionCount() : param.getOrderCount());

        int offset = getOffset();
        byte[] bytes = getBytes();

        // write length of payload
        util.UnsignedTypes.intToBytes(pay.length, bytes, offset);
        offset += UnsignedTypes.uint16Size;

        // write payload bytes
        for (int i = 0; i < pay.length; i++) {
            for (int j = 0; j < pay[i].getBytes().length; j++, offset++) {
                bytes[offset] = pay[i].getBytes()[j];
            }
        }
        core = pay;
    }


    protected static int computeSize(FilteredRequestCore[] pay) {
        int sum = 2;
        for (int i = 0; i < pay.length; i++) {
            sum += pay[i].getTotalSize();
        }
        return sum;
    }


    public FilteredRequestBase(byte[] bytes, Parameters param) {
        super(bytes, param);

        int offset = getOffset();
        byte[] tmp;
        int size = UnsignedTypes.bytesToInt(bytes, offset);
        offset += UnsignedTypes.uint16Size;

        FilteredRequestCore frc[] = new FilteredRequestCore[size];
        int fcCount = 0;
        while (offset < getOffset() + getPayloadSize() && fcCount < frc.length) {
            tmp = new byte[MessageTags.uint32Size];
            for (int i = 0; i < tmp.length; i++) {
                tmp[i] = bytes[offset + i + 2];
            }
            size = (int) util.UnsignedTypes.bytesToLong(tmp);
            size = getOffset() + size + MacSignatureMessage.computeAuthenticationSize(param.getFilterCount(),
                    param.useVerifier ? param.getExecutionCount() : param.getOrderCount(),
                    parameters);
            tmp = new byte[size];
            for (int i = 0; i < tmp.length; i++, offset++) {
                tmp[i] = bytes[offset];
            }
            frc[fcCount++] = new FilteredRequestCore(tmp, parameters);
        }
        core = frc;
        if (offset != getBytes().length - getAuthenticationSize()) {
            throw new RuntimeException("Invalid byte input");
        }
    }

    public FilteredRequestCore[] getCore() {
        return core;
    }

    public boolean equals(FilteredRequestBase r) {
        boolean res = r != null && super.equals(r);
        for (int i = 0; res && i < core.length; i++) {
            res = res && core[i].equals(r.core[i]);
        }
        return res;
    }
}