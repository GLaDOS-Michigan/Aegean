/*-------------------------------------------------------------------------
 * rbe.EBTestFactory.java
 * Timothy Heil
 * 10/5/99
 * 
 * Produces Test EBs.
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

public class EBTestFactory extends EBFactory {
    private static final EBTransition homeTrans =
            new EBSimpleTrans("http://www.cs.wisc.edu/~heil/");
    private static final EBTransition resTrans =
            new EBSimpleTrans("http://www.cae.wisc.edu/~ecearch/strata/");
    private static final EBTransition famTrans =
            new EBSimpleTrans("http://www.cs.wisc.edu/~heil/family.html");

    private int count = 1;

    private static final int[][] transProb =
            {
                    {3000, 6000, 9999},
                    {3000, 6000, 9999},
                    {3000, 6000, 9999}
            };

    private static final EBTransition[][] trans =
            {
                    {homeTrans, resTrans, famTrans},
                    {homeTrans, resTrans, famTrans},
                    {homeTrans, resTrans, famTrans}
            };

    public EB getEB(RBE rbe, String membershipfile) {
        EB res = (new EB(rbe, transProb, trans, 3,
                "Test EB #" + (count), membershipfile, count, 0));//!!!Previously "EB + " + count was being used instead of membership file
        count++;
        return res;
    }
}
