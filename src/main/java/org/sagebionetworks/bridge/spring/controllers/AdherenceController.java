package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.STUDY_COORDINATOR;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordsSearch;
import org.sagebionetworks.bridge.services.AdherenceService;

public class AdherenceController extends BaseController {
    
    static final StatusMessage CREATED_MSG = new StatusMessage("Adherence record created.");
    static final StatusMessage UPDATED_MSG = new StatusMessage("Adherence record update.");
    
    private AdherenceService service;

    @Autowired
    final void setAdherenceService(AdherenceService service) {
        this.service = service;
    }
    
    @PostMapping("/v5/studies/{studyId}/participants/self/adherence")
    @ResponseStatus(HttpStatus.CREATED)
    public StatusMessage createAdherenceRecord(@PathVariable String studyId) {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        AdherenceRecord record = parseJson(AdherenceRecord.class);
        record.setUserId(session.getId());
        record.setStudyId(studyId);
        
        service.createAdherenceRecord(record);
        return CREATED_MSG;
    }
    
    @PostMapping("/v5/studies/{studyId}/participants/self/adherence/{guid}")
    public StatusMessage updateAdherenceRecord(@PathVariable String studyId, @PathVariable String guid) {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        AdherenceRecord record = parseJson(AdherenceRecord.class);
        record.setUserId(session.getId());
        record.setStudyId(studyId);
        record.setGuid(guid);
        
        service.updateAdherenceRecord(record);
        return UPDATED_MSG;
    }
    
    @PostMapping("/v5/studies/{studyId}/participants/self/adherence/search")
    public PagedResourceList<AdherenceRecord> searchForSelfAdherenceRecords(@PathVariable String studyId) {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        AdherenceRecordsSearch payload = parseJson(AdherenceRecordsSearch.class);
        
        AdherenceRecordsSearch search = new AdherenceRecordsSearch.Builder()
                .copyOf(payload)
                .withUserId(session.getId())
                .withStudyId(studyId).build();
        
        return service.getAdherenceRecords(session.getAppId(), search);
    }
    
    @PostMapping("/v5/studies/{studyId}/participants/{userId}/adherence/search")
    public PagedResourceList<AdherenceRecord> searchForAdherenceRecords(@PathVariable String studyId,
            @PathVariable String userId) {
        UserSession session = getAuthenticatedSession(RESEARCHER, STUDY_COORDINATOR);
        
        AdherenceRecordsSearch payload = parseJson(AdherenceRecordsSearch.class);
        
        AdherenceRecordsSearch search = new AdherenceRecordsSearch.Builder()
                .copyOf(payload)
                .withUserId(userId)
                .withStudyId(studyId).build();
        
        return service.getAdherenceRecords(session.getAppId(), search);
    }
}
