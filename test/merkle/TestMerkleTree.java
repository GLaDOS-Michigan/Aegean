package merkle;

import BFT.exec.RequestInfo;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.EOFException;
import junit.framework.Assert;
import junit.framework.TestCase;

import java.util.Arrays;
import java.util.Random;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


/**
 * Created by IntelliJ IDEA.
 * User: iodine
 * Date: Jan 31, 2010
 * Time: 1:04:52 PM
 * To change this template use File | Settings | File Templates.
 */
public class TestMerkleTree extends TestCase {

    private byte[][] data = new byte[8][];
    private byte[] hash;

    @Override
    protected void setUp() throws Exception {
        //Generate Data
        Random rand = new Random();
        for (int i = 0; i < data.length; i++) {
            data[i] = new byte[1024];
            rand.nextBytes(data[i]);
        }
        //Calculate the hash sequentially
        /*MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[][] hashes = new byte[8][];
        for (int i = 0; i < data.length; i++) {
        digest.reset();
        hashes[i] = digest.digest(data[i]);
        }
        Tools.printHash(hashes[0]);
        byte[][] internal = new byte[4][];
        for (int i = 0; i < 4; i++) {
        digest.reset();
        digest.update(hashes[2 * i]);
        digest.update(hashes[2 * i + 1]);
        internal[i] = digest.digest();
        }
        byte[][] internal2 = new byte[2][];
        for (int i = 0; i < 2; i++) {
        digest.reset();
        digest.update(internal[2 * i]);
        digest.update(internal[2 * i + 1]);
        internal2[i] = digest.digest();
        }
        digest.reset();
        digest.update(internal2[0]);
        digest.update(internal2[1]);
        hash = digest.digest();*/
    }

    public void testAdd() throws Exception {
        MerkleTree tree = new MerkleTree();
        byte[] tmp = new byte[1024];
        tree.add(tmp);
        tree.finishThisVersion();
        Assert.assertEquals(tree.getIndex(tmp), 0);
        Assert.assertEquals(tree.getObject(0), tmp);
    }

    public void testGetHash() throws Exception {
        MerkleTree tree = new MerkleTree();
        for (int i = 0; i < data.length; i++) {
            tree.add(data[i]);
        }
        //System.out.println("First GetHash");
        byte[] hash1 = tree.getHash();

        tree = new MerkleTree();
        byte[][] tmp = new byte[8][];
        for (int i = 0; i < data.length; i++) {
            tmp[i] = new byte[1024];
            tree.add(tmp[i]);
        }
        //System.out.println("Second GetHash");
        tree.finishThisVersion();
        Assert.assertFalse(Arrays.equals(hash1, tree.getHash()));
        for (int i = 0; i < data.length; i++) {
            tree.update(tmp[i]);
            System.arraycopy(data[i], 0, tmp[i], 0, 1024);
        }
        Assert.assertTrue(Arrays.equals(hash1, tree.getHash()));
    }

    public void testGetHash2() throws Exception {
        MerkleTree tree = new MerkleTree();
        for (int i = 0; i < data.length; i++) {
            tree.add(data[i]);
        }
        //System.out.println("First GetHash");
        byte[] hash1 = tree.getHash();

        tree = new MerkleTree();
        byte[][] tmp = new byte[8][];
        for (int i = 0; i < data.length; i++) {
            tmp[i] = new byte[1024];
            if (i < 4) {
                System.arraycopy(data[i], 0, tmp[i], 0, 1024);
            }
            tree.add(tmp[i]);
        }
        //System.out.println("Second GetHash");
        Assert.assertFalse(Arrays.equals(hash1, tree.getHash()));
        for (int i = 4; i < data.length; i++) {
            tree.update(tmp[i]);
            System.arraycopy(data[i], 0, tmp[i], 0, 1024);
        }
        //System.out.println("Third GetHash");
        Assert.assertTrue(Arrays.equals(hash1, tree.getHash()));
    }

    //Test two merkle trees updating in a different order
    public void testGetHash3() throws Exception {
        MerkleTree tree = new MerkleTree();
        MerkleTree tree2 = new MerkleTree();
        byte[][] tmp = new byte[8][];
        byte[][] tmp2 = new byte[8][];
        for (int i = 0; i < data.length; i++) {
            tmp[i] = new byte[1024];
            tmp2[i] = new byte[1024];
            tree.add(tmp[i]);
            tree2.add(tmp2[i]);
        }


        for (int i = 0; i < 7; i++) {
            tree.update(tmp[i]);
            System.arraycopy(data[i], 0, tmp[i], 0, 1024);
            tree2.update(tmp2[7 - i]);
            System.arraycopy(data[7 - i], 0, tmp2[7 - i], 0, 1024);
        }

        //System.out.println("Second GetHash");
        Assert.assertFalse(Arrays.equals(tree.getHash(), tree2.getHash()));
    }

    //Rollback basic data
    public void testRollBack() throws Exception {
        DataItem item = new DataItem();
        MerkleTree tree = new MerkleTree();
        item.intValue = 1;
        item.longValue = 2;
        item.type = DataItem.Type.TYPE1;
        tree.add(item);
        byte oldhash[] = tree.getHash();
        tree.setVersionNo(1);
        tree.update(item);
        item.intValue = 3;
        item.longValue = 4;
        item.type = DataItem.Type.TYPE2;

        tree.rollBack(0);
        Assert.assertEquals(1, item.intValue);
        Assert.assertEquals(2, item.longValue);
        Assert.assertEquals(DataItem.Type.TYPE1, item.type);
        Assert.assertTrue(Arrays.equals(oldhash, tree.getHash()));
    }


