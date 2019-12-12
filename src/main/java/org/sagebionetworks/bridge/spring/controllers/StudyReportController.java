package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeUtils.getDateTimeOrDefault;
import static org.sagebionetworks.bridge.BridgeUtils.getIntOrDefault;
import static org.sagebionetworks.bridge.BridgeUtils.getLocalDateOrDefault;
import static org.sagebionetworks.bridge.BridgeUtils.setRequestContext;
import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.models.reports.ReportType.PARTICIPANT;
import static org.sagebionetworks.bridge.models.reports.ReportType.STUDY;

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

import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.DateRangeResourceList;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.ReportTypeResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.reports.ReportData;
import org.sagebionetworks.bridge.models.reports.ReportDataKey;
import org.sagebionetworks.bridge.models.reports.ReportIndex;
import org.sagebionetworks.bridge.models.reports.ReportType;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.services.ReportService;

/**
 * <p>Permissions for study reports are more complicated than other controllers:</p>
 * <p><b>Study Reports</b></p>
 * <ul>
 *   <li>any authenticated user can get the study identifiers (indices)</li>
 *   <li>any authenticated user can see a study report</li>  
 *   <li>developers/workers can add/delete</li>
 * </ul>
 */
@CrossOrigin
@RestController
public class StudyReportController extends BaseController {
    
    static final StatusMessage UPDATED_MSG = new StatusMessage("Report index updated.");
    static final StatusMessage DELETED_DATA_MSG = new StatusMessage("Report record deleted.");
    static final StatusMessage DELETED_MSG = new StatusMessage("Report deleted.");
    static final StatusMessage SAVED_MSG = new StatusMessage("Report data saved.");
    
    @Autowired
    ReportService reportService;
    
    final void setReportService(ReportService reportService) {
        this.reportService = reportService;
    }
    
    /**
     * Get a list of the identifiers used for reports in this study. For backwards compatibility this method 
     * takes an argument and can return participants, but there is now a separate endpoint for that.
     */
    @GetMapping("/v3/reports")
    public ReportTypeResourceList<? extends ReportIndex> listStudyReportIndices(
            @RequestParam(required = false) String type) throws Exception {
        UserSession session = getAuthenticatedSession();
        ReportType reportType = ("participant".equalsIgnoreCase(type)) ? PARTICIPANT : STUDY;
        
        return reportService.getReportIndices(session.getStudyIdentifier(), reportType);
    }
    
    /**
     * Any authenticated user can get study reports, as some might be internal/administrative and some might 
     * be intended for end users, and these do not expose user-specific information.
     */
    @GetMapping("/v3/reports/{identifier}")
    public DateRangeResourceList<? extends ReportData> getStudyReport(@PathVariable String identifier,
            @RequestParam(required = false) String startDate, @RequestParam(required = false) String endDate) {
        UserSession session = getAuthenticatedSession();
        
        LocalDate startDateObj = getLocalDateOrDefault(startDate, null);
        LocalDate endDateObj = getLocalDateOrDefault(endDate, null);
        
        return reportService.getStudyReport(session.getStudyIdentifier(), identifier, startDateObj, endDateObj);
    }
    
    /**
     * Get a study report *if* it is marked public, as this call does not require the user to be authenticated.
     */
    @GetMapping("/v3/studies/{studyId}/reports/{identifier}")
    public DateRangeResourceList<? extends ReportData> getPublicStudyReport(@PathVariable String studyId,
            @PathVariable String identifier, @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        StudyIdentifier studyIdObj = new StudyIdentifierImpl(studyId);

        verifyIndexIsPublic(studyIdObj, identifier);
        // We do not want to inherit a user's session information, if a session token is being 
        // passed to this method.
        setRequestContext(NULL_INSTANCE);

        LocalDate startDateObj = getLocalDateOrDefault(startDate, null);
        LocalDate endDateObj = getLocalDateOrDefault(endDate, null);
        
        return reportService.getStudyReport(studyIdObj, identifier, startDateObj, endDateObj);
    }
    
    /**
     * Any authenticated user can get study reports, as some might be internal/administrative and some might 
     * be intended for end users, and these do not expose user-specific information.
     */
    @GetMapping("/v4/reports/{identifier}")
    public ForwardCursorPagedResourceList<ReportData> getStudyReportV4(@PathVariable String identifier,
            @RequestParam(required = false) String startTime, @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String offsetKey, @RequestParam(required = false) String pageSize) {
        UserSession session = getAuthenticatedSession();
        
        DateTime startTimeObj = getDateTimeOrDefault(startTime, null);
        DateTime endTimeObj = getDateTimeOrDefault(endTime, null);
        int pageSizeInt = getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);
        
