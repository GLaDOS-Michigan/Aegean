/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package Applications.tpcw_ev.messages;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * @author manos
 */
public class GetPasswordRequest extends DatabaseRequest {

    public String uname;

    public GetPasswordRequest(String uname) {
        this.tag = MessageTags.getPassword;
        this.uname = uname;
    }

    public GetPasswordRequest(byte[] bytes) {
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
            ObjectInputStream ois = new ObjectInputStream(bis);
            tag = ois.readInt();
            uname = readString(ois);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    public byte[] getBytes() {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeInt(tag);
            writeString(oos, uname);
            oos.flush();
            return bos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


}
