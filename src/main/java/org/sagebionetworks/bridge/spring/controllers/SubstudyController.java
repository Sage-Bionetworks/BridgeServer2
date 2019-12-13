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
import org.sagebionetworks.bridge.models.substudies.Substudy;
import org.sagebionetworks.bridge.services.SubstudyService;

@CrossOrigin
@RestController
public class SubstudyController extends BaseController {

    static final StatusMessage DELETED_MSG = new StatusMessage("Substudy deleted.");
    private static final String INCLUDE_DELETED = "includeDeleted";
    private SubstudyService service;

    @Autowired
    final void setSubstudyService(SubstudyService substudyService) {
        this.service = substudyService;
    }

    @GetMapping("/v3/substudies")
    public ResourceList<Substudy> getSubstudies(@RequestParam(defaultValue = "false") boolean includeDeleted) {
        UserSession session = getAuthenticatedSession(DEVELOPER, RESEARCHER, ADMIN);

        List<Substudy> substudies = service.getSubstudies(session.getStudyIdentifier(), includeDeleted);

        return new ResourceList<>(substudies).withRequestParam(INCLUDE_DELETED, includeDeleted);
    }

    @PostMapping("/v3/substudies")
    @ResponseStatus(HttpStatus.CREATED)
    public VersionHolder createSubstudy() {
        UserSession session = getAuthenticatedSession(SUPERADMIN);

        Substudy substudy = parseJson(Substudy.class);
        return service.createSubstudy(session.getStudyIdentifier(), substudy);
    }

    @GetMapping("/v3/substudies/{id}")
    public Substudy getSubstudy(@PathVariable String id) {
        UserSession session = getAuthenticatedSession(SUPERADMIN);

        return service.getSubstudy(session.getStudyIdentifier(), id, true);
    }

    @PostMapping("/v3/substudies/{id}")
    public VersionHolder updateSubstudy(@PathVariable String id) {
        UserSession session = getAuthenticatedSession(SUPERADMIN);

        Substudy substudy = parseJson(Substudy.class);
        return service.updateSubstudy(session.getStudyIdentifier(), substudy);
    }

    @DeleteMapping("/v3/substudies/{id}")
    public StatusMessage deleteSubstudy(@PathVariable String id,
            @RequestParam(defaultValue = "false") boolean physical) {
        UserSession session = getAuthenticatedSession(SUPERADMIN);

        if (physical) {
            service.deleteSubstudyPermanently(session.getStudyIdentifier(), id);
        } else {
            service.deleteSubstudy(session.getStudyIdentifier(), id);
        }
        return DELETED_MSG;
    }
}