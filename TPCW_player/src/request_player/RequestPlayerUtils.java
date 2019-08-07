package request_player;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.util.Properties;

/* Some useful stuff for the request player */
public class RequestPlayerUtils
{

	private static final String properties_file = "requestPlayer.properties";

	// ATTENTION: The NUM_EBS and NUM_itemS variables are the only variables
	// that should be modified in order to rescale the DB.
	public static int NUM_EBS;
	public static int NUM_ITEMS;

	public static String requestsFile;

	public static int requestPatternVersion;
	public static final int reqPattern0 = 0;
	public static final int reqPattern1 = 1;
	public static final int reqPattern2 = 2;

	public static int nbClients;
	public static String serverAddr;
	public static int serverPort;
	public static int poolSize;

	public static boolean requests_processing;
	public static String driver;
	public static String protocol;
	public static String dbName;
	public static String dbCreateOpts;
	public static int isolationLevel;
	public static String DBClass;
	public static String DBStatements;
	public static String propsUser;
	public static String propsPasswd;
	public static int monitorDelay;

	public static int degreeOfParallelism;
	public static final int allParallel = 0;
	public static final int seqWritesParallelReads = 1;
	public static final int seqWritesParallelReadsNoWait = 2;
	public static final int parallelismRules = 3;

	// true iff there is a thread executing a write transaction
	public static boolean currentWrite = false;

	/* enumeration of method names in tpcw */
	public enum methodName
	{
		/* READ */
		getName, getBook, getCustomer, doSubjectSearch, doTitleSearch, doAuthorSearch, getNewProducts, getBestSellers, getRelated, GetUserName, GetPassword, GetMostRecentOrder, verifyDBConsistency,
		/* WRITE */
		adminUpdate, refreshSession, createNewCustomer, doBuyConfirm, createEmptyCart, doCart, getCart,
		/* UNKNOWN */
		unknown
	}

	/**
	 * Tags used to serialize the name of the setter used by the JDBC client
	 * application; one tag for each setter method. All tags must have the same
	 * length {@link #setPreparedStatement(String, java.sql.PreparedStatement)}
	 * from: Sequoia
	 */
	/** @see java.sql.PreparedStatement#setArray(int, Array) */
	public static final String ARRAY_TAG = "A|";
	/** @see java.sql.PreparedStatement#setByte(int, byte) */
	public static final String BYTE_TAG = "b|";
	/** @see java.sql.PreparedStatement#setBytes(int, byte[]) */
	public static final String BYTES_TAG = "B|";
	/** @see java.sql.PreparedStatement#setBlob(int, java.sql.Blob) */
	public static final String BLOB_TAG = "c|";
	/** @see java.sql.PreparedStatement#setClob(int, java.sql.Clob) */
	public static final String CLOB_TAG = "C|";
	/** @see java.sql.PreparedStatement#setBoolean(int, boolean) */
	public static final String BOOLEAN_TAG = "0|";
	/**
	 * @see java.sql.PreparedStatement#setBigDecimal(int, java.math.BigDecimal)
	 */
	public static final String BIG_DECIMAL_TAG = "1|";
	/** @see java.sql.PreparedStatement#setDate(int, java.sql.Date) */
	public static final String DATE_TAG = "d|";
	/** @see java.sql.PreparedStatement#setDouble(int, double) */
	public static final String DOUBLE_TAG = "D|";
	/** @see java.sql.PreparedStatement#setFloat(int, float) */
	public static final String FLOAT_TAG = "F|";
	/** @see java.sql.PreparedStatement#setInt(int, int) */
	public static final String INTEGER_TAG = "I|";
	/** @see java.sql.PreparedStatement#setLong(int, long) */
	public static final String LONG_TAG = "L|";

	/** Encoding of a named parameter in a CallableStatement */
	public static final String NAMED_PARAMETER_TAG = "P|";

	/** Encoding of a NULL value. Also used to as the _TAG for setNull() */
	public static final String NULL_VALUE = "N|";
	/**
	 * Special escape _type_ tag used when the string parameter is unfortunately
	 * equal to an encoded null value.
	 */
	public static final String NULL_STRING_TAG = "n|";

	/**
	 * @see java.sql.PreparedStatement#setObject(int, java.lang.Object)
	 */
	public static final String OBJECT_TAG = "O|";
	/** @see java.sql.PreparedStatement#setRef(int, java.sql.Ref) */
	public static final String REF_TAG = "R|";
	/** @see java.sql.PreparedStatement#setShort(int, short) */
	public static final String SHORT_TAG = "s|";
	/**
	 * @see java.sql.PreparedStatement#setString(int, java.lang.String)
	 */
	public static final String STRING_TAG = "S|";
	/** @see java.sql.PreparedStatement#setTime(int, java.sql.Time) */
	public static final String TIME_TAG = "t|";
	/**
	 * @see java.sql.PreparedStatement#setTimestamp(int, java.sql.Timestamp)
	 */
	public static final String TIMESTAMP_TAG = "T|";
	/** @see java.sql.PreparedStatement#setURL(int, java.net.URL) */
	public static final String URL_TAG = "U|";

