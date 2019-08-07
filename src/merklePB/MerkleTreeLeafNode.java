package merklePB;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

class MerkleTreeLeafNode {

    private int createVersionNo;
    private int versionNo;
    private Object object;
    private byte flags = 0;
    private static final int IS_DELETED = 0;
    private static final int IS_STATIC = 1;
    private static final int IS_CURRENT = 2;
    private static final int IS_UPDATED = 3;


    public MerkleTreeLeafNode() {
        setUpdated(true);
    }

    public static MerkleTreeLeafNode newMerkleTreeLeafNode(MerkleTree tree, int i) {
        MerkleTreeLeafNode ret = new MerkleTreeLeafNode();
        ret.createVersionNo = tree.currentCreateVersionNos[i];
        ret.versionNo = tree.currentVersionNos[i];
        ret.object = tree.currentObjects[i];
        ret.flags = tree.currentFlags[i];
        return ret;
    }

    public void toCurrent(MerkleTree tree, int i) {
        tree.currentCreateVersionNos[i] = createVersionNo;
        tree.currentVersionNos[i] = versionNo;
        tree.currentObjects[i] = object;
        tree.currentFlags[i] = flags;
        setUpdated(tree, i, true);
        setCurrent(tree, i);
        if (isStatic() && object instanceof byte[]) {
            try {
                tree.currentObjects[i] = Tools.deserializeStatic((byte[]) object, tree);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public MerkleTreeLeafNode(long createVersioNo, long versionNo, Object object, boolean isStatic, boolean isCurrent) {
        this.createVersionNo = (int) createVersioNo;
        this.versionNo = (int) versionNo;
        this.object = object;
        setFlag(IS_STATIC, isStatic);
        setFlag(IS_CURRENT, isCurrent);
        setUpdated(true);
    }

    private static void setFlag(MerkleTree tree, int i, int index, boolean value) {
        if (!value) {
            tree.currentFlags[i] &= ~(1 << index);
        } else {
            tree.currentFlags[i] |= (1 << index);
        }
    }

    private void setFlag(int index, boolean value) {
        if (!value) {
            flags &= ~(1 << index);
        } else {
            flags |= (1 << index);
        }
    }

    private static boolean getFlag(MerkleTree tree, int i, int index) {
        return (tree.currentFlags[i] & (1 << index)) != 0;
    }

    private boolean getFlag(int index) {
        return (flags & (1 << index)) != 0;
    }

    public static boolean isStatic(MerkleTree tree, int i) {
        return getFlag(tree, i, IS_STATIC);
    }

    public boolean isStatic() {
        return getFlag(IS_STATIC);
    }

    public static boolean isCurrent(MerkleTree tree, int i) {
        return getFlag(tree, i, IS_CURRENT);
    }

    public boolean isCurrent() {
        return getFlag(IS_CURRENT);
    }

    public static void setCurrent(MerkleTree tree, int i) {
        setFlag(tree, i, IS_CURRENT, true);
    }

    public void setCurrent() {
        setFlag(IS_CURRENT, true);
    }

    public static void setStaticAsCurrent(MerkleTree tree, int i)
            throws IOException, IllegalAccessException, ClassNotFoundException, InstantiationException, InvocationTargetException {
        if (!isStatic(tree, i)) {
            throw new RuntimeException("Set non-static one as current");
        }
        if (isCurrent(tree, i)) {
            throw new RuntimeException("Set current one as current");
        }
        //System.out.println("setStaticAsCurrent "+index);
        setFlag(tree, i, IS_CURRENT, true);
        tree.currentObjects[i] = Tools.deserializeStatic((byte[]) tree.currentObjects[i], tree);
    }

    public void setStaticAsCurrent(MerkleTree tree)
            throws IOException, IllegalAccessException, ClassNotFoundException, InstantiationException, InvocationTargetException {
        if (!isStatic()) {
            throw new RuntimeException("Set non-static one as current");
        }
        if (isCurrent()) {
            throw new RuntimeException("Set current one as current");
        }
        //System.out.println("setStaticAsCurrent "+index);
        setFlag(IS_CURRENT, true);
        this.object = Tools.deserializeStatic((byte[]) object, tree);
    }

    public static void setStaticAsOld(MerkleTree tree, int i)
            throws IOException, IllegalAccessException, ClassNotFoundException, InstantiationException, InvocationTargetException {
        if (!isStatic(tree, i)) {
            throw new RuntimeException("Set non-static one as old");
        }
        if (!isCurrent(tree, i)) {
            throw new RuntimeException("Set old one as old");
        }
        setFlag(tree, i, IS_CURRENT, false);
        tree.currentObjects[i] = Tools.serializeStatic((Class) tree.currentObjects[i], tree);
    }

    public void setStaticAsOld(MerkleTree tree)
            throws IOException, IllegalAccessException, ClassNotFoundException, InstantiationException, InvocationTargetException {
        if (!isStatic()) {
            throw new RuntimeException("Set non-static one as old");
        }
        if (!isCurrent()) {
            throw new RuntimeException("Set old one as old");
        }
        setFlag(IS_CURRENT, false);
        this.object = Tools.serializeStatic((Class) object, tree);
    }


    public long getVersionNo() {
        return this.versionNo;
    }

    public long getCreateVersionNo() {
        return this.createVersionNo;
    }

    public void setVersionNo(long versionNo) {
        this.versionNo = (int) versionNo;
    }

    public Object getObject() {
        return this.object;
    }

    public void setObject(Object object) {
        if (object == null || this.isDeleted()) {
            throw new RuntimeException("Something weird happens");
        }
        this.object = object;
    }

    public static void setDeleted(MerkleTree tree, int i) {
        setFlag(tree, i, IS_DELETED, true);
    }

    public void setDeleted() {
        setFlag(IS_DELETED, true);
    }

    public static boolean isDeleted(MerkleTree tree, int i) {
        return getFlag(tree, i, IS_DELETED);
    }

    public boolean isDeleted() {
        return getFlag(IS_DELETED);
    }

    /*public void setHash(byte[] hash){
    this.hash = hash;
    }

    public byte[] getHash(){
    return this.hash;
    }*/

    public static boolean isUpdated(MerkleTree tree, int i) {
        return getFlag(tree, i, IS_UPDATED);
    }

    public boolean isUpdated() {
        return getFlag(IS_UPDATED);
    }

    public static void setUpdated(MerkleTree tree, int i, boolean updated) {
        setFlag(tree, i, IS_UPDATED, updated);
    }

    public void setUpdated(boolean updated) {
        setFlag(IS_UPDATED, updated);
    }

    public static void write(ObjectOutputStream out, MerkleTree tree, int i) throws IOException, IllegalAccessException {
        out.writeInt(tree.currentCreateVersionNos[i]);
        out.writeInt(tree.currentVersionNos[i]);
        out.writeByte(tree.currentFlags[i]);
        if (!isStatic(tree, i)) {
            Tools.writeObject(out, tree.currentObjects[i], tree);
        } else {
            byte[] data;
            if (isCurrent(tree, i)) {
                data = Tools.serializeStatic((Class) tree.currentObjects[i], tree);
            } else {
                data = (byte[]) tree.currentObjects[i];
            }
            out.writeInt(data.length);
            out.write(data);
        }
    }

    public void write(ObjectOutputStream out, MerkleTree tree) throws IOException, IllegalAccessException {
        out.writeInt(this.createVersionNo);
        out.writeInt(this.versionNo);
        out.writeByte(this.flags);
        if (!isStatic()) {
            Tools.writeObject(out, object, tree);
        } else {
            byte[] data;
            if (isCurrent()) {
                data = Tools.serializeStatic((Class) object, tree);
            } else {
                data = (byte[]) object;
            }
            out.writeInt(data.length);
            out.write(data);
        }
    }

    public List<Object> read(ObjectInputStream in)
            throws IOException, IllegalAccessException, ClassNotFoundException, InstantiationException, InvocationTargetException {
        this.createVersionNo = in.readInt();
        this.versionNo = in.readInt();
        this.flags = in.readByte();
        //System.out.println("read object "+index);
        ArrayList<Object> refs = new ArrayList<Object>();
        if (!isStatic()) {
            this.object = Tools.readObject(in, refs);
        } else {
            int size = in.readInt();
            object = new byte[size];
            in.readFully((byte[]) object);
        }
        return refs;
    }

    public static void connectObject(MerkleTree tree, int i, List<Object> refs)
            throws IOException, IllegalAccessException, ClassNotFoundException, InstantiationException, InvocationTargetException {
        if (!isStatic(tree, i)) {
            if (tree.currentObjects[i] != null) {
                Tools.connectObject(tree.currentObjects[i], tree, refs);
            }
        } else {
            if (isCurrent(tree, i)) {
                tree.currentObjects[i] = Tools.deserializeStatic((byte[]) tree.currentObjects[i], tree);
            }
        }
        refs.clear();
    }

    public void connectObject(MerkleTree tree, List<Object> refs)
            throws IOException, IllegalAccessException, ClassNotFoundException, InstantiationException, InvocationTargetException {
        if (!isStatic()) {
            if (object != null) {
                Tools.connectObject(object, tree, refs);
            }
        } else {
            if (isCurrent()) {
                this.object = Tools.deserializeStatic((byte[]) object, tree);
            }
        }
        refs.clear();
    }
}
