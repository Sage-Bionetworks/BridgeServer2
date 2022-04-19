package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.models.schedules.CompoundActivityDefinition.PUBLIC_DEFINITION_WRITER;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.schedules.CompoundActivityDefinition;
import org.sagebionetworks.bridge.services.CompoundActivityDefinitionService;

@CrossOrigin
@RestController
public class CompoundActivityDefinitionController extends BaseController {
    
    private CompoundActivityDefinitionService compoundActivityDefService;

    /** Service for Compound Activity Definitions, managed by Spring. */
    @Autowired
    final void setCompoundActivityDefService(CompoundActivityDefinitionService compoundActivityDefService) {
        this.compoundActivityDefService = compoundActivityDefService;
    }

    /** Creates a compound activity definition. */
    @PostMapping(path="/v3/compoundactivitydefinitions", produces={APPLICATION_JSON_VALUE})
    @ResponseStatus(HttpStatus.CREATED)
    public String createCompoundActivityDefinition() throws JsonProcessingException, IOException {
        UserSession session = getAuthenticatedSession(DEVELOPER);

        CompoundActivityDefinition requestDef = parseJson(CompoundActivityDefinition.class);
        CompoundActivityDefinition createdDef = compoundActivityDefService.createCompoundActivityDefinition(
                session.getAppId(), requestDef);
        return PUBLIC_DEFINITION_WRITER.writeValueAsString(createdDef);
    }

    /** Deletes a compound activity definition. */
    @DeleteMapping("/v3/compoundactivitydefinitions/{taskId}")
    public StatusMessage deleteCompoundActivityDefinition(@PathVariable String taskId) {
        UserSession session = getAuthenticatedSession(DEVELOPER);

        compoundActivityDefService.deleteCompoundActivityDefinition(session.getAppId(), taskId);
        return new StatusMessage("Compound activity definition has been deleted.");
    }

    /** List all compound activity definitions in a app. */
    @GetMapping(path="/v3/compoundactivitydefinitions", produces={APPLICATION_JSON_VALUE})
    public String getAllCompoundActivityDefinitionsInApp() throws JsonProcessingException, IOException {
        UserSession session = getAuthenticatedSession(DEVELOPER);

        List<CompoundActivityDefinition> defList = compoundActivityDefService.getAllCompoundActivityDefinitionsInApp(
                session.getAppId());
        ResourceList<CompoundActivityDefinition> defResourceList = new ResourceList<>(defList);
        return PUBLIC_DEFINITION_WRITER.writeValueAsString(defResourceList);
    }

    /** Get a compound activity definition by ID. */
    @GetMapping(path="/v3/compoundactivitydefinitions/{taskId}", produces={APPLICATION_JSON_VALUE})
    public String getCompoundActivityDefinition(@PathVariable String taskId) throws JsonProcessingException, IOException {
        UserSession session = getAuthenticatedSession(DEVELOPER);

        CompoundActivityDefinition def = compoundActivityDefService.getCompoundActivityDefinition(
                session.getAppId(), taskId);
        return PUBLIC_DEFINITION_WRITER.writeValueAsString(def);
    }

    /** Update a compound activity definition. */
    @PostMapping(path="/v3/compoundactivitydefinitions/{taskId}", produces={APPLICATION_JSON_VALUE})
    public String updateCompoundActivityDefinition(@PathVariable String taskId) throws JsonProcessingException, IOException {
        UserSession session = getAuthenticatedSession(DEVELOPER);

        CompoundActivityDefinition requestDef = parseJson(CompoundActivityDefinition.class);
        CompoundActivityDefinition updatedDef = compoundActivityDefService.updateCompoundActivityDefinition(
                session.getAppId(), taskId, requestDef);
        return PUBLIC_DEFINITION_WRITER.writeValueAsString(updatedDef);
    }
}
