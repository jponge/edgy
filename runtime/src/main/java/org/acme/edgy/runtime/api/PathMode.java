package org.acme.edgy.runtime.api;

/**
 * Controls how a route's path string is interpreted.
 */
public enum PathMode {

    /**
     * Jakarta REST segment syntax: {@code /api/{id}}, {@code /api/{name:regex}},
     * trailing {@code /*} for wildcards. This is the default.
     */
    BASIC,

    /** Raw regular expression passed directly to Vert.x routing. */
    REGEXP
}
