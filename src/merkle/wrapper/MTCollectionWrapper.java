/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package merkle.wrapper;

import merkle.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * @author yangwang
 */
public class MTCollectionWrapper<T>
        extends MerkleTreeObjectImp
        implements Collection<T>, MerkleTreeSerializable {

    private Collection<T> target;
    private boolean ordered;
    private boolean directSerializable;

    @SuppressWarnings("unused")
    // This default constructor is not explicitly called,
    // only for reflection constructing.
    private MTCollectionWrapper() {
    }

    public MTCollectionWrapper(Collection<T> target, boolean ordered, boolean directSerializable) {
        this.target = target;
        this.ordered = ordered;
        this.directSerializable = directSerializable;
    }


    // Hack, need random index somewhere.
    @SuppressWarnings("rawtypes")
    public Object elementAt(int index) {
        assert (target instanceof Vector);
        return ((Vector) target).elementAt(index);
    }

    public Collection<T> getObject() {
        return target;
    }

    public boolean add(T o) {
        //System.out.println("Current is "+this);
        MerkleTreeInstance.update(this);
        return target.add(o);
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
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

    public boolean containsAll(Collection<?> c) {
        return target.containsAll(c);
    }

    @SuppressWarnings("unchecked")
    public boolean equals(Object o) {
        return target.equals(((MTCollectionWrapper<T>) o).target);
    }

    public int hashCode() {
        return target.hashCode();
    }

    public boolean isEmpty() {
        return target.isEmpty();
    }

    public Iterator<T> iterator() {
        return target.iterator();
    }

    public boolean remove(Object o) {
        MerkleTreeInstance.update(this);
        return target.remove(o);
    }

    public boolean removeAll(Collection<?> c) {
        MerkleTreeInstance.update(this);
        return target.removeAll(c);
    }

    public boolean retainAll(Collection<?> c) {
        return target.retainAll(c);
    }

    public int size() {
        return target.size();
    }

    public Object[] toArray() {
        return target.toArray();
    }

    @SuppressWarnings("unchecked")
    public Object[] toArray(Object[] a) {
        return target.toArray(a);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void copyObject(Object dst) throws IllegalAccessException, IOException, ClassNotFoundException, InstantiationException, InvocationTargetException {
        MTCollectionWrapper<T> dstWrapper = (MTCollectionWrapper<T>) dst;
        dstWrapper.ordered = this.ordered;
        dstWrapper.directSerializable = this.directSerializable;
        if (dstWrapper.target == null) {
            dstWrapper.target = this.target.getClass().newInstance();//(Collection) Tools.createNewObject(this.target.getClass());
        } else
            dstWrapper.target.clear();
        for (T obj : target) {
            dstWrapper.target.add(obj);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object cloneObject() throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException, InvocationTargetException {
        Collection<T> newCol = this.target.getClass().newInstance();//(Collection) Tools.createNewObject(this.target.getClass());
        for (T obj : target) {
            newCol.add(obj);
        }
        MTCollectionWrapper<T> ret = new MTCollectionWrapper<T>(newCol, ordered, directSerializable);
        return ret;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void connectObject(MerkleTree tree, List<Object> refs) throws IllegalAccessException {
        if (directSerializable)
            return;
        for (Object index : refs) {
            Object obj = tree.getObject((Integer) index);
            if (obj == null) {
                throw new RuntimeException("Object does not exist in tree");
            }
            this.target.add((T) obj);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void readObject(ObjectInputStream in, ArrayList<Object> refs) throws IOException, IllegalAccessException, ClassNotFoundException, InstantiationException, InvocationTargetException {
        this.ordered = in.readBoolean();
        this.directSerializable = in.readBoolean();
        String className = in.readUTF();
        target = (Collection<T>) Tools.createClass(className).newInstance();//(Collection) Tools.createNewObject(Tools.createClass(className));
        int size = in.readInt();
        for (int i = 0; i < size; i++) {
            if (!directSerializable)
                refs.add(in.readInt());
            else
                this.target.add((T) (in.readObject()));
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
