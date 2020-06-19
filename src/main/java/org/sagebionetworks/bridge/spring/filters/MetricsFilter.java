package org.sagebionetworks.bridge.spring.filters;

import static org.apache.http.HttpHeaders.USER_AGENT;
import static org.sagebionetworks.bridge.BridgeConstants.X_FORWARDED_FOR_HEADER;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.Metrics;

@Component
public class MetricsFilter implements Filter {

    private static final Logger LOG = LoggerFactory.getLogger(MetricsFilter.class);
    
    public static final String X_PASSTHROUGH = "X-Passthrough";
    
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        final Metrics metrics = BridgeUtils.getRequestContext().getMetrics();
        
        HttpServletRequest request = (HttpServletRequest)req;
        HttpServletResponse response = (HttpServletResponse)res;
        metrics.setMethod(request.getMethod());
        metrics.setUri(request.getServletPath());
        metrics.setProtocol(request.getProtocol());
        metrics.setRemoteAddress(header(request, X_FORWARDED_FOR_HEADER, request.getRemoteAddr()));
        metrics.setUserAgent(header(request, USER_AGENT, null));

        // Process the query parameters, and append them to the metrics.
        // TODO: Set an allow-list for the parameters to avoid PII leaking.
        List<NameValuePair> params = URLEncodedUtils.parse(request.getQueryString(), StandardCharsets.UTF_8);
        metrics.setQueryParams(params);

        try {
            chain.doFilter(req, res);
            metrics.setStatus(response.getStatus());
        } finally {
            if (response.getHeader(X_PASSTHROUGH) == null) {
                metrics.end();
                LOG.info(metrics.toJsonString());
            }
        }
    }

    private String header(HttpServletRequest request, String name, String defaultVal) {
        final String value = request.getHeader(name);
        return (value != null) ? value : defaultVal;
    }

    @Override
    public void init(FilterConfig filterConfig) {
        // no-op
    }

    @Override
    public void destroy() {
        // no-op
    }
}
