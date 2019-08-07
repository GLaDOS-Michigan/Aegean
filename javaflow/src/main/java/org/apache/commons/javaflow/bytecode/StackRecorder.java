/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.javaflow.bytecode;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.javaflow.merkle.MerkleTree;
import org.apache.commons.javaflow.utils.ReflectionUtils;
import org.apache.commons.javaflow.ContinuationDeath;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Adds additional behaviors necessary for stack capture/restore on top of
 * {@link Stack}.
 */
public final class StackRecorder extends Stack {

	private static final Log log = LogFactory.getLog(StackRecorder.class);
	private static final long serialVersionUID = 2L;

	private static final ThreadLocal<StackRecorder> threadMap = new ThreadLocal<StackRecorder>();

	/*
	 * a set of monitored stacks. This needs to be kept synchronized with
	 * threadMap.
	 */
	private static final HashSet<StackRecorder> allMonitoredThreads = new HashSet<StackRecorder>();
	/*
	 * lock used for accessing the allMonitoredThreads
	 */
	private static final Object lock = new byte[0];

	/**
	 * True, if the continuation restores the previous stack trace to the last
	 * invocation of suspend().
	 * 
	 * <p>
	 * This field is accessed from the byte code injected into application code,
	 * and therefore defining a wrapper get method makes it awkward to step
	 * through the user code. That's why this field is public.
	 */
	public transient boolean isRestoring = false;

	/**
	 * True, is the continuation freeze the strack trace, and stops the
	 * continuation.
	 * 
	 * @see #isRestoring
	 */
	public transient boolean isCapturing = false;

	/** Context object passed by the client code to continuation during resume */
	private transient Object context;
	/**
	 * Result object passed by the continuation to the client code during
	 * suspend
	 */
	public transient Object value;

	/**
	 * Creates a new empty {@link StackRecorder} that runs the given target.
	 */
	public StackRecorder(final Runnable pTarget) {
		super(pTarget);
	}

	private StackRecorder() {
		super();
	}

	/**
	 * Creates a clone of the given {@link StackRecorder}.
	 */
	public StackRecorder(final Stack pParent) {
		super(pParent);
	}

	public static Object suspend(final Object value) {
		log.debug("suspend()");

		final StackRecorder stackRecorder = get();
		if (stackRecorder == null) {
			throw new IllegalStateException("No continuation is running");
		}

		// set to isCapturing will be set to true.
		stackRecorder.isCapturing = !stackRecorder.isRestoring;
		stackRecorder.isRestoring = false;
		stackRecorder.value = value;

		// flow breaks here, actual return will be executed in resumed
		// continuation
		// return in continuation to be suspended is executed as well but
		// ignored

		return stackRecorder.context;
	}

	public StackRecorder execute(final Object context) {
		final StackRecorder old = registerThread();
		try {
			isRestoring = !isEmpty(); // start restoring if we have a filled
										// stack
			this.context = context;

			if (isRestoring) {
				if (log.isDebugEnabled()) {
					log.debug("Restoring state of "
							+ ReflectionUtils.getClassName(runnable) + "/"
							+ ReflectionUtils.getClassLoaderName(runnable));
				}
			}

			log.debug("calling runnable");
			runnable.run();

			if (isCapturing) {
				if (isEmpty()) {
					// if we were really capturing the stack, at least we should
					// have
					// one object in the reference stack. Otherwise, it usually
					// means
					// that the application wasn't instrumented correctly.
					throw new IllegalStateException("stack corruption. Is "
							+ runnable.getClass()
							+ " instrumented for javaflow?");
				}
				// top of the reference stack is the object that we'll call into
				// when resuming this continuation. we have a separate Runnable
				// for this, so throw it away
				popReference();
				return this;
			} else {
				return null; // nothing more to continue
			}
		} catch (final ContinuationDeath cd) {
			// this isn't an error, so no need to log
			throw cd;
		} catch (final Error e) {
			log.error(e.getMessage(), e);
			throw e;
		} catch (final RuntimeException e) {
			log.error(e.getMessage(), e);
			throw e;
		} finally {
			this.context = null;
			deregisterThread(old);
		}
	}

