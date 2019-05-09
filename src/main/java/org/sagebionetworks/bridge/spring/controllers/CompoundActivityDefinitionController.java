package org.sagebionetworks.bridge.spring.controllers;

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

import org.sagebionetworks.bridge.Roles;
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
    @PostMapping("/v3/compoundactivitydefinitions")
    @ResponseStatus(HttpStatus.CREATED)
    public String createCompoundActivityDefinition() throws JsonProcessingException, IOException {
        UserSession session = getAuthenticatedSession(Roles.DEVELOPER);

        CompoundActivityDefinition requestDef = parseJson(CompoundActivityDefinition.class);
        CompoundActivityDefinition createdDef = compoundActivityDefService.createCompoundActivityDefinition(
                session.getStudyIdentifier(), requestDef);
        return CompoundActivityDefinition.PUBLIC_DEFINITION_WRITER.writeValueAsString(createdDef);
    }

    /** Deletes a compound activity definition. */
    @DeleteMapping("/v3/compoundactivitydefinitions/{taskId}")
    public StatusMessage deleteCompoundActivityDefinition(@PathVariable String taskId) {
        UserSession session = getAuthenticatedSession(Roles.DEVELOPER);

        compoundActivityDefService.deleteCompoundActivityDefinition(session.getStudyIdentifier(), taskId);
        return new StatusMessage("Compound activity definition has been deleted.");
    }

    /** List all compound activity definitions in a study. */
    @GetMapping("/v3/compoundactivitydefinitions")
    public String getAllCompoundActivityDefinitionsInStudy() throws JsonProcessingException, IOException {
        UserSession session = getAuthenticatedSession(Roles.DEVELOPER);

        List<CompoundActivityDefinition> defList = compoundActivityDefService.getAllCompoundActivityDefinitionsInStudy(
                session.getStudyIdentifier());
        ResourceList<CompoundActivityDefinition> defResourceList = new ResourceList<>(defList);
        return CompoundActivityDefinition.PUBLIC_DEFINITION_WRITER.writeValueAsString(defResourceList);
    }

    /** Get a compound activity definition by ID. */
    @GetMapping("/v3/compoundactivitydefinitions/{taskId}")
    public String getCompoundActivityDefinition(@PathVariable String taskId) throws JsonProcessingException, IOException {
        UserSession session = getAuthenticatedSession(Roles.DEVELOPER);

        CompoundActivityDefinition def = compoundActivityDefService.getCompoundActivityDefinition(
                session.getStudyIdentifier(), taskId);
        return CompoundActivityDefinition.PUBLIC_DEFINITION_WRITER.writeValueAsString(def);
    }

    /** Update a compound activity definition. */
    @PostMapping("/v3/compoundactivitydefinitions/{taskId}")
    public String updateCompoundActivityDefinition(@PathVariable String taskId) throws JsonProcessingException, IOException {
        UserSession session = getAuthenticatedSession(Roles.DEVELOPER);

        CompoundActivityDefinition requestDef = parseJson(CompoundActivityDefinition.class);
        CompoundActivityDefinition updatedDef = compoundActivityDefService.updateCompoundActivityDefinition(
                session.getStudyIdentifier(), taskId, requestDef);
        return CompoundActivityDefinition.PUBLIC_DEFINITION_WRITER.writeValueAsString(updatedDef);
    }
}
