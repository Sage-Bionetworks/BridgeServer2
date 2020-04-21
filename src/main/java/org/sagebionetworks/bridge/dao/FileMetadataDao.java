package org.sagebionetworks.bridge.dao;

import java.util.Optional;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.files.FileMetadata;

public interface FileMetadataDao {
    PagedResourceList<FileMetadata> getFiles(String appId, int offset, int limit, boolean includeDeleted);

    Optional<FileMetadata> getFile(String appId, String guid);
    
    FileMetadata createFile(FileMetadata file);
    
    FileMetadata updateFile(FileMetadata file);
    
    void deleteFilePermanently(String appId, String guid);
    
    void deleteAllStudyFiles(String appId);
}
