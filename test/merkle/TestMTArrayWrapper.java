/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package merkle;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import junit.framework.Assert;
import junit.framework.TestCase;

import java.util.Arrays;

import merkle.wrapper.MTArrayWrapper;

/**
 *
 * @author yangwang
 */
public class TestMTArrayWrapper extends TestCase {

    @Override
    protected void setUp() throws Exception {
        MerkleTreeInstance.clear();
    }
    
    public void testArrayWrapper() throws Exception {
        int []target = new int[4];
        MTArrayWrapper<int []> wrapper = new MTArrayWrapper<int []>(target);
        //System.out.println("Create wrapper "+wrapper);
        MerkleTreeInstance.add(wrapper);
        target[0]=1234;


        MerkleTreeInstance.getShimInstance().setVersionNo(1);
        MerkleTreeInstance.update(wrapper);
        wrapper.setArray(new int[5]);
        wrapper.getArray()[0]=5678;

        Assert.assertEquals(5, wrapper.getArray().length);

        MerkleTreeInstance.getShimInstance().rollBack(0);
        Assert.assertEquals(4, wrapper.getArray().length);
        Assert.assertEquals(1234, wrapper.getArray()[0]);
    }

    public void testArrayWrapperSerialization() throws Exception {
        byte[] target = new byte[1024];
        MTArrayWrapper<byte[]> wrapper = new MTArrayWrapper<byte[]>(target);
        //System.out.println("Create wrapper "+wrapper);
        MerkleTreeInstance.add(wrapper);
        target[0]=3;

        byte[] hash1 = MerkleTreeInstance.getShimInstance().getHash();
        MerkleTreeInstance.getShimInstance().takeSnapshot("tmp/snap",0);
        MerkleTreeInstance.getShimInstance().setVersionNo(1);
        MerkleTreeInstance.update(wrapper);
        wrapper.setArray(new byte[2048]);
        wrapper.getArray()[0]=4;
        MerkleTreeInstance.getShimInstance().writeLog(new ObjectOutputStream(new FileOutputStream("tmp/log1")), 1);


        byte[] hash2 = MerkleTreeInstance.getShimInstance().getHash();

        MerkleTreeInstance.clear();
        MerkleTreeInstance.getShimInstance().loadSnapshot("tmp/snap");
        MerkleTreeInstance.getShimInstance().readLog(new ObjectInputStream(new FileInputStream("tmp/log1")));
        MTArrayWrapper<byte[]> wrapper2=(MTArrayWrapper<byte[]>)MerkleTreeInstance.getShimInstance().getObject(0);
        Assert.assertEquals(2048, wrapper2.getArray().length);
        Assert.assertEquals(4, wrapper2.getArray()[0]);
        Assert.assertTrue(Arrays.equals(hash2, MerkleTreeInstance.getShimInstance().getHash()));
        MerkleTreeInstance.getShimInstance().rollBack(0);
        Assert.assertEquals(1024, wrapper2.getArray().length);
        Assert.assertEquals(3, wrapper2.getArray()[0]);
        Assert.assertTrue(Arrays.equals(hash1, MerkleTreeInstance.getShimInstance().getHash()));

    }

    public void testArrayWrapperGC() throws Exception{
        Integer target[] = new Integer[2];
        MTArrayWrapper wrapper = new MTArrayWrapper(target);
        //System.out.println("Create wrapper "+wrapper);
        MerkleTreeInstance.addRoot(wrapper);
        Integer i1=new Integer(123);
        target[0]=i1;
        Integer i2=new Integer(456);
        target[1]=i2;


        MerkleTreeInstance.getShimInstance().setVersionNo(1);
        Integer i3=new Integer(789);
        MerkleTreeInstance.getShimInstance().gc();
        //Assert.assertEquals(3, ((MerkleTree)MerkleTreeInstance.getShimInstance()).size());
        Assert.assertNotSame(-1, MerkleTreeInstance.getShimInstance().getIndex(wrapper));
        Assert.assertNotSame(-1, MerkleTreeInstance.getShimInstance().getIndex(i1));
        Assert.assertNotSame(-1, MerkleTreeInstance.getShimInstance().getIndex(i2));
        Assert.assertEquals(-1, MerkleTreeInstance.getShimInstance().getIndex(i3));
    }


}


