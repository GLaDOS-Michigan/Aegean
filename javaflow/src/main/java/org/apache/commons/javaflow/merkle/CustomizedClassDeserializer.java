package org.apache.commons.javaflow.merkle;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;

public class CustomizedClassDeserializer extends ObjectInputStream {
	private ClassLoader classLoader;

	public CustomizedClassDeserializer(InputStream in, ClassLoader classLoader)
			throws IOException {
		super(in);
		this.classLoader = classLoader;
	}

	protected Class<?> resolveClass(ObjectStreamClass desc)
			throws ClassNotFoundException {
		return Class.forName(desc.getName(), false, classLoader);
	}
}
