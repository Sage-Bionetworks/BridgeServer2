package org.sagebionetworks.bridge.spring.controllers;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;
import static org.sagebionetworks.bridge.BridgeConstants.BRIDGE_API_STATUS_HEADER;
import static org.sagebionetworks.bridge.BridgeConstants.SESSION_TOKEN_HEADER;
import static org.sagebionetworks.bridge.BridgeConstants.X_FORWARDED_FOR_HEADER;
import static org.sagebionetworks.bridge.BridgeConstants.X_REQUEST_ID_HEADER;
import static org.sagebionetworks.bridge.spring.controllers.PassthroughController.CONFIG_KEY_BRIDGE_PF_HOST;
import static org.sagebionetworks.bridge.spring.filters.MetricsFilter.X_PASSTHROUGH;
import static org.springframework.http.HttpHeaders.ACCEPT_LANGUAGE;
import static org.springframework.http.HttpHeaders.CONTENT_LENGTH;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpHeaders.COOKIE;
import static org.springframework.http.HttpHeaders.SET_COOKIE;
import static org.springframework.http.HttpHeaders.USER_AGENT;
import static org.testng.Assert.assertEquals;

import java.nio.charset.Charset;
import java.util.Map;
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
import org.sagebionetworks.bridge.spring.filters.MetricsFilter;
import org.sagebionetworks.bridge.spring.util.HttpUtilTest;

@PrepareForTest({ Request.class, EntityUtils.class })
public class PassthroughControllerTest extends PowerMockTestCase {
    private static final String BRIDGE_PF_HOST = "http://example.com";
    private static final String DUMMY_BODY = "dummy body";
    private static final String IP_ADDRESS = "my-ip-address";
    private static final String OTHER_IP_ADDRESS = "other-ip-address";
    private static final String MIME_TYPE_TEXT_PLAIN = "text/plain";
    private static final String MIME_TYPE_TEXT_PLAIN_WITH_CHARSET = "text/plain;charset=utf-8";
    private static final String QUERY_PARAM_STRING = "key1=value1&key2=value2";
    private static final String REQUEST_ID = "test-request";
    private static final String OTHER_REQUEST_ID = "other-request";
    private static final String URL = "/v3/dummy/api";

    private static final String EXPECTED_FULL_URL = "http://example.com/v3/dummy/api";
    private static final String EXPECTED_FULL_URL_WITH_QUERY_PARAMS =
            "http://example.com/v3/dummy/api?key1=value1&key2=value2";
    private static final Map<String, String> EXPECTED_DEFAULT_HEADER_MAP = ImmutableMap.<String, String>builder()
            .put(X_FORWARDED_FOR_HEADER, IP_ADDRESS)
            .put(X_REQUEST_ID_HEADER, REQUEST_ID)
            .build();

    private PassthroughController controller;
    
    @BeforeMethod
    public void beforeMethod() {
        // Mock config.
        Config mockConfig = mock(Config.class);
        when(mockConfig.get(CONFIG_KEY_BRIDGE_PF_HOST)).thenReturn(BRIDGE_PF_HOST);
        
        // Set up controller.
        controller = spy(new PassthroughController());
        controller.setConfig(mockConfig);
    }

