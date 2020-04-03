package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeUtils.getDateTimeOrDefault;
import static org.sagebionetworks.bridge.BridgeUtils.getIntOrDefault;
import static org.sagebionetworks.bridge.BridgeUtils.getLocalDateOrDefault;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.WORKER;

import com.fasterxml.jackson.databind.JsonNode;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
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

import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.DateRangeResourceList;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.ReportTypeResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.reports.ReportData;
import org.sagebionetworks.bridge.models.reports.ReportDataKey;
import org.sagebionetworks.bridge.models.reports.ReportIndex;
import org.sagebionetworks.bridge.models.reports.ReportType;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.services.ReportService;

/**
 * <p>Permissions for participant reports are more complicated than other controllers:</p>
 * 
 * <p><b>Participant Reports</b></p>
 * <ul>
 *   <li>any authenticated user can get the participant identifiers (indices)</li>
 *   <li>user or researcher can see reports (for user, only self report)</li>
 *   <li>developers/workers can add/delete</li>
 * </ul>
 */
@CrossOrigin
@RestController
public class ParticipantReportController extends BaseController {
    
    private ReportService reportService;
    
    @Autowired
    final void setReportService(ReportService reportService) {
        this.reportService = reportService;
    }
    
    @GetMapping("/v3/users/self/reports/{identifier}")
    public DateRangeResourceList<? extends ReportData> getParticipantReportForSelf(@PathVariable String identifier,
            @RequestParam(required = false) String startDate, @RequestParam(required = false) String endDate) {
        UserSession session = getAuthenticatedSession();

        LocalDate localStartDate = getLocalDateOrDefault(startDate, null);
        LocalDate localEndDate = getLocalDateOrDefault(endDate, null);
        
        return reportService.getParticipantReport(session.getStudyIdentifier(), identifier, session.getHealthCode(),
                localStartDate, localEndDate);
    }

    @GetMapping("/v4/users/self/reports/{identifier}")
    public ForwardCursorPagedResourceList<ReportData> getParticipantReportForSelfV4(@PathVariable String identifier,
            @RequestParam(required = false) String startTime, @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String offsetKey, @RequestParam(required = false) String pageSize) {
        UserSession session = getAuthenticatedSession();

        DateTime startTimeObj = getDateTimeOrDefault(startTime, null);
        DateTime endTimeObj = getDateTimeOrDefault(endTime, null);
        int pageSizeInt = getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);
        
