package org.apache.commons.javaflow.merkle;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;

public class MerkleTree {
	public static ClassLoader classLoader;
	private static final HashMap<Integer, Object> idObjMap = new HashMap<Integer, Object>();

	public static int getObjectID(Object obj) {
		int id = obj.hashCode();
		if (!(obj instanceof Serializable)) {
			throw new RuntimeException("object not serializable.");
		}
		if (idObjMap.containsKey(id)) {
			return id;
		}
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			ObjectOutputStream out = new ObjectOutputStream(bos);
			out.writeObject(obj);
			out.close();

			ByteArrayInputStream bis = new ByteArrayInputStream(
					bos.toByteArray());
			ObjectInputStream in = new CustomizedClassDeserializer(bis,
					classLoader);
			idObjMap.put(id, in.readObject());
			return id;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return -1;
	}

	public static Object getObjectByID(int id) {
		// TODO
		// byte[] buf = idObjMap.get(id);
		// if (buf != null) {
		// try {
		// ByteArrayInputStream bis = new ByteArrayInputStream(buf);
		// ObjectInputStream in = new CustomizedClassDeserializer(bis,
		// classLoader);
		// return in.readObject();
		// } catch (IOException e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// } catch (ClassNotFoundException e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// }
		// }
		// return null;
		return idObjMap.get(id);
	}

}
