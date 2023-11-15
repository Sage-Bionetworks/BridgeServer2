package org.sagebionetworks.bridge.dao;

import java.util.Optional;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.upload.UploadTableJob;

/** DAO to interact with upload table rows. */
public interface UploadTableJobDao {
    /** Get the upload table job with the given GUID. */
    Optional<UploadTableJob> getUploadTableJob(String jobGuid);

    /** List upload table jobs for the given app and study. */
    PagedResourceList<UploadTableJob> listUploadTableJobsForStudy(String appId, String studyId, int start,
            int pageSize);

    /** Save the given upload table job. */
    void saveUploadTableJob(UploadTableJob job);
}
