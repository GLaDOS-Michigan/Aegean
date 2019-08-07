/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package merklePB2;

/**
 * @author yangwang
 */
public class MerkleTreeObjectImp implements MerkleTreeObject {

    private int MTIdentifier = -1;

    public void setMTIdentifier(int identifier) {
        this.MTIdentifier = identifier;
    }

    public int getMTIdentifier() {
        return this.MTIdentifier;
    }
}
