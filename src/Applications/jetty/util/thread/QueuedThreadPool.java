//
//  ========================================================================
//  Copyright (c) 1995-2013 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//


package Applications.jetty.util.thread;

import Applications.jetty.util.BlockingArrayQueue;
import Applications.jetty.util.StringUtil;
import Applications.jetty.util.annotation.ManagedAttribute;
import Applications.jetty.util.annotation.ManagedObject;
import Applications.jetty.util.annotation.ManagedOperation;
import Applications.jetty.util.annotation.Name;
import Applications.jetty.util.component.AbstractLifeCycle;
import Applications.jetty.util.component.ContainerLifeCycle;
import Applications.jetty.util.component.Dumpable;
import Applications.jetty.util.component.LifeCycle;
import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;
import Applications.jetty.util.thread.ThreadPool.SizedThreadPool;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@ManagedObject("A thread pool with no max bound by default")
public class QueuedThreadPool extends AbstractLifeCycle implements SizedThreadPool, Dumpable {
    private static final Logger LOG = Log.getLogger(QueuedThreadPool.class);

    private final AtomicInteger _threadsStarted = new AtomicInteger();
    private final AtomicInteger _threadsIdle = new AtomicInteger();
    private final AtomicLong _lastShrink = new AtomicLong();
    private final ConcurrentLinkedQueue<Thread> _threads = new ConcurrentLinkedQueue<>();
    private final Object _joinLock = new Object();
    private final BlockingQueue<Runnable> _jobs;
    private String _name = "qtp" + hashCode();
    private int _idleTimeout;
    private int _maxThreads;
    private int _minThreads;
    private int _priority = Thread.NORM_PRIORITY;
    private boolean _daemon = false;
    private boolean _detailedDump = false;

    public QueuedThreadPool() {
        this(200);
    }

    public QueuedThreadPool(@Name("maxThreads") int maxThreads) {
        this(maxThreads, 8);
    }

    public QueuedThreadPool(@Name("maxThreads") int maxThreads, @Name("minThreads") int minThreads) {
        this(maxThreads, minThreads, 60000);
    }

    public QueuedThreadPool(@Name("maxThreads") int maxThreads, @Name("minThreads") int minThreads, @Name("idleTimeout") int idleTimeout) {
        this(maxThreads, minThreads, idleTimeout, null);
    }

    public QueuedThreadPool(@Name("maxThreads") int maxThreads, @Name("minThreads") int minThreads, @Name("idleTimeout") int idleTimeout, @Name("queue") BlockingQueue<Runnable> queue) {
        setMinThreads(minThreads);
        setMaxThreads(maxThreads);
        setIdleTimeout(idleTimeout);
        setStopTimeout(5000);

        if (queue == null)
            queue = new BlockingArrayQueue<>(_minThreads, _minThreads);
        _jobs = queue;

    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        _threadsStarted.set(0);

        startThreads(_minThreads);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        long timeout = getStopTimeout();
        BlockingQueue<Runnable> jobs = getQueue();

        // If no stop timeout, clear job queue
        if (timeout <= 0)
            jobs.clear();

        // Fill job Q with noop jobs to wakeup idle
        Runnable noop = new Runnable() {
            @Override
            public void run() {
            }
        };
        for (int i = _threadsStarted.get(); i-- > 0; )
            jobs.offer(noop);

        // try to jobs complete naturally for half our stop time
        long stopby = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout) / 2;
        for (Thread thread : _threads) {
            long canwait = TimeUnit.NANOSECONDS.toMillis(stopby - System.nanoTime());
            if (canwait > 0)
                thread.join(canwait);
        }

        // If we still have threads running, get a bit more aggressive

        // interrupt remaining threads
        if (_threadsStarted.get() > 0)
            for (Thread thread : _threads)
                thread.interrupt();

