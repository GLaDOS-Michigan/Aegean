// $Id: DigestSHA256.java 722 2011-07-04 09:24:35Z aclement $

package BFT.messages;

import util.UnsignedTypes;

public class DigestSHA256 {


    protected DigestSHA256() {
        bytes = new byte[32];
    }

    public DigestSHA256(byte[] hash) {
        this();
        assert hash.length == 32;
        System.arraycopy(hash, 0, bytes, 0, 32);
    }

    public static DigestSHA256 fromBytes(byte[] bits) {
        assert bits.length == 32;
        DigestSHA256 d = new DigestSHA256();
        System.arraycopy(bits, 0, d.bytes, 0, 32);
        return d;
    }

    protected byte[] bytes;

    public byte[] getBytes() {
        return bytes;
    }

    @Override
    public String toString() {
        return UnsignedTypes.bytesToHexString(bytes);
    }

    public boolean equals(DigestSHA256 d) {
        if (this.bytes.length != 32 || d.bytes.length != 32)
            return false;
        boolean res = true && d != null;
        for (int i = 0; i < 32; i++) {
            res = res && bytes[i] == d.bytes[i];
        }
        return res;
    }

    public int getSize() {
        return 32;
    }

    public static int size() {
        return 32;
    } // size in bytes;

}