    public void testRollBackByteArray() throws Exception {
        byte []item = new byte[1024];
        MerkleTree tree = new MerkleTree();
        item[0] = (byte)123;
        tree.add(item);
        tree.setVersionNo(1);
        tree.update(item);
        item[0] = (byte)124;
        byte oldhash[] = tree.getHash();
	tree.setVersionNo(2);
	tree.update(item);
        item[0] = (byte)125;

        tree.rollBack(1);
        Assert.assertEquals(124, item[0]);
        Assert.assertTrue(Arrays.equals(oldhash, tree.getHash()));
    }

    //Rollback adding a new object and a reference
    public void testRollBack2() throws Exception {
        DataItem item1 = new DataItem();
        MerkleTree tree = new MerkleTree();
        item1.intValue = 1;
        item1.longValue = 2;
        item1.type = DataItem.Type.TYPE1;
        tree.add(item1);
        byte oldhash[] = tree.getHash();
        tree.setVersionNo(1);

        DataItem item2 = new DataItem();
        tree.add(item2);
        tree.update(item1);
        item1.reference = item2;

        Assert.assertFalse(Arrays.equals(oldhash, tree.getHash()));
        tree.rollBack(0);
        Assert.assertNull(item1.reference);
        Assert.assertEquals(-1, tree.getIndex(item2));
        Assert.assertNull(tree.getObject(1));
        Assert.assertEquals(0, tree.getIndex(item1));
        Assert.assertEquals(item1, tree.getObject(0));
        Assert.assertTrue(Arrays.equals(oldhash, tree.getHash()));
    }

    //Rollback deleting an object
    public void testRollBack3() throws Exception {
        DataItem item1 = new DataItem();
        MerkleTree tree = new MerkleTree();
        item1.intValue = 1;
        item1.longValue = 2;
        item1.type = DataItem.Type.TYPE1;

        DataItem item2 = new DataItem();
        item1.reference = item2;
        tree.add(item1);
        tree.add(item2);
        byte oldhash[] = tree.getHash();
        tree.setVersionNo(1);

        tree.update(item1);
        item1.reference = null;

        Assert.assertFalse(Arrays.equals(oldhash, tree.getHash()));
        tree.rollBack(0);
        Assert.assertEquals(item2, item1.reference);
        Assert.assertEquals(1, tree.getIndex(item2));
        Assert.assertNotNull(tree.getObject(1));
        Assert.assertEquals(0, tree.getIndex(item1));
        Assert.assertEquals(item1, tree.getObject(0));
        Assert.assertTrue(Arrays.equals(oldhash, tree.getHash()));
    }

    //Rollback and re-execute
    public void testRollBack4() throws Exception {
        DataItem item1 = new DataItem();
        MerkleTree tree = new MerkleTree();
        item1.intValue = 1;
        item1.longValue = 2;
        item1.type = DataItem.Type.TYPE1;

        DataItem item2 = new DataItem();
        item1.reference = item2;
        tree.add(item1);
        tree.add(item2);

        tree.setVersionNo(1);

        tree.update(item1);
        item1.reference = null;

        byte oldhash[] = tree.getHash();

        tree.rollBack(0);
        tree.setVersionNo(1);

        tree.update(item1);
        item1.reference = null;

        Assert.assertTrue(Arrays.equals(oldhash, tree.getHash()));
    }

    //The simplest partial transfer: modify a single object
    public void testPartialTransfer() throws Exception {
        DataItem item1 = new DataItem();
        DataItem item2 = new DataItem();
        MerkleTree tree1 = new MerkleTree();
        MerkleTree tree2 = new MerkleTree();
        tree1.add(item1);
        tree2.add(item2);
        tree1.setVersionNo(1);
        tree1.update(item1);
        item1.intValue = 3;
        item1.longValue = 5L;
        item1.doubleValue = 2.2;
        item1.type = DataItem.Type.TYPE2;
        tree1.getHash();
        tree2.getHash();
        byte[] states = tree1.fetchStates(0, 1);
        tree2.mergeStates(1, states);
        Assert.assertEquals(item1.intValue, item2.intValue);
        Assert.assertEquals(item1.longValue, item2.longValue);
        Assert.assertEquals(item1.doubleValue, item2.doubleValue);
        Assert.assertEquals(item1.type, item2.type);
        Assert.assertTrue(Arrays.equals(tree1.getHash(), tree2.getHash()));
    }

    //add a single object
    public void testPartialTransfer2() throws Exception {
        DataItem item1 = new DataItem();
        DataItem item2 = new DataItem();
        MerkleTree tree1 = new MerkleTree();
        MerkleTree tree2 = new MerkleTree();
        tree1.add(item1);
        tree2.add(item2);
        tree1.setVersionNo(1);
        DataItem item3 = new DataItem();
        item3.intValue = 123;
        tree1.add(item3);
        tree1.update(item1);
        item1.reference = item3;


        Assert.assertNull(item2.reference);
        tree1.getHash();
        tree2.getHash();
        byte[] states = tree1.fetchStates(0, 1);
        tree2.mergeStates(1, states);
        Assert.assertNotNull(item2.reference);
        Assert.assertTrue(item2.reference instanceof DataItem);
        Assert.assertEquals(item3.intValue, ((DataItem) item2.reference).intValue);
        Assert.assertTrue(Arrays.equals(tree1.getHash(), tree2.getHash()));
    }

