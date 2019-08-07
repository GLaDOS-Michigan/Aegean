/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package merklePB;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

/**
 * @author yangwang
 */
public interface MerkleTreeCloneable {

    /**
     * Copy this object to dst.
     *
     * @param dst: the target obj to copy
     * @throws IllegalAccessException
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public void copyObject(Object dst) throws IllegalAccessException, IOException, ClassNotFoundException, InstantiationException, InvocationTargetException;

    /**
     * Copy the current object. Usually this should be a shallow copy, unless some
     * fields are DirectSerializable
     *
     * @return The copied object.
     * @throws IOException
     * @throws ClassNotFoundException
     * @throws InstantiationException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    public Object cloneObject()
            throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException, InvocationTargetException;


}
