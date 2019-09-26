package org.sagebionetworks.bridge.dao;

import java.util.Optional;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.files.FileMetadata;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

public interface FileMetadataDao {
    PagedResourceList<FileMetadata> getFiles(StudyIdentifier studyId, int offset, int limit, boolean includeDeleted);

    Optional<FileMetadata> getFile(StudyIdentifier studyId, String guid);
    
    FileMetadata createFile(FileMetadata file);
    
    FileMetadata updateFile(FileMetadata file);
    
    void deleteFilePermanently(StudyIdentifier studyId, String guid);
    
    void deleteAllStudyFiles(StudyIdentifier studyId);
}
