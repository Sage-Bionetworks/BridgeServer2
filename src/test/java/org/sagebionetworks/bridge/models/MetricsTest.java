package org.sagebionetworks.bridge.models;

import static org.sagebionetworks.bridge.BridgeConstants.API_APP_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

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
    public void testSetStudy() {
        String requestId = "12345";
        Metrics metrics = new Metrics(requestId);
        metrics.setStudy(null);
        String json = metrics.toJsonString();
        assertFalse(json.contains("\"study\":"));
        metrics.setStudy(" ");
        json = metrics.toJsonString();
        assertFalse(json.contains("\"study\":"));
        metrics.setStudy(API_APP_ID);
        json = metrics.toJsonString();
        assertTrue(json.contains("\"study\":\"api\""));
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
