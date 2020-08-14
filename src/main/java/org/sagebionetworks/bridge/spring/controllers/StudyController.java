package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.StudyService;

@CrossOrigin
@RestController
public class StudyController extends BaseController {

    static final StatusMessage DELETED_MSG = new StatusMessage("Study deleted.");
    private static final String INCLUDE_DELETED = "includeDeleted";
    private StudyService service;

    @Autowired
    final void setStudyService(StudyService studyService) {
        this.service = studyService;
    }

    @GetMapping(path = {"/v5/studies", "/v3/substudies"})
    public ResourceList<Study> getStudies(@RequestParam(defaultValue = "false") boolean includeDeleted) {
        UserSession session = getAuthenticatedSession(DEVELOPER, RESEARCHER, ADMIN);

        List<Study> studies = service.getStudies(session.getAppId(), includeDeleted);

        return new ResourceList<>(studies, true).withRequestParam(INCLUDE_DELETED, includeDeleted);
    }

    @PostMapping(path = {"/v5/studies", "/v3/substudies"})
    @ResponseStatus(HttpStatus.CREATED)
    public VersionHolder createStudy() {
        UserSession session = getAuthenticatedSession(SUPERADMIN);

        Study study = parseJson(Study.class);
        return service.createStudy(session.getAppId(), study);
    }

    @GetMapping(path = {"/v5/studies/{id}", "/v3/substudies/{id}"})
    public Study getStudy(@PathVariable String id) {
        UserSession session = getAuthenticatedSession(SUPERADMIN);

        return service.getStudy(session.getAppId(), id, true);
    }

    @PostMapping(path = {"/v5/studies/{id}", "/v3/substudies/{id}"})
    public VersionHolder updateStudy(@PathVariable String id) {
        UserSession session = getAuthenticatedSession(SUPERADMIN);

        Study study = parseJson(Study.class);
        return service.updateStudy(session.getAppId(), study);
    }

    @DeleteMapping(path = {"/v5/studies/{id}", "/v3/substudies/{id}"})
    public StatusMessage deleteStudy(@PathVariable String id,
            @RequestParam(defaultValue = "false") boolean physical) {
        UserSession session = getAuthenticatedSession(SUPERADMIN);

        if (physical) {
            service.deleteStudyPermanently(session.getAppId(), id);
        } else {
            service.deleteStudy(session.getAppId(), id);
        }
        return DELETED_MSG;
    }
}