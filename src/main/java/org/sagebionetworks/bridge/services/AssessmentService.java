package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.jsoup.safety.Whitelist.none;
import static org.jsoup.safety.Whitelist.simpleText;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.PAGE_SIZE_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.SHARED_STUDY_ID_STRING;
import static org.sagebionetworks.bridge.BridgeUtils.sanitizeHTML;
import static org.sagebionetworks.bridge.models.ResourceList.CATEGORIES;
import static org.sagebionetworks.bridge.models.ResourceList.IDENTIFIER;
import static org.sagebionetworks.bridge.models.ResourceList.INCLUDE_DELETED;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_BY;
import static org.sagebionetworks.bridge.models.ResourceList.PAGE_SIZE;
import static org.sagebionetworks.bridge.models.ResourceList.TAGS;
import static org.sagebionetworks.bridge.util.BridgeCollectors.toImmutableSet;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.AssessmentDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.Tag;
import org.sagebionetworks.bridge.models.assessments.Assessment;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.validators.AssessmentValidator;
import org.sagebionetworks.bridge.validators.Validate;

@Component
public class AssessmentService {

    private AssessmentDao dao;
    
    private SubstudyService substudyService;
    
    @Autowired
    final void setAssessmentDao(AssessmentDao assessmentDao) {
        this.dao = assessmentDao;
    }
    
    @Autowired
    final void setSubstudyService(SubstudyService substudyService) {
        this.substudyService = substudyService;
    }
    
    // accessor to mock for tests
    String generateGuid() {
        return BridgeUtils.generateGuid();
    }

    // accessor to mock for tests
    DateTime getTimestamp() {
        return DateTime.now();
    }
    
    public PagedResourceList<Assessment> getAssessments(String appId, int offsetBy, int pageSize,
            Set<String> categories, Set<String> tags, boolean includeDeleted) {
        checkArgument(isNotBlank(appId));
        
        if (offsetBy < 0) {
            throw new BadRequestException("offsetBy cannot be negative");
        }
        if (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(PAGE_SIZE_ERROR);
        }
        return dao.getAssessments(appId, offsetBy, pageSize, categories, tags, includeDeleted)
                .withRequestParam(OFFSET_BY, offsetBy)
                .withRequestParam(PAGE_SIZE, pageSize)
                .withRequestParam(INCLUDE_DELETED, includeDeleted)
                .withRequestParam(CATEGORIES, categories)
                .withRequestParam(TAGS, tags);
    }
    
    public Assessment createAssessment(String appId, Assessment assessment) {
        checkArgument(isNotBlank(appId));
        checkNotNull(assessment);
        
        checkOwnership(substudyService, appId, assessment.getOwnerId());
        
        // If the identifier is missing, it will be a validation error.
        if (assessment.getIdentifier() != null) {
            // If an assessment under this identifier exists, use the revisions API. We want to 
            // warn people when they are unintentionally stomping on an existing identifier.
            Optional<Assessment> opt = getLatestInternal(appId, assessment.getIdentifier(), true);
            if (opt.isPresent()) {
                Assessment e = opt.get();
                Map<String,Object> map = new ImmutableMap.Builder<String,Object>()
                        .put("identifier", e.getIdentifier())
                        .put("revision", e.getRevision()).build();
                throw new EntityAlreadyExistsException(Assessment.class, 
                        "Assessment with this identifier exists, revision=" + e.getRevision() + 
                        ", deleted="+e.isDeleted(), map);
            }
        }
        return createAssessmentInternal(appId, assessment);
    }
    
    public Assessment createAssessmentRevision(String appId, Assessment assessment) {
        checkArgument(isNotBlank(appId));
        checkNotNull(assessment);
        
        checkOwnership(substudyService, appId, assessment.getOwnerId());
        
        // This will throw an exception if there's no assessment under this revision
        getLatestAssessment(appId, assessment.getIdentifier());
        
        return createAssessmentInternal(appId, assessment);
    }
        
