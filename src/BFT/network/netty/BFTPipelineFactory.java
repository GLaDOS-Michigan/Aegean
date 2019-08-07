/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package BFT.network.netty;

import BFT.network.NetworkQueue;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;

/**
 * @author manos
 */
public class BFTPipelineFactory implements ChannelPipelineFactory {

    NetworkQueue NWQ;

    public BFTPipelineFactory(NetworkQueue nwq) {
        NWQ = nwq;
    }

    public ChannelPipeline getPipeline() {
        ChannelPipeline pipeline = Channels.pipeline();
        //System.out.println(pipeline.getClass());
        ServerHandler handler = new ServerHandler(NWQ);
        MessageDecoder decoder = new MessageDecoder();
        pipeline.addLast("decoder", decoder);
        pipeline.addLast("handler", handler);
        return pipeline;
    }


}
