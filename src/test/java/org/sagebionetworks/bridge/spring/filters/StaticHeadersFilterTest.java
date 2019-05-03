package org.sagebionetworks.bridge.spring.filters;

import java.util.Map;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class StaticHeadersFilterTest extends Mockito {
    
    @Mock
    private HttpServletRequest mockRequest;
    
    @Mock
    private HttpServletResponse mockResponse;
    
    @Mock
    private FilterChain mockFilterChain;
    
    @InjectMocks
    private StaticHeadersFilter filter = new StaticHeadersFilter();

    @BeforeMethod
    private void before() {
        MockitoAnnotations.initMocks(this);
    }
    
    @Test
    public void setsAllTheHeaders() throws Exception {
        filter.doFilter(mockRequest, mockResponse, mockFilterChain);
        for (Map.Entry<String, String> entry : StaticHeadersFilter.HEADERS.entrySet()) {
            verify(mockResponse).setHeader(entry.getKey(), entry.getValue());    
        }
        verify(mockFilterChain).doFilter(mockRequest, mockResponse);
    }
}