    public Assessment updateAssessment(String appId, Assessment assessment) {
        checkArgument(isNotBlank(appId));
        checkNotNull(assessment);
        
        Assessment existing = dao.getAssessment(appId, assessment.getGuid())
                .orElseThrow(() -> new EntityNotFoundException(Assessment.class));
        
        if (existing.isDeleted() && assessment.isDeleted()) {
            throw new EntityNotFoundException(Assessment.class);
        }
        checkOwnership(substudyService, appId, existing.getOwnerId());
        
        assessment.setAppId(appId);
        assessment.setIdentifier(existing.getIdentifier());
        assessment.setOwnerId(existing.getOwnerId());
        assessment.setOriginGuid(existing.getOriginGuid());
        assessment.setCreatedOn(existing.getCreatedOn());
        DateTime timestamp = getTimestamp();
        assessment.setModifiedOn(timestamp);
        sanitizeAssessment(assessment);
       
        AssessmentValidator validator = new AssessmentValidator(Optional.empty());
        Validate.entityThrowingException(validator, assessment);
        
        return dao.updateAssessment(assessment);
    }
    
    public Assessment updateSharedAssessment(String callerAppId, Assessment assessment) {
        checkArgument(isNotBlank(callerAppId));
        checkNotNull(assessment);
        
        assessment.setAppId(SHARED_STUDY_ID_STRING);
        
        Assessment existing = dao.getAssessment(SHARED_STUDY_ID_STRING, assessment.getGuid())
                .orElseThrow(() -> new EntityNotFoundException(Assessment.class));
        
        // We need to verify that the caller has permission to operate on this shared object.
        String[] parts = existing.getOwnerId().split(":");
        String originAppId = parts[0];
        String originOrgId = parts[1];
        
        Set<String> callerSubstudies = BridgeUtils.getRequestContext().getCallerSubstudies();
        if (!callerAppId.equals(originAppId) || !callerSubstudies.contains(originOrgId)) {
            throw new UnauthorizedException();
            
        }
        return updateAssessment(SHARED_STUDY_ID_STRING, assessment);
    }
        
    public Assessment getAssessmentByGuid(String appId, String guid) {
        checkArgument(isNotBlank(appId));
        checkArgument(isNotBlank(guid));
        
        return dao.getAssessment(appId, guid)
                .orElseThrow(() -> new EntityNotFoundException(Assessment.class));
    }
        
    public Assessment getAssessmentById(String appId, String identifier, int revision) {
        checkArgument(isNotBlank(appId));
        checkArgument(isNotBlank(identifier));
        
        if (revision < 1) {
            throw new BadRequestException("offsetBy must be positive integer");
        }
        return dao.getAssessment(appId, identifier, revision)
                .orElseThrow(() -> new EntityNotFoundException(Assessment.class));
    }
        
    public Assessment getLatestAssessment(String appId, String identifier) {
        checkArgument(isNotBlank(appId));
        checkArgument(isNotBlank(identifier));
        
        return getLatestInternal(appId, identifier, false)
            .orElseThrow(() -> new EntityNotFoundException(Assessment.class));
    }
        
    public PagedResourceList<Assessment> getAssessmentRevisionsById(
        String appId, String identifier, int offsetBy, int pageSize, boolean includeDeleted) {
        checkArgument(isNotBlank(appId));
        
        if (isBlank(identifier)) {
            throw new BadRequestException("identifier required");
        }
        if (offsetBy < 0) {
            throw new BadRequestException("offsetBy cannot be negative");
        }
        if (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(PAGE_SIZE_ERROR);
        }
        PagedResourceList<Assessment> page = dao.getAssessmentRevisions(
                appId, identifier, offsetBy, pageSize, includeDeleted);
        if (page.getItems().isEmpty()) {
            throw new EntityNotFoundException(Assessment.class);
        }
        return page.withRequestParam(IDENTIFIER, identifier)
                .withRequestParam(OFFSET_BY, offsetBy)
                .withRequestParam(PAGE_SIZE, pageSize)
                .withRequestParam(INCLUDE_DELETED, includeDeleted);
    }
    
    public PagedResourceList<Assessment> getAssessmentRevisionsByGuid(String appId, 
            String guid, int offsetBy, int pageSize, boolean includeDeleted) {
            checkArgument(isNotBlank(appId));
            
       Assessment assessment = getAssessmentByGuid(appId, guid);
       return getAssessmentRevisionsById(appId, assessment.getIdentifier(), 
               offsetBy, pageSize, includeDeleted);
    }

