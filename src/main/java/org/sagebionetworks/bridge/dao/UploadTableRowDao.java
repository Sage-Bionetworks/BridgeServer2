package org.sagebionetworks.bridge.dao;

import java.util.Optional;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.upload.UploadTableRow;
import org.sagebionetworks.bridge.upload.UploadTableRowQuery;

/** DAO to interact with upload table rows. */
public interface UploadTableRowDao {
    /** Delete a single upload table row. */
    void deleteUploadTableRow(String appId, String studyId, String recordId);

    /** Get a single upload table row. */
    Optional<UploadTableRow> getUploadTableRow(String appId, String studyId, String recordId);

    /** Query for upload table rows. */
    PagedResourceList<UploadTableRow> queryUploadTableRows(UploadTableRowQuery query);

    /** Create a new upload table row, or overwrite it if the row already exists. */
    void saveUploadTableRow(UploadTableRow row);
}
