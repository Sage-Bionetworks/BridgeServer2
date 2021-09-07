package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.dao.AppConfigElementDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.appconfig.AppConfigElement;
import org.sagebionetworks.bridge.models.appconfig.AppConfigEnum;
import org.sagebionetworks.bridge.models.appconfig.AppConfigEnumId;
import org.sagebionetworks.bridge.validators.AppConfigElementValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableMap;

@Component
public class AppConfigElementService {
    
    private AppConfigElementDao appConfigElementDao;
    
    @Autowired
    final void setAppConfigElementDao(AppConfigElementDao appConfigElementDao) {
        this.appConfigElementDao = appConfigElementDao;
    }
    
    public List<AppConfigElement> getMostRecentElements(String appId, boolean includeDeleted) {
        checkNotNull(appId);
        
        return appConfigElementDao.getMostRecentElements(appId, includeDeleted);
    }
    
    public VersionHolder createElement(String appId, AppConfigElement element) {
        checkNotNull(appId);
        checkNotNull(element);
        
        if (element.getRevision() == null) {
            element.setRevision(1L);
        }
        // Validate that ID exists before you try and use it to set the key
        Validate.entityThrowingException(AppConfigElementValidator.INSTANCE, element);
        
        element.setAppId(appId);
        element.setId(element.getId());
        element.setVersion(null);
        element.setDeleted(false);
        element.setCreatedOn(DateTime.now().getMillis());
        element.setModifiedOn(element.getCreatedOn());
        
        AppConfigElement existing = appConfigElementDao.getElementRevision(appId, element.getId(),
                element.getRevision());
        if (existing != null) {
            throw new EntityAlreadyExistsException(AppConfigElement.class,
                    ImmutableMap.of("id", existing.getId(), "revision", existing.getRevision()));
        }
        return appConfigElementDao.saveElementRevision(element);
    }

    public List<AppConfigElement> getElementRevisions(String appId, String id, boolean includeDeleted) {
        checkNotNull(appId);
        checkNotNull(id);
        
        return appConfigElementDao.getElementRevisions(appId, id, includeDeleted);
    }
    
    public AppConfigElement getMostRecentElement(String appId, String id) {
        checkNotNull(appId);
        checkNotNull(id);
        
        AppConfigElement element = appConfigElementDao.getMostRecentElement(appId, id);
        if (element == null) {
            throw new EntityNotFoundException(AppConfigElement.class);
        }
        return element;
    }
    
    /**
     * Some app config elements contain enumerations that Bridge is aware of and will use to 
     * validate some models, allowing for the configuration of this validation by end users.
     * We know the JSON schema of these configuration elements, so we deserialize them into
     * a specific Java model (AppConfigEnum). If the config does not exist, a model is still
     * returned with validation set to false.
     */
    public AppConfigEnum getAppConfigEnum(String appId, AppConfigEnumId id) {
        checkNotNull(appId);
        checkNotNull(id);
        
        AppConfigElement element = appConfigElementDao.getMostRecentElement(appId, id.getAppConfigKey());
        if (element != null) {
            try {
                return BridgeObjectMapper.get().treeToValue(element.getData(), AppConfigEnum.class);
            } catch (JsonProcessingException e) {
                throw new BridgeServiceException(e);
            }
        }
        return new AppConfigEnum();
    }

    public AppConfigElement getElementRevision(String appId, String id, long revision) {
        checkNotNull(appId);
        checkNotNull(id);
        
        AppConfigElement element = appConfigElementDao.getElementRevision(appId, id, revision);
        if (element == null) {
            throw new EntityNotFoundException(AppConfigElement.class);
        }
        return element;
    }

    public VersionHolder updateElementRevision(String appId, AppConfigElement element) {
        checkNotNull(appId);
        checkNotNull(element);
        
        Validate.entityThrowingException(AppConfigElementValidator.INSTANCE, element);
        
        AppConfigElement existing = getElementRevision(appId, element.getId(), element.getRevision());
        if (element.isDeleted() && existing.isDeleted()) {
            throw new EntityNotFoundException(AppConfigElement.class);
        }
        element.setAppId(appId);
        element.setId(element.getId());
        element.setModifiedOn(DateTime.now().getMillis());
        // cannot change the creation timestamp
        element.setCreatedOn(existing.getCreatedOn());
        return appConfigElementDao.saveElementRevision(element);
    }
    
    public void deleteElementRevision(String appId, String id, long revision) {
        checkNotNull(appId);
        checkNotNull(id);
        
        AppConfigElement existing = getElementRevision(appId, id, revision);
        existing.setDeleted(true);
        existing.setModifiedOn(DateTime.now().getMillis());
        appConfigElementDao.saveElementRevision(existing);
    }
    
    public void deleteElementAllRevisions(String appId, String id) {
        checkNotNull(appId);
        checkNotNull(id);
        
        List<AppConfigElement> elements = appConfigElementDao.getElementRevisions(appId, id, false);
        long modifiedOn = DateTime.now().getMillis();
        for (AppConfigElement oneElement : elements) {
            oneElement.setDeleted(true);
            oneElement.setModifiedOn(modifiedOn);
            appConfigElementDao.saveElementRevision(oneElement);
        }
    }
    
    public void deleteElementRevisionPermanently(String appId, String id, long revision) {
        checkNotNull(appId);
        checkNotNull(id);
        
        // Throws exception if the element does not exist.
        getElementRevision(appId, id, revision);
        appConfigElementDao.deleteElementRevisionPermanently(appId, id, revision);
    }
    
    public void deleteElementAllRevisionsPermanently(String appId, String id) {
        checkNotNull(appId);
        checkNotNull(id);
        
        List<AppConfigElement> elements = appConfigElementDao.getElementRevisions(appId, id, true);
        for (AppConfigElement oneElement : elements) {
            appConfigElementDao.deleteElementRevisionPermanently(appId, oneElement.getId(), oneElement.getRevision());
        }
    }
}
