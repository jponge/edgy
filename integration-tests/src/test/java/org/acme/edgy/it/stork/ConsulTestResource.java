package org.acme.edgy.it.stork;

import java.util.HashMap;
import java.util.Map;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class ConsulTestResource implements QuarkusTestResourceLifecycleManager {

    private static final String CONSUL_IMAGE = "consul:1.15.4";
    private GenericContainer<?> container;

    @Override
    public Map<String, String> start() {
        container = new GenericContainer<>(CONSUL_IMAGE)
                .withExposedPorts(8500, 8501)
                .withCommand("agent", "-dev", "-client=0.0.0.0", "-bind=0.0.0.0", "--https-port=8501")
                .waitingFor(Wait.forLogMessage(".*Synced node info.*", 1));

        container.start();

        Map<String, String> config = new HashMap<>();
        config.put("consul.host", container.getHost());
        config.put("consul.port", Integer.toString(container.getMappedPort(8500)));
        config.put("quarkus.stork.my-service.service-discovery.type", "consul");
        config.put("quarkus.stork.my-service.service-discovery.consul-host", container.getHost());
        config.put("quarkus.stork.my-service.service-discovery.consul-port",
                Integer.toString(container.getMappedPort(8500)));
        // TLS configuration for secured stork service
        config.put("quarkus.stork.my-secured-service.service-discovery.type", "consul");
        config.put("quarkus.stork.my-secured-service.service-discovery.consul-host", container.getHost());
        config.put("quarkus.stork.my-secured-service.service-discovery.consul-port",
                Integer.toString(container.getMappedPort(8500)));
        config.put("edgy.origin.secured-stork-origin.tls-configuration-name", "my-tls-client");
        config.put("quarkus.tls.my-tls-client.key-store.p12.path", "target/certs/edgy-client-keystore.p12");
        config.put("quarkus.tls.my-tls-client.key-store.p12.password", "password");
        config.put("quarkus.tls.my-tls-client.trust-store.p12.path", "target/certs/edgy-client-truststore.p12");
        config.put("quarkus.tls.my-tls-client.trust-store.p12.password", "password");

        return config;
    }

    @Override
    public void stop() {
        container.stop();
    }
}
