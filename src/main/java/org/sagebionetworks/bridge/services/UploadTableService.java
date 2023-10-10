package org.sagebionetworks.bridge.services;

import java.util.Optional;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.UploadTableRowDao;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.upload.UploadTableRow;
import org.sagebionetworks.bridge.upload.UploadTableRowQuery;

/** Service handler for upload table rows. */
@Component
public class UploadTableService {
    private UploadTableRowDao uploadTableRowDao;

    @Autowired
    public final void setUploadTableRowDao(UploadTableRowDao uploadTableRowDao) {
        this.uploadTableRowDao = uploadTableRowDao;
    }

    /** Delete a single upload table row. */
    public void deleteUploadTableRow(String appId, String studyId, String recordId) {
        uploadTableRowDao.deleteUploadTableRow(appId, studyId, recordId);
    }

    /** Get a single upload table row. */
    public Optional<UploadTableRow> getUploadTableRow(String appId, String studyId, String recordId) {
        return uploadTableRowDao.getUploadTableRow(appId, studyId, recordId);
    }

    /** Query for upload table rows. */
    public PagedResourceList<UploadTableRow> queryUploadTableRows(String appId, String studyId,
            UploadTableRowQuery query) {
        // appId and studyId are required and come from the URL path. (This is validated in the controller.)
        query.setAppId(appId);
        query.setStudyId(studyId);

        // todo validate the query

        return uploadTableRowDao.queryUploadTableRows(query);
    }

    /** Create a new upload table row, or overwrite it if the row already exists. */
    public void saveUploadTableRow(String appId, String studyId, UploadTableRow row) {
        // appId and studyId are required and come from the URL path. (This is validated in the controller.)
        row.setAppId(appId);
        row.setStudyId(studyId);

        // todo validate the row

        // CreatedOn defaults to the current time.
        if (row.getCreatedOn() == null) {
            row.setCreatedOn(DateTime.now());
        }

        uploadTableRowDao.saveUploadTableRow(row);
    }
}
