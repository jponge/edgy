package org.acme.edgy.runtime.api.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import io.quarkus.runtime.util.StringUtil;

public interface SegmentUtils {

    static String replaceSegmentsWithRegex(String path) {
        return path.replaceAll(regexpSegment(), regexpWildcard());
    }

    static Map<String, String> extractSegmentValues(String pathTemplate, String actualPath) {
        if (Objects.requireNonNull(pathTemplate).isBlank()) {
            throw new IllegalArgumentException("pathTemplate must not be blank");
        }
        if (Objects.requireNonNull(actualPath).isBlank()) {
            throw new IllegalArgumentException("actualPath must not be blank");
        }

        boolean templateEndsWithSlash = pathTemplate.endsWith("/");
        boolean actualEndsWithSlash = actualPath.endsWith("/");
        // Normalize trailing slash presence
        if (templateEndsWithSlash && !actualEndsWithSlash) {
            actualPath += "/";
        } else if (actualEndsWithSlash && !templateEndsWithSlash) {
            pathTemplate += "/";
        }

        // Extract parameter names from template in order
        Matcher paramMatcher = segmentPattern().matcher(pathTemplate);
        List<String> paramNames = new ArrayList<>();
        while (paramMatcher.find()) {
            String segmentName = paramMatcher.group(1);
            if (paramNames.contains(segmentName)) {
                throw new IllegalArgumentException(
                        "Duplicate segment name found: '%s' in path '%s' ".formatted(segmentName,
                                pathTemplate));
            }
            paramNames.add(segmentName);
        }
        if (paramNames.isEmpty()) {
            return Collections.emptyMap();
        }


        String templatePathWithCaptureGroups =
                pathTemplate.replaceAll(regexpSegment(), regexpGroup());
        Pattern pattern = Pattern.compile(templatePathWithCaptureGroups);
        Matcher matcher = pattern.matcher(actualPath);
        Map<String, String> segmentValues = new HashMap<>();
        if (matcher.matches()) {
            for (int i = 0; i < paramNames.size(); i++) {
                segmentValues.put(paramNames.get(i), matcher.group(i + 1));
            }
        }
        return segmentValues;
    }

    static String escapeNonUriEncodedRegexCharacters(String path) {
        // `-` character is also not encoded, but without characters `[` and `]`, which are encoded,
        // it does not need escaping
        return path.replaceAll("([\\.\\*\\(\\)])", "\\\\$1");
    }

    private static Pattern segmentPattern() {
        return Pattern.compile(regexpSegment());
    }

    private static String regexpSegment() {
        return "\\{(.*?)\\}";
    }

    private static String regexpWildcard() {
        return ".*";
    }

    private static String regexpGroup() {
        return "([^/]+?)";
    }
}
