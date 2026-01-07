package org.acme.edgy.ssl;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.PfxOptions;

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
import java.util.concurrent.TimeUnit;

class HttpsServer implements AutoCloseable {
    private final HttpServer server;
    private final Vertx vertx;
    public static final int PORT = 63805;
    public static final String RESPONSE_BODY = "hello SSL world";

    HttpsServer(String keystorePath, String keystorePassword, String truststorePath,
            String truststorePassword) throws Exception {
        vertx = Vertx.vertx();
        HttpServerOptions options = new HttpServerOptions().setSsl(true).setHost("localhost");

        if (keystorePath != null) {
            PfxOptions keystoreOptions = new PfxOptions();
            KeyStore keyStore = createKeyStore(keystorePath, "PKCS12", keystorePassword);
            keystoreOptions.setValue(asBuffer(keyStore, keystorePassword.toCharArray()));
            keystoreOptions.setPassword(keystorePassword);
            options.setKeyCertOptions(keystoreOptions);
        }

        if (truststorePath != null) {
            options.setClientAuth(ClientAuth.REQUIRED);
            PfxOptions truststoreOptions = new PfxOptions();
            KeyStore trustStore = createKeyStore(truststorePath, "PKCS12", truststorePassword);
            truststoreOptions.setValue(asBuffer(trustStore, truststorePassword.toCharArray()));
            truststoreOptions.setPassword(truststorePassword);
            options.setTrustOptions(truststoreOptions);
        }

        server = vertx.createHttpServer(options).requestHandler(request -> {
            request.response().send("hello SSL world");
        }).listen(PORT).toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    @Override
    public void close() {
        server.close();
        vertx.close().toCompletionStage().toCompletableFuture().join();
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
                resultStream = HttpsServer.class.getResourceAsStream(path);
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
