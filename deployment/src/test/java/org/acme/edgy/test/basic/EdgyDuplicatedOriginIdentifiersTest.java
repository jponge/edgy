package org.acme.edgy.test.basic;

import static org.junit.jupiter.api.Assertions.fail;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;

class EdgyDuplicatedOriginIdentifiersTest {

    static class RoutingProvider {
        @Produces
        @Singleton
        RoutingConfiguration basicRouting() {
            return new RoutingConfiguration()
                    .addRoute(
                            new Route("/hello", Origin.of("duplicated-origin-id", "http://localhost:8081/test/hello")))
                    .addRoute(new Route("/hi", Origin.of("duplicated-origin-id", "http://localhost:8081/test/hi")));
        }
    }

    @RegisterExtension
    private static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class))
            .setExpectedException(IllegalStateException.class);

    @Test
    void test_helloProxy() {
        fail("Expected exception due to duplicated origin identifiers");
    }
}
