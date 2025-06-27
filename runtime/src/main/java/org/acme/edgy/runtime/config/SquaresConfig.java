package org.acme.edgy.runtime.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithParentName;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@ConfigMapping(prefix = "squares")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface SquaresConfig {

    @WithParentName
    Map<String, Square> squares();

    interface Square {
        int side();
    }
}
