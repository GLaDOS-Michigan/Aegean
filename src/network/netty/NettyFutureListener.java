/*
 * CHECKED BY ALLEN 2011.04.19.  This is used for reconnections and broken pipes/sockets.
 */

package network.netty;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;

import java.util.concurrent.ConcurrentLinkedQueue;

import static org.jboss.netty.buffer.ChannelBuffers.*;

/**
 * @author manos
 */
public class NettyFutureListener implements ChannelFutureListener {

    ConcurrentLinkedQueue messageQueue;
    Channel channel;

    public NettyFutureListener() {
        messageQueue = new ConcurrentLinkedQueue();
    }

    public Channel getChannel() {
        //if(channel!=null)
        //System.out.println(channel.getClass());
        return channel;
    }

    public void addMessage(byte[] m) {
        messageQueue.add(m);
    }

    public void operationComplete(ChannelFuture future) throws Exception {
        if (!future.isSuccess()) {
            System.out.println("Could not connect to channel");
            future.getCause().printStackTrace();
            clearChannel();
        } else {
            System.out.println("Channel connected successfully");
            Channel tmpChannel = future.getChannel();
            while (!messageQueue.isEmpty()) {
                byte[] msg = (byte[]) messageQueue.poll();
                ChannelBuffer buf = copiedBuffer(msg);
                //System.out.println("FutureNetty send "+System.currentTimeMillis());
                ChannelFuture lastWriteFuture = tmpChannel.write(buf);
                lastWriteFuture.addListener(CLOSE_ON_FAILURE);
            }
            channel = tmpChannel;
            channel.setInterestOps(Channel.OP_READ_WRITE);
        }
    }

    private void clearChannel() {
        channel = null;
    }
}
