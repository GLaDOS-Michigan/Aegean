/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package merklePB2.wrapper;

import merkle.MerkleTree;
import merkle.MerkleTreeObjectImp;
import merkle.MerkleTreeSerializable;
import merkle.Tools;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yangwang
 */
public class MTArrayWrapper<T> extends MerkleTreeObjectImp implements MerkleTreeSerializable {

    private T target;

    private MTArrayWrapper() {
    }

    public MTArrayWrapper(T target) {
        this.target = target;
    }

    public T getArray() {
        return target;
    }

    public void setArray(T target) {
        this.target = target;
    }

    @Override
    public void copyObject(Object dst) throws IllegalAccessException, IOException, ClassNotFoundException, InstantiationException, InvocationTargetException {
        MTArrayWrapper dstWrapper = (MTArrayWrapper) dst;
        if (dstWrapper.target == null
                || Tools.getArraySize(dstWrapper.target) != Tools.getArraySize(this.target)) {
            dstWrapper.target = Tools.createArray(this.target.getClass().getComponentType(),
                    Tools.getArraySize(this.target));
        }
        Tools.copyObject(this.target, dstWrapper.target);
    }

    @Override
    public Object cloneObject() throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException, InvocationTargetException {
        T array = (T) Tools.cloneObject(this.target);
        return new MTArrayWrapper<T>(array);
    }

    public void connectObject(MerkleTree tree, List<Object> refs) throws IllegalAccessException {
        Tools.connectObject(this.target, tree, refs);
    }

    @Override
    public void readObject(ObjectInputStream in, ArrayList<Object> refs) throws IOException, IllegalAccessException, ClassNotFoundException, InstantiationException, InvocationTargetException {
        this.target = (T) Tools.readObject(in, refs);
    }

    @Override
    public void writeObject(ObjectOutputStream out, MerkleTree tree) throws IOException, IllegalAccessException {
        Tools.writeObject(out, this.target, tree);
    }

    @Override
    public List<Object> getAllReferences() throws IOException, IllegalAccessException {
        return Tools.getAllReferences(target);
    }
}
