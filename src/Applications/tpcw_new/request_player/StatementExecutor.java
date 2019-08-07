package Applications.tpcw_new.request_player;

import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Random;
import java.util.Vector;

public class StatementExecutor {
    /*
     * execute the statement of code stmt_code with args arguments and process
     * the result as in TPCW
     */
    @SuppressWarnings("unused")
    public static void executeNProcess(int stmt_code, PreparedStatement ps,
                                       String arguments, DBStatements dbStatements) throws SQLException {
        System.err.println("Arguments: " + arguments);
        // set args
        setArguments(ps, arguments);
        //System.err.println(ps);
        if (stmt_code == dbStatements.code_getName()) {
            ResultSet rs = ps.executeQuery();
            DatabaseStats.addRead();

            // Results
            rs.next();
            String name = rs.getString("c_fname");
            name = rs.getString("c_lname");
            rs.close();
        } else if (stmt_code == dbStatements.code_getBook()) {
            ResultSet rs = ps.executeQuery();
            DatabaseStats.addRead();

            // Results
            rs.next();
            Book book = new Book(rs);
            rs.close();
        } else if (stmt_code == dbStatements.code_getCustomer()) {
            ResultSet rs = ps.executeQuery();
            DatabaseStats.addRead();

            // Results
            if (rs.next()) {
                Customer cust = new Customer(rs);
            }
            rs.close();
        } else if (stmt_code == dbStatements.code_doSubjectSearch()) {
            ResultSet rs = ps.executeQuery();
            DatabaseStats.addRead();

            // Results
            Vector<Book> vec = new Vector<Book>();
            while (rs.next()) {
                vec.addElement(new Book(rs));
            }
            rs.close();
        } else if (stmt_code == dbStatements.code_doTitleSearch()) {
            ResultSet rs = ps.executeQuery();
            DatabaseStats.addRead();

            // Results
            Vector<Book> vec = new Vector<Book>();
            while (rs.next()) {
                vec.addElement(new Book(rs));
            }
            rs.close();
        } else if (stmt_code == dbStatements.code_doAuthorSearch()) {
            ResultSet rs = ps.executeQuery();
            DatabaseStats.addRead();

            // Results
            Vector<Book> vec = new Vector<Book>();
            while (rs.next()) {
                vec.addElement(new Book(rs));
            }
            rs.close();
        } else if (stmt_code == dbStatements.code_getNewProducts()) {
            ResultSet rs = ps.executeQuery();
            DatabaseStats.addRead();

            // Results
            Vector<ShortBook> vec = new Vector<ShortBook>();
            while (rs.next()) {
                vec.addElement(new ShortBook(rs));
            }
            rs.close();
        } else if (stmt_code == dbStatements.code_getBestSellers()) {
            ResultSet rs = ps.executeQuery();
            DatabaseStats.addRead();

            // Results
            Vector<ShortBook> vec = new Vector<ShortBook>();
            while (rs.next()) {
                vec.addElement(new ShortBook(rs));
            }
            rs.close();
        } else if (stmt_code == dbStatements.code_getRelated()) {
            ResultSet rs = ps.executeQuery();
            DatabaseStats.addRead();

            // Clear the vectors
            Vector<Integer> i_id_vec = new Vector<Integer>();
            Vector<String> i_thumbnail_vec = new Vector<String>();
            i_id_vec.removeAllElements();
            i_thumbnail_vec.removeAllElements();

            // Results
            while (rs.next()) {
                i_id_vec.addElement(new Integer(rs.getInt(1)));
                i_thumbnail_vec.addElement(rs.getString(2));
            }
            rs.close();
        } else if (stmt_code == dbStatements.code_adminUpdate1()) {
            ps.executeUpdate();
            DatabaseStats.addWrite();
        } else if (stmt_code == dbStatements.code_adminUpdate2()) {
            ResultSet rs = ps.executeQuery();
            DatabaseStats.addRead();

            int[] related_items = new int[5];
            // Results
            int counter = 0;
            int last = 0;
            while (rs.next()) {
                last = rs.getInt(1);
                related_items[counter] = last;
                counter++;
            }

            // This is the case for the situation where there are not 5 related
            // books.
            for (int i = counter; i < 5; i++) {
                last++;
                related_items[i] = last;
            }
            rs.close();
        } else if (stmt_code == dbStatements.code_adminUpdate3()) {
            ps.executeUpdate();
            DatabaseStats.addWrite();
        } else if (stmt_code == dbStatements.code_GetUserName()) {
            ResultSet rs = ps.executeQuery();
            DatabaseStats.addRead();

            // Results
            rs.next();
            String u_name = rs.getString("c_uname");
            rs.close();
        } else if (stmt_code == dbStatements.code_GetPassword()) {
            ResultSet rs = ps.executeQuery();
            DatabaseStats.addRead();

            // Results
            rs.next();
            String passwd = rs.getString("c_passwd");
            rs.close();
        } else if (stmt_code == dbStatements.code_getRelated1()) {
            ResultSet rs = ps.executeQuery();
            DatabaseStats.addRead();

            if (rs.next()) {
                int related1 = rs.getInt(1);// Is 1 the correct index?
            }
            rs.close();
        } else if (stmt_code == dbStatements.code_GetMostRecentOrder1()) {
            ResultSet rs = ps.executeQuery();
            DatabaseStats.addRead();

            if (rs.next()) {
                int order_id = rs.getInt("o_id");
            }
            rs.close();
        } else if (stmt_code == dbStatements.code_GetMostRecentOrder2()) {
            ResultSet rs2 = ps.executeQuery();
            DatabaseStats.addRead();

            // Results
            if (rs2.next()) {
                Order order = new Order(rs2);
            }
            rs2.close();
        } else if (stmt_code == dbStatements.code_GetMostRecentOrder3()) {
            ResultSet rs3 = ps.executeQuery();
            DatabaseStats.addRead();

            // Results
            Vector<OrderLine> order_lines = new Vector<OrderLine>();
            while (rs3.next()) {
                order_lines.addElement(new OrderLine(rs3));
            }
            rs3.close();
        } else if (stmt_code == dbStatements.code_createEmptyCart1()) {
            ResultSet rs = ps.executeQuery();
            DatabaseStats.addRead();

            rs.next();
            int SHOPPING_ID = rs.getInt(1) + 1;
            rs.close();
        } else if (stmt_code == dbStatements.code_createEmptyCart2()) {
            ps.executeUpdate();
            DatabaseStats.addWrite();
        } else if (stmt_code == dbStatements.code_addItem1()) {

            ResultSet rs = ps.executeQuery();
            DatabaseStats.addRead();

            // Results
            if (rs.next()) {
                // The shopping cart id, item pair were already in the table
                int currqty = rs.getInt("scl_qty");
                currqty += 1;
            }
            rs.close();
        } else if (stmt_code == dbStatements.code_addItem2()) {
            ps.executeUpdate();
            DatabaseStats.addWrite();
        } else if (stmt_code == dbStatements.code_addItem3()) {
            ps.executeUpdate();
            DatabaseStats.addWrite();
        } else if (stmt_code == dbStatements.code_refreshCart1()) {
            ps.executeUpdate();
            DatabaseStats.addWrite();
        } else if (stmt_code == dbStatements.code_refreshCart2()) {
            ps.executeUpdate();
            DatabaseStats.addWrite();
        } else if (stmt_code == dbStatements
                .code_addRandomItemToCartIfNecessary()) {
            ResultSet rs = ps.executeQuery();
            DatabaseStats.addRead();

            rs.next();
            int x = rs.getInt(1);
            rs.close();
        } else if (stmt_code == dbStatements.code_resetCartTime()) {
            ps.executeUpdate();
            DatabaseStats.addWrite();
        } else if (stmt_code == dbStatements.code_getCart()) {
            ResultSet rs = ps.executeQuery();
            DatabaseStats.addRead();

            Cart mycart = new Cart(rs, 0.0);
            rs.close();
        } else if (stmt_code == dbStatements.code_refreshSession()) {
            ps.executeUpdate();
            DatabaseStats.addWrite();
        } else if (stmt_code == dbStatements.code_createNewCustomer1()) {
            ps.executeUpdate();
            DatabaseStats.addWrite();
        } else if (stmt_code == dbStatements.code_createNewCustomer2()) {
            ResultSet rs = ps.executeQuery();
            DatabaseStats.addRead();

            // Results
            rs.next();
            int c_id = rs.getInt(1);
            rs.close();
            c_id += 1;
            String c_uname = TPCW_Util.DigSyl(c_id, 0);
            String c_passwd = c_uname.toLowerCase();
            DatabaseStats.addRead();
        } else if (stmt_code == dbStatements.code_getCDiscount()) {
            ResultSet rs = ps.executeQuery();
            DatabaseStats.addRead();

            // Results
            rs.next();
            double c_discount = rs.getDouble(1);
            rs.close();
        } else if (stmt_code == dbStatements.code_getCaddrID()) {
            ResultSet rs = ps.executeQuery();
            DatabaseStats.addRead();

            // Results
            rs.next();
            int c_addr_id = rs.getInt(1);
            rs.close();
        } else if (stmt_code == dbStatements.code_getCAddr()) {
            ResultSet rs = ps.executeQuery();
            DatabaseStats.addRead();

            // Results
            rs.next();
            int c_addr_id = rs.getInt(1);
            rs.close();
        } else if (stmt_code == dbStatements.code_enterCCXact()) {
            ps.executeUpdate();
            DatabaseStats.addWrite();
        } else if (stmt_code == dbStatements.code_clearCart()) {
            ps.executeUpdate();
            DatabaseStats.addWrite();
        } else if (stmt_code == dbStatements.code_enterAddress1()) {
            ResultSet rs = ps.executeQuery();
            DatabaseStats.addRead();

            rs.next();
            int addr_co_id = rs.getInt("co_id");
            rs.close();
        } else if (stmt_code == dbStatements.code_enterAddress2()) {
            ResultSet rs = ps.executeQuery();
            DatabaseStats.addRead();

            if (rs.next()) {
                int addr_id = rs.getInt("addr_id");
            }
            rs.close();
        } else if (stmt_code == dbStatements.code_enterAddress3()) {
            ps.executeUpdate();
            DatabaseStats.addWrite();
        } else if (stmt_code == dbStatements.code_enterAddress4()) {
            ResultSet rs2 = ps.executeQuery();
            DatabaseStats.addRead();

            rs2.next();
            int addr_id = rs2.getInt(1) + 1;
            rs2.close();
        } else if (stmt_code == dbStatements.code_enterOrder1()) {
            ps.executeUpdate();
            DatabaseStats.addWrite();
        } else if (stmt_code == dbStatements.code_enterOrder2()) {
            ResultSet rs = ps.executeQuery();
            DatabaseStats.addRead();

            rs.next();
            int o_id = rs.getInt(1) + 1;
            rs.close();
        } else if (stmt_code == dbStatements.code_addOrderLine()) {
            ps.executeUpdate();
            DatabaseStats.addWrite();
        } else if (stmt_code == dbStatements.code_getStock()) {
            ResultSet rs = ps.executeQuery();
            DatabaseStats.addRead();

            // Results
            rs.next();
            int stock = rs.getInt("i_stock");
            rs.close();
        } else if (stmt_code == dbStatements.code_setStock()) {
            ps.executeUpdate();
            DatabaseStats.addWrite();
        } else if (stmt_code == dbStatements.code_verifyDBConsistency1()) {
            ResultSet rs = ps.executeQuery();
            DatabaseStats.addRead();

            int this_id;
            int id_expected = 1;
            while (rs.next()) {
                this_id = rs.getInt("c_id");
                while (this_id != id_expected) {
                    System.out.println("Missing C_ID " + id_expected);
                    id_expected++;
                }
                id_expected++;
            }
            rs.close();
        } else if (stmt_code == dbStatements.code_verifyDBConsistency2()) {
            ResultSet rs = ps.executeQuery();
            DatabaseStats.addRead();

            int this_id;
            int id_expected = 1;
            while (rs.next()) {
                this_id = rs.getInt("i_id");
                while (this_id != id_expected) {
                    System.out.println("Missing I_ID " + id_expected);
                    id_expected++;
                }
                id_expected++;
            }
            rs.close();
        } else if (stmt_code == dbStatements.code_verifyDBConsistency3()) {
            ResultSet rs = ps.executeQuery();
            DatabaseStats.addRead();

            int this_id;
            int id_expected = 1;
            while (rs.next()) {
                this_id = rs.getInt("addr_id");
                while (this_id != id_expected) {
                    System.out.println("Missing ADDR_ID " + id_expected);
                    id_expected++;
                }
                id_expected++;
            }
            rs.close();
        } else {
            System.err.println("Statement code " + stmt_code + " is unknown");
        }
    }

