package org.acme.edgy.runtime.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.acme.edgy.runtime.api.utils.SegmentUtils;
import org.acme.edgy.runtime.api.utils.SegmentUtils.CompiledPath;

public class ScatterRoute {

    private final String path;
    private final PathMode pathMode;

    private final CompiledPath transformedPath;
    private final boolean regexRoute;
    private final String resolvedPath;

    private RoutingPredicate predicate = rc -> true;
    private final List<Leg> legs = new ArrayList<>();
    private ResponseComposer composer;
    private FailureMode failureMode = FailureMode.FAIL_FAST;

    public ScatterRoute(String path) {
        this(path, PathMode.BASIC);
    }

    public ScatterRoute(String path, PathMode pathMode) {
        this.path = path;
        this.pathMode = pathMode;

        if (pathMode == PathMode.BASIC && SegmentUtils.needsRegexRouting(path)) {
            this.transformedPath = SegmentUtils.transform(path);
            this.regexRoute = true;
            this.resolvedPath = transformedPath.compiledPattern().pattern();
        } else if (pathMode == PathMode.REGEXP) {
            this.transformedPath = SegmentUtils.fromRegexp(path);
            this.regexRoute = true;
            this.resolvedPath = path;
        } else {
            this.transformedPath = null;
            this.regexRoute = false;
            this.resolvedPath = path;
        }
    }

    public String path() {
        return path;
    }

    public PathMode pathMode() {
        return pathMode;
    }

    public String resolvedPath() {
        return resolvedPath;
    }

    public boolean needsRegexRouting() {
        return regexRoute;
    }

    public boolean hasWildcard() {
        return path.endsWith("/*");
    }

    public Map<String, String> extractPathVariables(String requestUri) {
        return SegmentUtils.extractPathVariables(transformedPath, requestUri);
    }

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

    public ScatterRoute addLeg(Leg leg) {
        legs.add(Objects.requireNonNull(leg));
        return this;
    }

    public ResponseComposer composer() {
        return composer;
    }

    public ScatterRoute setComposer(ResponseComposer composer) {
        this.composer = Objects.requireNonNull(composer);
        return this;
    }

    public FailureMode failureMode() {
        return failureMode;
    }

    public ScatterRoute setFailureMode(FailureMode failureMode) {
        this.failureMode = Objects.requireNonNull(failureMode);
        return this;
    }

    public void validate() {
        if (legs.size() < 2) {
            throw new IllegalStateException(
                    "ScatterRoute '" + path + "' must have at least 2 legs, got " + legs.size());
        }
        if (composer == null) {
            throw new IllegalStateException(
                    "ScatterRoute '" + path + "' must have a composer");
        }
    }
}
