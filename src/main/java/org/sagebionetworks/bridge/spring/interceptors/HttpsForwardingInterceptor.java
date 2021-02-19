package org.sagebionetworks.bridge.spring.interceptors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import org.sagebionetworks.bridge.config.BridgeConfig;

/** Interceptor for HTTPS forwarding. */
@Component
public class HttpsForwardingInterceptor extends HandlerInterceptorAdapter {
    // Package-scoped for unit tests.
    static final String HEADER_LOCATION = "Location";
    static final String HEADER_X_FORWARDED_PROTO = "X-Forwarded-Proto";

    private BridgeConfig config;

    @Autowired
    final void setConfig(BridgeConfig config) {
        this.config = config;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (config.isLocal()) {
            // Skip for local.
            return true;
        }
        if (!config.useHttpsForwarding()) {
            // HTTPS Forwarding is disabled.
            return true;
        }

        if ("http".equalsIgnoreCase(request.getHeader(HEADER_X_FORWARDED_PROTO))) {
            // This is similar to the logic in the original Play implementation. Generally, the HTTPS stuff is handled
            // by the load balancer, but it will include the X-Forwarded-Proto handler with the original protocol. Use
            // this to determine whether we redirect to HTTPS.
            //
            // As a side effect, since local development boxes are not behind a load balancer, this will have no effect
            // on local development boxes.
            String redirectUrl = "https://" + request.getServerName() + request.getRequestURI();
            response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
            response.setHeader(HEADER_LOCATION, redirectUrl);

            // Return false to tell the server to not continue processing this request.
            return false;
        }
        return true;
    }
}
