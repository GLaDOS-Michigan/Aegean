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
public class MTMapWrapper<K, V> extends MerkleTreeObjectImp implements Map<K, V>, MerkleTreeSerializable {
    private Map<K, V> target;
    private boolean ordered;
    private boolean keyDirectSerializable;
    private boolean valueDirectSerializable;

    @SuppressWarnings("unused")
    // This default constructor is not explicitly called,
    // only for reflection constructing.
    private MTMapWrapper() {
    }

    public MTMapWrapper(Map<K, V> target, boolean ordered, boolean keyDirectSerializable, boolean valueDirectSerializable) {
        this.target = target;
        this.ordered = ordered;
        this.keyDirectSerializable = keyDirectSerializable;
        this.valueDirectSerializable = valueDirectSerializable;
    }

    public Object getObject() {
        return target;
    }

    public int size() {
        return target.size();
    }

    public boolean isEmpty() {
        return target.isEmpty();
    }

    public boolean containsKey(Object key) {
        return target.containsKey(key);
    }

    public boolean containsValue(Object value) {
        return target.containsKey(value);
    }

    public V get(Object key) {
        return target.get(key);
    }

    public V put(K key, V value) {
        MerkleTreeInstance.update(this);
        return target.put(key, value);
    }

    public V remove(Object key) {
        MerkleTreeInstance.update(this);
        return target.remove(key);
    }

    public void putAll(Map<? extends K, ? extends V> m) {
        MerkleTreeInstance.update(this);
        target.putAll(m);
    }

    public void clear() {
        MerkleTreeInstance.update(this);
        target.clear();
    }

    public Set<K> keySet() {
        return target.keySet();
    }

    public Collection<V> values() {
        return target.values();
    }

    public Set<Entry<K, V>> entrySet() {
        return target.entrySet();
    }

    @SuppressWarnings("unchecked")
    public boolean equals(Object o) {
        return target.equals(((MTMapWrapper<K, V>) o).target);
    }

    public int hashCode() {
        return target.hashCode();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void copyObject(Object dst) throws IllegalAccessException, IOException, ClassNotFoundException, InstantiationException, InvocationTargetException {
        MTMapWrapper<K, V> dstWrapper = (MTMapWrapper<K, V>) dst;
        dstWrapper.ordered = this.ordered;
        dstWrapper.valueDirectSerializable = this.valueDirectSerializable;
        dstWrapper.keyDirectSerializable = this.keyDirectSerializable;
        if (dstWrapper.target == null) {
            dstWrapper.target = this.target.getClass().newInstance();//(Map) Tools.createNewObject(this.target.getClass());
        } else
            dstWrapper.target.clear();
        for (K key : target.keySet()) {
            dstWrapper.target.put(key, target.get(key));
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object cloneObject() throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException, InvocationTargetException {
        Map<K, V> newMap = this.target.getClass().newInstance();//(Map) Tools.createNewObject(this.target.getClass());
        for (K key : target.keySet()) {
            newMap.put(key, target.get(key));
        }
        MTMapWrapper<K, V> ret = new MTMapWrapper<K, V>(newMap, ordered, keyDirectSerializable, valueDirectSerializable);
        return ret;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void connectObject(MerkleTree tree, List<Object> refs) throws IllegalAccessException {
        if (keyDirectSerializable && valueDirectSerializable)
            return;

        Iterator<Object> iter = refs.iterator();
        while (iter.hasNext()) {
            Object key = iter.next();
            Object value = iter.next();
            if (!keyDirectSerializable)
                key = tree.getObject((Integer) key); // Hack
            if (!valueDirectSerializable)
                value = tree.getObject((Integer) value); // Hack !!!!!!!!!!!
            target.put((K) key, (V) value);
        }

    }

    @SuppressWarnings("unchecked")
    @Override
    public void readObject(ObjectInputStream in, ArrayList<Object> refs) throws IOException, IllegalAccessException, ClassNotFoundException, InstantiationException, InvocationTargetException {
        this.ordered = in.readBoolean();
        this.keyDirectSerializable = in.readBoolean();
        this.valueDirectSerializable = in.readBoolean();
        String className = in.readUTF();
        target = (Map<K, V>) Tools.createClass(className).newInstance();
        int size = in.readInt();
        for (int i = 0; i < size; i++) {
            if (keyDirectSerializable && valueDirectSerializable) {
                target.put((K) (in.readObject()), (V) (in.readObject()));
            } else {
                if (keyDirectSerializable)
                    refs.add(in.readObject());
                else
                    refs.add(in.readInt());
                if (valueDirectSerializable)
                    refs.add(in.readObject());
                else
                    refs.add(in.readInt());
            }
        }
    }

    @Override
    public void writeObject(ObjectOutputStream out, MerkleTree tree) throws IOException, IllegalAccessException {
        out.writeBoolean(this.ordered);
        out.writeBoolean(this.keyDirectSerializable);
        out.writeBoolean(this.valueDirectSerializable);
        out.writeUTF(target.getClass().getName());
        out.writeInt(this.target.size());
        if (!ordered) {
            if (keyDirectSerializable) {
                TreeMap<Object, Object> tmp = new TreeMap<Object, Object>(target);
                for (Object key : tmp.keySet()) {
                    out.writeObject(key);
                    if (valueDirectSerializable)
                        out.writeObject(tmp.get(key));
                    else {
                        int index = tree.getIndex(tmp.get(key));
                        if (index == -1)
                            throw new RuntimeException("Object not found in tree");
                        out.writeInt(index);
                    }
                }
            } else {
                TreeMap<Integer, Object> tmp = new TreeMap<Integer, Object>();
                for (Object key : target.keySet()) {
                    int index = tree.getIndex(key);
                    if (index == -1)
                        throw new RuntimeException("Object not found in tree");
                    tmp.put(index, target.get(key));
                }
                for (Integer key : tmp.keySet()) {
                    out.writeInt(key);
                    if (valueDirectSerializable)
                        out.writeObject(tmp.get(key));
                    else {
                        int index = tree.getIndex(tmp.get(key));
                        if (index == -1)
                            throw new RuntimeException("Object not found in tree");
                        out.writeInt(index);
                    }
                }
            }


        } else {
            for (Object key : target.keySet()) {
                if (keyDirectSerializable)
                    out.writeObject(key);
                else {
                    int index = tree.getIndex(key);
                    if (index == -1)
                        throw new RuntimeException("Object not found in tree");
                    out.writeInt(index);
                }
                if (valueDirectSerializable)
                    out.writeObject(target.get(key));
                else {
                    int index = tree.getIndex(target.get(key));
                    if (index == -1)
                        throw new RuntimeException("Object not found in tree");
                    out.writeInt(index);
                }
            }
        }
    }

    @Override
    public List<Object> getAllReferences() {
        ArrayList<Object> ret = new ArrayList<Object>();
        if (!this.keyDirectSerializable)
            ret.addAll(target.keySet());
        if (!this.valueDirectSerializable)
            ret.addAll(target.values());
        return ret;
    }
}
