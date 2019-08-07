package merkle;

import Applications.benchmark.BenchmarkRequest;
import de.flexiprovider.core.FlexiCoreProvider;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import java.security.MessageDigest;
import java.security.Security;
import java.util.Random;

//import org.jikesrvm.scheduler.RVMThread;
/**
 * Created by IntelliJ IDEA.
 * User: GOOD
 * Date: 2009-12-22
 * Time: 19:48:17
 * To change this template use File | Settings | File Templates.
 */
public class Test {
    public static void main(String []args) throws Exception{
	//testFlexSHA();
	/*RVMThread.getCurrentThread().requestId = 1;
	RVMThread.getCurrentThread().objectId =0 ;
	for(int i=0;i<10000;i++){
	    Object a = new Object();
	    System.out.println(a.getMTID());
	}*/

	/*Integer tmp=(Integer)Tools.createNewObject(Integer.class);
	System.out.println(tmp);
	System.out.println(Tools.cloneObject(new Integer(3)));
	//System.out.println(new TestItem());
	DataItem item=(DataItem)Tools.createNewObject(DataItem.class);
	System.out.println(item);*/
        

        DataItem item1=new DataItem();
        DataItem item2=new DataItem();
        DataItem item3=new DataItem();
        item1.reference = item2;
        item2.reference = item3;
        item3.reference = item1;

	/*DataItem tmp = (DataItem)item1.mtClone();
	System.out.println(item1.reference);
	System.out.println(tmp.reference);*/
        MerkleTreeInstance.add(item3);
        MerkleTreeInstance.get().finishThisVersion();
        System.out.println(MerkleTreeInstance.get().getIndex(item1));
        System.out.println(MerkleTreeInstance.get().getIndex(item2));
        System.out.println(MerkleTreeInstance.get().getIndex(item3));
        //doSerializationTest();
        //doTestHash();
        //doMemAndHashTest();

        //System.out.printf("Hahs %s \n", 1);
        //Debug.log(Debug.MODULE_MERKLE, Debug.LEVEL_ERROR, "haha %d", 1);
        /*BitSet test=new BitSet(2);
        test.set(0);
        test.set(1);
        System.out.println(test.nextClearBit(0));*/
        //testMerkle();
        /*testSHA();
        System.out.println("FlexiSHA");
        testFlexSHA();*/
        /*Integer a=123;
        Integer b=456;
        System.out.println(b);
        Tools.copyObject(a, b);
        System.out.println(b);*/

       /* HashSet<String> c=new HashSet<String>();
        MTCollectionWrapper wrapper = new MTCollectionWrapper(c, true, true);
        MerkleTreeInstance.add(wrapper);
        Collection d=Collections.synchronizedCollection(wrapper);
        


        String str=new String();
        MerkleTreeInstance.add(str);
        d.add(str);
        MerkleTreeInstance.getShimInstance().takeSnapshot("tmp/snap");
        MerkleTreeInstance.clear();
        MerkleTreeInstance.getShimInstance().loadSnapshot("tmp/snap");*/

        /*MerkleTreeInstance.addStatic(TestData.class);
        MerkleTreeInstance.add(TestData.toto);

        MerkleTreeInstance.getShimInstance().takeSnapshot("tmp/snap");
        MerkleTreeInstance.clear();
        MerkleTreeInstance.getShimInstance().loadSnapshot("tmp/snap");*/
        /*String str=new String("haha");
        MerkleTreeInstance.add(str);
        TestDataChild child=new TestDataChild(str);
        MerkleTreeInstance.getShimInstance().takeSnapshot("tmp/snap");
        MerkleTreeInstance.clear();
        MerkleTreeInstance.getShimInstance().loadSnapshot("tmp/snap");
        TestDataChild tmp=(TestDataChild)MerkleTreeInstance.getShimInstance().getObject(1);
        System.out.println(tmp.getStr());*/
        /*Data1 data1=new Data1();
        MerkleTreeInstance.getShimInstance().setVersionNo(1);
        data1.setValue1(2);*/
    }

    public static void printArray(byte[] array){
        System.out.print("length="+array.length+" ");
        for(int i=0;i<array.length;i++)
            System.out.print(array[i]+" ");
        System.out.println();
    }

