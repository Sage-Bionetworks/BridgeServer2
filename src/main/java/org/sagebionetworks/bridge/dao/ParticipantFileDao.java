package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.files.ParticipantFile;

import java.util.List;
import java.util.Optional;

public interface ParticipantFileDao {
    /**
     * Get a ForwardCursorPagedResourceList of ParticipantFiles from the given userId, with nextPageOffsetKey set.
     * If nextPageOffsetKey is null, then the list reached the end and there does not exist next page.
     *
     * @param userId the id of the StudyParticipant
     * @param offsetKey the exclusive starting offset of the query, if null, then query from the start
     * @param pageSize the number of items in the result page
     * @return a ForwardCursorPagedResourceList of ParticipantFiles
     */
    ForwardCursorPagedResourceList<ParticipantFile> getParticipantFiles(String userId, String offsetKey, int pageSize);

    /** Internal API to get all files for a user. */
    List<ParticipantFile> getAllFilesForParticipant(String userId);

    /**
     * Returns the ParticipantFile from the given criteria. If no such file exists, returns Optional.empty.
     *
     * @param userId the id of the StudyParticipant
     * @param fileId the id of file from the user.
     * @return the ParticipantFile
     */
    Optional<ParticipantFile> getParticipantFile(String userId, String fileId);

    /**
     * Upload the ParticipantFile to the database.
     *
     * @param file the file to be uploaded
     */
    void uploadParticipantFile(ParticipantFile file);

    /**
     * Delete the ParticipantFile from the database.
     *
     * @param userId the id of the StudyParticipant
     * @param fileId the id of the file
     */
    void deleteParticipantFile(String userId, String fileId);

    /** Internal API to delete a list of files. */
    void batchDeleteParticipantFiles(List<ParticipantFile> fileIdList);
}
