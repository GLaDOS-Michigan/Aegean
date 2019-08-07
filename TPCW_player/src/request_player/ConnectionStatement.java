package request_player;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Properties;

public class ConnectionStatement
{
	private Connection con;
	private PreparedStatement[] psArray;
	
	
	//private static DBStatements dbStatement;

	private static String dbName = null;

	/* the properties to access the DB */
	private static Properties props = null;

	
	public ConnectionStatement(DBStatements dbStatements)
	{
		// create new connection
		con = ConnectionStatement.getNewConnection();

		// init statements
		psArray = new PreparedStatement[dbStatements.numberStatements()];
		for (int i = 0; i < psArray.length; i++)
		{
			try
			{
				psArray[i] = con.prepareStatement(dbStatements
						.convertCodeToStatement(i));
			}
			catch (SQLException e)
			{
				e.printStackTrace();
			}
		}
	}

	public Connection getConnection()
	{
		return con;
	}

	public PreparedStatement getPreparedStatement(int code)
	{
		return psArray[code];
	}
	
	public void closeConnection() {
		try
		{
			con.close();
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
	}
	
	

	/*
	 * Get a new connection to DB the DB must have already been created
	 */
	public static Connection getNewConnection()
	{
		Connection con;
		try
		{
			while (true)
			{
				try
				{
					con = DriverManager.getConnection(
							RequestPlayerUtils.protocol
									+ RequestPlayerUtils.dbName, props);

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
			if (dbName.equals("derby"))
			{
				con.setTransactionIsolation(RequestPlayerUtils.isolationLevel);
			}
			return con;
		}
		catch (java.lang.Exception ex)
		{
			ex.printStackTrace();
		}
		return null;
	}

	/*
	 * Get a new connection to DB dbName is the name of the DB. This method is
	 * called when creating the DB
	 */
	public static Connection getNewConnection(String dbN)
	{
		props = new Properties(); // connection properties
		props.put("user", RequestPlayerUtils.propsUser);
		props.put("password", RequestPlayerUtils.propsPasswd);
		
		dbName = dbN;

		Connection con;
		try
		{
			while (true)
			{
				try
				{
					con = DriverManager.getConnection(
							RequestPlayerUtils.protocol
									+ RequestPlayerUtils.dbName
									+ RequestPlayerUtils.dbCreateOpts, props);
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
			if (dbName.equals("derby"))
			{
				con.setTransactionIsolation(RequestPlayerUtils.isolationLevel);
			}
			return con;
		}
		catch (java.lang.Exception ex)
		{
			ex.printStackTrace();
		}
		return null;
	}
}