    public static void testMerkle() throws Exception{
        String i=new String("iodine");

        System.out.println(Tools.cloneObject(i));

        MerkleTree merkle = new MerkleTree();
        printArray(merkle.getHash());
        Data1 one1=new Data1(1,2L);
        Data1 one2=new Data1(1,2L);
        Data1 two=new Data1(3,4L);
        merkle.add(one1);
        merkle.add(one2);
        merkle.add(two);
        printArray(merkle.getHash());
        merkle.update(one1);
        merkle.update(one1);
        merkle.writeLog(new ObjectOutputStream(new FileOutputStream("tmp/log0")),0);
        merkle.setVersionNo(1);
        merkle.update(one1);
        merkle.writeLog(new ObjectOutputStream(new FileOutputStream("tmp/log1")),1);
         merkle.setVersionNo(2);
        merkle.update(one1);
        merkle.writeLog(new ObjectOutputStream(new FileOutputStream("tmp/log2")),2);
         merkle.setVersionNo(3);
        merkle.update(one1);
        merkle.writeLog(new ObjectOutputStream(new FileOutputStream("tmp/log3")),3);
         merkle.setVersionNo(4);
        merkle.update(one1);
        merkle.writeLog(new ObjectOutputStream(new FileOutputStream("tmp/log4")),4);
         merkle.setVersionNo(5);
        merkle.update(one1);
        merkle.writeLog(new ObjectOutputStream(new FileOutputStream("tmp/log5")),5);
        merkle.takeSnapshot("tmp/snapshot", 5);
        //System.out.println(merkle);
        byte []states=merkle.fetchStates(3,5);

        merkle.rollBack(3);
        //System.out.println(merkle);
        merkle.mergeStates(5, states);
        System.out.println("After merge");
        // System.out.println(merkle);
        MerkleTree merkle2 = new MerkleTree();
        merkle2.readLog(new ObjectInputStream(new FileInputStream("tmp/log0")));
        //System.out.println(merkle2);
        merkle2.readLog(new ObjectInputStream(new FileInputStream("tmp/log1")));
        merkle2.readLog(new ObjectInputStream(new FileInputStream("tmp/log2")));
        merkle2.readLog(new ObjectInputStream(new FileInputStream("tmp/log3")));
        merkle2.readLog(new ObjectInputStream(new FileInputStream("tmp/log4")));
        merkle2.readLog(new ObjectInputStream(new FileInputStream("tmp/log0")));
        System.out.println(merkle2);
        MerkleTree merkle3 = new MerkleTree();
        merkle3.loadSnapshot("tmp/snapshot");
        System.out.println(merkle3);
        merkle.remove(one1);
        System.out.println(merkle);
        merkle.makeStable(5);
        System.out.println(merkle);
        //printArray(merkle.getHash());

//        merkle.remove(one1);
        //printArray(merkle.getHash());
    }

    public static void testSHA() throws Exception{
        testSHA(1,4000);
        testSHA(64,2000);
        testSHA(256,1000);
        testSHA(512,500);
        testSHA(1024,500);
        testSHA(4096,200);
        testSHA(1048576,20);
        testSHA(1048576*10,2);
    }

    private static void testSHA(int size, int round) throws Exception{
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.reset();
        byte []data=new byte[size];
        Random rand=new Random();
        rand.nextBytes(data);
        long startTime=System.currentTimeMillis();
        for(int i=0;i<round;i++){
            //for(int j=0;j<size/32;j++)
            //digest.update(data, j*32, 32);
            for(int j=0; j<10; j++) {
                digest.digest(data);
            }
        }
        //digest.digest();
        long endTime=System.currentTimeMillis();
        System.out.println("Average time for "+size+"="+(double)(endTime-startTime)/round);
        System.out.println("Average time per byte="+(double)(endTime-startTime)/round/size);
    }

    private static void doTestHash() throws Exception{
        /*testHash(1024, 1, 50000);
        testHash(1024, 5, 10000);
        testHash(1024, 6, 8000);
        testHash(1024, 7, 7000);
        testHash(1024, 8, 6000);
        testHash(1024, 70, 500);
        testHash(1024, 71, 500);
        testHash(1024, 72, 500);
        testHash(1024, 73, 500);
        testHash(1024, 74, 500);
        testHash(1024, 75, 500);
        testHash(1024, 76, 500);
        testHash(1024, 77, 500);
        testHash(1024, 80, 250);
         */
        testHash(1024*1024, 1, 200);

    }
    
    private static void testHash(int size, int iterations, int round) throws Exception{
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.reset();
        byte []data=new byte[size];
        Random rand=new Random();
        rand.nextBytes(data);
        long startTime=System.currentTimeMillis();
        for(int i=0;i<round;i++){
            //for(int j=0;j<size/32;j++)
            //digest.update(data, j*32, 32);
            for(int j=0; j<iterations; j++) {
                digest.digest(data);
            }
        }
        //digest.digest();
        long endTime=System.currentTimeMillis();
        System.out.println("Average time for "+iterations+"="+(double)(endTime-startTime)/round);
        //System.out.println("Average time per byte="+(double)(endTime-startTime)/round/size);
    }

    public static void testFlexSHA() throws Exception{
        testFlexSHA(1,40000);
        testFlexSHA(64,20000);
        testFlexSHA(256,10000);
        testFlexSHA(512,5000);
        testFlexSHA(1024,5000);
        testFlexSHA(4096,2000);
        testFlexSHA(1048576,200);
        testFlexSHA(1048576*10,20);
    }

