package request_player.local;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;

import request_player.ConnectionStatement;
import request_player.DBStatements;
import request_player.DataBase;
import request_player.DatabaseStats;
import request_player.Request;
import request_player.RequestPlayerUtils;
import request_player.StatementExecutor;

/* Request player: replay a request trace captured by log4j in TPCW_Database */
public class ClientServer
{
	DataBase dataBase;

	/* to access to the statements */
	private DBStatements dbStatements = null;

	/* false until the replay is not finished */
	private boolean replayFinished = false;

	/*
	 * This hash table maps an interaction ID to a hash table which maps a
	 * transaction ID to a list of Requests
	 */
	private Hashtable<Integer, Hashtable<Integer, ArrayList<Request>>> allInteractions;

	/* Iterator over the keys of allInteractions */
	private Iterator<Integer> iidIterator;

	/* statistics for computing the throughput */
	private int nbRequests;
	private int nbTransactions;
	private int nbInteractions;

	/*
	 * main. Creates a new RequestPlayer Usage: RequestPlayer [properties_file]
	 */
	public static void main(String[] args)
	{
		if (args.length >= 1)
		{
			RequestPlayerUtils.initProperties(args[0]);
		}
		else
		{
			RequestPlayerUtils.initProperties(null);
		}

		System.out.println("Starting request player with "
				+ RequestPlayerUtils.nbClients + " clients and file "
				+ RequestPlayerUtils.requestsFile);

		new ClientServer();
	}

	/*
	 * Constructor of RequestPlayer. Get requests, launch clients, and print
	 * stats
	 */
	public ClientServer()
	{
		// initialize the statements
		dbStatements = (DBStatements) RequestPlayerUtils
				.loadClass(RequestPlayerUtils.DBStatements);

		// read file
		allInteractions = new Hashtable<Integer, Hashtable<Integer, ArrayList<Request>>>();
		readFile();

		initializeInteractionsIterator();

		// create DB
		dataBase = (DataBase) RequestPlayerUtils
				.loadClass(RequestPlayerUtils.DBClass);
		dataBase.initDB();

		System.out.println("Creating " + RequestPlayerUtils.nbClients
				+ " threads");
		ClientServerThread[] threads = new ClientServerThread[RequestPlayerUtils.nbClients];

		for (int i = 0; i < RequestPlayerUtils.nbClients; i++)
		{
			threads[i] = new ClientServerThread(i, this);
		}

		// start monitor thread
		new MonitorThread(0, RequestPlayerUtils.monitorDelay, this).start();

		System.out.println("Starting threads");
		long start = System.currentTimeMillis();
		for (int i = 0; i < RequestPlayerUtils.nbClients; i++)
		{
			threads[i].start();
		}

		for (int i = 0; i < RequestPlayerUtils.nbClients; i++)
		{
			try
			{
				threads[i].join();
			}
			catch (InterruptedException e)
			{
				System.out.println("RequestPlayerThread " + i
						+ " has been interrupted!");
				e.printStackTrace();
			}
		}
		long end = System.currentTimeMillis();

		System.out.println("=== End of replay! ===");

		replayFinished = true;

		// print stats
		printStats();

		// compute throughput
		printThroughput(end - start);
	}

