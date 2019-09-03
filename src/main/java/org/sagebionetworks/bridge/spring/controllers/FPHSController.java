package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;

import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.FPHSExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.services.FPHSService;

@CrossOrigin
@RestController
public class FPHSController extends BaseController {
    
    private static final StudyIdentifier FPHS_ID = new StudyIdentifierImpl("fphs");
    
    private static final TypeReference<List<FPHSExternalIdentifier>> EXTERNAL_ID_TYPE_REF = 
            new TypeReference<List<FPHSExternalIdentifier>>() {};

    private FPHSService fphsService;
    
    @Autowired
    final void setFPHSService(FPHSService service) {
        this.fphsService = service; 
    }
    
    @GetMapping("/fphs/externalId")
    public FPHSExternalIdentifier verifyExternalIdentifier(@RequestParam String identifier) throws Exception {
        // public API, no restrictions. externalId can be null so we can create a 400 error in the service.
        ExternalIdentifier externalId = ExternalIdentifier.create(FPHS_ID, identifier);
        fphsService.verifyExternalIdentifier(externalId);
        return FPHSExternalIdentifier.create(externalId.getIdentifier());
    }
    
    @PostMapping("/fphs/externalId")
    public StatusMessage registerExternalIdentifier() throws Exception {
        UserSession session = getAuthenticatedSession();
        
        ExternalIdentifier externalId = parseJson(ExternalIdentifier.class);
        fphsService.registerExternalIdentifier(session.getStudyIdentifier(), session.getHealthCode(), externalId);

        // The service saves the external identifier and saves this as an option. We also need 
        // to update the user's session.
        Set<String> dataGroups = Sets.newHashSet(session.getParticipant().getDataGroups());
        dataGroups.add("football_player");
        
        StudyParticipant updated = new StudyParticipant.Builder().copyOf(session.getParticipant())
                .withExternalId(externalId.getIdentifier()).withDataGroups(dataGroups).build();
        
        updateRequestContext(session);
        sessionUpdateService.updateParticipant(session, updated);
        
        return new StatusMessage("External identifier added to user profile.");
    }
    
    @PostMapping("/fphs/externalIds")
    @ResponseStatus(HttpStatus.CREATED)
    public StatusMessage addExternalIdentifiers() throws Exception {
        getAuthenticatedSession(ADMIN);
        
        List<FPHSExternalIdentifier> externalIds = MAPPER.convertValue(parseJson(JsonNode.class),
                EXTERNAL_ID_TYPE_REF);
        fphsService.addExternalIdentifiers(externalIds);
        
        return new StatusMessage("External identifiers added.");
    }
}
