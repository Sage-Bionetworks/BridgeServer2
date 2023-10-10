package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.Roles.WORKER;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.UploadTableService;
import org.sagebionetworks.bridge.upload.UploadTableRow;
import org.sagebionetworks.bridge.upload.UploadTableRowQuery;

/** Controller for upload table rows. */
@CrossOrigin
@RestController
public class UploadTableController extends BaseController {
    static final StatusMessage CREATED_MSG = new StatusMessage("Upload table row created.");
    static final StatusMessage DELETED_MSG = new StatusMessage("Upload table row deleted.");

    private StudyService studyService;
    private UploadTableService uploadTableService;

    @Autowired
    public final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }

    @Autowired
    public final void setUploadTableService(UploadTableService uploadTableService) {
        this.uploadTableService = uploadTableService;
    }

    /** Delete a single upload table row. This is currently only used by integration tests (so only superadmin). */
    @DeleteMapping("/v1/apps/{appId}/studies/{studyId}/uploadtable/{recordId}")
    public StatusMessage deleteUploadTableRowForSuperadmin(@PathVariable String appId, @PathVariable String studyId,
            @PathVariable String recordId) {
        getAuthenticatedSession(SUPERADMIN);

        // Verify study exists.
        studyService.getStudy(appId, studyId, true);

        uploadTableService.deleteUploadTableRow(appId, studyId, recordId);
        return DELETED_MSG;
    }

    /** Get a single upload table row. This is currently only used by integration tests (so only superadmin). */
    @GetMapping("/v1/apps/{appId}/studies/{studyId}/uploadtable/{recordId}")
    public UploadTableRow getUploadTableRowForSuperadmin(@PathVariable String appId, @PathVariable String studyId,
            @PathVariable String recordId) {
        getAuthenticatedSession(SUPERADMIN);

        // Verify study exists.
        studyService.getStudy(appId, studyId, true);

        return uploadTableService.getUploadTableRow(appId, studyId, recordId).orElseThrow(
                () -> new EntityNotFoundException(UploadTableRow.class));
    }

    /** Query for upload table rows. This is used by the worker to generate a CSV. */
    @PostMapping("/v1/apps/{appId}/studies/{studyId}/uploadtable/query")
    public PagedResourceList<UploadTableRow> queryUploadTableRowsForWorker(@PathVariable String appId,
            @PathVariable String studyId) {
        getAuthenticatedSession(WORKER);

        // Verify study exists.
        studyService.getStudy(appId, studyId, true);

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

        // Verify study exists.
        studyService.getStudy(appId, studyId, true);

        UploadTableRow row = parseJson(UploadTableRow.class);
        uploadTableService.saveUploadTableRow(appId, studyId, row);
        return CREATED_MSG;
    }
}