    /*
     * execute the PreparedStatement ps with arguments arguments
     */
    public static void execute(PreparedStatement ps, String arguments,
                               boolean isRead) throws SQLException {
        // set args
        setArguments(ps, arguments);
        // execute
        boolean isExecuteQuery = ps.execute();

        if (isExecuteQuery || isRead) {
            DatabaseStats.addRead();
        } else {
            DatabaseStats.addWrite();
        }
    }

    /*
     * execute the statement statement with arguments arguments
     */
    public static void execute(Connection con, String statement,
                               String arguments) throws SQLException {
        // create PreparedStatement
        PreparedStatement ps = con.prepareStatement(statement);

        // set args
        setArguments(ps, arguments);

        boolean isExecuteQuery = ps.execute();

        if (isExecuteQuery || statement.contains("SELECT")) {
            DatabaseStats.addRead();
        } else {
            DatabaseStats.addWrite();
        }
    }

    /**
     * Set the arguments in arguments in the prepared statement ps Source:
     * Sequoia's PreparedStatementSerialization.setPreparedStatement() method
     */
    private static void setArguments(PreparedStatement backendPS,
                                     String parameters) throws SQLException {
        int i = 0;
        int paramIdx = 0;

        // Set all parameters
        while ((i = parameters.indexOf(RequestPlayerUtils.START_PARAM_TAG, i)) > -1) {
            paramIdx++;

            int typeStart = i + RequestPlayerUtils.START_PARAM_TAG.length();

            // Here we assume that all tags have the same length as the
            // boolean tag.
            String paramType = parameters.substring(typeStart, typeStart
                    + RequestPlayerUtils.BOOLEAN_TAG.length());
            String paramValue = parameters.substring(typeStart
                            + RequestPlayerUtils.BOOLEAN_TAG.length(),
                    parameters.indexOf(RequestPlayerUtils.END_PARAM_TAG, i));
            paramValue = replace(paramValue,
                    RequestPlayerUtils.TAG_MARKER_ESCAPE,
                    RequestPlayerUtils.TAG_MARKER);

            if (!performCallOnPreparedStatement(backendPS, paramIdx, paramType,
                    paramValue)) {
                // invalid parameter, we want to be able to store strings
                // like
                // <?xml version="1.0" encoding="ISO-8859-1"?>
                paramIdx--;
            }
            i = typeStart + paramValue.length();
        }
    }

