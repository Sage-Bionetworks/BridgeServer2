package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.AuthEvaluatorField.USER_ID;
import static org.sagebionetworks.bridge.AuthUtils.CAN_EDIT_PARTICIPANTS;
import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeUtils.getDateTimeOrDefault;
import static org.sagebionetworks.bridge.BridgeUtils.getIntOrDefault;
import static org.sagebionetworks.bridge.BridgeUtils.getLocalDateOrDefault;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
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

import org.sagebionetworks.bridge.BridgeUtils;
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
        
        return reportService.getParticipantReport(session.getAppId(), identifier, session.getHealthCode(),
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
        
        return reportService.getParticipantReportV4(session.getAppId(), identifier, session.getHealthCode(),
                startTimeObj, endTimeObj, offsetKey, pageSizeInt);
    }

    @PostMapping({"/v4/users/self/reports/{identifier}", "/v3/users/self/reports/{identifier}"})
    @ResponseStatus(HttpStatus.CREATED)
    public StatusMessage saveParticipantReportForSelf(@PathVariable String identifier) {
        UserSession session = getAuthenticatedSession();
        
        ReportData reportData = parseJson(ReportData.class);
        reportData.setKey(null); // set in service, but just so no future use depends on it
        
        reportService.saveParticipantReport(session.getAppId(), identifier, 
                session.getHealthCode(), reportData);
        
        return new StatusMessage("Report data saved.");
    }
    
    /**
     * Get a list of the identifiers used for participant reports in this app.
     */
    @GetMapping("/v3/participants/reports")
    public ReportTypeResourceList<? extends ReportIndex> listParticipantReportIndices() {
        UserSession session = getAuthenticatedSession();
        
        return reportService.getReportIndices(session.getAppId(), ReportType.PARTICIPANT);
    }
    
    @GetMapping("/v3/participants/reports/{identifier}/index")
    public ReportIndex getParticipantReportIndex(@PathVariable String identifier) {
        UserSession session = getAuthenticatedSession();
        ReportDataKey key = new ReportDataKey.Builder()
                .withIdentifier(identifier)
                .withReportType(ReportType.PARTICIPANT)
                .withAppId(session.getAppId()).build();
        
        return reportService.getReportIndex(key);
    }

    /** API to get reports for the given user by date. */
    @GetMapping("/v3/participants/{userIdToken}/reports/{identifier}")
    public DateRangeResourceList<? extends ReportData> getParticipantReport(@PathVariable String userIdToken,
            @PathVariable String identifier, @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        UserSession session = getAdministrativeSession();
        
        AccountId accountId = BridgeUtils.parseAccountId(session.getAppId(), userIdToken);
        Account account = accountService.getAccount(accountId);
        if (account == null) {
            throw new EntityNotFoundException(Account.class);
        }
        
        CAN_EDIT_PARTICIPANTS.checkAndThrow(USER_ID, account.getId());
        
        return getParticipantReportInternal(session.getAppId(), account, identifier, startDate, endDate);
    }

    /** Worker API to get reports for the given user in the given app by date. */
    @GetMapping(path = { "/v1/apps/{appId}/participants/{userIdToken}/reports/{reportId}",
            "/v3/studies/{appId}/participants/{userIdToken}/reports/{reportId}" })
    public DateRangeResourceList<? extends ReportData> getParticipantReportForWorker(@PathVariable String appId,
            @PathVariable String userIdToken, @PathVariable String reportId, @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        getAuthenticatedSession(WORKER);
        
        AccountId accountId = BridgeUtils.parseAccountId(appId, userIdToken);
        Account account = accountService.getAccount(accountId);
        if (account == null) {
            throw new EntityNotFoundException(Account.class);
        }
        
        return getParticipantReportInternal(appId, account, reportId, startDate, endDate);
    }

    private DateRangeResourceList<? extends ReportData> getParticipantReportInternal(String appId, Account account, String reportId,
            String startDateString, String endDateString) {
        LocalDate startDate = getLocalDateOrDefault(startDateString, null);
        LocalDate endDate = getLocalDateOrDefault(endDateString, null);

        return reportService.getParticipantReport(appId, reportId, account.getHealthCode(), startDate, endDate);
    }

    /** API to get reports for the given user by date-time. */
    @GetMapping("/v4/participants/{userIdToken}/reports/{identifier}")
    public ForwardCursorPagedResourceList<ReportData> getParticipantReportV4(@PathVariable String userIdToken,
            @PathVariable String identifier, @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime, @RequestParam(required = false) String offsetKey,
            @RequestParam(required = false) String pageSize) {
        UserSession session = getAdministrativeSession();

        AccountId accountId = BridgeUtils.parseAccountId(session.getAppId(), userIdToken);
        Account account = accountService.getAccount(accountId);
        if (account == null) {
            throw new EntityNotFoundException(Account.class);
        }
        
        CAN_EDIT_PARTICIPANTS.checkAndThrow(USER_ID, session.getId());
        
        return getParticipantReportInternalV4(session.getAppId(), account, identifier, 
                startTime, endTime, offsetKey, pageSize);
    }

    /** Worker API to get reports for the given user in the given app by date-time. */
    @GetMapping(path = {"/v2/apps/{appId}/participants/{userIdToken}/reports/{reportId}",
            "/v4/studies/{appId}/participants/{userIdToken}/reports/{reportId}"})
    public ForwardCursorPagedResourceList<ReportData> getParticipantReportForWorkerV4(@PathVariable String appId,
            @PathVariable String userIdToken, @PathVariable String reportId,
            @RequestParam(required = false) String startTime, @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String offsetKey, @RequestParam(required = false) String pageSize) {
        getAuthenticatedSession(WORKER);
        
        AccountId accountId = BridgeUtils.parseAccountId(appId, userIdToken);
        Account account = accountService.getAccount(accountId);
        if (account == null) {
            throw new EntityNotFoundException(Account.class);
        }
        return getParticipantReportInternalV4(appId, account, reportId, startTime, endTime, offsetKey, pageSize);
    }

    // Helper method, shared by both getParticipantReportV4() and getParticipantReportForWorkerV4().
    private ForwardCursorPagedResourceList<ReportData> getParticipantReportInternalV4(String appId, Account account,
            String reportId, String startTimeString, String endTimeString, String offsetKey, String pageSizeString) {
        DateTime startTime = getDateTimeOrDefault(startTimeString, null);
        DateTime endTime = getDateTimeOrDefault(endTimeString, null);
        int pageSize = getIntOrDefault(pageSizeString, API_DEFAULT_PAGE_SIZE);

        return reportService.getParticipantReportV4(appId, reportId, account.getHealthCode(), 
                startTime, endTime, offsetKey, pageSize);
    }

    /**
     * Report participant data can be saved by developers or by worker processes. The JSON for these must 
     * include a healthCode field. This is validated when constructing the DataReportKey.
     */
    @PostMapping({"/v4/participants/{userIdToken}/reports/{identifier}", "/v3/participants/{userIdToken}/reports/{identifier}"})
    @ResponseStatus(HttpStatus.CREATED)
    public StatusMessage saveParticipantReport(@PathVariable String userIdToken, @PathVariable String identifier) {
        UserSession session = getAuthenticatedSession(DEVELOPER);

        String healthCode = accountService.getAccountHealthCode(session.getAppId(), userIdToken)
                .orElseThrow(() -> new EntityNotFoundException(Account.class));

        ReportData reportData = parseJson(ReportData.class);
        reportData.setKey(null); // set in service, but just so no future use depends on it
        
        reportService.saveParticipantReport(session.getAppId(), identifier, 
                healthCode, reportData);
        
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
        
        reportService.saveParticipantReport(session.getAppId(), identifier, 
                healthCode, reportData);
        
        return new StatusMessage("Report data saved.");
    }
    
    /**
     * Developers and workers can delete participant report data (though worker accounts are unlikely 
     * to know the user ID for records). This deletes all reports for all users. This is not 
     * performant for large data sets and should only be done during testing. 
     */
    @DeleteMapping({ "/v4/participants/{userIdToken}/reports/{identifier}",
            "/v3/participants/{userIdToken}/reports/{identifier}" })
    public StatusMessage deleteParticipantReport(@PathVariable String userIdToken, @PathVariable String identifier) {
        UserSession session = getAuthenticatedSession(DEVELOPER, WORKER);
        
        String healthCode = accountService.getAccountHealthCode(session.getAppId(), userIdToken)
                .orElseThrow(() -> new EntityNotFoundException(Account.class));

        reportService.deleteParticipantReport(session.getAppId(), identifier, healthCode);
        
        return new StatusMessage("Report deleted.");
    }
    
    /**
     * Delete an individual participant report record
     */
    @DeleteMapping("/v3/participants/{userIdToken}/reports/{identifier}/{date}")
    public StatusMessage deleteParticipantReportRecord(@PathVariable String userIdToken, @PathVariable String identifier,
            @PathVariable String date) {
        UserSession session = getAuthenticatedSession(DEVELOPER, WORKER);
        
        String healthCode = accountService.getAccountHealthCode(session.getAppId(), userIdToken)
                .orElseThrow(() -> new EntityNotFoundException(Account.class));

        reportService.deleteParticipantReportRecord(session.getAppId(), identifier, date, healthCode);
        
        return new StatusMessage("Report record deleted.");
    }
    
    @DeleteMapping("/v3/participants/reports/{identifier}")
    public StatusMessage deleteParticipantReportIndex(@PathVariable String identifier) {
        UserSession session = getAuthenticatedSession(ADMIN);
        
        reportService.deleteParticipantReportIndex(session.getAppId(), identifier);
        
        return new StatusMessage("Report index deleted.");
    }
}
