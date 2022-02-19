package org.sagebionetworks.bridge.spring.util;

import static org.sagebionetworks.bridge.BridgeConstants.SESSION_TOKEN_HEADER;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.net.HttpHeaders;

@Aspect
@Component
public class EtagComponent {
    private static final Logger LOG = LoggerFactory.getLogger(EtagComponent.class);

    private static final String NO_VALUE_ERROR = "EtagSupport: no value for key: ";
    private static final String ORG_ID_FIELD = "orgId";
    private static final String USER_ID_FIELD = "userId";
    private static final String APP_ID_FIELD = "appId";
    
    private CacheProvider cacheProvider;
    
    private DigestUtils md5DigestUtils;
    
    @Autowired
    final void setCacheProvider(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }
    
    @Autowired
    final void setDigestUtils(DigestUtils md5DigestUtils) {
        this.md5DigestUtils = md5DigestUtils;
    }

    protected HttpServletRequest request() {
        return ((ServletRequestAttributes) RequestContextHolder
                .currentRequestAttributes()).getRequest();
    }
    
    protected HttpServletResponse response() {
        return ((ServletRequestAttributes) RequestContextHolder
                .currentRequestAttributes()).getResponse();
    }
    
    protected EtagContext context(ProceedingJoinPoint joinPoint) {
        return new EtagContext(joinPoint);
    }
    
    @Around("@annotation(EtagSupport)")
    public Object checkEtag(ProceedingJoinPoint joinPoint) throws Throwable {
        EtagContext context = context(joinPoint);
        
        HttpServletResponse response = response();
        HttpServletRequest request = request();
        String requestEtag = request.getHeader(HttpHeaders.IF_NONE_MATCH);
        String sessionToken = request.getHeader(SESSION_TOKEN_HEADER);
        UserSession session = cacheProvider.getUserSession(sessionToken); // can be null

        CacheKey cacheKey = generateCacheKey(session, context);
        if (requestEtag != null) {
            String etag = cacheProvider.getObject(cacheKey, String.class);
            if (requestEtag.equals(etag)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Returning 304 for key: " + cacheKey.toString());    
                }
                response.setStatus(304);
                return null;
            }
        }
        Object retValue = joinPoint.proceed();
        
        String newEtag = calculateETag(retValue);
        response.addHeader(HttpHeaders.ETAG, newEtag);
        cacheProvider.setObject(cacheKey, newEtag);
        
        if (LOG.isDebugEnabled()) {
            LOG.debug("Caching etag: " + cacheKey.toString() + " = " + newEtag);
        }
        return retValue;
    }

    private String calculateETag(Object retValue) throws JsonProcessingException {
        String string = BridgeObjectMapper.get().writeValueAsString(retValue);
        byte[] md5 = md5DigestUtils.digest(string.getBytes());
        return Hex.encodeHexString(md5);
    }
    
    private CacheKey generateCacheKey(UserSession session, EtagContext context) {
        Class<?> model = context.getModel();
        List<String> keys = context.getCacheKeys();
        
        String[] elements = new String[keys.size()];
        int len = keys.size();
        for (int i=0; i < len; i++) {
            elements[i] = getValue(context, session, keys.get(i));
        }
        return CacheKey.etag(model, elements);
    }
    
    private String getValue(EtagContext context, UserSession session, String fieldName) {
        if (context.getArgValues().containsKey(fieldName)) {
            String value = (String) context.getArgValues().get(fieldName);
            if (value == null) {
                throw new IllegalArgumentException(NO_VALUE_ERROR + fieldName);
            }
            return value;
        }
        // It is possible to use @EtagSupport on an unauthenticated method, if it never needs to look up a field 
        // value from the session. But if it needs the session to find a value for a key, and the session isn't
        // present, it will throw an exception.
        String value = null;
        if (session != null) {
            if (APP_ID_FIELD.equals(fieldName)) {
                value = session.getAppId();
            }
            if (USER_ID_FIELD.equals(fieldName)) {
                value = session.getId();
            }
            if (ORG_ID_FIELD.equals(fieldName)) {
                value = session.getParticipant().getOrgMembership();
            }
        }
        if (value == null) {
            throw new IllegalArgumentException(NO_VALUE_ERROR + fieldName);
        }
        return value;
    }
}
