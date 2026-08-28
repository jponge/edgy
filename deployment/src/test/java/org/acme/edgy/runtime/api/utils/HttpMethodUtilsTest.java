package org.acme.edgy.runtime.api.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.vertx.core.http.HttpMethod;

class HttpMethodUtilsTest {

    @Test
    void bodylessMethodsHaveNoRequestBodySemantics() {
        assertThat(HttpMethodUtils.hasRequestBodySemantics(HttpMethod.GET)).isFalse();
        assertThat(HttpMethodUtils.hasRequestBodySemantics(HttpMethod.HEAD)).isFalse();
        assertThat(HttpMethodUtils.hasRequestBodySemantics(HttpMethod.DELETE)).isFalse();
        assertThat(HttpMethodUtils.hasRequestBodySemantics(HttpMethod.OPTIONS)).isFalse();
        assertThat(HttpMethodUtils.hasRequestBodySemantics(HttpMethod.TRACE)).isFalse();
    }

    @Test
    void methodsWithBodySemantics() {
        assertThat(HttpMethodUtils.hasRequestBodySemantics(HttpMethod.POST)).isTrue();
        assertThat(HttpMethodUtils.hasRequestBodySemantics(HttpMethod.PUT)).isTrue();
        assertThat(HttpMethodUtils.hasRequestBodySemantics(HttpMethod.PATCH)).isTrue();
    }
}
