package org.acme.edgy.runtime.api.utils;

import static org.acme.edgy.runtime.api.utils.SegmentUtils.extractSegmentValues;
import static org.acme.edgy.runtime.api.utils.SegmentUtils.replaceSegmentsWithRegex;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

class SegmentUtilsTest {

    @Test
    void testReplaceSegmentsWithRegex() {
        assertEquals("/", replaceSegmentsWithRegex("/"));
        assertEquals("/.*/?", replaceSegmentsWithRegex("/{id}"));
                assertEquals("/users/?", replaceSegmentsWithRegex("/users"));
        assertEquals("/users/.*/?", replaceSegmentsWithRegex("/users/{userId}"));
        assertEquals("/users/.*/orders/.*/?",
                replaceSegmentsWithRegex("/users/{userId}/orders/{orderId}"));
        assertEquals("/files/.*-id/download/?",
                replaceSegmentsWithRegex("/files/{inode}-id/download"));
        assertEquals("/a/.*-.*/b/?", replaceSegmentsWithRegex("/a/{a}-{b}/b"));

        // with trailing slash
        assertEquals("/.*/", replaceSegmentsWithRegex("/{id}/"));
        assertEquals("/users/", replaceSegmentsWithRegex("/users/"));
        assertEquals("/users/.*/", replaceSegmentsWithRegex("/users/{userId}/"));
        assertEquals("/users/.*/orders/.*/",
                replaceSegmentsWithRegex("/users/{userId}/orders/{orderId}/"));
        assertEquals("/files/.*-id/download/",
                replaceSegmentsWithRegex("/files/{inode}-id/download/"));
        assertEquals("/a/.*-.*/b/", replaceSegmentsWithRegex("/a/{a}-{b}/b/"));

        // with custom regex patterns
        assertEquals("/users/\\d+/?", replaceSegmentsWithRegex("/users/{<userId>\\d+}"));
        assertEquals("/users/\\d+/orders/\\d+/?",
                replaceSegmentsWithRegex("/users/{<userId>\\d+}/orders/{<orderId>\\d+}"));
        assertEquals("/files/[a-f0-9]+/?", replaceSegmentsWithRegex("/files/{<hash>[a-f0-9]+}"));
        assertEquals("/\\d+/?", replaceSegmentsWithRegex("/{<>\\d+}")); // empty name
        assertEquals("/users/\\d{3,5}/?", replaceSegmentsWithRegex("/users/{<id>\\d{3,5}}"));

        // mixed simple and custom regex
        assertEquals("/users/.*/orders/\\d+/?",
                replaceSegmentsWithRegex("/users/{userId}/orders/{<orderId>\\d+}"));

        // with trailing slash and custom regex patterns
        assertEquals("/users/\\d+/", replaceSegmentsWithRegex("/users/{<userId>\\d+}/"));
        assertEquals("/users/\\d+/orders/\\d+/",
                replaceSegmentsWithRegex("/users/{<userId>\\d+}/orders/{<orderId>\\d+}/"));
        assertEquals("/files/[a-f0-9]+/", replaceSegmentsWithRegex("/files/{<hash>[a-f0-9]+}/"));

        // mixed simple and custom regex with trailing slash
        assertEquals("/users/.*/orders/\\d+/",
                replaceSegmentsWithRegex("/users/{userId}/orders/{<orderId>\\d+}/"));
    }

    @Test
    void testExtractSegmentValues() {
        assertEquals(Map.of("userId", "123", "orderId", "456"),
                extractSegmentValues("/users/{userId}/orders/{orderId}", "/users/123/orders/456"));
        assertEquals(Map.of("inode", "file123"),
                extractSegmentValues("/files/{inode}-id/download", "/files/file123-id/download"));
        assertEquals(Map.of("a", "foo", "b", "bar"),
                extractSegmentValues("/a/{a}-{b}/b", "/a/foo-bar/b"));
        assertEquals(Map.of(), extractSegmentValues("/a/baz/b", "/a/baz/b"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> {
            extractSegmentValues("/users/{id}/orders/{id}", "/users/123/orders/456");
        }).getMessage(), containsString("Duplicate segment"));

        // with trailing slash in pathTemplate
        assertEquals(Map.of("userId", "123", "orderId", "456"),
                extractSegmentValues("/users/{userId}/orders/{orderId}/", "/users/123/orders/456"));
        assertEquals(Map.of("inode", "file123"),
                extractSegmentValues("/files/{inode}-id/download/", "/files/file123-id/download"));
        assertEquals(Map.of("a", "foo", "b", "bar"),
                extractSegmentValues("/a/{a}-{b}/b/", "/a/foo-bar/b"));
        assertEquals(Map.of(), extractSegmentValues("/a/baz/b/", "/a/baz/b"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> {
            extractSegmentValues("/users/{id}/orders/{id}/", "/users/123/orders/456");
        }).getMessage(), containsString("Duplicate segment"));

        // with trailing slash in actualPath
        assertEquals(Map.of("userId", "123", "orderId", "456"),
                extractSegmentValues("/users/{userId}/orders/{orderId}", "/users/123/orders/456/"));
        assertEquals(Map.of("inode", "file123"),
                extractSegmentValues("/files/{inode}-id/download", "/files/file123-id/download/"));
        assertEquals(Map.of("a", "foo", "b", "bar"),
                extractSegmentValues("/a/{a}-{b}/b", "/a/foo-bar/b/"));
        assertEquals(Map.of(), extractSegmentValues("/a/baz/b", "/a/baz/b/"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> {
            extractSegmentValues("/users/{id}/orders/{id}", "/users/123/orders/456/");
        }).getMessage(), containsString("Duplicate segment"));

        // with custom regex patterns
        assertEquals(Map.of("userId", "123"),
                extractSegmentValues("/users/{<userId>\\d+}", "/users/123"));
        assertEquals(Map.of("userId", "123", "orderId", "456"), extractSegmentValues(
                "/users/{<userId>\\d+}/orders/{<orderId>\\d+}", "/users/123/orders/456"));
        assertEquals(Map.of("hash", "abc123"),
                extractSegmentValues("/files/{<hash>[a-f0-9]+}", "/files/abc123"));

        // mixed simple and custom regex
        assertEquals(Map.of("userId", "john", "orderId", "456"), extractSegmentValues(
                "/users/{userId}/orders/{<orderId>\\d+}", "/users/john/orders/456"));
    }
}
