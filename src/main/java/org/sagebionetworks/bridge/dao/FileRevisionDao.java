package org.sagebionetworks.bridge.dao;

import java.util.Optional;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.files.FileRevision;

public interface FileRevisionDao {
    PagedResourceList<FileRevision> getFileRevisions(String guid, int offset, int pageSize);
    
    Optional<FileRevision> getFileRevision(String guid, DateTime createdOn);
    
    void createFileRevision(FileRevision revision);
    
    void updateFileRevision(FileRevision revision);
}
