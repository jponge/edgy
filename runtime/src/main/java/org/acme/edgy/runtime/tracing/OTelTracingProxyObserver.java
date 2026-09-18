package org.acme.edgy.runtime.tracing;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jakarta.inject.Singleton;

import org.acme.edgy.runtime.api.Leg;
import org.acme.edgy.runtime.api.LegResponse;
import org.acme.edgy.runtime.api.ProxyObservation;
import org.acme.edgy.runtime.api.ProxyObserver;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.ScatterObservation;
import org.acme.edgy.runtime.api.ScatterRoute;
import org.acme.edgy.runtime.cache.CacheStatus;
import org.jboss.logging.Logger;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.quarkus.opentelemetry.runtime.QuarkusContextStorage;
import io.smallrye.common.vertx.VertxContext;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;
import io.vertx.httpproxy.ProxyContext;

/**
 * {@link ProxyObserver} that extends OTel spans with proxy-specific attributes.
 */
@Singleton
public class OTelTracingProxyObserver implements ProxyObserver {

    private static final Logger logger = Logger.getLogger(OTelTracingProxyObserver.class);

    static final AttributeKey<String> EDGY_ORIGIN_URL = AttributeKey.stringKey("edgy.origin.url");
    static final AttributeKey<String> EDGY_ORIGIN_ID = AttributeKey.stringKey("edgy.origin.id");
    static final AttributeKey<String> EDGY_ROUTE = AttributeKey.stringKey("edgy.route");
    static final AttributeKey<String> EDGY_SCATTER_ROUTE = AttributeKey.stringKey("edgy.scatter.route");
    static final AttributeKey<Long> EDGY_SCATTER_LEG_COUNT = AttributeKey.longKey("edgy.scatter.leg.count");
    static final AttributeKey<String> EDGY_SCATTER_ORIGINS = AttributeKey.stringKey("edgy.scatter.origins");
    static final AttributeKey<Long> EDGY_SCATTER_LEGS_SUCCEEDED = AttributeKey.longKey("edgy.scatter.legs.succeeded");
    static final AttributeKey<Long> EDGY_SCATTER_LEGS_FAILED = AttributeKey.longKey("edgy.scatter.legs.failed");
    static final AttributeKey<Long> EDGY_LEG_STATUS = AttributeKey.longKey("edgy.leg.status");
    static final AttributeKey<String> EDGY_CACHE_STATUS = AttributeKey.stringKey("edgy.cache.status");

    private final Tracer tracer;

    OTelTracingProxyObserver(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public ProxyObservation observe(ProxyContext context, Route route) {
        Span span = Span.current();
        if (!span.isRecording()) {
            logger.warn("No recording span available, skipping edgy tracing attributes");
            return ProxyObservation.NOOP;
        }

        span.setAttribute(EDGY_ORIGIN_URL, route.origin().uri());
        span.setAttribute(EDGY_ORIGIN_ID, route.origin().identifier());
        span.setAttribute(EDGY_ROUTE, route.path());

        // cache status is only known once the chain unwinds
        return new ProxyObservation() {
            @Override
            public void end(ProxyContext context) {
                recordCacheStatus(span, context);
            }

            @Override
            public void error(ProxyContext context, Throwable error) {
                recordCacheStatus(span, context);
            }
        };
    }

    private static void recordCacheStatus(Span span, ProxyContext context) {
        CacheStatus status = context.get(CacheStatus.CONTEXT_KEY, CacheStatus.class);
        if (status != null) {
            span.setAttribute(EDGY_CACHE_STATUS, status.name());
        }
    }

    @Override
    public ScatterObservation observeScatter(RoutingContext context, ScatterRoute scatterRoute) {
        Span serverSpan = Span.current();
        if (!serverSpan.isRecording()) {
            return ScatterObservation.NOOP;
        }

        serverSpan.setAttribute(EDGY_SCATTER_ROUTE, scatterRoute.path());
        serverSpan.setAttribute(EDGY_SCATTER_LEG_COUNT, (long) scatterRoute.legs().size());
        String origins = scatterRoute.legs().stream()
                .map(leg -> leg.origin().identifier())
                .collect(Collectors.joining(","));
        serverSpan.setAttribute(EDGY_SCATTER_ORIGINS, origins);

        return new ScatterObservation() {
            @Override
            public Future<LegResponse> wrapLeg(Leg leg, Supplier<Future<LegResponse>> execution) {
                Span legSpan = tracer.spanBuilder("edgy scatter leg: " + leg.origin().identifier())
                        .startSpan();
                legSpan.setAttribute(EDGY_ORIGIN_ID, leg.origin().identifier());
                legSpan.setAttribute(EDGY_ORIGIN_URL, leg.origin().uri());

                Context otelContext = Context.current().with(legSpan);
                io.vertx.core.Context currentContext = Vertx.currentContext();

                Future<LegResponse> result;
                if (currentContext != null) {
                    // the OTel context is stored as Vert.x context-local data, so all legs sharing
                    // the request context would mean last-one-wins - give each leg a duplicated
                    // context to hold its own span
                    io.vertx.core.Context legContext = VertxContext.createNewDuplicatedContext(currentContext);
                    QuarkusContextStorage.INSTANCE.attach(legContext, otelContext);
                    Promise<LegResponse> promise = Promise.promise();
                    // run the leg *on* that context so everything it starts downstream
                    // (client spans, traceparent headers) is parented to legSpan
                    legContext.runOnContext(v -> execution.get().onComplete(promise));
                    result = promise.future();
                } else {
                    // called off a Vert.x context - no context to duplicate, just fire the leg
                    result = execution.get();
                }

                // the span stays open until the leg settles, whatever the outcome
                return result.onComplete(ar -> {
                    if (ar.succeeded()) {
                        LegResponse response = ar.result();
                        if (response.succeeded()) {
                            legSpan.setAttribute(EDGY_LEG_STATUS, (long) response.statusCode());
                        } else {
                            // leg completed but carries a failure instead of a response
                            legSpan.recordException(response.failure());
                            legSpan.setStatus(StatusCode.ERROR);
                        }
                    } else {
                        // leg future failed - transport error, guard rejection, timeout
                        legSpan.recordException(ar.cause());
                        legSpan.setStatus(StatusCode.ERROR);
                    }
                    legSpan.end();
                });
            }

            @Override
            public void end(List<LegResponse> responses) {
                long succeeded = responses.stream().filter(LegResponse::succeeded).count();
                serverSpan.setAttribute(EDGY_SCATTER_LEGS_SUCCEEDED, succeeded);
                serverSpan.setAttribute(EDGY_SCATTER_LEGS_FAILED, responses.size() - succeeded);
            }

            @Override
            public void error(Throwable error) {
                serverSpan.recordException(error);
            }
        };
    }
}
