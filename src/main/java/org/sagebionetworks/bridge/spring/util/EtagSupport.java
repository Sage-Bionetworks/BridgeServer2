    package org.sagebionetworks.bridge.spring.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for a controller method annotation that suspends normal execution and returns a 304 
 * response if all the cache keys specified in this annotation match the caller’s ETag value.
 * 
 * The tag has one or more @EtagCacheKey values that specify the values of a CacheKey.etag key.
 * These are the dependencies that need to be tracked to ensure the etag submitted by the client
 * is up-to-date. If the property name matches a method argument name of the annotated method, 
 * that value will be used. Otherwise, the property name will be used to retrieve the information 
 * from the caller’s session (the supported values here are “appId”, “userId”, and “orgId“). 
 * Currently this tag can only be used on authenticated requests (the presence of the session is 
 * required). 
 * 
 * The values of the etag CacheKeys are Joda DateTime instances, representing the last time that 
 * model was updated.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EtagSupport {
    EtagCacheKey[] value() default {};
}