    private static boolean performCallOnPreparedStatement(
            java.sql.PreparedStatement backendPS, int paramIdx,
            String paramType, String paramValue) throws SQLException {
        // Test tags in alphabetical order (to make the code easier to read)
        /*
		 * if (paramType.equals(RequestPlayerUtils.ARRAY_TAG)) { // in case of
		 * Array, the String contains: // - integer: sql type // - string: sql
		 * type name, delimited by ARRAY_BASETYPE_NAME_SEPARATOR // - serialized
		 * object array final String commonMsg = "Failed to deserialize Array
		 * parameter of setObject()"; int baseType; String baseTypeName; // Get
		 * start and end of the type name string int startBaseTypeName =
		 * paramValue.indexOf(RequestPlayerUtils.ARRAY_BASETYPE_NAME_SEPARATOR
		 * ); int endBaseTypeName =
		 * paramValue.indexOf(RequestPlayerUtils.ARRAY_BASETYPE_NAME_SEPARATOR ,
		 * startBaseTypeName + 1);
		 * 
		 * baseType = Integer.valueOf(paramValue.substring(0,
		 * startBaseTypeName)) .intValue(); baseTypeName =
		 * paramValue.substring(startBaseTypeName + 1, endBaseTypeName); //
		 * create a new string without type information in front. paramValue =
		 * paramValue.substring(endBaseTypeName + 1); // rest of the array
		 * deserialization code is copied from OBJECT_TAG
		 * 
		 * Object obj; try { byte[] decoded =
		 * AbstractBlobFilter.getDefaultBlobFilter().decode( paramValue); obj =
		 * new ObjectInputStream(new ByteArrayInputStream(decoded))
		 * .readObject(); } catch (ClassNotFoundException cnfe) { throw
		 * (SQLException) new SQLException(commonMsg + ", class not found on
		 * controller").initCause(cnfe); } catch (IOException ioe) // like for
		 * instance invalid stream header { throw (SQLException) new
		 * SQLException(commonMsg + ", I/O exception") .initCause(ioe); } //
		 * finally, construct the java.sql.array interface object using //
		 * deserialized object, and type information
		 * org.continuent.sequoia.common.protocol.Array array = new
		 * org.continuent.sequoia.common.protocol.Array( obj, baseType,
		 * baseTypeName); backendPS.setArray(paramIdx, array); } else
		 */
        if (paramType.equals(RequestPlayerUtils.BIG_DECIMAL_TAG)) {
            BigDecimal t = null;
            if (!paramValue.equals(RequestPlayerUtils.NULL_VALUE)) {
                t = new BigDecimal(paramValue);
            }
            backendPS.setBigDecimal(paramIdx, t);
        } else if (paramType.equals(RequestPlayerUtils.BOOLEAN_TAG)) {
            backendPS.setBoolean(paramIdx, Boolean.valueOf(paramValue)
                    .booleanValue());
        } else if (paramType.equals(RequestPlayerUtils.BYTE_TAG)) {
            byte t = new Integer(paramValue).byteValue();
            backendPS.setByte(paramIdx, t);
        }
		/*
		 * else if (paramType.equals(RequestPlayerUtils.BYTES_TAG)) { /**
		 * encoded by the driver at {@link #setBytes(int, byte[])}in order to
		 * inline it in the request (no database encoding here).
		 * 
		 * byte[] t =
		 * AbstractBlobFilter.getDefaultBlobFilter().decode(paramValue);
		 * backendPS.setBytes(paramIdx, t); }
		 */
		/*
		 * else if (paramType.equals(RequestPlayerUtils.BLOB_TAG)) {
		 * ByteArrayBlob b = null; // encoded by the driver at {@link
		 * #setBlob(int, java.sql.Blob)} if
		 * (!paramValue.equals(RequestPlayerUtils.NULL_VALUE)) b = new
		 * ByteArrayBlob(AbstractBlobFilter.getDefaultBlobFilter().decode(
		 * paramValue)); backendPS.setBlob(paramIdx, b); }
		 */
		/*
		 * else if (paramType.equals(RequestPlayerUtils.CLOB_TAG)) { StringClob
		 * c = null; if (!paramValue.equals(RequestPlayerUtils.NULL_VALUE)) c =
		 * new StringClob(paramValue); backendPS.setClob(paramIdx, c); }
		 */
        else if (paramType.equals(RequestPlayerUtils.DATE_TAG)) {
            if (paramValue.equals(RequestPlayerUtils.NULL_VALUE))
                backendPS.setDate(paramIdx, null);
            else
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                    Date t = new Date(sdf.parse(paramValue).getTime());
                    backendPS.setDate(paramIdx, t);
                } catch (ParseException p) {
                    backendPS.setDate(paramIdx, null);
                    throw new SQLException("Couldn't format date!!!");
                }
        } else if (paramType.equals(RequestPlayerUtils.DOUBLE_TAG)) {
            backendPS.setDouble(paramIdx, Double.valueOf(paramValue)
                    .doubleValue());
        } else if (paramType.equals(RequestPlayerUtils.FLOAT_TAG)) {
            backendPS
                    .setFloat(paramIdx, Float.valueOf(paramValue).floatValue());
        } else if (paramType.equals(RequestPlayerUtils.INTEGER_TAG)) {
            backendPS.setInt(paramIdx, Integer.valueOf(paramValue).intValue());
        } else if (paramType.equals(RequestPlayerUtils.LONG_TAG)) {
            backendPS.setLong(paramIdx, Long.valueOf(paramValue).longValue());
        } else if (paramType.equals(RequestPlayerUtils.NULL_VALUE)) {
            backendPS.setNull(paramIdx, Integer.valueOf(paramValue).intValue());
        }
		/*
		 * else if (paramType.equals(RequestPlayerUtils.OBJECT_TAG)) { if
		 * (paramValue.equals(RequestPlayerUtils.NULL_VALUE))
		 * backendPS.setObject(paramIdx, null); else { final String commonMsg =
		 * "Failed to deserialize object parameter of setObject()"; Object obj;
		 * try { byte[] decoded =
		 * AbstractBlobFilter.getDefaultBlobFilter().decode( paramValue); obj =
		 * new ObjectInputStream(new ByteArrayInputStream(decoded))
		 * .readObject(); } catch (ClassNotFoundException cnfe) { throw
		 * (SQLException) new SQLException(commonMsg + ", class not found on
		 * controller").initCause(cnfe); } catch (IOException ioe) // like for
		 * instance invalid stream header { throw (SQLException) new
		 * SQLException(commonMsg + ", I/O exception") .initCause(ioe); }
		 * backendPS.setObject(paramIdx, obj); } }
		 */
        else if (paramType.equals(RequestPlayerUtils.REF_TAG)) {
            if (paramValue.equals(RequestPlayerUtils.NULL_VALUE)) {
                backendPS.setRef(paramIdx, null);
            } else {
                throw new SQLException("Ref type not supported");
            }
        } else if (paramType.equals(RequestPlayerUtils.SHORT_TAG)) {
            short t = new Integer(paramValue).shortValue();
            backendPS.setShort(paramIdx, t);
        } else if (paramType.equals(RequestPlayerUtils.STRING_TAG)) {
            if (paramValue.equals(RequestPlayerUtils.NULL_VALUE)) {
                backendPS.setString(paramIdx, null);
            } else {
                backendPS.setString(paramIdx, paramValue);
            }
        } else if (paramType.equals(RequestPlayerUtils.NULL_STRING_TAG)) {
            backendPS.setString(paramIdx, null);
        } else if (paramType.equals(RequestPlayerUtils.TIME_TAG)) {
            if (paramValue.equals(RequestPlayerUtils.NULL_VALUE)) {
                backendPS.setTime(paramIdx, null);
            } else {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
                    Time t = new Time(sdf.parse(paramValue).getTime());
                    backendPS.setTime(paramIdx, t);
                } catch (ParseException p) {
                    backendPS.setTime(paramIdx, null);
                    throw new SQLException("Couldn't format time!!!");
                }
            }
        } else if (paramType.equals(RequestPlayerUtils.TIMESTAMP_TAG)) {
            if (paramValue.equals(RequestPlayerUtils.NULL_VALUE)) {
                backendPS.setTimestamp(paramIdx, null);
            } else {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat(
                            "yyyy-MM-dd HH:mm:ss.S");
                    Timestamp t = new Timestamp(sdf.parse(paramValue).getTime());
                    backendPS.setTimestamp(paramIdx, t);
                } catch (ParseException p) {
                    backendPS.setTimestamp(paramIdx, null);
                    throw new SQLException("Couldn't format timestamp!!!");
                }
            }
        } else if (paramType.equals(RequestPlayerUtils.URL_TAG)) {
            if (paramValue.equals(RequestPlayerUtils.NULL_VALUE)) {
                backendPS.setURL(paramIdx, null);
            } else {
                try {
                    backendPS.setURL(paramIdx, new URL(paramValue));
                } catch (MalformedURLException e) {
                    throw new SQLException("Unable to create URL " + paramValue
                            + " (" + e + ")");
                }
            }
        } else if (paramType.equals(RequestPlayerUtils.CS_PARAM_TAG)) {
            return true; // ignore, will be treated in the named
            // parameters
        } else {
            return false;
        }

        return true;
    }

    /**
     * Replaces all occurrences of a String within another String. Source:
     * Sequoia's Strings.java
     *
     * @param sourceString source String
     * @param replace      text pattern to replace
     * @param with         replacement text
     * @return the text with any replacements processed, <code>null</code> if
     * null String input
     */
    private static String replace(String sourceString, String replace,
                                  String with) {
        if (sourceString == null || replace == null || with == null
                || "".equals(replace)) {
            return sourceString;
        }

        StringBuffer buf = new StringBuffer(sourceString.length());
        int start = 0, end = 0;
        while ((end = sourceString.indexOf(replace, start)) != -1) {
            buf.append(sourceString.substring(start, end)).append(with);
            start = end + replace.length();
        }
        buf.append(sourceString.substring(start));
        return buf.toString();
    }
}

