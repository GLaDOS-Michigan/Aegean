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
public class ClearCartRequest extends DatabaseRequest {

    public int shopping_id;

    public ClearCartRequest(int id) {
        this.tag = MessageTags.clearCart;
        this.shopping_id = id;
    }

    public ClearCartRequest(byte[] bytes) {
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
            ObjectInputStream ois = new ObjectInputStream(bis);
            tag = ois.readInt();
            shopping_id = ois.readInt();
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
            oos.writeInt(shopping_id);
            oos.flush();
            return bos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


}