        // wait again for the other half of our stop time
        stopby = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout) / 2;
        for (Thread thread : _threads) {
            long canwait = TimeUnit.NANOSECONDS.toMillis(stopby - System.nanoTime());
            if (canwait > 0)
                thread.join(canwait);
        }

        Thread.yield();
        int size = _threads.size();
        if (size > 0) {
            Thread.yield();

            if (LOG.isDebugEnabled()) {
                for (Thread unstopped : _threads) {
                    StringBuilder dmp = new StringBuilder();
                    for (StackTraceElement element : unstopped.getStackTrace()) {
                        dmp.append(StringUtil.__LINE_SEPARATOR).append("\tat ").append(element);
                    }
                    LOG.warn("Couldn't stop {}{}", unstopped, dmp.toString());
                }
            } else {
                for (Thread unstopped : _threads)
                    LOG.warn("{} Couldn't stop {}", this, unstopped);
            }
        }

        synchronized (_joinLock) {
            _joinLock.notifyAll();
        }
    }

    /**
     * Delegated to the named or anonymous Pool.
     */
    public void setDaemon(boolean daemon) {
        _daemon = daemon;
    }

    /**
     * Set the maximum thread idle time.
     * Threads that are idle for longer than this period may be
     * stopped.
     * Delegated to the named or anonymous Pool.
     *
     * @param idleTimeout Max idle time in ms.
     * @see #getIdleTimeout
     */
    public void setIdleTimeout(int idleTimeout) {
        _idleTimeout = idleTimeout;
    }

    /**
     * Set the maximum number of threads.
     * Delegated to the named or anonymous Pool.
     *
     * @param maxThreads maximum number of threads.
     * @see #getMaxThreads
     */
    @Override
    public void setMaxThreads(int maxThreads) {
        _maxThreads = maxThreads;
        if (_minThreads > _maxThreads)
            _minThreads = _maxThreads;
    }

    /**
     * Set the minimum number of threads.
     * Delegated to the named or anonymous Pool.
     *
     * @param minThreads minimum number of threads
     * @see #getMinThreads
     */
    @Override
    public void setMinThreads(int minThreads) {
        _minThreads = minThreads;

        if (_minThreads > _maxThreads)
            _maxThreads = _minThreads;

        int threads = _threadsStarted.get();
        if (isStarted() && threads < _minThreads)
            startThreads(_minThreads - threads);
    }

    /**
     * @param name Name of this thread pool to use when naming threads.
     */
    public void setName(String name) {
        if (isRunning())
            throw new IllegalStateException("started");
        _name = name;
    }

    /**
     * Set the priority of the pool threads.
     *
     * @param priority the new thread priority.
     */
    public void setThreadsPriority(int priority) {
        _priority = priority;
    }

    /**
     * Get the maximum thread idle time.
     * Delegated to the named or anonymous Pool.
     *
     * @return Max idle time in ms.
     * @see #setIdleTimeout
     */
    @ManagedAttribute("maximum time a thread may be idle in ms")
    public int getIdleTimeout() {
        return _idleTimeout;
    }

    /**
     * Set the maximum number of threads.
     * Delegated to the named or anonymous Pool.
     *
     * @return maximum number of threads.
     * @see #setMaxThreads
     */
    @Override
    @ManagedAttribute("maximum number of threads in the pool")
    public int getMaxThreads() {
        return _maxThreads;
    }

    /**
     * Get the minimum number of threads.
     * Delegated to the named or anonymous Pool.
     *
     * @return minimum number of threads.
     * @see #setMinThreads
     */
    @Override
    @ManagedAttribute("minimum number of threads in the pool")
    public int getMinThreads() {
        return _minThreads;
    }

    /**
     * @return The name of the this thread pool
     */
    @ManagedAttribute("name of the thread pool")
    public String getName() {
        return _name;
    }

    /**
     * Get the priority of the pool threads.
     *
     * @return the priority of the pool threads.
     */
    @ManagedAttribute("priority of threads in the pool")
    public int getThreadsPriority() {
        return _priority;
    }

    /**
     * Get the size of the job queue.
     *
     * @return Number of jobs queued waiting for a thread
     */
    @ManagedAttribute("Size of the job queue")
    public int getQueueSize() {
        return _jobs.size();
    }

    /**
     * Delegated to the named or anonymous Pool.
     */
    @ManagedAttribute("thead pool using a daemon thread")
    public boolean isDaemon() {
        return _daemon;
    }

    public boolean isDetailedDump() {
        return _detailedDump;
    }

    public void setDetailedDump(boolean detailedDump) {
        _detailedDump = detailedDump;
    }

    @Override
    public void execute(Runnable job) {
        if (!isRunning() || !_jobs.offer(job)) {
            LOG.warn("{} rejected {}", this, job);
            throw new RejectedExecutionException(job.toString());
        }
    }

    /**
     * Blocks until the thread pool is {@link LifeCycle#stop stopped}.
     */
    @Override
    public void join() throws InterruptedException {
        synchronized (_joinLock) {
            while (isRunning())
                _joinLock.wait();
        }

        while (isStopping())
            Thread.sleep(1);
    }

    /**
     * @return The total number of threads currently in the pool
     */
    @Override
    @ManagedAttribute("total number of threads currently in the pool")
    public int getThreads() {
        return _threadsStarted.get();
    }

    /**
     * @return The number of idle threads in the pool
     */
    @Override
    @ManagedAttribute("total number of idle threads in the pool")
    public int getIdleThreads() {
        return _threadsIdle.get();
    }

    /**
     * @return True if the pool is at maxThreads and there are not more idle threads than queued jobs
     */
    @Override
    @ManagedAttribute("True if the pools is at maxThreads and there are not idle threads than queued jobs")
    public boolean isLowOnThreads() {
        return _threadsStarted.get() == _maxThreads && _jobs.size() >= _threadsIdle.get();
    }

    private boolean startThreads(int threadsToStart) {
        while (threadsToStart > 0) {
            int threads = _threadsStarted.get();
            if (threads >= _maxThreads)
                return false;

            if (!_threadsStarted.compareAndSet(threads, threads + 1))
                continue;

            boolean started = false;
            try {
                Thread thread = newThread(_runnable);
                thread.setDaemon(isDaemon());
                thread.setPriority(getThreadsPriority());
                thread.setName(_name + "-" + thread.getId());
                _threads.add(thread);

                thread.start();
                started = true;
            } finally {
                if (!started)
                    _threadsStarted.decrementAndGet();
            }
            if (started)
                threadsToStart--;
        }
        return true;
    }

    protected Thread newThread(Runnable runnable) {
        return new Thread(runnable);
    }


    @Override
    @ManagedOperation("dump thread state")
    public String dump() {
        return ContainerLifeCycle.dump(this);
    }

    @Override
    public void dump(Appendable out, String indent) throws IOException {
        List<Object> dump = new ArrayList<>(getMaxThreads());
        for (final Thread thread : _threads) {
            final StackTraceElement[] trace = thread.getStackTrace();
            boolean inIdleJobPoll = false;
            for (StackTraceElement t : trace) {
                if ("idleJobPoll".equals(t.getMethodName())) {
                    inIdleJobPoll = true;
                    break;
                }
            }
            final boolean idle = inIdleJobPoll;

            if (isDetailedDump()) {
                dump.add(new Dumpable() {
                    @Override
                    public void dump(Appendable out, String indent) throws IOException {
                        out.append(String.valueOf(thread.getId())).append(' ').append(thread.getName()).append(' ').append(thread.getState().toString()).append(idle ? " IDLE" : "").append('\n');
                        if (!idle)
                            ContainerLifeCycle.dump(out, indent, Arrays.asList(trace));
                    }

                    @Override
                    public String dump() {
                        return null;
                    }
                });
            } else {
                dump.add(thread.getId() + " " + thread.getName() + " " + thread.getState() + " @ " + (trace.length > 0 ? trace[0] : "???") + (idle ? " IDLE" : ""));
            }
        }

        ContainerLifeCycle.dumpObject(out, this);
        ContainerLifeCycle.dump(out, indent, dump);
    }

    @Override
    public String toString() {
        return String.format("%s{%s,%d<=%d<=%d,i=%d,q=%d}", _name, getState(), getMinThreads(), getThreads(), getMaxThreads(), getIdleThreads(), (_jobs == null ? -1 : _jobs.size()));
    }

    private Runnable idleJobPoll() throws InterruptedException {
        return _jobs.poll(_idleTimeout, TimeUnit.MILLISECONDS);
    }

    private Runnable _runnable = new Runnable() {
        @Override
        public void run() {
            boolean shrink = false;
            try {
                Runnable job = _jobs.poll();

                if (job != null && _threadsIdle.get() == 0) {
                    startThreads(1);
                }

                loop:
                while (isRunning()) {
                    // Job loop
                    while (job != null && isRunning()) {
                        runJob(job);
                        if (Thread.interrupted())
                            break loop;
                        job = _jobs.poll();
                    }

                    // Idle loop
                    try {
                        _threadsIdle.incrementAndGet();

                        while (isRunning() && job == null) {
                            if (_idleTimeout <= 0)
                                job = _jobs.take();
                            else {
                                // maybe we should shrink?
                                final int size = _threadsStarted.get();
                                if (size > _minThreads) {
                                    long last = _lastShrink.get();
                                    long now = System.nanoTime();
                                    if (last == 0 || (now - last) > TimeUnit.MILLISECONDS.toNanos(_idleTimeout)) {
                                        shrink = _lastShrink.compareAndSet(last, now) &&
                                                _threadsStarted.compareAndSet(size, size - 1);
                                        if (shrink) {
                                            return;
                                        }
                                    }
                                }
                                job = idleJobPoll();
                            }
                        }
                    } finally {
                        if (_threadsIdle.decrementAndGet() == 0) {
                            startThreads(1);
                        }
                    }
                }
            } catch (InterruptedException e) {
                LOG.ignore(e);
            } catch (Throwable e) {
                LOG.warn(e);
            } finally {
                if (!shrink)
                    _threadsStarted.decrementAndGet();
                _threads.remove(Thread.currentThread());
            }
        }
    };

    /**
     * <p>Runs the given job in the {@link Thread#currentThread() current thread}.</p>
     * <p>Subclasses may override to perform pre/post actions before/after the job is run.</p>
     *
     * @param job the job to run
     */
    protected void runJob(Runnable job) {
        job.run();
    }

    /**
     * @return the job queue
     */
    protected BlockingQueue<Runnable> getQueue() {
        return _jobs;
    }

    /**
     * @param queue the job queue
     */
    public void setQueue(BlockingQueue<Runnable> queue) {
        throw new UnsupportedOperationException("Use constructor injection");
    }

    /**
     * @param id The thread ID to interrupt.
     * @return true if the thread was found and interrupted.
     */
    @ManagedOperation("interrupt a pool thread")
    public boolean interruptThread(@Name("id") long id) {
        for (Thread thread : _threads) {
            if (thread.getId() == id) {
                thread.interrupt();
                return true;
            }
        }
        return false;
    }

    /**
     * @param id The thread ID to interrupt.
     * @return true if the thread was found and interrupted.
     */
    @ManagedOperation("dump a pool thread stack")
    public String dumpThread(@Name("id") long id) {
        for (Thread thread : _threads) {
            if (thread.getId() == id) {
                StringBuilder buf = new StringBuilder();
                buf.append(thread.getId()).append(" ").append(thread.getName()).append(" ").append(thread.getState()).append(":\n");
                for (StackTraceElement element : thread.getStackTrace())
                    buf.append("  at ").append(element.toString()).append('\n');
                return buf.toString();
            }
        }
        return null;
    }
}
