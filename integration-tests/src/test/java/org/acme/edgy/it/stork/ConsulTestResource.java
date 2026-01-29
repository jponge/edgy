package org.acme.edgy.it.stork;

import java.util.Map;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class ConsulTestResource implements QuarkusTestResourceLifecycleManager {

    private static final String IMAGE = "consul:1.7";
    private GenericContainer<?> container;

    @Override
    public Map<String, String> start() {
        container = new GenericContainer<>(IMAGE)
                .withExposedPorts(8500, 8501)
                .withCommand("agent", "-dev", "-client=0.0.0.0", "-bind=0.0.0.0", "--https-port=8501")
                .waitingFor(Wait.forLogMessage(".*Synced node info.*", 1));

        container.start();

        return Map.of(
                "consul.host", container.getHost(),
                "consul.port", Integer.toString(container.getMappedPort(8500)),
                "quarkus.stork.my-service.service-discovery.type", "consul",
                "quarkus.stork.my-service.service-discovery.consul-host", container.getHost(),
                "quarkus.stork.my-service.service-discovery.consul-port", Integer.toString(container.getMappedPort(8500))
        );
    }

    @Override
    public void stop() {
        container.stop();
    }
}