/********************* All the classes below this line come from TPCW java implementation *********************/

class Book {
    // Construct a book from a ResultSet
    public Book(ResultSet rs) {
        // The result set should have all of the fields we expect.
        // This relies on using field name access. It might be a bad
        // way to break this up since it does not allow us to use the
        // more efficient select by index access method. This also
        // might be a problem since there is no type checking on the
        // result set to make sure it is even a reasonble result set
        // to give to this function.

        try {
            i_id = rs.getInt("i_id");
            i_title = rs.getString("i_title");
            i_pub_Date = rs.getDate("i_pub_Date");
            i_publisher = rs.getString("i_publisher");
            i_subject = rs.getString("i_subject");
            i_desc = rs.getString("i_desc");
            i_related1 = rs.getInt("i_related1");
            i_related2 = rs.getInt("i_related2");
            i_related3 = rs.getInt("i_related3");
            i_related4 = rs.getInt("i_related4");
            i_related5 = rs.getInt("i_related5");
            i_thumbnail = rs.getString("i_thumbnail");
            i_image = rs.getString("i_image");
            i_srp = rs.getDouble("i_srp");
            i_cost = rs.getDouble("i_cost");
            i_avail = rs.getDate("i_avail");
            i_isbn = rs.getString("i_isbn");
            i_page = rs.getInt("i_page");
            i_backing = rs.getString("i_backing");
            i_dimensions = rs.getString("i_dimensions");
            a_id = rs.getInt("a_id");
            a_fname = rs.getString("a_fname");
            a_lname = rs.getString("a_lname");
        } catch (java.lang.Exception ex) {
            ex.printStackTrace();
        }
    }

