package org.sagebionetworks.bridge.dao;

import java.util.Optional;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.files.FileMetadata;

public interface FileMetadataDao {
    PagedResourceList<FileMetadata> getFiles(String studyId, int offset, int limit, boolean includeDeleted);

    Optional<FileMetadata> getFile(String studyId, String guid);
    
    FileMetadata createFile(FileMetadata file);
    
    FileMetadata updateFile(FileMetadata file);
    
    void deleteFilePermanently(String studyId, String guid);
    
    void deleteAllStudyFiles(String studyId);
}
