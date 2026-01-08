package org.acme.edgy.runtime;

import io.quarkus.tls.CertificateUpdatedEvent;
import io.quarkus.tls.TlsConfiguration;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;

import jakarta.enterprise.event.Observes;

import org.jboss.logging.Logger;

public class CertificateUpdateEventListener {

    private static final Logger logger = Logger.getLogger(CertificateUpdateEventListener.class);

    void onCertificateUpdate(@Observes CertificateUpdatedEvent event) {
        String updatedTlsConfigurationName = event.name();
        TlsConfiguration updatedTlsConfiguration = event.tlsConfiguration();
        for (HttpClient httpClientToBeChanged : EdgyRecorder
                .clientsUsingTlsConfig(updatedTlsConfigurationName)) {
            httpClientToBeChanged.updateSSLOptions(updatedTlsConfiguration.getSSLOptions())
                    .andThen(new Handler<AsyncResult<Boolean>>() {
                        @Override
                        public void handle(AsyncResult<Boolean> result) {
                            if (result.succeeded()) {
                                logger.infof(
                                        "Certificate reload succeeded for TLS configuration (bucket) name '%s'.",
                                        updatedTlsConfigurationName);
                                return;
                            }
                            logger.errorf(result.cause(),
                                    "Certificate reload failed for TLS configuration (bucket) name '%s'.",
                                    updatedTlsConfigurationName);
                        }
                    });

        }
    }
}
