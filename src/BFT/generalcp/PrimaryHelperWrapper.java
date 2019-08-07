package BFT.generalcp;

import java.util.concurrent.LinkedBlockingQueue;

public class PrimaryHelperWrapper implements AppCPInterface {
    private AppCPInterface primary;
    private AppCPInterface helper;
    HelperThread helperThread;

    private interface HelperRequest {
    }

    private class HelperSyncRequest implements HelperRequest {
    }

    private class HelperExecRequest implements HelperRequest {
        public byte[] request;
        public RequestInfo info;

        public HelperExecRequest(byte[] request, RequestInfo info) {
            this.request = request;
            this.info = info;
        }
    }

    private LinkedBlockingQueue<HelperRequest> requests = new LinkedBlockingQueue<HelperRequest>();

    public PrimaryHelperWrapper(AppCPInterface primary, AppCPInterface helper) {
        this.primary = primary;
        this.helper = helper;
        helperThread = new HelperThread();
        helperThread.start();
    }

    public void execAsync(byte[] request, RequestInfo info) {
        this.primary.execAsync(request, info);
        requests.add(new HelperExecRequest(request, info));
    }

    public void execReadonly(byte[] request, int clientId, long requestId) {
        this.primary.execReadonly(request, clientId, requestId);
    }

    public void loadSnapshot(String fileName) {
        requests.clear();
        helperThread.terminate();
        helperThread.interrupt();
        this.primary.loadSnapshot(fileName);
        this.helper.loadSnapshot(fileName);
        helperThread = new HelperThread();
        helperThread.start();
    }

    // write the whole state into the snapshot file
    // Notice: sync should not start until all the previous consumeLog finishes
    public void sync() {
        requests.add(new HelperSyncRequest());
    }

    private class HelperThread extends Thread {
        private boolean running = true;

        public void terminate() {
            running = false;
        }

        public void run() {
            try {
                while (running) {
                    HelperRequest req = requests.take();
                    if (req instanceof HelperExecRequest) {
                        helper.execAsync(((HelperExecRequest) req).request, ((HelperExecRequest) req).info);
                    } else {
                        helper.sync();
                    }
                }
            } catch (InterruptedException e) {
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
