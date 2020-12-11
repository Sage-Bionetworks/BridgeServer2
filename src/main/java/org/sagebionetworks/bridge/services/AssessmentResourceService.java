package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.jsoup.safety.Whitelist.simpleText;
import static org.sagebionetworks.bridge.AuthUtils.IS_ORG_MEMBER;
import static org.sagebionetworks.bridge.AuthUtils.IS_ORG_MEMBER_IN_APP;
import static org.sagebionetworks.bridge.BridgeConstants.SHARED_APP_ID;
import static org.sagebionetworks.bridge.BridgeUtils.sanitizeHTML;
import static org.sagebionetworks.bridge.models.ResourceList.CATEGORIES;
import static org.sagebionetworks.bridge.models.ResourceList.INCLUDE_DELETED;
import static org.sagebionetworks.bridge.models.ResourceList.MAX_REVISION;
import static org.sagebionetworks.bridge.models.ResourceList.MIN_REVISION;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_BY;
import static org.sagebionetworks.bridge.models.ResourceList.PAGE_SIZE;
import static org.sagebionetworks.bridge.validators.AssessmentResourceValidator.INSTANCE;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.AssessmentResourceDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.assessments.Assessment;
import org.sagebionetworks.bridge.models.assessments.AssessmentResource;
import org.sagebionetworks.bridge.models.assessments.ResourceCategory;
import org.sagebionetworks.bridge.validators.Validate;

@Component
public class AssessmentResourceService {
    
    private AssessmentResourceDao dao;
    
    private AssessmentService assessmentService;
    
    @Autowired
    final void setAssessmentResourceDao(AssessmentResourceDao dao) {
        this.dao = dao;
    }
    
