// $Id: Signature.java 49 2010-02-26 19:33:49Z yangwang $

package BFT.messages;

public class Signature {

    public Signature() {
    }

    public Signature(byte[] b) {
        bytes = b;
        if (b.length != size()) {
            throw new RuntimeException("invalid signature array");
        }
    }

    protected byte[] bytes;

    public boolean equals(Signature m) {
        boolean res = true;
        for (int i = 0; i < size(); i++) {
            res = res && bytes[i] == m.bytes[i];
        }
        return res;
    }

    public byte[] getBytes() {
        if (bytes == null) {
            bytes = new byte[size()];
        }
        return bytes;
    }

    public static final int size() {
        return 4;
    }
}