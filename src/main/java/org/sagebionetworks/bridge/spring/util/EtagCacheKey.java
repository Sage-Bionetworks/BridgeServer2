package org.sagebionetworks.bridge.spring.util;

public @interface EtagCacheKey {
    /**
     * The object being cached. This is the first argument to CacheKey.etag. 
     */
    Class<?> model();
    /**
     * The keys that will be used to cache the modifiedOn timestamp of the model. These are
     * the keys arguments to CacheKey.etag. The keys can be as as simple as the primary key 
     * of the model, but some models have a compound key that includes appId, and some 
     * objects are cached in a different scope (for example, objects that are specific to a 
     * user, or objects that are based on a schedule). It requires a balance between what is 
     * known in the controller, and what we know when objects are being modified in the service.
     */
    String[] keys();
    
    /**
     * If a query parameter name is given for this attribute, a change in that value from the 
     * persisted value on the server should invalidate the request (no 304 can be returned, 
     * although the call should update the value, so a correct etag can be calculated and 
     * returned to the caller after the controller method returns). The only field currently 
     * supported in this array is “clientTimeZone”. 
     */
    String invalidateCacheOnChange() default "";
}
