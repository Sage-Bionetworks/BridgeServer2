package org.sagebionetworks.bridge.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.RequestInfoDao;
import org.sagebionetworks.bridge.models.RequestInfo;

@Component
public class RequestInfoService {
    
    private CacheProvider cacheProvider;
    private RequestInfoDao requestInfoDao;
    
    @Autowired
    final void setCacheProvider(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }
    @Autowired
    final void setRequestInfoDao(RequestInfoDao requestInfoDao) {
        this.requestInfoDao = requestInfoDao;
    }
    
    public void updateRequestInfo(RequestInfo requestInfo) {
        requestInfoDao.updateRequestInfo(requestInfo);
    }
    
    public RequestInfo getRequestInfo(String userId) {
        RequestInfo requestInfo = requestInfoDao.getRequestInfo(userId);
        if (requestInfo == null) {
            requestInfo = cacheProvider.getRequestInfo(userId);
            if (requestInfo != null) {
                requestInfoDao.updateRequestInfo(requestInfo);
            }
        }
        return requestInfo;
    }
    
    public void removeRequestInfo(String userId) {
        requestInfoDao.removeRequestInfo(userId);
        cacheProvider.removeRequestInfo(userId);
    }

}
