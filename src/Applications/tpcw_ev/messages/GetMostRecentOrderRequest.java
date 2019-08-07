/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package Applications.tpcw_ev.messages;

import Applications.tpcw_ev.OrderLine;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Vector;

/**
 * @author manos
 */
public class GetMostRecentOrderRequest extends DatabaseRequest {

    public String c_uname;
    public Vector<OrderLine> order_lines;


    public GetMostRecentOrderRequest(String uname, Vector<OrderLine> order_lines) {
        this.tag = MessageTags.getMostRecentOrder;
        this.c_uname = uname;
        this.order_lines = order_lines;
    }

    public GetMostRecentOrderRequest(byte[] bytes) {
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
            ObjectInputStream ois = new ObjectInputStream(bis);
            tag = ois.readInt();
            c_uname = readString(ois);
            order_lines = (Vector<OrderLine>) ois.readObject();
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
            writeString(oos, c_uname);
            oos.writeObject(order_lines);
            oos.flush();
            return bos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


}
