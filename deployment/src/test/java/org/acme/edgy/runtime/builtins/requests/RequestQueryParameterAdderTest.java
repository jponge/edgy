package org.acme.edgy.runtime.builtins.requests;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;

import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.PathMode;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.acme.edgy.runtime.builtins.assertions.QueryParamAssertions;
import org.acme.edgy.runtime.builtins.assertions.QueryParamAssertions.QueryParamValueBeforeAndAfterDeserialization;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import static org.acme.edgy.runtime.api.utils.QueryParamUtils.EMPTY_QUERY_VALUE;
import static org.acme.edgy.runtime.api.utils.QueryParamUtils.QUERY_VALUE_SEPARATOR_SYMBOL;
import static org.acme.edgy.runtime.api.utils.QueryParamUtils.urlEncode;
import static org.acme.edgy.runtime.builtins.assertions.QueryParamAssertions.assertQueryParams;
import static org.acme.edgy.runtime.builtins.assertions.QueryParamAssertions.QueryParamValueBeforeAndAfterDeserialization;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.OK;

class RequestQueryParameterAdderTest {

    // special characters to test URL encoding
    private static final String QUERY_PARAM_KEY_1 = "?a)+%20 =&;\\##?&ad%0ff";
    private static final String QUERY_PARAM_KEY_2 = "%&?b=&;##+?// ad%&0ff";
    private static final String QUERY_PARAM_KEY_3 = "?c=&; ##+ ?ad%0&ff";

    private static final String QUERY_PARAM_VALUE_1 = "=ep?e+ %0o3#$??";
    private static final char QUERY_PARAM_VALUE_2 = 97;
    private static final char QUERY_PARAM_VALUE_3 = 98;
    private static final String QUERY_PARAM_VALUE_4 = "so??++ &?me value&^%#? for%20SomeClass";
    private static final char QUERY_PARAM_VALUE_5 = 99;

    static record SomeClass(String value) {
    }

    @ApplicationScoped
    static class RoutingProvider {
        @Produces
        RoutingConfiguration routingConfiguration() {
            return new RoutingConfiguration()
                            .addRoute(new Route("/values", Origin.of("origin-1", "http://localhost:8081/test/values"),
                                            PathMode.FIXED)
                                    .addRequestTransformer(new RequestQueryParameterAdder(
                                            QUERY_PARAM_KEY_1, QUERY_PARAM_VALUE_1))
                                    .addRequestTransformer(
                                            new RequestQueryParameterAdder(QUERY_PARAM_KEY_2,
                                                    QUERY_PARAM_VALUE_2, QUERY_PARAM_VALUE_3))
                                    .addRequestTransformer(new RequestQueryParameterAdder(
                                            QUERY_PARAM_KEY_3, QUERY_PARAM_VALUE_4))
                                    .addRequestTransformer(new RequestQueryParameterAdder(
                                            QUERY_PARAM_KEY_2, QUERY_PARAM_VALUE_5)))
                    .addRoute(new Route("/no-values",
                                            Origin.of("origin-2", "http://localhost:8081/test/no-values"),
                                            PathMode.FIXED)
                                    .addRequestTransformer(
                                            new RequestQueryParameterAdder(QUERY_PARAM_KEY_1))
                                    .addRequestTransformer(new RequestQueryParameterAdder(
                                            QUERY_PARAM_KEY_2, QUERY_PARAM_VALUE_2))
                                    .addRequestTransformer(
                                            new RequestQueryParameterAdder(QUERY_PARAM_KEY_3)))
                    .addRoute(new Route("/query-params-in-origin-uri", Origin.of(
                                            "origin-3",
                                            "http://localhost:8081/test/query-params-in-origin-uri?existingParam=existingValue&"
                                    + encodeQueryParamSinglePair(QUERY_PARAM_KEY_1,
                                            QUERY_PARAM_VALUE_1)),
                            PathMode.FIXED)
                                    .addRequestTransformer(new RequestQueryParameterAdder(
                                            QUERY_PARAM_KEY_2, QUERY_PARAM_VALUE_2))
                                    .addRequestTransformer(new RequestQueryParameterAdder(
                                            QUERY_PARAM_KEY_3, QUERY_PARAM_VALUE_4)))
                    .addRoute(new Route("/propagated-query-params",
                                            Origin.of("origin-4", "http://localhost:8081/test/propagated-query-params"),
                                            PathMode.FIXED)
                                    .addRequestTransformer(new RequestQueryParameterAdder(
                                            QUERY_PARAM_KEY_1, QUERY_PARAM_VALUE_4))
                                    .addRequestTransformer(new RequestQueryParameterAdder(
                                            QUERY_PARAM_KEY_2, QUERY_PARAM_VALUE_3))
                                    .addRequestTransformer(
                                            new RequestQueryParameterAdder(QUERY_PARAM_KEY_3)))
                    .addRoute(new Route("/propagated-query-params-and-query-params-in-origin-uri",
                            Origin.of(
                                                            "origin-5",
                                                            "http://localhost:8081/test/propagated-query-params-and-query-params-in-origin-uri?existingParam=existingValue&"
                                            + encodeQueryParamSinglePair(QUERY_PARAM_KEY_3,
                                                    QUERY_PARAM_VALUE_1)),
                            PathMode.FIXED)
                                    .addRequestTransformer(new RequestQueryParameterAdder(
                                            QUERY_PARAM_KEY_1, QUERY_PARAM_VALUE_1))
                                    .addRequestTransformer(
                                            new RequestQueryParameterAdder(QUERY_PARAM_KEY_2,
                                                    QUERY_PARAM_VALUE_3, QUERY_PARAM_VALUE_2))
                                    .addRequestTransformer(new RequestQueryParameterAdder(
                                            QUERY_PARAM_KEY_3, QUERY_PARAM_VALUE_4)));
        }
    }

