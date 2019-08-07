/*-------------------------------------------------------------------------
 * rbe.util.StrStrPattern.java
 * Timothy Heil
 * 10/5/99
 *
 * StringPattern matching a string.
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

package tpcw.real.util;

public class StrStrPattern extends StringPattern {
    protected String p;  // The string to match.

    public StrStrPattern(String s) {
        p = s;
    }

    public int length() {
        return (p.length());
    }

    // Search from index start to end, inclusive.
    public int find(String s, int start, int end) {
        int i = s.indexOf(p, start);
        if (i == -1) {
            return (-1);
        }
        // FIXME:  This is slower than needed, when the
        //  string is much longer than end.
        else if (i >= (end + p.length())) {
            return (-1);
        } else {
            this.start = i;
            this.end = this.start + p.length() - 1;
            return (i);
        }
    }

    // See if pattern matches exactly characters pos to end, inclusive.
    public boolean matchWithin(String s, int pos, int end) {
        if ((end - pos + 1) < p.length()) return (false);

        if (s.startsWith(p, pos)) {
            this.start = pos;
            this.end = pos + p.length() - 1;
            return (true);
        } else {
            return (false);
        }
    }

    public String toString() {
        return p;
    }

    // Minimum and maximum lengths.
    protected int minLength() {
        return (p.length());
    }

    protected int maxLength() {
        return (p.length());
    }

}
