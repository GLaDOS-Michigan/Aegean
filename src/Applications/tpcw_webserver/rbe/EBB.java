/*-------------------------------------------------------------------------
 * rbe.EBB.java
 * Timothy Heil
 * 10/26/99
 *
 * ECE902 Fall '99
 *
 * TPC-B emulated browser.
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

import java.util.Random;

public class EBB extends EB {
    private int minBID;
    private int maxBID;
    private int minAID;
    private int maxAID;
    private int minTID;
    private int maxTID;
    private int minDelta;
    private int maxDelta;

    public Random rand = new Random();

    public EBB(RBE rbe,
               int[][] prob, // Transition probabilities.
               //   See TPC-W Spec. Section 5.2.2.
               EBTransition[][] trans, // Actual transitions.
               int max,       // Number of transitions.
               // -1 implies continuous
               String name,   // String name.
               int minBID,
               int maxBID,
               int minAID,
               int maxAID,
               int minTID,
               int maxTID,
               int minDelta,
               int maxDelta,
               int clientId,
               int subId) {
        super(rbe, prob, trans, max, name, "EB + " + clientId, clientId, subId);

        this.minBID = minBID;
        this.maxBID = maxBID;
        this.minAID = minAID;
        this.maxAID = maxAID;
        this.minTID = minTID;
        this.maxTID = maxTID;
        this.minDelta = minDelta;
        this.maxDelta = maxDelta;
    }

    public int nextBID() {
        return (nextInt(maxBID - minBID + 1) + minBID);
    }

    public int nextTID() {
        return (nextInt(maxTID - minTID + 1) + minTID);
    }

    public int nextAID() {
        return (nextInt(maxAID - minAID + 1) + minAID);
    }

    public int nextDelta() {
        return (nextInt(maxDelta - minDelta + 1) + minDelta);
    }

    // Needed, because Java 1.1 did not have Random.nextInt(int range)
    public int nextInt(int range) {
        int i = Math.abs(rand.nextInt());
        return (i % (range));
    }
}
