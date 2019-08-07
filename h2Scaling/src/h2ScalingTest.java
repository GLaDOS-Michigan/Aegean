import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.util.Random;

public class h2ScalingTest
{
	private static Random rand;
	private static int nb_tables;
	private static int noOfRows;
	private static String DBdriver = "org.h2.Driver";
	private static String dbURL = "jdbc:h2:mem:testServer;LOCK_TIMEOUT=10000;QUERY_TIMEOUT=10000";
	private static String dbCreateOpts = ";DB_CLOSE_DELAY=-1;LOCK_MODE=3;MULTI_THREADED=1";
	private static Properties props;
	private static String propsUser = "sa";
	private static String propsPasswd = "sa";

	private static final boolean COUNTSTARWHERE = false;
	private static final boolean COUNTSTAR = false;
	private static final boolean PRIMARY = false;

	public static void main(String[] args)
	{
		if (args.length != 5)
		{
			System.out
					.println("Args: <seed> <nb_threads> <nb_tables> <noOfRows> <duration (sec)>");
			System.exit(-1);
		}

		long seed = Long.parseLong(args[0]);
		int nbThreads = Integer.parseInt(args[1]);
		nb_tables = Integer.parseInt(args[2]);
		noOfRows = Integer.parseInt(args[3]);
		int duration = Integer.parseInt(args[4]);

		System.out.println("seed=" + seed + ", nb_threads=" + nbThreads
				+ ", nb_tables=" + nb_tables + ", noOfRows=" + noOfRows
				+ ", duration=" + duration + "sec");

		// create DB & tables
		createDB();
		createTables(nb_tables);

		// create random number generator
		rand = new Random(seed);

		// create threads
		AccessThread[] threads = new AccessThread[nbThreads];
		for (int i = 0; i < nbThreads; i++)
		{
			threads[i] = new AccessThread(i, duration);
		}
		MonitorThread M = new MonitorThread(0, System.currentTimeMillis());

		// launch them
		for (int i = 0; i < nbThreads; i++)
		{
			threads[i].start();
		}
		M.start();

		// wait for the end
		for (int i = 0; i < nbThreads; i++)
		{
			try
			{
				threads[i].join();
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
			}
		}

		M.flush();

		System.out.println("This is the end...");
		System.exit(0);
	}

	/* load the appropriate class */
	private static Object loadClass(String className)
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

	private static void createDB()
	{
		loadClass(DBdriver);

		props = new Properties();
		props.put("user", propsUser);
		props.put("password", propsPasswd);

		Connection con;
		try
		{
			while (true)
			{
				try
				{
					con = DriverManager.getConnection(dbURL + dbCreateOpts,
							props);
					break;
				}
				catch (java.sql.SQLException ex)
				{
					System.err.println("Error getting connection: "
							+ ex.getMessage() + " : " + ex.getErrorCode()
							+ ": trying to get connection again.");
					ex.printStackTrace();
					java.lang.Thread.sleep(1000);
				}
			}

			con.close();
		}
		catch (java.lang.Exception ex)
		{
			ex.printStackTrace();
		}
	}

	private static Connection getNewConnection()
	{
		Connection con = null;

		try
		{
			while (true)
			{
				try
				{
					con = DriverManager.getConnection(dbURL, props);
					break;
				}
				catch (java.sql.SQLException ex)
				{
					System.err.println("Error getting connection: "
							+ ex.getMessage() + " : " + ex.getErrorCode()
							+ ": trying to get connection again.");
					ex.printStackTrace();
					java.lang.Thread.sleep(1000);
				}
			}
			con.setAutoCommit(false);
		}
		catch (java.lang.Exception ex)
		{
			ex.printStackTrace();
		}

		return con;
	}

