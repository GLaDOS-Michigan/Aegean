/*
 *    CHECKED BY ALLEN 2011.04.19
 */

package network.netty;

import network.NetworkQueue;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;

/**
 * @author manos
 */
public class PipelineFactory implements ChannelPipelineFactory {

    NetworkQueue NWQ;

    public PipelineFactory(NetworkQueue nwq) {
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
