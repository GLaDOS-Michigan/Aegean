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
public class AdminUpdateRequest extends DatabaseRequest {

    public int i_id;
    public double cost;
    public String image;
    public String thumbnail;

    public AdminUpdateRequest(int id, double cost, String image, String thumb) {
        this.tag = MessageTags.adminUpdate;
        this.i_id = id;
        this.cost = cost;
        this.image = image;
        this.thumbnail = thumb;
    }

    public AdminUpdateRequest(byte[] bytes) {
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
            ObjectInputStream ois = new ObjectInputStream(bis);
            tag = ois.readInt();
            i_id = ois.readInt();
            cost = ois.readDouble();
            image = readString(ois);
            thumbnail = readString(ois);
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
            oos.writeInt(i_id);
            oos.writeDouble(cost);
            writeString(oos, image);
            writeString(oos, thumbnail);
            oos.flush();
            return bos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


}
