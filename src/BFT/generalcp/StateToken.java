package BFT.generalcp;

import BFT.Debug;
import BFT.Parameters;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class StateToken {
    public static final int SNAPSHOT = 0;
    public static final int LOG = 1;
    int type;
    private String fileName;
    private long offset;
    private int len;
    private byte[] hash;
    private long seqNo = -1;
    private Parameters parameters;

    public int getType() {
        return this.type;
    }

    public String getFileName() {
        return this.fileName;
    }

    public long getOffset() {
        return this.offset;
    }

    public int getLength() {
        return this.len;
    }

    public long getSeqNo() {
        return this.seqNo;
    }

    public StateToken() {

    }

    public StateToken(Parameters param, int type, String fileName, long offset, int len,
                      byte[] contents, long seqNo) throws NoSuchAlgorithmException {
        this(param, type, fileName, offset, len, contents);
        this.seqNo = seqNo;
    }

    public StateToken(Parameters param, int type, String fileName, long offset, int len,
                      byte[] contents) throws NoSuchAlgorithmException {
        this.type = type;
        this.fileName = fileName;
        this.offset = offset;
        this.len = len;
        this.parameters = param;
        MessageDigest md = MessageDigest.getInstance(parameters.digestType);
        md.update(contents);
        this.hash = md.digest();
    }

    @Override
    public boolean equals(Object obj) {
        StateToken o = (StateToken) obj;
        if (this.type != o.type)
            return false;
        if (!this.fileName.equals(o.fileName))
            return false;
        if (this.offset != o.offset)
            return false;
        if (this.len != o.len)
            return false;
        if (!Arrays.equals(this.hash, o.hash))
            return false;
        if (this.seqNo != o.seqNo)
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        int sum = 0;
        for (int i = 0; i < hash.length / 4; i++) {
            for (int j = 0; j < 4; j++)
                sum += (hash[i * 4 + j]) << (j * 8);
        }
        return sum;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append(" type=" + type);
        str.append(" fileName=" + fileName);
        str.append(" offset=" + offset);
        str.append(" len=" + len);
        str.append(" seqNo=" + seqNo);
        str.append(" hash=");
        for (int i = 0; i < hash.length; i++)
            str.append(hash[i] + " ");
        return str.toString();
    }

    public boolean validate(byte[] contents) throws NoSuchAlgorithmException {
        if (contents.length != this.len) {
            Debug.println("Not same len, len=" + len + " contents="
                    + contents.length);
            return false;
        }
        MessageDigest md = MessageDigest.getInstance(parameters.digestType);
        md.update(contents);
        byte[] tmp = md.digest();
        for (int i = 0; i < tmp.length; i++) {
            Debug.print(tmp[i] + " ");
        }
        Debug.println();
        return Arrays.equals(this.hash, tmp);
    }

    public void writeBytes(OutputStream out) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);
        dos.writeInt(type);
        byte[] tmp = fileName.getBytes();
        dos.writeInt(tmp.length);
        dos.write(tmp);
        dos.writeLong(offset);
        dos.writeInt(len);
        dos.writeLong(seqNo);
        dos.writeInt(hash.length);
        dos.write(hash);
    }

    public void readBytes(InputStream in) throws IOException {
        DataInputStream dis = new DataInputStream(in);
        type = dis.readInt();
        int len = dis.readInt();
        byte[] tmp = new byte[len];
        dis.read(tmp);
        this.fileName = new String(tmp);
        this.offset = dis.readLong();
        this.len = dis.readInt();
        this.seqNo = dis.readLong();
        len = dis.readInt();
        this.hash = new byte[len];
        dis.read(this.hash);
    }

    public byte[] getBytes() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            this.writeBytes(bos);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return null;
        }
        return bos.toByteArray();
    }
}