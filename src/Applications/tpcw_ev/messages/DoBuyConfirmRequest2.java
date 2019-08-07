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
public class DoBuyConfirmRequest2 extends DatabaseRequest {

    public int shopping_id;
    public int customer_id;
    public String cc_type;
    public long cc_number;
    public String cc_name;
    public Date cc_expiry;
    public String shipping;
    public String street_1;
    public String street_2;
    public String city;
    public String state;
    public String zip;
    public String country;

    public DoBuyConfirmRequest2(int shopping_id, int customer_id, String cc_type, long cc_number,
                                String cc_name, Date cc_expiry, String shipping, String street_1, String street_2,
                                String city, String state, String zip, String country) {
        this.tag = MessageTags.doBuyConfirm2;
        this.shopping_id = shopping_id;
        this.customer_id = customer_id;
        this.cc_type = cc_type;
        this.cc_number = cc_number;
        this.cc_name = cc_name;
        this.cc_expiry = cc_expiry;
        this.shipping = shipping;
        this.street_1 = street_1;
        this.street_2 = street_2;
        this.city = city;
        this.state = state;
        this.zip = zip;
        this.country = country;
    }

    public DoBuyConfirmRequest2(byte[] bytes) {
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
            street_1 = readString(ois);
            street_2 = readString(ois);
            city = readString(ois);
            state = readString(ois);
            zip = readString(ois);
            country = readString(ois);
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
            writeString(oos, street_1);
            writeString(oos, street_2);
            writeString(oos, city);
            writeString(oos, state);
            writeString(oos, zip);
            writeString(oos, country);
            oos.flush();
            return bos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


}
