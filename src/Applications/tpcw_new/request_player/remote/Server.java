package Applications.tpcw_new.request_player.remote;

import Applications.tpcw_new.request_player.*;
import BFT.Parameters;
import BFT.exec.*;
import merkle.MerkleTreeInstance;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.ObjectInputStream;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/* Request player: replay a request trace coming from log4j logger in TPCW_Database (TPCW benchmark implemented by the Univ. Wisconsin)*/
public class Server implements RequestHandler, RequestFilter {

    private static ConnectionStatement[] connections;
    private static HashMap<Integer, ArrayList<RequestKey>> keys = new HashMap<Integer, ArrayList<RequestKey>>();
    DataBase dataBase;

    /* to access to the statements */
    private DBStatements dbStatements = null;

    private ReplyHandler replyHandler;
    private int id;
    private Parameters parameters = null;

    /*
     * main. Creates a new RequestPlayerServer Usage: RequestPlayerServer
     * server_port pool_size
     */
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage Server <membership> <id>");
            return;
        }


        RequestPlayerUtils.initProperties(null);
        ExecBaseNode exec = new ExecBaseNode(args[0], Integer.parseInt(args[1]));
        Server main = new Server(exec, Integer.parseInt(args[1]));
        main.createSession();
        exec.start(main, main);
        /*if (args.length >= 1)
		{
			RequestPlayerUtils.initProperties(args[0]);
		}
		else
		{
			RequestPlayerUtils.initProperties(null);
		}*/

    }

    private static void initRules(DBStatements d) {
        ArrayList<RequestKey> key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "customer"));
        keys.put(d.code_getName(), key);
        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "item"));
        key.add(new RequestKey(true, "author"));
        keys.put(d.code_getBook(), key);
        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "item"));
        key.add(new RequestKey(true, "address"));
        key.add(new RequestKey(true, "country"));
        keys.put(d.code_getCustomer(), key);
        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "item"));
        key.add(new RequestKey(true, "author"));
        keys.put(d.code_doSubjectSearch(), key);
        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "item"));
        key.add(new RequestKey(true, "author"));
        keys.put(d.code_doTitleSearch(), key);
        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "item"));
        key.add(new RequestKey(true, "author"));
        keys.put(d.code_doAuthorSearch(), key);
        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "item"));
        key.add(new RequestKey(true, "author"));
        keys.put(d.code_getNewProducts(), key);
        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "item"));
        key.add(new RequestKey(true, "author"));
        key.add(new RequestKey(true, "order_line"));
        keys.put(d.code_getBestSellers(), key);
        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "item"));
        keys.put(d.code_getRelated(), key);
        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(false, "item"));
        keys.put(d.code_adminUpdate1(), key);
        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "customer"));
        keys.put(d.code_GetUserName(), key);

        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "customer"));
        keys.put(d.code_GetPassword(), key);
        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "customer"));
        key.add(new RequestKey(true, "cc_xacts"));
        key.add(new RequestKey(true, "address"));
        key.add(new RequestKey(true, "country"));
        key.add(new RequestKey(true, "order_line"));
        keys.put(d.code_GetMostRecentOrder1(), key);
        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(false, "customer"));
        keys.put(d.code_refreshSession(), key);
        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(false, "customer"));
        key.add(new RequestKey(false, "address"));
        key.add(new RequestKey(true, "country"));
        key.add(new RequestKey(true, "address"));
        keys.put(d.code_enterAddress1(), key);
        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "item"));
        keys.put(d.code_getStock(), key);
        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "item"));
        key.add(new RequestKey(true, "customer"));
        key.add(new RequestKey(true, "address"));
        keys.put(d.code_verifyDBConsistency1(), key);
        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(false, "shopping_cart"));
        keys.put(d.code_createEmptyCart1(), key);

        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(false, "shopping_cart_line"));
        key.add(new RequestKey(false, "shopping_cart"));
        key.add(new RequestKey(true, "item"));
        //Need fix here doCart
        keys.put(d.code_addRandomItemToCartIfNecessary(), key);

        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(false, "shopping_cart_line"));
        key.add(new RequestKey(false, "shopping_cart"));
        keys.put(d.code_addItem1(), key);

        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "shopping_cart_line"));
        key.add(new RequestKey(true, "item"));
        keys.put(d.code_getCart(), key);

        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "customer"));
        key.add(new RequestKey(true, "address"));
        key.add(new RequestKey(false, "order_line"));
        key.add(new RequestKey(false, "item"));
        key.add(new RequestKey(false, "cc_xacts"));
        key.add(new RequestKey(false, "shopping_cart_line"));
        keys.put(d.code_getCDiscount(), key);
        //Fix me. DoBuyConfirm1

        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "customer"));
        key.add(new RequestKey(true, "address"));
        key.add(new RequestKey(false, "order_line"));
        key.add(new RequestKey(false, "item"));
        key.add(new RequestKey(false, "cc_xacts"));
        key.add(new RequestKey(false, "shopping_cart_line"));
        //Fix me. DoBuyConfirm2
        //
        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(false, "shopping_cart_line"));
        keys.put(d.code_clearCart(), key);
    }

    public Server(ReplyHandler replyHandler, int id) {
        this.replyHandler = replyHandler;
        this.id = id;
        // initialize the statements
        dbStatements = (DBStatements) RequestPlayerUtils
                .loadClass(RequestPlayerUtils.DBStatements);

        // create DB
        dataBase = (DataBase) RequestPlayerUtils
                .loadClass(RequestPlayerUtils.DBClass);
        dataBase.initDB();
        //MerkleTreeInstance.get().finishThisVersion();
        //merkle.Tools.printHash(MerkleTreeInstance.get().getHash());
        // initialize the statements
        dbStatements = (DBStatements) RequestPlayerUtils
                .loadClass(RequestPlayerUtils.DBStatements);
        initRules(dbStatements);
    }

    public void createSession() {
        connections = new ConnectionStatement[this.parameters.getNumberOfClients()];
        for (int i = 0; i < connections.length; i++) {
            ConnectionStatement cs = new ConnectionStatement(dbStatements);
            connections[i] = cs;
        }
        MerkleTreeInstance.get().getHash();
        //MerkleTreeInstance.get().printTree();
    }

    @Override
    public void execReadOnly(byte[] request, RequestInfo info) {
        throw new RuntimeException("Not Implemented");
    }

    public void execRequest(byte[] request, RequestInfo info) {
        try {
            long start = System.currentTimeMillis();
            ByteArrayInputStream bis = new ByteArrayInputStream(request);
            ObjectInputStream ois = new ObjectInputStream(bis);
            TransactionMessage tm = (TransactionMessage) ois.readObject();
            ConnectionStatement cs = connections[info.getClientId()];
            Connection con = cs.getConnection();

            int ret = 0;
            int retry = 0;
            boolean executed = false;
            while (retry < 1 && !executed) {
                try {
                    for (int i = 0; i < tm.getNbOfRequests(); i++) {
                        int stmt_code = tm.getStatementCode(i);
                        PreparedStatement ps = cs.getPreparedStatement(stmt_code);
                        //execute(cs.getPreparedStatement(statementCode), tm
                        //	.getArguments(i), tm.isRead());
                        if (RequestPlayerUtils.requests_processing) {
                            StatementExecutor.executeNProcess(stmt_code, ps,
                                    tm.getArguments(i), dbStatements);
                        } else {
                            StatementExecutor.execute(ps, tm.getArguments(i),
                                    dbStatements.statementIsRead(stmt_code));
                        }
                    }

                    ret = 0;
                    con.commit();
                    executed = true;
                } catch (java.lang.Exception ex) {
                    try {
                        ret = 1;
                        con.rollback();
                        System.out.println("rollback and retry");
                        retry++;
                        ex.printStackTrace();
                    } catch (Exception se) {
                        ret = 2;
                        retry++;
                        se.printStackTrace();
                    }
                }
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream oos = new DataOutputStream(bos);
            oos.writeInt(ret);
            oos.flush();
            replyHandler.result(bos.toByteArray(), info);
            //System.out.println("transaction "+tm.getStatementCode(0)+" time="+(System.currentTimeMillis()-start));
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    public List<RequestKey> generateKeys(byte[] request) {
        //if(true) return null;
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(request);
            ObjectInputStream ois = new ObjectInputStream(bis);
            TransactionMessage tm = (TransactionMessage) ois.readObject();

            int id = tm.getStatementCode(0);
            List<RequestKey> ret = keys.get(id);
            if (ret == null)
                System.out.println("Unknown request " + id);
			/*for (int i = 0; i < tm.getNbOfRequests(); i++)
                        {
                         	System.out.println(tm.getStatementCode(i));
			}*/
            return ret;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    private void execute(PreparedStatement ps, String arguments,
                         boolean isRead) throws SQLException {
        // set args
        setArguments(ps, arguments);
        ps.execute();
    }

    /**
     * Set the arguments in arguments in the prepared statement ps Source:
     * Sequoia's PreparedStatementSerialization.setPreparedStatement()
     * method
     */
    private void setArguments(PreparedStatement backendPS,
                              String parameters) throws SQLException {
        int i = 0;
        int paramIdx = 0;
        // Set all parameters
        while ((i = parameters.indexOf(RequestPlayerUtils.START_PARAM_TAG,
                i)) > -1) {
            paramIdx++;
            int typeStart = i + RequestPlayerUtils.START_PARAM_TAG.length();
            // Here we assume that all tags have the same length as the
            // boolean tag.
            String paramType = parameters.substring(typeStart, typeStart
                    + RequestPlayerUtils.BOOLEAN_TAG.length());
            String paramValue = parameters.substring(typeStart
                    + RequestPlayerUtils.BOOLEAN_TAG.length(), parameters
                    .indexOf(RequestPlayerUtils.END_PARAM_TAG, i));
            paramValue = replace(paramValue,
                    RequestPlayerUtils.TAG_MARKER_ESCAPE,
                    RequestPlayerUtils.TAG_MARKER);
            if (!performCallOnPreparedStatement(backendPS, paramIdx,
                    paramType, paramValue)) {
                // invalid parameter, we want to be able to store strings
                // like
                // <?xml version="1.0" encoding="ISO-8859-1"?>
                paramIdx--;
            }
            i = typeStart + paramValue.length();
        }
    }

    private boolean performCallOnPreparedStatement(
            java.sql.PreparedStatement backendPS, int paramIdx,
            String paramType, String paramValue) throws SQLException {
        // Test tags in alphabetical order (to make the code easier to read)
		/*
		 * if (paramType.equals(RequestPlayerUtils.ARRAY_TAG)) { // in case
		 * of Array, the String contains: // - integer: sql type // -
		 * string: sql type name, delimited by ARRAY_BASETYPE_NAME_SEPARATOR // -
		 * serialized object array final String commonMsg = "Failed to
		 * deserialize Array parameter of setObject()"; int baseType; String
		 * baseTypeName; // Get start and end of the type name string int
		 * startBaseTypeName =
		 * paramValue.indexOf(RequestPlayerUtils.ARRAY_BASETYPE_NAME_SEPARATOR );
		 * int endBaseTypeName =
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
		 * AbstractBlobFilter.getDefaultBlobFilter().decode( paramValue);
		 * obj = new ObjectInputStream(new ByteArrayInputStream(decoded))
		 * .readObject(); } catch (ClassNotFoundException cnfe) { throw
		 * (SQLException) new SQLException(commonMsg + ", class not found on
		 * controller").initCause(cnfe); } catch (IOException ioe) // like
		 * for instance invalid stream header { throw (SQLException) new
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
		 * encoded by the driver at {@link #setBytes(int, byte[])}in order
			 * to inline it in the request (no database encoding here).
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
		 * else if (paramType.equals(RequestPlayerUtils.CLOB_TAG)) {
		 * StringClob c = null; if
		 * (!paramValue.equals(RequestPlayerUtils.NULL_VALUE)) c = new
		 * StringClob(paramValue); backendPS.setClob(paramIdx, c); }
		 */
        else if (paramType.equals(RequestPlayerUtils.DATE_TAG)) {
            if (paramValue.equals(RequestPlayerUtils.NULL_VALUE)) {
                backendPS.setDate(paramIdx, null);
            } else {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat(
                            "yyyy-MM-dd");
                    Date t = new Date(sdf.parse(paramValue).getTime());
                    backendPS.setDate(paramIdx, t);
                } catch (ParseException p) {
                    backendPS.setDate(paramIdx, null);
                    throw new SQLException("Couldn't format date!!!");
                }
            }
        } else if (paramType.equals(RequestPlayerUtils.DOUBLE_TAG)) {
            backendPS.setDouble(paramIdx, Double.valueOf(paramValue)
                    .doubleValue());
        } else if (paramType.equals(RequestPlayerUtils.FLOAT_TAG)) {
            backendPS.setFloat(paramIdx, Float.valueOf(paramValue)
                    .floatValue());
        } else if (paramType.equals(RequestPlayerUtils.INTEGER_TAG)) {
            backendPS.setInt(paramIdx, Integer.valueOf(paramValue)
                    .intValue());
        } else if (paramType.equals(RequestPlayerUtils.LONG_TAG)) {
            backendPS.setLong(paramIdx, Long.valueOf(paramValue)
                    .longValue());
        } else if (paramType.equals(RequestPlayerUtils.NULL_VALUE)) {
            backendPS.setNull(paramIdx, Integer.valueOf(paramValue)
                    .intValue());
        }
		/*
		 * else if (paramType.equals(RequestPlayerUtils.OBJECT_TAG)) { if
		 * (paramValue.equals(RequestPlayerUtils.NULL_VALUE))
		 * backendPS.setObject(paramIdx, null); else { final String
		 * commonMsg = "Failed to deserialize object parameter of
		 * setObject()"; Object obj; try { byte[] decoded =
		 * AbstractBlobFilter.getDefaultBlobFilter().decode( paramValue);
		 * obj = new ObjectInputStream(new ByteArrayInputStream(decoded))
		 * .readObject(); } catch (ClassNotFoundException cnfe) { throw
		 * (SQLException) new SQLException(commonMsg + ", class not found on
		 * controller").initCause(cnfe); } catch (IOException ioe) // like
		 * for instance invalid stream header { throw (SQLException) new
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
                    Timestamp t = new Timestamp(sdf.parse(paramValue)
                            .getTime());
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
                    throw new SQLException("Unable to create URL "
                            + paramValue + " (" + e + ")");
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
     * @return the text with any replacements processed, <code>null</code>
     * if null String input
     */
    private String replace(String sourceString, String replace, String with) {
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
