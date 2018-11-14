package org.sagebionetworks.bridge.spring.controllers;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.Vector;
import javax.servlet.http.HttpServletRequest;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.entity.ContentType;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.mockito.ArgumentCaptor;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.testng.PowerMockTestCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.config.Config;

@PrepareForTest({ EntityUtils.class, Request.class })
public class PassthroughControllerTest extends PowerMockTestCase {
    private static final String BRIDGE_PF_HOST = "http://example.com";
    private static final String DUMMY_BODY = "dummy body";
    private static final String MIME_TYPE_TEXT_PLAIN = "text/plain";
    private static final String MIME_TYPE_TEXT_PLAIN_WITH_CHARSET = "text/plain;charset=utf-8";
    private static final String QUERY_PARAM_STRING = "key1=value1&key2=value2";
    private static final String REQUEST_ID = "test-request";
    private static final String OTHER_REQUEST_ID = "other-request";
    private static final String URL = "/v3/dummy/api";

    private static final String EXPECTED_FULL_URL = "http://example.com/v3/dummy/api";
    private static final String EXPECTED_FULL_URL_WITH_QUERY_PARAMS =
            "http://example.com/v3/dummy/api?key1=value1&key2=value2";
    private static final Map<String, String> EXPECTED_HEADER_MAP_WITH_REQUEST_ID =
            ImmutableMap.<String, String>builder().put(PassthroughController.HEADER_REQUEST_ID, REQUEST_ID).build();

    private PassthroughController controller;

    @BeforeMethod
    public void beforeClass() {
        // Mock config.
        Config mockConfig = mock(Config.class);
        when(mockConfig.get(PassthroughController.CONFIG_KEY_BRIDGE_PF_HOST)).thenReturn(BRIDGE_PF_HOST);

        // Set up controller.
        controller = spy(new PassthroughController());
        controller.setConfig(mockConfig);

        // Spy randomGuid() to make it easier to test.
        doReturn(REQUEST_ID).when(controller).randomGuid();
    }

