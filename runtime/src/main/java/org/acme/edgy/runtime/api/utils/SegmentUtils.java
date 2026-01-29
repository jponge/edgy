package org.acme.edgy.runtime.api.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface SegmentUtils {

    static String replaceSegmentsWithRegex(String path) {
        StringBuilder result = new StringBuilder();
        Matcher matcher = segmentPattern().matcher(path);

        int lastEnd = 0;
        while (matcher.find()) {
            // Escape regex chars in the literal part before the segment
            String beforeSegment = path.substring(lastEnd, matcher.start());
            result.append(escapeLiteralRegexChars(beforeSegment));

            String segmentId = matcher.group(1); // <name> or null
            String pattern = matcher.group(2);
            result.append((segmentId != null) ? pattern : wildcard());

            lastEnd = matcher.end();
        }
        // Escape the remaining part after the last segment
        if (lastEnd < path.length()) {
            result.append(escapeLiteralRegexChars(path.substring(lastEnd)));
        }
        if (!path.endsWith("/")) {
            // allowing if the request has the trailing slash
            result.append("/?");
        }

        return result.toString();
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

        // Extract segment IDs from template in order
        Matcher paramMatcher = segmentPattern().matcher(pathTemplate);
        List<String> segmentIds = new ArrayList<>();
        while (paramMatcher.find()) {
            String segmentIdWithAngleBrackets = paramMatcher.group(1); // <SEGMENT_ID> or null
            String patternOrSegmentId = paramMatcher.group(2);

            // For simple segments like {userId}, group(1) is null and group(2) is "userId"
            // For custom regex like {<userId>\d+}, group(1) is "<userId>" and group(2) is "\d+"
            String segmentId =
                    (segmentIdWithAngleBrackets != null)
                            ? segmentIdWithAngleBrackets.substring(1,
                                    segmentIdWithAngleBrackets.length() - 1)
                            : patternOrSegmentId; // in this case it is segmentId


            if (segmentIds.contains(segmentId)) {
                throw new IllegalArgumentException(
                        "Duplicate segment ID found: '%s' in path '%s' ".formatted(segmentId,
                                pathTemplate));
            }
            segmentIds.add(segmentId);
        }
        if (segmentIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // Build regex pattern with capture groups
        StringBuilder templateWithGroups = new StringBuilder();
        Matcher segmentMatcher = segmentPattern().matcher(pathTemplate);
        while (segmentMatcher.find()) {
            String nameWithAngleBrackets = segmentMatcher.group(1); // <SEGMENT_ID> or null
            String pattern = segmentMatcher.group(2);
            segmentMatcher.appendReplacement(templateWithGroups,
                    (nameWithAngleBrackets != null) ? Matcher.quoteReplacement("(" + pattern + ")")
                            : wildcardCaptureGroup());
        }
        segmentMatcher.appendTail(templateWithGroups);

        Pattern pattern = Pattern.compile(templateWithGroups.toString());
        Matcher matcher = pattern.matcher(actualPath);
        Map<String, String> segmentValues = new HashMap<>();
        if (matcher.matches()) {
            for (int i = 0; i < segmentIds.size(); i++) {
                segmentValues.put(segmentIds.get(i), matcher.group(i + 1));
            }
        }
        return segmentValues;
    }

    private static Pattern segmentPattern() {
        // Matches: {<SEGMENT_ID>PATTERN} or {SEGMENT_ID}
        return Pattern.compile("\\{(<[^>]*>)?([^}]+)\\}");
    }

    private static String wildcardCaptureGroup() {
        // Matches any characters (greedy)
        return "(" + wildcard() + ")";
    }

    private static String wildcard() {
        return ".*";
    }

    private static String escapeLiteralRegexChars(String literal) {
        // Escape regex special characters in literal path parts
        // `-` character is also not URI-encoded, but without `[` and `]` (which are encoded),
        // it does not need escaping
        return literal.replaceAll("([\\.\\*\\(\\)])", "\\\\$1");
    }
}
