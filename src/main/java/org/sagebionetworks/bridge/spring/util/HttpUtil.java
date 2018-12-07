package org.sagebionetworks.bridge.spring.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.sagebionetworks.bridge.json.DefaultObjectMapper;

/** Utilities for Spring HTTP. */
public class HttpUtil {
    public static final String CONTENT_TYPE_HEADER = "Content-Type";
    public static final String CONTENT_TYPE_JSON = "application/json; charset=utf-8";

    static final String KEY_MESSAGE = "message";
    static final String KEY_STATUS_CODE = "statusCode";
    static final String KEY_TYPE = "type";

    /**
     * Makes a JSON response entity with the given code, error type, and message. Note that this returns a
     * ResponseEntity<String> and not ResponseEntity<JsonNode> because some of our APIs expect a string.
     */
    public static ResponseEntity<String> convertErrorToJsonResponse(HttpStatus status, String type, String message)
            throws JsonProcessingException {
        // Make JSON.
        ObjectNode responseNode = DefaultObjectMapper.INSTANCE.createObjectNode();
        responseNode.put(KEY_STATUS_CODE, status.value());
        responseNode.put(KEY_MESSAGE, message);
        responseNode.put(KEY_TYPE, type);

        // Convert to pretty-printed string.
        String responseString = DefaultObjectMapper.INSTANCE.writerWithDefaultPrettyPrinter().writeValueAsString(
                responseNode);

        // Make content-type header.
        HttpHeaders headers = new HttpHeaders();
        headers.add(CONTENT_TYPE_HEADER, CONTENT_TYPE_JSON);

        return new ResponseEntity<>(responseString, headers, status);
    }
}
