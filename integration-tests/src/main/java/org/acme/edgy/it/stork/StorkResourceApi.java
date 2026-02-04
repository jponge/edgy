package org.acme.edgy.it.stork;

import static org.jboss.resteasy.reactive.RestResponse.StatusCode.NOT_FOUND;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.PfxOptions;
import io.vertx.ext.consul.ConsulClientOptions;
import io.vertx.ext.consul.ServiceOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.consul.ConsulClient;

@Path("/api/stork")
class StorkResourceApi {
    @ConfigProperty(name = "consul.host", defaultValue = "localhost")
    String host;
    @ConfigProperty(name = "consul.port", defaultValue = "8500")
    int port;

    @Inject
    Vertx vertx;

    static final int FIRST_SERVICE_PORT = 8082;
    static final int SECOND_SERVICE_PORT = 8083;
    static final int FIRST_SECURED_SERVICE_PORT = 8084;
    static final int SECOND_SECURED_SERVICE_PORT = 8085;
    
    @GET
    @Path("/services")
    public String startLoadBalancedServices() {
        vertx.createHttpServer()
            .requestHandler(req -> { 
                if (!req.path().equals("/test/hello")) {
                    req.response().setStatusCode(NOT_FOUND).endAndForget();
                    return;
                }
                req.response().endAndForget(String.valueOf(FIRST_SERVICE_PORT)); 
            }).listenAndAwait(FIRST_SERVICE_PORT);

            vertx.createHttpServer()
            .requestHandler(req -> { 
                if (!req.path().equals("/test/hello")) {
                    req.response().setStatusCode(NOT_FOUND).endAndForget();
                    return;
                }
                req.response().endAndForget(String.valueOf(SECOND_SERVICE_PORT));
            }).listenAndAwait(SECOND_SERVICE_PORT);

        return "services started";
    }

    @Path("/consul")
    @GET
    public String startConsul() {
        ConsulClient client = ConsulClient.create(vertx, new ConsulClientOptions().setHost(host).setPort(port));
        client.registerServiceAndAwait(
                new ServiceOptions().setPort(FIRST_SERVICE_PORT).setAddress("localhost").setName("my-service").setId("first"));
        client.registerServiceAndAwait(
                new ServiceOptions().setPort(SECOND_SERVICE_PORT).setAddress("localhost").setName("my-service").setId("second"));

        // Register secured services
        client.registerServiceAndAwait(
                new ServiceOptions().setPort(FIRST_SECURED_SERVICE_PORT).setAddress("localhost")
                        .setName("my-secured-service").setId("first-secured"));
        client.registerServiceAndAwait(
                new ServiceOptions().setPort(SECOND_SECURED_SERVICE_PORT).setAddress("localhost")
                        .setName("my-secured-service").setId("second-secured"));

        return "consul started";
    }

    @GET
    @Path("/secured-services")
    public String startSecuredLoadBalancedServices() throws Exception {
        // Create HTTPS server options with TLS
        HttpServerOptions httpsOptions1 = createHttpsServerOptions();
        HttpServerOptions httpsOptions2 = createHttpsServerOptions();

        vertx.createHttpServer(httpsOptions1)
                .requestHandler(req -> {
                    if (!req.path().equals("/test/hello")) {
                        req.response().setStatusCode(NOT_FOUND).endAndForget();
                        return;
                    }
                    req.response().endAndForget(String.valueOf(FIRST_SECURED_SERVICE_PORT));
                }).listenAndAwait(FIRST_SECURED_SERVICE_PORT);

        vertx.createHttpServer(httpsOptions2)
                .requestHandler(req -> {
                    if (!req.path().equals("/test/hello")) {
                        req.response().setStatusCode(NOT_FOUND).endAndForget();
                        return;
                    }
                    req.response().endAndForget(String.valueOf(SECOND_SECURED_SERVICE_PORT));
                }).listenAndAwait(SECOND_SECURED_SERVICE_PORT);

        return "secured services started";
    }

    private HttpServerOptions createHttpsServerOptions() throws Exception {
        HttpServerOptions options = new HttpServerOptions()
                .setSsl(true)
                .setHost("localhost")
                .setClientAuth(ClientAuth.REQUIRED);

        // Load keystore
        PfxOptions keystoreOptions = new PfxOptions();
        KeyStore keyStore = createKeyStore("target/certs/edgy-keystore.p12", "PKCS12", "password");
        keystoreOptions.setValue(asBuffer(keyStore, "password".toCharArray()));
        keystoreOptions.setPassword("password");
        options.setKeyCertOptions(keystoreOptions);

        // Load truststore
        PfxOptions truststoreOptions = new PfxOptions();
        KeyStore trustStore = createKeyStore("target/certs/edgy-server-truststore.p12", "PKCS12", "password");
        truststoreOptions.setValue(asBuffer(trustStore, "password".toCharArray()));
        truststoreOptions.setPassword("password");
        options.setTrustOptions(truststoreOptions);

        return options;
    }

    private static KeyStore createKeyStore(String path, String type, String password) {
        try {
            KeyStore keyStore = KeyStore.getInstance(type != null ? type : "JKS");
            if (password != null && !password.isEmpty()) {
                try (InputStream input = locateStream(path)) {
                    keyStore.load(input, password.toCharArray());
                } catch (CertificateException | NoSuchAlgorithmException | IOException e) {
                    throw new IllegalArgumentException(
                            "Failed to initialize key store from classpath resource " + path, e);
                }
                return keyStore;
            } else {
                throw new IllegalArgumentException("No password provided for keystore");
            }
        } catch (KeyStoreException e) {
            throw new RuntimeException(e);
        }
    }

    private static Buffer asBuffer(KeyStore keyStore, char[] password) {
        if (keyStore == null) {
            return null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            keyStore.store(out, password);
            return Buffer.buffer(out.toByteArray());
        } catch (IOException | NoSuchAlgorithmException | CertificateException
                | KeyStoreException e) {
            throw new RuntimeException("Failed to translate keystore to vert.x keystore", e);
        }
    }

    private static InputStream locateStream(String path) throws FileNotFoundException {
        if (path.startsWith("classpath:")) {
            path = path.replaceFirst("classpath:", "");
            InputStream resultStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
            if (resultStream == null) {
                resultStream = StorkResourceApi.class.getResourceAsStream(path);
            }

            if (resultStream == null) {
                throw new IllegalArgumentException(
                        "Classpath resource " + path + " not found for SSL configuration");
            } else {
                return resultStream;
            }
        } else {
            if (path.startsWith("file:")) {
                path = path.replaceFirst("file:", "");
            }

            File certificateFile = new File(path);
            if (!certificateFile.isFile()) {
                throw new IllegalArgumentException(
                        "Certificate file: " + path + " not found for SSL configuration");
            } else {
                return new FileInputStream(certificateFile);
            }
        }
    }
}
