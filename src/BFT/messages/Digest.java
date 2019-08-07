// $Id: Digest.java 722 2011-07-04 09:24:35Z aclement $

package BFT.messages;

import BFT.Parameters;
import util.UnsignedTypes;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.zip.CRC32;

public class Digest {

    Parameters parameters;

    protected Digest(Parameters param) {
        parameters = param;
        bytes = new byte[parameters.digestLength];
    }

    //private static MessageDigest digest_crc32 = null;
    private static MessageDigest digest_sha256 = null;
    int count = 0;

    static {
        try {
            //if(!Parameters.digestType.equals("CRC32")) {
            //digest_crc32=MessageDigest.getInstance("CRC32");
            digest_sha256 = MessageDigest.getInstance("SHA-256");
            //}
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Digest(Parameters param, byte[] bits) {
        this(param, bits, 0, bits.length);
    }

    public Digest(Parameters param, byte[] bits, int offset, int length) {
        parameters = param;
        try {
            if (parameters.digestType.equals("CRC32")) {
                CRC32 checksum = new CRC32();
                checksum.update(bits, offset, length);
                bytes = UnsignedTypes.longToBytes(checksum.getValue());
                System.err.println("don't think so");
            } else {
                synchronized (digest_sha256) {
                    digest_sha256.update(bits, offset, length);
                    bytes = digest_sha256.digest();

                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public static Digest fromBytes(Parameters param, byte[] bits) {
        return fromBytes(param, bits, 0, bits.length);
    }

    public static Digest fromBytes(Parameters param, byte[] bits, int offset, int length) {
        Digest d = new Digest(param);
        d.bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            d.bytes[i] = bits[i + offset];
        }
        return d;
    }

    protected byte[] bytes;

    public byte[] getBytes() {
        return bytes;
    }


    public void getBytes(byte[] b, int offset) {
        for (int i = 0; i < bytes.length; i++) {
            b[offset + i] = bytes[i];
        }
    }

    @Override
    public String toString() {
        String str = "";
        for (int i = 0; i < bytes.length; i++) {
            str += bytes[i] + " ";
        }

        return str;
    }

    public boolean equals(Digest d) {
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

    @Override
    public int hashCode() {
        int sum = 0;
        for (int i = 0; i < bytes.length; i++) {
            sum += bytes[i];
        }

        return sum;
    }

}
