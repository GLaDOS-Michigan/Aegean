/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package merklePB.wrapper;

import merkle.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * @author yangwang
 */
public class MTCollectionWrapper extends MerkleTreeObjectImp implements Collection, MerkleTreeSerializable {

    private Collection target;
    private boolean ordered;
    private boolean directSerializable;

    private MTCollectionWrapper() {
    }

    public MTCollectionWrapper(Collection target, boolean ordered, boolean directSerializable) {
        this.target = target;
        this.ordered = ordered;
        this.directSerializable = directSerializable;
    }

    public Object getObject() {
        return target;
    }

    public boolean add(Object o) {
        //System.out.println("Current is "+this);
        MerkleTreeInstance.update(this);
        return target.add(o);
    }

    public boolean addAll(Collection c) {
        MerkleTreeInstance.update(this);
        return target.addAll(c);
    }

    public void clear() {
        MerkleTreeInstance.update(this);
        target.clear();
    }

    public boolean contains(Object o) {
        return target.contains(o);
    }

    public boolean containsAll(Collection c) {
        return target.containsAll(c);
    }

    public boolean equals(Object o) {
        return target.equals(((MTCollectionWrapper) o).target);
    }

    public int hashCode() {
        return target.hashCode();
    }

    public boolean isEmpty() {
        return target.isEmpty();
    }

    public Iterator iterator() {
        return target.iterator();
    }

    public boolean remove(Object o) {
        MerkleTreeInstance.update(this);
        return target.remove(o);
    }

    public boolean removeAll(Collection c) {
        MerkleTreeInstance.update(this);
        return target.removeAll(c);
    }

    public boolean retainAll(Collection c) {
        return target.retainAll(c);
    }

    public int size() {
        return target.size();
    }

    public Object[] toArray() {
        return target.toArray();
    }

    public Object[] toArray(Object[] a) {
        return target.toArray(a);
    }

    @Override
    public void copyObject(Object dst) throws IllegalAccessException, IOException, ClassNotFoundException, InstantiationException, InvocationTargetException {
        MTCollectionWrapper dstWrapper = (MTCollectionWrapper) dst;
        dstWrapper.ordered = this.ordered;
        dstWrapper.directSerializable = this.directSerializable;
        if (dstWrapper.target == null) {
            dstWrapper.target = this.target.getClass().newInstance();//(Collection) Tools.createNewObject(this.target.getClass());
        } else
            dstWrapper.target.clear();
        for (Object obj : target) {
            dstWrapper.target.add(obj);
        }
    }

    @Override
    public Object cloneObject() throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException, InvocationTargetException {
        Collection newCol = this.target.getClass().newInstance();//(Collection) Tools.createNewObject(this.target.getClass());
        for (Object obj : target) {
            newCol.add(obj);
        }
        MTCollectionWrapper ret = new MTCollectionWrapper(newCol, ordered, directSerializable);
        return ret;
    }

    @Override
    public void connectObject(MerkleTree tree, List<Object> refs) throws IllegalAccessException {
        if (directSerializable)
            return;
        for (Object index : refs) {
            Object obj = tree.getObject((Integer) index);
            if (obj == null) {
                throw new RuntimeException("Object does not exist in tree");
            }
            this.target.add(obj);
        }
    }

    @Override
    public void readObject(ObjectInputStream in, ArrayList<Object> refs) throws IOException, IllegalAccessException, ClassNotFoundException, InstantiationException, InvocationTargetException {
        this.ordered = in.readBoolean();
        this.directSerializable = in.readBoolean();
        String className = in.readUTF();
        target = (Collection) Tools.createClass(className).newInstance();//(Collection) Tools.createNewObject(Tools.createClass(className));
        int size = in.readInt();
        for (int i = 0; i < size; i++) {
            if (!directSerializable)
                refs.add(in.readInt());
            else
                this.target.add(in.readObject());
        }
    }

    @Override
    public void writeObject(ObjectOutputStream out, MerkleTree tree) throws IOException, IllegalAccessException {
        out.writeBoolean(this.ordered);
        out.writeBoolean(this.directSerializable);
        out.writeUTF(target.getClass().getName());
        out.writeInt(this.target.size());
        if (!ordered) {
            if (!directSerializable) {
                ArrayList<Integer> tmp = new ArrayList<Integer>();
                for (Object obj : target) {
                    int index = tree.getIndex(obj);
                    if (index == -1)
                        throw new RuntimeException("Object is not found in tree " + obj.getClass());
                    tmp.add(index);
                }
                Integer[] tmp2 = new Integer[tmp.size()];
                tmp.toArray(tmp2);
                Arrays.sort(tmp2);

                for (Integer index : tmp2) {
                    out.writeInt(index.intValue());
                }
            } else {
                ArrayList<Object> tmp = new ArrayList<Object>();
                for (Object obj : target) {
                    tmp.add(obj);
                }
                Object[] tmp2 = new Object[tmp.size()];
                tmp.toArray(tmp2);
                Arrays.sort(tmp2);

                for (Object obj : tmp2) {
                    out.writeObject(obj);
                }
            }

        } else {
            if (!directSerializable) {
                for (Object obj : target) {
                    int index = tree.getIndex(obj);
                    if (index == -1)
                        throw new RuntimeException("Object is not found in tree " + target.getClass() + " " + obj.getClass());
                    out.writeInt(index);
                }
            } else {
                for (Object obj : target) {
                    out.writeObject(obj);
                }
            }
        }
    }

    @Override
    public List<Object> getAllReferences() {
        ArrayList<Object> ret = new ArrayList<Object>();
        if (this.directSerializable)
            return ret;
        ret.addAll(target);
        return ret;
    }
}
