/*------------------------------------------------------------------------
 * rbe.EBWBuyReqTrans.java
 * Timothy Heil
 * 10/13/99
 *
 * ECE902 Fall '99
 *
 * TPC-W home transition.  Requests the home page, and sends CID and
 *  shopping ID if known.
 *------------------------------------------------------------------------
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

package tpcw.real;

import tpcw.Customer;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;

public class BuyReqTrans extends Transition {
    @Override
    public void execute(User eb, Connection con, Statement stat) throws SQLException {
        System.out.println("Executing EBWBuyReqTrans");
        Benchmark rbe = eb.rbe;

        // TPCW_buy_request_servlet
        Customer cust = null;
        if (eb.cid != eb.ID_UNKNOWN) {
            // Recuperer String NAME and PASSWD from rbe.unameAndPass(eb.cid);

            // cust = TPCW_Database.getCustomer(UNAME);
            // TPCW_Database.refreshSession(cust.c_id);
            // if (!PASSWD.equals(cust.c_passwd))
            // {
            // out.print("Error: Incorrect Password</BODY></HTML>");
            // return;
            // }

        } else {
            cust = new Customer();

            eb.fname = rbe.astring(eb.rand, 8, 15);
            eb.lname = rbe.astring(eb.rand, 8, 15);

            cust.c_fname = eb.fname;
            cust.c_lname = eb.lname;
            cust.addr_street1 = Benchmark.astring(eb.rand, 15, 40);
            cust.addr_street2 = Benchmark.astring(eb.rand, 15, 40);
            cust.addr_city = Benchmark.astring(eb.rand, 10, 30);
            cust.addr_state = Benchmark.astring(eb.rand, 2, 20);
            cust.addr_zip = Benchmark.astring(eb.rand, 5, 10);
            cust.co_name = Benchmark.unifCountry(eb.rand);
            cust.c_phone = Benchmark.nstring(eb.rand, 9, 16);
            cust.c_email = Benchmark.astring(eb.rand, 8, 15) + "%40"
                    + rbe.astring(eb.rand, 2, 9) + ".com";
            cust.c_birthdate = new Date(rbe.unifDOB(eb.rand));
            cust.c_data = rbe.astring(eb.rand, 100, 500);
            // cust = TPCW_Database.createNewCustomer(cust);
        }

        // SHOPPING_ID should be retrieved from eb
        int SHOPPING_ID = 0;

        if (SHOPPING_ID == -1) {
            System.err.print("ERROR: Shopping Cart ID not set!</BODY></HTML>");
            return;
        }

        // Update the shopping cart cost and get the current contents
        //Cart mycart = TPCW_Database.getCart(SHOPPING_ID,
        //		cust.c_discount);


    }

    /* Find C_ID and SHOPPING_ID, if not already known. */
    public void postProcess(User eb, String html) {
        if (eb.cid == eb.ID_UNKNOWN) {
            // eb.cid = eb.findID(html, RBE.yourCID);
            if (eb.cid == eb.ID_UNKNOWN) {
                // Error
            }
            // System.out.println("Found CID = " + eb.cid);
        }
    }
}
