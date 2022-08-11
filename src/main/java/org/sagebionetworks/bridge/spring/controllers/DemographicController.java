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

@CrossOrigin
@RestController
public class DemographicController extends BaseController {
    private static final StatusMessage DELETE_DEMOGRAPHIC_MESSAGE = new StatusMessage("Demographic successfully deleted");
    private static final StatusMessage DELETE_DEMOGRAPHIC_USER_MESSAGE = new StatusMessage("Demographic user successfully deleted");
    
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

    // Save/update all demographics for a user
    @PostMapping({ "/v5/studies/{studyId}/participants/{userId}/demographics",
            "/v5/studies/{studyId}/participants/self/demographics",
            "/v1/apps/self/participants/{userId}/demographics",
            "/v1/apps/self/participants/self/demographics" })
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
            session = getAdministrativeSession();
            userIdUnwrapped = userId.get();
        } else {
            // posted by the user
            session = getAuthenticatedAndConsentedSession();
            userIdUnwrapped = session.getId();
        }
        participantService.getAccountInStudy(session.getAppId(), studyIdNullable, userIdUnwrapped);

        DemographicUser demographicUser = parseJson(DemographicUser.class);
        if (demographicUser == null) {
            throw new BadRequestException("invalid JSON for user demographics");
        }
        demographicUser.setAppId(session.getAppId());
        demographicUser.setStudyId(studyIdNullable);
        demographicUser.setUserId(userIdUnwrapped);
        return demographicService.saveDemographicUser(demographicUser);
    }

    // Save/update all demographics for a user
    @PostMapping({ "/v5/studies/{studyId}/participants/{userId}/demographics/assessment",
            "/v5/studies/{studyId}/participants/self/demographics/assessment",
            "/v1/apps/self/participants/{userId}/demographics/assessment",
            "/v1/apps/self/participants/self/demographics/assessment" })
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
            session = getAdministrativeSession();
            userIdUnwrapped = userId.get();
        } else {
            // posted by the user, either at an app or study level
            session = getAuthenticatedAndConsentedSession();
            userIdUnwrapped = session.getId();
        }
        participantService.getAccountInStudy(session.getAppId(), studyIdNullable, userIdUnwrapped);

        DemographicUserAssessment demographicUserAssessment = parseJson(DemographicUserAssessment.class);
        if (demographicUserAssessment == null) {
            throw new BadRequestException("invalid JSON for user demographics");
        }
        DemographicUser demographicUser = demographicUserAssessment.getDemographicUser();
        demographicUser.setAppId(session.getAppId());
        demographicUser.setStudyId(studyIdNullable);
        demographicUser.setUserId(userIdUnwrapped);
        return demographicService.saveDemographicUser(demographicUser);
    }

    // Delete a specific demographic for a user
    @DeleteMapping({ "/v5/studies/{studyId}/participants/{userId}/demographics/{demographicId}",
            "/v1/apps/self/participants/{userId}/demographics/{demographicId}" })
    public StatusMessage deleteDemographic(@PathVariable(required = false) Optional<String> studyId, @PathVariable String userId,
            @PathVariable String demographicId) throws EntityNotFoundException, NotAuthenticatedException,
            UnauthorizedException, ConsentRequiredException, UnsupportedVersionException {
        String studyIdNull = studyId.orElse(null);

        UserSession session;
        if (studyId.isPresent()) {
            // study level demographics
            session = getAuthenticatedSession(Roles.RESEARCHER, Roles.STUDY_COORDINATOR);
        } else {
            // app level demographics
            session = getAdministrativeSession();
        }
        participantService.getAccountInStudy(session.getAppId(), studyIdNull, userId);

        demographicService.deleteDemographic(userId, demographicId);
        return DELETE_DEMOGRAPHIC_MESSAGE;
    }

    // Delete all demographics for a user
    @DeleteMapping({ "/v5/studies/{studyId}/participants/{userId}/demographics",
            "/v1/apps/self/participants/{userId}/demographics" })
    public StatusMessage deleteDemographicUser(@PathVariable(required = false) Optional<String> studyId,
            @PathVariable String userId) throws EntityNotFoundException, NotAuthenticatedException,
            UnauthorizedException, ConsentRequiredException, UnsupportedVersionException {
        String studyIdNull = studyId.orElse(null);

        UserSession session;
        if (studyId.isPresent()) {
            // study level demographics
            session = getAuthenticatedSession(Roles.RESEARCHER, Roles.STUDY_COORDINATOR);
        } else {
            // app level demographics
            session = getAdministrativeSession();
        }
        participantService.getAccountInStudy(session.getAppId(), studyIdNull, userId);

        demographicService.deleteDemographicUser(session.getAppId(), studyIdNull, userId);
        return DELETE_DEMOGRAPHIC_USER_MESSAGE;
    }

    // Get all demographics for a user
    @GetMapping({ "/v5/studies/{studyId}/participants/{userId}/demographics",
            "/v1/apps/self/participants/{userId}/demographics" })
    public DemographicUser getDemographicUser(@PathVariable(required = false) Optional<String> studyId,
            @PathVariable String userId) throws EntityNotFoundException, NotAuthenticatedException,
            UnauthorizedException, ConsentRequiredException, UnsupportedVersionException {
        String studyIdNull = studyId.orElse(null);

        UserSession session;
        if (studyId.isPresent()) {
            // study level demographics
            session = getAuthenticatedSession(Roles.RESEARCHER, Roles.STUDY_COORDINATOR);
        } else {
            // app level demographics
            session = getAdministrativeSession();
        }
        participantService.getAccountInStudy(session.getAppId(), studyIdNull, userId);

        return demographicService.getDemographicUser(session.getAppId(), studyIdNull, userId);
    }

    // Get all demographics for all users
    // Paged with offset
    @GetMapping({ "/v5/studies/{studyId}/participants/demographics", "/v1/apps/self/participants/demographics" })
    public PagedResourceList<DemographicUser> getDemographicUsers(
            @PathVariable(required = false) Optional<String> studyId,
            @RequestParam(required = false) String offsetBy, @RequestParam(required = false) String pageSize)
            throws BadRequestException, NotAuthenticatedException,
            UnauthorizedException, ConsentRequiredException, UnsupportedVersionException {
        String studyIdNull = studyId.orElse(null);

        UserSession session;
        if (studyId.isPresent()) {
            // study level demographics
            session = getAuthenticatedSession(Roles.RESEARCHER, Roles.STUDY_COORDINATOR);
        } else {
            // app level demographics
            session = getAdministrativeSession();
        }
        int offsetInt = BridgeUtils.getIntOrDefault(offsetBy, 0);
        int pageSizeInt = BridgeUtils.getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);

        return demographicService.getDemographicUsers(session.getAppId(), studyIdNull, offsetInt, pageSizeInt);
    }
}
