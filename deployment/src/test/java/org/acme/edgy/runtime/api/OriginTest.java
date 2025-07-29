package org.acme.edgy.runtime.api;


import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class OriginTest {

    @Test
    void checkBasicSpec() {
        Origin spec = Origin.of("https://my.api.private/backend");
        assertThat(spec.protocol(), is("https"));
        assertThat(spec.host(), is("my.api.private"));
        assertThat(spec.port(), is(8080));
        assertThat(spec.path(), is("/backend"));
    }

    @Test
    void checkSpecWithNonStandardChars() {
        Origin spec = Origin.of("https://my.api.private:1443/:backend/{version}");
        assertThat(spec.protocol(), is("https"));
        assertThat(spec.host(), is("my.api.private"));
        assertThat(spec.port(), is(1443));
        assertThat(spec.path(), is("/:backend/{version}"));
    }

    @Test
    void checkBasicSpecWithQuery() {
        Origin spec = Origin.of("https://my.api.private/backend?a=1&b=2");
        assertThat(spec.protocol(), is("https"));
        assertThat(spec.host(), is("my.api.private"));
        assertThat(spec.port(), is(8080));
        assertThat(spec.path(), is("/backend?a=1&b=2"));
    }

    @Test
    void checkStorkSpec() {
        Origin spec = Origin.of("stork://my.api.private:4500/backend?a=1&b=2");
        assertThat(spec.protocol(), is("stork"));
        assertThat(spec.host(), is("my.api.private"));
        assertThat(spec.port(), is(4500));
        assertThat(spec.path(), is("/backend?a=1&b=2"));
    }
}