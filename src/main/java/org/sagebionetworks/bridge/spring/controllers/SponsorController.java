package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.springframework.http.HttpStatus.CREATED;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.models.organizations.Organization;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;

@CrossOrigin
@RestController
public class SponsorController extends BaseController {
    
    static final String REMOVE_SPONSOR_MSG = "Organization '%s' removed as a sponsor of study '%s'.";
    static final String ADD_SPONSOR_MSG = "Organization '%s' added as a sponsor of study '%s'.";

    @GetMapping("/v5/studies/{studyId}/sponsors")
    public PagedResourceList<Organization> getStudySponsors(@PathVariable String studyId, 
            @RequestParam(required = false) String offsetBy, 
            @RequestParam(required = false) String pageSize) {
        // Anyone can see the sponsoring organizations of a study.
        UserSession session = getAdminSession();
        
        int offsetByInt = BridgeUtils.getIntOrDefault(offsetBy, 0);
        int pageSizeInt = BridgeUtils.getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);
        
        return sponsorService.getStudySponsors(session.getAppId(), studyId, offsetByInt, pageSizeInt);
    }
    
    // Will not return logically deleted studies.
    @GetMapping("/v1/organizations/{orgId}/studies")
    public PagedResourceList<Study> getSponsoredStudies(@PathVariable String orgId, 
            @RequestParam(required = false) String offsetBy, 
            @RequestParam(required = false) String pageSize) {
        // Anyone can see the sponsored studies of an organization.
        UserSession session = getAdminSession();

        int offsetByInt = BridgeUtils.getIntOrDefault(offsetBy, 0);
        int pageSizeInt = BridgeUtils.getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);
        
        return sponsorService.getSponsoredStudies(session.getAppId(), orgId, offsetByInt, pageSizeInt);
    }
    
    @PostMapping(path = {"/v5/studies/{studyId}/sponsors/{orgId}", 
            "/v1/organizations/{orgId}/studies/{studyId}"})
    @ResponseStatus(code = CREATED)
    public StatusMessage addStudySponsor(
            @PathVariable(name = "studyId") String studyId, 
            @PathVariable(name = "orgId") String orgId) {
        UserSession session = getAuthenticatedSession(ADMIN);
        
        sponsorService.addStudySponsor(session.getAppId(), studyId, orgId);
        return new StatusMessage(String.format(ADD_SPONSOR_MSG, orgId, studyId));
    }
        
    @DeleteMapping(path = {"/v5/studies/{studyId}/sponsors/{orgId}", 
            "/v1/organizations/{orgId}/studies/{studyId}"})
    public StatusMessage removeStudySponsor(
            @PathVariable(name = "studyId") String studyId,
            @PathVariable(name = "orgId") String orgId) {
        UserSession session = getAuthenticatedSession(ADMIN);
        
        sponsorService.removeStudySponsor(session.getAppId(), studyId, orgId);
        return new StatusMessage(String.format(REMOVE_SPONSOR_MSG, orgId, studyId));
    }
}
