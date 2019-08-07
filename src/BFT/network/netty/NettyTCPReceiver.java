/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package BFT.network.netty;

import BFT.membership.Membership;
import BFT.membership.Principal;
import BFT.network.NetworkQueue;
import BFT.network.NetworkReceiver;
import BFT.util.Role;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.Executors;

/**
 * @author manos
 */
public class NettyTCPReceiver implements NetworkReceiver {

    // The channel on which we'll accept connections
    private ServerBootstrap bootstrap;


    public NettyTCPReceiver(Role[] roles, Membership membership, NetworkQueue NQ) {
        this(roles, membership, NQ, 1);
    }

    public NettyTCPReceiver(Role[] roles, Membership membership,
                            NetworkQueue NQ, int threadCount) {

        // create a bootstrap object that builds up the basic chain

        bootstrap = new ServerBootstrap(
                new NioServerSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool(),
                        threadCount));


        bootstrap.setOption("child.tcpNoDelay", true);
        bootstrap.setOption("child.keepAlive", true);

        bootstrap.setPipelineFactory(new BFTPipelineFactory(NQ));

        for (int j = 0; j < roles.length; j++) { // for each role that i am supposed to listen to, extract the interfaces
            Role thisRole = roles[j];
            Principal[] myInterfaces = membership.getMyInterfaces(thisRole);
            String[] IPports = new String[myInterfaces.length];
            for (int i = 0; i < myInterfaces.length; i++) {
                IPports[i] = new String(myInterfaces[i].getIP().toString().split("/", 0)[1] + ":" + myInterfaces[i].getPort());
            }

            for (int i = 0; i < IPports.length; i++) { // for each ip:port of this role, bind the bootstrap to it.
                try {

                    String ipStr = IPports[i].split(":", 0)[0];
                    String portStr = IPports[i].split(":", 0)[1];

                    InetSocketAddress isa = new InetSocketAddress(InetAddress.getByName(ipStr), Integer.parseInt(portStr));
                    System.out.println("Opening to " + ipStr + " " + portStr);
                    bootstrap.bind(isa);
                } catch (UnknownHostException ex) {
                    System.out.println("failed (2) on establishing a server channel " + i);
                    ex.printStackTrace();
                } catch (IOException ioe) {
                    System.out.println("failed on establishing server channel: " + i);
                    ioe.printStackTrace();
                }
            }
        }
    }


    public void start() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void stop() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}