    // From Item
    public int i_id;
    public String i_title;
    // public int i_a_id; // Redundant
    public Date i_pub_Date;
    public String i_publisher;
    public String i_subject;
    public String i_desc;
    public int i_related1;
    public int i_related2;
    public int i_related3;
    public int i_related4;
    public int i_related5;
    public String i_thumbnail;
    public String i_image;
    public double i_srp;
    public double i_cost;
    public Date i_avail;
    public String i_isbn;
    public int i_page;
    public String i_backing;
    public String i_dimensions;

    // From Author
    public int a_id;
    public String a_fname;
    public String a_lname;
}

class ShortBook {
    // Construct a book from a ResultSet
    public ShortBook(ResultSet rs) {
        // The result set should have all of the fields we expect.
        // This relies on using field name access. It might be a bad
        // way to break this up since it does not allow us to use the
        // more efficient select by index access method. This also
        // might be a problem since there is no type checking on the
        // result set to make sure it is even a reasonble result set
        // to give to this function.

        try {
            i_id = rs.getInt("i_id");
            i_title = rs.getString("i_title");
            a_fname = rs.getString("a_fname");
            a_lname = rs.getString("a_lname");
        } catch (java.lang.Exception ex) {
            ex.printStackTrace();
        }
    }

    // From Item
    public int i_id;
    public String i_title;
    public String a_fname;
    public String a_lname;
}

