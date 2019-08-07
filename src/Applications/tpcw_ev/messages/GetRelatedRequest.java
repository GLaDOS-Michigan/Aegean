/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package Applications.tpcw_ev.messages;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Vector;

/**
 * @author manos
 */
public class GetRelatedRequest extends DatabaseRequest {

    public int i_id;
    public Vector<Integer> i_id_vec;
    public Vector<String> i_thumbnail_vec;

    public GetRelatedRequest(int id, Vector<Integer> i_id_vec, Vector<String> i_thumbnail_vec) {
        this.tag = MessageTags.getRelated;
        this.i_id = id;
        this.i_id_vec = i_id_vec;
        this.i_thumbnail_vec = i_thumbnail_vec;
    }

    public GetRelatedRequest(byte[] bytes) {
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
            ObjectInputStream ois = new ObjectInputStream(bis);
            tag = ois.readInt();
            i_id = ois.readInt();
            i_id_vec = (Vector<Integer>) ois.readObject();
            i_thumbnail_vec = (Vector<String>) ois.readObject();
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
            oos.writeObject(i_id_vec);
            oos.writeObject(i_thumbnail_vec);
            oos.flush();
            return bos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


}