    @Autowired
    final void setAssessmentService(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    DateTime getCreatedOn() {
        return DateTime.now();
    }
    
    DateTime getModifiedOn() {
        return DateTime.now();
    }
    
    String generateGuid() {
        return BridgeUtils.generateGuid();
    }
    
    public PagedResourceList<AssessmentResource> getResources(String appId, String assessmentId, Integer offsetBy,
            Integer pageSize, Set<ResourceCategory> categories, Integer minRevision, Integer maxRevision,
            boolean includeDeleted) {
        checkArgument(isNotBlank(appId));
        checkArgument(isNotBlank(assessmentId));
        
        if (minRevision != null && maxRevision != null && maxRevision < minRevision) {
            throw new BadRequestException("maxRevision cannot be greater than minRevision");
        }
        Assessment assessment = assessmentService.getLatestAssessment(appId, assessmentId);

        PagedResourceList<AssessmentResource> page = dao.getResources(appId, assessmentId, offsetBy, pageSize, categories, minRevision, maxRevision, includeDeleted);
        
        for (AssessmentResource resource : page.getItems()) {
            resource.setUpToDate(resource.getCreatedAtRevision() == assessment.getRevision());
        }
        return page.withRequestParam(OFFSET_BY, offsetBy)
                .withRequestParam(PAGE_SIZE, pageSize)
                .withRequestParam(CATEGORIES, categories)
                .withRequestParam(MIN_REVISION, minRevision)
                .withRequestParam(MAX_REVISION, maxRevision)
                .withRequestParam(INCLUDE_DELETED, includeDeleted);
    }

    public AssessmentResource getResource(String appId, String assessmentId, String guid) {
        checkArgument(isNotBlank(appId));
        checkArgument(isNotBlank(assessmentId));
        checkArgument(isNotBlank(guid));
        
        Assessment assessment = assessmentService.getLatestAssessment(appId, assessmentId);
        AssessmentResource resource = dao.getResource(appId, guid)
                .orElseThrow(() -> new EntityNotFoundException(AssessmentResource.class));
        resource.setUpToDate(resource.getCreatedAtRevision() == assessment.getRevision());
        return resource;
    }
    
    public AssessmentResource createResource(String appId, String assessmentId, AssessmentResource resource) {
        checkArgument(isNotBlank(appId));
        checkArgument(isNotBlank(assessmentId));
        checkNotNull(resource);
        
        Assessment assessment = assessmentService.getLatestAssessment(appId, assessmentId);
        IS_ORG_MEMBER.checkAndThrow("orgId", assessment.getOwnerId());
        
        DateTime timestamp = getCreatedOn();
        resource.setGuid(generateGuid());
        resource.setCreatedOn(timestamp);
        resource.setModifiedOn(timestamp);
        resource.setDeleted(false);
        resource.setCreatedAtRevision(assessment.getRevision());
        sanitizeResource(resource);
        
        // validate
        Validate.entityThrowingException(INSTANCE, resource);
        
        AssessmentResource retValue = dao.saveResource(appId, assessmentId, resource);
        retValue.setUpToDate(true);
        return retValue;
    }
    
    public AssessmentResource updateResource(String appId, String assessmentId, AssessmentResource resource) {
        checkArgument(isNotBlank(appId));
        checkArgument(isNotBlank(assessmentId));
        checkNotNull(resource);
        
        Assessment assessment = assessmentService.getLatestAssessment(appId, assessmentId);
        
        IS_ORG_MEMBER.checkAndThrow("orgId", assessment.getOwnerId());
        
        return updateResourceInternal(appId, assessmentId, assessment, resource);
    }

    
    public AssessmentResource updateSharedResource(String callerAppId, String assessmentId, AssessmentResource resource) {
        checkArgument(isNotBlank(callerAppId));
        checkArgument(isNotBlank(assessmentId));
        checkNotNull(resource);
        
        Assessment assessment = assessmentService.getLatestAssessment(SHARED_APP_ID, assessmentId);
        
        IS_ORG_MEMBER_IN_APP.checkAndThrow("ownerId", assessment.getOwnerId());
        
        return updateResourceInternal(SHARED_APP_ID, assessmentId, assessment, resource);
    }
    
    
    private AssessmentResource updateResourceInternal(String appId, String assessmentId, Assessment assessment,
            AssessmentResource resource) {
        // Don't call this.getResource(), you'll just load the assessment twice
        AssessmentResource existing = dao.getResource(appId, resource.getGuid())
                .orElseThrow(() -> new EntityNotFoundException(AssessmentResource.class));
        if (resource.isDeleted() && existing.isDeleted()) {
            throw new EntityNotFoundException(AssessmentResource.class);
        }
        resource.setCreatedAtRevision(assessment.getRevision());
        resource.setCreatedOn(existing.getCreatedOn());
        resource.setModifiedOn(getModifiedOn());
        sanitizeResource(resource);
        
        Validate.entityThrowingException(INSTANCE, resource);
        
        AssessmentResource retValue = dao.saveResource(appId, assessmentId, resource);
        retValue.setUpToDate(true);
        return retValue;
    }
    
    public void deleteResource(String appId, String assessmentId, String guid) {
        checkArgument(isNotBlank(appId));
        checkArgument(isNotBlank(assessmentId));
        checkArgument(isNotBlank(guid));
        
        // Verify access to this.
        Assessment assessment = assessmentService.getLatestAssessment(appId, assessmentId);
        IS_ORG_MEMBER.checkAndThrow("orgId", assessment.getOwnerId());
        
        AssessmentResource resource = dao.getResource(appId, guid)
                .orElseThrow(() -> new EntityNotFoundException(AssessmentResource.class));
        resource.setDeleted(true);
        resource.setModifiedOn(getModifiedOn());
        
        dao.saveResource(appId, assessmentId, resource);
    }
    
    // Only admins can call this.
    public void deleteResourcePermanently(String appId, String assessmentId, String guid) {
        checkArgument(isNotBlank(appId));
        checkArgument(isNotBlank(assessmentId));
        checkArgument(isNotBlank(guid));
        
        Optional<AssessmentResource> opt = dao.getResource(appId, guid);
        if (opt.isPresent()) {
            dao.deleteResource(appId, opt.get());
        }
    }
    
    public List<AssessmentResource> importAssessmentResources(String appId, String assessmentId, Set<String> guids) {
        checkArgument(isNotBlank(appId));
        checkArgument(isNotBlank(assessmentId));
        
        // Must have imported the assessment already before you move resources
        Assessment assessment = assessmentService.getLatestAssessment(appId, assessmentId);
        // Cannot import a resource unless you are member of the org that owns the assessment
        IS_ORG_MEMBER.checkAndThrow("orgId", assessment.getOwnerId());
        return copyResources(SHARED_APP_ID, appId, assessment, guids);
    }
    
    public List<AssessmentResource> publishAssessmentResources(String appId, String assessmentId, Set<String> guids) {
        checkArgument(isNotBlank(appId));
        checkArgument(isNotBlank(assessmentId));
        
        // Must have published the assessment already before you move resources
        Assessment assessment = assessmentService.getLatestAssessment(SHARED_APP_ID, assessmentId);
        // Cannot publish a resource unless you are member of the org that owns the shared assessment
        IS_ORG_MEMBER_IN_APP.checkAndThrow("ownerId", assessment.getOwnerId());
        
        return copyResources(appId, SHARED_APP_ID, assessment, guids);
    }
    
    List<AssessmentResource> copyResources(String originId, String targetId, Assessment assessment, Set<String> guids) {
        checkArgument(isNotBlank(originId));
        checkArgument(isNotBlank(targetId));
        
        if (guids == null || guids.isEmpty()) {
            throw new BadRequestException("Must specify one or more resource GUIDs");
        }
        DateTime timestamp = getCreatedOn();
        
        List<AssessmentResource> resources = new ArrayList<>();
        for (String oneGuid : guids) {
            AssessmentResource origin = dao.getResource(originId, oneGuid)
                    .orElseThrow(() -> new EntityNotFoundException(AssessmentResource.class));
            
            AssessmentResource target = dao.getResource(targetId, oneGuid).orElse(null);
            
            origin.setModifiedOn(timestamp);
            origin.setDeleted(false);
            origin.setCreatedAtRevision(assessment.getRevision());
            if (target == null) {
                origin.setCreatedOn(timestamp);
                origin.setVersion(0L);
            } else {
                origin.setVersion(target.getVersion());
            }
            resources.add(origin);
        }
        return dao.saveResources(targetId, assessment.getIdentifier(), resources);
    }
    
    static void sanitizeResource(AssessmentResource resource) {
        resource.setTitle(sanitizeHTML(simpleText(), resource.getTitle()));
        resource.setUrl(sanitizeHTML(simpleText(), resource.getUrl()));
        resource.setFormat(sanitizeHTML(simpleText(), resource.getFormat()));
        resource.setDate(sanitizeHTML(simpleText(), resource.getDate()));
        resource.setDescription(sanitizeHTML(simpleText(), resource.getDescription()));
        resource.setLanguage(sanitizeHTML(simpleText(), resource.getLanguage()));
        if (resource.getContributors() != null) {
            resource.setContributors(resource.getContributors().stream()
                    .map(AssessmentResourceService::sanitize).collect(toList())); 
        }
        if (resource.getCreators() != null) {
            resource.setCreators(resource.getCreators().stream()
                    .map(AssessmentResourceService::sanitize).collect(toList())); 
        }
        if (resource.getPublishers() != null) {
            resource.setPublishers(resource.getPublishers().stream()
                    .map(AssessmentResourceService::sanitize).collect(toList())); 
        }
    }
    
    // for some reason, stream map() can't call this directly
    static String sanitize(String value) {
        return sanitizeHTML(simpleText(), value);
    }
}
