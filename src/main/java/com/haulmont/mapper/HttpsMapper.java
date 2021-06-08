package com.haulmont.mapper;

import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import com.haulmont.factory.ProxyServerPipelineFactory;
import com.haulmont.model.HttpHost;
import org.jboss.netty.bootstrap.ServerBootstrap;

import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

public class HttpsMapper {

    public static void main(String[] args) {

        int localPort = 8100; // default value, our application will attempt to run on this port
        String remoteHost = "";
        int remotePort = 443; // default port for HTTPS

        HttpHost destHost = null;

        if (args.length >= 3) {
            localPort = Integer.parseInt(args[0]); // our JAR will listen on this port
            remoteHost = args[1];                  // our JAR will send requests to this host
            remotePort = Integer.parseInt(args[2]);

        } else {
            System.out.println("Expecting three command line arguments: 1.listen port 2. Destination host 3. Destination port");
            // exit early
            System.exit(1);
        }

        System.out.println("Listening on port " + localPort);
        System.out.println("Getting data from [" + remoteHost + "]:[" + remotePort +"]");
        destHost = new HttpHost(remoteHost, remotePort);

        Executor threadPool = Executors.newCachedThreadPool();
        ServerBootstrap bootstrap = new ServerBootstrap(new NioServerSocketChannelFactory(threadPool, threadPool));
        // creating a pipelineFactory
        bootstrap.setPipelineFactory(new ProxyServerPipelineFactory(threadPool, destHost));
        bootstrap.bind(new InetSocketAddress(localPort));
    }


    public static HttpHost getUpstreamProxy() {
        // I assume it's needed for a proxy chain, I don't need to implement it
        return null;
    }

}