class CartLine {
    public String scl_title;
    public double scl_cost;
    public double scl_srp;
    public String scl_backing;
    public int scl_qty;
    public int scl_i_id;

    public CartLine(String title, double cost, double srp, String backing,
                    int qty, int id) {
        scl_title = title;
        scl_cost = cost;
        scl_srp = srp;
        scl_backing = backing;
        scl_qty = qty;
        scl_i_id = id;
    }
}

class Cart {

    public double SC_SUB_TOTAL;
    public double SC_TAX;
    public double SC_SHIP_COST;
    public double SC_TOTAL;

    public Vector<CartLine> lines;

    public Cart(ResultSet rs, double C_DISCOUNT) throws java.sql.SQLException {
        int i;
        int total_items;
        lines = new Vector<CartLine>();
        while (rs.next()) {// While there are lines remaining
            CartLine line = new CartLine(rs.getString("i_title"),
                    rs.getDouble("i_cost"), rs.getDouble("i_srp"),
                    rs.getString("i_backing"), rs.getInt("scl_qty"),
                    rs.getInt("scl_i_id"));
            lines.addElement(line);
        }

        SC_SUB_TOTAL = 0;
        total_items = 0;
        for (i = 0; i < lines.size(); i++) {
            CartLine thisline = (CartLine) lines.elementAt(i);
            SC_SUB_TOTAL += thisline.scl_cost * thisline.scl_qty;
            total_items += thisline.scl_qty;
        }

        // Need to multiply the sub_total by the discount.
        SC_SUB_TOTAL = SC_SUB_TOTAL * ((100 - C_DISCOUNT) * .01);
        SC_TAX = SC_SUB_TOTAL * .0825;
        SC_SHIP_COST = 3.00 + (1.00 * total_items);
        SC_TOTAL = SC_SUB_TOTAL + SC_SHIP_COST + SC_TAX;
    }
}