    @Path("/test")
    static class TestApi {
        @GET
        @Path("/values")
        public RestResponse<Void> endpointValues(@Context UriInfo uriInfo,
                @QueryParam(QUERY_PARAM_KEY_1) String firstQueryParam,
                @QueryParam(QUERY_PARAM_KEY_2) List<Character> secondQueryParam,
                @QueryParam(QUERY_PARAM_KEY_3) SomeClass thirdQueryParam) {
            // QUERY_PARAM_KEY_1=QUERY_PARAM_VALUE_1
            // QUERY_PARAM_KEY_2=QUERY_PARAM_VALUE_2
            // QUERY_PARAM_KEY_2=QUERY_PARAM_VALUE_3
            // QUERY_PARAM_KEY_3=QUERY_PARAM_VALUE_4
            // QUERY_PARAM_KEY_2=QUERY_PARAM_VALUE_5
            Map<String, QueryParamValueBeforeAndAfterDeserialization> expectedQueryParams = Map.of(
                    QUERY_PARAM_KEY_1,
                    new QueryParamValueBeforeAndAfterDeserialization(QUERY_PARAM_KEY_1,
                            List.of(QUERY_PARAM_VALUE_1), QUERY_PARAM_VALUE_1, firstQueryParam),
                    QUERY_PARAM_KEY_2,
                    new QueryParamValueBeforeAndAfterDeserialization(QUERY_PARAM_KEY_2,
                            List.of(String.valueOf(QUERY_PARAM_VALUE_2),
                                    String.valueOf(QUERY_PARAM_VALUE_3),
                                    String.valueOf(QUERY_PARAM_VALUE_5)),
                            List.of(QUERY_PARAM_VALUE_2, QUERY_PARAM_VALUE_3, QUERY_PARAM_VALUE_5),
                            secondQueryParam),
                    QUERY_PARAM_KEY_3,
                    new QueryParamValueBeforeAndAfterDeserialization(QUERY_PARAM_KEY_3,
                            List.of(QUERY_PARAM_VALUE_4), new SomeClass(QUERY_PARAM_VALUE_4),
                            thirdQueryParam));
            assertQueryParams(uriInfo, expectedQueryParams);
            return RestResponse.ok();
        }

        @GET
        @Path("/no-values")
        public RestResponse<Void> endpointNoValues(@Context UriInfo uriInfo,
                @QueryParam(QUERY_PARAM_KEY_2) char secondQueryParam) {
            // QUERY_PARAM_KEY_1=
            // QUERY_PARAM_KEY_2=QUERY_PARAM_VALUE_2
            // QUERY_PARAM_KEY_3=
            Map<String, QueryParamValueBeforeAndAfterDeserialization> expectedQueryParams =
                    Map.of(QUERY_PARAM_KEY_1,
                            new QueryParamValueBeforeAndAfterDeserialization(
                                    QUERY_PARAM_KEY_1, List.of(EMPTY_QUERY_VALUE), null, null),
                            QUERY_PARAM_KEY_2,
                            new QueryParamValueBeforeAndAfterDeserialization(QUERY_PARAM_KEY_2,
                                    List.of(String.valueOf(QUERY_PARAM_VALUE_2)),
                                    QUERY_PARAM_VALUE_2, secondQueryParam),
                            QUERY_PARAM_KEY_3, new QueryParamValueBeforeAndAfterDeserialization(
                                    QUERY_PARAM_KEY_3, List.of(EMPTY_QUERY_VALUE), null, null));
            assertQueryParams(uriInfo, expectedQueryParams);
            return RestResponse.ok();
        }

