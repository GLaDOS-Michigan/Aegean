package request_player.remote;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.NoSuchElementException;

import request_player.ConnectionStatement;
import request_player.DBStatements;
import request_player.DataBase;
import request_player.DatabaseStats;
import request_player.RequestPlayerUtils;
import request_player.StatementExecutor;

/* Request player: replay a request trace coming from log4j logger in TPCW_Database (TPCW benchmark implemented by the Univ. Wisconsin)*/
public class Server
{

	private LinkedList<PendingQueueElt> transactionPendingQueue;
	private LinkedList<PendingQueueElt> returnValuePendingQueue;
	DataBase dataBase;

	/* to access to the statements */
	private DBStatements dbStatements = null;

	/*
	 * main. Creates a new RequestPlayerServer Usage: RequestPlayerServer
	 * server_port pool_size
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

		System.out.println("Starting server on port "
				+ RequestPlayerUtils.serverPort + " with "
				+ RequestPlayerUtils.poolSize + " threads in the pool");
		new Server();
	}

	public Server()
	{
		// initialize the statements
		dbStatements = (DBStatements) RequestPlayerUtils
				.loadClass(RequestPlayerUtils.DBStatements);

		// create DB
		dataBase = (DataBase) RequestPlayerUtils
				.loadClass(RequestPlayerUtils.DBClass);
		dataBase.initDB();

		// initialize the statements
		dbStatements = (DBStatements) RequestPlayerUtils
				.loadClass(RequestPlayerUtils.DBStatements);

		// start monitor thread
		new MonitorThread(0, RequestPlayerUtils.monitorDelay).start();

		// create the ReplaySender thread
		returnValuePendingQueue = new LinkedList<PendingQueueElt>();
		new ReplaySenderThread(0, this).start();

		// create the pool of Executor threads
		transactionPendingQueue = new LinkedList<PendingQueueElt>();
		for (int threadId = 0; threadId < RequestPlayerUtils.poolSize; threadId++)
		{
			(new ExecutorThread(threadId, this)).start();
		}

		try
		{
			ServerSocketChannel ssc = ServerSocketChannel.open();
			ssc.configureBlocking(false);

			ServerSocket server = ssc.socket();

			// bind to address
			InetSocketAddress serverInetAddr = new InetSocketAddress(
					RequestPlayerUtils.serverPort);
			server.bind(serverInetAddr);

			Selector mySelector = Selector.open();
			ssc.register(mySelector, SelectionKey.OP_ACCEPT);

			System.out.println("Server is ready to accept connections...");

			while (true)
			{
				int num = mySelector.select();

				// If you don't have any activity, loop around and wait again.
				if (num == 0)
				{
					continue;
				}

				// Get the keys corresponding to the activity
				// that have been detected and process them
				// one by one.
				Set<SelectionKey> keys = mySelector.selectedKeys();
				Iterator<SelectionKey> it = keys.iterator();
				while (it.hasNext())
				{
					SelectionKey key = (SelectionKey) it.next();

					if (key.isAcceptable())
					{
						// Accept the incoming connection.
						Socket client = server.accept();
						client.setTcpNoDelay(true);

						// Make sure to make it nonblocking, so you can
						// use a Selector on it.
						SocketChannel sc = client.getChannel();
						sc.configureBlocking(false);
						// Register it with the Selector, for reading.
						sc.register(mySelector, SelectionKey.OP_READ);
					}
					else if (key.isReadable())
					{
						SocketChannel sc = (SocketChannel) key.channel();
						int bytesRead;

						ByteBuffer buffer4Size = ByteBuffer.allocate(4);

						// read the size of the message
						bytesRead = 0;
						while (bytesRead != 4)
						{
							bytesRead += sc.read(buffer4Size);
						}
						buffer4Size.flip();

						int size = buffer4Size.getInt();

						// receive the message
						ByteBuffer buffer4Msg = ByteBuffer.allocate(size);
						bytesRead = 0;
						while (bytesRead != size)
						{
							bytesRead += sc.read(buffer4Msg);
						}
						buffer4Msg.flip();

						ByteArrayInputStream bis = new ByteArrayInputStream(
								buffer4Msg.array(), 0, buffer4Msg.limit());
						ObjectInputStream ois = new ObjectInputStream(bis);

						try
						{
							TransactionMessage tm = (TransactionMessage) ois
									.readObject();
							transactionPendingQueueAdd(sc, tm);
						}
						catch (ClassNotFoundException e)
						{
							e.printStackTrace();
						}

					}

					it.remove();
				}

				// Remove the selected keys because you've dealt
				// with them.
				keys.clear();
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	public synchronized void returnValuePendingQueueAdd(SocketChannel sc,
			int returnValue)
	{
		returnValuePendingQueue.addLast(new PendingQueueElt(sc, returnValue));
		if (returnValuePendingQueue.size() == 1)
		{
			notifyAll();
		}
	}

	public synchronized PendingQueueElt returnValuePendingQueueGet()
	{
		try
		{
			while (returnValuePendingQueue.isEmpty())
			{
				wait();
			}

			return returnValuePendingQueue.removeFirst();
		}
		catch (InterruptedException e)
		{
		}
		catch (NoSuchElementException e)
		{
		}

		return null;
	}

	public synchronized void transactionPendingQueueAdd(SocketChannel sc,
			TransactionMessage tm)
	{
		transactionPendingQueue.addLast(new PendingQueueElt(sc, tm));
		if (transactionPendingQueue.size() == 1)
		{
			notifyAll();
		}
	}

	public synchronized PendingQueueElt transactionPendingQueueGet()
	{
		PendingQueueElt elt = null;

		try
		{
			while (transactionPendingQueue.isEmpty())
			{
				wait();
			}

			switch (RequestPlayerUtils.degreeOfParallelism)
			{
			case RequestPlayerUtils.allParallel:
				return transactionPendingQueue.removeFirst();

			case RequestPlayerUtils.seqWritesParallelReads:
				elt = transactionPendingQueue.removeFirst();

				if (!elt.isRead())
				{
					while (RequestPlayerUtils.currentWrite)
					{
						wait();
					}
					RequestPlayerUtils.currentWrite = true;
				}

				return elt;

			case RequestPlayerUtils.seqWritesParallelReadsNoWait:
				for (PendingQueueElt elt2 : transactionPendingQueue)
				{
					if (elt2.isRead())
					{
						transactionPendingQueue.remove(elt2);
						return elt2;
					}
					else if (!RequestPlayerUtils.currentWrite)
					{
						transactionPendingQueue.remove(elt2);
						RequestPlayerUtils.currentWrite = true;
						return elt2;
					}
				}

				// if we get here then it means that we cannot get an
				// element in
				// the pending queue
				// so we break to return null
				break;

			case RequestPlayerUtils.parallelismRules:
				System.err.println("Parallelism rules not implemented yet!");
				break;

			default:
				System.err.println("Unknown degree of parallelism: "
						+ RequestPlayerUtils.degreeOfParallelism);
				break;
			}
		}
		catch (InterruptedException e)
		{
		}
		catch (NoSuchElementException e)
		{
		}

		return null;
	}

	public synchronized void transactionExecuted(PendingQueueElt elt)
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
			if (!elt.isRead())
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

	class MonitorThread extends Thread
	{
		private int threadId;
		private long delay;

		/*
		 * create a new MonitorThread of id threadId which outputs statistics
		 * about the BD every delay minutes
		 */
		public MonitorThread(int threadId, long delay)
		{
			super("MonitorThread" + threadId);
			this.threadId = threadId;
			this.delay = delay * 60 * 1000;
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

	class ReplaySenderThread extends Thread
	{
		@SuppressWarnings("unused")
		private int threadId;
		private Server server;

		public ReplaySenderThread(int threadId, Server server)
		{
			super("ReplaySenderThread" + threadId);
			this.threadId = threadId;
			this.server = server;
		}

		public void run()
		{
			while (true)
			{
				PendingQueueElt elt = server.returnValuePendingQueueGet();

				if (elt != null)
				{
					try
					{
						ByteBuffer buffer = ByteBuffer.allocate(4);
						buffer.putInt(elt.getReturnValue());
						buffer.flip();

						SocketChannel sc = elt.getSocketChannel();
						sc.write(buffer);
					}
					catch (Exception e)
					{
						// e.printStackTrace();
					}
				}
			}
		}

	}

	class ExecutorThread extends Thread
	{
		@SuppressWarnings("unused")
		private int threadId;
		private Server server;
		private ConnectionStatement cs;

		public ExecutorThread(int threadId, Server server)
		{
			super("RequestPlayerServerThread" + threadId);
			this.threadId = threadId;
			this.server = server;

			cs = new ConnectionStatement(server.dbStatements);
		}

		public void run()
		{
			int ret;
			PendingQueueElt elt;

			while (true)
			{
				ret = 2;
				elt = server.transactionPendingQueueGet();

				if (elt == null)
				{
					// System.out.println("The pending queue element is null");
					continue;
				}

				TransactionMessage tm = elt.getTransactionMessage();

				if (tm != null)
				{
					int idx = 0;
					Connection con = cs.getConnection();
					DatabaseStats.addTransaction();

					try
					{
						while (idx < tm.getNbOfRequests())
						{

							int stmt_code = tm.getStatementCode(idx);
							PreparedStatement ps = cs.getPreparedStatement(stmt_code);

							// Use already created prepared statements
							if (RequestPlayerUtils.requests_processing)
							{
								StatementExecutor.executeNProcess(stmt_code, ps,
										tm.getArguments(idx), dbStatements);
							}
							else
							{
								StatementExecutor.execute(ps, tm.getArguments(idx),
										dbStatements.statementIsRead(stmt_code));
							}
							
							idx++;
						}

						ret = 0;
						con.commit();
						DatabaseStats.addCommit();
					}
					catch (java.lang.Exception ex)
					{
						try
						{
							ret = 1;
							con.rollback();
							DatabaseStats.addRollback();
							System.err.println(">>>Rollback of [shopping id: "
									+ tm.getShoppingID(idx) + ", "
									+ tm.getStatementCode(idx) + " / "
									+ tm.getArguments(idx) + "]");
							ex.printStackTrace();
						}
						catch (Exception se)
						{
							ret = 2;
							DatabaseStats.addErrors();
							System.err
									.println(">>>Failed rollback of [shopping id: "
											+ tm.getShoppingID(idx)
											+ ", "
											+ tm.getStatementCode(idx)
											+ " / "
											+ tm.getArguments(idx) + "]");
							se.printStackTrace();
						}
					}
				}

				// here we have finished to execute the transaction
				server.transactionExecuted(elt);
				server.returnValuePendingQueueAdd(elt.getSocketChannel(), ret);
			}

			// cs.closeConnection();
		}
	}

	/*
	 * PendingQueueElt is an element of the pending queues It can be used for
	 * passing transactions, and for passing replies to send to clients,
	 * according to which constructor is used.
	 */
	public class PendingQueueElt
	{
		private SocketChannel sc;
		private TransactionMessage tm;
		private boolean isRead;
		private int returnValue;

		/* Constructor used for passing transactions */
		public PendingQueueElt(SocketChannel sc, int returnValue)
		{
			this.sc = sc;
			this.returnValue = returnValue;
			this.isRead = false;
		}

		public PendingQueueElt(SocketChannel sc, TransactionMessage tm)
		{
			this.sc = sc;
			this.tm = tm;
			this.returnValue = 2;
			this.isRead = tm.isRead();
		}

		public SocketChannel getSocketChannel()
		{
			return sc;
		}

		public TransactionMessage getTransactionMessage()
		{
			return tm;
		}

		public boolean isRead()
		{
			return isRead;
		}

		public int getReturnValue()
		{
			return returnValue;
		}
	}
}
