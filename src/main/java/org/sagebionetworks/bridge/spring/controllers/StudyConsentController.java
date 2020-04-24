package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.DEVELOPER;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.App;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsent;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentForm;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentView;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.StudyConsentService;
import org.sagebionetworks.bridge.services.SubpopulationService;
import org.sagebionetworks.bridge.time.DateUtils;

@CrossOrigin
@RestController
public class StudyConsentController extends BaseController {

    private StudyConsentService studyConsentService;
    
    private SubpopulationService subpopService;

    @Autowired
    final void setStudyConsentService(StudyConsentService studyConsentService) {
        this.studyConsentService = studyConsentService;
    }

    @Autowired
    final void setSubpopulationService(SubpopulationService subpopService) {
        this.subpopService = subpopService;
    }

    // V1 API: consents directly associated to a study
    @Deprecated
    @GetMapping("/v3/consents")
    public ResourceList<StudyConsent> getAllConsents() {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        return getAllConsentsV2(session.getAppId());
    }

    @Deprecated
    @GetMapping("/v3/consents/published")
    public StudyConsentView getActiveConsent() {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        return getActiveConsentV2(session.getAppId());
    }
    
    @Deprecated
    @GetMapping("/v3/consents/recent")
    public StudyConsentView getMostRecentConsent() {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        return getMostRecentConsentV2(session.getAppId());
    }

    @Deprecated
    @GetMapping("/v3/consents/{createdOn}")
    public StudyConsentView getConsent(@PathVariable String createdOn) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        return getConsentV2(session.getAppId(), createdOn);
    }
    
    @Deprecated
    @PostMapping("/v3/consents")
    @ResponseStatus(HttpStatus.CREATED)
    public StudyConsentView addConsent() {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        return addConsentV2(session.getAppId());
    }

    @Deprecated
    @PostMapping("/v3/consents/{createdOn}/publish")
    public StatusMessage publishConsent(@PathVariable String createdOn) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        return publishConsentV2(session.getAppId(), createdOn);
    }
    
    // V2: consents associated to a subpopulation
    
    @GetMapping("/v3/subpopulations/{guid}/consents")
    public ResourceList<StudyConsent> getAllConsentsV2(@PathVariable String guid) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        String studyId = session.getAppId();
        SubpopulationGuid subpopGuid = SubpopulationGuid.create(guid);
        
        // Throws 404 exception if this subpopulation is not part of the caller's study
        subpopService.getSubpopulation(studyId, subpopGuid);
        
        List<StudyConsent> consents = studyConsentService.getAllConsents(subpopGuid);
        return new ResourceList<>(consents);
    }

    @GetMapping("/v3/subpopulations/{guid}/consents/published")
    public StudyConsentView getActiveConsentV2(@PathVariable String guid) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        String studyId = session.getAppId();
        SubpopulationGuid subpopGuid = SubpopulationGuid.create(guid);
        
        // Throws 404 exception if this subpopulation is not part of the caller's study
        Subpopulation subpop = subpopService.getSubpopulation(studyId, subpopGuid);
        
        return studyConsentService.getActiveConsent(subpop);
    }
    
    @GetMapping("/v3/subpopulations/{guid}/consents/recent")
    public StudyConsentView getMostRecentConsentV2(@PathVariable String guid) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        String studyId = session.getAppId();
        SubpopulationGuid subpopGuid = SubpopulationGuid.create(guid);
        
        // Throws 404 exception if this subpopulation is not part of the caller's study
        subpopService.getSubpopulation(studyId, subpopGuid);
        
        return studyConsentService.getMostRecentConsent(subpopGuid);
    }

    @GetMapping("/v3/subpopulations/{guid}/consents/{createdOn}")
    public StudyConsentView getConsentV2(@PathVariable String guid, @PathVariable String createdOn) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        String studyId = session.getAppId();
        SubpopulationGuid subpopGuid = SubpopulationGuid.create(guid);
        
        // Throws 404 exception if this subpopulation is not part of the caller's study
        subpopService.getSubpopulation(studyId, subpopGuid);

        long timestamp = DateUtils.convertToMillisFromEpoch(createdOn);
        return studyConsentService.getConsent(subpopGuid, timestamp);
    }
    
    @PostMapping("/v3/subpopulations/{guid}/consents")
    @ResponseStatus(HttpStatus.CREATED)
    public StudyConsentView addConsentV2(@PathVariable String guid) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        String studyId = session.getAppId();
        SubpopulationGuid subpopGuid = SubpopulationGuid.create(guid);
        
        // Throws 404 exception if this subpopulation is not part of the caller's study
        subpopService.getSubpopulation(studyId, subpopGuid);

        StudyConsentForm form = parseJson(StudyConsentForm.class);
        return studyConsentService.addConsent(subpopGuid, form);
    }
    
    @PostMapping("/v3/subpopulations/{guid}/consents/{createdOn}/publish")
    public StatusMessage publishConsentV2(@PathVariable String guid, @PathVariable String createdOn) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        App app = studyService.getStudy(session.getAppId());
        SubpopulationGuid subpopGuid = SubpopulationGuid.create(guid);
        
        // Throws 404 exception if this subpopulation is not part of the caller's study
        Subpopulation subpop = subpopService.getSubpopulation(app.getIdentifier(), subpopGuid);

        long timestamp = DateUtils.convertToMillisFromEpoch(createdOn);
        studyConsentService.publishConsent(app, subpop, timestamp);
        return new StatusMessage("Consent document set as active.");
    }
}