    //Transfer two versions
    public void testPartialTransfer3() throws Exception {
        DataItem item1 = new DataItem();
        DataItem item2 = new DataItem();
        MerkleTree tree1 = new MerkleTree();
        MerkleTree tree2 = new MerkleTree();
        tree1.add(item1);
        tree2.add(item2);
        tree1.setVersionNo(1);
        DataItem item3 = new DataItem();
        item3.intValue = 123;
        tree1.add(item3);
        tree1.update(item1);
        item1.reference = item3;
        tree1.setVersionNo(2);
        tree1.update(item1);
        item1.longValue = 12345;
        tree1.update(item3);
        item3.shortValue = 4;


        Assert.assertNull(item2.reference);
        tree1.finishThisVersion();
        tree2.finishThisVersion();
        byte[] states = tree1.fetchStates(0, 2);
        tree2.mergeStates(2, states);
        Assert.assertNotNull(item2.reference);
        Assert.assertTrue(item2.reference instanceof DataItem);
        Assert.assertEquals(item1.longValue, item2.longValue);
        Assert.assertEquals(item3.intValue, ((DataItem) item2.reference).intValue);
        Assert.assertEquals(item3.shortValue, ((DataItem) item2.reference).shortValue);
        Assert.assertTrue(Arrays.equals(tree1.getHash(), tree2.getHash()));

        /*tree1.rollBack(1);
        tree2.rollBack(1);
        Assert.assertNotNull(item2.reference);
        Assert.assertTrue(item2.reference instanceof DataItem);
        Assert.assertEquals(item1.longValue, item2.longValue);
        Assert.assertEquals(item3.intValue, ((DataItem) item2.reference).intValue);
        Assert.assertEquals(item3.shortValue, ((DataItem) item2.reference).shortValue);
        Assert.assertTrue(Arrays.equals(tree1.getHash(), tree2.getHash()));

        tree1.rollBack(0);
        tree2.rollBack(0);
        Assert.assertNull(item2.reference);
        Assert.assertTrue(Arrays.equals(tree1.getHash(), tree2.getHash()));*/
    }

    public void testMakeStable() throws Exception {
        DataItem item1 = new DataItem();
        MerkleTree tree1 = new MerkleTree();
        tree1.add(item1);
        tree1.setVersionNo(1);
        DataItem item3 = new DataItem();
        item3.intValue = 123;
        tree1.add(item3);
        tree1.update(item1);
        item1.reference = item3;
        tree1.setVersionNo(2);
        tree1.update(item1);
        item1.longValue = 12345;
        tree1.update(item3);
        item3.shortValue = 4;

        
        tree1.makeStable(1);
        //tree1.gc();
        
        tree1.rollBack(1);
        Assert.assertEquals(item3, item1.reference);
        Assert.assertEquals(0, item3.longValue);
        try {
            tree1.rollBack(0);
            throw new RuntimeException("Should not reach here");
        } catch (MerkleTreeException e) {
            //e.printStackTrace();
        }
    }

    public void testMakeStable2() throws Exception {
        DataItem item1 = new DataItem();
        MerkleTree tree1 = new MerkleTree();
        tree1.add(item1);
        tree1.setVersionNo(1);
        DataItem item3 = new DataItem();
        item3.intValue = 123;
        tree1.add(item3);
        tree1.update(item1);
        item1.reference = item3;
        tree1.setVersionNo(2);
        tree1.update(item1);
        item1.longValue = 12345;
        tree1.update(item3);
        item3.shortValue = 4;
        byte[] hash = tree1.getHash();
        tree1.makeStable(1);
        Assert.assertTrue(Arrays.equals(hash, tree1.getHash()));
    }

    public void testMakeStable3() throws Exception {
        DataItem item1 = new DataItem();
        MerkleTree tree1 = new MerkleTree();
        tree1.add(item1);
        tree1.setVersionNo(1);
        DataItem item3 = new DataItem();
        item3.intValue = 123;
        tree1.add(item3);
        tree1.update(item1);
        item1.reference = item3;
        tree1.finishThisVersion();
        tree1.makeStable(1);
        Assert.assertEquals(item1, tree1.getObject(0));
        Assert.assertEquals(item3, tree1.getObject(1));
    }

    public void testSnapshot() throws Exception {
        DataItem item1 = new DataItem();
        MerkleTree tree1 = new MerkleTree();
        tree1.add(item1);
        tree1.setVersionNo(1);
        DataItem item3 = new DataItem();
        item3.intValue = 123;
        tree1.add(item3);
        tree1.update(item1);
        item1.reference = item3;
        tree1.setVersionNo(2);
        tree1.update(item1);
        item1.longValue = 12345;
        tree1.update(item3);
        item3.shortValue = 4;

        tree1.takeSnapshot("tmp/snap",2);
        MerkleTree tree2 = new MerkleTree();
        tree2.loadSnapshot("tmp/snap");
        //tree1.getHash();
        //tree2.getHash();

        Assert.assertTrue(Arrays.equals(tree1.getHash(), tree2.getHash()));
        Assert.assertEquals(1, tree2.getIndex(tree2.getObject(1)));
        /*tree1.rollBack(1);
        tree2.rollBack(1);
        Assert.assertTrue(Arrays.equals(tree1.getHash(), tree2.getHash()));
        tree1.rollBack(0);
        tree2.rollBack(0);
        Assert.assertTrue(Arrays.equals(tree1.getHash(), tree2.getHash()));*/
    }

    public void testSnapshot2() throws Exception {
        DataItem item1 = new DataItem();
        MerkleTree tree1 = new MerkleTree();
        tree1.add(item1);
        tree1.setVersionNo(1);
        DataItem item3 = new DataItem();
        item3.intValue = 123;
        tree1.add(item3);
        tree1.update(item1);
        item1.reference = item3;
        //System.out.println(tree1);
        byte[] hash = tree1.getHash();
        tree1.setVersionNo(2);
        tree1.update(item1);
        item1.longValue = 12345;
        tree1.update(item3);
        item3.shortValue = 4;

        tree1.takeSnapshot("tmp/snap",1);
        MerkleTree tree2 = new MerkleTree();
        tree2.loadSnapshot("tmp/snap");

        //System.out.println(tree2);
        //tree1.getHash();
        //tree2.getHash();

        Assert.assertTrue(Arrays.equals(hash, tree2.getHash()));
        /*tree1.rollBack(1);
        tree2.rollBack(1);
        Assert.assertTrue(Arrays.equals(tree1.getHash(), tree2.getHash()));
        tree1.rollBack(0);
        tree2.rollBack(0);
        Assert.assertTrue(Arrays.equals(tree1.getHash(), tree2.getHash()));*/
    }

