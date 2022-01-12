package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.AuthEvaluatorField.STUDY_ID;
import static org.sagebionetworks.bridge.AuthUtils.CANNOT_ACCESS_PARTICIPANTS;
import static org.sagebionetworks.bridge.AuthUtils.CAN_READ_PARTICIPANT_REPORTS;
import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.STUDY_COORDINATOR;
import static org.sagebionetworks.bridge.Roles.STUDY_DESIGNER;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.sagebionetworks.bridge.AuthEvaluatorField;
import org.sagebionetworks.bridge.AuthUtils;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordList;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordsSearch;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamAdherenceReport;
import org.sagebionetworks.bridge.models.schedules2.adherence.weekly.WeeklyAdherenceReport;
import org.sagebionetworks.bridge.services.AdherenceService;

@CrossOrigin
@RestController
public class AdherenceController extends BaseController {
    
    static final StatusMessage SAVED_MSG = new StatusMessage("Adherence records saved.");
    static final StatusMessage DELETED_MSG = new StatusMessage("Adherence record deleted");
    
    private AdherenceService service;

    @Autowired
    final void setAdherenceService(AdherenceService service) {
        this.service = service;
    }
    
    protected DateTime getDateTime() {
        return DateTime.now();
    }
    
    @GetMapping("/v5/studies/{studyId}/participants/{userIdToken}/adherence/eventstream")
    public EventStreamAdherenceReport getEventStreamAdherenceReport(@PathVariable String studyId, 
            @PathVariable String userIdToken, @RequestParam(required = false) String datetime, 
            @RequestParam(required = false) String activeOnly) {
        UserSession session = getAuthenticatedSession(DEVELOPER, RESEARCHER, STUDY_DESIGNER, STUDY_COORDINATOR);

        AccountId accountId = BridgeUtils.parseAccountId(session.getAppId(), userIdToken);
        Account account = accountService.getAccount(accountId)
                .orElseThrow(() -> new EntityNotFoundException(Account.class));

        DateTime now = BridgeUtils.getDateTimeOrDefault(datetime, getDateTime());
        Boolean showActiveOnly = "true".equalsIgnoreCase(activeOnly);

        return service.getEventStreamAdherenceReport(session.getAppId(), studyId, account.getId(), now,
                account.getClientTimeZone(), showActiveOnly);
    }
    
    @GetMapping("/v5/studies/{studyId}/participants/self/adherence/eventstream")
    public EventStreamAdherenceReport getEventStreamAdherenceReportForSelf(@PathVariable String studyId,
            @RequestParam(required = false) String datetime, @RequestParam(required = false) String activeOnly) {
        UserSession session = getAuthenticatedAndConsentedSession();

        DateTime now = BridgeUtils.getDateTimeOrDefault(datetime, getDateTime());
        Boolean showActiveOnly = "true".equalsIgnoreCase(activeOnly);

        return service.getEventStreamAdherenceReport(session.getAppId(), studyId, session.getId(), now,
                session.getParticipant().getClientTimeZone(), showActiveOnly);
    }
    
    @GetMapping("/v5/studies/{studyId}/participants/{userIdToken}/adherence/weekly")
    public WeeklyAdherenceReport getWeeklyAdherenceReport(@PathVariable String studyId,
            @PathVariable String userIdToken) {
        UserSession session = getAuthenticatedSession(DEVELOPER, RESEARCHER, STUDY_DESIGNER, STUDY_COORDINATOR);

        AccountId accountId = BridgeUtils.parseAccountId(session.getAppId(), userIdToken);
        Account account = accountService.getAccount(accountId)
                .orElseThrow(() -> new EntityNotFoundException(Account.class));

        return service.getWeeklyAdherenceReport(session.getAppId(), studyId, account);
    }
    
    @GetMapping("/v5/studies/{studyId}/adherence/weekly")    
    public PagedResourceList<WeeklyAdherenceReport> getWeeklyAdherenceReports(@PathVariable String studyId,
            @RequestParam(required = false) String labelFilter, 
            @RequestParam(required = false) String complianceUnder, 
            @RequestParam(required = false) String offsetBy, 
            @RequestParam(required = false) String pageSize) {
        UserSession session = getAuthenticatedSession(DEVELOPER, RESEARCHER, STUDY_DESIGNER, STUDY_COORDINATOR);
        
        CAN_READ_PARTICIPANT_REPORTS.checkAndThrow(STUDY_ID, studyId);
        
        boolean testAccounts = CANNOT_ACCESS_PARTICIPANTS.check();
        System.out.println("testAccounts = " + testAccounts);

        // Security issues:
        // do need to secure these methods to people who are connected to the study
        // in this case, we need to record and to query in relation to whether or not it's a test user
        
        Integer offsetByInt = BridgeUtils.getIntegerOrDefault(offsetBy, 0);
        Integer pageSizeInt = BridgeUtils.getIntegerOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);
        Integer complianceUnderInt = BridgeUtils.getIntegerOrDefault(complianceUnder, null);
        
