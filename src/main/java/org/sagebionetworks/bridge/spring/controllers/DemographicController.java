package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;

import java.util.Optional;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.exceptions.UnsupportedVersionException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.DemographicUser;
import org.sagebionetworks.bridge.models.studies.DemographicUserAssessment;
import org.sagebionetworks.bridge.services.DemographicService;
import org.sagebionetworks.bridge.services.ParticipantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for demographic-related APIs.
 */
@CrossOrigin
@RestController
public class DemographicController extends BaseController {
    private static final StatusMessage DELETE_DEMOGRAPHIC_MESSAGE = new StatusMessage(
            "Demographic successfully deleted");
    private static final StatusMessage DELETE_DEMOGRAPHIC_USER_MESSAGE = new StatusMessage(
            "Demographic user successfully deleted");

    private DemographicService demographicService;

    private ParticipantService participantService;

    @Autowired
    public final void setDemographicService(DemographicService demographicService) {
        this.demographicService = demographicService;
    }

    @Autowired
    public final void setParticipantService(ParticipantService participantService) {
        this.participantService = participantService;
    }

    /**
     * Saves/updates all demographics for a user.
     * 
     * @param studyId The studyId of the study in which to save the DemographicUser.
     *                Can be empty if the demographics are app-level.
     * @param userId  The userId of the user to associate the DemographicUser with.
     *                Can be empty if the user themself is the caller.
     * @return The saved DemographicUser.
     * @throws BadRequestException         if the deserialized JSON is not in a
     *                                     valid format.
     * @throws EntityNotFoundException     if the user's account does not exist or
     *                                     the account is not in the specified
     *                                     study.
     * @throws InvalidEntityException      if the deserialized DemographicUser is
     *                                     not valid.
     * @throws NotAuthenticatedException   if the caller is not authenticated.
     * @throws UnauthorizedException       if called at the study level and not by
     *                                     the user themself but the caller is not a
     *                                     researcher or study-coordinator.
     * @throws ConsentRequiredException    if called by the user themself OR at an
     *                                     app level and not by the user themself
     *                                     but the caller is not consented.
     * @throws UnsupportedVersionException if the caller's app version is not
     *                                     supported.
     */
    @PostMapping({ "/v5/studies/{studyId}/participants/{userId}/demographics",
            "/v5/studies/{studyId}/participants/self/demographics",
            "/v3/participants/{userId}/demographics",
            "/v3/participants/self/demographics" })
    public DemographicUser saveDemographicUser(@PathVariable(required = false) Optional<String> studyId,
            @PathVariable(required = false) Optional<String> userId)
            throws BadRequestException, EntityNotFoundException, InvalidEntityException,
            NotAuthenticatedException, UnauthorizedException, ConsentRequiredException, UnsupportedVersionException {
        String studyIdNullable = studyId.orElse(null);

        UserSession session;
        String userIdUnwrapped;
        if (userId.isPresent() && studyId.isPresent()) {
            // posted on the user's behalf at a study level
            session = getAuthenticatedSession(Roles.RESEARCHER, Roles.STUDY_COORDINATOR);
            userIdUnwrapped = userId.get();
        } else if (userId.isPresent() && !studyId.isPresent()) {
            // posted on the user's behalf at an app level
            session = getAuthenticatedSession(Roles.ADMIN);
            userIdUnwrapped = userId.get();
        } else {
            // posted by the user
            session = getAuthenticatedAndConsentedSession();
            userIdUnwrapped = session.getId();
        }
        Account account = participantService.getAccountInStudy(session.getAppId(), studyIdNullable, userIdUnwrapped);

        DemographicUser demographicUser = parseJson(DemographicUser.class);
        if (demographicUser == null) {
            throw new BadRequestException("invalid JSON for user demographics");
        }
        demographicUser.setAppId(session.getAppId());
        demographicUser.setStudyId(studyIdNullable);
        demographicUser.setUserId(userIdUnwrapped);
        return demographicService.saveDemographicUser(demographicUser, account);
    }