    public void testLog() throws Exception {
        DataItem item1 = new DataItem();
        MerkleTree tree1 = new MerkleTree();
        tree1.add(item1);
        tree1.writeLog(new ObjectOutputStream(new FileOutputStream("tmp/log0")), 0);
        tree1.setVersionNo(1);
        DataItem item3 = new DataItem();
        item3.intValue = 123;
        tree1.add(item3);
        tree1.update(item1);
        item1.reference = item3;
        tree1.writeLog(new ObjectOutputStream(new FileOutputStream("tmp/log1")), 1);
        tree1.setVersionNo(2);
        tree1.update(item1);
        tree1.update(item3);
        item1.longValue = 12345;
        item3.shortValue = 4;
        tree1.writeLog(new ObjectOutputStream(new FileOutputStream("tmp/log2")), 2);
        MerkleTree tree2 = new MerkleTree();
        tree2.readLog(new ObjectInputStream(new FileInputStream("tmp/log0")));
        tree2.readLog(new ObjectInputStream(new FileInputStream("tmp/log1")));
        tree2.readLog(new ObjectInputStream(new FileInputStream("tmp/log2")));
        
        Assert.assertTrue(Arrays.equals(tree1.getHash(), tree2.getHash()));
        tree1.rollBack(1);
        tree2.rollBack(1);

        Assert.assertTrue(Arrays.equals(tree1.getHash(), tree2.getHash()));
        tree1.rollBack(0);
        tree2.rollBack(0);
        Assert.assertTrue(Arrays.equals(tree1.getHash(), tree2.getHash()));
    }

    public void testMergedLog() throws Exception {
        ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("tmp/log"));
        DataItem item1 = new DataItem();
        MerkleTree tree1 = new MerkleTree();
        tree1.add(item1);
        tree1.writeLog(oos, 0);
        tree1.setVersionNo(1);
        DataItem item3 = new DataItem();
        item3.intValue = 123;
        tree1.add(item3);
        tree1.update(item1);
        item1.reference = item3;
        tree1.writeLog(oos, 1);
        tree1.setVersionNo(2);
        tree1.update(item1);
        tree1.update(item3);
        item1.longValue = 12345;
        item3.shortValue = 4;
        tree1.writeLog(oos, 2);
        MerkleTree tree2 = new MerkleTree();
        ObjectInputStream ois = new ObjectInputStream(new FileInputStream("tmp/log"));
	try{
            while(ois.available()>0){
                tree2.readLog(ois);
            }
	}
	catch(EOFException e){
	}

