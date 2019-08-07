/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package merkle;

/**
 *
 * @author yangwang
 */
public class TestDataChild extends TestData{
    //public TestDataChild(){}
    public TestDataChild(String str){
        MerkleTreeInstance.add(this);
        this.str = str;
    }
}
