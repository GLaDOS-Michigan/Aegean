// $Id: Digest.java 272 2010-04-24 02:13:27Z manos $

package Applications.benchmark;

import BFT.Parameters;

import java.security.MessageDigest;

public class BenchDigest {

    protected BenchDigest(Parameters param) {
        this.parameters = param;
        bytes = new byte[parameters.executionDigestLength];
    }

    int count = 0;
    Parameters parameters;

    public BenchDigest(Parameters param, byte[] bits) {
        this(param, bits, 0, bits.length);
    }

    public BenchDigest(Parameters param, byte[] bits, int offset, int length) {
        this.parameters = param;
        try {
            MessageDigest m = MessageDigest.getInstance("SHA-256");
            m.update(bits, offset, length);
            bytes = m.digest();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public static BenchDigest fromBytes(byte[] bits, Parameters param) {
        BenchDigest d = new BenchDigest(param);
        d.bytes = new byte[bits.length];
        for (int i = 0; i < bits.length; i++) {
            d.bytes[i] = bits[i];
        }
        return d;
    }

    protected byte[] bytes;

    public byte[] getBytes() {
        return bytes;
    }

    @Override
    public String toString() {
        String str = "";
        for (int i = 0; i < bytes.length; i++) {
            str += bytes[i] + " ";
        }

        return str;
    }

    public boolean equals(BenchDigest d) {
        boolean res = true && d != null;
        for (int i = 0; i < bytes.length; i++) {
            res = res && bytes[i] == d.bytes[i];
        }
        return res;
    }

    public int getSize() {
        return parameters.digestLength;
    }

    public static int size(Parameters param) {
        return param.digestLength;
    } // size in bytes;

}
