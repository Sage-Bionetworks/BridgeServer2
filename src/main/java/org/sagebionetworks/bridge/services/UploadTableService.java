package org.sagebionetworks.bridge.services;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.UploadTableRowDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.upload.UploadTableRow;
import org.sagebionetworks.bridge.upload.UploadTableRowQuery;
import org.sagebionetworks.bridge.validators.UploadTableRowQueryValidator;
import org.sagebionetworks.bridge.validators.UploadTableRowValidator;
import org.sagebionetworks.bridge.validators.Validate;

/** Service handler for upload table rows. */
@Component
public class UploadTableService {
    private StudyService studyService;
    private UploadTableRowDao uploadTableRowDao;

    @Autowired
    public final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }

    @Autowired
    public final void setUploadTableRowDao(UploadTableRowDao uploadTableRowDao) {
        this.uploadTableRowDao = uploadTableRowDao;
    }

    /** Delete a single upload table row. */
    public void deleteUploadTableRow(String appId, String studyId, String recordId) {
        // Verify study exists, by passing in throwsException = true.
        studyService.getStudy(appId, studyId, true);

        uploadTableRowDao.deleteUploadTableRow(appId, studyId, recordId);
    }

    /** Get a single upload table row. Throws if the row doesn't exist. */
    public UploadTableRow getUploadTableRow(String appId, String studyId, String recordId) {
        // Verify study exists, by passing in throwsException = true.
        studyService.getStudy(appId, studyId, true);

        return uploadTableRowDao.getUploadTableRow(appId, studyId, recordId).orElseThrow(
                () -> new EntityNotFoundException(UploadTableRow.class));
    }

    /** Query for upload table rows. */
    public PagedResourceList<UploadTableRow> queryUploadTableRows(String appId, String studyId,
            UploadTableRowQuery query) {
        // Verify study exists, by passing in throwsException = true.
        studyService.getStudy(appId, studyId, true);

        // appId and studyId are required and come from the URL path.
        query.setAppId(appId);
        query.setStudyId(studyId);

        // Validate the query.
        Validate.entityThrowingException(UploadTableRowQueryValidator.INSTANCE, query);

        return uploadTableRowDao.queryUploadTableRows(query);
    }

    /** Create a new upload table row, or overwrite it if the row already exists. */
    public void saveUploadTableRow(String appId, String studyId, UploadTableRow row) {
        // Verify study exists, by passing in throwsException = true.
        studyService.getStudy(appId, studyId, true);

        // appId and studyId are required and come from the URL path.
        row.setAppId(appId);
        row.setStudyId(studyId);

        // CreatedOn defaults to the current time.
        if (row.getCreatedOn() == null) {
            row.setCreatedOn(DateTime.now());
        }

        // Validate the row.
        Validate.entityThrowingException(UploadTableRowValidator.INSTANCE, row);

        uploadTableRowDao.saveUploadTableRow(row);
    }
}
