package org.sagebionetworks.bridge.spring.controllers;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.SharingOption;
import org.sagebionetworks.bridge.models.itp.IntentToParticipate;
import org.sagebionetworks.bridge.services.IntentService;

@CrossOrigin
@RestController
public class IntentController extends BaseController {

    static final StatusMessage SUBMITTED_MSG = new StatusMessage("Intent to participate accepted.");
    private IntentService intentService;
    
    @Autowired
    final void setIntentService(IntentService intentService) {
        this.intentService = intentService;
    }
    
    @PostMapping("/v3/itp")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public StatusMessage submitIntentToParticipate() throws Exception {
        // An early hack in the system was that sharing scope was added to the consent signature 
        // JSON even though it is not part of the signature. We need to move that value because 
        // the client API continues to treat sharing as part of the consent signature.
        JsonNode requestNode = parseJson(JsonNode.class);
        IntentToParticipate intent = MAPPER.treeToValue(requestNode, IntentToParticipate.class);
        
        if (requestNode != null && requestNode.has("consentSignature")) {
            SharingOption sharing = SharingOption.fromJson(requestNode.get("consentSignature"), 2);
            intent = new IntentToParticipate.Builder().copyOf(intent)
                    .withScope(sharing.getSharingScope()).build();
        }
        intentService.submitIntentToParticipate(intent);
        
        return SUBMITTED_MSG;
    }
}
