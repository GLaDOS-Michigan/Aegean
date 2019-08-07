/*------------------------------------------------------------------------
 * rbe.EBWSearchResultTrans.java
 * Timothy Heil
 * 10/13/99
 *
 * ECE902 Fall '99
 *
 * TPC-W Search results transition.  Supplies the URL for the search
 *  results page.  Also send the search category (author,title,subject)
 *  and the search string.
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

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Vector;

public class SearchResultTrans extends Transition {
    @Override
    public void execute(User eb, Connection con, Statement stat) throws SQLException {
        System.out.println("Executing EBWSearchResultTrans");
        // Display promotions
        // int I_ID = TPCW_Util.getRandomI_ID();
        // Vector related_item_ids = new Vector();
        // Vector thumbnails = new Vector();
        // int i;
        // String url;
        //
        // TPCW_Database.getRelated(I_ID, related_item_ids, thumbnails);

        Benchmark rbe = eb.rbe;
        int srchType = eb.nextInt(3);

        String searchString = null;
        Vector books = null;

        switch (srchType) {
            case 0:
                // author
                searchString = Benchmark.digSyl(Benchmark.NURand(eb.rand, Benchmark.numItemA, 0,
                        Benchmark.numItem / 10), 7);
                // books = TPCW_Database.doAuthorSearch(searchString);
                break;
            case 1:
                // title
                searchString = Benchmark.digSyl(Benchmark.NURand(eb.rand, rbe.numItemA, 0,
                        rbe.numItem / 5), 7);
                // books = TPCW_Database.doTitleSearch(searchString);
                break;
            case 2:
                // subject
                searchString = Benchmark.unifSubject(eb.rand);
                // books = TPCW_Database.doSubjectSearch(searchString);
                break;
        }
        // return (eb.addIDs(url));
    }
}
