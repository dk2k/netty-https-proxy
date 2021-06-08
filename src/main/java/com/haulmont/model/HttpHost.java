package com.haulmont.model;

public class HttpHost {

    private String host;
    private int port;

    public HttpHost(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

}
