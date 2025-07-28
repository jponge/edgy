package org.acme.edgy.runtime;

import static java.util.Objects.requireNonNullElse;

record OriginSpec(String protocol, String host, int port, String path) {
    static OriginSpec of(String spec) {
        String protocol = "http";
        String host = "localhost";
        int port = 8080;
        String path = "/";

        if (spec == null || spec.isEmpty()) {
            return new OriginSpec(protocol, host, port, path);
        }

        String remaining = spec;

        int protocolEnd = remaining.indexOf("://");
        if (protocolEnd != -1) {
            protocol = remaining.substring(0, protocolEnd);
            remaining = remaining.substring(protocolEnd + 3);
        }

        int pathStart = remaining.indexOf('/');
        String hostPortPart = pathStart != -1 ? remaining.substring(0, pathStart) : remaining;
        String pathPart = pathStart != -1 ? remaining.substring(pathStart) : "/";

        int portSeparator = hostPortPart.indexOf(':');
        if (portSeparator != -1 && portSeparator < hostPortPart.length() - 1) {
            String potentialHost = hostPortPart.substring(0, portSeparator);
            String potentialPort = hostPortPart.substring(portSeparator + 1);
            try {
                int parsedPort = Integer.parseInt(potentialPort);
                host = potentialHost;
                port = parsedPort;
            } catch (NumberFormatException e) {
                host = hostPortPart;
            }
        } else {
            host = hostPortPart;
        }

        if (!pathPart.isEmpty()) {
            path = pathPart;
        }

        return new OriginSpec(protocol, host, port, path);
    }
}