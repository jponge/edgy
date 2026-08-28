package org.acme.edgy.runtime.api;

import java.util.Map;
import java.util.Objects;

import org.acme.edgy.runtime.api.utils.SegmentUtils;


public final class Route extends ProxyTarget<Route> implements RoutingEntry {

    final PathInfo pathInfo;
    private final String resolvedOriginPath;
    private RoutingPredicate predicate = rc -> true;

    public Route(String path, Origin origin) {
        this(path, origin, PathMode.BASIC);
    }

    public Route(String path, Origin origin, PathMode pathMode) {
        super(origin);
        this.pathInfo = PathInfo.resolve(path, pathMode);
        this.resolvedOriginPath = computeResolvedOriginPath();
    }

    private String computeResolvedOriginPath() {
        String originPath = origin().path();

        if (hasWildcard() && !originPath.contains("{")) {
            if (!originPath.endsWith("/")) {
                originPath += "/";
            }
            return originPath + "{+" + SegmentUtils.SUFFIX + "}";
        }

        return SegmentUtils.toReservedExpansion(originPath);
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

    public String resolvedOriginPath() {
        return resolvedOriginPath;
    }

    public boolean hasWildcard() {
        return pathInfo.path().endsWith("/*");
    }

    public Map<String, String> extractPathVariables(String requestUri) {
        return SegmentUtils.extractPathVariables(pathInfo.compiledPath(), requestUri);
    }

    public Route setPredicate(RoutingPredicate predicate) {
        this.predicate = Objects.requireNonNull(predicate);
        return this;
    }
}
