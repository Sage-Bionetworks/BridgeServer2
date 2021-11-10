package org.sagebionetworks.bridge.spring.handlers;

import static org.sagebionetworks.bridge.TestConstants.ENROLLMENT;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import com.amazonaws.AmazonServiceException;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.dynamodb.DynamoApp;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.studies.EnrollmentInfo;
import org.sagebionetworks.bridge.spring.util.HttpUtilTest;
import org.sagebionetworks.bridge.validators.AppValidator;
import org.sagebionetworks.bridge.validators.Validate;

public class BridgeExceptionHandlerTest extends Mockito {
    private static final String REQUEST_ID = "ABC-DEF";
    
    @Mock
    private HttpServletRequest mockRequest;

    @Spy
    private Logger logger;

    @InjectMocks
    private BridgeExceptionHandler handler = new BridgeExceptionHandler();
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        handler.setLogger(logger);
    }
    
    @Test
    public void test() throws Exception {
        when(mockRequest.getHeader(BridgeConstants.X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);
        
        String message = "dummy error message";
        Exception ex = new IllegalArgumentException(message);
        ResponseEntity<String> response = handler.handleException(mockRequest, ex);
        HttpUtilTest.assertErrorResponse(response, HttpStatus.INTERNAL_SERVER_ERROR,
                "IllegalArgumentException", message);

        // Verify log.
        ArgumentCaptor<String> errorMessageCaptor = ArgumentCaptor.forClass(String.class);
        verify(logger).error(errorMessageCaptor.capture(), same(ex));
        String errorMessage = errorMessageCaptor.getValue();
        assertTrue(errorMessage.contains(REQUEST_ID));
        assertTrue(errorMessage.contains(message));
    }
    
    @Test
    public void consentRequiredSessionSerializedCorrectly() throws Throwable {
        EnrollmentInfo info = new EnrollmentInfo("externalIdA", ENROLLMENT, null, null,
                null, false);
        Map<String, EnrollmentInfo> enrollments = ImmutableMap.of("studyA", info);
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withEmail("email@email.com")
                .withFirstName("firstName")
                .withLastName("lastName")
                .withHealthCode("healthCode")
                .withNotifyByEmail(true)
                .withId("userId")
                .withStudyIds(ImmutableSet.of("studyA"))
                .withExternalIds(ImmutableMap.of("studyA", "externalIdA"))
                .withEnrollments(enrollments)
                .withSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS)
                .withDataGroups(ImmutableSet.of("group1")).build();
        
        UserSession session = new UserSession(participant);
        session.setAuthenticated(true);
        session.setEnvironment(Environment.DEV);
        session.setInternalSessionToken("internalToken");
        session.setSessionToken("sessionToken");
        session.setReauthToken("reauthToken");
        session.setAppId("test");
        session.setConsentStatuses(Maps.newHashMap());
        
        ConsentRequiredException exception = new ConsentRequiredException(session);
        
        ResponseEntity<String> response = handler.handleException(mockRequest, exception);
        assertEquals(response.getStatusCodeValue(), 412);
        
        JsonNode node = new ObjectMapper().readTree(response.getBody());
        assertTrue(node.get("authenticated").booleanValue());
        assertFalse(node.get("consented").booleanValue());
        assertFalse(node.get("signedMostRecentConsent").booleanValue());
        assertEquals(node.get("sharingScope").textValue(), "all_qualified_researchers");
        assertEquals(node.get("sessionToken").textValue(), "sessionToken");
        assertEquals(node.get("environment").textValue(), "develop");
        assertEquals(node.get("username").textValue(), "email@email.com");
        assertEquals(node.get("email").textValue(), "email@email.com");
        assertEquals(node.get("id").textValue(), "userId");
        assertEquals(node.get("reauthToken").textValue(), "reauthToken");
        assertTrue(node.get("dataSharing").booleanValue());
        assertTrue(node.get("notifyByEmail").booleanValue());
        assertEquals(node.get("studyIds").get(0).textValue(), "studyA");
        assertEquals(node.get("enrollments").get("studyA").get("externalId").textValue(), "externalIdA");
        
        assertEquals(node.get("type").textValue(), "UserSessionInfo");
        ArrayNode array = (ArrayNode)node.get("roles");
        assertEquals(array.size(), 0);
        array = (ArrayNode)node.get("dataGroups");
        assertEquals(array.size(), 1);
        assertEquals(array.get(0).textValue(), "group1");
        assertEquals(node.get("consentStatuses").size(), 0);
        assertEquals(node.get("externalIds").get("studyA").textValue(), "externalIdA");
        assertEquals(node.get("externalId").textValue(), "externalIdA");
        // And no further properties
        assertEquals(node.size(), 24);
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
        assertEquals(node.get("message").textValue(), "This is an error message.");
        assertEquals(node.get("type").textValue(), "BadRequestException");

        assertEquals(response.getStatusCodeValue(), 400);
        assertEquals(node.get("statusCode").intValue(), 400);

        // Verify log.
        verify(logger).warn(contains(exc.getMessage()), same(exc));
    }

    @Test
    public void amazon500CorrectlyReported() throws Exception {
        AmazonServiceException ex = new AmazonServiceException("test message");
        ex.setStatusCode(500);

        handler.handleException(mockRequest, ex);
        verify(logger).error(contains(ex.getMessage()), same(ex));
    }

    // This shouldn't be possible, but test it for branch coverage.
    @Test
    public void amazon300CorrectlyReported() throws Exception {
        AmazonServiceException ex = new AmazonServiceException("test message");
        ex.setStatusCode(300);

        handler.handleException(mockRequest, ex);
        verify(logger).error(contains(ex.getMessage()), same(ex));
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
        assertEquals(node.get("type").textValue(), "BridgeServiceException");

        assertEquals(response.getStatusCodeValue(), 500);
        assertEquals(node.get("statusCode").intValue(), 500);

        // Verify log.
        verify(logger).error(contains(ex.getMessage()), same(ex));
    }
    
    // If you do not wrap a RuntimeException in BridgeServiceException, it's still reported as a 500 response, 
    // but the JSON will be based on that object, so e.g. the type will be the type of the exception. That and 
    // usually other details are internal to the system and will not make sense to an API caller.
    @Test
    public void bridgeServiceExceptionCorrectlyReported() throws Throwable {
        BridgeServiceException ex = new BridgeServiceException(new QueryParameterException("external system error"));
        
        ResponseEntity<String> response = handler.handleException(mockRequest, ex);
        JsonNode node = new ObjectMapper().readTree(response.getBody());
        
        assertEquals(node.get("statusCode").intValue(), 500);
        assertEquals(node.get("message").textValue(), "org.hibernate.QueryParameterException: external system error");
        assertEquals(node.get("type").textValue(), "BridgeServiceException");
    }

    @Test
    public void bridgeValidationExceptionCorrectlyReported() throws Throwable {
        App app = new DynamoApp();
        try {
            Validate.entityThrowingException(new AppValidator(), app); 
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            MethodInvocation invocation = mock(MethodInvocation.class);
            when(invocation.proceed()).thenThrow(e);
            
            ResponseEntity<String> response = handler.handleException(mockRequest, e);
            JsonNode node = new ObjectMapper().readTree(response.getBody());

            assertEquals(node.size(), 5);
            assertEquals(node.get("errors").get("identifier").get(0).textValue(), "identifier is required");
            assertEquals(node.get("type").textValue(), "InvalidEntityException");
            assertNotNull(node.get("entity"));
            assertNotNull(node.get("errors"));

            assertEquals(response.getStatusCodeValue(), 400);
            assertEquals(node.get("statusCode").intValue(), 400);

            // Verify log.
            verify(logger).info(contains(e.getMessage()));
        }
    }
    
    @Test
    public void convertsMissingServletRequestParameterException() throws Throwable {
        MissingServletRequestParameterException ex = new MissingServletRequestParameterException("myParam", "String");
        
        ResponseEntity<String> response = handler.handleException(mockRequest, ex);
        JsonNode node = new ObjectMapper().readTree(response.getBody());
        
        assertEquals(node.get("statusCode").intValue(), 400);
        assertEquals(node.get("message").textValue(), "Required request parameter 'myParam' is missing");
        assertEquals(node.get("type").textValue(), "BadRequestException");
    }
}
