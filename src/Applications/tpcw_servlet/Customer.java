/* 
 * Customer.java - stores the important information for a single customer. 
 *
 ************************************************************************
 *
 * This is part of the the Java TPC-W distribution,
 * written by Harold Cain, Tim Heil, Milo Martin, Eric Weglarz, and Todd
 * Bezenek.  University of Wisconsin - Madison, Computer Sciences
 * Dept. and Dept. of Electrical and Computer Engineering, as a part of
 * Prof. Mikko Lipasti's Fall 1999 ECE 902 course.
 *
 * Copyright (C) 1999, 2000 by Harold Cain, Timothy Heil, Milo Martin, 
 *                             Eric Weglarz, Todd Bezenek.
 *
 * This source code is distributed "as is" in the hope that it will be
 * useful.  It comes with no warranty, and no author or distributor
 * accepts any responsibility for the consequences of its use.
 *
 * Everyone is granted permission to copy, modify and redistribute
 * this code under the following conditions:
 *
 * This code is distributed for non-commercial use only.
 * Please contact the maintainer for restrictions applying to 
 * commercial use of these tools.
 *
 * Permission is granted to anyone to make or distribute copies
 * of this code, either as received or modified, in any
 * medium, provided that all copyright notices, permission and
 * nonwarranty notices are preserved, and that the distributor
 * grants the recipient permission for further redistribution as
 * permitted by this document.
 *
 * Permission is granted to distribute this code in compiled
 * or executable form under the same conditions that apply for
 * source code, provided that either:
 *
 * A. it is accompanied by the corresponding machine-readable
 *    source code,
 * B. it is accompanied by a written offer, with no time limit,
 *    to give anyone a machine-readable copy of the corresponding
 *    source code in return for reimbursement of the cost of
 *    distribution.  This written offer must permit verbatim
 *    duplication by anyone, or
 * C. it is distributed by someone who received only the
 *    executable form, and is accompanied by a copy of the
 *    written offer of source code that they received concurrently.
 *
 * In other words, you are welcome to use, share and improve this codes.
 * You are forbidden to forbid anyone else to use, share and improve what
 * you give them.
 *
 ************************************************************************/
package Applications.tpcw_servlet;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.sql.ResultSet;
import java.util.Date;

//glorified struct used for passing customer info around.
public class Customer implements Externalizable {
    private static final long serialVersionUID = 1464038167129005478L;

    public int c_id;
    public String c_uname;
    public String c_passwd;
    public String c_fname;
    public String c_lname;
    public String c_phone;
    public String c_email;
    public Date c_since;
    public Date c_last_visit;
    public Date c_login;
    public Date c_expiration;
    public double c_discount;
    public double c_balance;
    public double c_ytd_pmt;
    public Date c_birthdate;
    public String c_data;

    //From the address table
    public int addr_id;
    public String addr_street1;
    public String addr_street2;
    public String addr_city;
    public String addr_state;
    public String addr_zip;
    public int addr_co_id;

    //From the country table
    public String co_name;

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeLong(serialVersionUID);

        out.writeInt(c_id);
        //out.writeUTF(c_uname);
        out.writeObject(c_uname); // Do not use "writeUTF()" because c_uname can be null
        out.writeObject(c_passwd); // Same reason with c_uname
        out.writeUTF(c_fname);
        out.writeUTF(c_lname);
        out.writeUTF(c_phone);
        out.writeUTF(c_email);

        if (c_since == null)
            out.writeLong(-1L);
        else
            out.writeLong(c_since.getTime());
        if (c_last_visit == null)
            out.writeLong(-1L);
        else
            out.writeLong(c_last_visit.getTime());
        if (c_login == null)
            out.writeLong(-1L);
        else
            out.writeLong(c_login.getTime());
        if (c_expiration == null)
            out.writeLong(-1L);
        else
            out.writeLong(c_expiration.getTime());
        out.writeDouble(c_discount);
        out.writeDouble(c_balance);
        out.writeDouble(c_ytd_pmt);
        out.writeLong(c_birthdate.getTime());
        out.writeUTF(c_data);

        out.writeInt(addr_id);
        out.writeObject(addr_street1);
        out.writeObject(addr_street2);
        out.writeObject(addr_city);
        out.writeObject(addr_state);
        out.writeObject(addr_zip);
        out.writeInt(addr_co_id);

        out.writeObject(co_name);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException,
            ClassNotFoundException {
        long classId = in.readLong();
        assert (classId == serialVersionUID);

        this.c_id = in.readInt();
        this.c_uname = (String) in.readObject();
        this.c_passwd = (String) in.readObject();
        this.c_fname = in.readUTF();
        this.c_lname = in.readUTF();
        this.c_phone = in.readUTF();
        this.c_email = in.readUTF();

        long tmp = in.readLong();
        if (tmp == -1L)
            this.c_since = null;
        else
            this.c_since = new Date(tmp);
        tmp = in.readLong();
        if (tmp == -1L)
            this.c_last_visit = null;
        else
            this.c_last_visit = new Date(tmp);
        tmp = in.readLong();
        if (tmp == -1L)
            this.c_login = null;
        else
            this.c_login = new Date(tmp);
        tmp = in.readLong();
        if (tmp == -1L)
            this.c_expiration = null;
        else
            this.c_expiration = new Date(tmp);

        this.c_discount = in.readDouble();
        this.c_balance = in.readDouble();
        this.c_ytd_pmt = in.readDouble();
        this.c_birthdate = new Date(in.readLong());
        this.c_data = in.readUTF();

        this.addr_id = in.readInt();
        this.addr_street1 = (String) in.readObject();
        this.addr_street2 = (String) in.readObject();
        this.addr_city = (String) in.readObject();
        this.addr_state = (String) in.readObject();
        this.addr_zip = (String) in.readObject();
        this.addr_co_id = in.readInt();

        this.co_name = (String) in.readObject();
    }

    public Customer() {
    }

    public Customer(ResultSet rs) {
        // The result set should have all of the fields we expect.
        // This relies on using field name access.  It might be a bad
        // way to break this up since it does not allow us to use the
        // more efficient select by index access method.  This also
        // might be a problem since there is no type checking on the
        // result set to make sure it is even a reasonble result set
        // to give to this function.

        try {
            c_id = rs.getInt("c_id");
            c_uname = rs.getString("c_uname");
            c_passwd = rs.getString("c_passwd");
            c_fname = rs.getString("c_fname");
            c_lname = rs.getString("c_lname");

            c_phone = rs.getString("c_phone");
            c_email = rs.getString("c_email");
            c_since = rs.getDate("c_since");
            c_last_visit = rs.getDate("c_last_login");
            c_login = rs.getDate("c_login");
            c_expiration = rs.getDate("c_expiration");
            c_discount = rs.getDouble("c_discount");
            c_balance = rs.getDouble("c_balance");
            c_ytd_pmt = rs.getDouble("c_ytd_pmt");
            c_birthdate = rs.getDate("c_birthdate");
            c_data = rs.getString("c_data");

            addr_id = rs.getInt("addr_id");
            addr_street1 = rs.getString("addr_street1");
            addr_street2 = rs.getString("addr_street2");
            addr_city = rs.getString("addr_city");
            addr_state = rs.getString("addr_state");
            addr_zip = rs.getString("addr_zip");
            addr_co_id = rs.getInt("addr_co_id");

            co_name = rs.getString("co_name");

        } catch (java.lang.Exception ex) {
            ex.printStackTrace();
        }
    }

}
