package org.acme.edgy.runtime.builtins.requests;

import static org.acme.edgy.runtime.api.utils.QueryParamUtils.EMPTY_QUERY_VALUE;
import static org.acme.edgy.runtime.api.utils.QueryParamUtils.QUERY_VALUE_SEPARATOR_SYMBOL;
import static org.acme.edgy.runtime.api.utils.QueryParamUtils.urlEncode;
import static org.acme.edgy.runtime.builtins.assertions.QueryParamAssertions.assertQueryParams;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.OK;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
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

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

class RequestQueryParameterReplacerTest {

    // special characters to test URL encoding
    private static final String QUERY_PARAM_KEY_1 = "?rep=%%19ff &l +\\*&key$#";
    private static final String QUERY_PARAM_KEY_2 = "?repl&k%10&ey2> <";
    private static final String QUERY_PARAM_VALUE_1 = "1o? &=%10ldVal";
    private static final String QUERY_PARAM_VALUE_2 = "1o? &=%10nwldVal";

    @ApplicationScoped
    static class RoutingProvider {
        @Produces
        RoutingConfiguration routingConfiguration() {
            return new RoutingConfiguration()
                    .addRoute(new Route("/replace-with-value",
                                            Origin.of("origin-1", "http://localhost:8081/test/replace-with-value"),
                                            PathMode.FIXED).addRequestTransformer(
                                    new RequestQueryParameterReplacer(QUERY_PARAM_KEY_1,
                                            QUERY_PARAM_VALUE_2)))
                    .addRoute(new Route("/replace-non-existing",
                                            Origin.of("origin-2", "http://localhost:8081/test/replace-non-existing"),
                                            PathMode.FIXED).addRequestTransformer(
                                    new RequestQueryParameterReplacer(QUERY_PARAM_KEY_2,
                                            QUERY_PARAM_VALUE_2)))
                    .addRoute(new Route("/no-query",
                                            Origin.of("origin-3", "http://localhost:8081/test/no-query"),
                                            PathMode.FIXED)
                                    .addRequestTransformer(new RequestQueryParameterReplacer(
                                            QUERY_PARAM_KEY_2, QUERY_PARAM_VALUE_2)))
                    .addRoute(new Route("/replace-empty",
                                            Origin.of("origin-4", "http://localhost:8081/test/replace-with-value"),
                                            PathMode.FIXED).addRequestTransformer(
                                    new RequestQueryParameterReplacer(QUERY_PARAM_KEY_1)))
                    .addRoute(new Route("/replace-multiple-values",
                                            Origin.of("origin-5", "http://localhost:8081/test/replace-multiple-values"),
                                            PathMode.FIXED).addRequestTransformer(
                                    new RequestQueryParameterReplacer(QUERY_PARAM_KEY_1,
                                            QUERY_PARAM_VALUE_2, QUERY_PARAM_VALUE_1)))
                    .addRoute(new Route("/replace-origin-uri-query-params",
                                            Origin.of("origin-6",
                                                            "http://localhost:8081/test/replace-origin-uri-query-params?"
                                    + encodeQueryParamSinglePair(QUERY_PARAM_KEY_1,
                                                    EMPTY_QUERY_VALUE)),
                            PathMode.FIXED).addRequestTransformer(
                                    new RequestQueryParameterReplacer(QUERY_PARAM_KEY_1,
                                            QUERY_PARAM_VALUE_2)))
                    .addRoute(new Route("/replace-propagated-and-origin-uri-query-params", Origin
                                            .of("origin-7", "http://localhost:8081/test/replace-propagated-and-origin-uri-query-params?"
                                    + encodeQueryParamSinglePair(QUERY_PARAM_KEY_1,
                                                    QUERY_PARAM_VALUE_1)),
                            PathMode.FIXED).addRequestTransformer(
                                    new RequestQueryParameterReplacer(QUERY_PARAM_KEY_1,
                                            QUERY_PARAM_VALUE_2, QUERY_PARAM_VALUE_1)))
                    .addRoute(new Route("/add-query-param-transformer",
                                            Origin.of("origin-8",
                                                            "http://localhost:8081/test/add-query-param-transformer"),
                                            PathMode.FIXED)
                                    .addRequestTransformer(new RequestQueryParameterAdder(
                                            QUERY_PARAM_KEY_1, QUERY_PARAM_VALUE_1))
                                    .addRequestTransformer(new RequestQueryParameterReplacer(
                                            QUERY_PARAM_KEY_1, QUERY_PARAM_VALUE_2)));
        }
    }

