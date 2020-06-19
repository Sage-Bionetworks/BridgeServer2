package org.sagebionetworks.bridge.models;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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
    public void testSetQueryParams() {
        // Test empty params metrics.
        String requestId = "12345";
        Metrics metrics = new Metrics(requestId);
        metrics.setQueryParams(null);
        String json = metrics.toJsonString();
        assertFalse(json.contains("\"query_params\":"));

        // Test parsed, but no query params metrics.
        List<NameValuePair> testList = new ArrayList<>(URLEncodedUtils.parse("", StandardCharsets.UTF_8));
        metrics.setQueryParams(testList);
        json = metrics.toJsonString();
        assertTrue(json.contains("\"query_params\":{}"));

        // Test parsed, but not in allowlist params metrics.
        testList.addAll(URLEncodedUtils.parse("email=not_a_real_email@fake.com&name=piiLeaking", StandardCharsets.UTF_8));
        metrics.setQueryParams(testList);
        json = metrics.toJsonString();
        assertTrue(json.contains("\"query_params\":{}"));

        // Test parsed, in-allowlist params metrics.
        testList.addAll(URLEncodedUtils.parse("consents=true&pageSize=42", StandardCharsets.UTF_8));
        metrics.setQueryParams(testList);
        json = metrics.toJsonString();
        assertTrue(json.contains("\"query_params\":{\"consents\":\"true\",\"pageSize\":\"42\"}"));
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
