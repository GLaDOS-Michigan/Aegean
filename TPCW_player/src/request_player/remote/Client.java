package request_player.remote;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;

import request_player.DBStatements;
import request_player.Request;
import request_player.RequestPlayerUtils;

/* Request player: replay a request trace coming from log4j logger in TPCW_Database */
public class Client
{
	/*
	 * This hash table maps an interaction ID to a hash table which maps a
	 * transaction ID to a list of Requests
	 */
	private Hashtable<Integer, Hashtable<Integer, ArrayList<Request>>> allInteractions;

	/* Iterator over the keys of allInteractions */
	private Iterator<Integer> iidIterator;

	/* statistics for computing the throughput */
	private int nbRequests = 0;
	private int nbTransactions = 0;
	private int nbTransactions2 = 0;
	private int nbInteractions = 0;

	private int nbCommit = 0;
	private int nbRollbacks = 0;
	private int nbErrors = 0;

	private int replayFinished = 0;
	private boolean mainFinished = false;

	private DBStatements dbStatement;

	/*
	 * main. Creates a new RequestPlayer Usage: RequestPlayer [nb
	 * Clients] [requests_file] [server_address] [server_port]
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

		System.out.println("The server is " + RequestPlayerUtils.serverAddr
				+ ":" + RequestPlayerUtils.serverPort);
		System.out.println("Starting request player with "
				+ RequestPlayerUtils.nbClients + " clients and file "
				+ RequestPlayerUtils.requestsFile);

		new Client();
	}

	/*
	 * Constructor of RequestPlayer. Get requests, launch clients, and print
	 * stats
	 */
	public Client()
	{
		dbStatement = (DBStatements) RequestPlayerUtils
				.loadClass(RequestPlayerUtils.DBStatements);

		// read file
		allInteractions = new Hashtable<Integer, Hashtable<Integer, ArrayList<Request>>>();
		readFile();

		initializeInteractionsIterator();

		System.out.println("Creating " + RequestPlayerUtils.nbClients
				+ " threads");
		ClientThread[] threads = new ClientThread[RequestPlayerUtils.nbClients];

		for (int i = 0; i < RequestPlayerUtils.nbClients; i++)
		{
			threads[i] = new ClientThread(i, this,
					RequestPlayerUtils.serverAddr,
					RequestPlayerUtils.serverPort);
		}

		System.out.println("Starting threads");
		long start = System.currentTimeMillis();
		for (int i = 0; i < RequestPlayerUtils.nbClients; i++)
		{
			threads[i].start();
		}

		wait4ReplayEnd();

		long end = System.currentTimeMillis();

		System.out.println("=== End of replay! ===");

		// print stats
		printStats();

		// compute throughput
		printThroughput(end - start);

		setMainEnd(true);

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
				Request r = new Request(line, dbStatement);
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
		Vector<Integer> vectorOfKeys = new Vector<Integer>(allInteractions
				.keySet());
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

	public synchronized void replayFinished()
	{
		replayFinished++;
		notifyAll();
	}

	public synchronized void wait4ReplayEnd()
	{
		while (replayFinished < RequestPlayerUtils.nbClients)
		{
			try
			{
				wait();
			}
			catch (InterruptedException e)
			{
			}
		}
	}

	public synchronized void setMainEnd(boolean b)
	{
		mainFinished = b;
	}

	public synchronized boolean mainEnd()
	{
		return mainFinished;
	}

	public synchronized void addTransaction()
	{
		nbTransactions2++;
	}

	public synchronized void addCommit()
	{
		nbCommit++;
	}

	public synchronized void addRollback()
	{
		nbRollbacks++;
	}

	public synchronized void addError()
	{
		nbErrors++;
	}

	/* duration: a time in ms, of type long */
	private void printThroughput(long duration)
	{
		double runningTime = duration / 1000.0;

		System.out.println("Total running time: " + runningTime + "s");
		System.out.println("Number of requests / second: "
				+ (nbRequests / runningTime));
		System.out.println("Number of transactions / second: "
				+ (nbTransactions / runningTime));
		System.out.println("Number of interactions / second: "
				+ (nbInteractions / runningTime));
	}

	private void printStats()
	{
		System.out.println("Number of requests: " + nbRequests);
		System.out.println("Number of transactions: " + nbTransactions);
		if (nbTransactions != nbTransactions2)
		{
			System.out
					.println("Warning! The number of transactions in the trace"
							+ "file is not the same as the number of transactions during"
							+ "the replay: " + nbTransactions + " != "
							+ nbTransactions2);
		}
		System.out.println("\tNumber of commits: " + nbCommit);
		System.out.println("\tNumber of rollbacks: " + nbRollbacks);
		System.out.println("\tNumber of errors: " + nbErrors);
		System.out.println("Number of interactions: " + nbInteractions);
	}

	class ClientThread extends Thread
	{
		private int threadId;
		private Client father;
		private String serverAddr;
		private int serverPort;
		private Socket server;
		private InputStream in;
		private OutputStream out;

		public ClientThread(int threadId, Client father, String addr, int port)
		{
			super("RequestPlayerClientThread" + threadId);

			this.father = father;
			this.threadId = threadId;
			this.serverAddr = addr;
			this.serverPort = port;

			try
			{
				server = new Socket(serverAddr, serverPort);
				server.setTcpNoDelay(true);

				out = server.getOutputStream();
				in = server.getInputStream();
			}
			catch (Exception e)
			{
				System.out.println("Exception in thread " + threadId);
				e.printStackTrace();
			}
		}

		/* launch the client */
		public void run()
		{
			// System.out.println("bsid\tiid\tid\tmethod name");

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
			father.replayFinished();

			while (!mainEnd())
			{
				try
				{
					sleep(1000);
				}
				catch (InterruptedException e)
				{
				}
			}

			try
			{
				out.close();
				in.close();
				server.close();
			}
			catch (IOException e)
			{
				System.out.println("IOException in thread " + threadId);
				e.printStackTrace();
			}
		}

		/* execute an interaction */
		private void executeInteraction(
				Hashtable<Integer, ArrayList<Request>> interaction)
		{
			Vector<Integer> vectorOfKeys = new Vector<Integer>(interaction
					.keySet());
			Collections.sort(vectorOfKeys);
			Iterator<Integer> it = vectorOfKeys.iterator();

			while (it.hasNext())
			{
				ArrayList<Request> transaction = interaction.get(it.next());
				executeTransaction(transaction);
			}
		}

		/* execute a transaction, ie a set of Requests before a commit */
		private void executeTransaction(ArrayList<Request> transaction)
		{
			// create a new TransactionMessage
			TransactionMessage tm = new TransactionMessage(transaction);

			try
			{
				ByteArrayOutputStream bos = new ByteArrayOutputStream();
				ObjectOutputStream oos = new ObjectOutputStream(bos);
				oos.writeObject(tm);
				oos.flush();

				byte[] data = bos.toByteArray();
				byte[] dataSize = intToByteArray(data.length);

				out.write(dataSize);
				out.write(data);
				father.addTransaction();

				// IN THE MEAN TIME THE INTERACTION IS BEING EXECUTED

				/*
				 * possible values of r: 0: commit 1: rollback 2: error
				 */
				byte[] buffer = new byte[4];
				in.read(buffer);
				int r = ByteBuffer.wrap(buffer).getInt();

				if (r == 0)
				{
					father.addCommit();
				}
				else if (r == 1)
				{
					father.addRollback();
				}
				else
				{
					father.addError();
				}
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}

		/* source: http://snippets.dzone.com/posts/show/93 */
		public final byte[] intToByteArray(int value)
		{
			return new byte[] { (byte) (value >>> 24), (byte) (value >>> 16),
					(byte) (value >>> 8), (byte) value };
		}
	}
}
