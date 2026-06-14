package org.acme.edgy.deployment;

import java.util.function.BooleanSupplier;

import org.acme.edgy.runtime.CertificateUpdateEventListener;
import org.acme.edgy.runtime.DynamicRoutingConfigurationProvider;
import org.acme.edgy.runtime.OriginHttpClientManager;
import org.acme.edgy.runtime.RouterConfigurator;
import org.acme.edgy.runtime.config.EdgyBuildTimeConfig;
import org.acme.edgy.runtime.logging.LoggingProxyObserver;
import org.acme.edgy.runtime.metrics.MicrometerMetricsProxyObserver;
import org.acme.edgy.runtime.tracing.OTelTracingProxyObserver;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class EdgyProcessor {

    private static final Logger logger = Logger.getLogger(EdgyProcessor.class);

    private static final String FEATURE = "edgy";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void setupAdditionalBeans(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans.produce(new AdditionalBeanBuildItem(CertificateUpdateEventListener.class));
        additionalBeans.produce(new AdditionalBeanBuildItem(OriginHttpClientManager.class));
        additionalBeans.produce(new AdditionalBeanBuildItem(RouterConfigurator.class));
    }

    @BuildStep(onlyIf = IsDynamicallyConfigured.class)
    AdditionalBeanBuildItem addDynamicRoutingProvider() {
        return new AdditionalBeanBuildItem(DynamicRoutingConfigurationProvider.class);
    }

    static class IsDynamicallyConfigured implements BooleanSupplier {

        EdgyBuildTimeConfig config;

        @Override
        public boolean getAsBoolean() {
            return config.mode() == EdgyBuildTimeConfig.Mode.CONFIGURATION;
        }
    }

    @BuildStep
    void setupTracingObserver(EdgyBuildTimeConfig config, Capabilities capabilities,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        boolean otelPresent = capabilities.isPresent(Capability.OPENTELEMETRY_TRACER);
        boolean enabled = config.tracing().enabled().orElse(otelPresent);
        if (!enabled || !otelPresent) {
            return;
        }

        boolean vertxHttpInstrumentation = ConfigProvider.getConfig()
                .getOptionalValue("quarkus.otel.instrument.vertx-http", Boolean.class)
                .orElse(true);
        if (!vertxHttpInstrumentation) {
            logger.warn("Edgy tracing is enabled but 'quarkus.otel.instrument.vertx-http' is disabled. "
                    + "Edgy tracing attributes will not be added because no parent SERVER span is available.");
            return;
        }

        additionalBeans.produce(new AdditionalBeanBuildItem(OTelTracingProxyObserver.class));
    }

    @BuildStep
    void setupMetricsObserver(EdgyBuildTimeConfig config, Capabilities capabilities,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        boolean metricsPresent = capabilities.isPresent(Capability.METRICS);
        boolean enabled = config.metrics().enabled().orElse(metricsPresent);
        if (!enabled || !metricsPresent) {
            return;
        }
        additionalBeans.produce(new AdditionalBeanBuildItem(MicrometerMetricsProxyObserver.class));
    }

    @BuildStep
    void setupLoggingObserver(EdgyBuildTimeConfig config,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        if (!config.logging().enabled()) {
            return;
        }
        additionalBeans.produce(new AdditionalBeanBuildItem(LoggingProxyObserver.class));
    }
}
