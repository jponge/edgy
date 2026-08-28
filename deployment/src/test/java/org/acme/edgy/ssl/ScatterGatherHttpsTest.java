package org.acme.edgy.ssl;

import static org.acme.edgy.runtime.api.utils.StatusCode.OK;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Collectors;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import org.acme.edgy.runtime.api.Leg;
import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.acme.edgy.runtime.api.ScatterRoute;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;
import io.smallrye.certs.Format;
import io.smallrye.certs.junit5.Certificate;
import io.smallrye.certs.junit5.Certificates;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;

@Certificates(baseDir = "target/certs", certificates = @Certificate(name = "edgy", password = "password", formats = Format.PKCS12, client = true))
class ScatterGatherHttpsTest {

    private static final String ORIGIN_URI = "https://localhost:" + HttpsServer.PORT;

    private static final String CONFIGURATION = """
            edgy.origin.scatter-https-leg-1.tls-configuration-name=my-tls-client
            edgy.origin.scatter-https-leg-2.tls-configuration-name=my-tls-client
            quarkus.tls.my-tls-client.key-store.p12.path=target/certs/edgy-client-keystore.p12
            quarkus.tls.my-tls-client.key-store.p12.password=password
            quarkus.tls.my-tls-client.trust-store.p12.path=target/certs/edgy-client-truststore.p12
            quarkus.tls.my-tls-client.trust-store.p12.password=password
            """;

    static class RoutingProvider {
        @Produces
        @Singleton
        RoutingConfiguration routingConfiguration() {
            return RoutingConfiguration.builder()
                    .addScatterRoute(new ScatterRoute("/scatter-https",
                            responses -> {
                                String composed = responses.stream()
                                        .map(r -> r.body().toString())
                                        .collect(Collectors.joining("|"));
                                return Future.succeededFuture(Buffer.buffer(composed));
                            },
                            new Leg(Origin.of("scatter-https-leg-1", ORIGIN_URI)),
                            new Leg(Origin.of("scatter-https-leg-2", ORIGIN_URI))))
                    .build();
        }
    }

    @RegisterExtension
    private static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
            .withApplicationRoot(jar -> jar.addClasses(RoutingProvider.class, HttpsServer.class)
                    .addAsResource(new StringAsset(CONFIGURATION), "application.properties"));

    @Test
    void scatterWithHttpsOriginsProxiesSuccessfully() throws Exception {
        try (HttpsServer httpsServer = new HttpsServer("target/certs/edgy-keystore.p12", "password",
                "target/certs/edgy-server-truststore.p12", "password")) {
            String response = RestAssured.given()
                    .when()
                    .get("/scatter-https")
                    .then()
                    .statusCode(OK)
                    .extract()
                    .body()
                    .asString();

            // Both legs hit the same HTTPS server, so we expect the response body repeated
            assertThat(response).isEqualTo(HttpsServer.RESPONSE_BODY + "|" + HttpsServer.RESPONSE_BODY);
        }
    }
}
