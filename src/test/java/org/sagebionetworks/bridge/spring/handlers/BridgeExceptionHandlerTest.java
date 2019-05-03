package org.sagebionetworks.bridge.spring.handlers;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import com.amazonaws.AmazonServiceException.ErrorType;
import com.amazonaws.services.dynamodbv2.model.AmazonDynamoDBException;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughputExceededException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

import org.aopalliance.intercept.MethodInvocation;
import org.hibernate.QueryParameterException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.spring.util.HttpUtilTest;
import org.sagebionetworks.bridge.validators.StudyValidator;
import org.sagebionetworks.bridge.validators.Validate;

public class BridgeExceptionHandlerTest extends Mockito {
    
    @Mock
    private HttpServletRequest mockRequest;
    
    @InjectMocks
    private BridgeExceptionHandler handler = new BridgeExceptionHandler();
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
    }
    
    @Test
    public void test() throws Exception {
        when(mockRequest.getHeader(BridgeConstants.X_REQUEST_ID_HEADER)).thenReturn("ABC-DEF");
        
        String message = "dummy error message";
        Exception ex = new IllegalArgumentException(message);
        ResponseEntity<String> response = handler.handleException(mockRequest, ex);
        HttpUtilTest.assertErrorResponse(response, HttpStatus.INTERNAL_SERVER_ERROR,
                "IllegalArgumentException", message);
    }
    
    @Test
    public void consentRequiredSessionSerializedCorrectly() throws Throwable {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withEmail("email@email.com")
                .withFirstName("firstName")
                .withLastName("lastName")
                .withHealthCode("healthCode")
                .withNotifyByEmail(true)
                .withId("userId")
                .withSubstudyIds(ImmutableSet.of("substudyA"))
                .withExternalIds(ImmutableMap.of("substudyA", "externalIdA"))
                .withSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS)
                .withDataGroups(ImmutableSet.of("group1")).build();
        
        UserSession session = new UserSession(participant);
        session.setAuthenticated(true);
        session.setEnvironment(Environment.DEV);
        session.setInternalSessionToken("internalToken");
        session.setSessionToken("sessionToken");
        session.setReauthToken("reauthToken");
        session.setStudyIdentifier(new StudyIdentifierImpl("test"));
        session.setConsentStatuses(Maps.newHashMap());
        
        ConsentRequiredException exception = new ConsentRequiredException(session);
        
        ResponseEntity<String> response = handler.handleException(mockRequest, exception);
        assertEquals(response.getStatusCodeValue(), 412);
        
        JsonNode node = new ObjectMapper().readTree(response.getBody());
        assertTrue(node.get("authenticated").booleanValue());
        assertFalse(node.get("consented").booleanValue());
        assertFalse(node.get("signedMostRecentConsent").booleanValue());
        assertEquals("all_qualified_researchers", node.get("sharingScope").textValue());
        assertEquals("sessionToken", node.get("sessionToken").textValue());
        assertEquals("develop", node.get("environment").textValue());
        assertEquals("email@email.com", node.get("username").textValue());
        assertEquals("email@email.com", node.get("email").textValue());
        assertEquals("userId", node.get("id").textValue());
        assertEquals("reauthToken", node.get("reauthToken").textValue());
        assertTrue(node.get("dataSharing").booleanValue());
        assertTrue(node.get("notifyByEmail").booleanValue());
        assertEquals("substudyA", node.get("substudyIds").get(0).textValue());
        assertEquals("UserSessionInfo", node.get("type").textValue());
        ArrayNode array = (ArrayNode)node.get("roles");
        assertEquals(0, array.size());
        array = (ArrayNode)node.get("dataGroups");
        assertEquals(1, array.size());
        assertEquals("group1", array.get(0).textValue());
        assertEquals(0, node.get("consentStatuses").size());
        assertEquals("externalIdA", node.get("externalIds").get("substudyA").textValue());
        // And no further properties
        assertEquals(22, node.size());
    }
    
    @Test
    public void amazonServiceMessageCorrectlyReported() throws Throwable {
        Map<String,String> map = Maps.newHashMap();
        map.put("A", "B");
        
        // We're verifying that we suppress everything here except fields that are unique and important
        // in the exception.
        AmazonDynamoDBException exc = new AmazonDynamoDBException("This is not the final message?");
        exc.setStatusCode(400);
        exc.setErrorMessage("This is an error message.");
        exc.setErrorType(ErrorType.Client);
        exc.setRawResponseContent("rawResponseContent");
        exc.setRawResponse("rawResponseContent".getBytes());
        exc.setErrorCode("someErrorCode");
        exc.setHttpHeaders(map);
        exc.setRequestId("abd");
        exc.setServiceName("serviceName");
        
        ResponseEntity<String> response = handler.handleException(mockRequest, exc);
        JsonNode node = new ObjectMapper().readTree(response.getBody());
        
        assertEquals(3, node.size()); 
        assertEquals("This is an error message.", node.get("message").textValue());
        assertEquals("BadRequestException", node.get("type").textValue());

        assertEquals(400, response.getStatusCodeValue());
        assertEquals(400, node.get("statusCode").intValue());
    }

    @Test
    public void ddbThrottlingReportedAs500() throws Throwable {
        // ProvisionedThroughputExceededException from Amazon reports itself as a 400, but we want to treat it as a 500
        ProvisionedThroughputExceededException ex = new ProvisionedThroughputExceededException(
                "dummy exception message");
        ex.setStatusCode(400);

        // Execute and validate - Just test the status code and type. Everything else is tested elsewhere.
        ResponseEntity<String> response = handler.handleException(mockRequest, ex);
        JsonNode node = new ObjectMapper().readTree(response.getBody());
        assertEquals("BridgeServiceException", node.get("type").textValue());

        assertEquals(500, response.getStatusCodeValue());
        assertEquals(500, node.get("statusCode").intValue());
    }
    
    // If you do not wrap a RuntimeException in BridgeServiceException, it's still reported as a 500 response, 
    // but the JSON will be based on that object, so e.g. the type will be the type of the exception. That and 
    // usually other details are internal to the system and will not make sense to an API caller.
    @Test
    public void bridgeServiceExceptionCorrectlyReported() throws Throwable {
        BridgeServiceException ex = new BridgeServiceException(new QueryParameterException("external system error"));
        
        ResponseEntity<String> response = handler.handleException(mockRequest, ex);
        JsonNode node = new ObjectMapper().readTree(response.getBody());
        
        assertEquals(500, node.get("statusCode").intValue());
        assertEquals("org.hibernate.QueryParameterException: external system error", node.get("message").textValue());
        assertEquals("BridgeServiceException", node.get("type").textValue());
    }

    @Test
    public void bridgeValidationExceptionCorrectlyReported() throws Throwable {
        Study study = new DynamoStudy();
        try {
            Validate.entityThrowingException(new StudyValidator(), study); 
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            MethodInvocation invocation = mock(MethodInvocation.class);
            when(invocation.proceed()).thenThrow(e);
            
            ResponseEntity<String> response = handler.handleException(mockRequest, e);
            JsonNode node = new ObjectMapper().readTree(response.getBody());

            assertEquals(5, node.size());
            assertEquals("identifier is required", node.get("errors").get("identifier").get(0).textValue());
            assertEquals("InvalidEntityException", node.get("type").textValue());
            assertNotNull(node.get("entity"));
            assertNotNull(node.get("errors"));

            assertEquals(400, response.getStatusCodeValue());
            assertEquals(400, node.get("statusCode").intValue());
        }
    }
}
