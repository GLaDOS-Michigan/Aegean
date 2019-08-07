/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package merkle;

/**
 *
 * @author yangwang
 */
public class EncapsulatedData {

    private int value;

    private String name;

    public int getValue(){
        return this.value;
    }

    public String getName(){
        return this.name;
    }

    public void setValue(int value){
        MerkleTreeInstance.update(this);
        this.value = value;
    }

    public void createName(){
        MerkleTreeInstance.update(this);
        name=new String("Haha");
        MerkleTreeInstance.add(name);
    }

    public EncapsulatedData(){
        MerkleTreeInstance.add(this);
    }
}
