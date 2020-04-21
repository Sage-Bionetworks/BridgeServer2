package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Set;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.dao.SubstudyDao;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.substudies.Substudy;
import org.sagebionetworks.bridge.util.BridgeCollectors;
import org.sagebionetworks.bridge.validators.SubstudyValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableMap;

@Component
public class SubstudyService {
    
    private SubstudyDao substudyDao;
    
    @Autowired
    final void setSubstudyDao(SubstudyDao substudyDao) {
        this.substudyDao = substudyDao;
    }
    
    public Substudy getSubstudy(String appId, String id, boolean throwsException) {
        checkNotNull(appId);
        checkNotNull(id);
        
        Substudy substudy = substudyDao.getSubstudy(appId, id);
        if (throwsException && substudy == null) {
            throw new EntityNotFoundException(Substudy.class);
        }
        return substudy;
    }
    
    /**
     * Get the list of active substudy IDs for this app (used to validate criteria 
     * objects throughout the system). Calling this method is preferred to getSubstudies() 
     * so we can provide a cache for these infrequently changing identifiers.
     */
    public Set<String> getSubstudyIds(String appId) {
        return getSubstudies(appId, false).stream()
                .map(Substudy::getId).collect(BridgeCollectors.toImmutableSet());
    }
    
    public List<Substudy> getSubstudies(String appId, boolean includeDeleted) {
        checkNotNull(appId);
        
        return substudyDao.getSubstudies(appId, includeDeleted);
    }
    
    public VersionHolder createSubstudy(String appId, Substudy substudy) {
        checkNotNull(appId);
        checkNotNull(substudy);
        
        substudy.setAppId(appId);
        Validate.entityThrowingException(SubstudyValidator.INSTANCE, substudy);
        
        substudy.setVersion(null);
        substudy.setDeleted(false);
        DateTime timestamp = DateTime.now();
        substudy.setCreatedOn(timestamp);
        substudy.setModifiedOn(timestamp);
        
        Substudy existing = substudyDao.getSubstudy(appId, substudy.getId());
        if (existing != null) {
            throw new EntityAlreadyExistsException(Substudy.class,
                    ImmutableMap.of("id", existing.getId()));
        }
        return substudyDao.createSubstudy(substudy);
    }

    public VersionHolder updateSubstudy(String appId, Substudy substudy) {
        checkNotNull(appId);
        checkNotNull(substudy);

        substudy.setAppId(appId);
        Validate.entityThrowingException(SubstudyValidator.INSTANCE, substudy);
        
        Substudy existing = getSubstudy(appId, substudy.getId(), true);
        if (substudy.isDeleted() && existing.isDeleted()) {
            throw new EntityNotFoundException(Substudy.class);
        }
        substudy.setCreatedOn(existing.getCreatedOn());
        substudy.setModifiedOn(DateTime.now());
        
        return substudyDao.updateSubstudy(substudy);
    }
    
    public void deleteSubstudy(String appId, String id) {
        checkNotNull(appId);
        checkNotNull(id);
        
        Substudy existing = getSubstudy(appId, id, true);
        existing.setDeleted(true);
        existing.setModifiedOn(DateTime.now());
        substudyDao.updateSubstudy(existing);
    }
    
    public void deleteSubstudyPermanently(String appId, String id) {
        checkNotNull(appId);
        checkNotNull(id);
        
        // Throws exception if the element does not exist.
        getSubstudy(appId, id, true);
        substudyDao.deleteSubstudyPermanently(appId, id);
    }
}