	/** Escape for callable statement out parameter */
	public static final String REGISTER_OUT_PARAMETER = "o|";
	/** Escape for callable statement out parameter with scale */
	public static final String REGISTER_OUT_PARAMETER_WITH_SCALE = "w|";
	/** Escape for callable statement out parameter with type name */
	public static final String REGISTER_OUT_PARAMETER_WITH_NAME = "W|";
	/**
	 * Escape for 'void' placeholder when compiling callable statement OUT or
	 * named parameter
	 */
	public static final String CS_PARAM_TAG = "V|";

	/** Tag maker for parameters */
	public static final String TAG_MARKER = "!%";
	/** Escape for tag maker */
	public static final String TAG_MARKER_ESCAPE = TAG_MARKER + ";";

	/** Tag for parameters start delimiter */
	public static final String START_PARAM_TAG = "<" + TAG_MARKER;

	/** Tag for parameters end delimiter */
	public static final String END_PARAM_TAG = "|" + TAG_MARKER + ">";
	/**
	 * Tag for start and end delimiter for serializing array base type name
	 * string
	 */
	public static final String ARRAY_BASETYPE_NAME_SEPARATOR = "\"";

	/** To delimite sent transactions * */
	public static final String END_TRANSACTION_TAG = START_PARAM_TAG
			+ END_PARAM_TAG;

	/**
	 * Return the String str for output by the logger. Indeed str can be null,
	 * thus we check that before returning the right tag.
	 */
	public static String getString4Logger(String str)
	{
		if (str == null)
			return START_PARAM_TAG + NULL_STRING_TAG + NULL_VALUE
					+ END_PARAM_TAG;
		else
			return START_PARAM_TAG + STRING_TAG + str + END_PARAM_TAG;
	}

	/** Return the int i for output by the logger */
	public static String getInt4Logger(int i)
	{
		return START_PARAM_TAG + INTEGER_TAG + i + END_PARAM_TAG;
	}

	/** Return the double d for output by the logger */
	public static String getDouble4Logger(double d)
	{
		return START_PARAM_TAG + DOUBLE_TAG + d + END_PARAM_TAG;
	}

	/** Return the long l for output by the logger */
	public static String getLong4Logger(long l)
	{
		return START_PARAM_TAG + LONG_TAG + l + END_PARAM_TAG;
	}

	/** Return the java.sql.Date D for output by the logger */
	public static String getDate4Logger(java.sql.Date D)
	{
		if (D == null)
			return START_PARAM_TAG + DATE_TAG + NULL_VALUE + END_PARAM_TAG;
		else
			return START_PARAM_TAG + DATE_TAG + D + END_PARAM_TAG;
	}

