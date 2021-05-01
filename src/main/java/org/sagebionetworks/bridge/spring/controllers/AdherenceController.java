package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.STUDY_COORDINATOR;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordList;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordsSearch;
import org.sagebionetworks.bridge.services.AdherenceService;

@CrossOrigin
@RestController
public class AdherenceController extends BaseController {
    
    static final StatusMessage SAVED_MSG = new StatusMessage("Adherence records saved.");
    
    private AdherenceService service;

    @Autowired
    final void setAdherenceService(AdherenceService service) {
        this.service = service;
    }
    
    @PostMapping("/v5/studies/{studyId}/participants/self/adherence")
    public StatusMessage updateAdherenceRecords(@PathVariable String studyId) {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        AdherenceRecordList recordsList = parseJson(AdherenceRecordList.class);
        for (AdherenceRecord oneRecord : recordsList.getRecords()) {
            oneRecord.setUserId(session.getId());
            oneRecord.setStudyId(studyId);
        }
        service.updateAdherenceRecords(session.getAppId(), session.getHealthCode(), recordsList);
        return SAVED_MSG;
    }
    
    @PostMapping("/v5/studies/{studyId}/participants/self/adherence/search")
    public PagedResourceList<AdherenceRecord> searchForAdherenceRecordsForSelf(@PathVariable String studyId) {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        AdherenceRecordsSearch payload = parseJson(AdherenceRecordsSearch.class);
        
        AdherenceRecordsSearch search = new AdherenceRecordsSearch.Builder()
                .copyOf(payload)
                .withUserId(session.getId())
                .withStudyId(studyId).build();
        
        return service.getAdherenceRecords(session.getAppId(), session.getHealthCode(), search);
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
        
        String healthCode = accountService.getHealthCodeForAccount(
                AccountId.forId(session.getAppId(), userId));
        
        return service.getAdherenceRecords(session.getAppId(), healthCode, search);
    }
}
