package org.acme.edgy.runtime.api;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OriginTest {

    @Test
    void checkBasicSpec() {
        Origin spec = Origin.of("origin", "https://my.api.private/backend");
        assertThat(spec.identifier(), is("origin"));
        assertThat(spec.protocol(), is(Protocol.https));
        assertThat(spec.host(), is("my.api.private"));
        assertThat(spec.port(), is(8080));
        assertThat(spec.path(), is("/backend"));
    }

    @Test
    void checkSpecWithNonStandardChars() {
        Origin spec = Origin.of("origin", "https://my.api.private:1443/:backend/{version}");
        assertThat(spec.identifier(), is("origin"));
        assertThat(spec.protocol(), is(Protocol.https));
        assertThat(spec.host(), is("my.api.private"));
        assertThat(spec.port(), is(1443));
        assertThat(spec.path(), is("/:backend/{version}"));
    }

    @Test
    void checkBasicSpecWithQuery() {
        Origin spec = Origin.of("origin", "https://my.api.private/backend?a=1&b=2");
        assertThat(spec.identifier(), is("origin"));
        assertThat(spec.protocol(), is(Protocol.https));
        assertThat(spec.host(), is("my.api.private"));
        assertThat(spec.port(), is(8080));
        assertThat(spec.path(), is("/backend?a=1&b=2"));
    }

    @Test
    void checkStorkWithPort() {
        assertThat(assertThrows(IllegalArgumentException.class,
                () -> Origin.of("stork-origin", "stork://my.api.private:4500/backend?a=1&b=2")).getMessage(),
                containsString("port"));
    }

    @Test
    void checkStorkSpec() {
        Origin spec = Origin.of("stork-origin", "stork://my.api.private/backend?a=1&b=2");
        assertThat(spec.identifier(), is("stork-origin"));
        assertThat(spec.protocol(), is(Protocol.stork));
        assertThat(spec.host(), is("my.api.private"));
        // default port, but is never used (so for a sake of not having a null value for
        // a port)
        assertThat(spec.port(), is(8080));
        assertThat(spec.path(), is("/backend?a=1&b=2"));
    }

    @Test
    void checkPortUpperBound() {
        assertThat(
                assertThrows(IllegalArgumentException.class,
                        () -> Origin.of("upper-bound-origin", "http://my.api.private:65536/backend")).getMessage(),
                containsString("range"));
    }

    @Test
    void checkPortLowerBound() {
        assertThat(
                assertThrows(IllegalArgumentException.class,
                        () -> Origin.of("lower-bound-origin", "http://my.api.private:-1/backend")).getMessage(),
                containsString("range"));
    }

    @Test
    void checkTrailingColon() {
        assertThat(
                assertThrows(IllegalArgumentException.class,
                        () -> Origin.of("trailing-colon-origin", "https://my.api.private:/backend")).getMessage(),
                containsString("Port separator"));
    }

    @Test
    void checkHostCanonicalization() {
        Origin spec1 = Origin.of("canonical-1", "https://MY.API.PRIVATE/backend");
        Origin spec2 = Origin.of("canonical-1", "https://my.api.private/backend");
        assertThat(spec1.host(), is("my.api.private"));
        assertThat(spec2.host(), is("my.api.private"));
        assertThat(spec1.uri(), is(spec2.uri()));
    }

    @Test
    void checkOriginWithoutPort() {
        Origin spec = Origin.of("no-port-origin", "https://example.com/path");
        assertThat(spec.identifier(), is("no-port-origin"));
        assertThat(spec.protocol(), is(Protocol.https));
        assertThat(spec.host(), is("example.com"));
        assertThat(spec.port(), is(8080));
        assertThat(spec.path(), is("/path"));
    }

    @Test
    void checkOriginWithoutPath() {
        Origin spec = Origin.of("no-path-origin", "https://example.com:9000");
        assertThat(spec.identifier(), is("no-path-origin"));
        assertThat(spec.protocol(), is(Protocol.https));
        assertThat(spec.host(), is("example.com"));
        assertThat(spec.port(), is(9000));
        assertThat(spec.path(), is("/"));
    }

    @Test
    void checkMinimalOrigin() {
        Origin spec = Origin.of("minimal-origin", "example.com");
        assertThat(spec.identifier(), is("minimal-origin"));
        assertThat(spec.protocol(), is(Protocol.http));
        assertThat(spec.host(), is("example.com"));
        assertThat(spec.port(), is(8080));
        assertThat(spec.path(), is("/"));
    }

    @Test
    void checkNullAndBlankOrigins() {
        Origin nullOrigin = Origin.of("null-id", null);
        assertThat(nullOrigin.identifier(), is("null-id"));
        assertThat(nullOrigin.protocol(), is(Protocol.http));
        assertThat(nullOrigin.host(), is("localhost"));
        assertThat(nullOrigin.port(), is(8080));
        assertThat(nullOrigin.path(), is("/"));

        Origin blankOrigin = Origin.of("blank-id", "   ");
        assertThat(blankOrigin.identifier(), is("blank-id"));
        assertThat(blankOrigin.protocol(), is(Protocol.http));
        assertThat(blankOrigin.host(), is("localhost"));
        assertThat(blankOrigin.port(), is(8080));
        assertThat(blankOrigin.path(), is("/"));

    }
}