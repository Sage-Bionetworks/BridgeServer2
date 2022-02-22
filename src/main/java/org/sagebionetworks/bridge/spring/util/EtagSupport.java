    package org.sagebionetworks.bridge.spring.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EtagSupport {
    /**
     * One or more property names that will be used to construct the cache key. These values
     * must be provided in the same order in the CacheKey.etag(...) method when you are constructing
     * a key to invalidate the Etag cache. If the property name matches a method argument name, 
     * that value will be used, otherwise the property names will take information from the 
     * user’s session (the supported values here are appId, userId, and orgId). Note that if the 
     * request is not associated to a session, and the ETag implementation needs the session to 
     * fulfill one of these key values, then the ETag implementation will throw an 
     * IllegalArgumentException.
     */
    EtagCacheKey[] value() default {};
}
