package Applications.jetty.eve_connector;

import Applications.jetty.eve_task.JettyEveTask;
import Applications.jetty.http_servlet_wrapper.HttpUtils;
import Applications.jetty.server.FakedServer;
import BFT.clientShim.ClientGlueInterface;
import BFT.exec.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author lcosvse
 */

public class JettyEveConnector implements RequestHandler, RequestFilter, ClientGlueInterface {

    private transient ReplyHandler replyHandler;
    private static int keyCounter = 0;
    private transient SessionManagerInterface sessionManager;

    private static synchronized int getKeyCounter() {
        return JettyEveConnector.keyCounter;
    }

    public JettyEveConnector(ReplyHandler replyHandler) {
        //if replyhandler is null, this means we are running in one of the sequ`ential modes and no need to merkle tree
        //sequential modes uses a child class of this class (SequentialJettyEveConnector) and it uses generalcp instead of replyhandler
        if(replyHandler != null)
        {
            System.err.println("session manager is added to the merkle tree");
            this.sessionManager = new SessionManager(false);
        }
        else
        {
            this.sessionManager = new SessionManager(false);
        }

        this.replyHandler = replyHandler;
        HttpUtils.initMap();
    }

    @Override
    public List<RequestKey> generateKeys(byte[] request) {
        RequestKey key = new RequestKey(true, "Key: " + getKeyCounter());
        List<RequestKey> res = new ArrayList<RequestKey>();
        res.add(key);
        return res;
    }

    @Override
    public void execRequest(byte[] request, RequestInfo info) {
        String requestURL = new String(request);

        // 1. Compose request runnable task
        JettyEveTask task = new JettyEveTask(requestURL, this, info);

        // 2. Execute task in the current thread -- thread scheduling is
        // taken care by Eve.
        task.run();
    }

    @Override
    public void execReadOnly(byte[] request, RequestInfo info) {
        throw new RuntimeException("Readonly request execution is not implemented.");
    }

    @Override
    public byte[] canonicalEntry(byte[][] options) {
        byte[] result = null;
        for (int i = 0; i < options.length; i++) {
            if (options[i] == null)
                continue;
            //System.out.println(options[i].length);
            if (result == null) {
                result = options[i];
                continue;
            }
            if (!Arrays.equals(result, options[i])) {
                System.err.println("Unmatched replies");
                return null;
            }
        }
        assert (result != null);
        return result;
    }

    @Override
    public void brokenConnection() {
        throw new RuntimeException("EBClientProxy.brokenConnection() is not yet implemented.");
    }

    @Override
    public void returnReply(byte[] reply) {
        throw new RuntimeException("EBClientProxy.returnReply() is not yet implemented.");
    }

    public synchronized void replyCallback(ServletReply servletReply) {
        replyHandler.result(servletReply.getReplyData(), (RequestInfo) servletReply.getRequestInfo());
    }

    /*
     * Ugly impl., should be an interface method
     */
    public SessionManagerInterface getSessionManager() {
        return this.sessionManager;
    }
}
