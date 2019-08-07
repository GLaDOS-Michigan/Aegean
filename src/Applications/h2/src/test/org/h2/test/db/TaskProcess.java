/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.db;

import org.h2.test.utils.SelfDestructor;
import org.h2.util.StringUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * A task that is run as an external process. This class communicates over
 * standard input / output with the process. The standard error stream of the
 * process is directly send to the standard error stream of this process.
 */
public class TaskProcess {
    private final Task taskDef;
    private Process process;
    private BufferedReader reader;
    private BufferedWriter writer;

    /**
     * Construct a new task process. The process is not started yet.
     *
     * @param taskDef the task
     */
    public TaskProcess(Task taskDef) {
        this.taskDef = taskDef;
    }

    /**
     * Start the task with the given arguments.
     *
     * @param args the arguments, or null
     */
    public void start(String... args) {
        try {
            String selfDestruct = SelfDestructor.getPropertyString(60);
            ArrayList<String> list = new ArrayList<String>();
            list.add("java");
            list.add(selfDestruct);
            list.add("-cp");
            list.add("bin" + File.pathSeparator + ".");
            list.add(Task.class.getName());
            list.add(taskDef.getClass().getName());
            if (args != null && args.length > 0) {
                list.addAll(Arrays.asList(args));
            }
            String[] procDef = new String[list.size()];
            list.toArray(procDef);
            traceOperation("start: " + StringUtils.arrayCombine(procDef, ' '));
            process = Runtime.getRuntime().exec(procDef);
            copyInThread(process.getErrorStream(), System.err);
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
            String line = reader.readLine();
            if (line == null) {
                throw new RuntimeException("No reply from process, command: " + StringUtils.arrayCombine(procDef, ' '));
            } else if (line.startsWith("running")) {
                traceOperation("got reply: " + line);
            } else if (line.startsWith("init error")) {
                throw new RuntimeException(line);
            }
        } catch (Throwable t) {
            throw new RuntimeException("Error starting task", t);
        }
    }

    private void copyInThread(final InputStream in, final OutputStream out) {
        new Thread() {
            public void run() {
                try {
                    while (true) {
                        int x = in.read();
                        if (x < 0) {
                            return;
                        }
                        if (out != null) {
                            out.write(x);
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }.start();
    }

    /**
     * Receive a message from the process over the standard output.
     *
     * @return the message
     */
    public String receive() {
        try {
            return reader.readLine();
        } catch (IOException e) {
            throw new RuntimeException("Error reading", e);
        }
    }

    /**
     * Send a message to the process over the standard input.
     *
     * @param message the message
     */
    public void send(String message) {
        try {
            writer.write(message + "\n");
            writer.flush();
        } catch (IOException e) {
            throw new RuntimeException("Error writing " + message, e);
        }
    }

    /**
     * Kill the process if it still runs.
     */
    public void destroy() {
        process.destroy();
    }

    /**
     * Trace the operation. Tracing is disabled by default.
     *
     * @param s the string to print
     */
    private void traceOperation(String s) {
        // ignore
    }
}
