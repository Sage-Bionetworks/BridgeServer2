package org.sagebionetworks.bridge.spring.filters;

import static org.sagebionetworks.bridge.BridgeConstants.BRIDGE_API_STATUS_HEADER;
import static org.sagebionetworks.bridge.BridgeConstants.WARN_NO_ACCEPT_LANGUAGE;
import static org.sagebionetworks.bridge.BridgeConstants.WARN_NO_USER_AGENT;
import static org.sagebionetworks.bridge.BridgeConstants.X_FORWARDED_FOR_HEADER;
import static org.sagebionetworks.bridge.TestConstants.IP_ADDRESS;
import static org.sagebionetworks.bridge.TestConstants.UA;
import static org.springframework.http.HttpHeaders.ACCEPT_LANGUAGE;
import static org.springframework.http.HttpHeaders.USER_AGENT;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.ImmutableList;

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
import org.sagebionetworks.bridge.models.ClientInfo;

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
        assertEquals(wrapper.getHeader(BridgeConstants.X_REQUEST_ID_HEADER), "ABCXZ");
        
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
        assertEquals(context.getId(), "ABCXZ");
        
        assertNull(contextCaptor.getAllValues().get(1));
    }
    
    @Test
    public void testWithoutXForwardForHeader() throws Exception {
        when(mockRequest.getHeaderNames()).thenReturn(new Vector<String>().elements());
        when(filter.generateRequestId()).thenReturn("AAABAAA");
        
        filter.doFilter(mockRequest, mockResponse, mockFilterChain);
        
        verify(mockFilterChain).doFilter(requestCaptor.capture(), eq(mockResponse));
        
        HttpServletRequest wrapper = requestCaptor.getValue();
        assertEquals(wrapper.getHeader(BridgeConstants.X_REQUEST_ID_HEADER), "AAABAAA");
        
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
        assertEquals(context.getId(), "AAABAAA");
        
        assertNull(contextCaptor.getAllValues().get(1));
    }
    
    @Test
    public void getLanguagesFromAcceptLanguageHeader() {
        when(mockRequest.getHeader(ACCEPT_LANGUAGE))
            .thenReturn("de-de;q=0.4,de;q=0.2,en-ca,en;q=0.8,en-us;q=0.6,fr;q=0.1");

        List<String> langs = RequestFilter.getLanguagesFromAcceptLanguageHeader(mockRequest, mockResponse);
        assertEquals(langs, ImmutableList.of("en", "de", "fr"));
    }
    
    @Test
    public void getLanguagesFromAcceptLanguageHeaderMissing() {
        List<String> langs = RequestFilter.getLanguagesFromAcceptLanguageHeader(mockRequest, mockResponse);
        assertTrue(langs.isEmpty());
    }

    @Test
    public void getClientInfoFromUserAgentHeader() {
        when(mockRequest.getHeader(USER_AGENT)).thenReturn(UA);

        ClientInfo info = RequestFilter.getClientInfoFromUserAgentHeader(mockRequest, mockResponse);
        
        assertEquals(info, ClientInfo.fromUserAgentCache(UA));
    }

    @Test
    public void getClientInfoFromUserAgentHeaderMissing() {
        ClientInfo info = RequestFilter.getClientInfoFromUserAgentHeader(mockRequest, mockResponse);
        
        assertEquals(info, ClientInfo.UNKNOWN_CLIENT);
    }
    
    @Test
    public void addWarningMessage() {
        Collection<String> headerNames = new HashSet<>();
        when(mockResponse.getHeaderNames()).thenReturn(headerNames);
        
        RequestFilter.addWarningMessage(mockResponse, "first message");
        
        verify(mockResponse).setHeader(BRIDGE_API_STATUS_HEADER, "first message");
        
        // Mock what will change once the header is added, then add a second message
        headerNames.add(BRIDGE_API_STATUS_HEADER);
        when(mockResponse.getHeader(BRIDGE_API_STATUS_HEADER)).thenReturn("first message");
        
        RequestFilter.addWarningMessage(mockResponse, "second message");
        
        verify(mockResponse).setHeader(BRIDGE_API_STATUS_HEADER, "first message; second message");
    }
    
    // Tests from original Play tests.
    
    @Test
    public void doesNotThrowErrorWhenUserAgentStringInvalid() throws Exception {
        when(mockRequest.getHeader(USER_AGENT)).thenReturn(
                "Amazon Route 53 Health Check Service; ref:c97cd53f-2272-49d6-a8cd-3cd658d9d020; report http://amzn.to/1vsZADi");
        
        ClientInfo info = RequestFilter.getClientInfoFromUserAgentHeader(mockRequest, mockResponse);
        assertEquals(info, ClientInfo.UNKNOWN_CLIENT);
        assertNull(info.getAppName());
        assertNull(info.getAppVersion());
        assertNull(info.getOsName());
        assertNull(info.getOsVersion());
        assertNull(info.getSdkName());
        assertNull(info.getSdkVersion());
    }

    @Test
    public void doesNotSetWarningHeaderWhenHasUserAgent() throws Exception {
        when(mockRequest.getHeader(USER_AGENT)).thenReturn(UA);
        
        RequestFilter.getClientInfoFromUserAgentHeader(mockRequest, mockResponse);
        
        verify(mockResponse, never()).setHeader(any(), any());
    }

    @Test
    public void setWarningHeaderWhenNoUserAgent() throws Exception {
        RequestFilter.getClientInfoFromUserAgentHeader(mockRequest, mockResponse);

        verify(mockResponse).setHeader(BRIDGE_API_STATUS_HEADER, WARN_NO_USER_AGENT);
    }

    @Test
    public void setWarningHeaderWhenEmptyUserAgent() throws Exception {
        when(mockRequest.getHeader(USER_AGENT)).thenReturn("");

        RequestFilter.getClientInfoFromUserAgentHeader(mockRequest, mockResponse);

        verify(mockResponse).setHeader(BRIDGE_API_STATUS_HEADER, WARN_NO_USER_AGENT);
    }

    @Test
    public void setWarningHeaderWhenNullUserAgent() throws Exception {
        when(mockRequest.getHeader(USER_AGENT)).thenReturn(null);

        RequestFilter.getClientInfoFromUserAgentHeader(mockRequest, mockResponse);

        verify(mockResponse).setHeader(BRIDGE_API_STATUS_HEADER, WARN_NO_USER_AGENT);
    }

    @Test
    public void canRetrieveLanguagesWhenHeaderIsNull() throws Exception {
        when(mockRequest.getHeader(ACCEPT_LANGUAGE)).thenReturn(null);
        
        List<String> langs = RequestFilter.getLanguagesFromAcceptLanguageHeader(mockRequest, mockResponse);
        assertEquals(langs, ImmutableList.of());
    }        
    
    @Test
    public void canRetrieveLanguagesWhenHeaderIsEmpty() throws Exception {
        when(mockRequest.getHeader(ACCEPT_LANGUAGE)).thenReturn("");
        
        List<String> langs = RequestFilter.getLanguagesFromAcceptLanguageHeader(mockRequest, mockResponse);
        assertEquals(langs, ImmutableList.of());
    }
    
    @Test
    public void canRetrieveLanguagesWhenHeaderIsSimple() throws Exception {
        when(mockRequest.getHeader(ACCEPT_LANGUAGE)).thenReturn("en-US");
        
        List<String> langs = RequestFilter.getLanguagesFromAcceptLanguageHeader(mockRequest, mockResponse);
        assertEquals(langs, ImmutableList.of("en"));
    }
    
    @Test
    public void canRetrieveLanguagesWhenHeaderIsCompoundWithoutWeights() throws Exception {
        when(mockRequest.getHeader(ACCEPT_LANGUAGE)).thenReturn("FR,en-US");
        
        List<String> langs = RequestFilter.getLanguagesFromAcceptLanguageHeader(mockRequest, mockResponse);
        assertEquals(langs, ImmutableList.of("fr", "en"));
    }
    
    /**
     * Languages are reordered according to weight, and * is discarded properly.
     */
    @Test
    public void canRetrieveLanguagesRespectingWeights() throws Exception {
        when(mockRequest.getHeader(ACCEPT_LANGUAGE)).thenReturn("en;q=0.8, de;q=0.7, *;q=0.5, fr-CH, fr;q=0.9");
        
        List<String> langs = RequestFilter.getLanguagesFromAcceptLanguageHeader(mockRequest, mockResponse);
        assertEquals(langs, ImmutableList.of("fr", "en", "de"));
    }
    
    // We don't want to throw a BadRequestException due to a malformed header. Just return no languages.
    @Test
    public void badAcceptLanguageHeaderSilentlyIgnored() throws Exception {
        when(mockRequest.getHeader(ACCEPT_LANGUAGE)).thenReturn("chrome://global/locale/intl.properties");
        
        List<String> langs = RequestFilter.getLanguagesFromAcceptLanguageHeader(mockRequest, mockResponse);
        assertTrue(langs.isEmpty());
    }
    

    @Test
    public void setWarnHeaderWhenNoAcceptLanguage() throws Exception {
        // with no accept language header at all, things don't break;
        List<String> langs = RequestFilter.getLanguagesFromAcceptLanguageHeader(mockRequest, mockResponse);
        assertEquals(langs, ImmutableList.of());

        // verify if it set warning header
        verify(mockResponse).setHeader(BRIDGE_API_STATUS_HEADER, WARN_NO_ACCEPT_LANGUAGE);
    }

    @Test
    public void setWarnHeaderWhenEmptyAcceptLanguage() throws Exception {
        when(mockRequest.getHeader(ACCEPT_LANGUAGE)).thenReturn("");

        // with no accept language header at all, things don't break;
        List<String> langs = RequestFilter.getLanguagesFromAcceptLanguageHeader(mockRequest, mockResponse);
        assertEquals(langs, ImmutableList.of());

        // verify if it set warning header
        verify(mockResponse).setHeader(BRIDGE_API_STATUS_HEADER, WARN_NO_ACCEPT_LANGUAGE);
    }


    @Test
    public void setWarnHeaderWhenNullAcceptLanguage() throws Exception {
        when(mockRequest.getHeader(ACCEPT_LANGUAGE)).thenReturn(null);

        // with no accept language header at all, things don't break;
        List<String> langs = RequestFilter.getLanguagesFromAcceptLanguageHeader(mockRequest, mockResponse);
        assertEquals(langs, ImmutableList.of());

        // verify if it set warning header
        verify(mockResponse).setHeader(BRIDGE_API_STATUS_HEADER, WARN_NO_ACCEPT_LANGUAGE);
    }

    @Test
    public void setWarnHeaderWhenInvalidAcceptLanguage() throws Exception {
        when(mockRequest.getHeader(ACCEPT_LANGUAGE)).thenReturn("ThisIsAnVvalidAcceptLanguage");

        // with no accept language header at all, things don't break;
        List<String> langs = RequestFilter.getLanguagesFromAcceptLanguageHeader(mockRequest, mockResponse);
        assertEquals(langs, ImmutableList.of());

        // verify if it set warning header
        verify(mockResponse).setHeader(BRIDGE_API_STATUS_HEADER, WARN_NO_ACCEPT_LANGUAGE);
    }
    
    @Test
    public void getRemoteAddress() {
        when(mockRequest.getHeader(X_FORWARDED_FOR_HEADER)).thenReturn(IP_ADDRESS);
        
        String address = RequestFilter.getRemoteAddress(mockRequest);
        assertEquals(address, IP_ADDRESS);
    }
    
    @Test
    public void getRemoteAddressFallsBackToServletAPI() {
        when(mockRequest.getRemoteAddr()).thenReturn(IP_ADDRESS);
        
        String address = RequestFilter.getRemoteAddress(mockRequest);
        assertEquals(address, IP_ADDRESS);
    }
    
    @Test
    public void getRemoteAddressFails() {
        String address = RequestFilter.getRemoteAddress(mockRequest);
        assertNull(address);
    }
    
    @Test
    public void getRemoteAddressFromHeader() throws Exception {
        when(mockRequest.getHeader(X_FORWARDED_FOR_HEADER)).thenReturn(IP_ADDRESS);

        assertEquals(IP_ADDRESS, RequestFilter.getRemoteAddress(mockRequest));
    }

    @Test
    public void getRemoteAddressFromFallback() throws Exception {
        when(mockRequest.getRemoteAddr()).thenReturn(IP_ADDRESS);
        
        assertEquals(IP_ADDRESS, RequestFilter.getRemoteAddress(mockRequest));
    }
}
