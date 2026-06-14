package org.acme.edgy.runtime.config;

import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * Configuration for edgy.
 */
@ConfigMapping(prefix = "edgy")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface EdgyConfig {

    // TODO: use @LookupIfProperty to flip which model provider bean will be handling the router configuration
    enum Mode {
        API,
        CONFIGURATION,
    }

    /**
     * The configuration mode.
     */
    @WithDefault("api")
    Mode mode();

    @ConfigDocMapKey("origin-identifier")
    @WithName("origin")
    Map<String, EdgyOriginConfig> origins();

    /**
     * Tracing configuration.
     */
    TracingConfig tracing();

    /**
     * Logging configuration.
     */
    LoggingConfig logging();

    /**
     * Metrics configuration.
     */
    MetricsConfig metrics();

    interface TracingConfig {

        /**
         * Whether to enable tracing.
         */
        Optional<Boolean> enabled();
    }

    interface LoggingConfig {

        /**
         * Whether to enable logging.
         */
        @WithDefault("false")
        boolean enabled();
    }

    interface MetricsConfig {

        /**
         * Whether to enable metrics.
         */
        Optional<Boolean> enabled();
    }

}