	public Object getContext() {
		return context;
	}

	/**
	 * Bind this stack recorder to running thread.
	 */
	private StackRecorder registerThread() {
		final StackRecorder old = get();
		synchronized (lock) {
			threadMap.set(this);
			allMonitoredThreads.remove(old);
			allMonitoredThreads.add(this);
		}
		return old;
	}

	/**
	 * Unbind the current stack recorder to running thread.
	 */
	private void deregisterThread(final StackRecorder old) {
		synchronized (lock) {
			threadMap.set(old);
			allMonitoredThreads.remove(this);
			allMonitoredThreads.add(old);
		}
	}

	/**
	 * Return the continuation, which is associated to the current thread.
	 */
	public static StackRecorder get() {
		return threadMap.get();
	}

	/**
	 * Function added for Adam. It returns the stack of all running threads
	 * under Adam's monitor.
	 * 
	 * @return
	 */
	public static List<StackRecorder> getAllStacks() {
		synchronized (lock) {
			List<StackRecorder> allStacks = new ArrayList<StackRecorder>();
			for (StackRecorder s : allMonitoredThreads) {
				allStacks.add(s);
			}
			return allStacks;
		}
	}

	public void serialize(DataOutputStream out) throws IOException {
		out.writeInt(iTop);
		for (int i = 0; i < iTop; i++) {
			out.writeInt(istack[i]);
			log.debug("istack[" + i + "] = " + istack[i]);
		}

		out.writeInt(lTop);
		for (int i = 0; i < lTop; i++) {
			out.writeLong(lstack[i]);
			log.debug("lstack[" + i + "] = " + lstack[i]);
		}

		out.writeInt(dTop);
		for (int i = 0; i < dTop; i++) {
			out.writeDouble(dstack[i]);
			log.debug("dstack[" + i + "] = " + dstack[i]);
		}

		out.writeInt(fTop);
		for (int i = 0; i < fTop; i++) {
			out.writeDouble(fstack[i]);
			log.debug("fstack[" + i + "] = " + fstack[i]);
		}

		out.writeInt(oTop);
		for (int i = 0; i < oTop; i++) {
			out.writeInt(getObjectID(ostack[i]));
			log.debug("ostack[" + i + "] = " + ostack[i]);
		}

		out.writeInt(rTop);
		for (int i = 0; i < rTop; i++) {
			out.writeInt(getObjectID(rstack[i]));
			log.debug("rstack[" + i + "] = " + rstack[i]);
		}
	}

	public void deserialize(DataInputStream in) throws IOException,
			ClassNotFoundException {
		iTop = in.readInt();
		istack = new int[iTop];
		for (int i = 0; i < iTop; i++) {
			istack[i] = in.readInt();
		}

		lTop = in.readInt();
		lstack = new long[lTop];
		for (int i = 0; i < lTop; i++) {
			lstack[i] = in.readLong();
		}

		dTop = in.readInt();
		dstack = new double[dTop];
		for (int i = 0; i < dTop; i++) {
			dstack[i] = in.readDouble();
		}

		fTop = in.readInt();
		fstack = new float[fTop];
		for (int i = 0; i < fTop; i++) {
			fstack[i] = in.readFloat();
		}

		oTop = in.readInt();
		ostack = new Object[oTop];
		for (int i = 0; i < oTop; i++) {
			int id = in.readInt();
			ostack[i] = getObjectByID(id);
		}

		rTop = in.readInt();
		rstack = new Object[rTop];
		for (int i = 0; i < rTop; i++) {
			int id = in.readInt();
			rstack[i] = getObjectByID(id);
		}

	}

	private static int getObjectID(Object obj) {
		return MerkleTree.getObjectID(obj);
		// // TODO
		// return 0;
	}

	private static Object getObjectByID(int id) {
		return MerkleTree.getObjectByID(id);
		// // TODO
		// return null;
	}
}