    private static void testFlexSHA(int size, int round) throws Exception{
        Security.addProvider(new FlexiCoreProvider());
        MessageDigest digest = MessageDigest.getInstance("SHA256","FlexiCore");
        digest.reset();
        byte []data=new byte[size];
        Random rand=new Random();
        rand.nextBytes(data);
        long startTime=System.currentTimeMillis();
        for(int i=0;i<round;i++){
            //for(int j=0;j<size/32;j++)
            //digest.update(data, j*32, 32);
            digest.digest(data);
        }
        //digest.digest();
        long endTime=System.currentTimeMillis();
        System.out.println("Average time for "+size+"="+(double)(endTime-startTime)/round);
        System.out.println("Average time per byte="+(double)(endTime-startTime)/round/size);
    }

    private static void doMemAndHashTest() throws Exception {
        testMemCopy(1,40000);
        testMemCopy(64,20000);
        testMemCopy(256,10000);
        testMemCopy(512,5000);
        testMemCopy(1024,5000);
        testMemCopy(4096,2000);
        testMemCopy(1048576,200);

        testMemCopyAndHash(1,40000);
        testMemCopyAndHash(64,20000);
        testMemCopyAndHash(256,10000);
        testMemCopyAndHash(512,5000);
        testMemCopyAndHash(1024,5000);
        testMemCopyAndHash(4096,2000);
        testMemCopyAndHash(1048576,200);
        
    }

    private static void testMemCopy(int size, int round) throws Exception{
        System.out.println("Starting testMemCopy with size = "+size+" and round = "+round);
        byte []data=new byte[size];
        byte[] target = new byte[size];
        Random rand=new Random();
        rand.nextBytes(data);
        long startTime=System.currentTimeMillis();
        for(int i=0;i<round;i++){
            System.arraycopy(data, 0, target, 0, size);
        }
        //digest.digest();
        long endTime=System.currentTimeMillis();
        System.out.println("Average time for "+size+"="+(double)(endTime-startTime)/round);
        System.out.println("Average time per byte="+(double)(endTime-startTime)/round/size);
    }

    private static void testMemCopyAndHash(int size, int round) throws Exception{
        System.err.println("Starting testMemCopyAndHash with size = "+size+" and round = "+round);
        Security.addProvider(new FlexiCoreProvider());
        MessageDigest digest = MessageDigest.getInstance("SHA256","FlexiCore");
        digest.reset();
        byte []data=new byte[size];
        byte[] target = new byte[size];
        Random rand=new Random();
        rand.nextBytes(data);
        long startTime=System.currentTimeMillis();
        for(int i=0;i<round;i++){
            digest.digest(data);
            System.arraycopy(data, 0, target, 0, size);
        }
        //digest.digest();
        long endTime=System.currentTimeMillis();
        System.err.println("Average time for "+size+"="+(double)(endTime-startTime)/round);
        System.err.println("Average time per byte="+(double)(endTime-startTime)/round/size);
    }

    private static void doSerializationTest() throws Exception {
        testJavaFinegrainSerialization(1,40000);
        testJavaFinegrainSerialization(64,20000);
        testJavaFinegrainSerialization(256,10000);
        testJavaFinegrainSerialization(512,5000);
        testJavaFinegrainSerialization(1024,5000);
        testJavaFinegrainSerialization(4096,2000);
        testJavaFinegrainSerialization(1048576,200);

        testManualSerialization(1,40000);
        testManualSerialization(64,20000);
        testManualSerialization(256,10000);
        testManualSerialization(512,5000);
        testManualSerialization(1024,5000);
        testManualSerialization(4096,2000);
        testManualSerialization(1048576,200);

    }
    
    private static void testJavaFinegrainSerialization(int size, int round) throws Exception {
        System.out.println("Starting testSerialization with size = "+size+" and round = "+round);
        //byte []data=new byte[size];
        //byte[] target = new byte[size];
        //Random rand=new Random();
        //rand.nextBytes(data);
        BenchmarkRequest bmrq = new BenchmarkRequest(1, 4, 423, 1, size, 0);
        long startTime=System.currentTimeMillis();
        byte[] s = new byte[1];
        for(int i=0;i<round;i++){
           bmrq.getJavaBytes();
        }
        //System.out.println("size "+s.length);

        //digest.digest();
        long endTime=System.currentTimeMillis();
        System.out.println("Average time for "+size+"="+(double)(endTime-startTime)/round);
        System.out.println("Average time per byte="+(double)(endTime-startTime)/round/size);
    }

    private static void testManualSerialization(int size, int round) throws Exception {
        System.err.println("Starting testSerialization with size = "+size+" and round = "+round);
        //byte []data=new byte[size];
        //byte[] target = new byte[size];
        //Random rand=new Random();
        //rand.nextBytes(data);
        BenchmarkRequest bmrq = new BenchmarkRequest(1, 4, 423, 1, size, 0);
        long startTime=System.currentTimeMillis();
        byte[] s = null;
        for(int i=0;i<round;i++){
            s = bmrq.getBytes();
        }
        //System.out.println("size "+s.length);
        //digest.digest();
        long endTime=System.currentTimeMillis();
        System.err.println("Average time for "+size+"="+(double)(endTime-startTime)/round);
        System.err.println("Average time per byte="+(double)(endTime-startTime)/round/size);
    }
}
