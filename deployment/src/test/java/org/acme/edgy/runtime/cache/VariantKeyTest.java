package org.acme.edgy.runtime.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.vertx.core.MultiMap;

class VariantKeyTest {

    private static MultiMap headers(String... pairs) {
        MultiMap map = MultiMap.caseInsensitiveMultiMap();
        for (int i = 0; i < pairs.length; i += 2) {
            map.set(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    @Test
    void absentVaryYieldsNoFields() {
        assertThat(VariantKey.fields(null)).isEmpty();
        assertThat(VariantKey.fields("  ")).isEmpty();
    }

    @Test
    void fieldsAreLowercasedSortedAndDeduplicated() {
        assertThat(VariantKey.fields("Accept-Language, accept-encoding, ACCEPT-LANGUAGE"))
                .containsExactly("accept-encoding", "accept-language");
    }

    @Test
    void wildcardIsDetectedRegardlessOfSpacing() {
        assertThat(VariantKey.isWildcard("*")).isTrue();
        assertThat(VariantKey.isWildcard("  *  ")).isTrue();
        assertThat(VariantKey.isWildcard("Accept-Encoding")).isFalse();
        assertThat(VariantKey.isWildcard(null)).isFalse();
    }

    @Test
    void noFieldsKeysOnTheUriAlone() {
        assertThat(VariantKey.compose("/a", List.of(), headers("Accept-Encoding", "gzip")))
                .isEqualTo("/a");
    }

    @Test
    void differentHeaderValuesProduceDifferentKeys() {
        List<String> fields = List.of("accept-encoding");

        String gzip = VariantKey.compose("/a", fields, headers("Accept-Encoding", "gzip"));
        String identity = VariantKey.compose("/a", fields, headers("Accept-Encoding", "identity"));

        assertThat(gzip).isNotEqualTo(identity);
        assertThat(gzip).startsWith("/a");
    }

    @Test
    void sameHeaderValuesProduceTheSameKeyWhateverTheInsertionOrder() {
        List<String> fields = List.of("accept-encoding", "accept-language");

        String first = VariantKey.compose("/a", fields,
                headers("Accept-Encoding", "gzip", "Accept-Language", "en"));
        String second = VariantKey.compose("/a", fields,
                headers("Accept-Language", "en", "Accept-Encoding", "gzip"));

        assertThat(first).isEqualTo(second);
    }

    @Test
    void missingRequestHeaderIsTreatedAsEmptyAndStillDistinct() {
        List<String> fields = List.of("accept-encoding");

        String absent = VariantKey.compose("/a", fields, headers());
        String present = VariantKey.compose("/a", fields, headers("Accept-Encoding", "gzip"));

        assertThat(absent).isNotEqualTo(present);
        assertThat(absent).isEqualTo(VariantKey.compose("/a", fields, headers()));
    }

    @Test
    void differentUrisProduceDifferentKeysForTheSameHeaders() {
        List<String> fields = List.of("accept-encoding");
        MultiMap requestHeaders = headers("Accept-Encoding", "gzip");

        assertThat(VariantKey.compose("/a", fields, requestHeaders))
                .isNotEqualTo(VariantKey.compose("/b", fields, requestHeaders));
    }
}
