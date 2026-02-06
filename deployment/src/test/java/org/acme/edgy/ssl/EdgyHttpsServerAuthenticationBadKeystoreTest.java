package org.acme.edgy.ssl;

import static org.jboss.resteasy.reactive.RestResponse.StatusCode.BAD_GATEWAY;

import jakarta.enterprise.inject.Produces;

import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.PathMode;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import io.smallrye.certs.Format;
import io.smallrye.certs.junit5.Certificate;
import io.smallrye.certs.junit5.Certificates;

@Certificates(baseDir = "target/certs", certificates = {
        @Certificate(name = "edgy", password = "password", formats = Format.PKCS12, client = true),
        @Certificate(name = "wrong-edgy", password = "wrong-password", formats = Format.PKCS12, client = true) })
class EdgyHttpsServerAuthenticationBadKeystoreTest {

    private static final String ORIGIN_URI = "https://localhost:" + HttpsServer.PORT;

    // last two lines of code are for testing the precedence - explicit tls bucket
    // name > default
    private static final String CONFIGURATION = """
            edgy.origin.origin-1.tls-configuration-name=my-tls-client
            quarkus.tls.my-tls-client.trust-store.p12.path=target/certs/edgy-client-truststore.p12
            quarkus.tls.my-tls-client.trust-store.p12.password=password
            quarkus.tls.key-store.p12.path=target/certs/wrong-edgy-client-truststore.p12
            quarkus.tls.key-store.p12.password=wrong-password
            """;

    static class RoutingProvider {
        @Produces
        RoutingConfiguration routingConfiguration() {
            return new RoutingConfiguration().addRoute(new Route("/secure",
                    Origin.of("origin-1", ORIGIN_URI),
                    PathMode.FIXED));
        }
    }

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClasses(RoutingProvider.class, HttpsServer.class)
                    .addAsResource(new StringAsset(CONFIGURATION), "application.properties"));

    @Test
    void serverAuthentication_badKeystore() throws Exception {
        try (HttpsServer httpsServer = new HttpsServer("target/certs/wrong-edgy-keystore.p12",
                "wrong-password", null, null)) {
            RestAssured.given().when().get("/secure").then().statusCode(BAD_GATEWAY);
        }
    }
}
