/*
 * TPCW_Populate.java - database population program
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
package tpcw;

//CAVEAT:
//These TPCW DB Population routines stray from the TPCW Spec in the 
//following ways:
//1. The a_lname field in the AUTHOR table is not generated using the DBGEN
//   utility, because of the current unavailability of this utility.
//2. Ditto for the I_TITLE field of the ITEM table.

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.*;

public class TPCW_Populate {

    private static Connection con;

    // ATTENTION: The NUM_EBS and NUM_ITEMS variables are the only variables
    // that should be modified in order to rescale the DB.

    // initial value: 50
    private static final int NUM_EBS = 5;

    // initial value: 10.000
    static final int NUM_ITEMS = 1000;

    public final static int NUM_CUSTOMERS = NUM_EBS * 2880;
    private final static int NUM_ADDRESSES = 2 * NUM_CUSTOMERS;
    private final static int NUM_AUTHORS = (int) (.25 * NUM_ITEMS);
    private final static int NUM_ORDERS = (int) (.9 * NUM_CUSTOMERS);

    public static String[] SUBJECTS = {"ARTS", "BIOGRAPHIES", "BUSINESS",
            "CHILDREN", "COMPUTERS", "COOKING", "HEALTH", "HISTORY", "HOME",
            "HUMOR", "LITERATURE", "MYSTERY", "NON-FICTION", "PARENTING",
            "POLITICS", "REFERENCE", "RELIGION", "ROMANCE", "SELF-HELP",
            "SCIENCE-NATURE", "SCIENCE_FICTION", "SPORTS", "YOUTH", "TRAVEL"};

    public static String[] countries = {"United States", "United Kingdom",
            "Canada", "Germany", "France", "Japan", "Netherlands", "Italy",
            "Switzerland", "Australia", "Algeria", "Argentina", "Armenia",
            "Austria", "Azerbaijan", "Bahamas", "Bahrain", "Bangla Desh",
            "Barbados", "Belarus", "Belgium", "Bermuda", "Bolivia", "Botswana",
            "Brazil", "Bulgaria", "Cayman Islands", "Chad", "Chile", "China",
            "Christmas Island", "Colombia", "Croatia", "Cuba", "Cyprus",
            "Czech Republic", "Denmark", "Dominican Republic",
            "Eastern Caribbean", "Ecuador", "Egypt", "El Salvador", "Estonia",
            "Ethiopia", "Falkland Island", "Faroe Island", "Fiji", "Finland",
            "Gabon", "Gibraltar", "Greece", "Guam", "Hong Kong", "Hungary",
            "Iceland", "India", "Indonesia", "Iran", "Iraq", "Ireland",
            "Israel", "Jamaica", "Jordan", "Kazakhstan", "Kuwait", "Lebanon",
            "Luxembourg", "Malaysia", "Mexico", "Mauritius", "New Zealand",
            "Norway", "Pakistan", "Philippines", "Poland", "Portugal",
            "Romania", "Russia", "Saudi Arabia", "Singapore", "Slovakia",
            "South Africa", "South Korea", "Spain", "Sudan", "Sweden",
            "Taiwan", "Thailand", "Trinidad", "Turkey", "Venezuela", "Zambia"};
    public static int NUM_COUNTRIES = 92;

    public static String[] credit_cards = {"VISA", "MASTERCARD", "DISCOVER",
            "AMEX", "DINERS"};
    public static int num_card_types = 5;

    public static String[] ship_types = {"AIR", "UPS", "FEDEX", "SHIP",
            "COURIER", "MAIL"};
    public static int num_ship_types = 6;

    public static String[] status_types = {"PROCESSING", "SHIPPED", "PENDING",
            "DENIED"};
    public static int num_status_types = 4;

    public final static int NUM_SUBJECTS = 24;

    // To be used by the benchmark
    public static List<String> customers = new ArrayList<String>();
    public static List<String> titles = new ArrayList<String>();
    public static List<String> authors = new ArrayList<String>();

    private static java.sql.Timestamp tmpTime = new java.sql.Timestamp(0);

    // private static final int NUM_ADDRESSES = 10;
    public static void populate(Connection con, int id) {
        Random rand = new Random(12345);
        TPCW_Populate.con = con;
        System.out.println("Beginning TPCW Database population for id " + id);
        createTables(id);
        populateAddressTable(rand, id);
        populateAuthorTable(rand, id);
        populateCountryTable(id);
        populateCustomerTable(rand, id);
        populateItemTable(rand, id);
        // Need to debug
        populateOrdersAndCC_XACTSTable(rand, id);
        addIndexes(id);
        System.out.println("Done");
    }

    private static void addIndexes(int id) {
        System.out.println("Adding Indexes");
        try {
            PreparedStatement statement1 = con
                    .prepareStatement("create index author_a_lname" + id
                            + " on author" + id + "(a_lname)");
            statement1.executeUpdate();
            PreparedStatement statement2 = con
                    .prepareStatement("create index address_addr_co_id" + id
                            + " on address" + id + "(addr_co_id)");
            statement2.executeUpdate();
            PreparedStatement statement3 = con
                    .prepareStatement("create index addr_zip" + id
                            + " on address" + id + "(addr_zip)");
            statement3.executeUpdate();
            PreparedStatement statement4 = con
                    .prepareStatement("create index customer_c_addr_id" + id
                            + " on customer" + id + "(c_addr_id)");
            statement4.executeUpdate();
            PreparedStatement statement5 = con
                    .prepareStatement("create index customer_c_uname" + id
                            + " on customer" + id + "(c_uname)");
            statement5.executeUpdate();
            PreparedStatement statement6 = con
                    .prepareStatement("create index item_i_title" + id
                            + " on item" + id + "(i_title)");
            statement6.executeUpdate();
            PreparedStatement statement7 = con
                    .prepareStatement("create index item_i_subject" + id
                            + " on item" + id + "(i_subject)");
            statement7.executeUpdate();
            PreparedStatement statement8 = con
                    .prepareStatement("create index item_i_a_id" + id
                            + " on item" + id + "(i_a_id)");
            statement8.executeUpdate();
            PreparedStatement statement9 = con
                    .prepareStatement("create index order_line_ol_i_id" + id
                            + " on order_line" + id + "(ol_i_id)");
            statement9.executeUpdate();
            PreparedStatement statement10 = con
                    .prepareStatement("create index order_line_ol_o_id" + id
                            + " on order_line" + id + "(ol_o_id)");
            statement10.executeUpdate();
            PreparedStatement statement11 = con
                    .prepareStatement("create index country_co_name" + id
                            + " on country" + id + "(co_name)");
            statement11.executeUpdate();
            PreparedStatement statement12 = con
                    .prepareStatement("create index orders_o_c_id" + id
                            + " on orders" + id + "(o_c_id)");
            statement12.executeUpdate();
            PreparedStatement statement13 = con
                    .prepareStatement("create index scl_i_id" + id
                            + " on shopping_cart_line" + id + "(scl_i_id)");
            statement13.executeUpdate();

            con.commit();
        } catch (java.lang.Exception ex) {
            System.out.println("Unable to add indexes");
            ex.printStackTrace();
            System.exit(1);
        }
    }

    private static void populateCustomerTable(Random rand, int id) {
        String C_UNAME, C_PASSWD, C_LNAME, C_FNAME;
        int C_ADDR_ID, C_PHONE;
        String C_EMAIL;
        java.sql.Date C_SINCE, C_LAST_LOGIN;
        java.sql.Timestamp C_LOGIN, C_EXPIRATION;
        double C_DISCOUNT, C_BALANCE, C_YTD_PMT;
        java.sql.Date C_BIRTHDATE;
        String C_DATA;
        int i;
        System.out.println("Populating CUSTOMER" + id + " Table with "
                + NUM_CUSTOMERS + " customers");
        System.out.print("Complete (in 10,000's): ");
        try {
            PreparedStatement statement = con
                    .prepareStatement("INSERT INTO CUSTOMER"
                            + id
                            + " (C_ID,C_UNAME,C_PASSWD,C_FNAME,C_LNAME,C_ADDR_ID,C_PHONE,C_EMAIL,C_SINCE,C_LAST_LOGIN,C_LOGIN,C_EXPIRATION,C_DISCOUNT,C_BALANCE,C_YTD_PMT,C_BIRTHDATE,C_DATA) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?,?)");
            for (i = 1; i <= NUM_CUSTOMERS; i++) {
                if (i % 10000 == 0) {
                    System.out.print(i / 10000 + " ");
                }
                C_UNAME = DigSyl(i, 0);
                customers.add(C_UNAME);

                C_PASSWD = C_UNAME.toLowerCase();
                C_LNAME = getRandomAString(8, 15, rand);
                C_FNAME = getRandomAString(8, 15, rand);
                C_ADDR_ID = getRandomInt(1, 2 * NUM_CUSTOMERS, rand);
                C_PHONE = getRandomNString(9, 16, rand);
                C_EMAIL = C_UNAME + "@" + getRandomAString(2, 9, rand) + ".com";

                GregorianCalendar cal = new GregorianCalendar();
                cal.add(Calendar.DAY_OF_YEAR, -1 * getRandomInt(1, 730, rand));
                C_SINCE = new java.sql.Date(cal.getTime().getTime());
                cal.add(Calendar.DAY_OF_YEAR, getRandomInt(0, 60, rand));
                if (cal.after(new GregorianCalendar()))
                    cal = new GregorianCalendar();

                C_LAST_LOGIN = new java.sql.Date(cal.getTime().getTime());
                C_LOGIN = new java.sql.Timestamp(System.currentTimeMillis());
                cal = new GregorianCalendar();
                cal.add(Calendar.HOUR, 2);
                C_EXPIRATION = new java.sql.Timestamp(cal.getTime().getTime());

                C_DISCOUNT = (double) getRandomInt(0, 50, rand) / 100.0;
                C_BALANCE = 0.00;
                C_YTD_PMT = (double) getRandomInt(0, 99999, rand) / 100.0;
                int year = getRandomInt(1880, 2000, rand);
                int month = getRandomInt(0, 11, rand);
                int maxday = 31;
                int day;
                if (month == 3 | month == 5 | month == 8 | month == 10)
                    maxday = 30;
                else if (month == 1)
                    maxday = 28;
                day = getRandomInt(1, maxday, rand);
                cal = new GregorianCalendar(year, month, day);
                C_BIRTHDATE = new java.sql.Date(cal.getTime().getTime());

                C_DATA = getRandomAString(100, 500, rand);

                try {// Set parameter
                    //MerkleTreeInstance.add(C_UNAME);
                    //MerkleTreeInstance.add(C_PASSWD);
                    //MerkleTreeInstance.add(C_FNAME);
                    //MerkleTreeInstance.add(C_LNAME);
                    //MerkleTreeInstance.add(C_EMAIL);
                    //MerkleTreeInstance.add(C_SINCE);
                    //MerkleTreeInstance.add(C_LAST_LOGIN);
                    //MerkleTreeInstance.add(C_LOGIN);
                    //MerkleTreeInstance.add(C_EXPIRATION);
                    //MerkleTreeInstance.add(C_DISCOUNT);
                    //MerkleTreeInstance.add(C_BALANCE);
                    //MerkleTreeInstance.add(C_YTD_PMT);
                    //MerkleTreeInstance.add(C_BIRTHDATE);
                    //MerkleTreeInstance.add(C_DATA);

                    statement.setInt(1, i);
                    statement.setString(2, C_UNAME);
                    statement.setString(3, C_PASSWD);
                    statement.setString(4, C_FNAME);
                    statement.setString(5, C_LNAME);
                    statement.setInt(6, C_ADDR_ID);
                    statement.setInt(7, C_PHONE);
                    statement.setString(8, C_EMAIL);
                    statement.setDate(9, C_SINCE);
                    statement.setDate(10, C_LAST_LOGIN);
                    statement.setTimestamp(11, tmpTime);//C_LOGIN);
                    statement.setTimestamp(12, tmpTime);//C_EXPIRATION);
                    statement.setDouble(13, C_DISCOUNT);
                    statement.setDouble(14, C_BALANCE);
                    statement.setDouble(15, C_YTD_PMT);
                    statement.setDate(16, C_BIRTHDATE);
                    statement.setString(17, C_DATA);
                    statement.executeUpdate();
                    if (i % 1000 == 0)
                        con.commit();
                } catch (java.lang.Exception ex) {
                    System.err.println("Unable to populate CUSTOMER" + id
                            + " table");
                    System.out.println("C_ID=" + i + " C_UNAME=" + C_UNAME
                            + " C_PASSWD=" + C_PASSWD + " C_FNAME=" + C_FNAME
                            + " C_LNAME=" + C_LNAME + " C_ADDR_ID=" + C_ADDR_ID
                            + " C_PHONE=" + C_PHONE + " C_EMAIL=" + C_EMAIL
                            + " C_SINCE=" + C_SINCE + " C_LAST_LOGIN="
                            + C_LAST_LOGIN + " C_LOGIN= " + C_LOGIN
                            + " C_EXPIRATION=" + C_EXPIRATION + " C_DISCOUNT="
                            + C_DISCOUNT + " C_BALANCE=" + C_BALANCE
                            + " C_YTD_PMT" + C_YTD_PMT + "C_BIRTHDATE="
                            + C_BIRTHDATE + "C_DATA=" + C_DATA);
                    ex.printStackTrace();
                    System.exit(1);
                }
            }
            System.out.print("\n");
            con.commit();
        } catch (java.lang.Exception ex) {
            System.err.println("Unable to populate CUSTOMER" + id + " table");
            ex.printStackTrace();
            System.exit(1);
        }
    }

    private static void populateAddressTable(Random rand, int id) {
        System.out.println("Populating ADDRESS" + id + " Table with "
                + NUM_ADDRESSES + " addresses");
        System.out.print("Complete (in 10,000's): ");
        String ADDR_STREET1, ADDR_STREET2, ADDR_CITY, ADDR_STATE;
        String ADDR_ZIP;
        int ADDR_CO_ID;
        try {
            PreparedStatement statement = con
                    .prepareStatement("INSERT INTO ADDRESS"
                            + id
                            + "(ADDR_ID,ADDR_STREET1,ADDR_STREET2,ADDR_CITY,ADDR_STATE,ADDR_ZIP,ADDR_CO_ID) VALUES (?, ?, ?, ?, ?, ?, ?)");
            for (int i = 1; i <= NUM_ADDRESSES; i++) {
                if (i % 10000 == 0)
                    System.out.print(i / 10000 + " ");
                ADDR_STREET1 = getRandomAString(15, 40, rand);
                ADDR_STREET2 = getRandomAString(15, 40, rand);
                ADDR_CITY = getRandomAString(4, 30, rand);
                ADDR_STATE = getRandomAString(2, 20, rand);
                ADDR_ZIP = getRandomAString(5, 10, rand);
                ADDR_CO_ID = getRandomInt(1, 92, rand);

                // Set parameter
                //MerkleTreeInstance.add(ADDR_STREET1);
                //MerkleTreeInstance.add(ADDR_STREET2);
                //MerkleTreeInstance.add(ADDR_CITY);
                //MerkleTreeInstance.add(ADDR_STATE);
                //MerkleTreeInstance.add(ADDR_ZIP);

                statement.setInt(1, i);
                statement.setString(2, ADDR_STREET1);
                statement.setString(3, ADDR_STREET2);
                statement.setString(4, ADDR_CITY);
                statement.setString(5, ADDR_STATE);
                statement.setString(6, ADDR_ZIP);
                statement.setInt(7, ADDR_CO_ID);
                statement.executeUpdate();
                if (i % 1000 == 0)
                    con.commit();
            }
            con.commit();
        } catch (java.lang.Exception ex) {
            System.err.println("Unable to populate ADDRESS" + id + " table");
            ex.printStackTrace();
            System.exit(1);
        }
        System.out.print("\n");
    }

    private static void populateAuthorTable(Random rand, int id) {
        String A_FNAME, A_MNAME, A_LNAME, A_BIO;
        java.sql.Date A_DOB;
        GregorianCalendar cal;

        System.out.println("Populating AUTHOR" + id + " Table with "
                + NUM_AUTHORS + " authors");

        try {
            PreparedStatement statement = con
                    .prepareStatement("INSERT INTO AUTHOR"
                            + id
                            + "(A_ID,A_FNAME,A_LNAME,A_MNAME,A_DOB,A_BIO) VALUES (?, ?, ?, ?, ?, ?)");
            for (int i = 1; i <= NUM_AUTHORS; i++) {
                int month, day, year, maxday;
                A_FNAME = getRandomAString(3, 20, rand);
                A_MNAME = getRandomAString(1, 20, rand);
                A_LNAME = getRandomAString(1, 20, rand);
                authors.add(A_LNAME);
                year = getRandomInt(1800, 1990, rand);
                month = getRandomInt(0, 11, rand);
                maxday = 31;
                if (month == 3 | month == 5 | month == 8 | month == 10)
                    maxday = 30;
                else if (month == 1)
                    maxday = 28;
                day = getRandomInt(1, maxday, rand);
                cal = new GregorianCalendar(year, month, day);
                A_DOB = new java.sql.Date(cal.getTime().getTime());
                A_BIO = getRandomAString(125, 500, rand);
                // Set parameter
                //MerkleTreeInstance.add(A_FNAME);
                //MerkleTreeInstance.add(A_LNAME);
                //MerkleTreeInstance.add(A_MNAME);
                //MerkleTreeInstance.add(A_DOB);
                //MerkleTreeInstance.add(A_BIO);

                statement.setInt(1, i);
                statement.setString(2, A_FNAME);
                statement.setString(3, A_LNAME);
                statement.setString(4, A_MNAME);
                statement.setDate(5, A_DOB);
                statement.setString(6, A_BIO);
                statement.executeUpdate();
                if (i % 1000 == 0)
                    con.commit();

            }
            con.commit();
        } catch (java.lang.Exception ex) {
            System.err.println("Unable to populate AUTHOR" + id + " table");
            ex.printStackTrace();
            System.exit(1);
        }
    }

    private static void populateCountryTable(int id) {

        double[] exchanges = {1, .625461, 1.46712, 1.86125, 6.24238, 121.907,
                2.09715, 1842.64, 1.51645, 1.54208, 65.3851, 0.998, 540.92,
                13.0949, 3977, 1, .3757, 48.65, 2, 248000, 38.3892, 1, 5.74,
                4.7304, 1.71, 1846, .8282, 627.1999, 494.2, 8.278, 1.5391,
                1677, 7.3044, 23, .543, 36.0127, 7.0707, 15.8, 2.7, 9600,
                3.33771, 8.7, 14.9912, 7.7, .6255, 7.124, 1.9724, 5.65822,
                627.1999, .6255, 309.214, 1, 7.75473, 237.23, 74.147, 42.75,
                8100, 3000, .3083, .749481, 4.12, 37.4, 0.708, 150, .3062,
                1502, 38.3892, 3.8, 9.6287, 25.245, 1.87539, 7.83101, 52,
                37.8501, 3.9525, 190.788, 15180.2, 24.43, 3.7501, 1.72929,
                43.9642, 6.25845, 1190.15, 158.34, 5.282, 8.54477, 32.77,
                37.1414, 6.1764, 401500, 596, 2447.7};

        String[] currencies = {"Dollars", "Pounds", "Dollars",
                "Deutsche Marks", "Francs", "Yen", "Guilders", "Lira",
                "Francs", "Dollars", "Dinars", "Pesos", "Dram", "Schillings",
                "Manat", "Dollars", "Dinar", "Taka", "Dollars", "Rouble",
                "Francs", "Dollars", "Boliviano", "Pula", "Real", "Lev",
                "Dollars", "Franc", "Pesos", "Yuan Renmimbi", "Dollars",
                "Pesos", "Kuna", "Pesos", "Pounds", "Koruna", "Kroner",
                "Pesos", "Dollars", "Sucre", "Pounds", "Colon", "Kroon",
                "Birr", "Pound", "Krone", "Dollars", "Markka", "Franc",
                "Pound", "Drachmas", "Dollars", "Dollars", "Forint", "Krona",
                "Rupees", "Rupiah", "Rial", "Dinar", "Punt", "Shekels",
                "Dollars", "Dinar", "Tenge", "Dinar", "Pounds", "Francs",
                "Ringgit", "Pesos", "Rupees", "Dollars", "Kroner", "Rupees",
                "Pesos", "Zloty", "Escudo", "Leu", "Rubles", "Riyal",
                "Dollars", "Koruna", "Rand", "Won", "Pesetas", "Dinar",
                "Krona", "Dollars", "Baht", "Dollars", "Lira", "Bolivar",
                "Kwacha"};

        System.out.println("Populating COUNTRY" + id + " with " + NUM_COUNTRIES
                + " countries");

        try {
            PreparedStatement statement = con
                    .prepareStatement("INSERT INTO COUNTRY"
                            + id
                            + "(CO_ID,CO_NAME,CO_EXCHANGE,CO_CURRENCY) VALUES (?,?,?,?)");
            for (int i = 1; i <= NUM_COUNTRIES; i++) {
                // Set parameter
                //MerkleTreeInstance.add(countries[i - 1]);
                //MerkleTreeInstance.add(exchanges[i - 1]);
                //MerkleTreeInstance.add(currencies[i - 1]);

                statement.setInt(1, i);
                statement.setString(2, countries[i - 1]);
                statement.setDouble(3, exchanges[i - 1]);
                statement.setString(4, currencies[i - 1]);
                statement.executeUpdate();
            }
            con.commit();
        } catch (java.lang.Exception ex) {
            System.err.println("Unable to populate COUNTRY" + id + " table");
            ex.printStackTrace();
            System.exit(1);
        }
    }

    private static void populateItemTable(Random rand, int id) {
        String I_TITLE;
        GregorianCalendar cal;
        int I_A_ID;
        java.sql.Date I_PUB_DATE;
        String I_PUBLISHER, I_SUBJECT, I_DESC;
        int I_RELATED1, I_RELATED2, I_RELATED3, I_RELATED4, I_RELATED5;
        String I_THUMBNAIL, I_IMAGE;
        double I_SRP, I_COST;
        java.sql.Date I_AVAIL;
        int I_STOCK;
        String I_ISBN;
        int I_PAGE;
        String I_BACKING;
        String I_DIMENSIONS;

        String[] BACKINGS = {"HARDBACK", "PAPERBACK", "USED", "AUDIO",
                "LIMITED-EDITION"};
        int NUM_BACKINGS = 5;

        System.out.println("Populating ITEM" + id + " table with " + NUM_ITEMS
                + " items");
        try {
            PreparedStatement statement = con
                    .prepareStatement("INSERT INTO ITEM"
                            + id
                            + " ( I_ID, I_TITLE , I_A_ID, I_PUB_DATE, I_PUBLISHER, I_SUBJECT, I_DESC, I_RELATED1, I_RELATED2, I_RELATED3, I_RELATED4, I_RELATED5, I_THUMBNAIL, I_IMAGE, I_SRP, I_COST, I_AVAIL, I_STOCK, I_ISBN, I_PAGE, I_BACKING, I_DIMENSIONS) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            for (int i = 1; i <= NUM_ITEMS; i++) {
                int month, day, year, maxday;
                I_TITLE = getRandomAString(14, 60, rand);
                titles.add(I_TITLE);
                if (i <= (NUM_ITEMS / 4))
                    I_A_ID = i;
                else
                    I_A_ID = getRandomInt(1, NUM_ITEMS / 4, rand);

                year = getRandomInt(1930, 2000, rand);
                month = getRandomInt(0, 11, rand);
                maxday = 31;
                if (month == 3 | month == 5 | month == 8 | month == 10)
                    maxday = 30;
                else if (month == 1)
                    maxday = 28;
                day = getRandomInt(1, maxday, rand);
                cal = new GregorianCalendar(year, month, day);
                I_PUB_DATE = new java.sql.Date(cal.getTime().getTime());

                I_PUBLISHER = getRandomAString(14, 60, rand);
                I_SUBJECT = SUBJECTS[getRandomInt(0, NUM_SUBJECTS - 1, rand)];
                I_DESC = getRandomAString(100, 500, rand);

                I_RELATED1 = getRandomInt(1, NUM_ITEMS, rand);
                do {
                    I_RELATED2 = getRandomInt(1, NUM_ITEMS, rand);
                } while (I_RELATED2 == I_RELATED1);
                do {
                    I_RELATED3 = getRandomInt(1, NUM_ITEMS, rand);
                } while (I_RELATED3 == I_RELATED1 || I_RELATED3 == I_RELATED2);
                do {
                    I_RELATED4 = getRandomInt(1, NUM_ITEMS, rand);
                } while (I_RELATED4 == I_RELATED1 || I_RELATED4 == I_RELATED2
                        || I_RELATED4 == I_RELATED3);
                do {
                    I_RELATED5 = getRandomInt(1, NUM_ITEMS, rand);
                } while (I_RELATED5 == I_RELATED1 || I_RELATED5 == I_RELATED2
                        || I_RELATED5 == I_RELATED3 || I_RELATED5 == I_RELATED4);

                I_THUMBNAIL = new String("img" + i % 100 + "/thumb_" + i
                        + ".gif");
                I_IMAGE = new String("img" + i % 100 + "/image_" + i + ".gif");
                I_SRP = (double) getRandomInt(100, 99999, rand);
                I_SRP /= 100.0;

                I_COST = I_SRP
                        - ((((double) getRandomInt(0, 50, rand) / 100.0)) * I_SRP);

                cal.add(Calendar.DAY_OF_YEAR, getRandomInt(1, 30, rand));
                I_AVAIL = new java.sql.Date(cal.getTime().getTime());
                I_STOCK = getRandomInt(10, 30, rand);
                I_ISBN = getRandomAString(13, rand);
                I_PAGE = getRandomInt(20, 9999, rand);
                I_BACKING = BACKINGS[getRandomInt(0, NUM_BACKINGS - 1, rand)];
                I_DIMENSIONS = ((double) getRandomInt(1, 9999, rand) / 100.0)
                        + "x" + ((double) getRandomInt(1, 9999, rand) / 100.0)
                        + "x" + ((double) getRandomInt(1, 9999, rand) / 100.0);

                // Set parameter
                //MerkleTreeInstance.add(I_TITLE);
                //MerkleTreeInstance.add(I_PUB_DATE);
                //MerkleTreeInstance.add(I_PUBLISHER);
                //MerkleTreeInstance.add(I_SUBJECT);
                //MerkleTreeInstance.add(I_DESC);
                //MerkleTreeInstance.add(I_THUMBNAIL);
                //MerkleTreeInstance.add(I_IMAGE);
                //MerkleTreeInstance.add(I_SRP);
                //MerkleTreeInstance.add(I_COST);
                //MerkleTreeInstance.add(I_AVAIL);
                //MerkleTreeInstance.add(I_ISBN);
                //MerkleTreeInstance.add(I_BACKING);
                //MerkleTreeInstance.add(I_DIMENSIONS);

                statement.setInt(1, i);
                statement.setString(2, I_TITLE);
                statement.setInt(3, I_A_ID);
                statement.setDate(4, I_PUB_DATE);
                statement.setString(5, I_PUBLISHER);
                statement.setString(6, I_SUBJECT);
                statement.setString(7, I_DESC);
                statement.setInt(8, I_RELATED1);
                statement.setInt(9, I_RELATED2);
                statement.setInt(10, I_RELATED3);
                statement.setInt(11, I_RELATED4);
                statement.setInt(12, I_RELATED5);
                statement.setString(13, I_THUMBNAIL);
                statement.setString(14, I_IMAGE);
                statement.setDouble(15, I_SRP);
                statement.setDouble(16, I_COST);
                statement.setDate(17, I_AVAIL);
                statement.setInt(18, I_STOCK);
                statement.setString(19, I_ISBN);
                statement.setInt(20, I_PAGE);
                statement.setString(21, I_BACKING);
                statement.setString(22, I_DIMENSIONS);

                statement.executeUpdate();
                if (i % 1000 == 0)
                    con.commit();
            }
            con.commit();
        } catch (java.lang.Exception ex) {
            System.err.println("Unable to populate ITEM" + id + " table");
            ex.printStackTrace();
            System.exit(1);
        }
    }

    private static void populateOrdersAndCC_XACTSTable(Random rand, int id) {
        GregorianCalendar cal;
        // Order variables
        int O_C_ID;
        java.sql.Timestamp O_DATE;
        double O_SUB_TOTAL;
        double O_TAX;
        double O_TOTAL;
        String O_SHIP_TYPE;
        java.sql.Timestamp O_SHIP_DATE;
        int O_BILL_ADDR_ID, O_SHIP_ADDR_ID;
        String O_STATUS;

        String CX_TYPE;
        int CX_NUM;
        String CX_NAME;
        java.sql.Date CX_EXPIRY;
        String CX_AUTH_ID;
        int CX_CO_ID;

        System.out.println("Populating ORDERS" + id + ", ORDER_LINES" + id
                + ", CC_XACTS" + id + " with " + NUM_ORDERS + " orders");

        System.out.print("Complete (in 10,000's): ");
        try {
            PreparedStatement statement = con
                    .prepareStatement("INSERT INTO ORDERS"
                            + id
                            + "(O_ID, O_C_ID, O_DATE, O_SUB_TOTAL, O_TAX, O_TOTAL, O_SHIP_TYPE, O_SHIP_DATE, O_BILL_ADDR_ID, O_SHIP_ADDR_ID, O_STATUS) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            PreparedStatement statement2 = con
                    .prepareStatement("INSERT INTO ORDER_LINE"
                            + id
                            + "(OL_ID, OL_O_ID, OL_I_ID, OL_QTY, OL_DISCOUNT, OL_COMMENTS) VALUES (?, ?, ?, ?, ?, ?)");
            PreparedStatement statement3 = con
                    .prepareStatement("INSERT INTO CC_XACTS"
                            + id
                            + "(CX_O_ID,CX_TYPE,CX_NUM,CX_NAME,CX_EXPIRE,CX_AUTH_ID,CX_XACT_AMT,CX_XACT_DATE,CX_CO_ID) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");

            for (int i = 1; i <= NUM_ORDERS; i++) {
                if (i % 10000 == 0)
                    System.out.print(i / 10000 + " ");
                int num_items = getRandomInt(1, 5, rand);
                O_C_ID = getRandomInt(1, NUM_CUSTOMERS, rand);
                cal = new GregorianCalendar();
                cal.add(Calendar.DAY_OF_YEAR, -1 * getRandomInt(1, 60, rand));
                O_DATE = new java.sql.Timestamp(cal.getTime().getTime());
                O_SUB_TOTAL = (double) getRandomInt(1000, 999999, rand) / 100;
                O_TAX = O_SUB_TOTAL * 0.0825;
                O_TOTAL = O_SUB_TOTAL + O_TAX + 3.00 + num_items;
                O_SHIP_TYPE = ship_types[getRandomInt(0, num_ship_types - 1,
                        rand)];
                cal.add(Calendar.DAY_OF_YEAR, getRandomInt(0, 7, rand));
                O_SHIP_DATE = new java.sql.Timestamp(cal.getTime().getTime());

                O_BILL_ADDR_ID = getRandomInt(1, 2 * NUM_CUSTOMERS, rand);
                O_SHIP_ADDR_ID = getRandomInt(1, 2 * NUM_CUSTOMERS, rand);
                O_STATUS = status_types[getRandomInt(0, num_status_types - 1,
                        rand)];

                // Set parameter
                //MerkleTreeInstance.add(O_DATE);
                //MerkleTreeInstance.add(O_SUB_TOTAL);
                //MerkleTreeInstance.add(O_TAX);
                //MerkleTreeInstance.add(O_TOTAL);
                //MerkleTreeInstance.add(O_SHIP_TYPE);
                //MerkleTreeInstance.add(O_SHIP_DATE);
                //MerkleTreeInstance.add(O_STATUS);

                statement.setInt(1, i);
                statement.setInt(2, O_C_ID);
                statement.setTimestamp(3, tmpTime);//O_DATE);
                statement.setDouble(4, O_SUB_TOTAL);
                statement.setDouble(5, O_TAX);
                statement.setDouble(6, O_TOTAL);
                statement.setString(7, O_SHIP_TYPE);
                statement.setTimestamp(8, tmpTime);//O_SHIP_DATE);
                statement.setInt(9, O_BILL_ADDR_ID);
                statement.setInt(10, O_SHIP_ADDR_ID);
                statement.setString(11, O_STATUS);
                statement.executeUpdate();

                for (int j = 1; j <= num_items; j++) {
                    int OL_ID = j;
                    int OL_O_ID = i;
                    int OL_I_ID = getRandomInt(1, NUM_ITEMS, rand);
                    int OL_QTY = getRandomInt(1, 300, rand);
                    double OL_DISCOUNT = (double) getRandomInt(0, 30, rand) / 100;
                    String OL_COMMENTS = getRandomAString(20, 100, rand);
                    //MerkleTreeInstance.add(OL_DISCOUNT);
                    //MerkleTreeInstance.add(OL_COMMENTS);

                    statement2.setInt(1, OL_ID);
                    statement2.setInt(2, OL_O_ID);
                    statement2.setInt(3, OL_I_ID);
                    statement2.setInt(4, OL_QTY);
                    statement2.setDouble(5, OL_DISCOUNT);
                    statement2.setString(6, OL_COMMENTS);
                    statement2.executeUpdate();
                }

                CX_TYPE = credit_cards[getRandomInt(0, num_card_types - 1, rand)];
                CX_NUM = getRandomNString(16, rand);
                CX_NAME = getRandomAString(14, 30, rand);
                cal = new GregorianCalendar();
                cal.add(Calendar.DAY_OF_YEAR, getRandomInt(10, 730, rand));
                CX_EXPIRY = new java.sql.Date(cal.getTime().getTime());
                CX_AUTH_ID = getRandomAString(15, rand);
                CX_CO_ID = getRandomInt(1, 92, rand);
                //MerkleTreeInstance.add(CX_TYPE);
                //MerkleTreeInstance.add(CX_NAME);
                //MerkleTreeInstance.add(CX_EXPIRY);
                //MerkleTreeInstance.add(CX_AUTH_ID);
                //MerkleTreeInstance.add(O_TOTAL);
                //MerkleTreeInstance.add(O_SHIP_DATE);

                statement3.setInt(1, i);
                statement3.setString(2, CX_TYPE);
                statement3.setInt(3, CX_NUM);
                statement3.setString(4, CX_NAME);
                statement3.setDate(5, CX_EXPIRY);
                statement3.setString(6, CX_AUTH_ID);
                statement3.setDouble(7, O_TOTAL);
                statement3.setTimestamp(8, tmpTime);//O_SHIP_DATE);
                statement3.setInt(9, CX_CO_ID);
                statement3.executeUpdate();

                if (i % 1000 == 0)
                    con.commit();
            }
            con.commit();
        } catch (java.lang.Exception ex) {
            System.err.println("Unable to populate CC_XACTS table");
            ex.printStackTrace();
            System.exit(1);
        }
        System.out.print("\n");
    }

    private static void createTables(int id) {
        try {
            String ADDR_ID = "ADDR_ID";
            PreparedStatement statement = con
                    .prepareStatement("CREATE TABLE address"
                            + id
                            + " ( "
                            + ADDR_ID
                            + " int not null, ADDR_STREET1 varchar(40), "
                            + "ADDR_STREET2 varchar(40), ADDR_CITY varchar(30), "
                            + "ADDR_STATE varchar(20), ADDR_ZIP varchar(10), "
                            + "ADDR_CO_ID int, PRIMARY KEY(ADDR_ID))");
            //MerkleTreeInstance.add(ADDR_ID);
            //MerkleTreeInstance.add("ADDR_STREET1");
            //MerkleTreeInstance.add("ADDR_STREET2");
            //MerkleTreeInstance.add("ADDR_CITY");
            //MerkleTreeInstance.add("ADDR_STATE");
            //MerkleTreeInstance.add("ADDR_ZIP");
            //MerkleTreeInstance.add("ADDR_CO_ID");

            statement.executeUpdate();
            con.commit();
            System.out.println("Created table ADDRESS" + id);
        } catch (java.lang.Exception ex) {
            System.out.println("Unable to create table: ADDRESS" + id);
            ex.printStackTrace();
            System.exit(1);
        }

        try {
            PreparedStatement statement = con
                    .prepareStatement("CREATE TABLE author"
                            + id
                            + " ( A_ID int not null, A_FNAME varchar(20), A_LNAME varchar(20), "
                            + "A_MNAME varchar(20), A_DOB date, A_BIO varchar(500), "
                            + "PRIMARY KEY(A_ID))");
            //MerkleTreeInstance.add("A_ID");
            //MerkleTreeInstance.add("A_FNAME");
            //MerkleTreeInstance.add("A_LNAME");
            //MerkleTreeInstance.add("A_MNAME");
            //MerkleTreeInstance.add("A_DOB");
            //MerkleTreeInstance.add("A_BIO");

            statement.executeUpdate();
            con.commit();
            System.out.println("Created table AUTHOR" + id);
        } catch (java.lang.Exception ex) {
            System.out.println("Unable to create table: AUTHOR" + id);
            ex.printStackTrace();
            System.exit(1);
        }

        try {
            PreparedStatement statement = con
                    .prepareStatement("CREATE TABLE cc_xacts"
                            + id
                            + " ( CX_O_ID int not null, CX_TYPE varchar(10), "
                            + "CX_NUM varchar(20), CX_NAME varchar(30), "
                            + "CX_EXPIRE date, CX_AUTH_ID char(15), CX_XACT_AMT double, "
                            + "CX_XACT_DATE date, CX_CO_ID int, PRIMARY KEY(CX_O_ID))");
            //MerkleTreeInstance.add("CX_O_ID");
            //MerkleTreeInstance.add("CX_TYPE");
            //MerkleTreeInstance.add("CX_NUM");
            //MerkleTreeInstance.add("CX_NAME");
            //MerkleTreeInstance.add("CX_EXPIRE");
            //MerkleTreeInstance.add("CX_AUTH_ID");
            //MerkleTreeInstance.add("CX_XACT_AMT");
            //MerkleTreeInstance.add("CX_XACT_DATE");
            //MerkleTreeInstance.add("CX_CO_ID");

            statement.executeUpdate();
            con.commit();
            System.out.println("Created table CC_XACTS" + id);
        } catch (java.lang.Exception ex) {
            System.out.println("Unable to create table: CC_XACTS" + id);
            ex.printStackTrace();
            System.exit(1);
        }

        try {
            PreparedStatement statement = con
                    .prepareStatement("CREATE TABLE country"
                            + id
                            + " ( CO_ID int not null, CO_NAME varchar(50), CO_EXCHANGE double, "
                            + "CO_CURRENCY varchar(18), PRIMARY KEY(CO_ID))");

            //MerkleTreeInstance.add("CO_ID");
            //MerkleTreeInstance.add("CO_NAME");
            //MerkleTreeInstance.add("CO_EXCHANGE");
            //MerkleTreeInstance.add("CO_CURRENCY");

            statement.executeUpdate();
            con.commit();
            System.out.println("Created table COUNTRY" + id);
        } catch (java.lang.Exception ex) {
            System.out.println("Unable to create table: COUNTRY" + id);
            ex.printStackTrace();
            System.exit(1);
        }
        try {
            PreparedStatement statement = con
                    .prepareStatement("CREATE TABLE customer"
                            + id
                            + " ( C_ID int not null, C_UNAME varchar(20), C_PASSWD varchar(20), "
                            + "C_FNAME varchar(17), C_LNAME varchar(17), C_ADDR_ID int, "
                            + "C_PHONE varchar(18), C_EMAIL varchar(50), C_SINCE date, "
                            + "C_LAST_LOGIN date, C_LOGIN timestamp, C_EXPIRATION timestamp, "
                            + "C_DISCOUNT real, C_BALANCE double, C_YTD_PMT double, "
                            + "C_BIRTHDATE date, C_DATA varchar(510), PRIMARY KEY(C_ID))");
            //MerkleTreeInstance.add("C_ID");
            //MerkleTreeInstance.add("C_UNAME");
            //MerkleTreeInstance.add("C_PASSWD");
            //MerkleTreeInstance.add("C_FNAME");
            //MerkleTreeInstance.add("C_LNAME");
            //MerkleTreeInstance.add("C_ADDR_ID");
            //MerkleTreeInstance.add("C_PHONE");
            //MerkleTreeInstance.add("C_EMAIL");
            //MerkleTreeInstance.add("C_SINCE");
            //MerkleTreeInstance.add("C_LAST_LOGIN");
            //MerkleTreeInstance.add("C_LOGIN");
            //MerkleTreeInstance.add("C_EXPIRATION");
            //MerkleTreeInstance.add("C_DISCOUNT");
            //MerkleTreeInstance.add("C_BALANCE");
            //MerkleTreeInstance.add("C_YTD_PMT");
            //MerkleTreeInstance.add("C_BIRTHDATE");
            //MerkleTreeInstance.add("C_DATA");

            statement.executeUpdate();
            con.commit();
            System.out.println("Created table CUSTOMER" + id);
        } catch (java.lang.Exception ex) {
            System.out.println("Unable to create table: CUSTOMER" + id);
            ex.printStackTrace();
            System.exit(1);
        }

        try {
            PreparedStatement statement = con
                    .prepareStatement("CREATE TABLE item"
                            + id
                            + " ( I_ID int not null, I_TITLE varchar(60), I_A_ID int, "
                            + "I_PUB_DATE date, I_PUBLISHER varchar(60), I_SUBJECT varchar(60), "
                            + "I_DESC varchar(500), I_RELATED1 int, I_RELATED2 int, I_RELATED3 int, "
                            + "I_RELATED4 int, I_RELATED5 int, I_THUMBNAIL varchar(40), "
                            + "I_IMAGE varchar(40), I_SRP double, I_COST double, I_AVAIL date, "
                            + "I_STOCK int, I_ISBN char(13), I_PAGE int, I_BACKING varchar(15), "
                            + "I_DIMENSIONS varchar(25), PRIMARY KEY(I_ID))");
            //MerkleTreeInstance.add("I_ID");
            //MerkleTreeInstance.add("I_TITLE");
            //MerkleTreeInstance.add("I_A_ID");
            //MerkleTreeInstance.add("I_PUB_DATE");
            //MerkleTreeInstance.add("I_PUBLISHER");
            //MerkleTreeInstance.add("I_SUBJECT");
            //MerkleTreeInstance.add("I_DESC");
            //MerkleTreeInstance.add("I_RELATED1");
            //MerkleTreeInstance.add("I_RELATED2");
            //MerkleTreeInstance.add("I_RELATED3");
            //MerkleTreeInstance.add("I_RELATED4");
            //MerkleTreeInstance.add("I_RELATED5");
            //MerkleTreeInstance.add("I_THUMBNAIL");
            //MerkleTreeInstance.add("I_IMAGE");
            //MerkleTreeInstance.add("I_SRP");
            //MerkleTreeInstance.add("I_COST");
            //MerkleTreeInstance.add("I_AVAIL");
            //MerkleTreeInstance.add("I_STOCK");
            //MerkleTreeInstance.add("I_ISBN");
            //MerkleTreeInstance.add("I_PAGE");
            //MerkleTreeInstance.add("I_BACKING");
            //MerkleTreeInstance.add("I_DIMENSIONS");

            statement.executeUpdate();
            con.commit();
            System.out.println("Created table ITEM" + id);
        } catch (java.lang.Exception ex) {
            System.out.println("Unable to create table: ITEM" + id);
            ex.printStackTrace();
            System.exit(1);
        }

        try {
            PreparedStatement statement = con
                    .prepareStatement("CREATE TABLE order_line"
                            + id
                            + " ( OL_ID int not null, OL_O_ID int not null, OL_I_ID int, OL_QTY int, "
                            + "OL_DISCOUNT double, OL_COMMENTS varchar(110), PRIMARY KEY(OL_ID, OL_O_ID))");
            //MerkleTreeInstance.add("OL_ID");
            //MerkleTreeInstance.add("OL_O_ID");
            //MerkleTreeInstance.add("OL_I_ID");
            //MerkleTreeInstance.add("OL_QTY");
            //MerkleTreeInstance.add("OL_DISCOUNT");
            //MerkleTreeInstance.add("OL_COMMENTS");

            statement.executeUpdate();
            con.commit();
            System.out.println("Created table ORDER_LINE" + id);
        } catch (java.lang.Exception ex) {
            System.out.println("Unable to create table: ORDER_LINE" + id);
            ex.printStackTrace();
            System.exit(1);
        }

        try {
            PreparedStatement statement = con
                    .prepareStatement("CREATE TABLE orders"
                            + id
                            + " ( O_ID int not null, O_C_ID int, O_DATE date, O_SUB_TOTAL double, "
                            + "O_TAX double, O_TOTAL double, O_SHIP_TYPE varchar(10), O_SHIP_DATE date, "
                            + "O_BILL_ADDR_ID int, O_SHIP_ADDR_ID int, O_STATUS varchar(15), PRIMARY KEY(O_ID))");
            //MerkleTreeInstance.add("O_ID");
            //MerkleTreeInstance.add("O_C_ID");
            //MerkleTreeInstance.add("O_DATE");
            //MerkleTreeInstance.add("O_SUB_TOTAL");
            //MerkleTreeInstance.add("O_TAX");
            //MerkleTreeInstance.add("O_TOTAL");
            //MerkleTreeInstance.add("O_SHIP_TYPE");
            //MerkleTreeInstance.add("O_SHIP_DATE");
            //MerkleTreeInstance.add("O_BILL_ADDR_ID");
            //MerkleTreeInstance.add("O_SHIP_ADDR_ID");
            //MerkleTreeInstance.add("O_STATUS");
            //MerkleTreeInstance.add("O_TOTAL");

            statement.executeUpdate();
            con.commit();
            System.out.println("Created table ORDERS" + id);
        } catch (java.lang.Exception ex) {
            System.out.println("Unable to create table: ORDERS" + id);
            ex.printStackTrace();
            System.exit(1);
        }

        try {
            PreparedStatement statement = con
                    .prepareStatement("CREATE TABLE shopping_cart"
                            + id
                            + " ( SC_ID int not null, SC_TIME timestamp, PRIMARY KEY(SC_ID))");
            //MerkleTreeInstance.add("SC_ID");
            //MerkleTreeInstance.add("SC_TIME");

            statement.executeUpdate();
            con.commit();
            System.out.println("Created table SHOPPING_CART" + id);
        } catch (java.lang.Exception ex) {
            System.out.println("Unable to create table: SHOPPING_CART" + id);
            ex.printStackTrace();
            System.exit(1);
        }
        try {
            PreparedStatement statement = con
                    .prepareStatement("CREATE TABLE shopping_cart_line"
                            + id
                            + " ( SCL_SC_ID int not null, SCL_QTY int, SCL_I_ID int not null, PRIMARY KEY(SCL_SC_ID, SCL_I_ID))");
            //MerkleTreeInstance.add("SCL_SC_ID");
            //MerkleTreeInstance.add("SCL_QTY");
            //MerkleTreeInstance.add("SCL_I_ID");

            statement.executeUpdate();
            con.commit();
            System.out.println("Created table SHOPPING_CART_LINE" + id);
        } catch (java.lang.Exception ex) {
            System.out.println("Unable to create table: SHOPPING_CART_LINE"
                    + id);
            ex.printStackTrace();
            System.exit(1);
        }

        System.out.println("Done creating tables!");

    }

    // UTILITY FUNCTIONS BEGIN HERE
    public static String getRandomAString(int min, int max, Random rand) {
        String newstring = new String();
        int i;
        final char[] chars = {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i',
                'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u',
                'v', 'w', 'x', 'y', 'z', 'A', 'B', 'C', 'D', 'E', 'F', 'G',
                'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S',
                'T', 'U', 'V', 'W', 'X', 'Y', 'Z', '!', '@', '#', '$', '%',
                '^', '&', '*', '(', ')', '_', '-', '=', '+', '{', '}', '[',
                ']', '|', ':', ';', ',', '.', '?', '/', '~', ' '}; // 79
        // characters
        int strlen = (int) Math.floor(rand.nextDouble() * ((max - min) + 1));
        strlen += min;
        for (i = 0; i < strlen; i++) {
            char c = chars[(int) Math.floor(rand.nextDouble() * 79)];
            newstring = newstring.concat(String.valueOf(c));
        }
        return newstring;
    }

    private static String getRandomAString(int length, Random rand) {
        String newstring = new String();
        int i;
        final char[] chars = {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i',
                'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u',
                'v', 'w', 'x', 'y', 'z', 'A', 'B', 'C', 'D', 'E', 'F', 'G',
                'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S',
                'T', 'U', 'V', 'W', 'X', 'Y', 'Z', '!', '@', '#', '$', '%',
                '^', '&', '*', '(', ')', '_', '-', '=', '+', '{', '}', '[',
                ']', '|', ':', ';', ',', '.', '?', '/', '~', ' '}; // 79
        // characters
        for (i = 0; i < length; i++) {
            char c = chars[(int) Math.floor(rand.nextDouble() * 79)];
            newstring = newstring.concat(String.valueOf(c));
        }
        return newstring;
    }

    private static int getRandomNString(int num_digits, Random rand) {
        int return_num = 0;
        for (int i = 0; i < num_digits; i++) {
            return_num += getRandomInt(0, 9, rand)
                    * (int) java.lang.Math.pow(10.0, (double) i);
        }
        return return_num;
    }

    private static int getRandomNString(int min, int max, Random rand) {
        int strlen = (int) Math.floor(rand.nextDouble() * ((max - min) + 1))
                + min;
        return getRandomNString(strlen, rand);
    }

    static int getRandomInt(int lower, int upper, Random rand) {

        int num = (int) Math.floor(rand.nextDouble() * ((upper + 1) - lower));
        if (num + lower > upper || num + lower < lower) {
            System.out.println("ERROR: Random returned value of of range!");
            System.exit(1);
        }
        return num + lower;
    }

    private static String DigSyl(int D, int N) {
        int i;
        String resultString = new String();
        String Dstr = Integer.toString(D);

        if (N > Dstr.length()) {
            int padding = N - Dstr.length();
            for (i = 0; i < padding; i++)
                resultString = resultString.concat("BA");
        }

        for (i = 0; i < Dstr.length(); i++) {
            if (Dstr.charAt(i) == '0')
                resultString = resultString.concat("BA");
            else if (Dstr.charAt(i) == '1')
                resultString = resultString.concat("OG");
            else if (Dstr.charAt(i) == '2')
                resultString = resultString.concat("AL");
            else if (Dstr.charAt(i) == '3')
                resultString = resultString.concat("RI");
            else if (Dstr.charAt(i) == '4')
                resultString = resultString.concat("RE");
            else if (Dstr.charAt(i) == '5')
                resultString = resultString.concat("SE");
            else if (Dstr.charAt(i) == '6')
                resultString = resultString.concat("AT");
            else if (Dstr.charAt(i) == '7')
                resultString = resultString.concat("UL");
            else if (Dstr.charAt(i) == '8')
                resultString = resultString.concat("IN");
            else if (Dstr.charAt(i) == '9')
                resultString = resultString.concat("NG");
        }

        return resultString;
    }
}
