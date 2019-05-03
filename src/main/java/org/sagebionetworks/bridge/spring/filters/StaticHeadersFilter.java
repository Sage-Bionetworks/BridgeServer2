package org.sagebionetworks.bridge.spring.filters;

import java.io.IOException;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.ImmutableMap;

import org.springframework.stereotype.Component;

@Component
public class StaticHeadersFilter implements Filter {
    
    public static final Map<String,String> HEADERS = new ImmutableMap.Builder<String,String>()
            // Limits what a web browser will include or execute in a page; only applies to our html pages
            .put("Content-Security-Policy", "default-src 'self' 'unsafe-inline' assets.sagebridge.org")
            // Do not send a cookie across a connection that is not HTTPS
            .put("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
            // Do not allow Mime-Type content "sniffing," when we say something is JSON, it's JSON
            .put("X-Content-Type-Options", "nosniff")
            // Do not render our HTML pages in a frame, iframe or object
            .put("X-Frame-Options", "DENY")
            // Don't allow people to embed our PDFs in their web sites. May be overkill
            .put("X-Permitted-Cross-Domain-Policies", "none")
            // XSS protection (because we run inline scripts, this isn't a bad idea, but our page generation
            // is trivial and we have no 3rd party includes, so risk is very low)
            .put("X-XSS-Protection", "1; mode=block").build();
    
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        addHeaders((HttpServletResponse)res);
        chain.doFilter(req, res);
    }
    
    private void addHeaders(HttpServletResponse response) {
        for (Map.Entry<String, String> entry : HEADERS.entrySet()) {
            response.setHeader(entry.getKey(), entry.getValue());
        }
    }
}