        @GET
        @Path("/query-params-in-origin-uri")
        public RestResponse<Void> endpointQueryParamsInOriginUri(@Context UriInfo uriInfo,
                @QueryParam(QUERY_PARAM_KEY_1) String firstQueryParam,
                @QueryParam(QUERY_PARAM_KEY_2) char secondQueryParam,
                @QueryParam(QUERY_PARAM_KEY_3) String thirdQueryParam,
                @QueryParam("existingParam") String existingParam) {
            Map<String, QueryParamValueBeforeAndAfterDeserialization> expectedQueryParams = Map.of(
                    "existingParam",
                    new QueryParamValueBeforeAndAfterDeserialization("existingParam",
                            List.of("existingValue"), "existingValue", existingParam),
                    QUERY_PARAM_KEY_1,
                    new QueryParamValueBeforeAndAfterDeserialization(QUERY_PARAM_KEY_1,
                            List.of(QUERY_PARAM_VALUE_1), QUERY_PARAM_VALUE_1, firstQueryParam),
                    QUERY_PARAM_KEY_2,
                    new QueryParamValueBeforeAndAfterDeserialization(QUERY_PARAM_KEY_2,
                            List.of(String.valueOf(QUERY_PARAM_VALUE_2)), QUERY_PARAM_VALUE_2,
                            secondQueryParam),
                    QUERY_PARAM_KEY_3,
                    new QueryParamValueBeforeAndAfterDeserialization(QUERY_PARAM_KEY_3,
                            List.of(QUERY_PARAM_VALUE_4), QUERY_PARAM_VALUE_4, thirdQueryParam));
            assertQueryParams(uriInfo, expectedQueryParams);
            return RestResponse.ok();
        }

        @GET
        @Path("/propagated-query-params")
        public RestResponse<Void> endpointPropagatedQueryParams(@Context UriInfo uriInfo,
                @QueryParam(QUERY_PARAM_KEY_1) List<String> firstQueryParam,
                @QueryParam(QUERY_PARAM_KEY_2) List<String> secondQueryParam,
                @QueryParam(QUERY_PARAM_KEY_3) String thirdQueryParam) {
            // QUERY_PARAM_KEY_1=QUERY_PARAM_VALUE_1
            // QUERY_PARAM_KEY_1=QUERY_PARAM_VALUE_2
            // QUERY_PARAM_KEY_2=
            // QUERY_PARAM_KEY_2=QUERY_PARAM_VALUE_3
            // QUERY_PARAM_KEY_3=
            // QUERY_PARAM_KEY_1=QUERY_PARAM_VALUE_4
            Map<String, QueryParamValueBeforeAndAfterDeserialization> expectedQueryParams = Map.of(
                    QUERY_PARAM_KEY_1,
                    new QueryParamValueBeforeAndAfterDeserialization(QUERY_PARAM_KEY_1,
                            List.of(QUERY_PARAM_VALUE_1, String.valueOf(QUERY_PARAM_VALUE_2),
                                    QUERY_PARAM_VALUE_4),
                            List.of(QUERY_PARAM_VALUE_1, String.valueOf(QUERY_PARAM_VALUE_2),
                                    QUERY_PARAM_VALUE_4),
                            firstQueryParam),
                    QUERY_PARAM_KEY_2,
                    new QueryParamValueBeforeAndAfterDeserialization(QUERY_PARAM_KEY_2,
                            List.of(EMPTY_QUERY_VALUE, String.valueOf(QUERY_PARAM_VALUE_3)),
                            List.of(String.valueOf(QUERY_PARAM_VALUE_3)), secondQueryParam),
                    QUERY_PARAM_KEY_3, new QueryParamValueBeforeAndAfterDeserialization(
                            QUERY_PARAM_KEY_3, List.of(EMPTY_QUERY_VALUE), null, thirdQueryParam));
            assertQueryParams(uriInfo, expectedQueryParams);
            return RestResponse.ok();
        }

