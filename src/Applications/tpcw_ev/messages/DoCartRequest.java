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
public class DoCartRequest extends DatabaseRequest {

    public int shoppind_id;
    //public Integer i_id;
    public Vector<Integer> ids;
    public Vector<Integer> quantities;

    public DoCartRequest(int shopping_id, Vector<Integer> ids, Vector<Integer> quantities) {
        this.tag = MessageTags.doCart;
        this.shoppind_id = shopping_id;
        //this.i_id = i_id;
        this.ids = ids;
        this.quantities = quantities;
    }

    public DoCartRequest(byte[] bytes) {
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
            ObjectInputStream ois = new ObjectInputStream(bis);
            tag = ois.readInt();
            shoppind_id = ois.readInt();
            //i_id = (Integer) ois.readObject();
            ids = (Vector<Integer>) ois.readObject();
            quantities = (Vector<Integer>) ois.readObject();

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
            oos.writeInt(shoppind_id);
            //oos.writeObject(i_id);
            oos.writeObject(ids);
            oos.writeObject(quantities);
            oos.flush();
            return bos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


}