        return service.getWeeklyAdherenceReports(session.getAppId(), studyId, labelFilter, complianceUnderInt,
                offsetByInt, pageSizeInt);
    }
    
    @PostMapping("/v5/studies/{studyId}/participants/self/adherence")
    public StatusMessage updateAdherenceRecordsForSelf(@PathVariable String studyId) {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        AdherenceRecordList recordsList = parseJson(AdherenceRecordList.class);
        for (AdherenceRecord oneRecord : recordsList.getRecords()) {
            oneRecord.setAppId(session.getAppId());
            oneRecord.setUserId(session.getId());
            oneRecord.setStudyId(studyId);
        }
        service.updateAdherenceRecords(session.getAppId(), recordsList);
        return SAVED_MSG;
    }
    
    @PostMapping("/v5/studies/{studyId}/participants/{userIdToken}/adherence")
    public StatusMessage updateAdherenceRecords(@PathVariable String studyId, @PathVariable String userIdToken) {
        UserSession session = getAuthenticatedSession(DEVELOPER, RESEARCHER, STUDY_DESIGNER, STUDY_COORDINATOR);
        
        String userId = accountService.getAccountId(session.getAppId(), userIdToken)
                .orElseThrow(() -> new EntityNotFoundException(Account.class));
        
        AdherenceRecordList recordsList = parseJson(AdherenceRecordList.class);
        for (AdherenceRecord oneRecord : recordsList.getRecords()) {
            oneRecord.setAppId(session.getAppId());
            oneRecord.setUserId(userId);
            oneRecord.setStudyId(studyId);
        }
        service.updateAdherenceRecords(session.getAppId(), recordsList);
        return SAVED_MSG;
    }
    
    @PostMapping("/v5/studies/{studyId}/participants/self/adherence/search")
    public PagedResourceList<AdherenceRecord> searchForAdherenceRecordsForSelf(@PathVariable String studyId) {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        AdherenceRecordsSearch payload = parseJson(AdherenceRecordsSearch.class);
        
        AdherenceRecordsSearch search = payload.toBuilder()
                .withUserId(session.getId())
                .withStudyId(studyId).build();
        
        return service.getAdherenceRecords(session.getAppId(), search);
    }
    
    @PostMapping("/v5/studies/{studyId}/participants/{userIdToken}/adherence/search")
    public PagedResourceList<AdherenceRecord> searchForAdherenceRecords(@PathVariable String studyId,
            @PathVariable String userIdToken) {
        UserSession session = getAuthenticatedSession(DEVELOPER, RESEARCHER, STUDY_DESIGNER, STUDY_COORDINATOR);
        
        AdherenceRecordsSearch payload = parseJson(AdherenceRecordsSearch.class);
        String userId = accountService.getAccountId(session.getAppId(), userIdToken)
                .orElseThrow(() -> new EntityNotFoundException(Account.class));
        
        AdherenceRecordsSearch search = payload.toBuilder()
                .withUserId(userId)
                .withStudyId(studyId).build();

        return service.getAdherenceRecords(session.getAppId(), search);
    }

    @DeleteMapping("/v5/studies/{studyId}/participants/{userIdToken}/adherence/{instanceGuid}/{eventTimestamp}/{startedOn}")
    public StatusMessage deleteAdherenceRecord(
            @PathVariable String studyId,
            @PathVariable String userIdToken,
            @PathVariable String instanceGuid,
            @PathVariable String eventTimestamp,
            @PathVariable String startedOn) {
        UserSession session = getAuthenticatedSession(DEVELOPER, RESEARCHER, STUDY_DESIGNER, STUDY_COORDINATOR);

        String userId = accountService.getAccountId(session.getAppId(), userIdToken)
                .orElseThrow(() -> new EntityNotFoundException(Account.class));

        AdherenceRecord record = new AdherenceRecord();
        record.setAppId(session.getAppId());
        record.setUserId(userId);
        record.setStudyId(studyId);
        record.setInstanceGuid(instanceGuid);
        record.setEventTimestamp(BridgeUtils.getDateTimeOrDefault(eventTimestamp, null));
        record.setStartedOn(BridgeUtils.getDateTimeOrDefault(startedOn, null));

        service.deleteAdherenceRecord(record);
        return DELETED_MSG;
    }
}
