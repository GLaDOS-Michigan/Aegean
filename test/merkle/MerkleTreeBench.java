/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package merkle;

/**
 *
 * @author yangwang
 */
public class MerkleTreeBench {
    private static int round=10000;
    public static void testAddRemove() throws Exception{

        DataItem array[]=new DataItem[200];
        for(int j=0;j<array.length;j++){
             array[j]=new DataItem();
        }
        long startTime=System.currentTimeMillis();
        for(int i=0;i<round;i++){
            MerkleTreeInstance.getShimInstance().setVersionNo(2*i);
	    MerkleTreeInstance.getShimInstance().makeStable(2*i-1);
            for(int j=0;j<array.length;j++){
                MerkleTreeInstance.add(array[j]);
            }
            MerkleTreeInstance.getShimInstance().setVersionNo(2*i+1);
	    MerkleTreeInstance.getShimInstance().makeStable(2*i);
            for(int j=0;j<array.length;j++){
                MerkleTreeInstance.remove(array[j]);
            }
        }
        long endTime=System.currentTimeMillis();
        System.out.println("Add/Remove Throughput="+round*array.length/(endTime-startTime));
    }

    public static void main(String []args) throws Exception{
        testAddRemove();
    }
}
