package org.acme.edgy.runtime.config;

import java.util.Map;

import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "edgy")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface EdgyRuntimeConfig {

    @ConfigDocMapKey("origin-identifier")
    @WithName("origin")
    Map<String, EdgyOriginConfig> origins();
}
