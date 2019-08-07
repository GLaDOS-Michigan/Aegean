/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package merklePB2;

/**
 * @author yangwang
 */
public class MerkleTreeException extends Exception {

    public MerkleTreeException(String msg) {
        super(msg);
    }

    public MerkleTreeException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public MerkleTreeException(Throwable cause) {
        super(cause);
    }
}
