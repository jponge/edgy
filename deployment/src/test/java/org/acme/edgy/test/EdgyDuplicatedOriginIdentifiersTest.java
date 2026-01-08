package org.acme.edgy.test;

import io.quarkus.test.QuarkusUnitTest;
import jakarta.enterprise.inject.Produces;
import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.PathMode;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.fail;

class EdgyDuplicatedOriginIdentifiersTest {

    public static class RoutingProvider {
        @Produces
        RoutingConfiguration basicRouting() {
            return new RoutingConfiguration()
                    .addRoute(new Route("/hello", Origin.of("duplicated-origin-id", "http://localhost:8081/test/hello"),
                            PathMode.FIXED))
                    .addRoute(new Route("/hi", Origin.of("duplicated-origin-id", "http://localhost:8081/test/hi"),
                            PathMode.FIXED));
        }
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class))
            .setExpectedException(IllegalStateException.class);

    @Test
    public void test_helloProxy() {
        fail("Expected exception due to duplicated origin identifiers");
    }
}