        Assert.assertTrue(Arrays.equals(tree1.getHash(), tree2.getHash()));
        tree1.rollBack(1);
        tree2.rollBack(1);
        Assert.assertTrue(Arrays.equals(tree1.getHash(), tree2.getHash()));
        tree1.rollBack(0);
        tree2.rollBack(0);
        Assert.assertTrue(Arrays.equals(tree1.getHash(), tree2.getHash()));
    }

    public void testSnapshotAndLog() throws Exception {
        DataItem item1 = new DataItem();
        MerkleTree tree1 = new MerkleTree();
        tree1.add(item1);
        tree1.setVersionNo(1);
        DataItem item3 = new DataItem();
        item3.intValue = 123;
        tree1.add(item3);
        tree1.update(item1);
        item1.reference = item3;
        tree1.takeSnapshot("tmp/snap",1);
        tree1.setVersionNo(2);
        tree1.update(item1);
        tree1.update(item3);
        item1.longValue = 12345;
        item3.shortValue = 4;
        tree1.writeLog(new ObjectOutputStream(new FileOutputStream("tmp/log2")), 2);
        MerkleTree tree2 = new MerkleTree();
        tree2.loadSnapshot("tmp/snap");
        tree2.readLog(new ObjectInputStream(new FileInputStream("tmp/log2")));

        Assert.assertTrue(Arrays.equals(tree1.getHash(), tree2.getHash()));
        tree1.rollBack(1);
        tree2.rollBack(1);
        Assert.assertTrue(Arrays.equals(tree1.getHash(), tree2.getHash()));
        /*tree1.rollBack(0);
        tree2.rollBack(0);
        Assert.assertTrue(Arrays.equals(tree1.getHash(), tree2.getHash()));*/
    }

    public void testAddStatic() throws Exception {
        MerkleTree tree = new MerkleTree();
        tree.addStatic(DataItem.class);
        Assert.assertEquals(DataItem.class, tree.getObject(0));
    }

    public void testRollbackStatic() throws Exception {
        MerkleTree tree = new MerkleTree();

        tree.addStatic(DataItem.class);
        DataItem.staticInt = 3;
        tree.setVersionNo(1);
        tree.updateStatic(DataItem.class);
        DataItem.staticInt = 4;
        Assert.assertEquals(4, DataItem.staticInt);
        tree.rollBack(0);
        Assert.assertEquals(3, DataItem.staticInt);
    }

    public void testRollbackStaticRef() throws Exception {
        DataItem item1 = new DataItem();
        MerkleTree tree = new MerkleTree();
        item1.longValue = 123L;
        tree.add(item1);
        DataItem.staticReference = item1;
        tree.addStatic(DataItem.class);
        tree.setVersionNo(1);
        tree.updateStatic(DataItem.class);
        DataItem.staticReference = null;
        Assert.assertNull(DataItem.staticReference);
        tree.rollBack(0);
        Assert.assertNotNull(DataItem.staticReference);
        Assert.assertTrue(DataItem.staticReference == item1);
    }

    public void testRollbackStaticRef2() throws Exception {
        DataItem item1 = new DataItem();
        MerkleTree tree = new MerkleTree();
        item1.longValue = 123L;
        tree.add(item1);
        DataItem.staticReference = item1;
        tree.addStatic(DataItem.class);
        tree.setVersionNo(1);
        tree.updateStatic(DataItem.class);
        DataItem.staticReference = null;
        tree.update(item1);
        item1.longValue = 456L;
        Assert.assertNull(DataItem.staticReference);
        tree.rollBack(0);
        Assert.assertNotNull(DataItem.staticReference);
        Assert.assertTrue(DataItem.staticReference == item1);
        Assert.assertEquals(123L, ((DataItem) DataItem.staticReference).longValue);
    }

    public void testStaticPartialTransfer() throws Exception {
        DataItem item1 = new DataItem();
        MerkleTree tree = new MerkleTree();
        item1.longValue = 123L;
        tree.add(item1);
        DataItem.staticReference = item1;
        tree.addStatic(DataItem.class);
        byte[] hash1 = tree.getHash();
        tree.setVersionNo(1);
        tree.updateStatic(DataItem.class);
        DataItem.staticReference = null;
        tree.update(item1);
        item1.longValue = 456L;


        byte[] state = tree.fetchStates(0, 1);
        //System.out.println(tree);
        //Tools.printObject(tree.getObject(0), tree);
        byte[] hash2 = tree.getHash();
        
        tree.rollBack(0);
        //System.out.println(tree);
        Assert.assertNotNull(DataItem.staticReference);
        Assert.assertTrue(DataItem.staticReference == item1);
        Assert.assertEquals(123L, ((DataItem) DataItem.staticReference).longValue);
        Assert.assertTrue(Arrays.equals(hash1, tree.getHash()));

        tree.mergeStates(1, state);
        //System.out.println(tree);
        Assert.assertNull(DataItem.staticReference);
        Assert.assertEquals(456L, item1.longValue);
        //Tools.printObject(tree.getObject(0), tree);
        Assert.assertTrue(Arrays.equals(hash2, tree.getHash()));
        
    }

    public void testStaticSnapshotAndLog() throws Exception {

        DataItem item1 = new DataItem();
        MerkleTree tree = new MerkleTree();
        item1.longValue = 123L;
        tree.add(item1);
        DataItem.staticReference = item1;
        tree.addStatic(DataItem.class);
        //System.out.println("hash1===============");
        byte[] hash1 = tree.getHash();
        tree.takeSnapshot("tmp/snap",0);
        tree.setVersionNo(1);
        tree.updateStatic(DataItem.class);
        DataItem.staticReference = null;
        tree.update(item1);
        item1.longValue = 456L;
        tree.writeLog(new ObjectOutputStream(new FileOutputStream("tmp/log1")), 1);

        //System.out.println("hash2===============");
        byte[] hash2 = tree.getHash();

        tree.loadSnapshot("tmp/snap");
        tree.readLog(new ObjectInputStream(new FileInputStream("tmp/log1")));

        Assert.assertNull(DataItem.staticReference);
        Assert.assertEquals(456L, item1.longValue);

        // System.out.println("hash3===============");
        tree.getHash();
        Assert.assertTrue(Arrays.equals(hash2, tree.getHash()));
        tree.rollBack(0);
        Assert.assertNotNull(DataItem.staticReference);
        //System.out.println("hash4===============");
        Assert.assertTrue(Arrays.equals(hash1, tree.getHash()));
    }

    public void testString() throws Exception {
        DataItem item1 = new DataItem();
        MerkleTree tree = new MerkleTree();
        String str = new String("123");
        tree.add(str);
        item1.reference = str;
        tree.add(item1);
        byte[] hash1 = tree.getHash();
        tree.takeSnapshot("tmp/snap",0);


        tree.loadSnapshot("tmp/snap");

        Assert.assertNotNull(((DataItem) tree.getObject(1)).reference);
        Assert.assertEquals("123", ((DataItem) tree.getObject(1)).reference);
        Assert.assertEquals(null, ((DataItem) tree.getObject(1)).str);
        Assert.assertTrue(Arrays.equals(hash1, tree.getHash()));
    }


    public void testRemove() throws Exception {
        DataItem item = new DataItem();
        MerkleTree tree = new MerkleTree();
        tree.add(item);
        byte[] hash1 = tree.getHash();
        Assert.assertNotNull(tree.getObject(0));
        tree.setVersionNo(1);
        tree.remove(item);
        Assert.assertNull(tree.getObject(0));
        byte[] hash2 = tree.getHash();
        //System.out.println("Before Rollback");
        tree.rollBack(0);
        Assert.assertNotNull(tree.getObject(0));
        Assert.assertTrue(Arrays.equals(hash1, tree.getHash()));
        tree.setVersionNo(1);
        //item=(DataItem)tree.getObject(0);
        tree.remove(item);
        Assert.assertNull(tree.getObject(0));
        Assert.assertTrue(Arrays.equals(hash2, tree.getHash()));
        tree.makeStable(1);
        tree.gc();
        Assert.assertNull(tree.getObject(0));
        Assert.assertTrue(Arrays.equals(hash2, tree.getHash()));
    }

    public void testRemoveSerialization() throws Exception {
        DataItem item = new DataItem();
        MerkleTree tree = new MerkleTree();
        tree.add(item);
        byte[] hash1 = tree.getHash();
        Assert.assertNotNull(tree.getObject(0));
        tree.takeSnapshot("tmp/snap",0);
        tree.setVersionNo(1);
        tree.remove(item);
        tree.writeLog(new ObjectOutputStream(new FileOutputStream("tmp/log1")), 1);
        Assert.assertNull(tree.getObject(0));
        //System.out.println("hash2==========================");
        byte[] hash2 = tree.getHash();

        tree.loadSnapshot("tmp/snap");
        tree.readLog(new ObjectInputStream(new FileInputStream("tmp/log1")));
        Assert.assertNull(tree.getObject(0));

        tree.rollBack(0);
        Assert.assertNotNull(tree.getObject(0));
        //System.out.println("hash3==========================");
        Assert.assertTrue(Arrays.equals(hash1, tree.getHash()));
        tree.setVersionNo(1);
        tree.remove(tree.getObject(0));
        Assert.assertNull(tree.getObject(0));
        //System.out.println("hash4==========================");
        Assert.assertTrue(Arrays.equals(hash2, tree.getHash()));
        tree.makeStable(1);
        tree.gc();
        Assert.assertNull(tree.getObject(0));
        Assert.assertTrue(Arrays.equals(hash2, tree.getHash()));
    }

    //Added test for new add/remove pattern

    public void testNewAddRemove() throws Exception {
        DataItem item = new DataItem();
        DataItem item2 = new DataItem();
        MerkleTree tree = new MerkleTree();
        tree.add(item);
        //Assert.assertEquals(item, tree.getObject(0));
        tree.remove(item);
        Assert.assertEquals(null, tree.getObject(0));
        tree.add(item2);
        tree.finishThisVersion();
        Assert.assertEquals(item2, tree.getObject(0));
    }

    public void testNewRollbackRemoveAdd() throws Exception {
        DataItem item = new DataItem();
        DataItem item2 = new DataItem();
        MerkleTree tree = new MerkleTree();
        tree.add(item);


        tree.setVersionNo(1);
        Assert.assertEquals(item, tree.getObject(0));
        tree.remove(item);
        Assert.assertEquals(null, tree.getObject(0));
        tree.setVersionNo(2);
        tree.add(item2);
        tree.finishThisVersion();
        Assert.assertEquals(item2, tree.getObject(0));
        tree.rollBack(1);
        Assert.assertEquals(null, tree.getObject(0));
        tree.rollBack(0);
        Assert.assertEquals(item, tree.getObject(0));
    }

    public void testNewRollbackRemoveAdd2() throws Exception {
        DataItem item = new DataItem();
        DataItem item2 = new DataItem();
        MerkleTree tree = new MerkleTree();
        tree.add(item);


        tree.setVersionNo(1);
        Assert.assertEquals(item, tree.getObject(0));
        tree.remove(item);
        Assert.assertEquals(null, tree.getObject(0));
        tree.setVersionNo(2);
        tree.add(item2);
        tree.finishThisVersion();
        Assert.assertEquals(item2, tree.getObject(0));
        tree.rollBack(1);
        Assert.assertEquals(null, tree.getObject(0));
        tree.add(item2);
        tree.finishThisVersion();
        Assert.assertEquals(item2, tree.getObject(0));
    }

    public void testNewHashRemoveAdd() throws Exception {
        DataItem item = new DataItem();
        DataItem item2 = new DataItem();
        MerkleTree tree = new MerkleTree();
        tree.add(item);


        byte[] hash0 = tree.getHash();
        Assert.assertEquals(item, tree.getObject(0));
        tree.setVersionNo(1);
        tree.remove(item);
        Assert.assertEquals(null, tree.getObject(0));
        byte[] hash1 = tree.getHash();
        tree.setVersionNo(2);
        tree.add(item2);

        byte[] hash2 = tree.getHash();
        Assert.assertEquals(item2, tree.getObject(0));
        tree.rollBack(1);
        Assert.assertEquals(null, tree.getObject(0));
        Assert.assertTrue(Arrays.equals(hash1, tree.getHash()));
        tree.rollBack(0);
        Assert.assertEquals(item, tree.getObject(0));
        Assert.assertTrue(Arrays.equals(hash0, tree.getHash()));
    }

    public void testNewReplace() throws Exception {
        DataItem item1 = new DataItem();
        DataItem item2 = new DataItem();
        DataItem item3 = new DataItem();
        MerkleTree tree = new MerkleTree();
        tree.add(item1);
        tree.add(item2);
        item1.reference = item2;

        Assert.assertEquals(item2, item1.reference);
        byte[] hash0 = tree.getHash();
        tree.setVersionNo(1);
        tree.remove(item2);
        tree.add(item3);
        tree.update(item1);
        item1.reference = item3;
        tree.finishThisVersion();
        Assert.assertEquals(item3, item1.reference);
        Assert.assertEquals(item3, tree.getObject(1));
        Assert.assertEquals(1, tree.getIndex(item3));

        tree.rollBack(0);
        Assert.assertEquals(item2, item1.reference);
        Assert.assertEquals(item2, tree.getObject(1));
        Assert.assertEquals(1, tree.getIndex(item2));
        Assert.assertTrue(Arrays.equals(hash0, tree.getHash()));
    }

    public void testNewMakeStable() throws Exception {
        DataItem item = new DataItem();
        DataItem item2 = new DataItem();
        MerkleTree tree = new MerkleTree();
        tree.add(item);


        tree.setVersionNo(1);
        Assert.assertEquals(item, tree.getObject(0));
        tree.remove(item);
        Assert.assertEquals(null, tree.getObject(0));
        byte[] hash1 = tree.getHash();

        tree.makeStable(1);
        tree.gc();
        Assert.assertTrue(Arrays.equals(hash1, tree.getHash()));
        tree.makeStable(2);
        tree.gc();
    }

    public void testNewRemovePartialTransfer() throws Exception {
        DataItem item = new DataItem();
        DataItem item2 = new DataItem();
	DataItem item3 = new DataItem();
        MerkleTree tree = new MerkleTree();
        MerkleTree tree2 = new MerkleTree();
        tree.add(item);
        tree2.add(item3);
        byte[] hash0 = tree.getHash();
        tree.setVersionNo(1);
        tree.remove(item);
        byte[] hash1 = tree.getHash();

        tree.setVersionNo(2);
        tree.add(item2);
        byte[] hash2 = tree.getHash();

        byte[] states = tree.fetchStates(0, 2);
	System.out.println(tree2);
        tree2.mergeStates(2, states);
        Assert.assertNotNull(tree2.getObject(0));
        Assert.assertTrue(Arrays.equals(hash2, tree2.getHash()));
	//System.out.println(tree2);
        tree2.rollBack(1);
	//System.out.println(tree2);
        Assert.assertNull(tree2.getObject(0));
        Assert.assertTrue(Arrays.equals(hash1, tree2.getHash()));
        tree2.rollBack(0);
        Assert.assertNotNull(tree2.getObject(0));
        Assert.assertTrue(Arrays.equals(hash0, tree2.getHash()));

    }

    public void testNewRemovePartialTransfer2() throws Exception {
        DataItem item = new DataItem();
        DataItem item2 = new DataItem();
	DataItem item3 = new DataItem();
        MerkleTree tree = new MerkleTree();
        MerkleTree tree2 = new MerkleTree();
        tree.add(item);
        tree2.add(item3);
        byte[] hash0 = tree.getHash();
        tree.setVersionNo(1);
        tree.remove(item);
        tree.add(item2);
        byte[] hash1 = tree.getHash();


        byte[] states = tree.fetchStates(0, 1);
        tree2.mergeStates(1, states);
        Assert.assertNotNull(tree2.getObject(0));
        Assert.assertTrue(Arrays.equals(hash1, tree2.getHash()));
        tree2.rollBack(0);
        Assert.assertNotNull(tree2.getObject(0));
        Assert.assertTrue(Arrays.equals(hash0, tree2.getHash()));

    }

    public void testNewRemoveSerialization() throws Exception {
        DataItem item = new DataItem();
        DataItem item2 = new DataItem();
        MerkleTree tree = new MerkleTree();
        tree.add(item);

        byte[] hash0 = tree.getHash();
        Assert.assertNotNull(tree.getObject(0));
        tree.takeSnapshot("tmp/snap",0);
        tree.setVersionNo(1);
        tree.remove(item);
        tree.writeLog(new ObjectOutputStream(new FileOutputStream("tmp/log1")), 1);
        Assert.assertNull(tree.getObject(0));
        //System.out.println("hash2==========================");
        byte[] hash1 = tree.getHash();

        tree.setVersionNo(2);
        tree.add(item2);
        tree.writeLog(new ObjectOutputStream(new FileOutputStream("tmp/log2")), 2);
        Assert.assertNotNull(tree.getObject(0));
        //System.out.println("hash2==========================");
        byte[] hash2 = tree.getHash();

        tree.loadSnapshot("tmp/snap");
        tree.readLog(new ObjectInputStream(new FileInputStream("tmp/log1")));
        tree.readLog(new ObjectInputStream(new FileInputStream("tmp/log2")));
        Assert.assertNotNull(tree.getObject(0));
        Assert.assertTrue(Arrays.equals(hash2, tree.getHash()));

        tree.rollBack(0);
        Assert.assertNotNull(tree.getObject(0));
        //System.out.println("hash3==========================");
        Assert.assertTrue(Arrays.equals(hash0, tree.getHash()));
        tree.setVersionNo(1);
        tree.remove(tree.getObject(0));
        Assert.assertNull(tree.getObject(0));
        //System.out.println("hash4==========================");
        Assert.assertTrue(Arrays.equals(hash1, tree.getHash()));
        tree.makeStable(1);
        tree.gc();
        Assert.assertNull(tree.getObject(0));
        Assert.assertTrue(Arrays.equals(hash1, tree.getHash()));
        tree.readLog(new ObjectInputStream(new FileInputStream("tmp/log2")));
        Assert.assertNotNull(tree.getObject(0));
        Assert.assertTrue(Arrays.equals(hash2, tree.getHash()));
    }

    public void testReuse() throws Exception {
        DataItem item1 = new DataItem();
        DataItem item2 = new DataItem();
        DataItem item3 = new DataItem();
        MerkleTree tree = new MerkleTree();
        tree.add(item1);
        tree.add(item2);
        tree.setVersionNo(1);
        tree.remove(item1);

        tree.add(item3);
        tree.finishThisVersion();
        Assert.assertEquals(0, tree.getIndex(item3));
        Assert.assertEquals(1, tree.getIndex(item2));
        Assert.assertEquals(-1, tree.getIndex(item1));
        Assert.assertNull(tree.getObject(2));
    }

    public void testComprehensive() throws Exception {
        DataItem item1 = new DataItem();
        item1.intValue = 1;
        DataItem item2 = new DataItem();
        item2.intValue = 2;
        DataItem item3 = new DataItem();
        item3.intValue = 3;
        MerkleTree tree = new MerkleTree();
        tree.add(item1);
        tree.add(item2);
        tree.checkConsistency();
        item1.reference = item2;
        tree.takeSnapshot("tmp/snap",0);
        byte[] hash0 = tree.getHash();
        tree.setVersionNo(1);
        tree.remove(item2);
        tree.add(item3);
        tree.update(item1);
        item1.reference = item3;
        tree.writeLog(new ObjectOutputStream(new FileOutputStream("tmp/log1")), 1);
        tree.checkConsistency();
        byte[] hash1 = tree.getHash();
        tree.setVersionNo(2);
        tree.remove(item1);


        byte[] states = tree.fetchStates(1, 2);
        byte[] hash2 = tree.getHash();

        tree.loadSnapshot("tmp/snap");
        tree.checkConsistency();
        tree.readLog(new ObjectInputStream(new FileInputStream("tmp/log1")));
        tree.checkConsistency();
 
        //System.out.println("TestComprehensive before merge states");
        tree.mergeStates(2, states);
        tree.checkConsistency();
        Assert.assertNull(tree.getObject(0));

        Assert.assertEquals(3, ((DataItem) tree.getObject(1)).intValue);
        Assert.assertTrue(Arrays.equals(hash2, tree.getHash()));
        tree.rollBack(1);
        tree.checkConsistency();
        Assert.assertEquals(1, ((DataItem) tree.getObject(0)).intValue);
        Assert.assertEquals(3, ((DataItem) tree.getObject(1)).intValue);
        Assert.assertEquals(tree.getObject(1), ((DataItem) tree.getObject(0)).reference);
        Assert.assertTrue(Arrays.equals(hash1, tree.getHash()));
        tree.rollBack(0);
        tree.checkConsistency();
        Assert.assertEquals(1, ((DataItem) tree.getObject(0)).intValue);
        Assert.assertEquals(2, ((DataItem) tree.getObject(1)).intValue);
        Assert.assertEquals(tree.getObject(1), ((DataItem) tree.getObject(0)).reference);
        Assert.assertTrue(Arrays.equals(hash0, tree.getHash()));

        tree.readLog(new ObjectInputStream(new FileInputStream("tmp/log1")));
        tree.checkConsistency();
        tree.mergeStates(2, states);
        tree.checkConsistency();
        tree.makeStable(2);
        tree.checkConsistency();
        tree.gc();
        tree.add(new Integer(4));
        tree.finishThisVersion();
        Assert.assertEquals(4, tree.getObject(0));
        tree.checkConsistency();
    }


    public void testScanning() throws Exception{
	MerkleTree tree = new MerkleTree();
	DataItem item1=new DataItem();
        DataItem item2=new DataItem();
        DataItem item3=new DataItem();
        item1.reference = item2;
        item2.reference = item3;
        item3.reference = item1;

        tree.add(item3);
        tree.finishThisVersion();
	Assert.assertEquals(0, tree.getIndex(item3));
	Assert.assertEquals(1, tree.getIndex(item1));
	Assert.assertEquals(2, tree.getIndex(item2));
    }

    /*public void testConcurrentAdd() throws Exception {
        MerkleTree tree = new MerkleTree();
        DataItem item1 = new DataItem();
        DataItem item2 = new DataItem();
        DataItem item3 = new DataItem();
        AddingThread thread1 = new AddingThread(item2, 2, tree);
        AddingThread thread2 = new AddingThread(item1, 1, tree);
        AddingThread thread3 = new AddingThread(item3, 3, tree);
        thread1.start();
        thread2.start();
        thread3.start();
        thread1.join();
        thread2.join();
        thread3.join();
        tree.finishThisVersion();
        Assert.assertEquals(item1, tree.getObject(0));
        Assert.assertEquals(item2, tree.getObject(1));
        Assert.assertEquals(item3, tree.getObject(2));

    }

    public void testConcurrentAdd2() throws Exception {
        MerkleTree tree = new MerkleTree();
        DataItem item1 = new DataItem();
        DataItem item2 = new DataItem();
        DataItem item3 = new DataItem();
        AddingThread thread1 = new AddingThread(item2, new RequestInfo(false, 2, 1, 0, 0, 0), tree);
        AddingThread thread2 = new AddingThread(item1, new RequestInfo(false, 1, 1, 0, 0, 0), tree);
        AddingThread thread3 = new AddingThread(item3, new RequestInfo(false, 2, 1, 1, 0, 0), tree);
        thread1.start();
        thread2.start();
        thread3.start();
        thread1.join();
        thread2.join();
        thread3.join();
        tree.finishThisVersion();
        Assert.assertEquals(item1, tree.getObject(0));
        Assert.assertEquals(item2, tree.getObject(1));
        Assert.assertEquals(item3, tree.getObject(2));

    }

    private class AddingThread extends Thread {
        private Object obj;
        private Comparable id;
        MerkleTree tree;

        public AddingThread(Object obj, Comparable id, MerkleTree tree) {
            this.obj = obj;
            this.id = id;
            this.tree = tree;
        }

        public void run() {
            tree.setRequestID(id);
            try {
                tree.add(obj);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }*/

    public void testGC() throws Exception {
        DataItem item1 = new DataItem();
        MerkleTree tree1 = new MerkleTree();
        tree1.addRoot(item1);
        tree1.setVersionNo(1);
        DataItem item3 = new DataItem();
        item3.intValue = 123;
        tree1.add(item3);
        tree1.finishThisVersion();
        tree1.makeStable(1);
        tree1.gc();
        Assert.assertEquals(item1, tree1.getObject(0));
        Assert.assertEquals(null, tree1.getObject(1));
    }

    public void testGC2() throws Exception {
        DataItem item1 = new DataItem();
        MerkleTree tree1 = new MerkleTree();
        tree1.addRoot(item1);
        tree1.setVersionNo(1);
        DataItem item2 = new DataItem();
        item2.intValue = 123;
        tree1.add(item2);
        DataItem item3 = new DataItem();
        tree1.add(item3);
        item2.reference = item3;
        item3.reference = item2;
        tree1.finishThisVersion();
        tree1.makeStable(1);
        tree1.gc();
        //Assert.assertEquals(1, tree1.size());
        Assert.assertNotSame(-1, tree1.getIndex(item1));
        Assert.assertEquals(-1, tree1.getIndex(item2));
        Assert.assertEquals(-1, tree1.getIndex(item3));
    }


}
