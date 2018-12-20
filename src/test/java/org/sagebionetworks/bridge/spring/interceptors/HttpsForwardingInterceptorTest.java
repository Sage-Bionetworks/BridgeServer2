package org.sagebionetworks.bridge.spring.interceptors;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class HttpsForwardingInterceptorTest {
    private static final Object DUMMY_HANDLER = new Object();

    private static final String SERVER = "example.com";
    private static final String URL = "/foo";
    private static final String EXPECTED_HTTPS_FULL_URL = "https://example.com/foo";

    private HttpsForwardingInterceptor interceptor;
    private HttpServletRequest mockRequest;
    private HttpServletResponse mockResponse;

    @BeforeMethod
    public void setup() {
        mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getServerName()).thenReturn(SERVER);
        when(mockRequest.getRequestURI()).thenReturn(URL);

        mockResponse = mock(HttpServletResponse.class);

        interceptor = new HttpsForwardingInterceptor();
    }

    @Test
    public void httpRedirect() {
        // Mock request protocol header. Use all caps to verify that this is case-insensitive.
        when(mockRequest.getHeader(HttpsForwardingInterceptor.HEADER_X_FORWARDED_PROTO)).thenReturn("HTTP");

        // Execute.
        boolean result = interceptor.preHandle(mockRequest, mockResponse, DUMMY_HANDLER);
        assertFalse(result);

        // Verify response redirect.
        verify(mockResponse).setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
        verify(mockResponse).setHeader(HttpsForwardingInterceptor.HEADER_LOCATION, EXPECTED_HTTPS_FULL_URL);
    }

    @Test
    public void httpsNoRedirect() {
        // Mock request protocol header. Use all caps to verify that this is case-insensitive.
        when(mockRequest.getHeader(HttpsForwardingInterceptor.HEADER_X_FORWARDED_PROTO)).thenReturn("HTTPS");

        // Execute.
        boolean result = interceptor.preHandle(mockRequest, mockResponse, DUMMY_HANDLER);
        assertTrue(result);

        // Verify no response redirect.
        verifyZeroInteractions(mockResponse);
    }

    @Test
    public void noHeaderNoRedirect() {
        // Mock request protocol header.
        when(mockRequest.getHeader(HttpsForwardingInterceptor.HEADER_X_FORWARDED_PROTO)).thenReturn(null);

        // Execute.
        boolean result = interceptor.preHandle(mockRequest, mockResponse, DUMMY_HANDLER);
        assertTrue(result);

        // Verify no response redirect.
        verifyZeroInteractions(mockResponse);
    }
}
