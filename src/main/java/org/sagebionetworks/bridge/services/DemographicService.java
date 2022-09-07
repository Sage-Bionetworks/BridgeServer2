package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.PAGE_SIZE_ERROR;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.DemographicDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicUser;
import org.sagebionetworks.bridge.validators.DemographicUserValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Service for Demographic related operations.
 */
@Component
public class DemographicService {
    private ParticipantVersionService participantVersionService;

    private DemographicDao demographicDao;

    @Autowired
    public final void setDemographicDao(DemographicDao demographicDao) {
        this.demographicDao = demographicDao;
    }

    @Autowired
    public final void setParticipantVersionService(ParticipantVersionService participantVersionService) {
        this.participantVersionService = participantVersionService;
    }

    /**
     * Saves or overwrites a DemographicUser.
     * 
     * @param demographicUser The DemographicUser to save/overwrite.
     * @param account         The account of the user owning the demographics.
     * @return The DemographicUser that was saved.
     * @throws InvalidEntityException if demographicUser is invalid.
     */
    public DemographicUser saveDemographicUser(DemographicUser demographicUser, Account account)
            throws InvalidEntityException {
        demographicUser.setId(generateGuid());
        if (demographicUser.getDemographics() != null) {
            for (Demographic demographic : demographicUser.getDemographics().values()) {
                demographic.setId(generateGuid());
                demographic.setDemographicUser(demographicUser);
            }
        }
        Validate.entityThrowingException(DemographicUserValidator.INSTANCE, demographicUser);
        DemographicUser savedDemographicUser = demographicDao.saveDemographicUser(demographicUser, demographicUser.getAppId(),
                demographicUser.getStudyId(), demographicUser.getUserId());
        participantVersionService.createParticipantVersionFromAccount(account);
        return savedDemographicUser;
    }

    /**
     * Deletes a Demographic.
     * 
     * @param userId        The userId of the user who owns the Demographic to
     *                      delete.
     * @param demographicId The id of the Demographic to delete.
     * @param account       The account of the user owning the demographics.
     * @throws EntityNotFoundException if the Demographic does not exist or the
     *                                 specified user does not own the specified
     *                                 Demographic.
     */
    public void deleteDemographic(String userId, String demographicId, Account account) throws EntityNotFoundException {
        Demographic existingDemographic = demographicDao.getDemographic(demographicId)
                .orElseThrow(() -> new EntityNotFoundException(Demographic.class));
        if (!existingDemographic.getDemographicUser().getUserId().equals(userId)) {
            // user does not own this demographic
            // just give them a 404 because we don't want to expose the existence of another
            // user's demographic data
            throw new EntityNotFoundException(Demographic.class);
        }
        demographicDao.deleteDemographic(demographicId);
        participantVersionService.createParticipantVersionFromAccount(account);
    }

    /**
     * Deletes a DemographicUser (all demographics for a user).
     * 
     * @param appId   The appId of the app which contains the DemographicUser to
     *                delete.
     * @param studyId The studyId of the study which contains the DemographicUser to
     *                delete. Can be null if the demographics are app-level.
     * @param account The account of the user owning the demographics.
     * @param userId  The userId of the user to delete demographics for.
     * @throws EntityNotFoundException if the DemographicUser to delete does not
     *                                 exist based on the provided appId, studyId,
     *                                 and userId.
     */
    public void deleteDemographicUser(String appId, String studyId, String userId, Account account) throws EntityNotFoundException {
        String existingDemographicUserId = demographicDao.getDemographicUserId(appId, studyId, userId)
                .orElseThrow(() -> new EntityNotFoundException(DemographicUser.class));
        demographicDao.deleteDemographicUser(existingDemographicUserId);
        participantVersionService.createParticipantVersionFromAccount(account);
    }

    /**
     * Fetches a DemographicUser.
     * 
     * @param appId   The appId of the app which contains the DemographicUser to
     *                fetch.
     * @param studyId The studyId of the study which contains the DemographicUser to
     *                fetch. Can be null if the demographics are app-level.
     * @param userId  The userId of the user to fetch demographics for.
     * @return The fetched DemographicUser.
     * @throws EntityNotFoundException if the DemographicUser to fetch does not
     *                                 exist based on the provided appId, studyId,
     *                                 and userId.
     */
    public DemographicUser getDemographicUser(String appId, String studyId, String userId)
            throws EntityNotFoundException {
        DemographicUser existingDemographicUser = demographicDao.getDemographicUser(appId, studyId, userId)
                .orElseThrow(() -> new EntityNotFoundException(DemographicUser.class));
        return existingDemographicUser;
    }

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
     * @throws BadRequestException if the pageSize is invalid.
     */
    public PagedResourceList<DemographicUser> getDemographicUsers(String appId, String studyId, int offsetBy,
            int pageSize) throws BadRequestException {
        if (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(PAGE_SIZE_ERROR);
        }
        return demographicDao.getDemographicUsers(appId, studyId, offsetBy, pageSize);
    }

    /**
     * Generates a guid.
     * 
     * @return a generated guid.
     */
    public String generateGuid() {
        return BridgeUtils.generateGuid();
    }
}
