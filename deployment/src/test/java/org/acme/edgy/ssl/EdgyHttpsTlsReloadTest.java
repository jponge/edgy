package org.acme.edgy.ssl;

import static org.hamcrest.CoreMatchers.is;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.BAD_GATEWAY;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.OK;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.PathMode;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.tls.CertificateUpdatedEvent;
import io.quarkus.tls.TlsConfiguration;
import io.quarkus.tls.TlsConfigurationRegistry;
import io.restassured.RestAssured;
import io.smallrye.certs.Format;
import io.smallrye.certs.junit5.Certificate;
import io.smallrye.certs.junit5.Certificates;

@Certificates(baseDir = "target/certs", certificates = {
        @Certificate(name = "reload-edgy", password = "password", formats = Format.PKCS12, client = true),
        @Certificate(name = "wrong-reload-edgy", password = "password", formats = Format.PKCS12, client = true) })
class EdgyHttpsTlsReloadTest {

    private static final File temp = new File("target/test-certificates-" + UUID.randomUUID());
    private static final String TLS_BUCKET_NAME = "my-tls-client";

    private static final String ORIGIN_URI = "https://localhost:" + HttpsServer.PORT;

    static class RoutingProvider {
        @Produces
        RoutingConfiguration routingConfiguration() {
            return new RoutingConfiguration().addRoute(new Route("/secure",
                    Origin.of("origin-1", ORIGIN_URI),
                    PathMode.FIXED));
        }
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(HttpsServer.class, RoutingProvider.class))
            .overrideRuntimeConfigKey("loc", temp.getAbsolutePath())
            .overrideRuntimeConfigKey("edgy.origin.origin-1.tls-configuration-name",
                    TLS_BUCKET_NAME)
            .overrideRuntimeConfigKey("quarkus.tls." + TLS_BUCKET_NAME + ".key-store.p12.path",
                    temp.getAbsolutePath() + "/tls.p12")
            .overrideRuntimeConfigKey("quarkus.tls." + TLS_BUCKET_NAME + ".key-store.p12.password",
                    "password")
            .overrideRuntimeConfigKey("quarkus.tls." + TLS_BUCKET_NAME + ".trust-all", "true")
            .setBeforeAllCustomizer(() -> {
                try {
                    temp.mkdirs();
                    Files.copy(
                            new File("target/certs/wrong-reload-edgy-client-keystore.p12")
                                    .toPath(),
                            new File(temp, "/tls.p12").toPath());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

    @ConfigProperty(name = "loc")
    File certs;

    @Inject
    Event<CertificateUpdatedEvent> event;

    @Inject
    TlsConfigurationRegistry tlsConfigurationRegistry;

    @Test
    void testTlsReload() throws Exception {
        try (HttpsServer httpsServer = new HttpsServer("target/certs/reload-edgy-keystore.p12",
                "password", "target/certs/reload-edgy-server-truststore.p12", "password")) {
            RestAssured.given().when().get("/secure").then().statusCode(BAD_GATEWAY);

            // replace the bad certificate with the correct one
            Files.copy(new File("target/certs/reload-edgy-client-keystore.p12").toPath(),
                    new File(certs, "/tls.p12").toPath(), StandardCopyOption.REPLACE_EXISTING);

            TlsConfiguration config = tlsConfigurationRegistry.get(TLS_BUCKET_NAME).orElseThrow();
            assertTrue(config.reload());
            event.fire(new CertificateUpdatedEvent(TLS_BUCKET_NAME, config));
            RestAssured.given().when().get("/secure").then().statusCode(OK)
                    .body(is(HttpsServer.RESPONSE_BODY));
        }
    }
}
