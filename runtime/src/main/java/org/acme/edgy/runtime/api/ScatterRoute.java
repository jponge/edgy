package org.acme.edgy.runtime.api;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.acme.edgy.runtime.api.resiliency.GuardHandler;
import org.acme.edgy.runtime.interceptors.resiliency.GuardInterceptor;

import io.vertx.core.http.HttpMethod;
import io.vertx.httpproxy.ProxyInterceptor;

/**
 * A scatter/gather routing entry that fans out a request to multiple
 * {@link Leg legs} in parallel and composes their responses.
 * <p>
 * Scatter-level defaults (method, keepBody, transformers, guard) apply
 * to every leg unless the leg explicitly overrides them:
 * <ul>
 *   <li><b>Method / keepBody / guard:</b> the leg's own value wins
 *       when explicitly set; otherwise the scatter-level value applies.</li>
 *   <li><b>Transformers:</b> scatter-level transformers execute
 *       <em>before</em> each leg's own transformers — they accumulate,
 *       not replace.</li>
 * </ul>
 */
public final class ScatterRoute implements RoutingEntry {

    private final PathInfo pathInfo;
    private RoutingPredicate predicate = rc -> true;

    private final List<Leg> legs;
    private final ResponseComposer composer;
    private FailureMode failureMode = FailureMode.FAIL_FAST;

    private HttpMethod methodOverride;
    private Boolean keepBody;
    private final List<RequestTransformer> requestTransformers = new ArrayList<>();
    private final Deque<ResponseTransformer> responseTransformers = new ArrayDeque<>();
    private ProxyInterceptor guardInterceptor;

    public ScatterRoute(String path, ResponseComposer composer, Leg leg1, Leg leg2, Leg... otherLegs) {
        this(path, PathMode.BASIC, composer, leg1, leg2, otherLegs);
    }

    public ScatterRoute(String path, PathMode pathMode, ResponseComposer composer, Leg leg1, Leg leg2,
            Leg... otherLegs) {
        this.pathInfo = PathInfo.resolve(path, pathMode);
        this.composer = Objects.requireNonNull(composer, "composer");

        List<Leg> allLegs = new ArrayList<>();
        allLegs.add(Objects.requireNonNull(leg1, "leg1"));
        allLegs.add(Objects.requireNonNull(leg2, "leg2"));
        for (Leg leg : otherLegs) {
            allLegs.add(Objects.requireNonNull(leg, "leg"));
        }
        this.legs = allLegs;
    }

    @Override
    public String path() {
        return pathInfo.path();
    }

    @Override
    public PathMode pathMode() {
        return pathInfo.pathMode();
    }

    @Override
    public String resolvedPath() {
        return pathInfo.resolvedPath();
    }

    @Override
    public boolean needsRegexRouting() {
        return pathInfo.regexRoute();
    }

    @Override
    public RoutingPredicate predicate() {
        return predicate;
    }

    public ScatterRoute setPredicate(RoutingPredicate predicate) {
        this.predicate = Objects.requireNonNull(predicate);
        return this;
    }

    public List<Leg> legs() {
        return legs;
    }

    public ResponseComposer composer() {
        return composer;
    }

    public FailureMode failureMode() {
        return failureMode;
    }

    public ScatterRoute setFailureMode(FailureMode failureMode) {
        this.failureMode = Objects.requireNonNull(failureMode);
        return this;
    }

    /**
     * Default HTTP method for all legs. Individual legs override
     * this when they call {@link Leg#setMethod}.
     */
    public ScatterRoute setMethod(HttpMethod method) {
        this.methodOverride = Objects.requireNonNull(method);
        return this;
    }

    /**
     * Default keep-body flag for all legs. Individual legs override
     * this when they call {@link Leg#setKeepBody}.
     */
    public ScatterRoute setKeepBody(boolean keep) {
        this.keepBody = keep;
        return this;
    }

    /**
     * Adds a request transformer applied to every leg <em>before</em>
     * the leg's own request transformers.
     */
    public ScatterRoute addRequestTransformer(RequestTransformer transformer) {
        requestTransformers.add(transformer);
        return this;
    }

    /**
     * Adds a response transformer applied to every leg <em>before</em>
     * the leg's own response transformers.
     */
    public ScatterRoute addResponseTransformer(ResponseTransformer transformer) {
        responseTransformers.addFirst(transformer);
        return this;
    }

    /**
     * Default guard handler for all legs. Individual legs override
     * this when they call {@link Leg#setGuardHandler}.
     */
    public ScatterRoute setGuardHandler(GuardHandler handler) {
        this.guardInterceptor = new GuardInterceptor(handler);
        return this;
    }

    public HttpMethod methodOverride() {
        return methodOverride;
    }

    public boolean keepBody() {
        return keepBody != null && keepBody;
    }

    public Boolean keepBodyOverride() {
        return keepBody;
    }

    public List<RequestTransformer> requestTransformers() {
        return requestTransformers;
    }

    public Deque<ResponseTransformer> responseTransformers() {
        return responseTransformers;
    }

    public Optional<ProxyInterceptor> guardInterceptor() {
        return Optional.ofNullable(guardInterceptor);
    }
}