    @Test
    public void getWithQueryParams() throws Exception {
        // Make request.
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getQueryString()).thenReturn(QUERY_PARAM_STRING);
        when(mockRequest.getRemoteAddr()).thenReturn(IP_ADDRESS);
        when(mockRequest.getRequestURI()).thenReturn(URL);
        when(mockRequest.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);

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
        assertRequest(mockPfRequest, EXPECTED_DEFAULT_HEADER_MAP, null, null,
                null);
    }

    @Test
    public void postWithBody() throws Exception {
        // Make request.
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getContentType()).thenReturn(MIME_TYPE_TEXT_PLAIN);
        when(mockRequest.getMethod()).thenReturn("POST");
        when(mockRequest.getRemoteAddr()).thenReturn(IP_ADDRESS);
        when(mockRequest.getRequestURI()).thenReturn(URL);
        when(mockRequest.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);

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
        assertRequest(mockPfRequest, EXPECTED_DEFAULT_HEADER_MAP, DUMMY_BODY, MIME_TYPE_TEXT_PLAIN,
                null);
    }

    @Test
    public void bodyContentTypeHasCharset() throws Exception {
        // Make request.
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getContentType()).thenReturn(MIME_TYPE_TEXT_PLAIN_WITH_CHARSET);
        when(mockRequest.getMethod()).thenReturn("POST");
        when(mockRequest.getRemoteAddr()).thenReturn(IP_ADDRESS);
        when(mockRequest.getRequestURI()).thenReturn(URL);
        when(mockRequest.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);

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
        assertRequest(mockPfRequest, EXPECTED_DEFAULT_HEADER_MAP, DUMMY_BODY, MIME_TYPE_TEXT_PLAIN,
                Charsets.UTF_8);
    }

    @Test
    public void delete() throws Exception {
        // Make request.
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getMethod()).thenReturn("DELETE");
        when(mockRequest.getRemoteAddr()).thenReturn(IP_ADDRESS);
        when(mockRequest.getRequestURI()).thenReturn(URL);
        when(mockRequest.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);

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
        assertRequest(mockPfRequest, EXPECTED_DEFAULT_HEADER_MAP, null, null,
                null);
    }
    
    @Test
    public void headRequest() throws Exception {
        // Make request.
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getMethod()).thenReturn("HEAD");
        when(mockRequest.getRemoteAddr()).thenReturn(IP_ADDRESS);
        when(mockRequest.getRequestURI()).thenReturn(URL);
        when(mockRequest.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);

        // Mock HTTP client.
        Request mockPfRequest = mock(Request.class);
        mockHttpResponseForRequest(mockPfRequest, 200, null, null);

        mockStatic(Request.class);
        when(Request.Head(anyString())).thenReturn(mockPfRequest);

        // Execute.
        ResponseEntity<String> response = controller.handleDefault(mockRequest, null);
        assertResponseEntity(response, HttpStatus.OK, null, null);

        // Verify request.
        verifyStatic(Request.class);
        Request.Head(EXPECTED_FULL_URL);
        assertRequest(mockPfRequest, EXPECTED_DEFAULT_HEADER_MAP, null, null, null);
    }

    // branch coverage
    @Test
    public void unsupportedMethod() throws Exception {
        // Make request.
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getMethod()).thenReturn("PUT");
        when(mockRequest.getRequestURI()).thenReturn(URL);
        when(mockRequest.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);

        // Execute.
        ResponseEntity<String> response = controller.handleDefault(mockRequest, null);
        HttpUtilTest.assertErrorResponse(response, HttpStatus.BAD_REQUEST, PassthroughController.BAD_REQUEST_EXCEPTION,
                "Method PUT not supported");
    }

    // branch coverage
    @Test
    public void unrecognizedStatusCode() throws Exception {
        // Make request.
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getRequestURI()).thenReturn(URL);
        when(mockRequest.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);

        // Mock HTTP client. Spring MVC doesn't parse status code 499 to anything.
        Request mockPfRequest = mock(Request.class);
        mockHttpResponseForRequest(mockPfRequest, 499, null, null);

        mockStatic(Request.class);
        when(Request.Get(anyString())).thenReturn(mockPfRequest);

        // Execute.
        ResponseEntity<String> response = controller.handleDefault(mockRequest, null);
        HttpUtilTest.assertErrorResponse(response, HttpStatus.NOT_IMPLEMENTED,
                PassthroughController.NOT_IMPLEMENTED_EXCEPTION, "Unrecognized status code 499");
    }

    @Test
    public void requestWithHeaders() throws Exception {
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getContentType()).thenReturn(MIME_TYPE_TEXT_PLAIN);
        when(mockRequest.getHeader("Dummy-Header")).thenReturn("dummy header value");
        when(mockRequest.getHeader("Content-Length")).thenReturn("10");
        when(mockRequest.getHeader(X_FORWARDED_FOR_HEADER)).thenReturn(OTHER_IP_ADDRESS);
        when(mockRequest.getHeader(X_REQUEST_ID_HEADER)).thenReturn(OTHER_REQUEST_ID);
        when(mockRequest.getHeader(SESSION_TOKEN_HEADER)).thenReturn("sessionToken");
        when(mockRequest.getHeader(ACCEPT_LANGUAGE)).thenReturn("en");
        when(mockRequest.getHeader(USER_AGENT)).thenReturn("app/1");
        when(mockRequest.getHeader(COOKIE)).thenReturn("one cookie");
        when(mockRequest.getMethod()).thenReturn("POST");
        when(mockRequest.getRemoteAddr()).thenReturn(IP_ADDRESS);
        when(mockRequest.getRequestURI()).thenReturn(URL);

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
                .put(X_REQUEST_ID_HEADER, OTHER_REQUEST_ID)
                .put(X_FORWARDED_FOR_HEADER, OTHER_IP_ADDRESS)
                .put(SESSION_TOKEN_HEADER, "sessionToken")
                .put(ACCEPT_LANGUAGE, "en")
                .put(USER_AGENT, "app/1")
                .put(COOKIE, "one cookie")
                .build();

        verifyStatic(Request.class);
        Request.Post(EXPECTED_FULL_URL);
        assertRequest(mockPfRequest, expectedHeaderMap, DUMMY_BODY, MIME_TYPE_TEXT_PLAIN, null);
    }

    @Test
    public void responseWithHeadersAndBody() throws Exception {
        // Make request.
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getRemoteAddr()).thenReturn(IP_ADDRESS);
        when(mockRequest.getRequestURI()).thenReturn(URL);
        when(mockRequest.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);
        
        // Mock HTTP client.
        String expectedResponseBody = "expected response body";
        Map<String, String> responseHeaderMap = ImmutableMap.<String, String>builder()
                .put(CONTENT_TYPE, MIME_TYPE_TEXT_PLAIN)
                .put(CONTENT_LENGTH, "22")
                .put("Dummy-Response-Header", "dummy response header value")
                .put(SESSION_TOKEN_HEADER, "oneSession")
                .put(BRIDGE_API_STATUS_HEADER, "warning")
                .put(SET_COOKIE, "this is not in cookie format")
                .build();
        
        Request mockPfRequest = mock(Request.class);
        mockHttpResponseForRequest(mockPfRequest, 200, responseHeaderMap, expectedResponseBody);

        mockStatic(Request.class);
        when(Request.Get(anyString())).thenReturn(mockPfRequest);

        // Here's what we should copy back from the Bridge response. We do not include every
        // header.
        Map<String, String> expectedResponseHeaderMap = ImmutableMap.<String, String>builder()
                .put(CONTENT_TYPE, MIME_TYPE_TEXT_PLAIN)
                .put(CONTENT_LENGTH, "22")
                .put(SESSION_TOKEN_HEADER, "oneSession")
                .put(BRIDGE_API_STATUS_HEADER, "warning")
                .put(SET_COOKIE, "this is not in cookie format")
                .put(X_PASSTHROUGH, "BridgePF")
                .build();
        
        // Execute.
        ResponseEntity<String> response = controller.handleDefault(mockRequest, null);
        assertResponseEntity(response, HttpStatus.OK, expectedResponseHeaderMap, expectedResponseBody);

        // Verify request.
        verifyStatic(Request.class);
        Request.Get(EXPECTED_FULL_URL);
        assertRequest(mockPfRequest, EXPECTED_DEFAULT_HEADER_MAP, null, null,
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
                BasicHeader oneHeader = new BasicHeader(header.getKey(), header.getValue());
                headerArray[i] = oneHeader;
                when(mockPfHttpResponse.getFirstHeader(header.getKey())).thenReturn(oneHeader);
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
            // The only thing that is present is the header indicating this request has been passed 
            // through (so we don't log it twice)
            assertEquals(responseEntity.getHeaders().size(), 1);
            assertEquals(responseEntity.getHeaders().get(MetricsFilter.X_PASSTHROUGH).get(0), "BridgePF");
        }
    }
}