    /**
     * Saves/updates all demographics for a user. POSTed JSON must be in the
     * assessment format.
     * 
     * @param studyId The studyId of the study in which to save the DemographicUser.
     *                Can be empty if the demographics are app-level.
     * @param userId  The userId of the user to associate the DemographicUser with.
     *                Can be empty if the user themself is the caller.
     * @return The saved DemographicUser.
     * @throws BadRequestException         if the deserialized JSON is not in a
     *                                     valid format.
     * @throws EntityNotFoundException     if the user's account does not exist or
     *                                     the account is not in the specified
     *                                     study.
     * @throws InvalidEntityException      if the deserialized DemographicUser is
     *                                     not valid.
     * @throws NotAuthenticatedException   if the caller is not authenticated.
     * @throws UnauthorizedException       if called at the study level and not by
     *                                     the user themself but the caller is not a
     *                                     researcher or study-coordinator.
     * @throws ConsentRequiredException    if called by the user themself OR at an
     *                                     app level and not by the user themself
     *                                     but the caller is not consented.
     * @throws UnsupportedVersionException if the caller's app version is not
     *                                     supported.
     */
    @PostMapping({ "/v5/studies/{studyId}/participants/{userId}/demographics/assessment",
            "/v5/studies/{studyId}/participants/self/demographics/assessment",
            "/v3/participants/{userId}/demographics/assessment",
            "/v3/participants/self/demographics/assessment" })
    public DemographicUser saveDemographicUserAssessment(@PathVariable(required = false) Optional<String> studyId,
            @PathVariable(required = false) Optional<String> userId)
            throws BadRequestException, EntityNotFoundException, InvalidEntityException,
            NotAuthenticatedException, UnauthorizedException, ConsentRequiredException, UnsupportedVersionException {
        String studyIdNullable = studyId.orElse(null);

        UserSession session;
        String userIdUnwrapped;
        if (userId.isPresent() && studyId.isPresent()) {
            // posted on the user's behalf at a study level
            session = getAuthenticatedSession(Roles.RESEARCHER, Roles.STUDY_COORDINATOR);
            userIdUnwrapped = userId.get();
        } else if (userId.isPresent() && !studyId.isPresent()) {
            // posted on the user's behalf at an app level
            session = getAuthenticatedSession(Roles.ADMIN);
            userIdUnwrapped = userId.get();
        } else {
            // posted by the user, either at an app or study level
            session = getAuthenticatedAndConsentedSession();
            userIdUnwrapped = session.getId();
        }
        Account account = participantService.getAccountInStudy(session.getAppId(), studyIdNullable, userIdUnwrapped);

        DemographicUserAssessment demographicUserAssessment = parseJson(DemographicUserAssessment.class);
        if (demographicUserAssessment == null) {
            throw new BadRequestException("invalid JSON for user demographics");
        }
        DemographicUser demographicUser = demographicUserAssessment.getDemographicUser();
        demographicUser.setAppId(session.getAppId());
        demographicUser.setStudyId(studyIdNullable);
        demographicUser.setUserId(userIdUnwrapped);
        return demographicService.saveDemographicUser(demographicUser, account);
    }

    /**
     * Deletes a specific Demographic for a user.
     * 
     * @param studyId       The studyId of the study which contains the Demographic
     *                      to delete. Can be empty if the demographics are
     *                      app-level.
     * @param userId        The userId of the user which owns the Demographic to
     *                      delete. Can be empty if the user themself is the caller.
     * @param demographicId The id of the Demographic to delete.
     * @return a success message if the deletion occurred successfully.
     * @throws EntityNotFoundException   if the user's account does not exist, the
     *                                   user is not in the specified study, the
     *                                   Demographic does not exist, or the user
     *                                   does not own the specified Demographic.
     * @throws NotAuthenticatedException if the caller is not authenticated.
     * @throws UnauthorizedException     if called at the study level but the
     *                                   caller is not a researcher or
     *                                   study-coordinator, or if called at the app
     *                                   level but the caller is not an admin.
     */
    @DeleteMapping({ "/v5/studies/{studyId}/participants/{userId}/demographics/{demographicId}",
            "/v3/participants/{userId}/demographics/{demographicId}" })
    public StatusMessage deleteDemographic(@PathVariable(required = false) Optional<String> studyId,
            @PathVariable String userId,
            @PathVariable String demographicId)
            throws EntityNotFoundException, NotAuthenticatedException, UnauthorizedException {
        String studyIdNull = studyId.orElse(null);

        UserSession session;
        if (studyId.isPresent()) {
            // study level demographics
            session = getAuthenticatedSession(Roles.RESEARCHER, Roles.STUDY_COORDINATOR);
        } else {
            // app level demographics
            session = getAuthenticatedSession(Roles.ADMIN);
        }
        Account account = participantService.getAccountInStudy(session.getAppId(), studyIdNull, userId);

        demographicService.deleteDemographic(userId, demographicId, account);
        return DELETE_DEMOGRAPHIC_MESSAGE;
    }

