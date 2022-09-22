package org.sagebionetworks.bridge.dao;

import java.util.Optional;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicUser;

/**
 * Dao for demographic-related operations.
 */
public interface DemographicDao {
    /**
     * Saves/overwrites a DemographicUser.
     * 
     * @param demographicUser The DemographicUser to save/overwrite.
     * @param appId           The appId of the app in which to save the
     *                        DemographicUser.
     * @param studyId         The studyId of the study in which to save the
     *                        DemographicUser. Can be null if the demographics are
     *                        app-level.
     * @param userId          The userId of the user to associate the
     *                        DemographicUser with.
     * @return the saved DemographicUser.
     */
    DemographicUser saveDemographicUser(DemographicUser demographicUser, String appId, String studyId, String userId);

    /**
     * Deletes a Demographic.
     * 
     * @param demographicId The id of the Demographic to delete.
     */
    void deleteDemographic(String demographicId);

    /**
     * Deletes a DemographicUser.
     * 
     * @param demographicUserId The id of the DemographicUser to delete.
     */
    void deleteDemographicUser(String demographicUserId);

    /**
     * Fetches a Demographic.
     * 
     * @param demographicId The id of the Demographic to fetch.
     * @return the fetched Demographic, or empty if it was not found.
     */
    Optional<Demographic> getDemographic(String demographicId);

    /**
     * Fetches the id for a DemographicUser.
     * 
     * @param appId   The appId of the app which contains the DemographicUser whose
     *                id will be fetched.
     * @param studyId The studyId of the study which contains the DemographicUser
     *                whose id will be fetched. Can be null if the demographics are
     *                app-level.
     * @param userId  The userId of the user who is associated with the
     *                DemographicUser whose id will be fetched.
     * @return the id for a DemographicUser, or empty if it was not found.
     */
    Optional<String> getDemographicUserId(String appId, String studyId, String userId);

    /**
     * Fetches a DemographicUser.
     * 
     * @param appId   The appId of the app which contains the DemographicUser to
     *                fetch.
     * @param studyId The studyId of the study which contains the DemographicUser to
     *                fetch. Can be null if the demographics are app-level.
     * @param userId  The userId of the user who is associated with the
     *                DemographicUser to be fetched.
     * @return a DemographicUser, or empty if it was not found.
     */
    Optional<DemographicUser> getDemographicUser(String appId, String studyId, String userId);

    /**
     * Fetches all app-level DemographicUsers for an app or all study-level
     * DemographicUsers for a study.
     * 
     * @param appId    The appId of the app which contains the DemographicUsers to
     *                 fetch.
     * @param studyId  The studyId of the study which contains the DemographicUsers
     *                 to fetch. Can be null if the demographics are app-level.
     * @param offsetBy The offset at which the returned list of DemographicUsers
     *                 should begin.
     * @param pageSize The maximum number of entries in the returned list of
     *                 DemographicUsers.
     * @return a paged list of fetched DemographicUsers.
     */
    PagedResourceList<DemographicUser> getDemographicUsers(String appId, String studyId, int offsetBy, int pageSize);
}
