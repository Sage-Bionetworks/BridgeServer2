package org.sagebionetworks.bridge.models;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class MetricsTest {
    private static final DateTime START_TIME = DateTime.parse("2018-02-16T17:23:05.590Z");
    private static final DateTime END_TIME = DateTime.parse("2018-02-16T17:23:06.791Z");
    private static final long EXPECTED_ELAPSED_MILLIS = 1201L;

    @AfterClass
    public static void cleanup() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test
    public void test() throws Exception {
        // Create Metrics.
        String requestId = "12345";
        Metrics metrics = new Metrics(requestId);
        assertNotNull(metrics);

        // Validate cache key.
        assertEquals(metrics.getCacheKey(), "12345:Metrics");
        assertEquals(Metrics.getCacheKey(requestId), "12345:Metrics");

        // Apply setters.
        metrics.setRecordId("test-record");

        // Validate JSON.
        final String json = metrics.toJsonString();
        assertNotNull(json);
        JsonNode metricsNode = BridgeObjectMapper.get().readTree(json);
        assertEquals(metricsNode.get("record_id").textValue(), "test-record");
        assertEquals(metricsNode.get("request_id").textValue(), requestId);
        assertTrue(metricsNode.hasNonNull("start"));
        assertEquals(metricsNode.get("version").intValue(), 1);
    }

    @Test
    public void testTimingMetrics() {
        // Mock start and test.
        DateTimeUtils.setCurrentMillisFixed(START_TIME.getMillis());
        Metrics metrics = new Metrics("12345");
        assertEquals(metrics.getJson().get("start").textValue(), START_TIME.toString());

        // Mock end and test.
        DateTimeUtils.setCurrentMillisFixed(END_TIME.getMillis());
        metrics.end();
        assertEquals(metrics.getJson().get("end").textValue(), END_TIME.toString());
        assertEquals(metrics.getJson().get("elapsedMillis").longValue(), EXPECTED_ELAPSED_MILLIS);
    }

    @Test
    public void testElapsedWithNoStart() {
        // This should never happen, but if it does, don't throw.
        Metrics metrics = new Metrics("12345");
        metrics.getJson().remove("start");
        metrics.end();
        assertFalse(metrics.getJson().has("elapsedMillis"));
    }

    @Test
    public void testElapsedWithInvalidStart() {
        // This should never happen, but if it does, don't throw.
        Metrics metrics = new Metrics("12345");
        metrics.getJson().put("start", "February 16, 2018 2 5:14pm");
        metrics.end();
        assertFalse(metrics.getJson().has("elapsedMillis"));
    }

    @Test
    public void testSetStatus() {
        String requestId = "12345";
        Metrics metrics = new Metrics(requestId);
        metrics.setStatus(200);
        final String json = metrics.toJsonString();
        assertTrue(json.contains("\"status\":200"));
    }

    @Test
    public void testSetAppId() {
        String requestId = "12345";
        Metrics metrics = new Metrics(requestId);
        metrics.setAppId(null);
        String json = metrics.toJsonString();
        assertFalse(json.contains("\"app_id\":"));
        metrics.setAppId(" ");
        json = metrics.toJsonString();
        assertFalse(json.contains("\"app_id\":"));
        metrics.setAppId(TEST_APP_ID);
        json = metrics.toJsonString();
        assertTrue(json.contains("\"app_id\":\""+TEST_APP_ID+"\""));
    }

    @Test
    public void testSetSession() {
        String requestId = "12345";
        Metrics metrics = new Metrics(requestId);
        metrics.setSessionId(null);
        String json = metrics.toJsonString();
        assertFalse(json.contains("\"session_id\":"));
        metrics.setSessionId("d839fe");
        json = metrics.toJsonString();
        assertTrue(json.contains("\"session_id\":\"d839fe\""));
    }

    @Test
    public void testSetQueryParams() throws Exception {
        // Test empty params metrics.
        String requestId = "12345";
        Metrics metrics = new Metrics(requestId);
        metrics.setQueryParams(null);
        String json = metrics.toJsonString();
        JsonNode metricsNode = BridgeObjectMapper.get().readTree(json);
        assertFalse(metricsNode.has("query_params"));

        Map<String, List<String>> paramsMap = new LinkedHashMap<>();
        metrics = new Metrics(requestId);
        metrics.setQueryParams(paramsMap);
        metricsNode = metrics.getJson();
        assertFalse(metricsNode.has("query_params"));

        paramsMap.put("not_real_key", new LinkedList<>());
        metrics = new Metrics(requestId);
        metrics.setQueryParams(paramsMap);
        metricsNode = metrics.getJson();
        assertTrue(metricsNode.has("query_params"));
        JsonNode paramsNode = metricsNode.get("query_params");
        assertEquals(1, paramsNode.size());
        assertTrue(paramsNode.has("not_real_key"));
        assertEquals(0, paramsNode.get("not_real_key").size());

        paramsMap.get("not_real_key").add("only_one");
        metrics = new Metrics(requestId);
        metrics.setQueryParams(paramsMap);
        metricsNode = metrics.getJson();
        assertTrue(metricsNode.has("query_params"));
        paramsNode = metricsNode.get("query_params");
        assertEquals(1, paramsNode.size());
        assertTrue(paramsNode.has("not_real_key"));
        assertTrue(paramsNode.get("not_real_key").isArray());
        assertEquals(1, paramsNode.get("not_real_key").size());
        assertEquals("only_one", paramsNode.get("not_real_key").get(0).textValue());

        paramsMap.get("not_real_key").add("the_second");
        metrics = new Metrics(requestId);
        metrics.setQueryParams(paramsMap);
        metricsNode = metrics.getJson();
        paramsNode = metricsNode.get("query_params");
        assertEquals(2, paramsNode.get("not_real_key").size());
        assertEquals("the_second", paramsNode.get("not_real_key").get(1).textValue());

        paramsMap.put("now_new_key", new LinkedList<>(Collections.singletonList("third")));
        metrics = new Metrics(requestId);
        metrics.setQueryParams(paramsMap);
        metricsNode = metrics.getJson();
        paramsNode = metricsNode.get("query_params");
        assertEquals(2, paramsNode.size());
        assertTrue(paramsNode.has("now_new_key"));
        assertEquals(1, paramsNode.get("now_new_key").size());
        assertEquals("third", paramsNode.get("now_new_key").get(0).textValue());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testConstructorRequestIdMustNotBeNull() {
        new Metrics(null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testConstructorRequestIdMustNotBeEmpty() {
        new Metrics(" ");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testGetCacheKeyRequestIdMustNotBeNull() {
        Metrics.getCacheKey(null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testGetCacheKeyRequestIdMustNotBeEmpty() {
        Metrics.getCacheKey(" ");
    }
}
