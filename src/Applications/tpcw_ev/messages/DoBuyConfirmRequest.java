/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package Applications.tpcw_ev.messages;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.Date;

/**
 * @author manos
 */
public class DoBuyConfirmRequest extends DatabaseRequest {

    public int shopping_id;
    public int customer_id;
    public String cc_type;
    public long cc_number;
    public String cc_name;
    public Date cc_expiry;
    public String shipping;

    public DoBuyConfirmRequest(int shopping_id, int customer_id, String cc_type, long cc_number,
                               String cc_name, Date cc_expiry, String shipping) {
        this.tag = MessageTags.doBuyConfirm;
        this.shopping_id = shopping_id;
        this.customer_id = customer_id;
        this.cc_type = cc_type;
        this.cc_number = cc_number;
        this.cc_name = cc_name;
        this.cc_expiry = cc_expiry;
        this.shipping = shipping;
    }

    public DoBuyConfirmRequest(byte[] bytes) {
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
            ObjectInputStream ois = new ObjectInputStream(bis);
            tag = ois.readInt();
            shopping_id = ois.readInt();
            customer_id = ois.readInt();
            cc_type = readString(ois);
            cc_number = ois.readLong();
            cc_name = readString(ois);
            cc_expiry = (Date) ois.readObject();
            shipping = readString(ois);
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
            oos.writeInt(customer_id);
            writeString(oos, cc_type);
            oos.writeLong(cc_number);
            writeString(oos, cc_name);
            oos.writeObject(cc_expiry);
            writeString(oos, shipping);
            oos.flush();
            return bos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


}
