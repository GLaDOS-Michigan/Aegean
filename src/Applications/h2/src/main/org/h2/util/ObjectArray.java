/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.util;

import merkle.MerkleTreeInstance;
import org.h2.constant.SysProperties;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;

/**
 * The object array is basically the same as ArrayList. It is a bit faster than
 * ArrayList in some versions of Java.
 *
 * @param <T> the element type
 */
public class ObjectArray<T> implements Iterable<T>, merkle.MerkleTreeObject, merkle.MerkleTreeCloneable {
    private static final int CAPACITY_INIT = 4, CAPACITY_SHRINK = 256;

    int size;
    private T[] data;
    private boolean addInTree;


    private int MTIdentifier = -1;

    public void setMTIdentifier(int identifier) {
        this.MTIdentifier = identifier;
    }

    public int getMTIdentifier() {
        return this.MTIdentifier;
    }

    public void copyObject(Object dst) {
        ObjectArray<T> target = (ObjectArray<T>) dst;
        target.size = this.size;
        target.data = this.data;
        target.addInTree = this.addInTree;
    }

    public Object cloneObject() {
        ObjectArray<T> target = new ObjectArray<T>();
        this.copyObject(target);
        return target;
    }


    public ObjectArray() {
    }

    private ObjectArray(boolean addInTree, int capacity) {
        this.addInTree = addInTree;
        if (addInTree) {
            MerkleTreeInstance.add(this);
        }
        data = createArray(capacity);
    }

    private ObjectArray(boolean addInTree, Collection<T> collection) {
        this.addInTree = addInTree;
        if (addInTree) {
            MerkleTreeInstance.add(this);
        }
        size = collection.size();
        data = createArray(size);
        Iterator<T> it = collection.iterator();
        for (int i = 0; i < size; i++) {
            data[i] = it.next();
        }
    }

    /**
     * Create a new object with the given initial capacity.
     *
     * @param capacity the initial capacity
     * @return the object
     */
    public static <T> ObjectArray<T> newInstance(boolean addInTree, int capacity) {
        return new ObjectArray<T>(addInTree, capacity);
    }

    /**
     * Create a new object with the given values.
     *
     * @param list the initial elements
     * @return the object
     */
    public static <T> ObjectArray<T> newInstance(boolean addInTree, T... list) {
        ObjectArray<T> t = new ObjectArray<T>(addInTree, CAPACITY_INIT);
        for (T x : list) {
            t.add(x);
        }
        return t;
    }

    /**
     * Create a new object with the default initial capacity.
     *
     * @return the object
     */
    public static <T> ObjectArray<T> newInstance(boolean addInTree) {
        return new ObjectArray<T>(addInTree, CAPACITY_INIT);
    }

    /**
     * Create a new object with all elements of the given collection.
     *
     * @param collection the collection with all elements
     * @return the object
     */
    public static <T> ObjectArray<T> newInstance(boolean addInTree,
                                                 Collection<T> collection) {
        return new ObjectArray<T>(addInTree, collection);
    }

    @SuppressWarnings("unchecked")
    private T[] createArray(int capacity) {
        T[] t = (T[]) new Object[capacity > 1 ? capacity : 1];
        if (addInTree) {
            MerkleTreeInstance.add(t);
        }
        return t;
    }

    private void throwException(int index) {
        throw new ArrayIndexOutOfBoundsException("i=" + index + " size=" + size);
    }

    /**
     * Append an object at the end of the list.
     *
     * @param value the value
     */
    public void add(T value) {
        if (size >= data.length) {
            ensureCapacity(size);
        }
        if (addInTree) {
            MerkleTreeInstance.update(data);
            MerkleTreeInstance.update(this);
        }
        data[size++] = value;
    }

    /**
     * Get the object at the given index.
     *
     * @param index the index
     * @return the value
     */
    public T get(int index) {
        if (SysProperties.CHECK2 && index >= size) {
            throwException(index);
        }
        return data[index];
    }

    /**
     * Remove the object at the given index.
     *
     * @param index the index
     * @return the removed object
     */
    public Object remove(int index) {
        // TODO performance: the app should (where possible)
        // remove from end to start, to avoid O(n^2)
        if (SysProperties.CHECK2 && index >= size) {
            throwException(index);
        }
        Object value = data[index];
        if (addInTree) {
            MerkleTreeInstance.update(data);
            MerkleTreeInstance.update(this);
        }
        System.arraycopy(data, index + 1, data, index, size - index - 1);
        size--;
        data[size] = null;
        // TODO optimization / lib: could shrink ObjectArray on element remove
        return value;
    }

    /**
     * Remove a number of elements from the given start and end index.
     *
     * @param from the start index
     * @param to   the end index
     */
    public void removeRange(int from, int to) {
        // throw new UnsupportedOperationException();
        if (SysProperties.CHECK2 && (to > size || from > to)) {
            throw new ArrayIndexOutOfBoundsException("to=" + to + " from="
                    + from + " size=" + size);
        }
        if (addInTree) {
            MerkleTreeInstance.update(data);
            MerkleTreeInstance.update(this);
        }
        System.arraycopy(data, to, data, from, size - to);
        size -= to - from;
        for (int i = size + (to - from) - 1; i >= size; i--) {
            data[i] = null;
        }
    }

    /**
     * Fill the list with empty elements until it reaches the given size.
     *
     * @param size the new size
     */
    public void setSize(int size) {
        ensureCapacity(size);
        if (addInTree) {
            MerkleTreeInstance.update(this);
        }
        this.size = size;
    }

