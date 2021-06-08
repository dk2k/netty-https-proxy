package com.haulmont.handler;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import java.util.List;
import java.util.zip.GZIPInputStream;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelStateEvent;

import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpHeaders;

// this class handles outgoing connections to OS
@ChannelPipelineCoverage("one")
public class RemoteHostHandler extends SimpleChannelUpstreamHandler {

    private HttpRequest	request = null;
    private HttpResponse	response = null;

    public RemoteHostHandler(HttpRequest request) {
        this.request = request;
    }

    public HttpResponse getResponse() {
        return response;
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        // Connection to OS established, forwarding the client's request into it
        e.getChannel().write(request);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        response = (HttpResponse) e.getMessage();
        // Got response from OS, unzipping if necessary

        List<String> encodings = response.getHeaders(HttpHeaders.Names.CONTENT_ENCODING);
        if (response.getStatus().equals(HttpResponseStatus.OK) &&
                !encodings.isEmpty() && encodings.contains(HttpHeaders.Values.GZIP)) {

            ChannelBuffer in = response.getContent();
            int compressedLength = in.readableBytes();

            GZIPInputStream gzip = new GZIPInputStream(
                    new ByteArrayInputStream(in.toByteBuffer().array(), 0, compressedLength));
            ChannelBuffer out = ChannelBuffers.dynamicBuffer();
            byte[] buf = new byte[1024];
            int read;

            try {
                while ((read = gzip.read(buf)) > 0) {
                    out.writeBytes(buf, 0, read);
                }
            } catch (IOException ex) {}

            in.clear();
            response.setContent(out);

            encodings.remove(HttpHeaders.Values.GZIP);
            if (encodings.isEmpty()) {
                response.removeHeader(HttpHeaders.Names.CONTENT_ENCODING);
            }

            response.setHeader(HttpHeaders.Names.CONTENT_LENGTH, String.valueOf(out.readableBytes()));
        }

        // we can process the http response here, say inject some html markup

        // logging
        System.err.println("");
        System.err.println(request);
        System.err.println("");
        System.err.println(response);
        System.err.println("");

        e.getChannel().close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        System.out.println("Unexpected exception from remote host handler: " + e.getCause());
        response = ProxyServerHandler.createErrorResponse("Error while processing request");
        e.getChannel().close();
    }

}
