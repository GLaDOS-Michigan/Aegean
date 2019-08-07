// $Id: HistoryDigest.java 709 2011-06-29 13:19:15Z aclement $

package BFT.messages;

import BFT.Parameters;

public class HistoryDigest extends Digest {


    public HistoryDigest(Parameters param, HistoryDigest d, CommandBatch b, NonDeterminism n,
                         Digest cp) {
        super(param, combineArrays(
                combineArrays(d.getBytes(), b.getBytes()),
                combineArrays(n.getBytes(), cp.getBytes())
        ));
    }

    /**
     * Constructor that takes the previous history digest and the next
     * command batch and creates a new history digest.
     **/
    public HistoryDigest(Parameters param, HistoryDigest d, CommandBatch b, NonDeterminism n) {
        super(param, combineArrays(combineArrays(d.getBytes(),
                b.getBytes()), n.getBytes()));
    }

    public HistoryDigest(Parameters param) {
        super(param);
    }

    public static HistoryDigest fromBytes(byte[] bits, Parameters param) {
//        System.err.println("HistoryDigest.fromBytes");
        return fromBytes(bits, 0, bits.length, param);
    }

    public static HistoryDigest fromBytes(byte[] bits, int offset, int length, Parameters param) {
//        System.err.println("HistoryDigest.fromBytes(more params)");
        HistoryDigest d = new HistoryDigest(param);
        d.bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            d.bytes[i] = bits[offset + i];
        }
        return d;
    }

    public HistoryDigest(Parameters param, byte[] b) {
        super(param, b);
    }

    public static int size(Parameters param) {
        return Digest.size(param);
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