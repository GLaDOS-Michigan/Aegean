/*------------------------------------------------------------------------
 * rbe.EBWBuyConfTrans.java
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

import tpcw.BuyConfirmResult;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class BuyConfTrans extends Transition {
    @Override
    public void execute(User eb, Connection con, Statement stat) throws SQLException {
        System.out.println("Executing EBWBuyConfTrans");
        Benchmark rbe = eb.rbe;

        String field_cctype = rbe.unifCCType(eb.rand);
        String field_ccnumber = rbe.nstring(eb.rand, 16, 16);

        String field_ccname = eb.fname + "+" + eb.lname;
        String field_ccexp = rbe.unifExpDate(eb.rand);
        String field_shipping = rbe.unifShipping(eb.rand);

        BuyConfirmResult result = null;
        if (eb.nextInt(100) < 5) {
            String field_street1 = rbe.astring(eb.rand, 15, 40);
            String field_street2 = rbe.astring(eb.rand, 15, 40);

            String field_city = rbe.astring(eb.rand, 4, 30);
            String field_state = rbe.astring(eb.rand, 2, 20);
            String field_zip = rbe.astring(eb.rand, 5, 10);
            String field_country = rbe.unifCountry(eb.rand);

            // result = TPCW_Database
            // .doBuyConfirm(SHOPPING_ID, C_ID, field_cctype,
            // field_ccnumber, field_ccname, new java.sql.Date(
            // new java.util.Date(field_ccexp).getTime()),
            // field_shipping, field_street1, field_street2, field_city,
            // field_state, field_zip, field_country);

        } else {
            // result = TPCW_Database.doBuyConfirm(SHOPPING_ID, C_ID,
            // field_cctype,
            // field_ccnumber, field_ccname, new
            // java.util.Date(field_ccexp).getTime()),
            // field_shipping);
        }
    }
}
