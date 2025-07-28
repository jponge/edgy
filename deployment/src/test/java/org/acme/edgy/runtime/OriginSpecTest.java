package org.acme.edgy.runtime;


import org.acme.edgy.runtime.api.PathMode;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class OriginSpecTest {

    @Test
    void checkBasicSpec() {
        OriginSpec spec = OriginSpec.of("https://my.api.private/backend", PathMode.FIXED);
        assertThat(spec.protocol(), is("https"));
        assertThat(spec.host(), is("my.api.private"));
        assertThat(spec.port(), is(8080));
        assertThat(spec.path(), is("/backend"));
        assertThat(spec.pathMode(), is(PathMode.FIXED));
    }

    @Test
    void checkSpecWithNonStandardChars() {
        OriginSpec spec = OriginSpec.of("https://my.api.private:1443/:backend/{version}",  PathMode.PARAMS);
        assertThat(spec.protocol(), is("https"));
        assertThat(spec.host(), is("my.api.private"));
        assertThat(spec.port(), is(1443));
        assertThat(spec.path(), is("/:backend/{version}"));
        assertThat(spec.pathMode(), is(PathMode.PARAMS));
    }

    @Test
    void checkBasicSpecWithQuery() {
        OriginSpec spec = OriginSpec.of("https://my.api.private/backend?a=1&b=2", PathMode.FIXED);
        assertThat(spec.protocol(), is("https"));
        assertThat(spec.host(), is("my.api.private"));
        assertThat(spec.port(), is(8080));
        assertThat(spec.path(), is("/backend?a=1&b=2"));
        assertThat(spec.pathMode(), is(PathMode.FIXED));
    }

    @Test
    void checkStorkSpec() {
        OriginSpec spec = OriginSpec.of("stork://my.api.private:4500/backend?a=1&b=2", PathMode.FIXED);
        assertThat(spec.protocol(), is("stork"));
        assertThat(spec.host(), is("my.api.private"));
        assertThat(spec.port(), is(4500));
        assertThat(spec.path(), is("/backend?a=1&b=2"));
        assertThat(spec.pathMode(), is(PathMode.FIXED));
    }
}