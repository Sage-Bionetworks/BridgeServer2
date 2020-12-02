package org.sagebionetworks.bridge.hibernate;

import static com.google.common.base.Preconditions.checkNotNull;

import javax.annotation.Resource;

import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.RequestInfoDao;
import org.sagebionetworks.bridge.models.RequestInfo;

@Component
public class HibernateRequestInfoDao implements RequestInfoDao {
    private HibernateHelper hibernateHelper;
    
    @Resource(name = "basicHibernateHelper")
    final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }
    
    @Override
    public void updateRequestInfo(RequestInfo requestInfo) {
        RequestInfo existingRequestInfo = getRequestInfo(requestInfo.getUserId());
        if (existingRequestInfo != null) {
            RequestInfo.Builder builder = new RequestInfo.Builder();    
            builder.copyOf(existingRequestInfo);
            builder.copyOf(requestInfo);
            hibernateHelper.update(builder.build());
        } else {
            hibernateHelper.create(requestInfo);
        }        
    }

    @Override
    public RequestInfo getRequestInfo(String userId) {
        checkNotNull(userId);
        
        return hibernateHelper.getById(RequestInfo.class, userId);
    }
    
    @Override
    public void removeRequestInfo(String userId) {
        checkNotNull(userId);
        
        // You do get an error if you try and delete a request info object that doesn't exist.
        RequestInfo existingRequestInfo = getRequestInfo(userId);
        if (existingRequestInfo != null) {
            hibernateHelper.deleteById(RequestInfo.class, userId);    
        }
    }    
}
