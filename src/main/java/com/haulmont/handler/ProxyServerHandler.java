package com.haulmont.handler;

import java.net.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

import com.haulmont.factory.HttpsMapperSslContextFactory;
import com.haulmont.mapper.HttpsMapper;
import com.haulmont.model.HttpHost;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.*;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipelineCoverage;

import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;

import org.jboss.netty.handler.codec.http.HttpResponseDecoder;
import org.jboss.netty.handler.codec.http.HttpRequestEncoder;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;

import javax.net.ssl.SSLEngine;
import org.jboss.netty.handler.ssl.SslHandler;

/*
Handler of the incoming connections
Receives the request, transforms to HTTP 1.1 if needed, establishes an outgoing connection to OS
and requests data from the OS. OS's response is unzipped if necessary and is forwarded to the client.
There will be a redirect if OS sends response 301 or 302.
*/

@ChannelPipelineCoverage("one")
public class ProxyServerHandler extends SimpleChannelUpstreamHandler {

    private volatile Channel	proxyServerChannel = null;
    private Executor	threadPool = null;
    private String	remoteHost = null;
    private int	remotePort = 80; // default value
    private RemoteHostHandler	remoteHostHandler = null;

    private static AtomicLong requestCount = new AtomicLong(0);

    public ProxyServerHandler(Executor threadPool) {
        this.threadPool = threadPool;
    }

    public ProxyServerHandler(Executor threadPool, HttpHost destHost) {
        this.threadPool = threadPool;
        this.remoteHost = destHost.getHost();
        this.remotePort = destHost.getPort();
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        HttpRequest request = (HttpRequest) e.getMessage();

        long currRequestValue = requestCount.incrementAndGet();
        if (currRequestValue % 100 == 0) {
            // reporting every 100th request
            System.out.println("Num Of HTTP Requests: " + currRequestValue);
        }

        // keep alive not supported

        request.removeHeader("Proxy-Connection");
        request.setHeader("Connection", "close");

        if (request.getProtocolVersion() == HttpVersion.HTTP_1_0) {
            request = convertRequestTo1_1(request);
        }

        // here we can process the request, e.g. detect specific URI and respond using
        // writeResponseAndClose(ErrorResponse.create("unsupported uri")); and stop the processing

        ClientBootstrap bootstrap = new ClientBootstrap(new NioClientSocketChannelFactory(threadPool, threadPool));
        ChannelPipeline p = bootstrap.getPipeline();


        // Add SSL handler first to encrypt and decrypt everything.
        // In this example, we use a bogus certificate in the server side
        // and accept any invalid certificates in the client side.
        // You will need something more complicated to identify both
        // and server in the real world

        SSLEngine engine = HttpsMapperSslContextFactory.getClientContext().createSSLEngine();
        engine.setUseClientMode(true);
        // this one provides coding / encoding
        p.addLast("ssl", new SslHandler(engine));

        p.addLast("decoder", new HttpResponseDecoder());
        p.addLast("aggregator", new HttpChunkAggregator(1048576));
        p.addLast("encoder", new HttpRequestEncoder());
        remoteHostHandler = new RemoteHostHandler(request); // depends on the client's request
        p.addLast("handler", remoteHostHandler);

        proxyServerChannel = e.getChannel();

        HttpHost proxy = HttpsMapper.getUpstreamProxy(); // currently returns null, always
        InetSocketAddress dest = proxy == null ?
                new InetSocketAddress(remoteHost, remotePort) :
                new InetSocketAddress(proxy.getHost(), proxy.getPort());

        ChannelFuture connectFuture = bootstrap.connect(dest);

        connectFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {

                    future.getChannel().getCloseFuture().addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            writeResponseAndClose(remoteHostHandler.getResponse()); // here we get the response from OS and forward it to the client
                        }
                    });
                } else {
                    future.getChannel().close();
                    writeResponseAndClose(createErrorResponse("Could not connect to " + remoteHost + ":" + remotePort));
                }
            }
        });

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        System.out.println("Unexpected exception from proxy server handler: " + e.getCause());
        e.getChannel().close();
    }

    // sends the generated response into incoming connection (channel between HttpsMapper and the client)
    private void writeResponseAndClose(HttpResponse response) {
        if (response != null) {
            response.setHeader("Connection", "close");
            proxyServerChannel.write(response).addListener(ChannelFutureListener.CLOSE);
        } else {
            proxyServerChannel.close();
        }
    }

    // converts HTTP 1.0 requests into HTTP 1.1
    private HttpRequest convertRequestTo1_1(HttpRequest request) throws URISyntaxException {
        DefaultHttpRequest newReq = new DefaultHttpRequest(HttpVersion.HTTP_1_1, request.getMethod(), request.getUri());
        if (!request.getHeaderNames().isEmpty()) {
            for (String name : request.getHeaderNames()) {
                newReq.setHeader(name, request.getHeaders(name));
            }
        }
        if (!newReq.containsHeader(HttpHeaders.Names.HOST)) {
            URI url = new URI(newReq.getUri());
            String host = url.getHost();
            if (url.getPort() != -1) {
                host += ":" + url.getPort();
            }
            newReq.setHeader(HttpHeaders.Names.HOST, host);
        }
        newReq.setContent(request.getContent());
        return newReq;
    }

    // creates an http response with a specified text
    public static HttpResponse createErrorResponse(String errorText) {
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.setHeader(HttpHeaders.Names.CONTENT_TYPE, "text/html; charset=utf-8");
        ChannelBuffer buf = ChannelBuffers.copiedBuffer("<html><body><h3>" + errorText + "</h3></body></html>", "utf-8");
        response.setContent(buf);
        response.setHeader("Content-Length", String.valueOf(buf.readableBytes()));
        return response;
    }

}