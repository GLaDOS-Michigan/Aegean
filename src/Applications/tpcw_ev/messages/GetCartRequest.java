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
public class GetCartRequest extends DatabaseRequest {


    public int shopping_id;
    public double c_discount;

    public GetCartRequest(int shopping_id, double c_discount) {
        this.tag = MessageTags.getCart;
        this.shopping_id = shopping_id;
        this.c_discount = c_discount;
    }

    public GetCartRequest(byte[] bytes) {
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
            ObjectInputStream ois = new ObjectInputStream(bis);
            tag = ois.readInt();
            shopping_id = ois.readInt();
            c_discount = ois.readDouble();
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
            oos.writeDouble(c_discount);
            oos.flush();
            return bos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


}
