package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.sagebionetworks.bridge.TestUtils.assertAccept;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.sagebionetworks.bridge.models.accounts.SharingScope.ALL_QUALIFIED_RESEARCHERS;
import static org.sagebionetworks.bridge.models.accounts.SharingScope.SPONSORS_AND_PARTNERS;
import static org.testng.Assert.assertEquals;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.itp.IntentToParticipate;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.services.IntentService;

public class IntentControllerTest extends Mockito {

    @Mock
    IntentService mockIntentService;
    
    @Mock
    HttpServletRequest mockRequest;
    
    @Mock
    HttpServletResponse mockResponse;
    
    @Captor
    ArgumentCaptor<IntentToParticipate> intentCaptor;

    @InjectMocks
    @Spy
    IntentController controller;

    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
    }
    
    @Test
    public void verifyAnnotations() throws Exception { 
        assertCrossOrigin(IntentController.class);
        assertAccept(IntentController.class, "submitIntentToParticipate");
    }
    
    @Test
    public void canSubmitAnIntent() throws Exception {
        // See comment in controller. Client APIs send scope as part of signature for legacy
        // reasons, but it is not part of the consent signature. Controller transfers it to the ITP.
        IntentToParticipate intent = createIntentToParticipate();
        JsonNode node = BridgeObjectMapper.get().valueToTree(intent);
        ((ObjectNode)node).remove("scope");
        ((ObjectNode)node.get("consentSignature")).put("scope", "all_qualified_researchers");

        mockRequestBody(mockRequest, node.toString());
        
        StatusMessage result = controller.submitIntentToParticipate();
        assertEquals(result, IntentController.SUBMITTED_MSG);
        
        verify(mockIntentService).submitIntentToParticipate(intentCaptor.capture());
        
        IntentToParticipate captured = intentCaptor.getValue();
        // It's pretty simple, we just want to make sure we got it, check a couple of fields
        assertEquals(captured.getPhone().getNumber(), PHONE.getNumber());
        assertEquals(captured.getConsentSignature().getName(), "Gladlight Stonewell");
        assertEquals(captured.getScope(), ALL_QUALIFIED_RESEARCHERS);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class, 
            expectedExceptionsMessageRegExp = ".*no String-argument constructor/factory method.*")
    public void intentToParticipanteBadJson() throws Exception {
        mockRequestBody(mockRequest, "{\"phone\": \"+1234567890\"}");
        
        controller.submitIntentToParticipate();
    }
    
    private IntentToParticipate createIntentToParticipate() {
        ConsentSignature consentSignature = new ConsentSignature.Builder()
                .withName("Gladlight Stonewell")
                .withBirthdate("1980-10-10")
                .withConsentCreatedOn(TIMESTAMP.getMillis())
                .withImageData("image-data")
                .withImageMimeType("image/png").build();
        return new IntentToParticipate.Builder()
                .withStudyId(TEST_STUDY_IDENTIFIER)
                .withScope(SPONSORS_AND_PARTNERS)
                .withPhone(PHONE)
                .withSubpopGuid("subpopGuid")
                .withConsentSignature(consentSignature).build();
    }
}
