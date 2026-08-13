package org.acme.edgy.runtime.scatter;

import java.util.ArrayList;
import java.util.List;

import org.acme.edgy.runtime.api.FailureMode;
import org.acme.edgy.runtime.api.Leg;
import org.acme.edgy.runtime.api.LegResponse;
import org.acme.edgy.runtime.api.ScatterRoute;
import org.acme.edgy.runtime.interceptors.scatter.MethodBodyInterceptor;
import org.jboss.logging.Logger;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyRequest;

public class ScatterHandler implements io.vertx.core.Handler<RoutingContext> {

    private static final Logger logger = Logger.getLogger(ScatterHandler.class);

    private final ScatterRoute scatterRoute;
    private final List<LegDefinition> legDefinitions;

    public record LegDefinition(
            Leg leg,
            HttpClient httpClient,
            List<ProxyInterceptor> interceptors) {
    }

    public ScatterHandler(ScatterRoute scatterRoute, List<LegDefinition> legDefinitions) {
        this.scatterRoute = scatterRoute;
        this.legDefinitions = legDefinitions;
    }

    @Override
    public void handle(RoutingContext rc) {
        if (!scatterRoute.predicate().test(rc)) {
            rc.next();
            return;
        }

        enrichSpanWithScatterAttributes();

        HttpServerRequest request = rc.request();
        Buffer bodyAccumulator = Buffer.buffer();
        request.handler(bodyAccumulator::appendBuffer);
        request.endHandler(v -> {
            fireLegs(request, bodyAccumulator)
                    .compose(responses -> scatterRoute.composer().apply(responses))
                    .onSuccess(composedBody -> rc.response()
                            .setStatusCode(200)
                            .end(composedBody))
                    .onFailure(error -> {
                        logger.errorf(error, "Scatter/gather failed for route %s", scatterRoute.path());
                        if (!rc.response().ended()) {
                            rc.response().setStatusCode(502).end();
                        }
                    });
        });
        request.resume();
    }

    private Future<List<LegResponse>> fireLegs(HttpServerRequest originalRequest, Buffer bufferedBody) {
        List<Future<LegResponse>> legFutures = new ArrayList<>();

        for (LegDefinition legDef : legDefinitions) {
            Body body = bufferedBody != null && bufferedBody.length() > 0
                    ? Body.body(bufferedBody)
                    : null;

            ScatterLegRequest legRequest = new ScatterLegRequest(originalRequest);
            ProxyRequest proxyRequest = ProxyRequest.reverseProxy(legRequest);

            ScatterLegContext context = new ScatterLegContext(
                    proxyRequest,
                    legDef.httpClient(),
                    legDef.leg().origin().originRequestProvider(),
                    legDef.leg().origin().identifier(),
                    legDef.interceptors());

            context.set(MethodBodyInterceptor.BUFFERED_BODY_KEY, body);
            legFutures.add(context.execute());
        }

        if (scatterRoute.failureMode() == FailureMode.FAIL_FAST) {
            return Future.all(legFutures).map(cf -> {
                List<LegResponse> results = new ArrayList<>();
                for (Future<LegResponse> legFuture : legFutures) {
                    results.add(legFuture.result());
                }
                return results;
            });
        } else {
            List<Future<LegResponse>> resilientFutures = new ArrayList<>();
            for (int i = 0; i < legFutures.size(); i++) {
                String originId = legDefinitions.get(i).leg().origin().identifier();
                resilientFutures.add(legFutures.get(i)
                        .recover(throwable -> Future.succeededFuture(new LegResponse(
                                originId, null, -1, MultiMap.caseInsensitiveMultiMap(), throwable))));
            }
            return Future.join(resilientFutures).map(cf -> {
                List<LegResponse> results = new ArrayList<>();
                for (Future<LegResponse> f : resilientFutures) {
                    results.add(f.result());
                }
                return results;
            });
        }
    }

    private void enrichSpanWithScatterAttributes() {
        try {
            io.opentelemetry.api.trace.Span span = io.opentelemetry.api.trace.Span.current();
            if (span.isRecording()) {
                span.setAttribute(
                        io.opentelemetry.api.common.AttributeKey.stringKey("edgy.scatter.route"),
                        scatterRoute.path());
            }
        } catch (LinkageError ignored) {
            // OpenTelemetry API not on classpath
        }
    }
}