        @GET
        @Path("/propagated-query-params-and-query-params-in-origin-uri")
        public RestResponse<Void> endpointPropagatedQueryParamsAndQueryParamsInOriginUri(
                @Context UriInfo uriInfo, @QueryParam("existingParam") String existingParam,
                @QueryParam(QUERY_PARAM_KEY_1) List<String> firstQueryParam,
                @QueryParam(QUERY_PARAM_KEY_2) List<String> secondQueryParam,
                @QueryParam(QUERY_PARAM_KEY_3) List<String> thirdQueryParam) {
            // existingParam=existingValue (from origin URI)
            // QUERY_PARAM_KEY_3=QUERY_PARAM_VALUE_1 (from origin URI)
            // QUERY_PARAM_KEY_1=QUERY_PARAM_VALUE_1 (from request)
            // QUERY_PARAM_KEY_2= (from request)
            // QUERY_PARAM_KEY_1=QUERY_PARAM_VALUE_2 (from request)
            // QUERY_PARAM_KEY_1=QUERY_PARAM_VALUE_1 (from transformer)
            // QUERY_PARAM_KEY_2=QUERY_PARAM_VALUE_3 (from transformer)
            // QUERY_PARAM_KEY_2=QUERY_PARAM_VALUE_2 (from transformer)
            // QUERY_PARAM_KEY_3=QUERY_PARAM_VALUE_4 (from transformer)
            Map<String, QueryParamValueBeforeAndAfterDeserialization> expectedQueryParams = Map.of(
                    "existingParam",
                    new QueryParamValueBeforeAndAfterDeserialization("existingParam",
                            List.of("existingValue"), "existingValue", existingParam),
                    QUERY_PARAM_KEY_1,
                    new QueryParamValueBeforeAndAfterDeserialization(QUERY_PARAM_KEY_1,
                            List.of(String.valueOf(QUERY_PARAM_VALUE_1),
                                    String.valueOf(QUERY_PARAM_VALUE_2), QUERY_PARAM_VALUE_1),
                            List.of(String.valueOf(QUERY_PARAM_VALUE_1),
                                    String.valueOf(QUERY_PARAM_VALUE_2), QUERY_PARAM_VALUE_1),
                            firstQueryParam),
                    QUERY_PARAM_KEY_2,
                    new QueryParamValueBeforeAndAfterDeserialization(QUERY_PARAM_KEY_2,
                            List.of(EMPTY_QUERY_VALUE, String.valueOf(QUERY_PARAM_VALUE_3),
                                    String.valueOf(QUERY_PARAM_VALUE_2)),
                            List.of(String.valueOf(QUERY_PARAM_VALUE_3),
                                    String.valueOf(QUERY_PARAM_VALUE_2)),
                            secondQueryParam),
                    QUERY_PARAM_KEY_3,
                    new QueryParamValueBeforeAndAfterDeserialization(QUERY_PARAM_KEY_3,
                            List.of(QUERY_PARAM_VALUE_1, QUERY_PARAM_VALUE_4),
                            List.of(QUERY_PARAM_VALUE_1, QUERY_PARAM_VALUE_4), thirdQueryParam));
            assertQueryParams(uriInfo, expectedQueryParams);
            return RestResponse.ok();
        }
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest =
            new QuarkusUnitTest().setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class, QueryParamAssertions.class));

    @Test
    void test_addQueryParamsWithValues() {
        RestAssured.given().get("/values").then().statusCode(OK);
    }

    @Test
    void test_addQueryParamsWithNoValues() {
        RestAssured.given().get("/no-values").then().statusCode(OK);
    }

    @Test
    void test_addQueryParamsWhenOriginUriAlreadyHasQueryParams() {
        RestAssured.given().get("/query-params-in-origin-uri").then().statusCode(OK);
    }

    @Test
    void test_addQueryParamsWithPropagatedQueryParams() {
        RestAssured.given().queryParam(QUERY_PARAM_KEY_1, QUERY_PARAM_VALUE_1, QUERY_PARAM_VALUE_2)
                .queryParam(QUERY_PARAM_KEY_2).get("/propagated-query-params").then()
                .statusCode(OK);
    }

    @Test
    void test_addQueryWithPropagatedQueryParamsWhileOriginUriAlreadyHasQueryParams() {
        RestAssured.given().queryParam(QUERY_PARAM_KEY_1, QUERY_PARAM_VALUE_1)
                .queryParam(QUERY_PARAM_KEY_2).queryParam(QUERY_PARAM_KEY_1, QUERY_PARAM_VALUE_2)
                .get("/propagated-query-params-and-query-params-in-origin-uri").then()
                .statusCode(OK);
    }

    private static String encodeQueryParamSinglePair(String name, Object value) {
        return urlEncode(name) + QUERY_VALUE_SEPARATOR_SYMBOL
                + ((value == null) ? EMPTY_QUERY_VALUE : urlEncode(String.valueOf(value)));
    }
}
