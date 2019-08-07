// $Id: MacBytes.java 292 2010-04-25 22:17:40Z glc $

package BFT.messages;

public class MacBytes {

    public MacBytes() {
        bytes = new byte[MacBytes.size()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = 0;
        }
    }

    public MacBytes(byte[] b) {
        bytes = b;
        if (b.length != size()) {
            throw new RuntimeException("invalid mac array");
        }
    }

    protected byte[] bytes;

    public boolean equals(MacBytes m) {
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

    public String toString() {
        String tmp = "";
        for (int i = 0; i < bytes.length; i++) {
            tmp = tmp + " " + bytes[i];
        }
        return tmp;
    }

    public static final int size() {
        return BFT.util.KeyGen.macsize;
    }
}
