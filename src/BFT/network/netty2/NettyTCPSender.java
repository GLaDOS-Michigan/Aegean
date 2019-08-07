/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package BFT.network.netty2;

import BFT.Debug;
import BFT.network.DiscardNetworkQueue;
import BFT.network.NetworkSender;
import BFT.util.Role;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import util.UnsignedTypes;

import java.net.InetSocketAddress;
import java.util.Hashtable;
import java.util.concurrent.Executors;

import static org.jboss.netty.buffer.ChannelBuffers.*;

/**
 * @author manos
 */
public class NettyTCPSender implements NetworkSender {
    private Hashtable<Object, NettyFutureListener> nfls;
    private Hashtable<Object, ChannelFuture> pendingConnects;
    ClientBootstrap clientBootstrap;

    public NettyTCPSender() {
        this(1);
    }

    public void setTCPNoDelay(boolean nodelay) {
        clientBootstrap.setOption("tcpNoDelay", nodelay);
    }

    public void setKeepAlive(boolean alive) {
        clientBootstrap.setOption("keepAlive", alive);
    }


    public NettyTCPSender(int threadCount) {
        clientBootstrap = new ClientBootstrap(
                new NioClientSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool(), threadCount));

        clientBootstrap.setOption("tcpNoDelay", true);
        clientBootstrap.setOption("keepAlive", true);


        DiscardNetworkQueue dnq = new DiscardNetworkQueue();
        clientBootstrap.setPipelineFactory(new PipelineFactory(dnq));

        nfls = new Hashtable<Object, NettyFutureListener>();
        pendingConnects = new Hashtable<Object, ChannelFuture>();
    }


    // public void send(byte[] m, int rcptId){
    // 	send(m, membership.getPrincipal(rcptId);
    // }


    public void send(byte[] m, InetSocketAddress recipient) {

        Debug.info(Debug.MODULE_NETWORK, "\t\tSending to %s:%d",
                recipient.getHostName(),
                recipient.getPort());
        byte[] lenBytes = UnsignedTypes.longToBytes((long) m.length);
        byte[] allBytes = new byte[m.length + 8]; // MARKER change
        System.arraycopy(lenBytes, 0, allBytes, 0, 4);
        System.arraycopy(m, 0, allBytes, 4, m.length);
        System.arraycopy(lenBytes, 0, allBytes, m.length + 4, 4);
        // sending the bytes with a header/footer corresponding to length of actual bytes


        NettyFutureListener nfl = nfls.get(recipient);
        if (nfl == null) {
            synchronized (nfls) {
                nfl = nfls.get(recipient);
                if (nfl == null) {
                    nfls.put(recipient, new NettyFutureListener());
                }
                nfl = nfls.get(recipient);
            }
        }
        Channel channel = nfl.getChannel();
        if (channel == null || !channel.isOpen()) {
            synchronized (pendingConnects) {
                nfl.addMessage(allBytes);
                ChannelFuture future = pendingConnects.get(recipient);
                if (future == null || (future.isDone() &&
                        !future.getChannel().isOpen())) {
                    pendingConnects.remove(recipient);
                    Debug.info(Debug.MODULE_NETWORK, ("Attempting to connect to : " + recipient));
                    future = clientBootstrap.connect(recipient);
                    pendingConnects.put(recipient, future);
                }

                future.addListener(nfl);
            }
        } else {
            ChannelBuffer buf = copiedBuffer(allBytes);
            ChannelFuture lastWriteFuture = channel.write(buf);
        }
    }

    public void send(byte[] m, InetSocketAddress[] recipients) {
        Debug.warning(Debug.MODULE_NETWORK, "really ought to optimize this to avoid extraneous calls to system.arraycopy.  NettyTCPSender.send(byte[], principal[]");
        for (int i = 0; i < recipients.length; i++) {
            send(m, recipients[i]);
        }
    }

    @Override
    public void send(byte[] msg, Role role, int id) {
        // TODO Auto-generated method stub

    }

    @Override
    public void send(byte[] msg, Role role, int id, int subId) {
        // TODO Auto-generated method stub

    }

    @Override
    public void send(byte[] msg, Role role, int[] ids) {
        // TODO Auto-generated method stub

    }

}
