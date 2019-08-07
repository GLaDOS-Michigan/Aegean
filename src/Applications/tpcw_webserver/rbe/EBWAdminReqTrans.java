/*------------------------------------------------------------------------
 * rbe.EBWAdminReqTrans.java
 * Timothy Heil
 * 11/10/99
 *
 * ECE902 Fall '99
 *
 *
 * TPC-W Administrative Request transition from the product detail page
 *  to the admin request page.  Supplies the I_ID of the book being
 *  changed.
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

package Applications.tpcw_webserver.rbe;

import Applications.tpcw_webserver.rbe.util.CharSetStrPattern;
import Applications.tpcw_webserver.rbe.util.StrStrPattern;

public class EBWAdminReqTrans extends EBTransition {

    private static final StrStrPattern iid = new StrStrPattern("I_ID=");

    public String request(EB eb, String html) {
        String url;
        int i, e, id;
        RBE rbe = eb.rbe;

	 /* Find the I_ID to add. */
        i = iid.find(html);
        if (i == -1) {
            rbe.stats.error("Unable to find I_ID in admin request page.", "???");
            return ("");
        }
        i = i + iid.length();

        e = CharSetStrPattern.notDigit.find(html.substring(i));
        if (e == -1) {
            rbe.stats.error("Unable to find end of I_ID in admin request page.", "???");
            return ("");
        }
        e = e + i;
        id = Integer.parseInt(html.substring(i, e));

        url = rbe.adminReqURL + "?" + rbe.field_iid + "=" + id;

        return (eb.addIDs(url));
    }
}