	public static void initProperties(String f)
	{
		Properties P = new Properties();

		try
		{
			FileInputStream F;

			if (f != null && !f.equals(""))
				F = new FileInputStream(f);
			else
				F = new FileInputStream(properties_file);

			P.load(F);
		}
		catch (FileNotFoundException e)
		{
			e.printStackTrace();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

		requestsFile = P.getProperty("requests_file");
		requestPatternVersion = Integer.parseInt(P
				.getProperty("request_pattern_version"));
		if (requestPatternVersion != reqPattern0
				&& requestPatternVersion != reqPattern1
				&& requestPatternVersion != reqPattern2)
		{
			System.out.println("Wrong request_pattern_version: "
					+ requestPatternVersion);
			System.exit(-1);
		}
		nbClients = Integer.parseInt(P.getProperty("nbClients"));
		serverAddr = P.getProperty("server_addr");
		serverPort = Integer.parseInt(P.getProperty("server_port"));
		poolSize = Integer.parseInt(P.getProperty("poolSize"));
		degreeOfParallelism = Integer.parseInt(P
				.getProperty("degreeOfParallelism"));
		if (degreeOfParallelism != allParallel
				&& degreeOfParallelism != seqWritesParallelReads
				&& degreeOfParallelism != seqWritesParallelReadsNoWait
				&& degreeOfParallelism != parallelismRules)
		{
			System.err.println("Error with degreeOfParallelism value: "
					+ degreeOfParallelism + " is not a valid value");
			System.exit(-1);
		}
		monitorDelay = Integer.parseInt(P.getProperty("monitorDelay"));

		try
		{
			isolationLevel = (Integer) Connection.class.getField(
					P.getProperty("isolationLevel", "READ_UNCOMMITTED")).get(
					Connection.class);
		}
		catch (IllegalArgumentException e)
		{
			e.printStackTrace();
		}
		catch (SecurityException e)
		{
			e.printStackTrace();
		}
		catch (IllegalAccessException e)
		{
			e.printStackTrace();
		}
		catch (NoSuchFieldException e)
		{
			e.printStackTrace();
		}

		NUM_EBS = Integer.parseInt(P.getProperty("NUM_EBS", "100"));
		NUM_ITEMS = Integer.parseInt(P.getProperty("NUM_ITEMS", "10000"));

		requests_processing = Boolean.parseBoolean(P.getProperty("requests_processing"));
		DBClass = P.getProperty("DBClass");
		DBStatements = P.getProperty("statementsClass");
		driver = P.getProperty("driver");
		protocol = P.getProperty("protocol");
		dbName = P.getProperty("dbName");
		dbCreateOpts = P.getProperty("dbCreateOpts");
		propsUser = P.getProperty("props_user");
		propsPasswd = P.getProperty("props_passwd");
	}

	/* load the appropriate class */
	public static Object loadClass(String className)
	{
		try
		{
			return (Object) (Class.forName(className).newInstance());
			// System.out.println("Loaded the class " + className);
		}
		catch (ClassNotFoundException cnfe)
		{
			System.err.println("\nUnable to load the class " + className);
			System.err.println("Please check your CLASSPATH.");
			cnfe.printStackTrace(System.err);
		}
		catch (InstantiationException ie)
		{
			System.err
					.println("\nUnable to instantiate the class " + className);
			ie.printStackTrace(System.err);
		}
		catch (IllegalAccessException iae)
		{
			System.err
					.println("\nNot allowed to access the class " + className);
			iae.printStackTrace(System.err);
		}
		return null;
	}

	public static methodName string2methodName(String s)
	{
		methodName m = methodName.unknown;

		if (s.equals("getName"))
		{
			m = methodName.getName;
		}
		else if (s.equals("getBook"))
		{
			m = methodName.getBook;
		}
		else if (s.equals("getCustomer"))
		{
			m = methodName.getCustomer;
		}
		else if (s.equals("doSubjectSearch"))
		{
			m = methodName.doSubjectSearch;
		}
		else if (s.equals("doTitleSearch"))
		{
			m = methodName.doTitleSearch;
		}
		else if (s.equals("doAuthorSearch"))
		{
			m = methodName.doAuthorSearch;
		}
		else if (s.equals("getNewProducts"))
		{
			m = methodName.getNewProducts;
		}
		else if (s.equals("getBestSellers"))
		{
			m = methodName.getBestSellers;
		}
		else if (s.equals("getRelated"))
		{
			m = methodName.getRelated;
		}
		else if (s.equals("adminUpdate"))
		{
			m = methodName.adminUpdate;
		}
		else if (s.equals("GetUserName"))
		{
			m = methodName.GetUserName;
		}
		else if (s.equals("GetPassword"))
		{
			m = methodName.GetPassword;
		}
		else if (s.equals("GetMostRecentOrder"))
		{
			m = methodName.GetMostRecentOrder;
		}
		else if (s.equals("refreshSession"))
		{
			m = methodName.refreshSession;
		}
		else if (s.equals("createNewCustomer"))
		{
			m = methodName.createNewCustomer;
		}
		else if (s.equals("doBuyConfirm"))
		{
			m = methodName.doBuyConfirm;
		}
		else if (s.equals("createEmptyCart"))
		{
			m = methodName.createEmptyCart;
		}
		else if (s.equals("doCart"))
		{
			m = methodName.doCart;
		}
		else if (s.equals("getCart"))
		{
			m = methodName.getCart;
		}
		else if (s.equals("verifyDBConsistency"))
		{
			m = methodName.verifyDBConsistency;
		}

		return m;
	}

	public static boolean methodIsRead(methodName method)
	{
		if (method == methodName.getName || method == methodName.getBook
				|| method == methodName.getCustomer
				|| method == methodName.doSubjectSearch
				|| method == methodName.doTitleSearch
				|| method == methodName.doAuthorSearch
				|| method == methodName.getNewProducts
				|| method == methodName.getBestSellers
				|| method == methodName.getRelated
				|| method == methodName.GetUserName
				|| method == methodName.GetPassword
				|| method == methodName.GetMostRecentOrder
				|| method == methodName.verifyDBConsistency)
			return true;
		else
			return false;
	}
}
