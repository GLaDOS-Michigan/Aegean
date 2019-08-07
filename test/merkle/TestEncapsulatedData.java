/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package merkle;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import junit.framework.Assert;
import junit.framework.TestCase;
/**
 *
 * @author yangwang
 */
public class TestEncapsulatedData extends TestCase{

    @Override
    protected void setUp() throws Exception {
        MerkleTreeInstance.clear();
    }
    
    public void testBasic() throws Exception{
        EncapsulatedData item = new EncapsulatedData();
        MerkleTreeInstance.getShimInstance().finishThisVersion();
        Assert.assertEquals(item, MerkleTreeInstance.getShimInstance().getObject(0));

        MerkleTreeInstance.getShimInstance().setVersionNo(1);
        item.setValue(2);
        item.createName();
        String name=item.getName();

        MerkleTreeInstance.getShimInstance().setVersionNo(2);
        item.setValue(3);

        MerkleTreeInstance.getShimInstance().rollBack(1);
        Assert.assertEquals(2, item.getValue());
        Assert.assertEquals(name, item.getName());

        MerkleTreeInstance.getShimInstance().rollBack(0);
        Assert.assertEquals(0, item.getValue());
        Assert.assertEquals(null, item.getName());
    }

    public void testDoubleAdd() throws Exception{
        EncapsulatedData item = new EncapsulatedData();
        MerkleTreeInstance.add(item);
        MerkleTreeInstance.getShimInstance().finishThisVersion();
        Assert.assertEquals(item, MerkleTreeInstance.getShimInstance().getObject(0));
        Assert.assertEquals(null, MerkleTreeInstance.getShimInstance().getObject(1));

    }

    public void testSerialization() throws Exception{
        EncapsulatedData item = new EncapsulatedData();
        MerkleTreeInstance.getShimInstance().finishThisVersion();
        Assert.assertEquals(item, MerkleTreeInstance.getShimInstance().getObject(0));
        byte []hash0 = MerkleTreeInstance.getShimInstance().getHash();
        MerkleTreeInstance.getShimInstance().takeSnapshot("tmp/snap", 0);
        MerkleTreeInstance.getShimInstance().setVersionNo(1);
        item.setValue(2);
        item.createName();
        String name=item.getName();
        MerkleTreeInstance.getShimInstance().writeLog(
                new ObjectOutputStream(new FileOutputStream("tmp/log1")), 1);
        byte []hash1 = MerkleTreeInstance.getShimInstance().getHash();
        MerkleTreeInstance.getShimInstance().setVersionNo(2);
        item.setValue(3);
        byte []hash2 = MerkleTreeInstance.getShimInstance().getHash();
	
        byte []state=MerkleTreeInstance.getShimInstance().fetchStates(1, 2);

        MerkleTreeInstance.clear();
        MerkleTreeInstance.getShimInstance().loadSnapshot("tmp/snap");
        MerkleTreeInstance.getShimInstance().readLog(new ObjectInputStream(new FileInputStream("tmp/log1")));
        MerkleTreeInstance.getShimInstance().mergeStates(2, state);

        EncapsulatedData item2 = (EncapsulatedData)MerkleTreeInstance.getShimInstance().getObject(0);

        Assert.assertEquals(3, item2.getValue());
        Assert.assertEquals(name, item2.getName());
	
        Assert.assertTrue(Arrays.equals(hash2, MerkleTreeInstance.getShimInstance().getHash()));

        MerkleTreeInstance.getShimInstance().rollBack(1);
        Assert.assertEquals(2, item2.getValue());
        Assert.assertEquals(name, item2.getName());
        Assert.assertTrue(Arrays.equals(hash1, MerkleTreeInstance.getShimInstance().getHash()));

        MerkleTreeInstance.getShimInstance().rollBack(0);
        Assert.assertEquals(0, item2.getValue());
        Assert.assertEquals(null, item2.getName());
        Assert.assertTrue(Arrays.equals(hash0, MerkleTreeInstance.getShimInstance().getHash()));

        //Test new add after deserialization
        EncapsulatedData newOne = new EncapsulatedData();
        MerkleTreeInstance.getShimInstance().finishThisVersion();
        Assert.assertEquals(newOne, MerkleTreeInstance.getShimInstance().getObject(1));
    }
}
