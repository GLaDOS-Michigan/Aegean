package scalability;

public class Benchmark
{
	private static int NB_THREADS;
	private final static int NB_REQUESTS_BETWEEN_COMMIT = 1;
	private final static int NB_REQUESTS_BETWEEN_STATS = 10000;

	// private static final Object LOCK = new Object();

	// private static long startTime;
	// private static long totalTime;

	// private static int nbProcessedStatements = 0;

	public static void main(String... args) throws Exception
	{
		NB_THREADS = Integer.parseInt(args[0]);

		System.out.println("Nb threads = " + NB_THREADS);

		runTest();
	}

	public static void runTest() throws Exception
	{
		Thread[] threads = new Thread[NB_THREADS];
		for (int i = 0; i < NB_THREADS; i++)
		{
			threads[i] = new Thread(new BenchmarkThread(i));
		}

		// startTime = System.currentTimeMillis();

		for (Thread t : threads)
		{
			t.start();
		}
		for (Thread t : threads)
		{
			t.join();
		}
	}

	public static class BenchmarkThread implements Runnable
	{
		int id = -1;
		int integerField = 0;
		int nbProcessedStatements = 0;

		public BenchmarkThread(int id)
		{
			this.id = id;
		}

		public void run()
		{
			int nbLocallyProcessed = 0;

			try
			{
				long startTime = System.currentTimeMillis();
				while (true)
				{

					// synchronized (LOCK)
					// {
					if (nbProcessedStatements > 0
							&& nbProcessedStatements
									% NB_REQUESTS_BETWEEN_STATS == 0)
					{
						long totalTime = System.currentTimeMillis() - startTime;
						double statPerSec = (double) 1000
								* nbProcessedStatements / totalTime;
						System.out.println("Thread #" + id
								+ ": Nb statements = " + nbProcessedStatements
								+ " Statements per second: " + statPerSec);
					}
					nbProcessedStatements++;
					// }

					for (int k = 0; k < 1000; k++)
					{
						// String str = new Object().toString();
						integerField += k;
						// selectBranchs[id].executeQuery();
					}
					integerField = 0;

					// updateBranchs[i].setInt(1, delta);
					// updateBranchs[i].setInt(2, branch);
					// updateBranchs[i].executeUpdate();

					nbLocallyProcessed++;
					if (nbLocallyProcessed % NB_REQUESTS_BETWEEN_COMMIT == 0)
					{
						// conn.commit();
					}
				}
			} catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}
}
