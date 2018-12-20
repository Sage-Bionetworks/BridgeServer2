package org.sagebionetworks.bridge.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import org.sagebionetworks.bridge.spring.interceptors.HttpsForwardingInterceptor;

@Component
public class BridgeWebMvcConfig implements WebMvcConfigurer {
    private HttpsForwardingInterceptor httpsForwardingInterceptor;

    @Autowired
    public final void setHttpsForwardingInterceptor(HttpsForwardingInterceptor httpsForwardingInterceptor) {
        this.httpsForwardingInterceptor = httpsForwardingInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(httpsForwardingInterceptor);
    }
}
