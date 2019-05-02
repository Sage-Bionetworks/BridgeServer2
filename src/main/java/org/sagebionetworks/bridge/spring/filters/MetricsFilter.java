package org.sagebionetworks.bridge.spring.filters;

import static org.apache.http.HttpHeaders.USER_AGENT;
import static org.sagebionetworks.bridge.BridgeConstants.X_FORWARDED_FOR_HEADER;
import static org.sagebionetworks.bridge.BridgeConstants.X_REQUEST_ID_HEADER;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.models.Metrics;

@Component
public class MetricsFilter implements Filter {

    private static final Logger LOG = LoggerFactory.getLogger(MetricsFilter.class);
    
    private CacheProvider cacheProvider;
    
    @Autowired
    final void setCacheProvider(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        final Metrics metrics = initMetrics((HttpServletRequest)req);
        
        CacheKey metricsKey = CacheKey.metricsKey(metrics.getCacheKey());
        cacheProvider.setObject(metricsKey, metrics, BridgeConstants.METRICS_EXPIRE_SECONDS);
        try {
            chain.doFilter(req, res);
            metrics.setStatus(((HttpServletResponse)res).getStatus());
        } finally {
            cacheProvider.removeObject(metricsKey);
            metrics.end();
            LOG.info(metrics.toJsonString());
        }
    }

    Metrics initMetrics(HttpServletRequest request) {
        String requestId = request.getHeader(X_REQUEST_ID_HEADER);
        final Metrics metrics = new Metrics(requestId);
        metrics.setMethod(request.getMethod());
        metrics.setUri(request.getServletPath());
        metrics.setProtocol(request.getProtocol());
        metrics.setRemoteAddress(header(request, X_FORWARDED_FOR_HEADER, request.getRemoteAddr()));
        metrics.setUserAgent(header(request, USER_AGENT, null));
        return metrics;
    }
    
    private String header(HttpServletRequest request, String name, String defaultVal) {
        final String value = request.getHeader(name);
        return (value != null) ? value : defaultVal;
    }
}