    private void ensureCapacity(int i) {
        while (i >= data.length) {
            T[] d = createArray(Math.max(CAPACITY_INIT, data.length * 2));
            System.arraycopy(data, 0, d, 0, size);
            if (addInTree) {
                MerkleTreeInstance.update(this);
                MerkleTreeInstance.remove(data);
            }
            data = d;
            //MerkleTreeInstance.add(data);
        }
    }

    /**
     * Shrink the array to the required size.
     */
    public void trimToSize() {
        System.out.println("trimToSize: Here is the problem!!!");
        T[] d = createArray(size);
        System.arraycopy(data, 0, d, 0, size);
        if (addInTree) {
            MerkleTreeInstance.update(this);
            MerkleTreeInstance.remove(data);
        }

        data = d;
        //MerkleTreeInstance.add(data);
    }

    /**
     * Insert an element at the given position. The element at this position and
     * all elements with a higher index move one element.
     *
     * @param index the index where to insert the object
     * @param value the object to insert
     */
    public void add(int index, T value) {
        if (SysProperties.CHECK2 && index > size) {
            throwException(index);
        }
        ensureCapacity(size);
        if (index == size) {
            add(value);
        } else {
            if (addInTree) {
                MerkleTreeInstance.update(data);
                MerkleTreeInstance.update(this);
            }
            System.arraycopy(data, index, data, index + 1, size - index);
            data[index] = value;
            size++;
        }
    }

    /**
     * Update the object at the given index.
     *
     * @param index the index
     * @param value the new value
     */
    public void set(int index, T value) {
        if (SysProperties.CHECK2 && index >= size) {
            throwException(index);
        }
        /*if(index >= data.length)
		    System.out.println(index+" "+size+" "+data.length+" thisIndex"+MerkleTreeInstance.get().getIndex(this)+" dataIndex="+MerkleTreeInstance.get().getIndex(data));*/
        if (addInTree) {
            MerkleTreeInstance.update(data);
        }
        data[index] = value;
    }

    /**
     * Get the size of the list.
     *
     * @return the size
     */
    public int size() {
        return size;
    }

    /**
     * Convert this list to an array. The target array must be big enough.
     *
     * @param array the target array
     * @return the array
     */
    public T[] toArray(T[] array) {
        if (addInTree) {
            MerkleTreeInstance.update(array);
        }
        ObjectUtils.arrayCopy(data, array, size);
        return array;
    }

    /**
     * Remove all elements from the list.
     */
    public void clear() {
        if (addInTree) {
            MerkleTreeInstance.update(this);
        }
        if (data.length > CAPACITY_SHRINK) {
            data = createArray(CAPACITY_INIT);
        } else {
            if (addInTree) {
                MerkleTreeInstance.update(data);
            }
            for (int i = 0; i < size; i++) {
                data[i] = null;
            }
        }
        size = 0;
    }

    /**
     * Get the index of the given object, or -1 if not found.
     *
     * @param o the object to search
     * @return the index
     */
    public int indexOf(Object o) {
        for (int i = 0; i < size; i++) {
            if (data[i] == o) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Add all objects from the given list.
     *
     * @param list the list
     */
    public void addAll(ObjectArray<? extends T> list) {
        for (int i = 0; i < list.size; i++) {
            add(list.data[i]);
        }
    }

    private void swap(int l, int r) {
        if (addInTree) {
            MerkleTreeInstance.update(data);
        }
        T t = data[r];
        data[r] = data[l];
        data[l] = t;
    }

    /**
     * Sort the elements using the given comparator.
     *
     * @param comp the comparator
     */
    public void sort(Comparator<T> comp) {
        sort(comp, 0, size - 1);
    }

    /**
     * Sort using the quicksort algorithm.
     *
     * @param comp the comparator
     * @param l    the first element (left)
     * @param r    the last element (right)
     */
    private void sort(Comparator<T> comp, int left, int right) {
        if (addInTree) {
            MerkleTreeInstance.update(data);
        }
        T[] d = data;
        while (right - left > 12) {
            // randomized pivot to avoid worst case
            int i = RandomUtils.nextInt(right - left - 4) + left + 2;
            // d[left]: smallest, d[i]: highest, d[right]: median
            if (comp.compare(d[left], d[i]) > 0) {
                swap(left, i);
            }
            if (comp.compare(d[left], d[right]) > 0) {
                swap(left, right);
            }
            if (comp.compare(d[right], d[i]) > 0) {
                swap(right, i);
            }
            T p = d[right];
            i = left - 1;
            int j = right;
            while (true) {
                do {
                    ++i;
                } while (comp.compare(d[i], p) < 0);
                do {
                    --j;
                } while (comp.compare(d[j], p) > 0);
                if (i >= j) {
                    break;
                }
                swap(i, j);
            }
            swap(i, right);
            sort(comp, left, i - 1);
            left = i + 1;
        }
        for (int j, i = left + 1; i <= right; i++) {
            T t = data[i];
            for (j = i - 1; j >= left && (comp.compare(data[j], t) > 0); j--) {
                data[j + 1] = data[j];
            }
            data[j + 1] = t;
        }
    }

    /**
     * The iterator for this list.
     */
    class ObjectArrayIterator implements Iterator<T> {
        private int index;

        public boolean hasNext() {
            return index < size;
        }

        public T next() {
            return get(index++);
        }

        public void remove() {
            throw new RuntimeException();
        }
    }

    public Iterator<T> iterator() {
        return new ObjectArrayIterator();
    }

    public String toString() {
        StatementBuilder buff = new StatementBuilder("{");
        for (int i = 0; i < size; i++) {
            buff.appendExceptFirst(", ");
            T t = get(i);
            buff.append(t == null ? "" : t.toString());
        }
        return buff.append('}').toString();
    }

}