        return reportService.getStudyReportV4(session.getStudyIdentifier(), identifier, startTimeObj, endTimeObj,
                offsetKey, pageSizeInt);
    }
    
    /**
     * Report study data can be saved by developers or by worker processes.
     */
    @PostMapping({"/v4/reports/{identifier}", "/v3/reports/{identifier}"})
    @ResponseStatus(HttpStatus.CREATED)
    public StatusMessage saveStudyReport(@PathVariable String identifier) throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER, WORKER);
     
        ReportData reportData = parseJson(ReportData.class);
        reportData.setKey(null); // set in service, but just so no future use depends on it
        
        reportService.saveStudyReport(session.getStudyIdentifier(), identifier, reportData);
        
        return SAVED_MSG;
    }

    /**
     * A similar method as above but specifying study id only for WORKER
     */
    @PostMapping("/v3/studies/{studyId}/reports/{identifier}")
    @ResponseStatus(HttpStatus.CREATED)
    public StatusMessage saveStudyReportForWorker(@PathVariable String studyId, @PathVariable String identifier)
            throws Exception {
        getAuthenticatedSession(WORKER);

        ReportData reportData = parseJson(ReportData.class);
        reportData.setKey(null); // set in service, but just so no future use depends on it

        StudyIdentifier studyIdObj = new StudyIdentifierImpl(studyId);
        reportService.saveStudyReport(studyIdObj, identifier, reportData);

        return SAVED_MSG;
    }
    
    /**
     * Developers and workers can delete study report data. This is not performant for large data sets and 
     * should only be done during testing.
     */
    @DeleteMapping({"/v4/reports/{identifier}", "/v3/reports/{identifier}"})
    public StatusMessage deleteStudyReport(@PathVariable String identifier) {
        UserSession session = getAuthenticatedSession(DEVELOPER, WORKER);
        
        reportService.deleteStudyReport(session.getStudyIdentifier(), identifier);
        
        return DELETED_MSG;
    }
    
    /**
     * Delete an individual study report record. 
     */
    @DeleteMapping("/v3/reports/{identifier}/{date}")
    public StatusMessage deleteStudyReportRecord(@PathVariable String identifier, @PathVariable String date) {
        UserSession session = getAuthenticatedSession(DEVELOPER, WORKER);
        
        reportService.deleteStudyReportRecord(session.getStudyIdentifier(), identifier, date);
        
        return DELETED_DATA_MSG;
    }
    
    /**
     * Get a single study report index
     */
    @GetMapping("/v3/reports/{identifier}/index")
    public ReportIndex getStudyReportIndex(@PathVariable String identifier) {
        UserSession session = getAuthenticatedSession();
        ReportDataKey key = new ReportDataKey.Builder()
                .withIdentifier(identifier)
                .withReportType(STUDY)
                .withStudyIdentifier(session.getStudyIdentifier()).build();
        
        return reportService.getReportIndex(key);
    }
    
    /**
     * Update a single study report index. 
     */
    @PostMapping("/v3/reports/{identifier}/index")
    public StatusMessage updateStudyReportIndex(@PathVariable String identifier) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        ReportIndex index = parseJson(ReportIndex.class);
        ReportDataKey key = new ReportDataKey.Builder()
                .withHealthCode(session.getHealthCode())
                .withReportType(STUDY)
                .withIdentifier(identifier)
                .withStudyIdentifier(session.getStudyIdentifier()).build();
        index.setKey(key.getIndexKeyString());
        index.setIdentifier(identifier);
        
        reportService.updateReportIndex(session.getStudyIdentifier(), STUDY, index);
        
        return UPDATED_MSG;
    }

    private void verifyIndexIsPublic(final StudyIdentifier studyId, final String identifier) {
        ReportDataKey key = new ReportDataKey.Builder()
                .withIdentifier(identifier)
                .withReportType(STUDY)
                .withStudyIdentifier(studyId).build();
        
        ReportIndex index = reportService.getReportIndex(key);
        if (index == null || !index.isPublic()) {
            throw new EntityNotFoundException(ReportIndex.class);
        }
    }
}
