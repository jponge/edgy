package org.acme.edgy.runtime.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithParentName;

import java.util.Map;

@ConfigMapping(prefix = "edgy")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface EdgyConfig {

    String host();

    int port();

    Foo foo();

    interface Foo {
        String bar();
    }

    Map<String, Flex> flexes();

    interface Flex {

        String id();

        Map<String, String> extra();
    }
}