class Customer {

    public int c_id;
    public String c_uname;
    public String c_passwd;
    public String c_fname;
    public String c_lname;
    public String c_phone;
    public String c_email;
    public Date c_since;
    public Date c_last_visit;
    public Date c_login;
    public Date c_expiration;
    public double c_discount;
    public double c_balance;
    public double c_ytd_pmt;
    public Date c_birthdate;
    public String c_data;

    // From the addess table
    public int addr_id;
    public String addr_street1;
    public String addr_street2;
    public String addr_city;
    public String addr_state;
    public String addr_zip;
    public int addr_co_id;

    // From the country table
    public String co_name;

    public Customer() {
    }

    public Customer(ResultSet rs) {
        // The result set should have all of the fields we expect.
        // This relies on using field name access. It might be a bad
        // way to break this up since it does not allow us to use the
        // more efficient select by index access method. This also
        // might be a problem since there is no type checking on the
        // result set to make sure it is even a reasonble result set
        // to give to this function.

        try {
            c_id = rs.getInt("c_id");
            c_uname = rs.getString("c_uname");
            c_passwd = rs.getString("c_passwd");
            c_fname = rs.getString("c_fname");
            c_lname = rs.getString("c_lname");

            c_phone = rs.getString("c_phone");
            c_email = rs.getString("c_email");
            c_since = rs.getDate("c_since");
            c_last_visit = rs.getDate("c_last_login");
            c_login = rs.getDate("c_login");
            c_expiration = rs.getDate("c_expiration");
            c_discount = rs.getDouble("c_discount");
            c_balance = rs.getDouble("c_balance");
            c_ytd_pmt = rs.getDouble("c_ytd_pmt");
            c_birthdate = rs.getDate("c_birthdate");
            c_data = rs.getString("c_data");

            addr_id = rs.getInt("addr_id");
            addr_street1 = rs.getString("addr_street1");
            addr_street2 = rs.getString("addr_street2");
            addr_city = rs.getString("addr_city");
            addr_state = rs.getString("addr_state");
            addr_zip = rs.getString("addr_zip");
            addr_co_id = rs.getInt("addr_co_id");

            co_name = rs.getString("co_name");

        } catch (java.lang.Exception ex) {
            ex.printStackTrace();
        }
    }

}

class BuyConfirmResult {
    public Cart cart;
    public int order_id;
}

class Address {
}

class Order {
    public Order(ResultSet rs) {
        try {
            o_id = rs.getInt("o_id");
            c_fname = rs.getString("c_fname");
            c_lname = rs.getString("c_lname");
            c_passwd = rs.getString("c_passwd");
            c_uname = rs.getString("c_uname");
            c_phone = rs.getString("c_phone");
            c_email = rs.getString("c_email");
            o_date = rs.getDate("o_date");
            o_subtotal = rs.getDouble("o_sub_total");
            o_tax = rs.getDouble("o_tax");
            o_total = rs.getDouble("o_total");
            o_ship_type = rs.getString("o_ship_type");
            o_ship_date = rs.getDate("o_ship_date");
            o_status = rs.getString("o_status");
            cx_type = rs.getString("cx_type");

            bill_addr_street1 = rs.getString("bill_addr_street1");
            bill_addr_street2 = rs.getString("bill_addr_street2");
            bill_addr_state = rs.getString("bill_addr_state");
            bill_addr_zip = rs.getString("bill_addr_zip");
            bill_co_name = rs.getString("bill_co_name");

            ship_addr_street1 = rs.getString("ship_addr_street1");
            ship_addr_street2 = rs.getString("ship_addr_street2");
            ship_addr_state = rs.getString("ship_addr_state");
            ship_addr_zip = rs.getString("ship_addr_zip");
            ship_co_name = rs.getString("ship_co_name");
        } catch (java.lang.Exception ex) {
            ex.printStackTrace();
        }
    }