        return reportService.getParticipantReportV4(session.getStudyIdentifier(), identifier, session.getHealthCode(),
                startTimeObj, endTimeObj, offsetKey, pageSizeInt);
    }

    @PostMapping({"/v4/users/self/reports/{identifier}", "/v3/users/self/reports/{identifier}"})
    @ResponseStatus(HttpStatus.CREATED)
    public StatusMessage saveParticipantReportForSelf(@PathVariable String identifier) {
        UserSession session = getAuthenticatedSession();
        
        ReportData reportData = parseJson(ReportData.class);
        reportData.setKey(null); // set in service, but just so no future use depends on it
        
        reportService.saveParticipantReport(session.getStudyIdentifier(), identifier, 
                session.getHealthCode(), reportData);
        
        return new StatusMessage("Report data saved.");
    }
    
    /**
     * Get a list of the identifiers used for participant reports in this study.
     */
    @GetMapping("/v3/participants/reports")
    public ReportTypeResourceList<? extends ReportIndex> listParticipantReportIndices() {
        UserSession session = getAuthenticatedSession();
        
        return reportService.getReportIndices(session.getStudyIdentifier(), ReportType.PARTICIPANT);
    }
    
    @GetMapping("/v3/participants/reports/{identifier}/index")
    public ReportIndex getParticipantReportIndex(@PathVariable String identifier) {
        UserSession session = getAuthenticatedSession();
        ReportDataKey key = new ReportDataKey.Builder()
                .withIdentifier(identifier)
                .withReportType(ReportType.PARTICIPANT)
                .withStudyIdentifier(session.getStudyIdentifier()).build();
        
        return reportService.getReportIndex(key);
    }

    /** API to get reports for the given user by date. */
    @GetMapping("/v3/participants/{userId}/reports/{identifier}")
    public DateRangeResourceList<? extends ReportData> getParticipantReport(@PathVariable String userId,
            @PathVariable String identifier, @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        return getParticipantReportInternal(session.getStudyIdentifier(), userId, identifier, startDate,
                endDate);
    }

    /** Worker API to get reports for the given user in the given study by date. */
    @GetMapping("/v3/studies/{studyId}/participants/{userId}/reports/{reportId}")
    public DateRangeResourceList<? extends ReportData> getParticipantReportForWorker(@PathVariable String studyId,
            @PathVariable String userId, @PathVariable String reportId, @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        getAuthenticatedSession(WORKER);
        return getParticipantReportInternal(new StudyIdentifierImpl(studyId), userId, reportId, startDate,
                endDate);
    }

    private DateRangeResourceList<? extends ReportData> getParticipantReportInternal(StudyIdentifier studyId, String userId, String reportId,
            String startDateString, String endDateString) {
        LocalDate startDate = getLocalDateOrDefault(startDateString, null);
        LocalDate endDate = getLocalDateOrDefault(endDateString, null);

        Account account = accountService.getAccount(AccountId.forId(studyId.getIdentifier(), userId));
        if (account == null) {
            throw new EntityNotFoundException(Account.class);
        }
        return reportService.getParticipantReport(studyId, reportId, account.getHealthCode(), startDate, endDate);
    }

    /** API to get reports for the given user by date-time. */
    @GetMapping("/v4/participants/{userId}/reports/{identifier}")
    public ForwardCursorPagedResourceList<ReportData> getParticipantReportV4(@PathVariable String userId,
            @PathVariable String identifier, @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime, @RequestParam(required = false) String offsetKey,
            @RequestParam(required = false) String pageSize) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        return getParticipantReportInternalV4(session.getStudyIdentifier(), userId, identifier, startTime,
                endTime, offsetKey, pageSize);
    }

    /** Worker API to get reports for the given user in the given study by date-time. */
    @GetMapping("/v4/studies/{studyId}/participants/{userId}/reports/{reportId}")
    public ForwardCursorPagedResourceList<ReportData> getParticipantReportForWorkerV4(@PathVariable String studyId,
            @PathVariable String userId, @PathVariable String reportId,
            @RequestParam(required = false) String startTime, @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String offsetKey, @RequestParam(required = false) String pageSize) {
        getAuthenticatedSession(WORKER);
        return getParticipantReportInternalV4(new StudyIdentifierImpl(studyId), userId, reportId, startTime, endTime,
                offsetKey, pageSize);
    }

    // Helper method, shared by both getParticipantReportV4() and getParticipantReportForWorkerV4().
    private ForwardCursorPagedResourceList<ReportData> getParticipantReportInternalV4(StudyIdentifier studyId, String userId, String reportId,
            String startTimeString, String endTimeString, String offsetKey, String pageSizeString) {
        DateTime startTime = getDateTimeOrDefault(startTimeString, null);
        DateTime endTime = getDateTimeOrDefault(endTimeString, null);
        int pageSize = getIntOrDefault(pageSizeString, API_DEFAULT_PAGE_SIZE);

        Account account = accountService.getAccount(AccountId.forId(studyId.getIdentifier(), userId));
        if (account == null) {
            throw new EntityNotFoundException(Account.class);
        }
        return reportService.getParticipantReportV4(studyId, reportId, account.getHealthCode(), startTime, endTime,
                offsetKey, pageSize);
    }

    /**
     * Report participant data can be saved by developers or by worker processes. The JSON for these must 
     * include a healthCode field. This is validated when constructing the DataReportKey.
     */
    @PostMapping({"/v4/participants/{userId}/reports/{identifier}", "/v3/participants/{userId}/reports/{identifier}"})
    @ResponseStatus(HttpStatus.CREATED)
    public StatusMessage saveParticipantReport(@PathVariable String userId, @PathVariable String identifier) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        Account account = accountService.getAccount(AccountId.forId(study.getIdentifier(), userId));
        if (account == null) {
            throw new EntityNotFoundException(Account.class);
        }
        ReportData reportData = parseJson(ReportData.class);
        reportData.setKey(null); // set in service, but just so no future use depends on it
        
        reportService.saveParticipantReport(session.getStudyIdentifier(), identifier, 
                account.getHealthCode(), reportData);
        
        return new StatusMessage("Report data saved.");
    }
    
    /**
     * When saving, worker accounts do not know the userId of the account, only the healthCode, so a 
     * special method is needed.
     */
    @PostMapping("/v3/participants/reports/{identifier}")
    @ResponseStatus(HttpStatus.CREATED)
    public StatusMessage saveParticipantReportForWorker(@PathVariable String identifier) {
        UserSession session = getAuthenticatedSession(WORKER);
        
        JsonNode node = parseJson(JsonNode.class);
        if (!node.has("healthCode")) {
            throw new BadRequestException("A health code is required to save report data.");
        }
        String healthCode = node.get("healthCode").asText();
        
        ReportData reportData = parseJson(node, ReportData.class);
        reportData.setKey(null); // set in service, but just so no future use depends on it
        
        reportService.saveParticipantReport(session.getStudyIdentifier(), identifier, 
                healthCode, reportData);
        
        return new StatusMessage("Report data saved.");
    }
    
    /**
     * Developers and workers can delete participant report data (though worker accounts are unlikely 
     * to know the user ID for records). This deletes all reports for all users. This is not 
     * performant for large data sets and should only be done during testing. 
     */
    @DeleteMapping({ "/v4/participants/{userId}/reports/{identifier}",
            "/v3/participants/{userId}/reports/{identifier}" })
    public StatusMessage deleteParticipantReport(@PathVariable String userId, @PathVariable String identifier) {
        UserSession session = getAuthenticatedSession(DEVELOPER, WORKER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        Account account = accountService.getAccount(AccountId.forId(study.getIdentifier(), userId));
        if (account == null) {
            throw new EntityNotFoundException(Account.class);    
        }
        reportService.deleteParticipantReport(session.getStudyIdentifier(), identifier, account.getHealthCode());
        
        return new StatusMessage("Report deleted.");
    }
    
    /**
     * Delete an individual participant report record
     */
    @DeleteMapping("/v3/participants/{userId}/reports/{identifier}/{date}")
    public StatusMessage deleteParticipantReportRecord(@PathVariable String userId, @PathVariable String identifier,
            @PathVariable String date) {
        UserSession session = getAuthenticatedSession(DEVELOPER, WORKER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        Account account = accountService.getAccount(AccountId.forId(study.getIdentifier(), userId));
        if (account == null) {
            throw new EntityNotFoundException(Account.class);
        }
        reportService.deleteParticipantReportRecord(session.getStudyIdentifier(), identifier, date, account.getHealthCode());
        
        return new StatusMessage("Report record deleted.");
    }
    
    @DeleteMapping("/v3/participants/reports/{identifier}")
    public StatusMessage deleteParticipantReportIndex(@PathVariable String identifier) {
        UserSession session = getAuthenticatedSession(ADMIN);
        
        reportService.deleteParticipantReportIndex(session.getStudyIdentifier(), identifier);
        
        return new StatusMessage("Report index deleted.");
    }
}
