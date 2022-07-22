package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;

import java.util.Optional;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.DemographicUser;
import org.sagebionetworks.bridge.models.studies.DemographicUserAssessment;
import org.sagebionetworks.bridge.models.studies.Enrollment;
import org.sagebionetworks.bridge.services.DemographicService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;

@CrossOrigin
@RestController
public class DemographicController extends BaseController {
    private DemographicService demographicService;

    @Autowired
    public final void setDemographicService(DemographicService demographicService) {
        this.demographicService = demographicService;
    }

    // Save/update all demographics for a user
    @PostMapping({ "/v5/studies/{studyId}/participants/{userId}/demographics", // TODO check version
            "/v1/apps/self/participants/{userId}/demographics" })
    public DemographicUser saveDemographicUser(@PathVariable(required = false) Optional<String> studyId,
            @PathVariable String userId) throws MismatchedInputException, BadRequestException {
        String studyIdNull = studyId.orElse(null);

        // TODO researchers, study coordinators
        UserSession session = getAdministrativeSession();
        checkAccountExistsInStudy(session.getAppId(), studyIdNull, userId);

        DemographicUser demographicUser = parseJson(DemographicUser.class);
        if (demographicUser == null) {
            throw new BadRequestException("invalid JSON for user demographics");
        }
        demographicUser.setAppId(session.getAppId());
        demographicUser.setStudyId(studyIdNull);
        demographicUser.setUserId(userId);
        return demographicService.saveDemographicUser(demographicUser);
    }

    // TODO convert this endpoint to normal model
    // Save/update all demographics for a user
    @PostMapping({ "/v5/studies/{studyId}/participants/{userId}/demographics/assessment", // TODO check version
            "/v1/apps/self/participants/{userId}/demographics/assessment" })
    public DemographicUser saveDemographicUserAssessment(@PathVariable(required = false) Optional<String> studyId,
            @PathVariable String userId) throws MismatchedInputException, BadRequestException {
        String studyIdNull = studyId.orElse(null);

        // TODO researchers, study coordinators
        UserSession session = getAdministrativeSession();
        checkAccountExistsInStudy(session.getAppId(), studyIdNull, userId);

        DemographicUser demographicUser = parseJson(DemographicUserAssessment.class).getDemographicUser();
        if (demographicUser == null) {
            throw new BadRequestException("invalid JSON for user demographics");
        }
        demographicUser.setAppId(session.getAppId());
        demographicUser.setStudyId(studyIdNull);
        demographicUser.setUserId(userId);
        return demographicService.saveDemographicUser(demographicUser);
    }

    // Delete a specific demographic for a user
    @DeleteMapping({ "/v5/studies/{studyId}/participants/{userId}/demographics/{demographicId}",
            "/v1/apps/self/participants/{userId}/demographics/{demographicId}" })
    public void deleteDemographic(@PathVariable(required = false) Optional<String> studyId, @PathVariable String userId,
            @PathVariable String demographicId) throws EntityNotFoundException {
        String studyIdNull = studyId.orElse(null);

        UserSession session = getAdministrativeSession();
        checkAccountExistsInStudy(session.getAppId(), studyIdNull, userId);

        demographicService.deleteDemographic(session.getAppId(), studyIdNull, userId, demographicId);
    }

    // Delete all demographics for a user
    @DeleteMapping({ "/v5/studies/{studyId}/participants/{userId}/demographics",
            "/v1/apps/self/participants/{userId}/demographics" })
    public void deleteDemographicUser(@PathVariable(required = false) Optional<String> studyId,
            @PathVariable String userId) throws EntityNotFoundException {
        String studyIdNull = studyId.orElse(null);

        UserSession session = getAdministrativeSession();
        checkAccountExistsInStudy(session.getAppId(), studyIdNull, userId);

        demographicService.deleteDemographicUser(session.getAppId(), studyIdNull, userId);
    }

    // Get all demographics for a user
    @GetMapping({ "/v5/studies/{studyId}/participants/{userId}/demographics",
            "/v1/apps/self/participants/{userId}/demographics" })
    public DemographicUser getDemographicUser(@PathVariable(required = false) Optional<String> studyId,
            @PathVariable String userId) throws EntityNotFoundException {
        String studyIdNull = studyId.orElse(null);

        UserSession session = getAdministrativeSession();
        checkAccountExistsInStudy(session.getAppId(), studyIdNull, userId);

        return demographicService.getDemographicUser(session.getAppId(), studyIdNull, userId);
    }

    // Get all demographics for all users
    // Paged with offset
    @GetMapping({ "/v5/studies/{studyId}/participants/demographics", "/v1/apps/self/participants/demographics" })
    public PagedResourceList<DemographicUser> getDemographicUsers(
            @PathVariable(required = false) Optional<String> studyId,
            @RequestParam(required = false) String offsetBy, @RequestParam(required = false) String pageSize)
            throws BadRequestException {
        String studyIdNull = studyId.orElse(null);

        UserSession session = getAdministrativeSession();
        int offsetInt = BridgeUtils.getIntOrDefault(offsetBy, 0);
        int pageSizeInt = BridgeUtils.getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);

        return demographicService.getDemographicUsers(session.getAppId(), studyIdNull, offsetInt, pageSizeInt);
    }

    public void checkAccountExistsInStudy(String appId, String studyId, String userId) throws EntityNotFoundException {
        AccountId accountId = BridgeUtils.parseAccountId(appId, userId);
        Account account = accountService.getAccount(accountId)
                .orElseThrow(() -> new EntityNotFoundException(Account.class));
        if (studyId != null) {
            BridgeUtils.getElement(account.getEnrollments(), Enrollment::getStudyId, studyId)
                    .orElseThrow(() -> new EntityNotFoundException(Account.class));
        }
    }
}
