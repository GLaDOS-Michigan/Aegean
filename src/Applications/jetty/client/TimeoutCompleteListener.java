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

package Applications.jetty.client;

import Applications.jetty.client.api.Request;
import Applications.jetty.client.api.Response;
import Applications.jetty.client.api.Result;
import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;
import Applications.jetty.util.thread.Scheduler;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public class TimeoutCompleteListener implements Response.CompleteListener, Runnable {
    private static final Logger LOG = Log.getLogger(TimeoutCompleteListener.class);

    private final AtomicReference<Scheduler.Task> task = new AtomicReference<>();
    private final Request request;

    public TimeoutCompleteListener(Request request) {
        this.request = request;
    }

    @Override
    public void onComplete(Result result) {
        Scheduler.Task task = this.task.getAndSet(null);
        if (task != null) {
            boolean cancelled = task.cancel();
            LOG.debug("Cancelled (successfully: {}) timeout task {}", cancelled, task);
        }
    }

    public boolean schedule(Scheduler scheduler) {
        long timeout = request.getTimeout();
        Scheduler.Task task = scheduler.schedule(this, timeout, TimeUnit.MILLISECONDS);
        if (this.task.getAndSet(task) != null)
            throw new IllegalStateException();
        LOG.debug("Scheduled timeout task {} in {} ms", task, timeout);
        return true;
    }

    @Override
    public void run() {
        request.abort(new TimeoutException("Total timeout elapsed"));
    }
}
