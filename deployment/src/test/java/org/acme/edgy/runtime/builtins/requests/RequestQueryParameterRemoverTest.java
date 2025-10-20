package org.acme.edgy.runtime.builtins.requests;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
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
import org.acme.edgy.runtime.api.utils.QueryParamUtils;
import org.acme.edgy.runtime.builtins.assertions.QueryParamAssertions;
import org.acme.edgy.runtime.builtins.assertions.QueryParamAssertions.QueryParamValueBeforeAndAfterDeserialization;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import java.util.List;
import java.util.Map;
import static org.acme.edgy.runtime.api.utils.QueryParamUtils.EMPTY_QUERY_VALUE;
import static org.acme.edgy.runtime.api.utils.QueryParamUtils.QUERY_SEPARATOR_SYMBOL;
import static org.acme.edgy.runtime.api.utils.QueryParamUtils.QUERY_VALUE_SEPARATOR_SYMBOL;
import static org.acme.edgy.runtime.api.utils.QueryParamUtils.urlEncode;
import static org.acme.edgy.runtime.builtins.assertions.QueryParamAssertions.assertQueryParams;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.OK;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestQueryParameterRemoverTest {

    // special characters to test URL encoding
    private static final String QUERY_PARAM_KEY_1 = "?&= +a%04ff%?";
    private static final String QUERY_PARAM_KEY_2 = "?&= %20 +b";
    private static final String QUERY_PARAM_KEY_3 = "?&= %20 +c";

    private static final String QUERY_PARAM_VALUE_1 = "?&1";
    private static final String QUERY_PARAM_VALUE_2 = "?&2";
    private static final String QUERY_PARAM_VALUE_3 = "?&3";

    @ApplicationScoped
    static class RoutingProvider {

        @Produces
        RoutingConfiguration routingConfiguration() {
            return new RoutingConfiguration()
                    .addRoute(new Route("/some", Origin.of("http://localhost:8081/test/some"), PathMode.FIXED)
                        .addRequestTransformer(new RequestQueryParameterRemover(QUERY_PARAM_KEY_1, QUERY_PARAM_KEY_2)))
                    .addRoute(new Route("/all", Origin.of("http://localhost:8081/test/all"), PathMode.FIXED)
                        .addRequestTransformer(new RequestQueryParameterRemover(QUERY_PARAM_KEY_1, QUERY_PARAM_KEY_2, QUERY_PARAM_KEY_3)))
                    .addRoute(new Route("/none", Origin.of("http://localhost:8081/test/none"), PathMode.FIXED)
                            .addRequestTransformer(new RequestQueryParameterRemover(
                                    QUERY_PARAM_KEY_1, QUERY_PARAM_KEY_2)))
                    .addRoute(new Route("/no-query",
                            Origin.of("http://localhost:8081/test/no-query"), PathMode.FIXED)
                                    .addRequestTransformer(new RequestQueryParameterRemover(
                                            QUERY_PARAM_KEY_1, QUERY_PARAM_KEY_2)))
                    .addRoute(new Route("/origin-query-params",
                            Origin.of("http://localhost:8081/test/origin-query-params?"
                                    + String.join(QUERY_SEPARATOR_SYMBOL,
                                            encodeQueryParamSinglePair(QUERY_PARAM_KEY_1,
                                                    QUERY_PARAM_VALUE_1),
                                            encodeQueryParamSinglePair(QUERY_PARAM_KEY_3,
                                                    QUERY_PARAM_VALUE_3),
                                            encodeQueryParamSinglePair(QUERY_PARAM_KEY_2),
                                            encodeQueryParamSinglePair(QUERY_PARAM_KEY_1,
                                                    QUERY_PARAM_VALUE_3))),
                            PathMode.FIXED).addRequestTransformer(
                                    new RequestQueryParameterRemover(QUERY_PARAM_KEY_2,
                                            QUERY_PARAM_KEY_3)))
                    .addRoute(new Route("/origin-query-params-with-api-gateway-queries", Origin.of(
                            "http://localhost:8081/test/origin-query-params-with-api-gateway-queries?"
                                    + String.join(QUERY_SEPARATOR_SYMBOL,
                                            encodeQueryParamSinglePair(QUERY_PARAM_KEY_1,
                                                    QUERY_PARAM_VALUE_1),
                                            encodeQueryParamSinglePair(QUERY_PARAM_KEY_3,
                                                    QUERY_PARAM_VALUE_3),
                                            encodeQueryParamSinglePair(QUERY_PARAM_KEY_2),
                                            encodeQueryParamSinglePair(QUERY_PARAM_KEY_1,
                                                    QUERY_PARAM_VALUE_3))),
                            PathMode.FIXED).addRequestTransformer(
                                    new RequestQueryParameterRemover(QUERY_PARAM_KEY_2,
                                            QUERY_PARAM_KEY_3)))
                    .addRoute(new Route("/add-query-param-transformer",
                            Origin.of("http://localhost:8081/test/add-query-param-transformer"),
                            PathMode.FIXED)
                                    .addRequestTransformer(new RequestQueryParameterAdder(
                                            QUERY_PARAM_KEY_1, QUERY_PARAM_VALUE_1))
                                    .addRequestTransformer(
                                            new RequestQueryParameterRemover(QUERY_PARAM_KEY_1)));
        }
    }

    @Path("/test")
    static class TestApi {

        @GET
        @Path("/some")
        public RestResponse<Void> endpointSome(@Context UriInfo uriInfo, @QueryParam(QUERY_PARAM_KEY_3) String thirdQueryParam) {
            Map<String, QueryParamValueBeforeAndAfterDeserialization> expectedQueryParams =
                    Map.of(QUERY_PARAM_KEY_3,
                            new QueryParamValueBeforeAndAfterDeserialization(QUERY_PARAM_KEY_3,
                                    List.of(QUERY_PARAM_VALUE_3), QUERY_PARAM_VALUE_3,
                                    thirdQueryParam));
            assertQueryParams(uriInfo, expectedQueryParams);
            return RestResponse.ok();
        }

        @GET
        @Path("/all")
        public RestResponse<Void> endpointAll(@Context UriInfo uriInfo) {
            assertTrue(uriInfo.getQueryParameters().isEmpty());
            return RestResponse.ok();
        }

        @GET
        @Path("/none")
        public RestResponse<Void> endpointNone(@Context UriInfo uriInfo, @QueryParam(QUERY_PARAM_KEY_3) String thirdQueryParam) {
            Map<String, QueryParamValueBeforeAndAfterDeserialization> expectedQueryParams =
                    Map.of(QUERY_PARAM_KEY_3,
                            new QueryParamValueBeforeAndAfterDeserialization(QUERY_PARAM_KEY_3,
                                    List.of(QUERY_PARAM_VALUE_3), QUERY_PARAM_VALUE_3,
                                    thirdQueryParam));
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
        @Path("/origin-query-params")
        public RestResponse<Void> endpointOriginQueryParams(@Context UriInfo uriInfo,
                @QueryParam(QUERY_PARAM_KEY_1) List<String> firstQueryParam) {
            Map<String, QueryParamValueBeforeAndAfterDeserialization> expectedQueryParams = Map.of(
                    QUERY_PARAM_KEY_1,
                    new QueryParamValueBeforeAndAfterDeserialization(QUERY_PARAM_KEY_1,
                            List.of(QUERY_PARAM_VALUE_1, QUERY_PARAM_VALUE_3),
                            List.of(QUERY_PARAM_VALUE_1, QUERY_PARAM_VALUE_3), firstQueryParam));
            assertQueryParams(uriInfo, expectedQueryParams);
            return RestResponse.ok();
        }

        @GET
        @Path("/origin-query-params-with-api-gateway-queries")
        public RestResponse<Void> endpointOriginQueryParamsWithApiGatewayQueries(
                @Context UriInfo uriInfo,
                @QueryParam(QUERY_PARAM_KEY_1) List<String> firstQueryParam) {
            Map<String, QueryParamValueBeforeAndAfterDeserialization> expectedQueryParams = Map.of(
                    QUERY_PARAM_KEY_1,
                    new QueryParamValueBeforeAndAfterDeserialization(QUERY_PARAM_KEY_1,
                            List.of(QUERY_PARAM_VALUE_1, QUERY_PARAM_VALUE_3, QUERY_PARAM_VALUE_1),
                            List.of(QUERY_PARAM_VALUE_1, QUERY_PARAM_VALUE_3, QUERY_PARAM_VALUE_1),
                            firstQueryParam));
            assertQueryParams(uriInfo, expectedQueryParams);
            return RestResponse.ok();
        }

        @GET
        @Path("/add-query-param-transformer")
        public RestResponse<Void> endpointAddQueryParamTransformer(@Context UriInfo uriInfo) {
            assertTrue(uriInfo.getQueryParameters().isEmpty());
            return RestResponse.ok();
        }
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class, QueryParamAssertions.class));

    @Test
    void test_removeSomeQueryParams() {
        RestAssured.given()
                .queryParam(QUERY_PARAM_KEY_1, QUERY_PARAM_VALUE_1, QUERY_PARAM_VALUE_2) // removing
                .queryParam(QUERY_PARAM_KEY_2, QUERY_PARAM_VALUE_1, QUERY_PARAM_VALUE_2) // removing
                .queryParam(QUERY_PARAM_KEY_3, QUERY_PARAM_VALUE_3) // keeping
                .queryParam(QUERY_PARAM_KEY_1, QUERY_PARAM_VALUE_3) // removing
                .get("/some")
                .then()
                .statusCode(OK);
    }

    @Test
    void test_removeAllQueryParams() {
        RestAssured.given()
                .queryParam(QUERY_PARAM_KEY_1, QUERY_PARAM_VALUE_1, QUERY_PARAM_VALUE_2) // removing
                .queryParam(QUERY_PARAM_KEY_2, QUERY_PARAM_VALUE_1, QUERY_PARAM_VALUE_2) // removing
                .queryParam(QUERY_PARAM_KEY_1, QUERY_PARAM_VALUE_3) // removing
                .get("/all")
                .then()
                .statusCode(OK);
    }

    @Test
    void test_removeNonExistingQueryParams() {
        RestAssured.given()
                .queryParam(QUERY_PARAM_KEY_3, QUERY_PARAM_VALUE_3) // keeping
                .get("/none")
                .then()
                .statusCode(OK);
    }

    @Test
    void test_removeNonExistingQueryParamInQuery() {
        RestAssured.given().get("/no-query").then().statusCode(OK);
    }

    @Test
    void test_removeQueryParamsFromOriginUri() {
        RestAssured.given().get("/origin-query-params").then().statusCode(OK);
    }

    @Test
    void test_removeQueryParamsFromOriginUriWithApiGatewayQueries() {
        RestAssured.given().queryParam(QUERY_PARAM_KEY_1, QUERY_PARAM_VALUE_1) // keeping
                .queryParam(QUERY_PARAM_KEY_2, QUERY_PARAM_VALUE_2) // removing
                .get("/origin-query-params-with-api-gateway-queries").then().statusCode(OK);
    }

    @Test
    void test_removeQueryParamAfterAddQueryParamTransformer() {
        RestAssured.given().get("/add-query-param-transformer").then().statusCode(OK);
    }

    private static String encodeQueryParamSinglePair(String name, Object value) {
            return urlEncode(name) + QUERY_VALUE_SEPARATOR_SYMBOL
                            + ((value == null) ? EMPTY_QUERY_VALUE
                                            : urlEncode(String.valueOf(value)));
    }

    private static String encodeQueryParamSinglePair(String name) {
            return encodeQueryParamSinglePair(name, null);
    }
}
