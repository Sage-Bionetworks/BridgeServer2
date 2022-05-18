package org.sagebionetworks.bridge.spring.util;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;

/** 
 * The context information for the Etag annotation is difficult to mock (some classes 
 * are final), so we mock this context object instead. This code gets verified through 
 * integration and manual tests.
 */
public class EtagContext {
    
    private final Class<?> model;
    private final List<EtagCacheKey> cacheKeys;
    private final Map<String,Object> argumentValues;
    private final boolean authenticationRequired; 

    public EtagContext(ProceedingJoinPoint joinPoint) {
        MethodSignature method = (MethodSignature)joinPoint.getSignature();
        model = method.getMethod().getReturnType();
        
        EtagSupport[] etag = method.getMethod().getAnnotationsByType(EtagSupport.class);
        cacheKeys = Arrays.asList(etag[0].value());
        authenticationRequired = etag[0].authenticationRequired();
        
        argumentValues = new HashMap<>();
        int len = joinPoint.getArgs().length;
        for (int i=0; i < len; i++) {
            String fieldName = method.getParameterNames()[i];
            String fieldValue = (String)joinPoint.getArgs()[i];
            argumentValues.put(fieldName, fieldValue); // null is ok
        }
    }
    
    public Class<?> getModel() {
        return model;
    }
    public List<EtagCacheKey> getCacheKeys() {
        return cacheKeys;
    }
    public Map<String,Object> getArgValues() {
        return argumentValues;
    }
    public boolean isAuthenticationRequired() {
        return authenticationRequired;
    }
}
