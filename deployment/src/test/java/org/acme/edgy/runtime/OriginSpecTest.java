package org.acme.edgy.runtime;


import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

class OriginSpecTest {

    @Test
    void checkBasicSpec() {
        OriginSpec spec = OriginSpec.of("https://my.api.private/backend");
        assertThat(spec.protocol(), is("https"));
        assertThat(spec.host(), is("my.api.private"));
        assertThat(spec.port(), is(8080));
        assertThat(spec.path(), is("/backend"));
    }

    @Test
    void checkSpecWithNonStandardChars() {
        OriginSpec spec = OriginSpec.of("https://my.api.private:1443/:backend/{version}");
        assertThat(spec.protocol(), is("https"));
        assertThat(spec.host(), is("my.api.private"));
        assertThat(spec.port(), is(1443));
        assertThat(spec.path(), is("/:backend/{version}"));
    }

    @Test
    void checkBasicSpecWithQuery() {
        OriginSpec spec = OriginSpec.of("https://my.api.private/backend?a=1&b=2");
        assertThat(spec.protocol(), is("https"));
        assertThat(spec.host(), is("my.api.private"));
        assertThat(spec.port(), is(8080));
        assertThat(spec.path(), is("/backend?a=1&b=2"));
    }

    @Test
    void checkStorkSpec() {
        OriginSpec spec = OriginSpec.of("stork://my.api.private:4500/backend?a=1&b=2");
        assertThat(spec.protocol(), is("stork"));
        assertThat(spec.host(), is("my.api.private"));
        assertThat(spec.port(), is(4500));
        assertThat(spec.path(), is("/backend?a=1&b=2"));
    }
}