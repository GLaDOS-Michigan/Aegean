/*-------------------------------------------------------------------------
 * rbe.args.DateArg.java
 * Timothy Heil
 * 10/29/99
 *
 * Abstract command line argument parsing class.
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

package Applications.tpcw_webserver.rbe.args;

import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;

public class DateArg extends Arg {
    public Date d;
    protected DateFormat df =
            DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG);

    public DateArg(String arg, String name, String desc) {
        super(arg, name, desc, false, false);
    }

    public DateArg(String arg, String name, String desc, ArgDB db) {
        super(arg, name, desc, false, false, db);
    }

    // NOTE:  Due to the vulgarities of Java, the current time is
    //  used as the default time, even if defNow is false.
    public DateArg(String arg, String name, String desc, boolean defNow) {
        super(arg, name, desc, false, true);
        d = new Date(System.currentTimeMillis());
    }

    // NOTE:  Due to the vulgarities of Java, the current time is
    //  used as the default time, even if defNow is false.
    public DateArg(String arg, String name, String desc, boolean defNow,
                   ArgDB db) {
        super(arg, name, desc, false, true, db);
        d = new Date(System.currentTimeMillis());
    }

    public DateArg(String arg, String name, String desc, long def,
                   ArgDB db) {
        super(arg, name, desc, false, true, db);
        d = new Date(def);
    }

    // Customize to parse arguments.
    protected int parseMatch(String[] args, int a)
            throws Arg.Exception {
        if (a == args.length) {
            throw new Arg.Exception("Date argument missing time.", a);
        }
        try {
            df.setLenient(true);
            d = df.parse(args[a]);
        } catch (ParseException pe) {
            throw new Arg.Exception("Unable to parse date (" +
                    args[a] + ").", a);
        }

        return (a + 1);
    }

    public String value() {
        return (df.format(d));
    }
}

