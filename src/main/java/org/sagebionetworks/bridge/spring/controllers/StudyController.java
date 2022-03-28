package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.AuthEvaluatorField.STUDY_ID;
import static org.sagebionetworks.bridge.AuthUtils.CAN_UPDATE_STUDIES;
import static org.sagebionetworks.bridge.AuthUtils.CAN_READ_STUDIES;
import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.ONE_DAY_IN_SECONDS;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.ORG_ADMIN;
import static org.sagebionetworks.bridge.Roles.STUDY_COORDINATOR;
import static org.sagebionetworks.bridge.Roles.STUDY_DESIGNER;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.models.files.FileDispositionType.INLINE;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.files.FileMetadata;
import org.sagebionetworks.bridge.models.files.FileRevision;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.FileService;
import org.sagebionetworks.bridge.services.StudyService;

@CrossOrigin
@RestController
public class StudyController extends BaseController {

    static final StatusMessage DELETED_MSG = new StatusMessage("Study deleted.");
    
    private StudyService service;
    
    private FileService fileService;

    @Autowired
    final void setStudyService(StudyService studyService) {
        this.service = studyService;
    }
    
    @Autowired
    final void setFileService(FileService fileService) {
        this.fileService = fileService;
    }
    
    @GetMapping(path = {"/v5/studies", "/v3/substudies"})
    public PagedResourceList<Study> getStudies(
            @RequestParam(required = false) String offsetBy, 
            @RequestParam(required = false) String pageSize,            
            @RequestParam(defaultValue = "false") boolean includeDeleted) {
        UserSession session = getAdministrativeSession();

        int offsetByInt = BridgeUtils.getIntOrDefault(offsetBy, 0);
        int pageSizeInt = BridgeUtils.getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);

