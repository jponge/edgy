package org.acme.edgy.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;

import org.acme.edgy.runtime.CertificateUpdateEventListener;
import org.acme.edgy.runtime.DynamicRoutingConfigurationProvider;
import org.acme.edgy.runtime.EdgyRecorder;
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
    void setupAdditionalBeans(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans.produce(new AdditionalBeanBuildItem(CertificateUpdateEventListener.class));
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void setupShutdownTask(ShutdownContextBuildItem shutdown, EdgyRecorder recorder) {
        recorder.cleanUp(shutdown);
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
