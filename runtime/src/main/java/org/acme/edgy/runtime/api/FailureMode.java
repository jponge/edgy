package org.acme.edgy.runtime.api;

/**
 * Controls how a {@link ScatterRoute} reacts when one of its legs fails.
 * <p>
 * A leg counts as <em>failed</em> only when its {@link io.vertx.core.Future} completes
 * exceptionally: a transport error (connection refused, DNS failure, read timeout), a guard
 * rejection, or an interceptor error. An origin that answers with an error <em>status</em>
 * (4xx/5xx) is a <em>successful</em> leg — the status is carried in
 * {@link LegResponse#statusCode()} and passed to the {@link ResponseComposer} like any other
 * response.
 * <p>
 * Legs are always fired in parallel; the mode only decides what is done with the gathered result.
 */
public enum FailureMode {

    /**
     * Abort the whole scatter as soon as the first leg fails. The {@link ResponseComposer} is never
     * invoked and the client receives {@code 502 Bad Gateway}. Legs still in flight are left to
     * complete on their own, but their results are discarded.
     * <p>
     * This is the default. Use it when the composed response is meaningless without every leg.
     */
    FAIL_FAST,

    /**
     * Wait for every leg to settle, then invoke the {@link ResponseComposer} with whatever came
     * back. A failed leg is represented by a placeholder {@link LegResponse} with
     * {@code statusCode == -1} and a non-null {@link LegResponse#failure()}; use
     * {@link LegResponse#succeeded()} to tell the two apart. The client receives {@code 200 OK}
     * even when every leg failed, unless the composer itself fails.
     * <p>
     * Use it when a degraded response is better than no response.
     */
    PARTIAL
}
