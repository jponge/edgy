package org.acme.edgy.runtime.builtins.assertions;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;

public interface QueryParamAssertions {

    static void assertQueryParams(UriInfo uriInfo,
            Map<String, QueryParamValueBeforeAndAfterDeserialization> mapWithExpectedQueryParams) {
        MultivaluedMap<String, String> actualQueryParams = uriInfo.getQueryParameters();
        // testing deserialization
        for (QueryParamValueBeforeAndAfterDeserialization deserializedExpectedAndActual : mapWithExpectedQueryParams
                .values()) {
            deserializedExpectedAndActual.assertDeserialization();
        }
        // testing before deserialization (UriInfo)
        MultivaluedMap<String, String> expectedQueryParams =
                mapWithExpectedQueryParams.entrySet().stream().collect(MultivaluedHashMap::new,
                        (map, entry) -> map.put(entry.getKey(),
                                entry.getValue().beforeDeserializationExpected()),
                        MultivaluedMap::putAll);
        if (!expectedQueryParams.equals(actualQueryParams)) {
            throw new AssertionError("Expected query params: " + expectedQueryParams + ", but got: "
                    + actualQueryParams);
        }
    }

    record QueryParamValueBeforeAndAfterDeserialization(String queryName,
            List<String> beforeDeserializationExpected, Object afterDeserializationExpected,
            Object afterDeserializationActual) {
        public void assertDeserialization() {
            if (!Objects.equals(afterDeserializationExpected, afterDeserializationActual)) {
                throw new AssertionError(
                        "For query param '" + queryName + "', expected after deserialization: "
                                + afterDeserializationExpected + ", but got: "
                                + afterDeserializationActual);
            }
        }
    }

}
