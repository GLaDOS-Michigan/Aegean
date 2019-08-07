/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package BFT.network.netty2;

import BFT.network.NetworkQueue;
import BFT.network.NetworkReceiver;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * @author manos
 */
public class NettyTCPReceiver implements NetworkReceiver {

    // The channel on which we'll accept connections
    ServerBootstrap bootstrap;


    ///  MEMBERSHIP needs to capture the parse of the xml files
    public NettyTCPReceiver(InetSocketAddress me, NetworkQueue NQ) {
        this(me, NQ, 1);
    }

    public NettyTCPReceiver(InetSocketAddress me, NetworkQueue NQ, int threadCount) {
        InetSocketAddress foo[] = new InetSocketAddress[1];
        foo[0] = me;
        init(foo, NQ, threadCount);
    }

    public NettyTCPReceiver(InetSocketAddress me[], NetworkQueue NQ) {
        this(me, NQ, 1);
    }

    public NettyTCPReceiver(InetSocketAddress me[], NetworkQueue NQ, int threadCount) {
        init(me, NQ, threadCount);
    }


    public void setTCPNoDelay(boolean nodelay) {
        bootstrap.setOption("child.tcpNoDelay", nodelay);
    }

    public void setKeepAlive(boolean alive) {
        bootstrap.setOption("child.keepAlive", alive);
    }

    private void init(InetSocketAddress me[], NetworkQueue NQ, int threadCount) {
        bootstrap = new ServerBootstrap(
                new NioServerSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool(),
                        threadCount));


        bootstrap.setOption("child.tcpNoDelay", true);
        bootstrap.setOption("child.keepAlive", true);

        bootstrap.setPipelineFactory(new PipelineFactory(NQ));

        for (int i = 0; i < me.length; i++) {

            // String IPport = new String(me[i].getHost().toString().split("/",0)[1]+":"+me[i].getPort());

            // String ipStr = me[i].getHost().toString().split("/", 0)[0];
            // System.out.println("Host name is:  "+ipStr);
            // System.out.println(ipStr.length());
            // if (ipStr.length() == 0){
            // 	ipStr =  me[i].getHost().toString().split("/", 0)[1];
            // 	System.out.println("Host IP is: "+ipStr);
            // }
            // String portStr = ""+me[i].getPort();
            //	    try{
            //		InetSocketAddress isa = new InetSocketAddress(InetAddress.getByName(ipStr), Integer.parseInt(portStr));
            System.out.println("Listening to " + me[i]);
            bootstrap.bind(me[i]);
            //	    } catch (UnknownHostException ex) {
            //System.out.println("failed (2) on establihsing a server channel "+me[i]);
            //ex.printStackTrace();
            //	    } catch (IOException ioe) {
            //System.out.println("failed on establishing server channel: "+me[i]);
            //ioe.printStackTrace();
            //}
        }
    }


    public void start() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void stop() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}
