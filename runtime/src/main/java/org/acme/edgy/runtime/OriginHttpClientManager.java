package org.acme.edgy.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.inject.Singleton;

import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.config.EdgyOriginConfig;
import org.acme.edgy.runtime.config.EdgyRuntimeConfig;
import org.jboss.logging.Logger;

import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.tls.TlsConfigurationRegistry;
import io.quarkus.tls.runtime.config.TlsConfig;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;

@Singleton
public class OriginHttpClientManager {

    private static final Logger logger = Logger.getLogger(OriginHttpClientManager.class);

    private final Map<String, Origin> origins = new HashMap<>();
    private final Map<String, List<HttpClient>> tlsConfigToHttpClients = new HashMap<>();

    private final Vertx vertx;
    private final TlsConfigurationRegistry tlsConfigurationRegistry;
    private final EdgyRuntimeConfig edgyRuntimeConfig;

    OriginHttpClientManager(Vertx vertx, TlsConfigurationRegistry tlsConfigurationRegistry,
            EdgyRuntimeConfig edgyRuntimeConfig) {
        this.vertx = vertx;
        this.tlsConfigurationRegistry = tlsConfigurationRegistry;
        this.edgyRuntimeConfig = edgyRuntimeConfig;
    }

    public HttpClient getOrCreateHttpClient(Origin origin) {
        Origin existingOrigin = origins.get(origin.identifier());
        if (existingOrigin != null) {
            if (!existingOrigin.uri().equals(origin.uri())) {
                throw new IllegalStateException(
                        "Origin identifier '" + origin.identifier() + "' is already associated with a different URI: "
                                + existingOrigin.uri() + " vs " + origin.uri());
            }
        } else {
            origins.put(origin.identifier(), origin);
        }

        HttpClient existing = origin.httpClient();
        if (existing != null) {
            return existing;
        }

        HttpClientOptions options = new HttpClientOptions();
        EdgyOriginConfig originConfig = edgyRuntimeConfig.origins().get(origin.identifier());
        if (originConfig != null) {
            configureHttpClientOptions(options, originConfig);
        }
        HttpClient httpClient = vertx.createHttpClient(options);
        if (originConfig != null) {
            configureTlsOptions(origin, originConfig, httpClient);
        }
        origin.setHttpClient(httpClient);
        return httpClient;
    }

    public List<HttpClient> clientsUsingTlsConfig(String tlsConfigName) {
        return tlsConfigToHttpClients.getOrDefault(tlsConfigName, Collections.emptyList());
    }

    private void registerHttpClient(String tlsConfigName, HttpClient httpClient) {
        tlsConfigToHttpClients.computeIfAbsent(tlsConfigName, k -> new ArrayList<>())
                .add(httpClient);
    }

    private void configureHttpClientOptions(HttpClientOptions options, EdgyOriginConfig originConfig) {
        originConfig.idleTimeout().ifPresent(options::setIdleTimeout);
        originConfig.maxPoolSize().ifPresent(options::setMaxPoolSize);
    }

    private void configureTlsOptions(Origin origin, EdgyOriginConfig originConfig, HttpClient httpClient) {
        originConfig.tlsConfigurationName()
                .ifPresentOrElse(bucketName -> tlsConfigurationRegistry.get(bucketName).ifPresentOrElse(
                        tlsConfig -> {
                            if (!origin.supportsTls()) {
                                logger.warnf(
                                        "Origin '%s' does not support TLS, but a TLS configuration ('%s') was specified for it."
                                                + " Make sure to use the proper protocol for the origin.",
                                        origin.identifier(), bucketName);
                            }
                            registerHttpClient(bucketName, httpClient);
                            httpClient.updateSSLOptions(tlsConfig.getSSLOptions());
                        },
                        () -> {
                            throw new ConfigurationException("TLS configuration '" + bucketName
                                    + "' was specified for origin '" + origin.identifier()
                                    + "', but it does not exist.");
                        }),
                        () -> tlsConfigurationRegistry.getDefault().ifPresent(
                                tlsConfig -> {
                                    registerHttpClient(TlsConfig.DEFAULT_NAME, httpClient);
                                    httpClient.updateSSLOptions(tlsConfig.getSSLOptions());
                                }));
    }
}
