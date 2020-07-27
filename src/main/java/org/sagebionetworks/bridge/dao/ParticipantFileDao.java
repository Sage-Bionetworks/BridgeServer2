package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.files.ParticipantFile;

import java.util.Optional;

public interface ParticipantFileDao {
    /**
     * Get a paged resource list of ParticipantFiles from the given userId.
     *
     * @param userId the id of the StudyParticipant
     * @param start the start of the result page
     * @param offset the offset of the result page
     * @return a paged resource list of items in the interval [start, start+offset] from the given userId.
     */
    PagedResourceList<ParticipantFile> getParticipantFiles(String userId, int start, int offset);

    /**
     * Returns the ParticipantFile from the given criteria. If no such file exists, return empty.
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
    // TODO: Not decided what will happen if such file already exists.
    void uploadParticipantFile(ParticipantFile file);

    /**
     * Delete the ParticipantFile from the database.
     *
     * @param userId the id of the StudyParticipant
     * @param fileId the id of the file
     */
    void deleteParticipantFile(String userId, String fileId);
}
