package Applications.h2bench;

import BFT.Debug;
import BFT.exec.MessageFactory;
import BFT.exec.RequestFilter;
import BFT.exec.RequestHandler;
import BFT.exec.RequestInfo;
import BFT.messages.FilteredRequest;
import BFT.messages.FilteredRequestCore;
import BFT.messages.Reply;
import BFT.messages.VerifiedMessageBase;
import BFT.network.PassThroughNetworkQueue;
import BFT.network.netty.NettyTCPReceiver;
import BFT.network.netty.NettyTCPSender;
import BFT.util.Role;

public class FakeExec extends BFT.exec.ExecBaseNode {

    private RequestHandler requestHandler;
    private int myIndex;

    public FakeExec(String membership, int id) {
        super(membership, id);
        this.myIndex = id;
    }

    @Override
    public void start(RequestHandler handler, RequestFilter filter) {
        this.requestHandler = handler;

        NettyTCPSender sendNet = new NettyTCPSender(parameters, this.getMembership(), 1);
        this.setNetwork(sendNet);

        Role[] roles = new Role[4];
        roles[0] = Role.EXEC;
        roles[1] = Role.FILTER;
        roles[2] = Role.CLIENT;
        roles[3] = Role.VERIFIER;

        PassThroughNetworkQueue ptnq = new PassThroughNetworkQueue(this);
        NettyTCPReceiver receiveNet = new NettyTCPReceiver(roles,
                this.getMembership(), ptnq, 1);
    }

    @Override
    public synchronized void result(byte[] reply, RequestInfo info) {
        Reply rep = new Reply(myIndex, info.getRequestId(), reply);
        this.authenticateClientMacMessage(rep, info.getClientId(), info.getSubId());
        this.sendToClient(rep.getBytes(), info.getClientId(), info.getSubId());
    }

    private void process(FilteredRequest request) {
        //System.out.println("Get filtered request from " + request.getSender());

        for (int i = 0; i < request.getCore().length; i++) {
            FilteredRequestCore tmp = request.getCore()[i];
            process(tmp, (int) request.getSender());
        }
    }

    protected void process(FilteredRequestCore req, int sender) {
        //System.out.println("ProcessFilterCore "+req.getRequestId());
        FilteredRequestCore rc = req;
        this.requestHandler.execRequest(req.getCommand(),
                new RequestInfo(false, sender, 0L, req.getRequestId(), 0L, 0L));

    }

    public synchronized void handle(byte[] vmbbytes) {

        VerifiedMessageBase vmb = MessageFactory.fromBytes(vmbbytes, parameters);
        //System.out.println("Got new tag "+vmb.getTag());
        switch (vmb.getTag()) {
            case BFT.exec.messages.MessageTags.FilteredRequest:
                process((FilteredRequest) vmb);
                break;
            default:
                Debug.kill("servershim does not handle message " + vmb.getTag());
        }
    }
}