    /**
     * Deletes all demographics for a user.
     * 
     * @param studyId The studyId of the study which contains the DemographicUser to
     *                delete. Can be empty if the demographics are app-level.
     * @param userId  The userId of the DemographicUser to delete. Can be empty if
     *                the user themself is the caller.
     * @return a success message if the delete occurred successfully.
     * @throws EntityNotFoundException   if the user's account does not exist, the
     *                                   user is not in the specified study, or
     *                                   the DemographicUser to delete does not
     *                                   exist.
     * @throws NotAuthenticatedException if the caller is not authenticated.
     * @throws UnauthorizedException     if called at the study level but the
     *                                   caller is not a researcher or
     *                                   study-coordinator, or if called at the app
     *                                   level but the caller is not an admin.
     */
    @DeleteMapping({ "/v5/studies/{studyId}/participants/{userId}/demographics",
            "/v3/participants/{userId}/demographics" })
    public StatusMessage deleteDemographicUser(@PathVariable(required = false) Optional<String> studyId,
            @PathVariable String userId)
            throws EntityNotFoundException, NotAuthenticatedException, UnauthorizedException {
        String studyIdNull = studyId.orElse(null);

        UserSession session;
        if (studyId.isPresent()) {
            // study level demographics
            session = getAuthenticatedSession(Roles.RESEARCHER, Roles.STUDY_COORDINATOR);
        } else {
            // app level demographics
            session = getAuthenticatedSession(Roles.ADMIN);
        }
        Account account = participantService.getAccountInStudy(session.getAppId(), studyIdNull, userId);

        demographicService.deleteDemographicUser(session.getAppId(), studyIdNull, userId, account);
        return DELETE_DEMOGRAPHIC_USER_MESSAGE;
    }

    /**
     * Fetches a DemographicUser (all demographics for a user).
     * 
     * @param studyId The studyId of the study which contains the DemographicUser to
     *                fetch. Can be empty if the demographics are app-level.
     * @param userId  The userId of the DemographicUser to fetch. Can be empty if
     *                the user themself is the caller.
     * @return the fetched DemographicUser.
     * @throws EntityNotFoundException   if the user's account does not exist, the
     *                                   user is not in the specified study, or
     *                                   the DemographicUser to fetch does not
     *                                   exist.
     * @throws NotAuthenticatedException if the caller is not authenticated.
     * @throws UnauthorizedException     if called at the study level but the
     *                                   caller is not a researcher or
     *                                   study-coordinator, or if called at the app
     *                                   level but the caller is not an admin.
     */
    @GetMapping({ "/v5/studies/{studyId}/participants/{userId}/demographics",
            "/v3/participants/{userId}/demographics" })
    public DemographicUser getDemographicUser(@PathVariable(required = false) Optional<String> studyId,
            @PathVariable String userId)
            throws EntityNotFoundException, NotAuthenticatedException, UnauthorizedException {
        String studyIdNull = studyId.orElse(null);

        UserSession session;
        if (studyId.isPresent()) {
            // study level demographics
            session = getAuthenticatedSession(Roles.RESEARCHER, Roles.STUDY_COORDINATOR);
        } else {
            // app level demographics
            session = getAuthenticatedSession(Roles.ADMIN);
        }
        participantService.getAccountInStudy(session.getAppId(), studyIdNull, userId);

        return demographicService.getDemographicUser(session.getAppId(), studyIdNull, userId)
                .orElseThrow(() -> new EntityNotFoundException(DemographicUser.class));
    }

    /**
     * Fetches all app-level DemographicUsers for an app or all study-level
     * DemographicUsers for a study. Paged with offset.
     * 
     * @param studyId  The studyId of the study which contains the DemographicUsers
     *                 to fetch. Can be empty if the demographics are app-level.
     * @param offsetBy The offset at which the returned list of DemographicUsers
     *                 should begin.
     * @param pageSize The maximum number of entries in the returned list of
     *                 DemographicUsers.
     * @return A paged list of fetched DemographicUsers.
     * @throws BadRequestException       if the pageSize is invalid.
     * @throws NotAuthenticatedException if the caller is not authenticated.
     * @throws UnauthorizedException     if called at the study level but the
     *                                   caller is not a researcher or
     *                                   study-coordinator, or if called at the app
     *                                   level but the caller is not an admin.
     */
    @GetMapping({ "/v5/studies/{studyId}/participants/demographics", "/v3/participants/demographics" })
    public PagedResourceList<DemographicUser> getDemographicUsers(
            @PathVariable(required = false) Optional<String> studyId,
            @RequestParam(required = false) String offsetBy, @RequestParam(required = false) String pageSize)
            throws BadRequestException, NotAuthenticatedException, UnauthorizedException {
        String studyIdNull = studyId.orElse(null);

        UserSession session;
        if (studyId.isPresent()) {
            // study level demographics
            session = getAuthenticatedSession(Roles.RESEARCHER, Roles.STUDY_COORDINATOR);
        } else {
            // app level demographics
            session = getAuthenticatedSession(Roles.ADMIN);
        }
        int offsetInt = BridgeUtils.getIntOrDefault(offsetBy, 0);
        int pageSizeInt = BridgeUtils.getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);

        return demographicService.getDemographicUsers(session.getAppId(), studyIdNull, offsetInt, pageSizeInt);
    }
}
