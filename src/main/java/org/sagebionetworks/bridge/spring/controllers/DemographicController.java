package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.DemographicUser;
import org.sagebionetworks.bridge.models.studies.Enrollment;
import org.sagebionetworks.bridge.services.DemographicService;
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
    private DemographicService demographicService;

    // Save/update all demographics for a user
    @PostMapping("/v5/studies/{studyId}/participants/{userId}/demographics")
    public void saveDemographicUser(@PathVariable String studyId, @PathVariable String userId) {
        UserSession session = getAdministrativeSession();
        checkAccountExistsInStudy(session.getAppId(), studyId, userId);

        DemographicUser demographicUser = parseJson(DemographicUser.class);
        if (null == demographicUser) {
            throw new BadRequestException("invalid JSON for user demographics");
        }
        demographicUser.setAppId(session.getAppId());
        demographicUser.setStudyId(studyId);
        demographicUser.setUserId(userId);
        demographicService.saveDemographicUser(demographicUser);
    }

    // Delete a specific demographic for a user
    @DeleteMapping("/v5/studies/{studyId}/participants/{userId}/demographics/{categoryName}")
    public void deleteDemographic(@PathVariable String studyId, @PathVariable String userId,
            @PathVariable String categoryName) {
        UserSession session = getAdministrativeSession();
        checkAccountExistsInStudy(session.getAppId(), studyId, userId);

        demographicService.deleteDemographic(session.getAppId(), studyId, userId, categoryName);
    }

    // Delete all demographics for a user
    @DeleteMapping("/v5/studies/{studyId}/participants/{userId}/demographics")
    public void deleteDemographicUser(@PathVariable String studyId, @PathVariable String userId) {
        UserSession session = getAdministrativeSession();
        checkAccountExistsInStudy(session.getAppId(), studyId, userId);

        demographicService.deleteDemographicUser(session.getAppId(), studyId, userId);
    }

    // Get all demographics for a user
    @GetMapping("/v5/studies/{studyId}/participants/{userId}/demographics")
    public DemographicUser getDemographicUser(@PathVariable String studyId, @PathVariable String userId) {
        UserSession session = getAdministrativeSession();
        checkAccountExistsInStudy(session.getAppId(), studyId, userId);

        return demographicService.getDemographicUser(session.getAppId(), studyId, userId);
    }

    // Get all demographics for all users in the study
    // Paged with offset
    @GetMapping("/v5/studies/{studyId}/participants/demographics")
    public PagedResourceList<DemographicUser> getDemographicUsers(@PathVariable String studyId,
            @RequestParam(required = false) String offsetBy, @RequestParam(required = false) String pageSize) {
        UserSession session = getAdministrativeSession();
        int offsetInt = BridgeUtils.getIntOrDefault(offsetBy, 0);
        int pageSizeInt = BridgeUtils.getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);

        return demographicService.getDemographicUsers(session.getAppId(), studyId, offsetInt, pageSizeInt);
    }

    private void checkAccountExistsInStudy(String appId, String studyId, String userId) throws EntityNotFoundException {
        AccountId accountId = BridgeUtils.parseAccountId(appId, userId);
        Account account = accountService.getAccount(accountId)
                .orElseThrow(() -> new EntityNotFoundException(Account.class));
        BridgeUtils.getElement(account.getEnrollments(), Enrollment::getStudyId, studyId)
                .orElseThrow(() -> new EntityNotFoundException(Account.class));
    }
}
