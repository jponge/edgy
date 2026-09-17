package org.acme.edgy.runtime.api;

import org.acme.edgy.runtime.api.utils.SegmentUtils;
import org.acme.edgy.runtime.api.utils.SegmentUtils.CompiledPath;

/**
 * Pre-compiled path metadata shared by {@link Route} and {@link ScatterRoute}.
 */
record PathInfo(String path, PathMode pathMode, CompiledPath compiledPath,
        boolean regexRoute, String resolvedPath) {

    static PathInfo resolve(String path, PathMode pathMode) {
        if (pathMode == PathMode.BASIC && SegmentUtils.needsRegexRouting(path)) {
            CompiledPath compiled = SegmentUtils.transform(path);
            return new PathInfo(path, pathMode, compiled, true,
                    compiled.compiledPattern().pattern());
        } else if (pathMode == PathMode.REGEXP) {
            return new PathInfo(path, pathMode, SegmentUtils.fromRegexp(path),
                    true, path);
        }
        return new PathInfo(path, pathMode, null, false, path);
    }
}
