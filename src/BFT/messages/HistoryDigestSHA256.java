// $Id: HistoryDigest.java 49 2010-02-26 19:33:49Z yangwang $

package BFT.messages;

import BFT.Parameters;

public class HistoryDigestSHA256 extends Digest {


    public HistoryDigestSHA256(Parameters param, HistoryDigestSHA256 h, Digest d) {
        super(param, combineArrays(h.getBytes(), d.getBytes()));
    }

    public HistoryDigestSHA256(Parameters param) {
        super(param);
    }

    public static HistoryDigestSHA256 fromBytes(byte[] bits, Parameters param) {
        HistoryDigestSHA256 d = new HistoryDigestSHA256(param);
        d.bytes = new byte[bits.length];
        for (int i = 0; i < bits.length; i++) {
            d.bytes[i] = bits[i];
        }
        return d;
    }

    public HistoryDigestSHA256(Parameters param, byte[] b) {
        super(param, b);
    }

    private static byte[] combineArrays(byte[] b1, byte[] b2) {
        byte[] res = new byte[b1.length + b2.length];
        int i = 0;
        for (; i < b1.length; i++) {
            res[i] = b1[i];
        }
        for (int j = 0; j < b2.length; j++) {
            res[i + j] = b2[j];
        }
        return res;
    }
}