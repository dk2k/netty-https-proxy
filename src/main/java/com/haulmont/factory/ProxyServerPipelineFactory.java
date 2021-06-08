package com.haulmont.factory;

import java.util.concurrent.Executor;

import com.haulmont.handler.ProxyServerHandler;
import com.haulmont.model.HttpHost;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;

import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;


// this class handles requests from the client
public class ProxyServerPipelineFactory implements ChannelPipelineFactory {

    private Executor threadPool = null;
    private HttpHost destHost = null;

    public ProxyServerPipelineFactory(Executor threadPool, HttpHost destHost) {
        this.threadPool = threadPool;
        this.destHost = destHost;
    }

    @Override
    public ChannelPipeline getPipeline() throws Exception {
        ChannelPipeline p = Channels.pipeline();
        p.addLast("decoder", new HttpRequestDecoder());
        p.addLast("aggregator", new HttpChunkAggregator(1048576));
        p.addLast("encoder", new HttpResponseEncoder());
        // that's our custom handler
        p.addLast("handler", new ProxyServerHandler(threadPool, destHost));
        return p;
    }

}