package org.sagebionetworks.bridge.spring.filters;

import static java.util.stream.Collectors.toCollection;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.BridgeConstants.BRIDGE_API_STATUS_HEADER;
import static org.sagebionetworks.bridge.BridgeConstants.WARN_NO_ACCEPT_LANGUAGE;
import static org.sagebionetworks.bridge.BridgeConstants.WARN_NO_USER_AGENT;
import static org.sagebionetworks.bridge.BridgeConstants.X_FORWARDED_FOR_HEADER;
import static org.sagebionetworks.bridge.BridgeConstants.X_REQUEST_ID_HEADER;
import static org.sagebionetworks.bridge.models.ClientInfo.UNKNOWN_CLIENT;
import static org.springframework.http.HttpHeaders.ACCEPT_LANGUAGE;
import static org.springframework.http.HttpHeaders.USER_AGENT;

import java.io.IOException;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Vector;
import java.util.Locale.LanguageRange;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.ImmutableList;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.models.ClientInfo;

@Component
public class RequestFilter implements Filter {
    private final static Logger LOG = LoggerFactory.getLogger(RequestFilter.class);
    
    private static class RequestIdWrapper extends HttpServletRequestWrapper {
        private final String requestId;
        RequestIdWrapper(HttpServletRequest request, String requestId) {
            super(request);
            this.requestId = requestId;
        }
        @Override
        public String getHeader(String name) {
            if (X_REQUEST_ID_HEADER.equalsIgnoreCase(name)) {
                return requestId;
            }
            return super.getHeader(name);
        }
        @Override
        public Enumeration<String> getHeaderNames() {
            Vector<String> vector = new Vector<>();
            Enumeration<String> headerNames = super.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                vector.add(headerNames.nextElement());
            }
            if (!vector.contains(X_REQUEST_ID_HEADER)) {
                vector.add(X_REQUEST_ID_HEADER);    
            }
            return vector.elements();
        }
    }
    
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        // Set request ID in a request-scoped context object. This object will be replaced 
        // with further user-specific security information, if the controller method that 
        // was intercepted retrieves the user's session (this code is consolidated in the 
        // BaseController). For unauthenticated/public requests, we do *not* want a 
        // Bridge-Session header changing the security context of the call.
        
        HttpServletRequest request = (HttpServletRequest)req;
        HttpServletResponse response = (HttpServletResponse)res;
        String requestId = request.getHeader(X_REQUEST_ID_HEADER);
        if (requestId == null) {
            requestId = generateRequestId();
        }
        RequestContext.Builder builder = new RequestContext.Builder()
                .withRequestId(requestId)
                .withCallerIpAddress(parseIpAddress(getRemoteAddress(request)))
                .withCallerClientInfo(getClientInfoFromUserAgentHeader(request, response))
                .withCallerLanguages(getLanguagesFromAcceptLanguageHeader(request, response));
        setRequestContext(builder.build());

        req = new RequestIdWrapper(request, requestId);
        try {
            chain.doFilter(req, res);
        } finally {
            // Clear request context when finished.
            setRequestContext(null);
        }
    }
    
    // Isolated for testing
    protected String generateRequestId() {
        return BridgeUtils.generateGuid();
    }
    
    // Isolated for testing
    protected void setRequestContext(RequestContext context) {
        BridgeUtils.setRequestContext(context);
    }

    @Override
    public void init(FilterConfig filterConfig) {
        // no-op
    }

    @Override
    public void destroy() {
        // no-op
    }
    
    /**
     * Returns languages in the order of their quality rating in the original LanguageRange objects 
     * that are created from the Accept-Language header (first item in ordered set is the most-preferred 
     * language option).
     * @return
     */
    static List<String> getLanguagesFromAcceptLanguageHeader(HttpServletRequest request, HttpServletResponse response) {
        String acceptLanguageHeader = request.getHeader(ACCEPT_LANGUAGE);
        if (isNotBlank(acceptLanguageHeader)) {
            try {
                List<LanguageRange> ranges = Locale.LanguageRange.parse(acceptLanguageHeader);
                LinkedHashSet<String> languageSet = ranges.stream().map(range -> {
                    return Locale.forLanguageTag(range.getRange()).getLanguage();
                }).filter(StringUtils::isNotBlank).collect(toCollection(LinkedHashSet::new));
                return ImmutableList.copyOf(languageSet);
            } catch(IllegalArgumentException e) {
                // Accept-Language header was not properly formatted, do not throw an exception over 
                // a malformed header, just return that no languages were found.
                LOG.debug("Malformed Accept-Language header sent: " + acceptLanguageHeader);
            }
        }

        // if no Accept-Language header detected, we shall add an extra warning header
        addWarningMessage(response, WARN_NO_ACCEPT_LANGUAGE);
        return ImmutableList.of();
    }
    
    static ClientInfo getClientInfoFromUserAgentHeader(HttpServletRequest request, HttpServletResponse response) {
        String userAgentHeader = request.getHeader(USER_AGENT);
        ClientInfo info = ClientInfo.fromUserAgentCache(userAgentHeader);

        // if the user agent cannot be parsed (probably due to missing user agent string or unrecognizable user agent),
        // should set an extra header to http response as warning - we should have an user agent info for filtering to work
        if (info.equals(UNKNOWN_CLIENT)) {
            addWarningMessage(response, WARN_NO_USER_AGENT);
        }
        LOG.debug("User-Agent: '"+userAgentHeader+"' converted to " + info);    
        return info;
    }
    
    /**
     * Helper method to add warning message as an HTTP header.
     * @param msg
     */
    static void addWarningMessage(HttpServletResponse response, String msg) {
        if (response.getHeaderNames().contains(BRIDGE_API_STATUS_HEADER)) {
            String previousWarning = response.getHeader(BRIDGE_API_STATUS_HEADER);
            response.setHeader(BRIDGE_API_STATUS_HEADER, previousWarning + "; " + msg);
        } else {
            response.setHeader(BRIDGE_API_STATUS_HEADER, msg);
        }
    }
    
    static String getRemoteAddress(HttpServletRequest request) {
        String forwardHeader = request.getHeader(X_FORWARDED_FOR_HEADER);
        return (forwardHeader == null) ? request.getRemoteAddr() : forwardHeader;
    }
    
    // Helper method to parse an IP address from a raw string, as specified by getRemoteAddress().
    static String parseIpAddress(String fullIpAddressString) {
        if (isBlank(fullIpAddressString)) {
            // Canonicalize unspecified IP address to null.
            return null;
        }

        // Remote address comes from the X-Forwarded-For header. Since we're behind Amazon, this is almost always
        // 2 IP addresses, separated by a comma and a space. The second is an Amazon router. The first one is probably
        // the real IP address.
        //
        // Note that this isn't fool-proof. X-Forwarded-For can be spoofed. Also, double-proxies might exist, or the
        // first IP address might simply resolve to 192.168.X.1. In local, this is probably just 127.0.0.1. But at
        // least this is an added layer of defense vs not IP-locking at all.
        String[] ipAddressArray = fullIpAddressString.split(",");
        return ipAddressArray[0];
    }
}
