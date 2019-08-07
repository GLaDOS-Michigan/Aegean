package network;

import java.net.InetAddress;

public interface Principal {
    public InetAddress getHost();

    public int getPort();
}