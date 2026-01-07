package org.acme.edgy.runtime;

import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.http.HttpClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Recorder
public class EdgyRecorder {

    private static final Map<String, List<HttpClient>> TLS_CONFIG_NAME_TO_VERTX_HTTP_CLIENTS = new ConcurrentHashMap<>();

    public static void registerHttpClient(String tlsConfigName, HttpClient httpClient) {
        TLS_CONFIG_NAME_TO_VERTX_HTTP_CLIENTS.computeIfAbsent(tlsConfigName, k -> new ArrayList<>())
                .add(httpClient);
    }

    public static List<HttpClient> clientsUsingTlsConfig(String tlsConfigName) {
        return TLS_CONFIG_NAME_TO_VERTX_HTTP_CLIENTS.getOrDefault(tlsConfigName, Collections.emptyList());
    }

    public void cleanUp(ShutdownContext shutdown) {
        shutdown.addShutdownTask(TLS_CONFIG_NAME_TO_VERTX_HTTP_CLIENTS::clear);
    }
}
