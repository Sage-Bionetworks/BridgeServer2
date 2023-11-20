package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.STUDY_COORDINATOR;
import static org.sagebionetworks.bridge.Roles.STUDY_DESIGNER;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.Roles.WORKER;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.services.UploadTableService;
import org.sagebionetworks.bridge.upload.UploadTableJob;
import org.sagebionetworks.bridge.upload.UploadTableJobGuidHolder;
import org.sagebionetworks.bridge.upload.UploadTableJobResult;
import org.sagebionetworks.bridge.upload.UploadTableRow;
import org.sagebionetworks.bridge.upload.UploadTableRowQuery;

/** Controller for upload table rows. */
@CrossOrigin
@RestController
public class UploadTableController extends BaseController {
    private static final StatusMessage CREATED_MSG = new StatusMessage("Upload table row created.");
    private static final StatusMessage DELETED_MSG = new StatusMessage("Upload table row deleted.");
    private static final StatusMessage UPDATED_JOB_MSG = new StatusMessage("Upload table job updated.");

    private UploadTableService uploadTableService;

    @Autowired
    public final void setUploadTableService(UploadTableService uploadTableService) {
        this.uploadTableService = uploadTableService;
    }

    /** Get the upload table job result, with the downloadable S3 URL if it's ready. */
    @GetMapping("/v5/studies/{studyId}/uploadtable/requests/{jobGuid}")
    public UploadTableJobResult getUploadTableJobResult(@PathVariable String studyId, @PathVariable String jobGuid) {
        UserSession session = getAuthenticatedSession(STUDY_DESIGNER, STUDY_COORDINATOR, DEVELOPER, RESEARCHER);
        return uploadTableService.getUploadTableJobResult(session.getAppId(), studyId, jobGuid);
    }

    /** List upload table jobs for the given app and study. Does not include the downloadable S3 URL. */
    @GetMapping("/v5/studies/{studyId}/uploadtable/requests")
    public PagedResourceList<UploadTableJob> listUploadTableJobsForStudy(@PathVariable String studyId,
            @RequestParam(required = false) String start, @RequestParam(required = false) String pageSize) {
        UserSession session = getAuthenticatedSession(STUDY_DESIGNER, STUDY_COORDINATOR, DEVELOPER, RESEARCHER);

        int startInt = BridgeUtils.getIntOrDefault(start, 0);
        int pageSizeInt = BridgeUtils.getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);

        return uploadTableService.listUploadTableJobsForStudy(session.getAppId(), studyId, startInt, pageSizeInt);
    }

    /** Request a zip file with CSVs of all uploads in this app and study. */
    @PostMapping("/v5/studies/{studyId}/uploadtable/requests")
    public UploadTableJobGuidHolder requestUploadTableForStudy(@PathVariable String studyId) {
        UserSession session = getAuthenticatedSession(STUDY_DESIGNER, STUDY_COORDINATOR, DEVELOPER, RESEARCHER);
        return uploadTableService.requestUploadTableForStudy(session.getAppId(), studyId);
    }

    /** Worker API to get the upload table job. Does not include the downloadable S3 URL. */
    @GetMapping("/v1/apps/{appId}/studies/{studyId}/uploadtable/requests/{jobGuid}")
    public UploadTableJob getUploadTableJobForWorker(@PathVariable String appId, @PathVariable String studyId,
            @PathVariable String jobGuid) {
        getAuthenticatedSession(WORKER);
        return uploadTableService.getUploadTableJobForWorker(appId, studyId, jobGuid);
    }

    /** Worker API to update the upload table job. */
    @PostMapping("/v1/apps/{appId}/studies/{studyId}/uploadtable/requests/{jobGuid}")
    public StatusMessage updateUploadTableJobForWorker(@PathVariable String appId, @PathVariable String studyId,
            @PathVariable String jobGuid) {
        getAuthenticatedSession(WORKER);
        UploadTableJob job = parseJson(UploadTableJob.class);
        uploadTableService.updateUploadTableJobForWorker(appId, studyId, jobGuid, job);
        return UPDATED_JOB_MSG;
    }

    /** Delete a single upload table row. This is currently only used by integration tests (so only superadmin). */
    @DeleteMapping("/v1/apps/{appId}/studies/{studyId}/uploadtable/{recordId}")
    public StatusMessage deleteUploadTableRowForSuperadmin(@PathVariable String appId, @PathVariable String studyId,
            @PathVariable String recordId) {
        getAuthenticatedSession(SUPERADMIN);
        uploadTableService.deleteUploadTableRow(appId, studyId, recordId);
        return DELETED_MSG;
    }

    /** Get a single upload table row. This is currently only used by integration tests (so only superadmin). */
    @GetMapping("/v1/apps/{appId}/studies/{studyId}/uploadtable/{recordId}")
    public UploadTableRow getUploadTableRowForSuperadmin(@PathVariable String appId, @PathVariable String studyId,
            @PathVariable String recordId) {
        getAuthenticatedSession(SUPERADMIN);
        return uploadTableService.getUploadTableRow(appId, studyId, recordId);
    }

    /** Query for upload table rows. This is used by the worker to generate a CSV. */
    @PostMapping("/v1/apps/{appId}/studies/{studyId}/uploadtable/query")
    public PagedResourceList<UploadTableRow> queryUploadTableRowsForWorker(@PathVariable String appId,
            @PathVariable String studyId) {
        getAuthenticatedSession(WORKER);
        UploadTableRowQuery query = parseJson(UploadTableRowQuery.class);
        return uploadTableService.queryUploadTableRows(appId, studyId, query);
    }

    /**
     * Create a new upload table row, or overwrite it if the row already exists. This is called by the
     * Exporter3Worker to write rows for each upload.
     */
    @PostMapping("/v1/apps/{appId}/studies/{studyId}/uploadtable")
    public StatusMessage saveUploadTableRowForWorker(@PathVariable String appId, @PathVariable String studyId) {
        getAuthenticatedSession(WORKER);
        UploadTableRow row = parseJson(UploadTableRow.class);
        uploadTableService.saveUploadTableRow(appId, studyId, row);
        return CREATED_MSG;
    }
}
