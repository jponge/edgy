package org.acme.edgy.runtime.api;

import io.vertx.core.http.HttpClient;
import io.vertx.core.http.RequestOptions;
import io.vertx.httpproxy.OriginRequestProvider;

import java.util.Objects;

import static org.acme.edgy.runtime.api.utils.StorkUtils.storkFuture;

public final class Origin {

    private static final Protocol DEFAULT_PROTOCOL = Protocol.http;
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 8080;
    private static final String EMPTY_PATH = "/";

    private static final String URI_SCHEME_SEPARATOR = "://";
    private static final char PORT_SEPARATOR = ':';

    private static final int MIN_PORT_NUMBER = 0;
    private static final int MAX_PORT_NUMBER = 65535;

    private final String identifier;

    private final Protocol protocol;
    private final String host;
    private final int port;
    private final String path;

    private HttpClient httpClient;

    private Origin(String identifier, Protocol protocol, String host, int port, String path) {
        this.identifier = identifier;
        this.protocol = protocol;
        this.host = host;
        this.port = port;
        this.path = path;
    }

    public static Origin of(String identifier, String uri) {
        Protocol protocol = canonizeProtocol(uri);
        String host = canonizeHost(uri);
        int port = canonizePort(uri);
        String path = canonizePath(uri);

        // stork should not have a port specified
        if (protocol == Protocol.stork && port != DEFAULT_PORT) {
            String remaining = uri;
            int protocolEnd = remaining.indexOf(URI_SCHEME_SEPARATOR);
            if (protocolEnd != -1) {
                remaining = remaining.substring(protocolEnd + URI_SCHEME_SEPARATOR.length());
            }
            int pathStart = remaining.indexOf('/');
            String hostPortPart = pathStart != -1 ? remaining.substring(0, pathStart) : remaining;
            if (hostPortPart.contains(String.valueOf(PORT_SEPARATOR))) {
                throw new IllegalArgumentException("Stork origin cannot specify a port");
            }
        }

        return new Origin(identifier, protocol, host, port, path);
    }

    public HttpClient httpClient() {
        return httpClient;
    }

    public void setHttpClient(HttpClient httpClient) {
        Objects.requireNonNull(httpClient, "httpClient");
        HttpClient existing = this.httpClient;
        if (existing != null && existing != httpClient) {
            throw new IllegalStateException("Origin already has an HttpClient assigned");
        }
        this.httpClient = httpClient;
    }

    public String identifier() {
        return identifier;
    }

    public Protocol protocol() {
        return protocol;
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public String path() {
        return path;
    }

    public OriginRequestProvider originRequestProvider() {
        return proxyContext -> switch (protocol) {
            case stork -> storkFuture(host, proxyContext);
            case http, https -> proxyContext.client().request(new RequestOptions().setHost(host)
                    .setPort(port).setSsl(protocol == Protocol.https));
        };
    }

    public String uri() {
        return protocol.name() + URI_SCHEME_SEPARATOR + host + PORT_SEPARATOR + port + path;
    }

    public boolean supportsTls() {
        return protocol == Protocol.https;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Origin other)) {
            return false;
        }
        return Objects.equals(uri(), other.uri());
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri());
    }

    @Override
    public String toString() {
        return "Origin{" +
                "identifier='" + identifier + '\'' +
                "protocol=" + protocol +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", path='" + path + '\'' +
                '}';
    }

    private static Protocol canonizeProtocol(String uri) {
        if (uri == null || uri.isBlank()) {
            return DEFAULT_PROTOCOL;
        }
        int protocolEnd = uri.indexOf(URI_SCHEME_SEPARATOR);
        if (protocolEnd == -1) {
            return DEFAULT_PROTOCOL;
        }
        String protocolStr = uri.substring(0, protocolEnd).toLowerCase();
        try {
            return Protocol.valueOf(protocolStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown protocol: " + protocolStr);
        }
    }

    private static String canonizeHost(String uri) {
        if (uri == null || uri.isBlank()) {
            return DEFAULT_HOST;
        }

        String remaining = uri;
        int protocolEnd = remaining.indexOf(URI_SCHEME_SEPARATOR);
        if (protocolEnd != -1) {
            remaining = remaining.substring(protocolEnd + URI_SCHEME_SEPARATOR.length());
        }

        int pathStart = remaining.indexOf('/');
        String hostPortPart = pathStart != -1 ? remaining.substring(0, pathStart) : remaining;

        int portSeparator = hostPortPart.indexOf(PORT_SEPARATOR);
        String host = portSeparator != -1 ? hostPortPart.substring(0, portSeparator) : hostPortPart;

        return host.isBlank() ? DEFAULT_HOST : host.trim().toLowerCase();
    }

    private static int canonizePort(String uri) {
        if (uri == null || uri.isBlank()) {
            return DEFAULT_PORT;
        }

        String remaining = uri;
        int protocolEnd = remaining.indexOf(URI_SCHEME_SEPARATOR);
        if (protocolEnd != -1) {
            remaining = remaining.substring(protocolEnd + URI_SCHEME_SEPARATOR.length());
        }

        int pathStart = remaining.indexOf('/');
        String hostPortPart = pathStart != -1 ? remaining.substring(0, pathStart) : remaining;

        int portSeparator = hostPortPart.indexOf(PORT_SEPARATOR);
        if (portSeparator == -1) {
            return DEFAULT_PORT;
        }

        if (portSeparator >= hostPortPart.length() - 1) {
            throw new IllegalArgumentException("Port separator ':' present but port number missing");
        }

        String portStr = hostPortPart.substring(portSeparator + 1);
        try {
            int port = Integer.parseInt(portStr);
            if (port < MIN_PORT_NUMBER || port > MAX_PORT_NUMBER) {
                throw new IllegalArgumentException("Port number out of range ("
                        + MIN_PORT_NUMBER + "-" + MAX_PORT_NUMBER + "): " + port);
            }
            return port;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid port number: " + portStr);
        }
    }

    private static String canonizePath(String uri) {
        if (uri == null || uri.isBlank()) {
            return EMPTY_PATH;
        }

        String remaining = uri;
        int protocolEnd = remaining.indexOf(URI_SCHEME_SEPARATOR);
        if (protocolEnd != -1) {
            remaining = remaining.substring(protocolEnd + URI_SCHEME_SEPARATOR.length());
        }

        int pathStart = remaining.indexOf('/');
        if (pathStart == -1) {
            return EMPTY_PATH;
        }

        String path = remaining.substring(pathStart);
        return path.isBlank() ? EMPTY_PATH : path;
    }
}