    public int o_id;
    public String c_fname;
    public String c_lname;
    public String c_passwd;
    public String c_uname;
    public String c_phone;
    public String c_email;
    public Date o_date;
    public double o_subtotal;
    public double o_tax;
    public double o_total;
    public String o_ship_type;
    public Date o_ship_date;
    public String o_status;

    // Billing address
    public String bill_addr_street1;
    public String bill_addr_street2;
    public String bill_addr_state;
    public String bill_addr_zip;
    public String bill_co_name;

    // Shipping address
    public String ship_addr_street1;
    public String ship_addr_street2;
    public String ship_addr_state;
    public String ship_addr_zip;
    public String ship_co_name;

    public String cx_type;
}

class OrderLine {
    public OrderLine(ResultSet rs) {
        try {
            ol_i_id = rs.getInt("ol_i_id");
            i_title = rs.getString("i_title");
            i_publisher = rs.getString("i_publisher");
            i_cost = rs.getDouble("i_cost");
            ol_qty = rs.getInt("ol_qty");
            ol_discount = rs.getDouble("ol_discount");
            ol_comments = rs.getString("ol_comments");
        } catch (java.lang.Exception ex) {
            ex.printStackTrace();
        }
    }

    public int ol_i_id;
    public String i_title;
    public String i_publisher;
    public double i_cost;
    public int ol_qty;
    public double ol_discount;
    public String ol_comments;
}

class TPCW_Util {

    public static final String server_name = "http://sp14.cs.rice.edu:8080/";
    public static final String servlet_prefix = server_name + "servlet/";
    public static final String doc_prefix = server_name + "/tpcw/";
    public static final String image_prefix = server_name + "TPCW/";

    //---------------------------------------------------------------------

    //This must be equal to the number of items in the ITEM table
    public static final int NUM_EBS = 100;
    public static final int NUM_ITEMS = 10000;

    //---------------------------------------------------------------------

    //public final String SESSION_ID="JIGSAW_SESSION_ID";
    //public static final String SESSION_ID="JServSessionIdroot";
    //public static final String SESSION_ID=";$sessionid$";
    public static final String SESSION_ID = ";jsessionid="; // For Tomcat to encode session information in the url


    //---------------------------------------------------------------------

    public static int getRandomI_ID() {
        Random rand = new Random();
        Double temp = new Double(Math.floor(rand.nextFloat() * NUM_ITEMS));
        return temp.intValue();
    }

    public static int getRandom(int i) {  // Returns integer 1, 2, 3 ... i
        return ((int) (java.lang.Math.random() * i) + 1);
    }

    //Not very random function. If called in swift sucession, it will
    //return the same string because the system time used to seed the
    //random number generator won't change. 
    public static String getRandomString(int min, int max) {
        String newstring = new String();
        Random rand = new Random(5353);
        int i;
        final char[] chars = {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k',
                'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v',
                'w', 'x', 'y', 'z', 'A', 'B', 'C', 'D', 'E', 'F', 'G',
                'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R',
                'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', '!', '@', '#',
                '$', '%', '^', '&', '*', '(', ')', '_', '-', '=', '+',
                '{', '}', '[', ']', '|', ':', ';', ',', '.', '?', '/',
                '~', ' '}; //79 characters
        int strlen = (int) Math.floor(rand.nextDouble() * (max - min + 1));
        strlen += min;
        for (i = 0; i < strlen; i++) {
            char c = chars[(int) Math.floor(rand.nextDouble() * 79)];
            newstring = newstring.concat(String.valueOf(c));
        }
        return newstring;
    }

    // Defined in TPC-W Spec Clause 4.6.2.8
    private static final String[] digS = {
            "BA", "OG", "AL", "RI", "RE", "SE", "AT", "UL", "IN", "NG"
    };


    public static String DigSyl(int d, int n) {
        String s = "";

        if (n == 0) return (DigSyl(d));
        for (; n > 0; n--) {
            int c = d % 10;
            s = digS[c] + s;
            d = d / 10;
        }

        return (s);
    }

    public static String DigSyl(int d) {
        String s = "";

        for (; d != 0; d = d / 10) {
            int c = d % 10;
            s = digS[c] + s;
        }

        return (s);
    }
}