    @Path("/test")
    static class TestApi {
        @GET
        @Path("/replace-with-value")
        public RestResponse<Void> endpointReplace(@Context UriInfo uriInfo,
                @QueryParam(QUERY_PARAM_KEY_1) String firstValue) {
            Map<String, QueryParamValueBeforeAndAfterDeserialization> expectedQueryParams =
                    Map.of(QUERY_PARAM_KEY_1,
                            new QueryParamValueBeforeAndAfterDeserialization(QUERY_PARAM_KEY_1,
                                    List.of(QUERY_PARAM_VALUE_2), QUERY_PARAM_VALUE_2, firstValue));
            assertQueryParams(uriInfo, expectedQueryParams);
            return RestResponse.ok();
        }

        @GET
        @Path("/replace-non-existing")
        public RestResponse<Void> endpointReplaceNonExisting(@Context UriInfo uriInfo,
                @QueryParam(QUERY_PARAM_KEY_1) String firstParam) {
            Map<String, QueryParamValueBeforeAndAfterDeserialization> expectedQueryParams =
                    Map.of(QUERY_PARAM_KEY_1,
                            new QueryParamValueBeforeAndAfterDeserialization(QUERY_PARAM_KEY_1,
                                    List.of(QUERY_PARAM_VALUE_1), QUERY_PARAM_VALUE_1, firstParam));
            assertQueryParams(uriInfo, expectedQueryParams);
            return RestResponse.ok();
        }

        @GET
        @Path("/no-query")
        public RestResponse<Void> endpointNoQuery(@Context UriInfo uriInfo) {
            assertTrue(uriInfo.getQueryParameters().isEmpty());
            return RestResponse.ok();
        }

        @GET
        @Path("/replace-empty")
        public RestResponse<Void> endpointReplaceEmpty(@Context UriInfo uriInfo,
                @QueryParam(QUERY_PARAM_KEY_1) String firstValue) {
            Map<String, QueryParamValueBeforeAndAfterDeserialization> expectedQueryParams =
                    Map.of(QUERY_PARAM_KEY_1, new QueryParamValueBeforeAndAfterDeserialization(
                            QUERY_PARAM_KEY_1, List.of(EMPTY_QUERY_VALUE), null, firstValue));
            assertQueryParams(uriInfo, expectedQueryParams);
            return RestResponse.ok();
        }

        @GET
        @Path("/replace-multiple-values")
        public RestResponse<Void> endpointReplaceMultipleValues(@Context UriInfo uriInfo,
                @QueryParam(QUERY_PARAM_KEY_1) List<String> firstValues,
                @QueryParam(QUERY_PARAM_KEY_2) String secondValue) {
            Map<String, QueryParamValueBeforeAndAfterDeserialization> expectedQueryParams =
                    Map.of(QUERY_PARAM_KEY_1,
                            new QueryParamValueBeforeAndAfterDeserialization(QUERY_PARAM_KEY_1,
                                    List.of(QUERY_PARAM_VALUE_2, QUERY_PARAM_VALUE_1),
                                    List.of(QUERY_PARAM_VALUE_2, QUERY_PARAM_VALUE_1), firstValues),
                            QUERY_PARAM_KEY_2,
                            new QueryParamValueBeforeAndAfterDeserialization(QUERY_PARAM_KEY_2,
                                    List.of(QUERY_PARAM_VALUE_2), QUERY_PARAM_VALUE_2,
                                    secondValue));
            assertQueryParams(uriInfo, expectedQueryParams);
            return RestResponse.ok();
        }

        @GET
        @Path("/replace-origin-uri-query-params")
        public RestResponse<Void> endpointOriginUri(@Context UriInfo uriInfo,
                @QueryParam(QUERY_PARAM_KEY_1) List<String> firstValues) {
            Map<String, QueryParamValueBeforeAndAfterDeserialization> expectedQueryParams =
                    Map.of(QUERY_PARAM_KEY_1,
                            new QueryParamValueBeforeAndAfterDeserialization(QUERY_PARAM_KEY_1,
                                    List.of(QUERY_PARAM_VALUE_2), List.of(QUERY_PARAM_VALUE_2),
                                    firstValues));
            assertQueryParams(uriInfo, expectedQueryParams);
            return RestResponse.ok();
        }