	private static void createTables(int nbDbInstances)
	{
		Connection con = getNewConnection();

		Random r = new Random();

		try
		{
			for (int i = 0; i < nb_tables; i++)
			{

				PreparedStatement stat;
				if (PRIMARY) {
				stat = con
						.prepareStatement("CREATE TABLE BRANCHES"
								+ i
								+ "(BID INT NOT NULL PRIMARY KEY, BBALANCE INT, FILLER VARCHAR(88))");
				} else {
					stat = con
					.prepareStatement("CREATE TABLE BRANCHES"
							+ i
							+ "(BID INT NOT NULL, BBALANCE INT, FILLER VARCHAR(88))");
				}
				
				stat.execute();

				PreparedStatement insert = con
						.prepareStatement("INSERT INTO BRANCHES" + i

						+ "(BID, BBALANCE, FILLER) VALUES(?, ?, ?)");

				for (int j = 0; j < noOfRows; j++)
				{

					insert.setInt(1, j);
					insert.setInt(2, r.nextInt());
					insert.setString(3, "hahahahahahahahahaha");

					insert.execute();
				}

				con.commit();
			}
			con.close();
		}
		catch (SQLException e)
		{
			e.printStackTrace();
			try
			{
				con.rollback();
			}
			catch (SQLException e1)
			{
				e1.printStackTrace();
			}
		}

	}

	public static synchronized int getNextTableID()
	{
		return rand.nextInt(nb_tables);
	}

	static class MonitorThread extends Thread
	{
		private long startTime;

		public MonitorThread(int threadId, long start)
		{
			super("MonitorThread" + threadId);
			startTime = start;
		}

		public void flush()
		{
			long now = System.currentTimeMillis();

			long elapsedTime = (now - startTime) / 1000;
			System.out.println("Elapsed time: " + elapsedTime + " sec");
			System.out.println("Number of transactions: "
					+ DatabaseStats.getTransaction());
			System.out.println("Number of commits: "
					+ DatabaseStats.getCommit());
			System.out.println("Number of rollbacks: "
					+ DatabaseStats.getRollback());
			System.out
					.println("Number of errors: " + DatabaseStats.getErrors());

			if (elapsedTime > 0)
				System.out.println("Commits/sec: " + DatabaseStats.getCommit()
						/ elapsedTime);
		}

		public void run()
		{
			while (true)
			{
				flush();

				try
				{
					sleep(60 * 1000);
				}
				catch (InterruptedException e)
				{
				}
			}
		}
	}

	static class AccessThread extends Thread
	{
		private int duration; // how long does the experiment lasts, in seconds
		private Connection con;

		public AccessThread(int threadId, int duration)
		{
			super("AccessThread" + threadId);
			this.duration = duration;
			con = getNewConnection();
		}

		public void run()
		{
			Random r = new Random();

			long start = System.currentTimeMillis();
			while ((System.currentTimeMillis() - start) / 1000 < duration)
			{
				DatabaseStats.addTransaction();
				int i = getNextTableID();

				try
				{
					PreparedStatement select;
					if (COUNTSTARWHERE)
					{
						select = con
								.prepareStatement("SELECT COUNT(*) from BRANCHES"
										+ i
										+ " where BBALANCE=(SELECT max(BBALANCE) FROM BRANCHES"
										+ i + ")");
					}
					else if (COUNTSTAR)
					{
						select = con
								.prepareStatement("SELECT COUNT(*) from BRANCHES" + i);
					}
					else
					{
						select = con
								.prepareStatement("SELECT BID from BRANCHES"
										+ i
										+ " where BBALANCE=(SELECT max(BBALANCE) FROM BRANCHES"
										+ i + ")");
					}

					ResultSet rs = select.executeQuery();
					long l = 0;
					while (rs.next())
					{
						l++;
					}

					PreparedStatement update = con
							.prepareStatement("UPDATE BRANCHES" + i
									+ " SET BBALANCE=? WHERE BID=?");

					update.setInt(1, r.nextInt());
					update.setInt(2, r.nextInt());

					update.execute();

					con.commit();
					DatabaseStats.addCommit();
				}
				catch (SQLException e)
				{
					e.printStackTrace();
					try
					{
						con.rollback();
						DatabaseStats.addRollback();
					}
					catch (SQLException e1)
					{
						e1.printStackTrace();
						DatabaseStats.addErrors();
					}
				}
			}

			try
			{
				con.close();
			}
			catch (SQLException e)
			{
				e.printStackTrace();
			}
		}
	}
}
