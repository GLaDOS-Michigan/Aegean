/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package merklePB;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yangwang
 */
public interface MerkleTreeSerializable extends MerkleTreeObject, MerkleTreeCloneable {

    /**
     * Write the current object into the output stream. For children references, usually
     * it should write the index of that object, unless it is DirectSerializable
     *
     * @param out:  the output stream.
     * @param tree: the Merkle tree to get the index of the an object
     * @throws IOException
     * @throws IllegalAccessException
     */
    public void writeObject(ObjectOutputStream out, MerkleTree tree)
            throws IOException, IllegalAccessException;

    /**
     * Read the object from the input stream. In this step, it should only read basic datas,
     * like integer, long, etc and put index of objects into the refs list.
     *
     * @param in:   the input stream
     * @param refs: the reference list (output)
     * @throws IOException
     * @throws IllegalAccessException
     * @throws ClassNotFoundException
     * @throws InstantiationException
     * @throws InvocationTargetException
     */
    public void readObject(ObjectInputStream in, ArrayList<Object> refs)
            throws IOException, IllegalAccessException, ClassNotFoundException, InstantiationException, InvocationTargetException;

    /**
     * Connect the references to real objects.
     *
     * @param tree: the merkle tree to get object from
     * @param refs: the reference list (input)
     * @throws IllegalAccessException
     */
    public void connectObject(MerkleTree tree, List<Object> refs) throws IllegalAccessException;

    /**
     * Retrieve all the Non-null objects referenced by this object. It is used in gc to determine which objects are not referenced any more.
     *
     * @return A list of objects referenced by this object
     */
    public List<Object> getAllReferences() throws IOException, IllegalAccessException;

}
