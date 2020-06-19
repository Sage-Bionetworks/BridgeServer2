package org.sagebionetworks.bridge.models;

import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import org.apache.http.NameValuePair;
import org.joda.time.DateTime;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.time.DateUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.sagebionetworks.bridge.json.JsonUtils;

import java.util.List;

/**
 * Request-scoped metrics.
 */
public class Metrics {

    /** The version of the metrics schema. */
    private static final int VERSION = 1;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<String> ALLOW_LIST =
            BridgeConfigFactory.getConfig().getList("query.param.allowlist");

    private final ObjectNode json;

    public static String getCacheKey(String requestId) {
        checkArgument(isNotBlank(requestId), "Request ID cannot be blank.");
        return requestId + ":" + Metrics.class.getSimpleName();
    }

    public Metrics(final String requestId) {
        json = MAPPER.createObjectNode();
        json.put("version", VERSION);
        start();
        setRequestId(requestId);
    }

    public String getCacheKey() {
        return Metrics.getCacheKey(json.get("request_id").asText());
    }

    /** The JSON node backing this metrics object. This is used primarily for testing. */
    public ObjectNode getJson() {
        return json;
    }

    public String toJsonString() {
        return json.toString();
    }

    public void start() {
        json.put("start", DateUtils.getCurrentISODateTime());
    }

    public void end() {
        // Log endTime
        DateTime endDateTime = DateUtils.getCurrentDateTime();
        json.put("end", endDateTime.toString());

        // Calculate elapsed time.
        DateTime startDateTime = JsonUtils.asDateTime(json, "start");
        if (startDateTime == null) {
            // This should not be possible, but if it happens, don't throw an NPE.
            return;
        }
        long elapsedMillis = endDateTime.getMillis() - startDateTime.getMillis();
        json.put("elapsedMillis", elapsedMillis);
    }

    /** Record ID, used for synchronous health data submission API. */
    public void setRecordId(String recordId) {
        put("record_id", recordId);
    }

    public void setRequestId(String requestId) {
        checkArgument(isNotBlank(requestId), "Request ID cannot be blank.");
        json.put("request_id", requestId);
    }

    public void setRemoteAddress(String remoteAddress) {
        put("remote_address", remoteAddress);
    }

    public void setMethod(String method) {
        put("method", method);
    }

    public void setUri(String uri) {
        put("uri", uri);
    }

    public void setProtocol(String protocol) {
        put("protocol", protocol);
    }

    public void setUserAgent(String userAgent) {
        put("user_agent", userAgent);
    }

    public void setStatus(int status) {
        json.put("status", status);
    }

    public void setAppId(String appId) {
        put("app_id", appId);
    }

    public void setUserId(String userId) {
        put("user_id", userId);
    }

    public void setSessionId(String sessionId) {
        put("session_id", sessionId);
    }

    public void setUploadId(String uploadId) {
        put("upload_id", uploadId);
    }

    public void setUploadSize(long uploadSize) {
        json.put("upload_size", uploadSize);
    }

    /**
     * Set the query params from the url request to json.
     *
     * @param params The query parameters.
     */
    public void setQueryParams(List<NameValuePair> params) {
        if (params != null) {
            ObjectNode paramNode = MAPPER.createObjectNode();
            for (NameValuePair pair : params) {
                if (ALLOW_LIST.contains(pair.getName())) {
                    paramNode.put(pair.getName(), pair.getValue());
                }
            }
            json.set("query_params", paramNode);
        }
    }

    private void put(final String field, final String value) {
        if (isNotBlank(value)) {
            json.put(field, value);
        }
    }
}
