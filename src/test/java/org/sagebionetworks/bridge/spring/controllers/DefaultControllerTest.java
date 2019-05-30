package org.sagebionetworks.bridge.spring.controllers;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.exceptions.EndpointNotFoundException;

// Trivial controller with trivial tests for branch coverage.
public class DefaultControllerTest {
    @Test(expectedExceptions = EndpointNotFoundException.class)
    public void test() {
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getRequestURI()).thenReturn("/nonexistent");

        DefaultController controller = new DefaultController();
        controller.handle(mockRequest);
    }
}
