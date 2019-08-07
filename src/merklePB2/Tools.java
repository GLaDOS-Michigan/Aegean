package merklePB2;

import sun.reflect.ReflectionFactory;

import java.io.*;
import java.lang.reflect.*;
import java.security.AccessController;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class Tools {

    private static Hashtable<String, Class> classMap = new Hashtable<String, Class>();
    private static final ReflectionFactory reflFactory =
            (ReflectionFactory) AccessController.doPrivileged(
                    new ReflectionFactory.GetReflectionFactoryAction());

    public static Class createClass(String className) throws ClassNotFoundException {
        if (classMap.size() == 0) {
            classMap.put("int", int.class);
            classMap.put("long", long.class);
            classMap.put("short", short.class);
            classMap.put("char", char.class);
            classMap.put("byte", byte.class);
            classMap.put("boolean", boolean.class);
            classMap.put("float", float.class);
            classMap.put("double", double.class);
        }
        if (classMap.containsKey(className)) {
            return classMap.get(className);
        } else {
            return Class.forName(className);
        }
    }

    public static void copyObject(Object src, Object dst) throws IllegalAccessException, IOException, ClassNotFoundException, InstantiationException, InvocationTargetException {
        if (src instanceof MerkleTreeCloneable) {
            ((MerkleTreeCloneable) src).copyObject(dst);
            return;
        }
        if (!src.getClass().isArray()) {
            Field[] fieldSrc = getAllFields(src.getClass());//src.getClass().getDeclaredFields();
            Field[] fieldDst = getAllFields(dst.getClass());//src.getClass().getDeclaredFields();
            if (fieldSrc.length != fieldDst.length) {
                throw new RuntimeException("Unmatched fields of " + src + " and " + dst);
            }
            for (int i = 0; i < fieldSrc.length; i++) {
                fieldSrc[i].setAccessible(true);
                fieldDst[i].setAccessible(true);
                if (Modifier.isStatic(fieldSrc[i].getModifiers())
                        || Modifier.isTransient(fieldSrc[i].getModifiers())) {
                    continue;
                }
                if (fieldSrc[i].getType() == int.class) {
                    fieldDst[i].setInt(dst, fieldSrc[i].getInt(src));
                } else if (fieldSrc[i].getType() == long.class) {
                    fieldDst[i].setLong(dst, fieldSrc[i].getLong(src));
                } else if (fieldSrc[i].getType() == byte.class) {
                    fieldDst[i].setByte(dst, fieldSrc[i].getByte(src));
                } else if (fieldSrc[i].getType() == char.class) {
                    fieldDst[i].setChar(dst, fieldSrc[i].getChar(src));
                } else if (fieldSrc[i].getType() == short.class) {
                    fieldDst[i].setShort(dst, fieldSrc[i].getShort(src));
                } else if (fieldSrc[i].getType() == boolean.class) {
                    fieldDst[i].setBoolean(dst, fieldSrc[i].getBoolean(src));
                } else if (fieldSrc[i].getType() == float.class) {
                    fieldDst[i].setFloat(dst, fieldSrc[i].getFloat(src));
                } else if (fieldSrc[i].getType() == double.class) {
                    fieldDst[i].setDouble(dst, fieldSrc[i].getDouble(src));
                } else {
                    if (fieldSrc[i].getAnnotation(MerkleTreeDirectSerializable.class) == null) {
                        fieldDst[i].set(dst, fieldSrc[i].get(src));
                    } else {
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        ObjectOutputStream oos = new ObjectOutputStream(bos);
                        oos.writeObject(fieldSrc[i].get(src));
                        oos.flush();
                        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
                        ObjectInputStream ois = new ObjectInputStream(bis);
                        fieldDst[i].set(dst, ois.readObject());

                    }

                }
            }
        } else {
            System.arraycopy(src, 0, dst, 0, getArraySize(src));
        }
    }

    private static ConcurrentHashMap<Class, Field[]> fieldMap = new ConcurrentHashMap<Class, Field[]>();

    private static Field[] getAllFields(Class cls) {
        Field[] exist = fieldMap.get(cls);
        if (exist != null) {
            return exist;
        }
        ArrayList<Field> ret = new ArrayList<Field>();
        Class tmp = cls;
        while (cls != Object.class) {
            Field[] fields = cls.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                ret.add(fields[i]);
            }
            cls = cls.getSuperclass();
        }
        Field[] ret2 = new Field[ret.size()];
        ret.toArray(ret2);
        fieldMap.put(tmp, ret2);
        return ret2;

    }

    public static void writeObject(ObjectOutputStream out, Object obj, MerkleTree tree)
            throws IOException, IllegalAccessException {
        if (obj == null) {
            out.writeUTF("null");
            return;
        }
        String className = obj.getClass().getName();
        out.writeUTF(className);
        if (obj instanceof String) {
            out.writeUTF((String) obj);
            return;
        } else if (obj instanceof MerkleTreeSerializable) {
            ((MerkleTreeSerializable) obj).writeObject(out, tree);
            return;
        }
        if (!obj.getClass().isArray()) {
            Field[] field = getAllFields(obj.getClass());//obj.getClass().getDeclaredFields();
            for (int i = 0; i < field.length; i++) {
                field[i].setAccessible(true);
                if (Modifier.isStatic(field[i].getModifiers())
                        || Modifier.isTransient(field[i].getModifiers())) {
                    continue;
                }
                if (field[i].getType() == int.class) {
                    out.writeInt(field[i].getInt(obj));
                } else if (field[i].getType() == long.class) {
                    out.writeLong(field[i].getLong(obj));
                } else if (field[i].getType() == byte.class) {
                    out.writeByte(field[i].getByte(obj));
                } else if (field[i].getType() == char.class) {
                    out.writeChar(field[i].getChar(obj));
                } else if (field[i].getType() == short.class) {
                    out.writeShort(field[i].getShort(obj));
                } else if (field[i].getType() == boolean.class) {
                    out.writeBoolean(field[i].getBoolean(obj));
                } else if (field[i].getType() == float.class) {
                    out.writeFloat(field[i].getFloat(obj));
                } else if (field[i].getType() == double.class) {
                    out.writeDouble(field[i].getDouble(obj));
                } else if (field[i].getType().isEnum()) {
                    out.writeObject(field[i].get(obj));
                } else {
                    Object ref = field[i].get(obj);
                    if (ref == null) {
                        out.writeInt(-1);
                    } else if (field[i].getAnnotation(MerkleTreeDirectSerializable.class) != null) {
                        out.writeInt(0);
                        out.writeObject(ref);
                    } else {
                        int index = tree.getIndex(ref);
                        if (index == -1) {
                            throw new RuntimeException("Missing reference parent=("
                                    + obj.getClass() + "," + obj + ") ref=" + field[i].getName());
                        }
                        out.writeInt(index);
                    }
                }
            }
        } else {

            if (obj.getClass().getComponentType() == int.class) {
                int[] tmp = (int[]) obj;
                out.writeInt(tmp.length);
                for (int i = 0; i < tmp.length; i++) {
                    out.writeInt(tmp[i]);
                }
            } else if (obj.getClass().getComponentType() == long.class) {
                long[] tmp = (long[]) obj;
                out.writeInt(tmp.length);
                for (int i = 0; i < tmp.length; i++) {
                    out.writeLong(tmp[i]);
                }
            } else if (obj.getClass().getComponentType() == byte.class) {
                byte[] tmp = (byte[]) obj;
                out.writeInt(tmp.length);
                out.write(tmp);
            } else if (obj.getClass().getComponentType() == char.class) {
                char[] tmp = (char[]) obj;
                out.writeInt(tmp.length);
                for (int i = 0; i < tmp.length; i++) {
                    out.writeChar(tmp[i]);
                }
            } else if (obj.getClass().getComponentType() == short.class) {
                short[] tmp = (short[]) obj;
                out.writeInt(tmp.length);
                for (int i = 0; i < tmp.length; i++) {
                    out.writeShort(tmp[i]);
                }
            } else if (obj.getClass().getComponentType() == boolean.class) {
                boolean[] tmp = (boolean[]) obj;
                out.writeInt(tmp.length);
                for (int i = 0; i < tmp.length; i++) {
                    out.writeBoolean(tmp[i]);
                }
            } else if (obj.getClass().getComponentType() == float.class) {
                float[] tmp = (float[]) obj;
                out.writeInt(tmp.length);
                for (int i = 0; i < tmp.length; i++) {
                    out.writeFloat(tmp[i]);
                }
            } else if (obj.getClass().getComponentType() == double.class) {
                double[] tmp = (double[]) obj;
                out.writeInt(tmp.length);
                for (int i = 0; i < tmp.length; i++) {
                    out.writeDouble(tmp[i]);
                }
            } else if (obj.getClass().getComponentType().isEnum()) {
                Object[] tmp = (Object[]) obj;
                out.writeInt(tmp.length);
                for (int i = 0; i < tmp.length; i++) {
                    out.writeObject(tmp[i]);
                }
            } else {
                Object[] tmp = (Object[]) obj;
                out.writeInt(tmp.length);
                for (int i = 0; i < tmp.length; i++) {
                    Object ref = tmp[i];
                    if (ref == null) {
                        out.writeInt(-1);
                    } else {
                        int index = tree.getIndex(ref);
                        if (index == -1) {
                            throw new RuntimeException("Missing reference parent=("
                                    + obj.getClass() + "," + obj + ") element[" + i + "]");
                        }
                        out.writeInt(index);
                    }
                }
            }
        }
    }

    public static void printObject(Object obj, MerkleTree tree) {
        try {
            if (obj == null) {
                System.err.println("null");
                return;
            }
            String className = obj.getClass().getName();
            System.err.println(className);
            if (obj instanceof MerkleTreeSerializable) {
                return;
            }
            if (obj instanceof String) {
                System.err.println((String) obj);
                return;
            }
            if (!obj.getClass().isArray()) {
                Field[] field = getAllFields(obj.getClass());//obj.getClass().getDeclaredFields();
                for (int i = 0; i < field.length; i++) {
                    field[i].setAccessible(true);
                    if (Modifier.isStatic(field[i].getModifiers())
                            || Modifier.isTransient(field[i].getModifiers())) {
                        continue;
                    }
                    if (field[i].getType() == int.class) {
                        System.err.println(field[i].getName() + ":" + field[i].getInt(obj));
                    } else if (field[i].getType() == long.class) {
                        System.err.println(field[i].getName() + ":" + field[i].getLong(obj));
                    } else if (field[i].getType() == byte.class) {
                        System.err.println(field[i].getName() + ":" + field[i].getByte(obj));
                    } else if (field[i].getType() == char.class) {
                        System.err.println(field[i].getName() + ":" + field[i].getChar(obj));
                    } else if (field[i].getType() == short.class) {
                        System.err.println(field[i].getName() + ":" + field[i].getShort(obj));
                    } else if (field[i].getType() == boolean.class) {
                        System.err.println(field[i].getName() + ":" + field[i].getBoolean(obj));
                    } else if (field[i].getType() == float.class) {
                        System.err.println(field[i].getName() + ":" + field[i].getFloat(obj));
                    } else if (field[i].getType() == double.class) {
                        System.err.println(field[i].getName() + ":" + field[i].getDouble(obj));
                    } else if (field[i].getType().isEnum()) {
                        System.err.println(field[i].getName() + ":" + field[i].get(obj));
                    } else {
                        Object ref = field[i].get(obj);
                        if (ref == null) {
                            System.err.println(field[i].getName() + ":" + -1);
                        } else if (field[i].getAnnotation(MerkleTreeDirectSerializable.class) != null) {
                            System.err.println(field[i].getName() + ": DirectSerializable " + ref);
                        } else {
                            int index = tree.getIndex(ref);
                            if (index == -1) {
                                throw new RuntimeException("Missing reference parent=("
                                        + obj.getClass() + "," + obj + ") ref=" + field[i].getName());
                            }
                            System.err.println(field[i].getName() + ": ref" + index);
                        }
                    }
                }
            } else {
                if (obj.getClass().getComponentType() == int.class) {
                    int[] tmp = (int[]) obj;
                    System.err.println("array length:" + tmp.length);
                    for (int i = 0; i < tmp.length; i++) {
                        System.err.print(tmp[i] + " ");
                    }
                    System.err.println();
                } else if (obj.getClass().getComponentType() == long.class) {
                    long[] tmp = (long[]) obj;
                    System.err.println("array length:" + tmp.length);
                    for (int i = 0; i < tmp.length; i++) {
                        System.err.print(tmp[i] + " ");
                    }
                    System.err.println();
                } else if (obj.getClass().getComponentType() == byte.class) {
                    byte[] tmp = (byte[]) obj;
                    for (int i = 0; i < tmp.length; i++) {
                        System.err.print(tmp[i] + " ");
                    }
                    System.err.println();
                } else if (obj.getClass().getComponentType() == char.class) {
                    char[] tmp = (char[]) obj;
                    System.err.println("array length:" + tmp.length);
                    for (int i = 0; i < tmp.length; i++) {
                        System.err.print(tmp[i] + " ");
                    }
                    System.err.println();
                } else if (obj.getClass().getComponentType() == short.class) {
                    short[] tmp = (short[]) obj;
                    System.err.println("array length:" + tmp.length);
                    for (int i = 0; i < tmp.length; i++) {
                        System.err.print(tmp[i] + " ");
                    }
                    System.err.println();
                } else if (obj.getClass().getComponentType() == boolean.class) {
                    boolean[] tmp = (boolean[]) obj;
                    System.err.println("array length:" + tmp.length);
                    for (int i = 0; i < tmp.length; i++) {
                        System.err.print(tmp[i] + " ");
                    }
                    System.err.println();
                } else if (obj.getClass().getComponentType() == float.class) {
                    float[] tmp = (float[]) obj;
                    System.err.println("array length:" + tmp.length);
                    for (int i = 0; i < tmp.length; i++) {
                        System.err.print(tmp[i] + " ");
                    }
                    System.err.println();
                } else if (obj.getClass().getComponentType() == double.class) {
                    double[] tmp = (double[]) obj;
                    System.err.println("array length:" + tmp.length);
                    for (int i = 0; i < tmp.length; i++) {
                        System.err.print(tmp[i] + " ");
                    }
                    System.err.println();
                } else if (obj.getClass().getComponentType().isEnum()) {
                    Object[] tmp = (Object[]) obj;
                    System.err.println("array length:" + tmp.length);
                    for (int i = 0; i < tmp.length; i++) {
                        System.err.print(tmp[i] + " ");
                    }
                    System.err.println();
                } else {
                    Object[] tmp = (Object[]) obj;
                    System.err.println("array length:" + tmp.length);
                    for (int i = 0; i < tmp.length; i++) {
                        Object ref = tmp[i];
                        if (ref == null) {
                            System.err.print(-1 + " ");
                        } else {
                            int index = tree.getIndex(ref);
                            if (index == -1) {
                                throw new RuntimeException("Missing reference parent=("
                                        + obj.getClass() + "," + obj + ") elements[" + i + "]");
                            }

                            System.err.print("ref " + index + " ");
                        }
                        if (i % 10 == 9)
                            System.err.println();
                    }
                    System.err.println();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static List<Object> getAllReferences(Object obj)
            throws IOException, IllegalAccessException {
        ArrayList<Object> ret = new ArrayList<Object>();
        if (obj == null) {
            return null;
        }
        if (obj instanceof String) {
            return null;
        } else if (obj instanceof MerkleTreeSerializable) {
            return ((MerkleTreeSerializable) obj).getAllReferences();
        }
        if (!obj.getClass().isArray()) {
            if (!(obj instanceof Class)) {
                Field[] field = getAllFields(obj.getClass());//obj.getClass().getDeclaredFields();
                for (int i = 0; i < field.length; i++) {
                    field[i].setAccessible(true);
                    if (Modifier.isStatic(field[i].getModifiers())
                            || Modifier.isTransient(field[i].getModifiers())) {
                        continue;
                    }
                    if (!field[i].getType().isPrimitive() && !field[i].getType().isEnum() && field[i].getAnnotation(MerkleTreeDirectSerializable.class) == null) {
                        Object tmp = field[i].get(obj);
                        if (tmp != null) {
                            ret.add(tmp);
                        }
                    }
                }
            } else {
                Field[] field = getAllFields((Class) obj);//obj.getClass().getDeclaredFields();
                for (int i = 0; i < field.length; i++) {
                    field[i].setAccessible(true);
                    if (!Modifier.isStatic(field[i].getModifiers())
                            || Modifier.isTransient(field[i].getModifiers())
                            || Modifier.isFinal(field[i].getModifiers())) {
                        continue;
                    }
                    if (!field[i].getType().isPrimitive() && !field[i].getType().isEnum() && field[i].getAnnotation(MerkleTreeDirectSerializable.class) == null) {
                        Object tmp = field[i].get(null);
                        if (tmp != null) {
                            ret.add(tmp);
                        }
                    }
                }
            }
        } else {
            if (!obj.getClass().getComponentType().isPrimitive() && !obj.getClass().getComponentType().isEnum()) {
                Object[] tmp = (Object[]) obj;
                for (int i = 0; i < tmp.length; i++) {
                    if (tmp[i] != null) {
                        ret.add(tmp[i]);
                    }
                }
            }
        }
        return ret;
    }

    public static byte[] serializeStatic(Class cls, MerkleTree tree)
            throws IOException, IllegalAccessException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bos);
        String className = cls.getName();
        out.writeUTF(className);
        Field[] field = getAllFields(cls);//cls.getDeclaredFields();
        for (int i = 0; i < field.length; i++) {
            field[i].setAccessible(true);
            if (!Modifier.isStatic(field[i].getModifiers())
                    || Modifier.isTransient(field[i].getModifiers())) {
                continue;
            }
            if (Modifier.isFinal(field[i].getModifiers())) {
                continue;
            }
            if (field[i].getType() == int.class) {
                out.writeInt(field[i].getInt(null));
            } else if (field[i].getType() == long.class) {
                out.writeLong(field[i].getLong(null));
            } else if (field[i].getType() == byte.class) {
                out.writeByte(field[i].getByte(null));
            } else if (field[i].getType() == char.class) {
                out.writeChar(field[i].getChar(null));
            } else if (field[i].getType() == short.class) {
                System.out.println(field[i].getShort(null));
                out.writeShort(field[i].getShort(null));
            } else if (field[i].getType() == boolean.class) {
                out.writeBoolean(field[i].getBoolean(null));
            } else if (field[i].getType() == float.class) {
                out.writeFloat(field[i].getFloat(null));
            } else if (field[i].getType() == double.class) {
                out.writeDouble(field[i].getDouble(null));
            } else if (field[i].getType().isEnum()) {
                out.writeObject(field[i].get(null));
            } else {
                Object ref = field[i].get(null);
                if (ref == null) {
                    out.writeInt(-1);
                } else if (field[i].getAnnotation(MerkleTreeDirectSerializable.class) != null) {
                    out.writeInt(0);
                    out.writeObject(ref);
                } else {
                    int index = tree.getIndex(ref);
                    if (index == -1) {
                        throw new RuntimeException("Missing reference parent=("
                                + cls + ", static fields)" + " ref=" + field[i].getName());
                    }
                    out.writeInt(index);
                }
            }
        }
        out.flush();
        return bos.toByteArray();
    }

    public static Object readObject(ObjectInputStream in, ArrayList<Object> refs)
            throws IOException, IllegalAccessException, ClassNotFoundException, InstantiationException, InvocationTargetException {
        String className = in.readUTF();
        if (className.equals("null")) {
            return null;
        }
        Class cls = createClass(className);
        Object ret = null;
        if (cls == String.class) {
            ret = in.readUTF();
            return ret;
        } else if (!cls.isArray()) {
            ret = createNewObject(cls);
        } else {
            int size = in.readInt();
            ret = createArray(cls.getComponentType(), size);
        }
        if (ret instanceof MerkleTreeSerializable) {
            ((MerkleTreeSerializable) ret).readObject(in, refs);
            return ret;
        }
        if (!cls.isArray()) {
            Field[] field = getAllFields(cls);//cls.getDeclaredFields();
            for (int i = 0; i < field.length; i++) {
                field[i].setAccessible(true);
                if (Modifier.isStatic(field[i].getModifiers())
                        || Modifier.isTransient(field[i].getModifiers())) {
                    continue;
                }

                if (field[i].getType() == int.class) {
                    field[i].setInt(ret, in.readInt());
                } else if (field[i].getType() == long.class) {
                    field[i].setLong(ret, in.readLong());
                } else if (field[i].getType() == byte.class) {
                    field[i].setByte(ret, in.readByte());
                } else if (field[i].getType() == char.class) {
                    field[i].setChar(ret, in.readChar());
                } else if (field[i].getType() == short.class) {
                    field[i].setShort(ret, in.readShort());
                } else if (field[i].getType() == boolean.class) {
                    field[i].setBoolean(ret, in.readBoolean());
                } else if (field[i].getType() == float.class) {
                    field[i].setFloat(ret, in.readFloat());
                } else if (field[i].getType() == double.class) {
                    field[i].setDouble(ret, in.readDouble());
                } else if (field[i].getType().isEnum()) {
                    field[i].set(ret, in.readObject());
                } else {
                    if (field[i].getAnnotation(MerkleTreeDirectSerializable.class) != null) {
                        int tmp = in.readInt();
                        if (tmp != -1) {
                            field[i].set(ret, in.readObject());
                        } else {
                            field[i].set(ret, null);
                        }
                    } else {
                        refs.add(in.readInt());
                    }
                }
            }
        } else {
            if (cls.getComponentType() == int.class) {
                int[] tmp = (int[]) ret;
                for (int i = 0; i < tmp.length; i++) {
                    tmp[i] = in.readInt();
                }
            } else if (cls.getComponentType() == long.class) {
                long[] tmp = (long[]) ret;
                for (int i = 0; i < tmp.length; i++) {
                    tmp[i] = in.readLong();
                }
            } else if (cls.getComponentType() == byte.class) {
                byte[] tmp = (byte[]) ret;
                in.readFully(tmp);
                //for (int i = 0; i < tmp.length; i++)
                //    tmp[i] = in.readByte();
            } else if (cls.getComponentType() == char.class) {
                char[] tmp = (char[]) ret;
                for (int i = 0; i < tmp.length; i++) {
                    tmp[i] = in.readChar();
                }
            } else if (cls.getComponentType() == short.class) {
                short[] tmp = (short[]) ret;
                for (int i = 0; i < tmp.length; i++) {
                    tmp[i] = in.readShort();
                }
            } else if (cls.getComponentType() == boolean.class) {
                boolean[] tmp = (boolean[]) ret;
                for (int i = 0; i < tmp.length; i++) {
                    tmp[i] = in.readBoolean();
                }
            } else if (cls.getComponentType() == float.class) {
                float[] tmp = (float[]) ret;
                for (int i = 0; i < tmp.length; i++) {
                    tmp[i] = in.readFloat();
                }
            } else if (cls.getComponentType() == double.class) {
                double[] tmp = (double[]) ret;
                for (int i = 0; i < tmp.length; i++) {
                    tmp[i] = in.readDouble();
                }
            } else if (cls.getComponentType().isEnum()) {
                Object[] tmp = (Object[]) ret;
                for (int i = 0; i < tmp.length; i++) {
                    tmp[i] = in.readObject();
                }
            } else {
                Object[] tmp = (Object[]) ret;
                for (int i = 0; i < tmp.length; i++) {
                    refs.add(in.readInt());
                }
            }
        }
        return ret;
    }

    public static Class deserializeStatic(byte[] data, MerkleTree tree)
            throws IOException, IllegalAccessException, ClassNotFoundException, InstantiationException, InvocationTargetException {
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        ObjectInputStream in = new ObjectInputStream(bis);
        String className = in.readUTF();
        Class cls = createClass(className);
        Field[] field = getAllFields(cls);//cls.getDeclaredFields();
        for (int i = 0; i < field.length; i++) {
            field[i].setAccessible(true);
            if (!Modifier.isStatic(field[i].getModifiers())
                    || Modifier.isTransient(field[i].getModifiers())) {
                continue;
            }
            if (Modifier.isFinal(field[i].getModifiers())) {
                continue;
            }

            if (field[i].getType() == int.class) {
                int value = in.readInt();
                field[i].setInt(null, value);
            } else if (field[i].getType() == long.class) {
                field[i].setLong(null, in.readLong());
            } else if (field[i].getType() == byte.class) {
                field[i].setByte(null, in.readByte());
            } else if (field[i].getType() == char.class) {
                field[i].setChar(null, in.readChar());
            } else if (field[i].getType() == short.class) {
                field[i].setShort(null, in.readShort());
            } else if (field[i].getType() == boolean.class) {
                field[i].setBoolean(null, in.readBoolean());
            } else if (field[i].getType() == float.class) {
                field[i].setFloat(null, in.readFloat());
            } else if (field[i].getType() == double.class) {
                field[i].setDouble(null, in.readDouble());
            } else if (field[i].getType().isEnum()) {
                field[i].set(null, in.readObject());
            } else {
                int index = in.readInt();
                if (index != -1) {
                    if (field[i].getAnnotation(MerkleTreeDirectSerializable.class) != null) {
                        field[i].set(null, in.readObject());
                    } else {
                        Object target = tree.getObject(index);
                        field[i].set(null, target);
                    }
                } else {
                    field[i].set(null, null);
                }

            }
        }
        return cls;
    }

    public static void connectObject(Object obj, MerkleTree tree, List<Object> refs) throws IllegalAccessException {
        if (obj instanceof MerkleTreeSerializable) {
            ((MerkleTreeSerializable) obj).connectObject(tree, refs);
            return;
        } else if (obj instanceof String) {
            return;
        }
        Iterator<Object> iter = refs.iterator();
        if (!obj.getClass().isArray()) {
            Field[] field = getAllFields(obj.getClass());//obj.getClass().getDeclaredFields();
            for (int i = 0; i < field.length; i++) {
                field[i].setAccessible(true);
                if (Modifier.isStatic(field[i].getModifiers())
                        || Modifier.isTransient(field[i].getModifiers())) {
                    continue;
                }
                if (!field[i].getType().isPrimitive() && !field[i].getType().isEnum()
                        && field[i].getAnnotation(MerkleTreeDirectSerializable.class) == null) {
                    int index = (Integer) iter.next();
                    if (index != -1) {
                        Object target = tree.getObject(index);
                        field[i].set(obj, target);
                    }
                }
            }
        } else {
            if (!obj.getClass().getComponentType().isPrimitive() && !obj.getClass().getComponentType().isEnum()) {
                Object[] tmp = (Object[]) obj;
                for (int i = 0; i < tmp.length; i++) {
                    int index = (Integer) iter.next();
                    if (index != -1) {
                        Object target = tree.getObject(index);
                        tmp[i] = target;
                    }
                }
            }
        }
    }

    public static int getArraySize(Object src) {
        Class cls = src.getClass().getComponentType();
        if (cls == int.class) {
            return ((int[]) src).length;
        } else if (cls == long.class) {
            return ((long[]) src).length;
        } else if (cls == byte.class) {
            return ((byte[]) src).length;
        } else if (cls == char.class) {
            return ((char[]) src).length;
        } else if (cls == short.class) {
            return ((short[]) src).length;
        } else if (cls == boolean.class) {
            return ((boolean[]) src).length;
        } else if (cls == float.class) {
            return ((float[]) src).length;
        } else if (cls == double.class) {
            return ((double[]) src).length;
        } else {
            return ((Object[]) src).length;
        }
    }

    public static Object cloneObject(Object src)
            throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException, InvocationTargetException {
        if (src instanceof MerkleTreeCloneable) {
            return ((MerkleTreeCloneable) src).cloneObject();
        }
        Object ret = null;
        Class cls = src.getClass();
        if (!cls.isArray()) {
            ret = createNewObject(cls);
        } else {
            ret = createArray(cls.getComponentType(), getArraySize(src));
        }
        copyObject(src, ret);
        return ret;
    }

    private static Constructor getSerializableConstructor(Class<?> cl) {
        Class<?> initCl = cl;
        /*while (Serializable.class.isAssignableFrom(initCl)) {
        if ((initCl = initCl.getSuperclass()) == null) {
        System.out.println("Cannot getSerializableConstructor for "+cl);
        return null;
        }
        }
        System.out.println("Cannot find constructor for "+cl);
        return null;*/
        try {
            while (Serializable.class.isAssignableFrom(initCl)) {
                if ((initCl = initCl.getSuperclass()) == null) {
                    System.out.println("Cannot getSerializableConstructor for " + cl);
                    return null;
                }
                Constructor cons = initCl.getDeclaredConstructor((Class<?>[]) null);
                int mods = cons.getModifiers();
                cons = reflFactory.newConstructorForSerialization(cl, cons);
                if (cons == null) {
                    System.out.println("Cannot getSerializableConstructor for " + cl);
                }
                cons.setAccessible(true);
                return cons;
            }
        } catch (NoSuchMethodException ex) {
            System.out.println("Cannot getSerializableConstructor for " + cl);
            return null;
        }
        return null;
    }

    public static Object createNewObject(Class cls)
            throws ClassNotFoundException, InstantiationException, IllegalAccessException, InvocationTargetException {
        Object ret = null;

        Constructor[] cons = cls.getDeclaredConstructors();

        for (int i = 0; i < cons.length; i++) {
            //System.out.println(cons[i]);
            if (cons[i].getParameterTypes().length == 0) {
                cons[i].setAccessible(true);
                ret = cons[i].newInstance();

                return ret;
            }
        }
        return getSerializableConstructor(cls).newInstance(null);
//throw new RuntimeException("No constructor without argument. FIXME");
    }

    public static Object createArray(Class cls, int length)
            throws ClassNotFoundException, InstantiationException, IllegalAccessException, InvocationTargetException {
        return Array.newInstance(cls, length);
    }

    public static void printHash(byte[] hash) {
        for (int i = 0; i < hash.length; i++) {
            System.out.print(hash[i] + " ");
        }
        System.out.println();
        System.out.println();
    }

    /*    public static void main(String[] args) throws Exception {
    Data1 src = new Data1(1, 2);
    System.out.println(src);
    Data1 dst = new Data1();
    System.out.println(dst);
    Tools.copyObject(src, dst);
    System.out.println(dst);
    byte[] srcArray = new byte[]{1, 2, 3};
    printArray(srcArray);
    byte[] dstArray = new byte[3];
    printArray(dstArray);
    Tools.copyObject(srcArray, dstArray);
    printArray(dstArray);
    printArray((byte[]) cloneObject(srcArray));
    System.out.println(cloneObject(dst));
    }

    public static void printArray(byte[] array) {
    System.out.print("length=" + array.length + " ");
    for (int i = 0; i < array.length; i++)
    System.out.print(array[i] + " ");
    System.out.println();
    }*/
}
