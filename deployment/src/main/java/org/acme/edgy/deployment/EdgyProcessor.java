package org.acme.edgy.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import org.acme.edgy.runtime.DynamicRoutingConfigurationProvider;
import org.acme.edgy.runtime.RouterConfigurator;
import org.acme.edgy.runtime.config.EdgyConfig;

import java.util.function.BooleanSupplier;

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

    @BuildStep(onlyIf = IsDynamicallyConfigured.class)
    AdditionalBeanBuildItem addDynamicRoutingProvider() {
        return new AdditionalBeanBuildItem(DynamicRoutingConfigurationProvider.class);
    }

    static class IsDynamicallyConfigured implements BooleanSupplier {

        EdgyConfig config;

        @Override
        public boolean getAsBoolean() {
            return config.mode() == EdgyConfig.Mode.CONFIGURATION;
        }
    }
}
