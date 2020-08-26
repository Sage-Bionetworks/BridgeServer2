package org.sagebionetworks.bridge.spring.util;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.DefaultObjectMapper;

import javax.servlet.http.Cookie;

@SuppressWarnings("ConstantConditions")
public class HttpUtilTest {
    @Test
    public void convertErrorToJsonResponse() throws Exception {
        String message = "This is a message.";
        String type = "TestException";
        ResponseEntity<String> response = HttpUtil.convertErrorToJsonResponse(HttpStatus.CREATED, type, message);
        assertErrorResponse(response, HttpStatus.CREATED, type, message);
    }

    @Test
    public void makeSessionCookie() {
        String sessionToken = "test_only_token";
        Cookie testCookie = HttpUtil.makeSessionCookie(sessionToken, 25);
        assertNotNull(testCookie);
        assertEquals(25, testCookie.getMaxAge());
        assertEquals("/", testCookie.getPath());
        assertEquals("localhost", testCookie.getDomain());
        assertFalse(testCookie.isHttpOnly());
        assertFalse(testCookie.getSecure());
    }

    public static void assertErrorResponse(ResponseEntity<String> actualResponse, HttpStatus expectedStatus,
                                           String expectedType, String expectedMessage) throws IOException {
        assertEquals(actualResponse.getStatusCode(), expectedStatus);
        assertEquals(actualResponse.getHeaders().get(HttpUtil.CONTENT_TYPE_HEADER).get(0), HttpUtil.CONTENT_TYPE_JSON);

        JsonNode responseNode = DefaultObjectMapper.INSTANCE.readTree(actualResponse.getBody());
        assertEquals(responseNode.get(HttpUtil.KEY_STATUS_CODE).intValue(), expectedStatus.value());
        assertEquals(responseNode.get(HttpUtil.KEY_TYPE).textValue(), expectedType);
        assertEquals(responseNode.get(HttpUtil.KEY_MESSAGE).textValue(), expectedMessage);
    }
}
