package org.sagebionetworks.bridge.dao;

import java.util.Optional;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.files.File;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

public interface FileDao {
    PagedResourceList<File> getFiles(StudyIdentifier studyId, Integer offset, Integer limit, boolean includeDeleted);

    Optional<File> getFile(StudyIdentifier studyId, String guid);
    
    File createFile(File file);
    
    File updateFile(File file);
    
    void deleteFilePermanently(StudyIdentifier studyId, String guid);
    
    void deleteAllStudyFiles(StudyIdentifier studyId);
}
