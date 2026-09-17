package org.acme.edgy.runtime.api;

import java.util.List;
import java.util.function.Supplier;

import io.vertx.core.Future;

/**
 * Handle for an in-flight scatter/gather observation.
 * Created by {@link ProxyObserver#observeScatter} and completed via
 * {@link #end} or {@link #error}.
 */
public interface ScatterObservation {

    ScatterObservation NOOP = new ScatterObservation() {
        @Override
        public Future<LegResponse> wrapLeg(Leg leg, Supplier<Future<LegResponse>> execution) {
            return execution.get();
        }

        @Override
        public void end(List<LegResponse> responses) {
            // no-op
        }

        @Override
        public void error(Throwable error) {
            // no-op
        }
    };

    /**
     * Wraps the execution of a scatter leg. The observer can set up context
     * (e.g. tracing spans) before calling the execution supplier, and clean up
     * when the returned future completes.
     */
    Future<LegResponse> wrapLeg(Leg leg, Supplier<Future<LegResponse>> execution);

    /**
     * Called when the scatter succeeded: every leg settled and the composer produced a response
     * body. In {@link FailureMode#PARTIAL} the responses may include failed legs.
     *
     * @param responses the gathered leg responses handed to the composer
     */
    void end(List<LegResponse> responses);

    /**
     * Called when the scatter failed and the client will receive {@code 502 Bad Gateway}, either
     * because a leg failed in {@link FailureMode#FAIL_FAST} or because the composer failed.
     *
     * @param error the failure that aborted the scatter
     */
    void error(Throwable error);
}