    /**
     * Takes a reference to an assessment in the caller's context (appId), and creates an 
     * assessment in the shared context. Stores the full substudy identifier (appId + 
     * substudyId) as the "ownerId" in shared context for authorization checks on 
     * operations in the shared context (only owners can edit their shared assessments).
     * The caller must be associated to the organization that own's the assessment 
     * locally.
     */
    public Assessment publishAssessment(String appId, String guid) {
        checkArgument(isNotBlank(appId));
        checkArgument(isNotBlank(guid));
        
        Assessment assessmentToPublish = getAssessmentByGuid(appId, guid);
        
        checkOwnership(substudyService, appId, assessmentToPublish.getOwnerId());
        
        int revision = nextRevisionNumber(SHARED_STUDY_ID_STRING, assessmentToPublish.getIdentifier());
        assessmentToPublish.setGuid(generateGuid());
        assessmentToPublish.setRevision(revision);
        assessmentToPublish.setOriginGuid(null);
        assessmentToPublish.setAppId(SHARED_STUDY_ID_STRING);
        assessmentToPublish.setOwnerId(appId + ":" + assessmentToPublish.getOwnerId());
        assessmentToPublish.setVersion(0L);
        
        // Neither of these assessments should be in an attached state because both are loaded in 
        // separate transactions that are closed in the DAO.
        Assessment original = getAssessmentByGuid(appId, guid);
        original.setOriginGuid(assessmentToPublish.getGuid());
        // These collections have been proxied by Hibernate and it doesn't like it when you share 
        // to entities (Tag) in two collections. All of this is weird because we're not using Spring Boot's 
        // JPA support, including its support for transactions at the DAO or service layer. I may 
        // go back and work on this. Lots of code is coming with similar issues.
        if (original.getCategories() != null) {
            assessmentToPublish.setCategories(ImmutableSet.copyOf(original.getCategories()));    
        }
        if (original.getTags() != null) {
            assessmentToPublish.setTags(ImmutableSet.copyOf(original.getTags()));    
        }
        return dao.publishAssessment(original, assessmentToPublish);
    }
    
    /**
     * Takes an assessment in the shared context and clones it into the caller's local context, using the 
     * specified organization as the new owner of the local assessment (must be an organization the caller
     * is associated to). If the identifier already exists in this study, the revision number is adjusted
     * appropriately. The origin GUID is set to the GUID of the shared assessment.
     */
    public Assessment importAssessment(String appId, String ownerId, String guid) {
        checkArgument(isNotBlank(appId));
        checkArgument(isNotBlank(guid));
        
        if (isBlank(ownerId)) {
            throw new BadRequestException("ownerId parameter is required");
        }
        checkOwnership(substudyService, appId, ownerId);

        Assessment assessment = getAssessmentByGuid(SHARED_STUDY_ID_STRING, guid);
        
        // Figure out what revision this should be in the new app context if the identifier already exists
        int revision = nextRevisionNumber(appId, assessment.getIdentifier());
        assessment.setRevision(revision);
        assessment.setOriginGuid(assessment.getGuid());
        assessment.setOwnerId(ownerId);
        
        return createAssessmentInternal(appId, assessment);
    }
        
    public void deleteAssessment(String appId, String guid) {
        checkArgument(isNotBlank(appId));
        checkArgument(isNotBlank(guid));
        
        Assessment assessment = getAssessmentByGuid(appId, guid);
        if (assessment.isDeleted()) {
            throw new EntityNotFoundException(Assessment.class);
        }
        checkOwnership(substudyService, appId, assessment.getOwnerId());
        
        assessment.setDeleted(true);
        assessment.setModifiedOn(getTimestamp());
        dao.updateAssessment(assessment);
    }
        
    public void deleteAssessmentPermanently(String appId, String guid) {
        checkArgument(isNotBlank(appId));
        checkArgument(isNotBlank(guid));
        
        Optional<Assessment> opt = dao.getAssessment(appId, guid);
        if (opt.isPresent()) {
            dao.deleteAssessment(opt.get());    
        }
    }

