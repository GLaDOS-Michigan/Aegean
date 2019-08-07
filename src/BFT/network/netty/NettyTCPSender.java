/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package BFT.network.netty;

import BFT.Parameters;
import BFT.membership.Membership;
import BFT.membership.Principal;
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
import java.util.concurrent.Executors;

import static org.jboss.netty.buffer.ChannelBuffers.*;

/**
 * @author manos
 */
public class NettyTCPSender implements NetworkSender {
    //private Hashtable<String, NettyFutureListener> socketTable;
    private NettyFutureListener[] clientSockets, filterSockets,
            orderSockets, verifierSockets, execSockets;
    private Membership membership;
    ClientBootstrap clientBootstrap;
    Parameters parameters;

    public NettyTCPSender(Parameters param, Membership members, int threadCount) {
        this.membership = members;
        this.parameters = param;
        clientBootstrap = new ClientBootstrap(
                new NioClientSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool(), threadCount));

        clientBootstrap.setOption("tcpNoDelay", true);
        clientBootstrap.setOption("keepAlive", true);
        RoleMap.initialize(parameters.getNumberOfClients(),
                parameters.getFilterCount(),
                parameters.getOrderCount(),
                parameters.getExecutionCount(),
                parameters.getVerifierCount());

        DiscardNetworkQueue dnq = new DiscardNetworkQueue();
        clientBootstrap.setPipelineFactory(new BFTPipelineFactory(dnq));

        //        socketTable = new Hashtable<String, NettyFutureListener>();

        clientSockets = new NettyFutureListener[parameters.getNumberOfClients()];
        filterSockets = new NettyFutureListener[parameters.getFilterCount()];
        orderSockets = new NettyFutureListener[parameters.getOrderCount()];
        verifierSockets = new NettyFutureListener[parameters.getVerifierCount()];
        execSockets = new NettyFutureListener[parameters.getExecutionCount()];

        for (int i = 0; i < parameters.getNumberOfClients(); i++) {
            NettyFutureListener nfl = new NettyFutureListener();
            //      socketTable.put(RoleMap.getRoleString(Role.CLIENT, i), nfl);
            clientSockets[i] = nfl;
        }
        for (int i = 0; i < parameters.getOrderCount(); i++) {
            NettyFutureListener nfl = new NettyFutureListener();
            //            socketTable.put(RoleMap.getRoleString(Role.ORDER, i), nfl);
            orderSockets[i] = nfl;
        }
        for (int i = 0; i < parameters.getFilterCount(); i++) {
            NettyFutureListener nfl = new NettyFutureListener();
            //            socketTable.put(RoleMap.getRoleString(Role.FILTER, i), nfl);
            filterSockets[i] = nfl;
        }
        for (int i = 0; i < parameters.getExecutionCount(); i++) {
            NettyFutureListener nfl = new NettyFutureListener();
            //            socketTable.put(RoleMap.getRoleString(Role.EXEC, i), nfl);
            execSockets[i] = nfl;
        }
        for (int i = 0; i < parameters.getVerifierCount(); i++) {
            NettyFutureListener nfl = new NettyFutureListener();
            //            socketTable.put(RoleMap.getRoleString(Role.VERIFIER, i), nfl);
            verifierSockets[i] = nfl;
        }
    }

    public void send(byte[] m, Role role, int index) {
        int[] tmpindices = new int[1];
        tmpindices[0] = index;
        if (role.equals(Role.CLIENT)) {
            for (int i = 0; i < membership.getClientNodes()[index].length; i++) {
                send(m, role, tmpindices, i);
            }
        } else {
            send(m, role, tmpindices, -1);
        }

    }

    public void send(byte[] m, Role role, int index, int subId) {
        int[] tmpindices = new int[1];
        tmpindices[0] = index;
        send(m, role, tmpindices, subId);
    }

    public void send(byte[] m, Role role, int[] indices) {
        if (role.equals(Role.CLIENT)) {
            throw new RuntimeException("Manos: I think this method was " +
                    "not intended for sending to multiple clients");
        } else {
            send(m, role, indices, -1);
        }
    }

    public void send(byte[] m, Role role, int[] indices, int subId) {
        //System.out.println("\tSending to "+role+"."+indices[0]+" subId = "+subId);
        byte[] lenBytes = UnsignedTypes.longToBytes((long) m.length);
        byte[] allBytes = new byte[m.length + 8]; // MARKER change
        // TLR 2009.1.23: Changed marker to be length of message
        ////System.err.println("\tsending "+m.length+" bytes");
        System.arraycopy(lenBytes, 0, allBytes, 0, 4);
        System.arraycopy(m, 0, allBytes, 4, m.length);
        System.arraycopy(lenBytes, 0, allBytes, m.length + 4, 4);

        NettyFutureListener[] list;
        switch (role) {
            case CLIENT:
                list = clientSockets;
                break;
            case ORDER:
                list = orderSockets;
                break;
            case FILTER:
                list = filterSockets;
                break;
            case EXEC:
                list = execSockets;
                break;
            case VERIFIER:
                list = verifierSockets;
                break;
            default:
                throw new RuntimeException("invalid role");
        }

        for (int i = 0; i < indices.length; i++) {
            int index = indices[i];
            //	    String socketName = RoleMap.getRoleString(role, index);//myRole.toString() + index;
            NettyFutureListener nfl = list[index];// = socketTable.get(socketName);

            Channel channel = nfl.getChannel();
            //System.out.println(socketName+" "+channel);
            Principal p = null;


            if (channel == null || !channel.isOpen()) {
                switch (role) {
                    case CLIENT:
                        p = membership.getClientNodes()[index][subId];
                        break;
                    case ORDER:
                        p = membership.getOrderNodes()[index];
                        break;
                    case EXEC:
                        p = membership.getExecNodes()[index];
                        break;
                    case FILTER:
                        p = membership.getFilterNodes()[index];
                        break;
                    case VERIFIER:
                        p = membership.getVerifierNodes()[index];
                        break;
                    default:
                        throw new RuntimeException("Unknown Role " + role);
                }

                System.out.println("Will try to connect to IP " + p.getIP() + " and port " + p.getPort());

                nfl.addMessage(allBytes);

                ChannelFuture future = clientBootstrap.connect(new InetSocketAddress(p.getIP(), p.getPort()));
                future.addListener(nfl);

                //channel = future.awaitUninterruptibly().getChannel();
                //socketTable.put(socketName, socket);

			/* this is the non-Netty way
			   try {
			   socket = SocketChannel.open();
			   socket.configureBlocking(Parameters.blockingSends);
			   socket.socket().setTcpNoDelay(true);
			   socket.connect(new InetSocketAddress(p.getIP(), p.getPort()));
			   socketTable.put(socketName, new CWrapper(socket));
			   }
			   catch (IOException e) {
			   System.err.println("Failed on connection to "+
			   myRole+"."+index);
			   wrapper.kill();
			   e.printStackTrace(System.err);
			   //System.exit(1);
			   }*/
            } else {
                ChannelBuffer buf = copiedBuffer(allBytes);
                //System.out.println("Netty Send "+System.currentTimeMillis()+" "+channel.getClass());
                ChannelFuture lastWriteFuture = channel.write(buf);
            }
        }
    }
}
