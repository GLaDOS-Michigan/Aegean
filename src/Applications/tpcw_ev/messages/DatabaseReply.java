/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package Applications.tpcw_ev.messages;

import java.io.*;

/**
 * @author manos
 */
public class DatabaseReply {

    public Object obj;

    public DatabaseReply() {
    }

    public DatabaseReply(Object o) {
        obj = o;
    }

    public DatabaseReply(byte[] b) {
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(b);
            ObjectInputStream ois = new ObjectInputStream(bis);
            obj = ois.readObject();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public byte[] getBytes() {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(obj);
            oos.flush();
            return bos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void writeString(ObjectOutputStream oos, String str) throws IOException {
        oos.writeInt(str.length());
        oos.write(str.getBytes());
    }

    public String readString(ObjectInputStream ois) throws IOException {
        int length = ois.readInt();
        byte[] data = new byte[length];
        ois.read(data, 0, length);
        return new String(data);
    }

}
