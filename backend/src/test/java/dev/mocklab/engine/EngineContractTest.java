package dev.mocklab.engine;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.mocklab.common.BusinessException;
import dev.mocklab.common.JsonCodec;
import dev.mocklab.config.MockProperties;
import dev.mocklab.model.Exchange;
import dev.mocklab.model.Scene;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class EngineContractTest {
    private final RouteMatcher routes = new RouteMatcher();
    private final RequestParser parser =
            new RequestParser(new MockProperties(), new JsonCodec(new ObjectMapper()));

    @Test
    void staticSegmentsWinAtFirstDifferentPosition() {
        assertTrue(routes.compareSpecificity("/orders/latest", "/orders/{id}") < 0);
        assertTrue(routes.compareSpecificity("/a/static/{x}", "/a/{x}/static") < 0);
        assertEquals(routes.shape("/orders/{id}"), routes.shape("/orders/{orderId}"));
    }

    @Test
    void pathsAreCaseAndTrailingSlashSensitive() {
        assertNull(routes.parameters("/orders", "/Orders"));
        assertNull(routes.parameters("/orders", "/orders/"));
        assertNull(routes.parameters("/orders/{id}", "/orders/"));
        assertEquals("a+b", routes.parameters("/orders/{id}", "/orders/a+b").get("id"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/x/%2f", "/x/%5c", "/x/%2e%2e", "/x/%xx", "/x/../a", "/x//a"})
    void rejectAmbiguousAndEncodedTraversalPaths(String path) {
        assertEquals(
                400,
                assertThrows(BusinessException.class, () -> routes.assertRequestPath(path))
                        .getStatus());
    }

    @Test
    void repeatedQueryAndFormValuesAreArrays() {
        Exchange request =
                parser.parse(
                        "POST",
                        "/x",
                        "q=a&q=b&empty=",
                        Map.of("Content-Type", "application/x-www-form-urlencoded"),
                        "k=1&k=2".getBytes());
        assertEquals(List.of("a", "b"), request.getQuery().get("q"));
        assertEquals("", request.getQuery().get("empty"));
        assertEquals(List.of("1", "2"), ((Map<?, ?>) request.getBody()).get("k"));
    }

    @Test
    void missingNullAndTextComparisonRemainDistinct() {
        Scene fallback = new Scene();
        fallback.setName("fallback");
        fallback.setFallback(true);
        Scene condition = new Scene();
        condition.setName("condition");
        condition.setField("body.id");
        condition.setPriority(10);
        condition.setValue("1001");
        SceneMatcher matcher = new SceneMatcher(new JsonCodec(new ObjectMapper()));
        Exchange request =
                parser.parse(
                        "POST",
                        "/x",
                        null,
                        Map.of("content-type", "application/json"),
                        "{\"id\":1001}".getBytes());
        assertEquals("condition", matcher.select(List.of(fallback, condition), request).getName());
        request.setBody(Map.of());
        condition.setOperator("ne");
        assertEquals("fallback", matcher.select(List.of(condition, fallback), request).getName());
        request.setBody(new HashMap<>(Map.of("id", "value")));
        ((Map<String, Object>) request.getBody()).put("id", null);
        condition.setOperator("exists");
        assertEquals("condition", matcher.select(List.of(condition, fallback), request).getName());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Connection",
                "Content-Length",
                "Transfer-Encoding",
                "X-Mock-Platform-Error"
            })
    void protocolHeadersCannotBeConfigured(String name) {
        assertThrows(
                BusinessException.class,
                () -> new HeaderPolicy().merge("application/json", Map.of(name, "x"), Map.of()));
    }

    @Test
    void invalidJsonAndUnsupportedCharsetAreRejected() {
        assertEquals(
                400,
                assertThrows(
                                BusinessException.class,
                                () ->
                                        parser.parse(
                                                "POST",
                                                "/x",
                                                null,
                                                Map.of("content-type", "application/json"),
                                                "{".getBytes()))
                        .getStatus());
        assertEquals(
                415,
                assertThrows(
                                BusinessException.class,
                                () ->
                                        parser.parse(
                                                "POST",
                                                "/x",
                                                null,
                                                Map.of("content-type", "text/plain;charset=GBK"),
                                                new byte[0]))
                        .getStatus());
    }
}
