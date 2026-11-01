package dev.mocklab.logging;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.mocklab.common.JsonCodec;
import dev.mocklab.config.MockProperties;
import dev.mocklab.entity.MockCallLog;
import dev.mocklab.model.Exchange;
import dev.mocklab.model.MockResult;
import dev.mocklab.service.CallLogService;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collection;
import java.util.List;
import java.util.Map;

class LogWriterContractTest {
    @Test
    void diagnosticsMaskNestedSecretsWithoutChangingResponse() {
        CallLogService service = mock(CallLogService.class);
        MockProperties properties = new MockProperties();
        properties.setLogBody(true);
        LogWriter writer =
                new LogWriter(
                        service,
                        new JsonCodec(new ObjectMapper()),
                        properties,
                        new SimpleMeterRegistry());
        Exchange request = new Exchange();
        request.setMethod("POST");
        request.setHeaders(Map.of("Authorization", "secret-header"));
        request.setQuery(Map.of("token", "secret-query"));
        request.setBody(
                Map.of("items", List.of(Map.of("password", "secret-body", "orderId", "1001"))));
        MockResult response = new MockResult();
        response.setTraceId("trace");
        response.setSource("LIVE");
        response.setBody("{\"token\":\"secret-response\",\"message\":\"ok\"}");
        String original = response.getBody();
        writer.record(1L, 1L, "ERP", "/orders", "127.0.0.1", request, response);
        writer.flush();
        ArgumentCaptor<Collection<MockCallLog>> capture = ArgumentCaptor.forClass(Collection.class);
        verify(service).saveBatch(capture.capture(), eq(500));
        MockCallLog saved = capture.getValue().iterator().next();
        assertFalse(saved.getRequestDetail().contains("secret-"));
        assertFalse(saved.getResponseDetail().contains("secret-"));
        assertTrue(saved.getRequestDetail().contains("1001"));
        assertEquals(original, response.getBody());
    }

    @Test
    void databaseLogFailureDoesNotChangeCallResult() {
        CallLogService service = mock(CallLogService.class);
        doThrow(new IllegalStateException("database unavailable"))
                .when(service)
                .saveBatch(anyCollection(), anyInt());
        LogWriter writer =
                new LogWriter(
                        service,
                        new JsonCodec(new ObjectMapper()),
                        new MockProperties(),
                        new SimpleMeterRegistry());
        Exchange request = new Exchange();
        request.setMethod("GET");
        MockResult response = new MockResult();
        response.setTraceId("trace");
        response.setSource("LIVE");
        response.setBody("ok");
        assertDoesNotThrow(
                () -> {
                    writer.record(1L, 1L, "ERP", "/orders", "127.0.0.1", request, response);
                    writer.flush();
                });
        assertEquals("ok", response.getBody());
    }
}
