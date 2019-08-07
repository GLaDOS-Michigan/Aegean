/*-------------------------------------------------------------------------
 * rbe.EB.java
 * Timothy Heil
 * 10/5/99
 *
 * ECE902 Fall '99
 *
 * TPC-W emulated browser.
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

import tpcw.Book;
import tpcw.Cart;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Random;
import java.util.Vector;

public class User extends Thread {
    private final static String URL = "jdbc:h2:mem:testServer;MULTI_THREADED=1;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000;LOCK_MODE=3";
    private final static String USER = "sa";
    private final static String PASSWORD = "sa";

    // Terminate all EBs.
    public static volatile boolean terminate = false;

    int[/* from */][/* to */] transProb; // Transition probabilities.
    Transition[/* from */][/* to */] trans; // EB transitions.
    Transition curTrans;
    int curState; // Current state.
    byte[] buffer = new byte[4096];
    int cid; // CUSTOMER_ID. See TPC-W Spec.
    String sessionID; // SESSION_ID. See TPC-W Spec.
    int shopID; // Shopping ID.
    String fname = null; // Customer first name.
    String lname = null; // Customer last name.
    public Benchmark rbe;
    boolean toHome;
    int dbID = -1;
    public Random rand;

    public static final int NO_TRANS = 0;
    public static final int MIN_PROB = 1;
    public static final int MAX_PROB = 9999;
    public static final int ID_UNKNOWN = -1;


    public int SHOPPING_ID = -1;
    Vector related_item_ids = new Vector();
    Vector thumbnails = new Vector();
    Cart cart;
    Vector books;
    Book mybook;

    public User(Benchmark rbe, int[][] prob, // Transition probabilities.
                // See TPC-W Spec. Section 5.2.2.
                Transition[][] trans, // Actual transitions.
                int max, // Number of transitions. -1 implies continuous
                String name, // String name.
                int dbID, int seed) {
        this.rbe = rbe;
        this.transProb = prob;
        this.trans = trans;
        this.dbID = dbID;
        rand = new Random(seed);
        initialize();
    }

    public final int states() {
        return (transProb.length);
    }

    public void initialize() {
        curState = 0;
        // cid = ID_UNKNOWN;
        cid = rbe.NURand(rand, rbe.cidA, 1, rbe.numCustomer);
        sessionID = null;
        shopID = ID_UNKNOWN;
        fname = null;
        lname = null;
        toHome = false;
    }

    public void run() {
        Connection con = null;
        Statement stat = null;
        try {
            con = DriverManager.getConnection(URL, USER, PASSWORD);
            con.setAutoCommit(false);
            stat = con.createStatement();
        } catch (SQLException e1) {
            e1.printStackTrace();
        }

        while (true) {
            if (curTrans != null) {
                System.out.println("curTrans = " + curTrans);
                try {
                    curTrans.execute(this, con, stat);
                } catch (SQLException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }

            // Check if user session is finished.
            if (toHome) {
                // User session is complete. Start new user session.
                System.out.println("Completed user session.");
                initialize();
            }

            // 6) Pick the next navigation option.
            // 7) Compose HTTP request.
            nextState();

        }
    }

    void nextState() {
        int i = nextInt(MAX_PROB - MIN_PROB + 1) + MIN_PROB;
        int j;

        for (j = 0; j < transProb[curState].length; j++) {
            if (transProb[curState][j] >= i) {
                curTrans = trans[curState][j];
                toHome = trans[curState][j].toHome();
                curState = j;
                return;
            }
        }
    }

    // Needed, because Java 1.1 did not have Random.nextInt(int range)
    public int nextInt(int range) {
        int i = Math.abs(rand.nextInt());
        return (i % (range));
    }
}
