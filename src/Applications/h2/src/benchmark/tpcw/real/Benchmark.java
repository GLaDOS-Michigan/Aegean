/*-------------------------------------------------------------------------
 * rbe.RBE.java
 * Timothy Heil
 * ECE902 Fall '99
 *
 * TPC-W Remote Browser Emulator.
 *
 * Main program.
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

import tpcw.TPCW_Database;
import tpcw.TPCW_Populate;
import tpcw.real.util.StrStrPattern;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;

public class Benchmark {
    private final static String URL = "jdbc:h2:mem:testServer;MULTI_THREADED=1;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000;LOCK_MODE=3";
    private final static String USER = "sa";
    private final static String PASSWORD = "sa";

    public static final boolean TRACE = false;

    // URLs
    public static final StrStrPattern yourCID = new StrStrPattern("C_ID=");
    public static final StrStrPattern yourShopID = new StrStrPattern(
            "SHOPPING_ID=");
    public static final StrStrPattern yourSessionID = new StrStrPattern(
            ";$sessionid$");
    public static final StrStrPattern endSessionID = new StrStrPattern("?");

    // FIELD NAMES
    public static final String field_cid = "C_ID";
    public static boolean getImage; // Whether to fetch images.

    public int numCustomer = 1000; // Number of initial customers
    public int cidA = 1023; // Used for generating random CIDs
    // See TPC-W spec Clause 2.3.2

    // TPC-W spec values for cidA. See TPC-W spec Clause 2.3.2
    public static final int[][] stdCIDA =
            // For NUM_CUSTOMERS in this range Value for A
            {{1, 9999, 1023}, {10000, 39999, 4095}, {40000, 159999, 16383},
                    {160000, 639999, 65535}, {640000, 2559999, 262143},
                    {2560000, 10239999, 1048575}, {10240000, 40959999, 4194303},
                    {40960000, 163839999, 16777215},
                    {163840000, 655359999, 67108863}};

    public static int numItem = 10000; // Number of items for sale.
    public static int numItemA = 511; // Used for search strings. See
    // TPC-W Spec. 2.10.5.1

    public static final int[][] stdNumItemA =
            // For NUM_ITEMS Value for A
            {{1000, 63}, {10000, 511}, {100000, 4095}, {1000000, 32767},
                    {10000000, 524287}};

    public static final BufferedReader bin = new BufferedReader(
            new InputStreamReader(System.in));

    public static void main(String[] args) {
        Benchmark rbe = new Benchmark();
        int i;
        Vector ebs = new Vector(0);

        System.out.println("Remote Browser Emulator for TPC-W.");

        // Copy in parameters.
        // rbe.numCustomer = 1000;
        rbe.numCustomer = 1;
        // -1 means use TPC-W spec. value
        rbe.cidA = -1;
        rbe.numItem = 10000;
        // -1 means use TPC-W spec. value
        rbe.numItemA = -1;

        if (rbe.numCustomer > stdCIDA[stdCIDA.length - 1][1]) {
            System.out.println("Number of customers (" + rbe.numCustomer
                    + ")  must be <= " + stdCIDA[stdCIDA.length - 1][1] + ".");
            return;
        }

        // Compute default CID A value, if needed.
        if (rbe.cidA == -1) {
            for (i = 0; i < stdCIDA.length; i++) {
                if ((rbe.numCustomer >= stdCIDA[i][0])
                        && (rbe.numCustomer <= stdCIDA[i][1])) {
                    rbe.cidA = stdCIDA[i][2];
                    System.out.println("Choose " + rbe.cidA + " for -CUSTA.");
                    break;
                }
            }
        }

        // Check num item is valid.
        for (i = 0; i < stdNumItemA.length; i++) {
            if (rbe.numItem == stdNumItemA[i][0])
                break;
        }

        if (i == stdNumItemA.length) {
            System.out.println("Number of items (" + rbe.numItem
                    + ") must be one of ");
            for (i = 0; i < stdNumItemA.length; i++) {
                System.out.println("    " + stdNumItemA[i][0]);
            }
            return;
        }

        // Compute standard item A value, if needed.
        if (rbe.numItemA == -1) {
            rbe.numItemA = stdNumItemA[i][1];
            System.out.println("Choose " + rbe.numItemA + " for -ITEMA.");
        }

        Connection con;
        try {
            con = DriverManager.getConnection(URL, USER, PASSWORD);
            con.setAutoCommit(false);
            TPCW_Populate.populate(con, 0);
            TPCW_Database.verifyDBConsistency(con, 0);
        } catch (SQLException e1) {
            e1.printStackTrace();
        }

        TPCW_Shopping_Mix_Factory factory = new TPCW_Shopping_Mix_Factory();
        factory.initialize();
        for (int j = 0; j < rbe.numCustomer; j++) {
            ebs.add(factory.getEB(rbe, 0, j));
        }

        // Start EBs...
        System.out.println("Starting " + ebs.size() + " EBs.");

        for (i = 0; i < ebs.size(); i++) {
            User e = (User) ebs.elementAt(i);
            e.start();
        }
    }

    // Static random functions.

    // Defined in TPC-W Spec Clause 2.1.13
    public static final int NURand(Random rand, int A, int x, int y) {
        return ((((nextInt(rand, A + 1)) | (nextInt(rand, y - x + 1) + x)) % (y
                - x + 1)) + x);
    }

    // Defined in TPC-W Spec Clause 4.6.2.8
    private static final String[] digS = {"BA", "OG", "AL", "RI", "RE", "SE",
            "AT", "UL", "IN", "NG"};

    public static String digSyl(int d, int n) {
        String s = "";

        if (n == 0)
            return (digSyl(d));

        for (; n > 0; n--) {
            int c = d % 10;
            s = digS[c] + s;
            d = d / 10;
        }

        return (s);
    }

    public static String digSyl(int d) {
        String s = "";

        for (; d != 0; d = d / 10) {
            int c = d % 10;
            s = digS[c] + s;
        }

        return (s);
    }

    // Gets the username and password fields according to TPC-W Spec. 4.6.2.9
    // ff.
    public static String unameAndPass(int cid) {
        String un = digSyl(cid);
        return ("UNAME =" + un + "& PASSWD =" + un.toLowerCase());
    }

    // Subject list. See TPC-W Spec. 4.6.2.11
    public static final String[] subjects = {"ARTS", "BIOGRAPHIES",
            "BUSINESS", "CHILDREN", "COMPUTERS", "COOKING", "HEALTH",
            "HISTORY", "HOME", "HUMOR", "LITERATURE", "MYSTERY", "NON-FICTION",
            "PARENTING", "POLITICS", "REFERENCE", "RELIGION", "ROMANCE",
            "SELF-HELP", "SCIENCE-NATURE", "SCIENCE-FICTION", "SPORTS",
            "YOUTH", "TRAVEL"};

    // Select a subject string randomly and uniformly from above list.
    // See TPC-W Spec. 2.10.5.1
    public static String unifSubject(Random rand) {
        return (subjects[nextInt(rand, subjects.length)]);
    }

    // Select a subject string randomly and uniformly from above list.
    // See TPC-W Spec. 2.10.5.1
    // NOTE: The "YOUTH" and "TRAVEL" subjects are missing from the home page.
    // I believe this to be an error, but cannot be sure.
    // Change this function if this is determined to not be an error.
    public static String unifHomeSubject(Random rand) {
        return (unifSubject(rand));
    }

    // Adds a field to a HTTP request.
    // field name = f, value = v.
    public static String addField(String i, String f, String v) {
        if (i.indexOf((int) '?') == -1) {
            // First field
            i = i + '?';
        } else {
            // Another additional field.
            i = i + '&';
        }
        i = i + f + "=" + v;

        return (i);
    }

    public static String addSession(String i, String f, String v) {
        StringTokenizer tok = new StringTokenizer(i, "?");
        String return_val = null;
        try {
            return_val = tok.nextToken();
            return_val = return_val + f + v;
            return_val = return_val + "?" + tok.nextToken();
        } catch (NoSuchElementException e) {
        }

        return (return_val);
    }

    public static final String[] nchars = {"0", "1", "2", "3", "4", "5", "6",
            "7", "8", "9"};

    public static final String[] achars = {"0", "1", "2", "3", "4", "5", "6",
            "7", "8", "9", "a", "b", "c", "d", "e", "f", "g", "h", "i", "j",
            "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w",
            "x", "y", "z", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J",
            "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W",
            "X", "Y", "Z",

            "%21", // "!"
            "%40", // "@"
            "%23", // "#"
            "%24", // "$"
            "%25", // "%"
            "%5E", // "^"
            "%26", // "&"
            "*", // "*"
            "%28", // "("
            "%29", // ")"
            // "_", // "_"
            "%5F", "-", // "-"
            "%3D", // "="
            "%2B", // "+"
            "%7B", // "{"
            "%7D", // "}"
            "%5B", // "["
            "%5D", // "]"
            "%7C", // "|"
            "%3A", // ":"
            "%3B", // ";"
            "%2C", // ","
            ".", // "."
            "%3F", // "?"
            "%2F", // "/"
            "%7E", // "~"
            "+" // " "
    };

    public static String astring(Random rand, int min, int max) {
        return (rstring(rand, min, max, achars));
    }

    public static String nstring(Random rand, int min, int max) {
        return (rstring(rand, min, max, nchars));
    }

    private static String rstring(Random rand, int min, int max, String[] cset) {
        int l = cset.length;
        int r = nextInt(rand, max - min + 1) + min;
        String s;

        for (s = ""; s.length() < r; s = s + cset[nextInt(rand, l)])
            ;

        return (s);
    }

    public static final String[] countries = {"United+States",
            "United+Kingdom", "Canada", "Germany", "France", "Japan",
            "Netherlands", "Italy", "Switzerland", "Australia", "Algeria",
            "Argentina", "Armenia", "Austria", "Azerbaijan", "Bahamas",
            "Bahrain", "Bangla+Desh", "Barbados", "Belarus", "Belgium",
            "Bermuda", "Bolivia", "Botswana", "Brazil", "Bulgaria",
            "Cayman+Islands", "Chad", "Chile", "China", "Christmas+Island",
            "Colombia", "Croatia", "Cuba", "Cyprus", "Czech+Republic",
            "Denmark", "Dominican+Republic", "Eastern+Caribbean", "Ecuador",
            "Egypt", "El+Salvador", "Estonia", "Ethiopia", "Falkland+Island",
            "Faroe+Island", "Fiji", "Finland", "Gabon", "Gibraltar", "Greece",
            "Guam", "Hong+Kong", "Hungary", "Iceland", "India", "Indonesia",
            "Iran", "Iraq", "Ireland", "Israel", "Jamaica", "Jordan",
            "Kazakhstan", "Kuwait", "Lebanon", "Luxembourg", "Malaysia",
            "Mexico", "Mauritius", "New+Zealand", "Norway", "Pakistan",
            "Philippines", "Poland", "Portugal", "Romania", "Russia",
            "Saudi+Arabia", "Singapore", "Slovakia", "South+Africa",
            "South+Korea", "Spain", "Sudan", "Sweden", "Taiwan", "Thailand",
            "Trinidad", "Turkey", "Venezuela", "Zambia",};

    public static String unifCountry(Random rand) {
        return (countries[nextInt(rand, countries.length)]);
    }

    public static final Calendar c = new GregorianCalendar(1880, 1, 1);
    public static final long dobStart = c.getTime().getTime();
    ;
    public static final long dobEnd = System.currentTimeMillis();
    ;

    public static String unifThumbnail(Random rand) {
        int i = nextInt(rand, numItem) + 1;
        int grp = i % 100;
        return "img" + grp + "/thumb_" + i + ".gif";
    }

    public static String unifDOB(Random rand) {
        long t = ((long) (rand.nextDouble() * (dobEnd - dobStart))) + dobStart;
        Date d = new Date(t);
        Calendar c = new GregorianCalendar();
        c.setTime(d);

        return ("" + c.get(Calendar.DAY_OF_MONTH) + "%2f"
                + c.get(Calendar.DAY_OF_WEEK) + "%2f" + c.get(Calendar.YEAR));
    }

    public static final String[] ccTypes = {"VISA", "MASTERCARD", "DISCOVER",
            "DINERS", "AMEX"};

    public static String unifCCType(Random rand) {
        return (ccTypes[nextInt(rand, ccTypes.length)]);
    }

    public static String unifExpDate(Random rand) {
        Date d = new Date(System.currentTimeMillis()
                + ((long) (nextInt(rand, 730)) + 1) * 24L * 60L * 60L * 1000L);
        Calendar c = new GregorianCalendar();
        c.setTime(d);

        return ("" + c.get(Calendar.DAY_OF_MONTH) + "%2f"
                + c.get(Calendar.DAY_OF_WEEK) + "%2f" + c.get(Calendar.YEAR));

    }

    public static int unifDollars(Random rand) {
        return (nextInt(rand, 9999) + 1);
    }

    public static int unifCents(Random rand) {
        return (nextInt(rand, 100));
    }

    public static final String[] shipTypes = {"AIR", "UPS", "FEDEX", "SHIP",
            "COURIER", "MAIL"};

    public static String unifShipping(Random rand) {
        int i = nextInt(rand, shipTypes.length);
        return (shipTypes[i]);
    }

    // Needed, because Java 1.1 did not have Random.nextInt(int range)
    public static int nextInt(Random rand, int range) {
        int i = Math.abs(rand.nextInt());
        return (i % (range));
    }

}
