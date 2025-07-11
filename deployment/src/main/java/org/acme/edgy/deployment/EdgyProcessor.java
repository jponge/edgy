package org.acme.edgy.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import org.acme.edgy.runtime.RouterConfigurator;

class EdgyProcessor {

    private static final String FEATURE = "edgy";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    AdditionalBeanBuildItem addRouterConfigurator() {
        return new AdditionalBeanBuildItem(RouterConfigurator.class);
    }
}