    private Assessment createAssessmentInternal(String appId, Assessment assessment) {
        checkArgument(isNotBlank(appId));
        checkNotNull(assessment);
        
        assessment.setGuid(generateGuid());
        assessment.setAppId(appId);
        DateTime timestamp = getTimestamp();
        assessment.setCreatedOn(timestamp);
        assessment.setModifiedOn(timestamp);
        assessment.setDeleted(false);
        sanitizeAssessment(assessment);

        Optional<Assessment> opt = dao.getAssessment(appId, assessment.getIdentifier(), assessment.getRevision());
        AssessmentValidator validator = new AssessmentValidator(opt);
        
        Validate.entityThrowingException(validator, assessment);
        
        return dao.createAssessment(assessment);
    }
    
    private Optional<Assessment> getLatestInternal(String appId, String identifier, boolean includeDeleted) {
        checkArgument(isNotBlank(appId));
        checkArgument(isNotBlank(identifier));
        
        PagedResourceList<Assessment> page = dao.getAssessmentRevisions(appId, identifier, 0, 1, includeDeleted);
        if (page.getItems().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(page.getItems().get(0));
    }

    /**
     * If the caller has no organizational membership, then they can set any organization (however they 
     * must set one, unlike the implementation of substudy relationships to user accounts). In this 
     * case we check the org ID to ensure it's valid. If the caller has organizational memberships, 
     * then the caller must be a member of the organization being cited. At that point we do not need 
     * to validate the org ID since it was validated when it was set as an organizational relationship
     * on the account. 
     */
    static void checkOwnership(SubstudyService subService, String appId, String ownerId) {
        if (isBlank(ownerId)) {
            return; // this will be prevented as a of validation error; the field is required.
        }
        Set<String> callerSubstudies = BridgeUtils.getRequestContext().getCallerSubstudies();
        if (callerSubstudies.isEmpty()) {
            StudyIdentifier app = new StudyIdentifierImpl(appId);
            if (subService.getSubstudy(app, ownerId, false) != null) {
                return;
            }
        } else if (callerSubstudies.contains(ownerId)) {
            return;
        }
        // This throws a very strange exception, maybe fix this.
        throw new UnauthorizedException("Assessment must be associated to one of the caller’s organizations.");
    }
    
    /**
     * Figure out what the next revision number should be in the target app context. Include deleted 
     * revisions since the database will enforce a constraint violation if you select any existing 
     * revision number.
     */
    private int nextRevisionNumber(String appId, String identifier) {
        Optional<Assessment> opt = getLatestInternal(appId, identifier, true);
        return opt.isPresent() ? (opt.get().getRevision()+1) : 1;
    }
    
    /**
     * Assessments are publicly available and thus, could be inserted as is into web applications/
     * sites. So we scrub HTML in all the user input fields, check others during validation 
     * (identifiers), and restrict user input into yet other fields (GUIDs).
     */
    static void sanitizeAssessment(Assessment assessment) {
        assessment.setTitle(sanitizeHTML(simpleText(), assessment.getTitle()));
        assessment.setSummary(sanitizeHTML(simpleText(), assessment.getSummary()));
        assessment.setValidationStatus(sanitizeHTML(simpleText(), assessment.getValidationStatus()));
        assessment.setNormingStatus(sanitizeHTML(simpleText(), assessment.getNormingStatus()));
        if (assessment.getCategories() != null) {
            assessment.setCategories( 
                assessment.getCategories().stream()
                    .map((tag) -> new Tag(sanitizeHTML(none(), tag.getValue()), tag.getCategory()))
                    .collect(toImmutableSet())
            );
        }
        if (assessment.getTags() != null) {
            assessment.setTags( 
                // Tag categories are never input by users and don't need to be sanitized.
                assessment.getTags().stream()
                    .map(tag -> new Tag(sanitizeHTML(none(), tag.getValue()), tag.getCategory()))
                    .collect(toImmutableSet())
            );
        }
        if (assessment.getCustomizationFields() != null) {
            Map<String, Set<String>> map = new HashMap<>();
            for (Map.Entry<String, Set<String>> entry : assessment.getCustomizationFields().entrySet()) {                
                String key = sanitizeHTML(none(), entry.getKey());
                Set<String> values = entry.getValue().stream()
                        .map(string -> sanitizeHTML(none(), string))
                        .collect(toImmutableSet());
                map.put(key, values);
            }
            assessment.setCustomizationFields(map);
        }
    }
}
