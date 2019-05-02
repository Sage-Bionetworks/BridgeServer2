package org.sagebionetworks.bridge.spring.filters;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.RequestContext;

public class RequestFilterTest extends Mockito {

    @Mock
    private HttpServletRequest mockRequest;
    
    @Mock
    private HttpServletResponse mockResponse;
    
    @Mock
    private FilterChain mockFilterChain;
    
    @Captor
    private ArgumentCaptor<HttpServletRequest> requestCaptor;
    
    @Captor
    private ArgumentCaptor<RequestContext> contextCaptor;
    
    @InjectMocks
    @Spy
    private RequestFilter filter = new RequestFilter();

    @BeforeMethod
    private void before() {
        MockitoAnnotations.initMocks(this);
    }
    
    @Test
    public void testWithXForwardForHeader() throws Exception {
        when(mockRequest.getHeader(BridgeConstants.X_REQUEST_ID_HEADER)).thenReturn("ABCXZ");
        when(mockRequest.getHeaderNames()).thenReturn(new Vector<String>().elements());
        
        filter.doFilter(mockRequest, mockResponse, mockFilterChain);
        
        verify(mockFilterChain).doFilter(requestCaptor.capture(), eq(mockResponse));
        
        HttpServletRequest wrapper = requestCaptor.getValue();
        assertEquals("ABCXZ", wrapper.getHeader(BridgeConstants.X_REQUEST_ID_HEADER));
        
        boolean foundRequestId = false;
        Enumeration<String> enumeration = wrapper.getHeaderNames();
        while(enumeration.hasMoreElements()) {
            String headerName = enumeration.nextElement();
            if (headerName.equals(BridgeConstants.X_REQUEST_ID_HEADER)) {
                foundRequestId = true;
            }
        }
        assertTrue(foundRequestId);
        
        verify(filter, times(2)).setRequestContext(contextCaptor.capture());
        
        RequestContext context = contextCaptor.getAllValues().get(0);
        assertEquals("ABCXZ", context.getId());
        
        assertNull(contextCaptor.getAllValues().get(1));
    }
    
    @Test
    public void testWithoutXForwardForHeader() throws Exception {
        when(mockRequest.getHeaderNames()).thenReturn(new Vector<String>().elements());
        when(filter.generateRequestId()).thenReturn("AAABAAA");
        
        filter.doFilter(mockRequest, mockResponse, mockFilterChain);
        
        verify(mockFilterChain).doFilter(requestCaptor.capture(), eq(mockResponse));
        
        HttpServletRequest wrapper = requestCaptor.getValue();
        assertEquals("AAABAAA", wrapper.getHeader(BridgeConstants.X_REQUEST_ID_HEADER));
        
        boolean foundRequestId = false;
        Enumeration<String> enumeration = wrapper.getHeaderNames();
        while(enumeration.hasMoreElements()) {
            String headerName = enumeration.nextElement();
            if (headerName.equals(BridgeConstants.X_REQUEST_ID_HEADER)) {
                foundRequestId = true;
            }
        }
        assertTrue(foundRequestId);
        
        verify(filter, times(2)).setRequestContext(contextCaptor.capture());
        
        RequestContext context = contextCaptor.getAllValues().get(0);
        assertEquals("AAABAAA", context.getId());
        
        assertNull(contextCaptor.getAllValues().get(1));
    }

    @Test
    public void headerNamesNormalized() throws Exception {
        Set<String> headerNames = new HashSet<>();
        headerNames.add("lowercase-header");
        headerNames.add("Sentencecase-header");
        headerNames.add("ALL-CAPS-HEADER");
        headerNames.add("Ideal-Header");
        when(mockRequest.getHeaderNames()).thenReturn(new Vector<String>(headerNames).elements());
        when(filter.generateRequestId()).thenReturn("AAABAAA");
        
        filter.doFilter(mockRequest, mockResponse, mockFilterChain);
        
        verify(mockFilterChain).doFilter(requestCaptor.capture(), eq(mockResponse));
        
        HttpServletRequest wrapper = requestCaptor.getValue();
        Enumeration<String> enumeration = wrapper.getHeaderNames();
        
        Set<String> normalizedHeaderNames = new HashSet<>(Collections.list(enumeration));
        assertTrue(normalizedHeaderNames.contains("Lowercase-Header"));
        assertTrue(normalizedHeaderNames.contains("Sentencecase-Header"));
        assertTrue(normalizedHeaderNames.contains("All-Caps-Header"));
        assertTrue(normalizedHeaderNames.contains("Ideal-Header"));
        assertTrue(normalizedHeaderNames.contains(BridgeConstants.X_REQUEST_ID_HEADER));
    }

}
