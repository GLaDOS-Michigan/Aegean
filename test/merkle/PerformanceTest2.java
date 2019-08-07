package merkle;

import java.util.Random;
import java.security.MessageDigest;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;

public class PerformanceTest2 {

    private static int datasize;
    private static int datanum;
    private static int round;
    private static int appWork;
    private static int noOfThreads;
    private static int batchSize;

    private static int workFinished = 0;
    private static final Object workFinishedSig = new Object();

    private static String URL = "jdbc:h2:mem:testServer;MULTI_THREADED=1;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000;LOCK_MODE=3";
    private static String USER = "sa";
    private static String PASSWORD = "sa";

    private static String CREATE = "CREATE TABLE BRANCHES"
                        + "(BID INT NOT NULL PRIMARY KEY, BBALANCE INT, FILLER VARCHAR(88))";
    private static String INSERT = "INSERT INTO BRANCHES"
                                + "(BID, BBALANCE, FILLER) VALUES(1, 2, 'haha')";
    private static String SELECT = "SELECT * FROM BRANCHES";
    private static String UPDATE = "UPDATE BRANCHES SET BBALANCE=3 WHERE BID=1";

    private static void init() throws Exception {
        workFinished = 0;
        Random rand = new Random(System.currentTimeMillis());
	Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
        Statement stat=conn.createStatement();
	for(int i=0;i<noOfThreads;i++){
	    String createUser="CREATE USER " + ("USER"+i) + " IDENTIFIED BY " + ("PW"+i);
            String create="CREATE TABLE BRANCHES" + i
                    + "(BID INT NOT NULL PRIMARY KEY, BBALANCE INT, FILLER VARCHAR(88))";
	    System.out.println(createUser);
	    stat.execute(createUser);
	    stat.execute(create);
	    stat.execute("GRANT ALL ON BRANCHES" + i + " TO " + ("USER"+i));


	}
    }



    private static void clear() throws Exception{
	System.gc();
    }

    static byte[] tmp=new byte[datasize];
    private static void doOperation() throws Exception {
        //System.out.println(tmp.length+" "+obj.length);
        //System.arraycopy(tmp, 0, obj, 0, datasize);
        for (int i = 0; i < appWork; i++) {
	    //byte [] tmp = new byte[datasize];
	    for(int j=0;j<tmp.length;j++)
		tmp[i]=1;
        }
    }

    public static void testSingleBase() throws Exception {
	Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
        Statement stat=conn.createStatement();
	String create = "CREATE TABLE BRANCHES"
                        + "(BID INT NOT NULL PRIMARY KEY, BBALANCE INT, FILLER VARCHAR("+datasize+"))";
	stat.execute(create);
	char data[]=new char[datasize];
	for(int i=0;i<datasize;i++)
	    data[i]='1';
	String dataStr = new String(data);
	for(int i=0; i<datanum; i++){
	    String insert="INSERT INTO BRANCHES"
                                + "(BID, BBALANCE, FILLER) VALUES("+i+", " + i +", '"+dataStr+"')";
	    stat.execute(insert);
	}
	Random rand=new Random();
        long startTime = System.currentTimeMillis();
	for(int i=0;i<round;i++){
	    int index=rand.nextInt(datasize-1);
	    String update = "UPDATE BRANCHES SET FILLER='haha' WHERE BBALANCE="+i;
	    stat.execute(update);
	}
        long endTime = System.currentTimeMillis();
        System.out.println("time=" + (endTime - startTime) + " throughput=" + ((double) round / (endTime - startTime)));
    }


    public static void testMultipleBase() throws Exception {

        WorkerThread[] threads = new WorkerThread[noOfThreads];
        for (int i = 0; i < noOfThreads; i++) {
            threads[i] = new WorkerThread(batchSize / noOfThreads, i);
        }
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < noOfThreads; i++) {
            threads[i].start();
        }
        for (int i = 0; i < round / batchSize; i++) {
            synchronized (workFinishedSig) {
                workFinished = 0;
            }
            for (int j = 0; j < noOfThreads; j++) {
                threads[j].doWork();
            }
            /*Random rand = new Random(System.currentTimeMillis());
            for (int j = 0; j < batchSize / noOfThreads; j++) {
                //}
                try {
                    doOperation();
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }*/
            synchronized (workFinishedSig) {
                while (workFinished < batchSize ){//- batchSize/noOfThreads) {
                    workFinishedSig.wait();
                }
            }
        }
        for (int j = 0; j < noOfThreads; j++) {
            threads[j].finish();
        }
        long endTime = System.currentTimeMillis();
        System.out.println("testMultipleBase");
        printConfiguration();
        System.out.println("time=" + (endTime - startTime) + " throughput=" + ((double) round / (endTime - startTime)));
    }



    private static class WorkerThread extends Thread {

        private int workCount;
        private final Object sig = new Object();
        private boolean working = false;
        private boolean running = true;
	private Connection conn = null;
	private int index;
        public WorkerThread(int workCount, int index) {
            this.workCount = workCount;
	    this.index = index;
            //System.out.println("workCount="+workCount);
            try{
		
	    	for(int i=0;i<10000;i++){
		    conn=DriverManager.getConnection("jdbc:h2:mem:testServer", "USER"+index, "PW"+index);
		    Statement stat=conn.createStatement();
		    String insert="INSERT INTO BRANCHES" + index
                                + "(BID, BBALANCE, FILLER) VALUES(" + i + ", 2, 'haha')";
		    stat.execute(insert);
	    	}
	    }
	    catch(Exception e){e.printStackTrace();}
        }

        public void doWork() {
            synchronized (this.sig) {
                working = true;
                sig.notifyAll();
            }
        }

        public void finish() {
            running = false;
            doWork();
        }

        public void run() {
            while (true) {
                synchronized (this.sig) {
                    while (working == false) {
                        try {
                            sig.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                if (running == false) {
                    return;
                }
                //byte[] tmp = new byte[datasize];
                long batchStart=System.currentTimeMillis();
		Statement stat = null;
		try{
		    stat=conn.createStatement();
		}
		catch(SQLException e){e.printStackTrace();return;}
                for (int i = 0; i < workCount; i++) {
		//    System.out.println(index);
                    try {
                        //doOperation();
			String update="UPDATE BRANCHES"+index+" SET BBALANCE=3";// WHERE BID="+i;
			stat.execute(update);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
		System.out.println(workCount+" haha="+(System.currentTimeMillis()-batchStart)/workCount);
                synchronized (workFinishedSig) {
                    working = false;
                    workFinished += this.workCount;
		    if(workFinished==workCount*noOfThreads)
                        workFinishedSig.notifyAll();
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 6) {
            System.out.println("Usage: PerformanceTest "
                    + "datasize datanum round appWork noOfThreads batchSize");
            return;
        }

        datasize = Integer.parseInt(args[0]);
        datanum = Integer.parseInt(args[1]);
        round = Integer.parseInt(args[2]);
        appWork = Integer.parseInt(args[3]);
        noOfThreads = Integer.parseInt(args[4]);
        batchSize = Integer.parseInt(args[5]);

        //init();
        testSingleBase();

        //init();
        //testMultipleBase();
	//clear();

    }

    private static void printConfiguration() {
        System.out.println("datasize=" + datasize + " datanum=" + datanum + " round=" + round
                + " appWork=" + appWork + " noOfThreads=" + noOfThreads + " batchSize=" + batchSize);
    }
}