        @GET
        @Path("/replace-propagated-and-origin-uri-query-params")
        public RestResponse<Void> endpointPropagatedAndOriginUri(@Context UriInfo uriInfo,
                @QueryParam(QUERY_PARAM_KEY_1) List<String> firstValues) {
            Map<String, QueryParamValueBeforeAndAfterDeserialization> expectedQueryParams = Map.of(
                    QUERY_PARAM_KEY_1,
                    new QueryParamValueBeforeAndAfterDeserialization(QUERY_PARAM_KEY_1,
                            List.of(QUERY_PARAM_VALUE_2, QUERY_PARAM_VALUE_1),
                            List.of(QUERY_PARAM_VALUE_2, QUERY_PARAM_VALUE_1), firstValues));
            assertQueryParams(uriInfo, expectedQueryParams);
            return RestResponse.ok();
        }

        @GET
        @Path("/add-query-param-transformer")
        public RestResponse<Void> endpointAddQueryParamTransformer(@Context UriInfo uriInfo,
                @QueryParam(QUERY_PARAM_KEY_1) String firstValue) {
            Map<String, QueryParamValueBeforeAndAfterDeserialization> expectedQueryParams =
                    Map.of(QUERY_PARAM_KEY_1,
                            new QueryParamValueBeforeAndAfterDeserialization(QUERY_PARAM_KEY_1,
                                    List.of(QUERY_PARAM_VALUE_2), QUERY_PARAM_VALUE_2, firstValue));
            assertQueryParams(uriInfo, expectedQueryParams);
            return RestResponse.ok();
        }
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest =
            new QuarkusUnitTest().setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class, QueryParamAssertions.class));

    @Test
    void test_replaceQueryParam() {
        RestAssured.given().queryParam(QUERY_PARAM_KEY_1, QUERY_PARAM_VALUE_1)
                .get("/replace-with-value").then().statusCode(OK);
    }

    @Test
    void test_replaceNonExistingQueryParamInQuery() {
        RestAssured.given().queryParam(QUERY_PARAM_KEY_1, QUERY_PARAM_VALUE_1)
                .get("/replace-non-existing").then().statusCode(OK);
    }

    @Test
    void test_replaceNonExistingQueryParamInNoQuery() {
        RestAssured.given().get("/no-query").then().statusCode(OK);
    }

    @Test
    void test_replaceEmptyQuery() {
        RestAssured.given().queryParam(QUERY_PARAM_KEY_1, QUERY_PARAM_VALUE_1)
                .get("/replace-with-value").then().statusCode(OK);
    }

    @Test
    void test_replaceMultipleValues() {
        RestAssured.given().queryParam(QUERY_PARAM_KEY_1, QUERY_PARAM_VALUE_1)
                .queryParam(QUERY_PARAM_KEY_2, QUERY_PARAM_VALUE_2)
                .queryParam(QUERY_PARAM_KEY_1, QUERY_PARAM_VALUE_2).get("/replace-multiple-values")
                .then().statusCode(OK);
    }

    @Test
    void test_replaceOriginUriQueryParams() {
        RestAssured.given().get("/replace-origin-uri-query-params").then().statusCode(OK);
    }

    @Test
    void test_replacePropagatedAndOriginUriQueryParams() {
        RestAssured.given().queryParam(QUERY_PARAM_KEY_1, QUERY_PARAM_VALUE_1)
                .get("/replace-propagated-and-origin-uri-query-params").then().statusCode(OK);
    }

    @Test
    void test_replaceQueryParamAfterAddQueryParamTransformer() {
        RestAssured.given().get("/add-query-param-transformer").then().statusCode(OK);
    }

    private static String encodeQueryParamSinglePair(String name, Object value) {
        return urlEncode(name) + QUERY_VALUE_SEPARATOR_SYMBOL
                + ((value == null) ? EMPTY_QUERY_VALUE : urlEncode(String.valueOf(value)));
    }
}
