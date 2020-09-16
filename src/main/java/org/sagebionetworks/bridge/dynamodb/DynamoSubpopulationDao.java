package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.util.BridgeCollectors.toImmutableList;
import static org.sagebionetworks.bridge.models.OperatingSystem.ANDROID;
import static org.sagebionetworks.bridge.models.OperatingSystem.IOS;

import java.util.List;

import javax.annotation.Resource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.CriteriaDao;
import org.sagebionetworks.bridge.dao.SubpopulationDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.google.common.collect.ImmutableSet;

@Component
public class DynamoSubpopulationDao implements SubpopulationDao {
    
    static final String CANNOT_DELETE_DEFAULT_SUBPOP_MSG = "Cannot delete the default subpopulation for an app.";
    private DynamoDBMapper mapper;
    private CriteriaDao criteriaDao;

    @Resource(name = "subpopulationDdbMapper")
    final void setMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }

    @Autowired
    final void setCriteriaDao(CriteriaDao criteriaDao) {
        this.criteriaDao = criteriaDao;
    }
    
    // Exposed for mock tests
    String generateGuid() {
        return BridgeUtils.generateGuid();
    }
    
    @Override
    public Subpopulation createSubpopulation(Subpopulation subpop) {
        checkNotNull(subpop);
        checkNotNull(subpop.getAppId());
        
        subpop.setGuidString(generateGuid());
        subpop.setDeleted(false); 
        subpop.setDefaultGroup(false);
        subpop.setVersion(null);
        subpop.setPublishedConsentCreatedOn(0L);

        Criteria criteria = persistCriteria(subpop);
        subpop.setCriteria(criteria);

        mapper.save(subpop);
        return subpop;
    }

    @Override
    public Subpopulation updateSubpopulation(Subpopulation subpop) {
        checkNotNull(subpop);
        checkNotNull(subpop.getAppId());
        
        // These have to be supplied by the user so if they don't exist, we want a 400-level exception,
        if (subpop.getVersion() == null || subpop.getGuidString() == null) {
            throw new BadRequestException("Subpopulation appears to be a new object (no guid or version).");
        }
        Subpopulation existing = getSubpopulation(subpop.getAppId(), subpop.getGuid());
        if (existing.isDeleted() && subpop.isDeleted()) {
            throw new EntityNotFoundException(Subpopulation.class);
        }
        // You also can't set the deleted flag to deleted if this is the default subpopulation.
        if (existing.isDefaultGroup() && subpop.isDeleted()) {
            throw new BadRequestException(CANNOT_DELETE_DEFAULT_SUBPOP_MSG);
        }
        Criteria criteria = persistCriteria(subpop);
        subpop.setCriteria(criteria);
        subpop.setDefaultGroup(existing.isDefaultGroup());
        
        mapper.save(subpop);
        return subpop;
    }

    @Override
    public List<Subpopulation> getSubpopulations(String appId, boolean includeDeleted) {
        DynamoSubpopulation hashKey = new DynamoSubpopulation();
        hashKey.setAppId(appId);
        
        DynamoDBQueryExpression<DynamoSubpopulation> query = 
                new DynamoDBQueryExpression<DynamoSubpopulation>().withHashKeyValues(hashKey);
        
        // Get all the records because we only create a default if there are no physical records, 
        // regardless of the deletion status. This was a bootstrapping step and at this point, 
        // no new apps will be created without a default subpopulation.
        List<DynamoSubpopulation> subpops = mapper.query(DynamoSubpopulation.class, query);
        // Now filter out deleted subpopulations, if requested
        List<Subpopulation> subpopulations = subpops.stream()
                .filter(subpop -> includeDeleted || !subpop.isDeleted())
                .collect(toImmutableList());
        
        for (Subpopulation subpop : subpopulations) {
            loadCriteria(subpop);
        }
        return subpopulations;
    }
    
    @Override
    public Subpopulation createDefaultSubpopulation(String appId, String studyId) {
        DynamoSubpopulation subpop = new DynamoSubpopulation();
        subpop.setAppId(appId);
        subpop.setGuidString(appId);
        subpop.setName("Default Consent Group");
        subpop.setDefaultGroup(true);
        // The first group is required until the study designers say otherwise
        subpop.setRequired(true);
        subpop.setStudyIdsAssignedOnConsent(ImmutableSet.of(studyId));
        
        Criteria criteria = Criteria.create();
        criteria.setKey(getKey(subpop));
        criteria.setMinAppVersion(ANDROID, 0);
        criteria.setMinAppVersion(IOS, 0);

        criteria = criteriaDao.createOrUpdateCriteria(criteria);
        subpop.setCriteria(criteria);
        
        mapper.save(subpop);
        return subpop;
    }
    
    @Override
    public Subpopulation getSubpopulation(String appId, SubpopulationGuid subpopGuid) {
        DynamoSubpopulation hashKey = new DynamoSubpopulation();
        hashKey.setAppId(appId);
        hashKey.setGuidString(subpopGuid.getGuid());
        
        Subpopulation subpop = mapper.load(hashKey);
        if (subpop == null) {
            throw new EntityNotFoundException(Subpopulation.class);
        }
        loadCriteria(subpop);
        
        return subpop;
    }

    @Override
    public void deleteSubpopulation(String appId, SubpopulationGuid subpopGuid) {
        Subpopulation subpop = getSubpopulation(appId, subpopGuid);
        
        if (subpop.isDeleted()) {
            throw new EntityNotFoundException(Subpopulation.class);
        }
        if (subpop.isDefaultGroup()) {
            throw new BadRequestException(CANNOT_DELETE_DEFAULT_SUBPOP_MSG);
        }
        subpop.setDeleted(true);
        mapper.save(subpop);
    }
    
    @Override
    public void deleteSubpopulationPermanently(String appId, SubpopulationGuid subpopGuid) {
        Subpopulation subpop = getSubpopulation(appId, subpopGuid);

        criteriaDao.deleteCriteria(subpop.getCriteria().getKey());
        mapper.delete(subpop);
    }
    
    private String getKey(Subpopulation subpop) {
        return "subpopulation:" + subpop.getGuidString();
    }
    
    private Criteria persistCriteria(Subpopulation subpop) {
        Criteria criteria = subpop.getCriteria();
        criteria.setKey(getKey(subpop));
        return criteriaDao.createOrUpdateCriteria(criteria);
    }

    private void loadCriteria(Subpopulation subpop) {
        Criteria criteria = criteriaDao.getCriteria(getKey(subpop));
        // Not sure this is even possible at this point. But if the original save did not completely succeed, 
        // this will prevent errors and the user will be able to redo criteria (if any).
        if (criteria == null) {
            criteria = Criteria.create();
        }
        criteria.setKey(getKey(subpop));
        subpop.setCriteria(criteria);
    }
}
