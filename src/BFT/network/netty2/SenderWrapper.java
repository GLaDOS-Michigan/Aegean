package BFT.network.netty2;

import BFT.Debug;
import BFT.membership.Membership;
import BFT.membership.Principal;
import BFT.network.NetworkSender;
import BFT.util.Role;

import java.net.InetSocketAddress;


public class SenderWrapper implements NetworkSender {

    InetSocketAddress[][] addressMap;

    NettyTCPSender sender;
    Membership membership;

    public SenderWrapper(Membership membership, int threadCount) {
        sender = new NettyTCPSender(threadCount);
        addressMap = new InetSocketAddress[5][];
        this.membership = membership;

        Principal clientNodes[][] = membership.getClientNodes();
        int len = 0;
        for (int i = 0; i < clientNodes.length; i++) {
            len += clientNodes[i].length;
        }
        addressMap[0] = new InetSocketAddress[len];
        int count = 0;
        for (int i = 0; i < clientNodes.length; i++) {
            for (int j = 0; j < clientNodes[i].length; j++) {
                addressMap[0][count++] = new InetSocketAddress(clientNodes[i][j].getIP(), clientNodes[i][j].getPort());
            }
        }
        Principal[] nodes = membership.getOrderNodes();
        addressMap[1] = new InetSocketAddress[nodes.length];
        for (int i = 0; i < addressMap[1].length; i++)
            addressMap[1][i] = new InetSocketAddress(nodes[i].getIP(), nodes[i].getPort());
        nodes = membership.getExecNodes();
        addressMap[2] = new InetSocketAddress[nodes.length];
        for (int i = 0; i < addressMap[2].length; i++)
            addressMap[2][i] = new InetSocketAddress(nodes[i].getIP(), nodes[i].getPort());
        nodes = membership.getFilterNodes();
        addressMap[3] = new InetSocketAddress[nodes.length];
        for (int i = 0; i < addressMap[3].length; i++)
            addressMap[3][i] = new InetSocketAddress(nodes[i].getIP(), nodes[i].getPort());
        nodes = membership.getVerifierNodes();
        addressMap[4] = new InetSocketAddress[nodes.length];
        for (int i = 0; i < addressMap[4].length; i++) {
            addressMap[4][i] = new InetSocketAddress(nodes[i].getIP(), nodes[i].getPort());
        }

    }

    public SenderWrapper(Membership mem) {
        this(mem, 1);
    }


    public void setTCPNoDelay(boolean nodelay) {
        sender.clientBootstrap.setOption("tcpNoDelay", nodelay);
    }

    public void setKeepAlive(boolean alive) {
        sender.clientBootstrap.setOption("keepAlive", alive);
    }

    public void send(byte[] msg, Role role, int id, int subId) {
        String extra = role.equals(Role.CLIENT) ? subId + "" : "N/A";
        Debug.debug(Debug.MODULE_NETWORK, "\tInside SenderWrapper.send: role %s, id = %d, subId = %s",
                role.toString(),
                id,
                extra);
        if (role.equals(Role.CLIENT)) {
            send(msg, role, getClientIndex(id, subId));
        } else {
            send(msg, role, id);
        }
    }

    public void send(byte[] msg, Role role, int id) {
        sender.send(msg, addressMap[roleToInt(role)][id]);
    }

    public void send(byte[] msg, Role role, int ids[]) {
        for (int i = 0; i < ids.length; i++)
            send(msg, role, ids[i]);
    }


    static int roleToInt(Role role) {
        switch (role) {
            case CLIENT:
                return 0;
            case ORDER:
                return 1;
            case EXEC:
                return 2;
            case FILTER:
                return 3;
            case VERIFIER:
                return 4;
            default:
                throw new RuntimeException("unknown role");
        }
    }

    private int getClientIndex(int clientId, int subId) {
        Principal clientNodes[][] = membership.getClientNodes();
        int index = 0;
        for (int i = 0; i < clientId; i++) {
            index += clientNodes[i].length;
        }
        index += subId;
        //System.out.println("returning client index = "+index);
        return index;
    }
}
