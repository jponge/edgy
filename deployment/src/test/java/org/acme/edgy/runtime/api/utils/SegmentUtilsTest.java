package org.acme.edgy.runtime.api.utils;

import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.acme.edgy.runtime.api.utils.SegmentUtils.escapeNonUriEncodedRegexCharacters;
import static org.acme.edgy.runtime.api.utils.SegmentUtils.extractSegmentValues;
import static org.acme.edgy.runtime.api.utils.SegmentUtils.replaceSegmentsWithRegex;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SegmentUtilsTest {

    @Test
    void testReplaceSegmentsWithRegex() {
        assertEquals("/", replaceSegmentsWithRegex("/"));
        assertEquals("/.*", replaceSegmentsWithRegex("/{id}"));
        assertEquals("/users", replaceSegmentsWithRegex("/users"));
        assertEquals("/users/.*", replaceSegmentsWithRegex("/users/{userId}"));
        assertEquals("/users/.*/orders/.*",
                replaceSegmentsWithRegex("/users/{userId}/orders/{orderId}"));
        assertEquals("/files/.*-id/download",
                replaceSegmentsWithRegex("/files/{inode}-id/download"));
        assertEquals("/a/.*-.*/b", replaceSegmentsWithRegex("/a/{a}-{b}/b"));

        // with trialing slash
        assertEquals("/.*/", replaceSegmentsWithRegex("/{id}/"));
        assertEquals("/users/", replaceSegmentsWithRegex("/users/"));
        assertEquals("/users/.*/", replaceSegmentsWithRegex("/users/{userId}/"));
        assertEquals("/users/.*/orders/.*/",
                replaceSegmentsWithRegex("/users/{userId}/orders/{orderId}/"));
        assertEquals("/files/.*-id/download/",
                replaceSegmentsWithRegex("/files/{inode}-id/download/"));
        assertEquals("/a/.*-.*/b/", replaceSegmentsWithRegex("/a/{a}-{b}/b/"));
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

        // with trialing slash in pathTemplate
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

        // with trialing slash in actualPath
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
    }

    @Test
    void testEscapeNonUriEncodedRegexCharacters() {
        assertEquals("/\\.\\*", escapeNonUriEncodedRegexCharacters("/.*"));
        assertEquals("/file/\\(name\\)-id", escapeNonUriEncodedRegexCharacters("/file/(name)-id"));

        // with trialing slash
        assertEquals("/\\.\\*/", escapeNonUriEncodedRegexCharacters("/.*/"));
        assertEquals("/file/\\(name\\)-id/",
                escapeNonUriEncodedRegexCharacters("/file/(name)-id/"));
    }

}
