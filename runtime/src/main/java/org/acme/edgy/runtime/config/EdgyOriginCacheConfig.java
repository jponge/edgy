package org.acme.edgy.runtime.config;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.configuration.MemorySize;
import io.smallrye.config.WithDefault;

/**
 * Response caching configuration for an origin.
 */
@ConfigGroup
public interface EdgyOriginCacheConfig {

    /**
     * Whether response caching is enabled for this origin.
     */
    @WithDefault("false")
    boolean enabled();

    /**
     * The maximum number of responses cached for this origin.
     * Once exceeded, the oldest stored entry is evicted.
     */
    @WithDefault("1000")
    int maxSize();

    /**
     * The largest response body the cache will hold.
     * <p>
     * Responses exceeding this size are streamed through uncached. The worst
     * case memory footprint is {@code max-size × max-entry-size}.
     */
    @WithDefault("1M")
    MemorySize maxEntrySize();

}
