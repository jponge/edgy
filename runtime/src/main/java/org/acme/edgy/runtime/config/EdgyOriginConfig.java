package org.acme.edgy.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface EdgyOriginConfig {

    /**
     * The TLS configuration (bucket) name to use for this origin.
     */
    Optional<String> tlsConfigurationName();

}
