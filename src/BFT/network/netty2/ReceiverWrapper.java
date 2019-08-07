package BFT.network.netty2;

import BFT.membership.Membership;
import BFT.membership.Principal;
import BFT.network.NetworkQueue;
import BFT.network.NetworkReceiver;
import BFT.util.Role;

import java.net.InetSocketAddress;


public class ReceiverWrapper implements NetworkReceiver {


    NettyTCPReceiver receiver;

    public ReceiverWrapper(Role roles[], Membership membership, NetworkQueue q, int threadCount) {
        int count = 0;
        for (int i = 0; i < roles.length; i++) {
            count += membership.getMyInterfaces(roles[i]).length;
        }
        InetSocketAddress[] addressList = new InetSocketAddress[count];

        count = 0;
        for (int i = 0; i < roles.length; i++) {
            Principal p[] = membership.getMyInterfaces(roles[i]);
            for (int j = 0; j < p.length; j++) {
                addressList[count++] = new InetSocketAddress(p[j].getIP(), p[j].getPort());
            }
        }
        receiver = new NettyTCPReceiver(addressList, q, threadCount);
    }

    public void setTCPNoDelay(boolean nodelay) {
        receiver.bootstrap.setOption("tcpNoDelay", nodelay);
    }

    public void setKeepAlive(boolean alive) {
        receiver.bootstrap.setOption("keepAlive", alive);
    }

    public void start() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void stop() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}