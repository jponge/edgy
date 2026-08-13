package org.acme.edgy.runtime.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ScatterRouteValidationTest {

    @Test
    void scatter_route_requires_at_least_two_legs() {
        var scatter = new ScatterRoute("/test")
                .addLeg(new Leg(Origin.of("s1", "http://host1/a")))
                .setComposer(responses -> null);
        assertThatThrownBy(scatter::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 2 legs");
    }

    @Test
    void scatter_route_requires_composer() {
        var scatter = new ScatterRoute("/test")
                .addLeg(new Leg(Origin.of("s1", "http://host1/a")))
                .addLeg(new Leg(Origin.of("s2", "http://host2/b")));
        assertThatThrownBy(scatter::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("composer");
    }

    @Test
    void valid_scatter_route_passes_validation() {
        var scatter = new ScatterRoute("/test")
                .addLeg(new Leg(Origin.of("s1", "http://host1/a")))
                .addLeg(new Leg(Origin.of("s2", "http://host2/b")))
                .setComposer(responses -> null);
        scatter.validate(); // should not throw
        assertThat(scatter.legs()).hasSize(2);
        assertThat(scatter.path()).isEqualTo("/test");
        assertThat(scatter.failureMode()).isEqualTo(FailureMode.FAIL_FAST);
    }

    @Test
    void routing_configuration_exposes_scatter_routes() {
        var config = RoutingConfiguration.builder()
                .addScatterRoute(new ScatterRoute("/scatter")
                        .addLeg(new Leg(Origin.of("s1", "http://host1/a")))
                        .addLeg(new Leg(Origin.of("s2", "http://host2/b")))
                        .setComposer(responses -> null))
                .build();
        assertThat(config.scatterRoutes()).hasSize(1);
        assertThat(config.scatterRoutes().get(0).path()).isEqualTo("/scatter");
    }
}
