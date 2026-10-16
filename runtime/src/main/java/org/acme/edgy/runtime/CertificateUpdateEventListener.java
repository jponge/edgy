package org.acme.edgy.runtime;

import jakarta.enterprise.event.Observes;

import org.jboss.logging.Logger;

import io.quarkus.tls.CertificateUpdatedEvent;
import io.quarkus.tls.TlsConfiguration;
import io.vertx.core.http.HttpClient;

public class CertificateUpdateEventListener {

    private static final Logger logger = Logger.getLogger(CertificateUpdateEventListener.class);

    private final OriginManager originHttpClientManager;

    CertificateUpdateEventListener(OriginManager originHttpClientManager) {
        this.originHttpClientManager = originHttpClientManager;
    }

    void onCertificateUpdate(@Observes CertificateUpdatedEvent event) {
        String updatedTlsConfigurationName = event.name();
        TlsConfiguration updatedTlsConfiguration = event.tlsConfiguration();
        for (HttpClient httpClientToBeChanged : originHttpClientManager
                .clientsUsingTlsConfig(updatedTlsConfigurationName)) {
            httpClientToBeChanged.updateSSLOptions(updatedTlsConfiguration.getSSLOptions())
                    .onSuccess(
                            v -> logger.infof("Certificate reload succeeded for TLS configuration (bucket) name '%s'.",
                                    updatedTlsConfigurationName))
                    .onFailure(cause -> logger.errorf(cause,
                            "Certificate reload failed for TLS configuration (bucket) name '%s'.",
                            updatedTlsConfigurationName));
        }
    }
}