	private void readFile()
	{
		BufferedReader reader;
		String line;

		nbRequests = 0;
		nbTransactions = 0;
		nbInteractions = 0;

		try
		{
			reader = new BufferedReader(new FileReader(
					RequestPlayerUtils.requestsFile));

			while ((line = reader.readLine()) != null)
			{
				Request r = new Request(line, dbStatements);
				nbRequests++;

				Integer tid = new Integer(r.getTransactionId());
				Integer iid = new Integer(r.getInteractionId());

				Hashtable<Integer, ArrayList<Request>> oneInteraction = allInteractions
						.get(iid);

				if (oneInteraction == null)
				{
					oneInteraction = new Hashtable<Integer, ArrayList<Request>>();
					allInteractions.put(iid, oneInteraction);
					nbInteractions++;
				}

				// get the transaction of this request
				ArrayList<Request> transaction = oneInteraction.get(tid);

				if (transaction == null)
				{
					transaction = new ArrayList<Request>();
					oneInteraction.put(tid, transaction);
					nbTransactions++;
				}

				// now we can add the request
				transaction.add(r);
			}

			reader.close();
		}
		catch (FileNotFoundException e)
		{
			e.printStackTrace();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

		System.out.println("=== Trace file statistics ===");
		System.out.println("Number of requests: " + nbRequests);
		System.out.println("Number of transactions: " + nbTransactions);
		System.out.println("Number of interactions: " + nbInteractions);
	}

	/*
	 * private void initializeBrowsingSessionIterator() { Vector<Integer>
	 * vectorOfKeys = new Vector<Integer>( allBrowsingSessions.keySet());
	 * Collections.sort(vectorOfKeys); bsidIterator = vectorOfKeys.iterator(); }
	 */

	private void initializeInteractionsIterator()
	{
		Vector<Integer> vectorOfKeys = new Vector<Integer>(
				allInteractions.keySet());
		Collections.sort(vectorOfKeys);
		iidIterator = vectorOfKeys.iterator();
	}

	// public synchronized Hashtable<Integer, Hashtable<Integer,
	// ArrayList<Request>>> getBrowsingSession()
	public synchronized Hashtable<Integer, ArrayList<Request>> getInteraction()
	{
		if (iidIterator.hasNext())
		{
			return allInteractions.remove(iidIterator.next());
		}
		else
		{
			return null;
		}
	}

	public synchronized boolean canExecuteTransaction(
			ArrayList<Request> transaction)
	{
		RequestPlayerUtils.methodName method;
		boolean isRead;
		try
		{
			switch (RequestPlayerUtils.degreeOfParallelism)
			{
			case RequestPlayerUtils.allParallel:
				return true;

			case RequestPlayerUtils.seqWritesParallelReads:
				method = RequestPlayerUtils.string2methodName(transaction
						.get(0).getMethod());
				isRead = RequestPlayerUtils.methodIsRead(method);

				if (!isRead)
				{
					while (RequestPlayerUtils.currentWrite)
					{
						wait();
					}

					RequestPlayerUtils.currentWrite = true;
				}

				return true;

			case RequestPlayerUtils.seqWritesParallelReadsNoWait:
				method = RequestPlayerUtils.string2methodName(transaction
						.get(0).getMethod());
				isRead = RequestPlayerUtils.methodIsRead(method);

				if (!isRead)
				{
					if (RequestPlayerUtils.currentWrite)
					{
						return false;
					}

					RequestPlayerUtils.currentWrite = true;
				}

				return true;

			case RequestPlayerUtils.parallelismRules:
				System.err.println("Rules parallelism not implemented yet!");
				break;

			default:
				System.err.println("Unknown degree of parallelism: "
						+ RequestPlayerUtils.degreeOfParallelism);
				break;
			}
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}

		return false;
	}

	public synchronized void transactionExecuted(ArrayList<Request> transaction)
	{
		switch (RequestPlayerUtils.degreeOfParallelism)
		{
		case RequestPlayerUtils.allParallel:
		{
			break;
		}

		case RequestPlayerUtils.seqWritesParallelReads:
		case RequestPlayerUtils.seqWritesParallelReadsNoWait:
		{
			RequestPlayerUtils.methodName method = RequestPlayerUtils
					.string2methodName(transaction.get(0).getMethod());
			boolean isRead = RequestPlayerUtils.methodIsRead(method);

			if (!isRead)
			{
				RequestPlayerUtils.currentWrite = false;
				if (RequestPlayerUtils.degreeOfParallelism == RequestPlayerUtils.seqWritesParallelReads)
				{
					notifyAll();
				}
			}
		}
			break;

		case RequestPlayerUtils.parallelismRules:
		{
			System.err.println("Rules parallelism not implemented yet!");
		}
			break;

		default:
		{
			System.err.println("Unknown degree of parallelism: "
					+ RequestPlayerUtils.degreeOfParallelism);
		}
			break;
		}
	}

	/* duration: a time in ms, of type long */
	private void printThroughput(long duration)
	{
		double runningTime = duration / 1000.0;

		System.out.println("Total running time: " + runningTime + "s");
		System.out.println("Number of requests / second: "
				+ (nbRequests / runningTime));
		System.out.println("Number of transactions / second: "
				+ (DatabaseStats.getTransaction() / runningTime));
		System.out.println("Number of interactions / second: "
				+ (nbInteractions / runningTime));
	}

	private void printStats()
	{
		System.out.println("Number of requests: " + nbRequests);
		System.out.println("Number of transactions: "
				+ DatabaseStats.getTransaction());
		System.out.println("\tNumber of commits: " + DatabaseStats.getCommit());
		System.out.println("\tNumber of rollbacks: "
				+ DatabaseStats.getRollback());
		System.out.println("\tNumber of errors: " + DatabaseStats.getErrors());
		System.out.println("\tNumber of reads: " + DatabaseStats.getReads());
		System.out.println("\tNumber of writes: " + DatabaseStats.getWrites());
		System.out.println("Number of interactions: " + nbInteractions);
	}

	public synchronized boolean replayFinished()
	{
		return replayFinished;
	}

	class MonitorThread extends Thread
	{
		private int threadId;
		private long delay;
		private ClientServer father;

		/*
		 * create a new MonitorThread of id threadId which outputs statistics
		 * about the BD every delay minutes
		 */
		public MonitorThread(int threadId, long delay, ClientServer father)
		{
			super("MonitorThread" + threadId);
			this.threadId = threadId;
			this.delay = delay * 60 * 1000;
			this.father = father;
		}

		public void run()
		{
			int currentDuration = 0;

			System.out.println("Monitor thread " + threadId + " launched");

			while (true)
			{
				// print stats
				System.out.println("Monitor: " + currentDuration + " minutes");
				System.out.println("\tNumber of transactions: "
						+ DatabaseStats.getTransaction());
				System.out.println("\tNumber of commits: "
						+ DatabaseStats.getCommit());
				System.out.println("\tNumber of rollbacks: "
						+ DatabaseStats.getRollback());
				System.out.println("\tNumber of errors: "
						+ DatabaseStats.getErrors());
				System.out.println("\tNumber of reads: "
						+ DatabaseStats.getReads());
				System.out.println("\tNumber of writes: "
						+ DatabaseStats.getWrites());

				if (father.replayFinished())
				{
					break;
				}

				// sleep
				try
				{
					sleep(delay);
				}
				catch (InterruptedException e)
				{
				}
				currentDuration += delay / (60 * 1000);
			}
		}
	}

	class ClientServerThread extends Thread
	{
		private ClientServer father;
		@SuppressWarnings("unused")
		private int threadId;
		private ConnectionStatement cs;

		public ClientServerThread(int threadId, ClientServer father)
		{
			super("RequestPlayerThread" + threadId);

			this.father = father;
			this.threadId = threadId;

			cs = new ConnectionStatement(father.dbStatements);
		}

		/* launch the client */
		public void run()
		{
			while (true)
			{

				// get interaction
				Hashtable<Integer, ArrayList<Request>> interaction = father
						.getInteraction();

				if (interaction == null)
				{
					break;
				}

				// execute interaction
				executeInteraction(interaction);

			}

			// System.out.println("Thread " + threadId + " is ending");
			cs.closeConnection();
		}

		/* Execute a browsing session */
		@SuppressWarnings("unused")
		private void executeBrowsingSession(
				Hashtable<Integer, Hashtable<Integer, ArrayList<Request>>> browsingSession)
		{
			Vector<Integer> vectorOfKeys = new Vector<Integer>(
					browsingSession.keySet());
			Collections.sort(vectorOfKeys);
			Iterator<Integer> it = vectorOfKeys.iterator();

			while (it.hasNext())
			{
				Hashtable<Integer, ArrayList<Request>> interaction = browsingSession
						.get(it.next());
				executeInteraction(interaction);
			}
		}

		/* execute an interaction */
		private void executeInteraction(
				Hashtable<Integer, ArrayList<Request>> interaction)
		{
			Vector<Integer> vectorOfKeys = new Vector<Integer>(
					interaction.keySet());
			Collections.sort(vectorOfKeys);

			int idx = 0;
			while (!interaction.isEmpty())
			{
				Integer key = vectorOfKeys.get(idx % vectorOfKeys.size());
				ArrayList<Request> transaction = interaction.remove(key);

				if (father.canExecuteTransaction(transaction))
				{
					executeTransaction(transaction);
					father.transactionExecuted(transaction);
					vectorOfKeys.remove(idx % vectorOfKeys.size());
				}
				else
				{
					interaction.put(key, transaction);
					idx++;
				}
			}

			/*
			 * Iterator<Integer> it = vectorOfKeys.iterator();
			 * 
			 * while (it.hasNext()) { ArrayList<Request> transaction =
			 * interaction.get(it.next()); ArrayList<Request> transaction =
			 * executeTransaction(transaction); }
			 */
		}

		/* execute a transaction, ie a set of Requests before a commit */
		private void executeTransaction(ArrayList<Request> transaction)
		{
			Request r = null;
			Connection con = cs.getConnection();
			DatabaseStats.addTransaction();

			try
			{
				for (int i = 0; i < transaction.size(); i++)
				{
					r = transaction.get(i);

					int stmt_code = r.getStatementCode();
					PreparedStatement ps = cs.getPreparedStatement(stmt_code);

					// Use already created prepared statements
					if (RequestPlayerUtils.requests_processing)
					{
						StatementExecutor.executeNProcess(stmt_code, ps,
								r.getArguments(), father.dbStatements);
					}
					else
					{
						StatementExecutor.execute(ps, r.getArguments(),
								father.dbStatements.statementIsRead(stmt_code));
					}
				}

				con.commit();
				DatabaseStats.addCommit();
			}
			catch (java.lang.Exception ex)
			{
				try
				{
					con.rollback();
					DatabaseStats.addRollback();
					if (r != null)
					System.err.println(">>>Rollback of [" + r.toString() + "]");
					else
						System.err.println(">>>Rollback of a null request");
					ex.printStackTrace();
				}
				catch (Exception se)
				{
					DatabaseStats.addErrors();
					// System.err.println("Transaction rollback failed.");
					if (r != null)
					System.err.println(">>>Failed rollback of [" + r.toString()
							+ "]");
					else
						System.err.println(">>>Failed rollback of a null request");
					se.printStackTrace();
				}
			}
		}
	}
}
