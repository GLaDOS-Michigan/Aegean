/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package Applications.tpcw_ev.messages;

import Applications.tpcw_ev.Order;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.Date;

/**
 * @author manos
 */
public class OrderReply extends DatabaseReply {

    private Order order;

    public OrderReply(Order o) {
        this.order = o;
    }

    public OrderReply(byte[] b) {

        order = new Order();

        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(b);
            ObjectInputStream ois = new ObjectInputStream(bis);
            order.c_fname = readString(ois);
            order.c_lname = readString(ois);
            order.c_passwd = readString(ois);
            order.c_uname = readString(ois);
            order.c_phone = readString(ois);
            order.c_email = readString(ois);
            order.o_date = (Date) ois.readObject();
            order.o_subtotal = ois.readDouble();
            order.o_tax = ois.readDouble();
            order.o_total = ois.readDouble();
            order.o_ship_type = readString(ois);
            order.o_ship_date = (Date) ois.readObject();
            order.o_status = readString(ois);

            order.bill_addr_street1 = readString(ois);
            order.bill_addr_street2 = readString(ois);
            order.bill_addr_state = readString(ois);
            order.bill_addr_zip = readString(ois);
            order.bill_co_name = readString(ois);

            order.ship_addr_street1 = readString(ois);
            order.ship_addr_street2 = readString(ois);
            order.ship_addr_state = readString(ois);
            order.ship_addr_zip = readString(ois);
            order.ship_co_name = readString(ois);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public byte[] getBytes() {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            writeString(oos, order.c_fname);
            writeString(oos, order.c_lname);
            writeString(oos, order.c_passwd);
            writeString(oos, order.c_uname);
            writeString(oos, order.c_phone);
            writeString(oos, order.c_email);
            oos.writeObject(order.o_date);
            oos.writeDouble(order.o_subtotal);
            oos.writeDouble(order.o_tax);
            oos.writeDouble(order.o_total);
            writeString(oos, order.o_ship_type);
            oos.writeObject(order.o_ship_date);
            writeString(oos, order.o_status);

            writeString(oos, order.bill_addr_street1);
            writeString(oos, order.bill_addr_street2);
            writeString(oos, order.bill_addr_state);
            writeString(oos, order.bill_addr_zip);
            writeString(oos, order.bill_co_name);

            writeString(oos, order.ship_addr_street1);
            writeString(oos, order.ship_addr_street2);
            writeString(oos, order.ship_addr_state);
            writeString(oos, order.ship_addr_zip);
            writeString(oos, order.ship_co_name);

            writeString(oos, order.cx_type);

            oos.flush();
            return bos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public Order getOrder() {
        return order;
    }

}
