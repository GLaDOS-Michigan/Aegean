package merklePB2;

import BFT.Parameters;

import java.security.MessageDigest;
import java.util.zip.CRC32;

public class Checksum {

    private MessageDigest digest = null;
    private CRC32 crc = null;
    private Parameters parameters;

    public Checksum(Parameters param) {
        parameters = param;
        if (parameters.digestType.equals("CRC32")) {
            crc = new CRC32();
        } else {
            try {
                digest = MessageDigest.getInstance(parameters.digestType);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void reset() {
        if (crc != null)
            crc.reset();
        else
            digest.reset();
    }

    public void update(byte[] data) {
        if (crc != null)
            crc.update(data);
        else
            digest.update(data);
    }

    private static byte[] longToBytes(long value) {
        return new byte[]{
                (byte) ((value >> 56) & 0xff),
                (byte) ((value >> 48) & 0xff),
                (byte) ((value >> 40) & 0xff),
                (byte) ((value >> 32) & 0xff),
                (byte) ((value >> 24) & 0xff),
                (byte) ((value >> 16) & 0xff),
                (byte) ((value >> 8) & 0xff),
                (byte) ((value >> 0) & 0xff),
        };
    }

    public byte[] getValue() {
        if (crc != null)
            return longToBytes(crc.getValue());
        else
            return digest.digest();
    }

}