        return service.getStudies(session.getAppId(), offsetByInt, pageSizeInt, includeDeleted);
    }

    @PostMapping(path = {"/v5/studies", "/v3/substudies"})
    @ResponseStatus(HttpStatus.CREATED)
    public VersionHolder createStudy() {
        UserSession session = getAuthenticatedSession(STUDY_DESIGNER, STUDY_COORDINATOR, ORG_ADMIN);

        // we don't check if the study coordinator is member of the study because it doesn't
        // exist yet. If the caller is in an organization, that organization will sponsor the
        // created study.
        Study study = parseJson(Study.class);
        
        return service.createStudy(session.getAppId(), study, true);
    }

    @GetMapping(path = {"/v5/studies/{id}", "/v3/substudies/{id}"})
    public Study getStudy(@PathVariable String id) {
        UserSession session = getAuthenticatedSession();
        
        Study study = service.getStudy(session.getAppId(), id, true);
        CAN_READ_STUDIES.checkAndThrow(STUDY_ID, id);
        
        return study;
    }

    @PostMapping(path = {"/v5/studies/{id}", "/v3/substudies/{id}"})
    public VersionHolder updateStudy(@PathVariable String id) {
        UserSession session = getAdministrativeSession();
        
        CAN_UPDATE_STUDIES.checkAndThrow(STUDY_ID, id);

        Study study = parseJson(Study.class);
        study.setIdentifier(id);
        
        return service.updateStudy(session.getAppId(), study);
    }

    @DeleteMapping(path = {"/v5/studies/{id}", "/v3/substudies/{id}"})
    public StatusMessage deleteStudy(@PathVariable String id,
            @RequestParam(defaultValue = "false") String physical) {
        UserSession session = getAuthenticatedSession(STUDY_DESIGNER, DEVELOPER);
        
        if ("true".equals(physical)) {
            service.deleteStudyPermanently(session.getAppId(), id);
        } else {
            service.deleteStudy(session.getAppId(), id);
        }
        return DELETED_MSG;
    }
    
    @PostMapping("/v5/studies/{id}/logo")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public FileRevision createStudyLogo(@PathVariable String id) {
        UserSession session = getAdministrativeSession();
        
        Study study = service.getStudy(session.getAppId(), id, true);
        CAN_UPDATE_STUDIES.checkAndThrow(STUDY_ID, id);
        
        FileMetadata metadata = null;
        if (study.getLogoGuid() != null) {
            metadata = fileService.getFile(session.getAppId(), study.getLogoGuid());
        } else {
            metadata = new FileMetadata();
            metadata.setName(study.getName() + " Logo");
            metadata.setAppId(session.getAppId());
            metadata.setDisposition(INLINE);
            metadata = fileService.createFile(session.getAppId(), metadata);
            study.setLogoGuid(metadata.getGuid());
            service.updateStudy(session.getAppId(), study);
        }
        
        FileRevision revision = parseJson(FileRevision.class);
        revision.setFileGuid(metadata.getGuid());
        
        return fileService.createFileRevision(session.getAppId(), revision);
    }
    
    @PostMapping("/v5/studies/{id}/logo/{createdOn}")
    @ResponseStatus(HttpStatus.CREATED)
    public Study finishStudyLogo(@PathVariable String id, @PathVariable("createdOn") String createdOnStr) {
        UserSession session = getAdministrativeSession();
        
        Study study = service.getStudy(session.getAppId(), id, true);
        CAN_UPDATE_STUDIES.checkAndThrow(STUDY_ID, id);
        
        String guid = study.getLogoGuid();
        if (guid == null) {
            throw new BadRequestException("Study logo upload must be started before it can be finished.");
        }
        DateTime createdOn = DateTime.parse(createdOnStr);
        fileService.finishFileRevision(session.getAppId(), guid, createdOn);
        
        FileRevision revision = fileService.getFileRevision(guid, createdOn)
                .orElseThrow(() -> new EntityNotFoundException(FileRevision.class));
        
        study.setStudyLogoUrl(revision.getDownloadURL());
        service.updateStudy(session.getAppId(), study);
        
        return study;
    }

    // This exists because apps want to get rudimentary study data to show participants before they've created their
    // account. For the worker API, see getStudyForWorker() below.
    @GetMapping(path = "/v1/apps/{appId}/studies/{studyId}", produces = { APPLICATION_JSON_UTF8_VALUE })
    public String getStudyForApp(@PathVariable String appId, @PathVariable String studyId)
            throws JsonProcessingException {
        CacheKey key = CacheKey.publicStudy(appId, studyId);
        String json = cacheProvider.getObject(key, String.class);
        if (json == null) {
            appService.getApp(appId);
            Study study = service.getStudy(appId, studyId, true);
            json = Study.STUDY_SUMMARY_WRITER.writeValueAsString(study);
            cacheProvider.setObject(key, json, ONE_DAY_IN_SECONDS);
        }
        return json;
    }

    @GetMapping("/v2/apps/{appId}/studies/{studyId}")
    public Study getStudyForWorker(@PathVariable String appId, @PathVariable String studyId) {
        getAuthenticatedSession(WORKER);
        // This throws a 404 with the correct error message if the app doesn't exist.
        appService.getApp(appId);
        return service.getStudy(appId, studyId, true);
    }

    @GetMapping(path = "/v1/apps/{appId}/studies")
    public PagedResourceList<Study> getAppStudiesForWorker(@PathVariable String appId,
            @RequestParam(required = false) String offsetBy, 
            @RequestParam(required = false) String pageSize,
            @RequestParam(required = false) String includeDeleted) {
        getAuthenticatedSession(WORKER);
        
        int offsetByInt = BridgeUtils.getIntOrDefault(offsetBy, 0);
        int pageSizeInt = BridgeUtils.getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);
        boolean incDeletedBool = Boolean.valueOf(includeDeleted);
        
        return service.getStudies(appId, offsetByInt, pageSizeInt, incDeletedBool);
    }

    @PostMapping("/v5/studies/{studyId}/design")
    public Study design(@PathVariable String studyId) {
        UserSession session = getAdministrativeSession();
        
        return service.transitionToDesign(session.getAppId(), studyId);
    }
    
    @PostMapping("/v5/studies/{studyId}/recruit")
    public Study recruitment(@PathVariable String studyId) {
        UserSession session = getAdministrativeSession();
        
        return service.transitionToRecruitment(session.getAppId(), studyId);
    }

    @PostMapping("/v5/studies/{studyId}/conduct")
    public Study closeEnrollment(@PathVariable String studyId) {
        UserSession session = getAdministrativeSession();
        
        return service.transitionToInFlight(session.getAppId(), studyId);
    }
    
    @PostMapping("/v5/studies/{studyId}/analyze")
    public Study analysis(@PathVariable String studyId) { 
        UserSession session = getAdministrativeSession();
        
        return service.transitionToAnalysis(session.getAppId(), studyId);
    }
    
    @PostMapping("/v5/studies/{studyId}/complete")
    public Study completed(@PathVariable String studyId) { 
        UserSession session = getAdministrativeSession();
        
        return service.transitionToCompleted(session.getAppId(), studyId);
    }
    
    @PostMapping("/v5/studies/{studyId}/withdraw")
    public Study withdrawn(@PathVariable String studyId) {
        UserSession session = getAdministrativeSession();
       
        return service.transitionToWithdrawn(session.getAppId(), studyId);
    }
}