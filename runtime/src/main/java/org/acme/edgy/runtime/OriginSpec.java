package org.acme.edgy.runtime;

import java.net.URI;
import java.net.URISyntaxException;

import static java.util.Objects.requireNonNullElse;

record OriginSpec(String protocol, String host, int port, String path) {
    static OriginSpec of(String spec) {
        try {
            URI uri = new URI(spec);
            String uScheme = uri.getScheme();
            String uHost = uri.getHost();
            int uPort = uri.getPort();
            String uPath = uri.getPath();
            String uQuery = uri.getQuery();
            String targetPath = requireNonNullElse(uPath, "/");
            if (uQuery != null) {
                targetPath += "?" + uQuery;
            }
            return new OriginSpec(
                    requireNonNullElse(uScheme, "http"),
                    requireNonNullElse(uHost, "localhost"),
                    uPort > 0 ? uPort : 8080,
                    targetPath);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