    @Test
    public void getWithQueryParams() throws Exception {
        // Make request.
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getHeaderNames()).thenReturn(new Vector<String>().elements());
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getQueryString()).thenReturn(QUERY_PARAM_STRING);
        when(mockRequest.getRequestURI()).thenReturn(URL);

        // Mock HTTP client.
        Request mockPfRequest = mock(Request.class);
        mockHttpResponseForRequest(mockPfRequest, 200, null, null);

        mockStatic(Request.class);
        when(Request.Get(anyString())).thenReturn(mockPfRequest);

        // Execute.
        ResponseEntity<String> response = controller.handleDefault(mockRequest, null);
        assertResponseEntity(response, HttpStatus.OK, null, null);

        // Verify request.
        verifyStatic(Request.class);
        Request.Get(EXPECTED_FULL_URL_WITH_QUERY_PARAMS);
        assertRequest(mockPfRequest, EXPECTED_HEADER_MAP_WITH_REQUEST_ID, null, null,
                null);
    }

    @Test
    public void postWithBody() throws Exception {
        // Make request.
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getContentType()).thenReturn(MIME_TYPE_TEXT_PLAIN);
        when(mockRequest.getHeaderNames()).thenReturn(new Vector<String>().elements());
        when(mockRequest.getMethod()).thenReturn("POST");
        when(mockRequest.getRequestURI()).thenReturn(URL);

        // Mock HTTP client.
        Request mockPfRequest = mock(Request.class);
        mockHttpResponseForRequest(mockPfRequest, 201, null, null);

        mockStatic(Request.class);
        when(Request.Post(anyString())).thenReturn(mockPfRequest);

        // Execute.
        ResponseEntity<String> response = controller.handleDefault(mockRequest, DUMMY_BODY);
        assertResponseEntity(response, HttpStatus.CREATED, null, null);

        // Verify request.
        verifyStatic(Request.class);
        Request.Post(EXPECTED_FULL_URL);
        assertRequest(mockPfRequest, EXPECTED_HEADER_MAP_WITH_REQUEST_ID, DUMMY_BODY, MIME_TYPE_TEXT_PLAIN,
                null);
    }

    @Test
    public void bodyContentTypeHasCharset() throws Exception {
        // Make request.
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getContentType()).thenReturn(MIME_TYPE_TEXT_PLAIN_WITH_CHARSET);
        when(mockRequest.getHeaderNames()).thenReturn(new Vector<String>().elements());
        when(mockRequest.getMethod()).thenReturn("POST");
        when(mockRequest.getRequestURI()).thenReturn(URL);

        // Mock HTTP client.
        Request mockPfRequest = mock(Request.class);
        mockHttpResponseForRequest(mockPfRequest, 201, null, null);

        mockStatic(Request.class);
        when(Request.Post(anyString())).thenReturn(mockPfRequest);

        // Execute.
        ResponseEntity<String> response = controller.handleDefault(mockRequest, DUMMY_BODY);
        assertResponseEntity(response, HttpStatus.CREATED, null, null);

        // Verify request.
        verifyStatic(Request.class);
        Request.Post(EXPECTED_FULL_URL);
        assertRequest(mockPfRequest, EXPECTED_HEADER_MAP_WITH_REQUEST_ID, DUMMY_BODY, MIME_TYPE_TEXT_PLAIN,
                Charsets.UTF_8);
    }

    @Test
    public void delete() throws Exception {
        // Make request.
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getHeaderNames()).thenReturn(new Vector<String>().elements());
        when(mockRequest.getMethod()).thenReturn("DELETE");
        when(mockRequest.getRequestURI()).thenReturn(URL);

        // Mock HTTP client.
        Request mockPfRequest = mock(Request.class);
        mockHttpResponseForRequest(mockPfRequest, 200, null, null);

        mockStatic(Request.class);
        when(Request.Delete(anyString())).thenReturn(mockPfRequest);

        // Execute.
        ResponseEntity<String> response = controller.handleDefault(mockRequest, null);
        assertResponseEntity(response, HttpStatus.OK, null, null);

        // Verify request.
        verifyStatic(Request.class);
        Request.Delete(EXPECTED_FULL_URL);
        assertRequest(mockPfRequest, EXPECTED_HEADER_MAP_WITH_REQUEST_ID, null, null,
                null);
    }

    // branch coverage
    @Test
    public void unsupportedMethod() throws Exception {
        // Make request.
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getHeaderNames()).thenReturn(new Vector<String>().elements());
        when(mockRequest.getMethod()).thenReturn("PUT");
        when(mockRequest.getRequestURI()).thenReturn(URL);

        // Execute.
        ResponseEntity<String> response = controller.handleDefault(mockRequest, null);
        assertEquals(response.getStatusCode(), HttpStatus.BAD_REQUEST);
    }

    // branch coverage
    @Test
    public void unrecognizedStatusCode() throws Exception {
        // Make request.
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getHeaderNames()).thenReturn(new Vector<String>().elements());
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getRequestURI()).thenReturn(URL);

        // Mock HTTP client. Spring MVC doesn't parse status code 499 to anything.
        Request mockPfRequest = mock(Request.class);
        mockHttpResponseForRequest(mockPfRequest, 499, null, null);

        mockStatic(Request.class);
        when(Request.Get(anyString())).thenReturn(mockPfRequest);

        // Execute.
        ResponseEntity<String> response = controller.handleDefault(mockRequest, null);
        assertEquals(response.getStatusCode(), HttpStatus.NOT_IMPLEMENTED);
    }

    @Test
    public void requestWithHeaders() throws Exception {
        // Make request.
        Map<String, String> requestHeaderMap = ImmutableMap.<String, String>builder()
                .put("Dummy-Header", "dummy header value")
                .put("Content-Length", "10")
                .put(PassthroughController.HEADER_REQUEST_ID, OTHER_REQUEST_ID)
                .build();

        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getContentType()).thenReturn(MIME_TYPE_TEXT_PLAIN);
        when(mockRequest.getHeaderNames()).thenReturn(new Vector<>(requestHeaderMap.keySet()).elements());
        when(mockRequest.getMethod()).thenReturn("POST");
        when(mockRequest.getRequestURI()).thenReturn(URL);

        when(mockRequest.getHeader(any())).thenAnswer(invocation -> {
            String headerName = invocation.getArgumentAt(0, String.class);
            return requestHeaderMap.get(headerName);
        });

        // Mock HTTP client.
        Request mockPfRequest = mock(Request.class);
        mockHttpResponseForRequest(mockPfRequest, 201, null, null);

        mockStatic(Request.class);
        when(Request.Post(anyString())).thenReturn(mockPfRequest);

        // Execute.
        ResponseEntity<String> response = controller.handleDefault(mockRequest, DUMMY_BODY);
        assertResponseEntity(response, HttpStatus.CREATED, null, null);

        // Verify request. Note that we filter out the Content-Length.
        Map<String, String> expectedHeaderMap = ImmutableMap.<String, String>builder()
                .put("Dummy-Header", "dummy header value")
                .put(PassthroughController.HEADER_REQUEST_ID, OTHER_REQUEST_ID)
                .build();

        verifyStatic(Request.class);
        Request.Post(EXPECTED_FULL_URL);
        assertRequest(mockPfRequest, expectedHeaderMap, DUMMY_BODY, MIME_TYPE_TEXT_PLAIN, null);
    }

    @Test
    public void responseWithHeadersAndBody() throws Exception {
        // Make request.
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getHeaderNames()).thenReturn(new Vector<String>().elements());
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getRequestURI()).thenReturn(URL);

        // Mock HTTP client.
        String expectedResponseBody = "expected response body";
        Map<String, String> expectedResponseHeaderMap = ImmutableMap.<String, String>builder()
                .put("Content-Type", MIME_TYPE_TEXT_PLAIN)
                .put("Content-Length", "22")
                .put("Dummy-Response-Header", "dummy response header value")
                .build();

        Request mockPfRequest = mock(Request.class);
        mockHttpResponseForRequest(mockPfRequest, 200, expectedResponseHeaderMap, expectedResponseBody);

        mockStatic(Request.class);
        when(Request.Get(anyString())).thenReturn(mockPfRequest);

        // Execute.
        ResponseEntity<String> response = controller.handleDefault(mockRequest, null);
        assertResponseEntity(response, HttpStatus.OK, expectedResponseHeaderMap, expectedResponseBody);

        // Verify request.
        verifyStatic(Request.class);
        Request.Get(EXPECTED_FULL_URL);
        assertRequest(mockPfRequest, EXPECTED_HEADER_MAP_WITH_REQUEST_ID, null, null,
                null);
    }

    private static void mockHttpResponseForRequest(Request mockPfRequest, int statusCode,
            Map<String, String> headerMap, String body) throws Exception {
        HttpResponse mockPfHttpResponse = mock(HttpResponse.class);

        // Status code.
        StatusLine mockStatusLine = mock(StatusLine.class);
        when(mockStatusLine.getStatusCode()).thenReturn(statusCode);
        when(mockPfHttpResponse.getStatusLine()).thenReturn(mockStatusLine);

        // Headers.
        Header[] headerArray;
        if (headerMap != null && !headerMap.isEmpty()) {
            headerArray = new Header[headerMap.size()];
            int i = 0;
            for (Map.Entry<String, String> header : headerMap.entrySet()) {
                headerArray[i] = new BasicHeader(header.getKey(), header.getValue());
                i++;
            }
        } else {
            headerArray = new Header[0];
        }

        when(mockPfHttpResponse.getAllHeaders()).thenReturn(headerArray);

        // Body.
        HttpEntity mockEntity = mock(HttpEntity.class);
        when(mockPfHttpResponse.getEntity()).thenReturn(mockEntity);

        mockStatic(EntityUtils.class);
        when(EntityUtils.toString(mockEntity, Charsets.UTF_8)).thenReturn(body);

        // Put response into mock request.
        Response mockPfResponse = mock(Response.class);
        when(mockPfResponse.returnResponse()).thenReturn(mockPfHttpResponse);

        when(mockPfRequest.execute()).thenReturn(mockPfResponse);
    }

    private static void assertRequest(Request request, Map<String, String> expectedHeaderMap, String expectedBody,
            String expectedContentType, Charset expectedCharset) {
        // Assert headers.
        if (expectedHeaderMap != null && !expectedHeaderMap.isEmpty()) {
            for (Map.Entry<String, String> header : expectedHeaderMap.entrySet()) {
                verify(request).addHeader(header.getKey(), header.getValue());
            }
        } else {
            verify(request, never()).addHeader(any(), any());
        }

        // Assert body.
        if (expectedBody != null && expectedContentType != null) {
            ArgumentCaptor<ContentType> contentTypeCaptor = ArgumentCaptor.forClass(ContentType.class);
            verify(request).bodyString(eq(expectedBody), contentTypeCaptor.capture());

            ContentType contentType = contentTypeCaptor.getValue();
            assertEquals(contentType.getMimeType(), expectedContentType);
            assertEquals(contentType.getCharset(), expectedCharset);
        } else {
            verify(request, never()).bodyString(any(), any());
        }
    }

    private static void assertResponseEntity(ResponseEntity<String> responseEntity, HttpStatus expectedStatus,
            Map<String, String> expectedHeaderMap, String expectedBody) {
        assertEquals(responseEntity.getStatusCode(), expectedStatus);
        assertEquals(responseEntity.getBody(), expectedBody);

        if (expectedHeaderMap != null && !expectedHeaderMap.isEmpty()) {
            assertEquals(responseEntity.getHeaders().toSingleValueMap(), expectedHeaderMap);
        } else {
            assertTrue(responseEntity.getHeaders().isEmpty());
        }
    }
}
