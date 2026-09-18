package org.acme.edgy.runtime.config;

import java.util.Optional;
import java.util.OptionalInt;

import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface EdgyOriginConfig {

    /**
     * The TLS configuration (bucket) name to use for this origin.
     */
    Optional<String> tlsConfigurationName();

    /**
     * The idle timeout for connections to this origin, in seconds.
     * If not set, the Vert.x default is used.
     */
    OptionalInt idleTimeout();

    /**
     * The maximum number of connections in the pool for this origin.
     * If not set, the Vert.x default (5) is used.
     */
    OptionalInt maxPoolSize();

    /**
     * Response caching for this origin.
     */
    EdgyOriginCacheConfig cache();

}
