package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;

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

import org.sagebionetworks.bridge.models.GuidVersionHolder;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.SubpopulationService;

@CrossOrigin
@RestController
public class SubpopulationController extends BaseController {

    static final StatusMessage DELETED_MSG = new StatusMessage("Subpopulation has been deleted.");

    private SubpopulationService subpopService;

    @Autowired
    final void setSubpopulationService(SubpopulationService subpopService) {
        this.subpopService = subpopService;
    }

    @GetMapping(path="/v3/subpopulations", produces={APPLICATION_JSON_UTF8_VALUE})
    public String getAllSubpopulations(@RequestParam(defaultValue = "false") boolean includeDeleted) throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);

        List<Subpopulation> subpopulations = subpopService.getSubpopulations(session.getStudyIdentifier(),
                includeDeleted);

        return Subpopulation.SUBPOP_WRITER.writeValueAsString(new ResourceList<Subpopulation>(subpopulations));
    }

    @PostMapping("/v3/subpopulations")
    @ResponseStatus(HttpStatus.CREATED)
    public GuidVersionHolder createSubpopulation() {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        Subpopulation subpop = parseJson(Subpopulation.class);
        subpop = subpopService.createSubpopulation(study, subpop);

        return new GuidVersionHolder(subpop.getGuidString(), subpop.getVersion());
    }

    @PostMapping("/v3/subpopulations/{guid}")
    public GuidVersionHolder updateSubpopulation(@PathVariable String guid) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        Subpopulation subpop = parseJson(Subpopulation.class);
        subpop.setGuidString(guid);

        subpop = subpopService.updateSubpopulation(study, subpop);

        return new GuidVersionHolder(subpop.getGuidString(), subpop.getVersion());
    }

    @GetMapping(path="/v3/subpopulations/{guid}", produces={APPLICATION_JSON_UTF8_VALUE})
    public String getSubpopulation(@PathVariable String guid) throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER, RESEARCHER);
        SubpopulationGuid subpopGuid = SubpopulationGuid.create(guid);

        Subpopulation subpop = subpopService.getSubpopulation(session.getStudyIdentifier(), subpopGuid);

        return Subpopulation.SUBPOP_WRITER.writeValueAsString(subpop);
    }

    @DeleteMapping("/v3/subpopulations/{guid}")
    public StatusMessage deleteSubpopulation(@PathVariable String guid,
            @RequestParam(defaultValue = "false") boolean physical) {
        UserSession session = getAuthenticatedSession(ADMIN, DEVELOPER);

        SubpopulationGuid subpopGuid = SubpopulationGuid.create(guid);
        if (physical && session.isInRole(ADMIN)) {
            subpopService.deleteSubpopulationPermanently(session.getStudyIdentifier(), subpopGuid);
        } else {
            subpopService.deleteSubpopulation(session.getStudyIdentifier(), subpopGuid);
        }
        return DELETED_MSG;
    }
}