package org.sagebionetworks.bridge.spring.handlers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.spring.util.HttpUtilTest;

public class BridgeExceptionHandlerTest {
    @Test
    public void test() throws Exception {
        String message = "dummy error message";
        Exception ex = new IllegalArgumentException(message);
        ResponseEntity<String> response = new BridgeExceptionHandler().handleException(ex);
        HttpUtilTest.assertErrorResponse(response, HttpStatus.INTERNAL_SERVER_ERROR,
                "IllegalArgumentException", message);
    }
}
