package org.sagebionetworks.bridge.spring.controllers;

import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static com.google.common.net.HttpHeaders.USER_AGENT;
import static org.sagebionetworks.bridge.BridgeConstants.TEST_USER_GROUP;
import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.sagebionetworks.bridge.TestConstants.USER_ID;
import static org.sagebionetworks.bridge.TestConstants.USER_SUBSTUDY_IDS;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.APPOINTMENT_REPORT;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.APP_ID;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.FHIR_CONTEXT;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.OBSERVATION_REPORT;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.PROCEDURE_REPORT;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.TIMESTAMP_FIELD;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.CUIMC_USERNAME;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.USER_ID_VALUE_NS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.IOException;
import java.util.Base64;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.hl7.fhir.dstu3.model.Address;
import org.hl7.fhir.dstu3.model.Appointment;
import org.hl7.fhir.dstu3.model.ContactPoint.ContactPointSystem;
import org.hl7.fhir.dstu3.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.dstu3.model.Identifier;
import org.hl7.fhir.dstu3.model.Observation;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.ProcedureRequest;
import org.joda.time.LocalDate;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.http.ResponseEntity;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.models.DateRangeResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.healthdata.HealthDataSubmission;
import org.sagebionetworks.bridge.models.reports.ReportData;
import org.sagebionetworks.bridge.models.substudies.AccountSubstudy;
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.services.AppService;
import org.sagebionetworks.bridge.services.HealthDataService;
import org.sagebionetworks.bridge.services.ParticipantService;
import org.sagebionetworks.bridge.services.ReportService;
import org.sagebionetworks.bridge.services.SessionUpdateService;

public class CRCControllerTest extends Mockito {

    static final LocalDate JAN1 = LocalDate.parse("1970-01-01");
    static final LocalDate JAN2 = LocalDate.parse("1970-01-02");
    static final String HEALTH_CODE = "healthCode";
    String CREDENTIALS = CUIMC_USERNAME + ":dummy-password";
    String AUTHORIZATION_HEADER_VALUE = "Basic "
            + new String(Base64.getEncoder().encode(CREDENTIALS.getBytes()));
    
    static final AccountSubstudy ACCT_SUB1 = AccountSubstudy.create(APP_ID, "substudyA", USER_ID);
    static final AccountSubstudy ACCT_SUB2 = AccountSubstudy.create(APP_ID, "substudyB", USER_ID);
    static final AccountId ACCOUNT_ID = AccountId.forId(APP_ID, USER_ID);
    static final AccountId ACCOUNT_ID_FOR_HC = AccountId.forHealthCode(APP_ID, HEALTH_CODE);
    
    @Mock
    ParticipantService mockParticipantService;
    
    @Mock
    ReportService mockReportService;
    
    @Mock
    AppService mockAppService;
    
    @Mock
    AccountService mockAccountService;
    
    @Mock
    SessionUpdateService mockSessionUpdateService;
    
    @Mock
    App mockApp;
    
    @Mock
    HttpServletRequest mockRequest;
    
    @Mock
    HttpServletResponse mockResponse;
    
    @Mock
    HealthDataService mockHealthDataService;
    
    @Captor
    ArgumentCaptor<SignIn> signInCaptor;
    
    @Captor
    ArgumentCaptor<StudyParticipant> participantCaptor;
    
    @Captor
    ArgumentCaptor<ReportData> reportCaptor;
    
    @Captor
    ArgumentCaptor<Account> accountCaptor;
    
    @Captor
    ArgumentCaptor<String> stringCaptor;
    
    @Captor
    ArgumentCaptor<HealthDataSubmission> dataCaptor;
    
    App app;
    
    Account account;
    
    @InjectMocks
    @Spy
    CRCController controller;

    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);

        account = Account.create();
        account.setHealthCode(HEALTH_CODE);
        account.setAccountSubstudies(ImmutableSet.of(ACCT_SUB1, ACCT_SUB2));
        account.setDataGroups(ImmutableSet.of("group1"));
        
        app = App.create();
        app.setIdentifier(APP_ID);
        when(mockAppService.getApp(APP_ID)).thenReturn(app);
        
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
        doReturn(TestConstants.TIMESTAMP).when(controller).getTimestamp();
    }
    
    @AfterMethod
    public void afterMethod() {
        BridgeUtils.setRequestContext(RequestContext.NULL_INSTANCE);
    }

    @Test
    public void getUserAgent() {
        when(mockRequest.getHeader(USER_AGENT)).thenReturn("Client Agent");
        assertEquals(controller.getUserAgent(), "Client Agent");
    }

    @Test
    public void getUserAgentDefault() {
        assertEquals(controller.getUserAgent(), "<Unknown>");
    }
    
    @Test
    public void updateParticipantCallsExternal() throws Exception {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        
        Account account = Account.create();
        account.setDataGroups(ImmutableSet.of("group1"));
        when(mockAccountService.getAccount(ACCOUNT_ID_FOR_HC)).thenReturn(account);
        
        HttpResponse mockResponse = mock(HttpResponse.class);
        StatusLine mockStatusLine = mock(StatusLine.class);
        when(mockResponse.getStatusLine()).thenReturn(mockStatusLine);
        when(mockStatusLine.getStatusCode()).thenReturn(200);
                
        doReturn(mockResponse).when(controller).post(any());
        
        StatusMessage message = controller.updateParticipant("healthcode:"+HEALTH_CODE);
        assertEquals(message.getMessage(), CRCController.UPDATE_MSG);

        verify(mockAccountService).updateAccount(account, null);
        verify(controller).createLabOrder(account);
        verify(controller).post(stringCaptor.capture());

        assertEquals(account.getDataGroups(), makeSetOf(CRCController.AccountStates.TESTS_REQUESTED, "group1"));
        assertEquals(stringCaptor.getValue(), "{\"resourceType\":\"Patient\","
                +"\"identifier\":[{\"system\":\"https://ws.sagebridge.org/#userId\"}],"
                +"\"active\":true,\"gender\":\"unknown\",\"address\":[{\"state\":\"NY\"}]}");
        
        assertFalse(BridgeUtils.getRequestContext().getCallerSubstudies().isEmpty());
    }
    
    @Test
    public void updateParticipantDoesNotCallExternalForTestAccount() throws Exception {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        
        account.setDataGroups(ImmutableSet.of(TEST_USER_GROUP));
        when(mockAccountService.getAccount(ACCOUNT_ID_FOR_HC)).thenReturn(account);
        
        HttpResponse mockResponse = mock(HttpResponse.class);
        StatusLine mockStatusLine = mock(StatusLine.class);
        when(mockResponse.getStatusLine()).thenReturn(mockStatusLine);
        when(mockStatusLine.getStatusCode()).thenReturn(200);
                
        doReturn(mockResponse).when(controller).post(any());
        
        StatusMessage message = controller.updateParticipant("healthcode:"+HEALTH_CODE);
        assertEquals(message.getMessage(), CRCController.UPDATE_MSG);

        verify(mockAccountService).updateAccount(account, null);
        verify(controller, never()).createLabOrder(any());
        
        String substudy = Iterables.getFirst(BridgeUtils.getRequestContext().getCallerSubstudies(), null);
        assertEquals(substudy, "substudyA");
    }
    
    @Test
    public void updateParticipantWithSageAccountHasNoSubstudies() throws Exception {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        
        AccountSubstudy acctSubstudy = AccountSubstudy.create(APP_ID, "sage", USER_ID);
        
        account.setDataGroups(ImmutableSet.of(TEST_USER_GROUP));
        account.setAccountSubstudies(ImmutableSet.of(acctSubstudy));
        when(mockAccountService.getAccount(ACCOUNT_ID_FOR_HC)).thenReturn(account);
        
        HttpResponse mockResponse = mock(HttpResponse.class);
        StatusLine mockStatusLine = mock(StatusLine.class);
        when(mockResponse.getStatusLine()).thenReturn(mockStatusLine);
        when(mockStatusLine.getStatusCode()).thenReturn(200);
                
        doReturn(mockResponse).when(controller).post(any());
        
        StatusMessage message = controller.updateParticipant("healthcode:"+HEALTH_CODE);
        assertEquals(message.getMessage(), CRCController.UPDATE_MSG);

        verify(mockAccountService).updateAccount(account, null);
        verify(controller, never()).createLabOrder(any());
        
        assertTrue(BridgeUtils.getRequestContext().getCallerSubstudies().isEmpty());
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp = "Account not found.")
    public void updateParticipantAccountNotFound() {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
                
        controller.updateParticipant("healthcode:"+HEALTH_CODE);
    }
    
    @Test
    public void createLabOrderOK() throws Exception { 
        mockExternalService(200, "OK");
        // no errors
        controller.createLabOrder(Account.create());
    }
    
    @Test
    public void createLabOrderCreated() throws Exception { 
        mockExternalService(201, "Created");
        // no errors
        controller.createLabOrder(Account.create());
    }
    
    @Test(expectedExceptions = BridgeServiceException.class, 
            expectedExceptionsMessageRegExp = "Internal Service Error")
    public void createLabOrderBadRequest() throws Exception { 
        mockExternalService(400, "Bad Request");
        controller.createLabOrder(Account.create());
    }
    
    @Test(expectedExceptions = BridgeServiceException.class)
    public void createLabOrderInternalServerError() throws Exception { 
        mockExternalService(500, "Internal Server Error");
        controller.createLabOrder(Account.create());
    }
    
    @Test(expectedExceptions = BridgeServiceException.class)
    public void createLabOrderServiceUnavailable() throws Exception { 
        mockExternalService(503, "Service Unavailable");
        controller.createLabOrder(Account.create());
    }
    
    @Test(expectedExceptions = BridgeServiceException.class)
    public void createLabOrderIOException() throws Exception {
        doThrow(new IOException()).when(controller).post(any());
        controller.createLabOrder(Account.create());
    }
    
    private void mockExternalService(int statusCode, String statusReason) throws Exception {
        StatusLine statusLine = new BasicStatusLine(new ProtocolVersion("HTTP", 1, 2), statusCode, statusReason);
        HttpResponse response = new BasicHttpResponse(statusLine);
        doReturn(response).when(controller).post(any());
    }
    
    @Test
    public void postAppointmentCreated() throws Exception {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        DateRangeResourceList<? extends ReportData> results = new  DateRangeResourceList<>(ImmutableList.of());
        doReturn(results).when(mockReportService).getParticipantReport(
                APP_ID, APPOINTMENT_REPORT, HEALTH_CODE, JAN1, JAN2);
        
        // add a wrong ID to verify we go through them all and look for ours
        Identifier wrongId = new Identifier();
        wrongId.setSystem("some-other-namespace");
        wrongId.setValue(USER_ID);
        
        Appointment appointment = new Appointment();
        appointment.addIdentifier(wrongId);
        appointment.addIdentifier(makeIdentifier(USER_ID_VALUE_NS, USER_ID));
        String json = FHIR_CONTEXT.newJsonParser().encodeResourceToString(appointment);
        mockRequestBody(mockRequest, json);
        
        ResponseEntity<StatusMessage> retValue = controller.postAppointment();
        assertEquals(retValue.getBody().getMessage(), "Appointment created.");
        assertEquals(retValue.getStatusCodeValue(), 201);
        
        verify(mockAccountService).authenticate(eq(app), signInCaptor.capture());
        SignIn capturedSignIn = signInCaptor.getValue();
        assertEquals(capturedSignIn.getAppId(), APP_ID);
        assertEquals(capturedSignIn.getExternalId(), CUIMC_USERNAME);
        assertEquals(capturedSignIn.getPassword(), "dummy-password");
        
        verify(mockReportService).saveParticipantReport(eq(APP_ID), eq(APPOINTMENT_REPORT), eq(HEALTH_CODE),
                reportCaptor.capture());
        ReportData capturedReport = reportCaptor.getValue();
        assertEquals(capturedReport.getDate(), "1970-01-01");
        verifyIdentifier(capturedReport.getData());
        assertEquals(capturedReport.getSubstudyIds(), USER_SUBSTUDY_IDS);
        
        verify(mockAccountService).updateAccount(accountCaptor.capture(), isNull());
        Account capturedAcct = accountCaptor.getValue();
        assertEquals(capturedAcct.getDataGroups(), makeSetOf(CRCController.AccountStates.TESTS_SCHEDULED, "group1"));
        assertEquals(capturedAcct.getAttributes().get(TIMESTAMP_FIELD), TIMESTAMP.toString());
        
        verify(mockHealthDataService).submitHealthData(eq(APP_ID), participantCaptor.capture(), dataCaptor.capture());
        HealthDataSubmission healthData = dataCaptor.getValue();
        assertEquals(healthData.getAppVersion(), "v1");
        assertEquals(healthData.getCreatedOn(), TIMESTAMP);
        assertEquals(healthData.getMetadata().toString(), "{\"fhir-type\":\""+APPOINTMENT_REPORT+"\"}");
        assertEquals(healthData.getData().toString(), json);
    }
    
    @Test
    public void postAppointmentUpdated() throws Exception {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        DateRangeResourceList<? extends ReportData> results = new DateRangeResourceList<>(ImmutableList.of(ReportData.create()));
        doReturn(results).when(mockReportService).getParticipantReport(
                APP_ID, APPOINTMENT_REPORT, HEALTH_CODE, JAN1, JAN2);
        
        // add a wrong ID to verify we go through them all and look for ours
        Identifier wrongId = new Identifier();
        wrongId.setSystem("some-other-namespace");
        wrongId.setValue(USER_ID);
        
        Appointment appointment = new Appointment();
        appointment.addIdentifier(wrongId);
        appointment.addIdentifier(makeIdentifier(USER_ID_VALUE_NS, USER_ID));
        String json = FHIR_CONTEXT.newJsonParser().encodeResourceToString(appointment);
        mockRequestBody(mockRequest, json);
        
        ResponseEntity<StatusMessage> retValue = controller.postAppointment();
        assertEquals(retValue.getBody().getMessage(), "Appointment updated.");
        assertEquals(retValue.getStatusCodeValue(), 200);
        
        verify(mockHealthDataService).submitHealthData(eq(APP_ID), participantCaptor.capture(), dataCaptor.capture());
        HealthDataSubmission healthData = dataCaptor.getValue();
        assertEquals(healthData.getAppVersion(), "v1");
        assertEquals(healthData.getCreatedOn(), TIMESTAMP);
        assertEquals(healthData.getMetadata().toString(), "{\"fhir-type\":\""+APPOINTMENT_REPORT+"\"}");
        assertEquals(healthData.getData().toString(), json);
    }
    
    @Test
    public void postProcedureCreated() throws Exception {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        String json = makeProcedureRequest();
        mockRequestBody(mockRequest, json);
        
        DateRangeResourceList<? extends ReportData> results = new  DateRangeResourceList<>(ImmutableList.of());
        doReturn(results).when(mockReportService).getParticipantReport(
                APP_ID, PROCEDURE_REPORT, HEALTH_CODE, JAN1, JAN2);
        
        ResponseEntity<StatusMessage> retValue = controller.postProcedureRequest();
        assertEquals(retValue.getBody().getMessage(), "ProcedureRequest created.");
        assertEquals(retValue.getStatusCodeValue(), 201);
        
        verify(mockAccountService).authenticate(eq(app), signInCaptor.capture());
        SignIn capturedSignIn = signInCaptor.getValue();
        assertEquals(capturedSignIn.getAppId(), APP_ID);
        assertEquals(capturedSignIn.getExternalId(), CUIMC_USERNAME);
        assertEquals(capturedSignIn.getPassword(), "dummy-password");
        
        verify(mockReportService).saveParticipantReport(eq(APP_ID), eq(PROCEDURE_REPORT), eq(HEALTH_CODE),
                reportCaptor.capture());
        ReportData capturedReport = reportCaptor.getValue();
        assertEquals(capturedReport.getDate(), "1970-01-01");
        verifyIdentifier(capturedReport.getData());
        assertEquals(capturedReport.getSubstudyIds(), USER_SUBSTUDY_IDS);
        
        verify(mockAccountService).updateAccount(accountCaptor.capture(), isNull());
        Account capturedAcct = accountCaptor.getValue();
        assertEquals(capturedAcct.getDataGroups(), makeSetOf(CRCController.AccountStates.TESTS_COLLECTED, "group1"));
        assertEquals(capturedAcct.getAttributes().get(TIMESTAMP_FIELD), TIMESTAMP.toString());
        
        verify(mockHealthDataService).submitHealthData(eq(APP_ID), participantCaptor.capture(), dataCaptor.capture());
        HealthDataSubmission healthData = dataCaptor.getValue();
        assertEquals(healthData.getAppVersion(), "v1");
        assertEquals(healthData.getCreatedOn(), TIMESTAMP);
        assertEquals(healthData.getMetadata().toString(), "{\"fhir-type\":\""+PROCEDURE_REPORT+"\"}");
        assertEquals(healthData.getData().toString(), json);
    }
    
    @Test
    public void postProcedureUpdated() throws Exception {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        String json = makeProcedureRequest();
        mockRequestBody(mockRequest, json);
        
        DateRangeResourceList<? extends ReportData> results = new DateRangeResourceList<>(ImmutableList.of(ReportData.create()));
        doReturn(results).when(mockReportService).getParticipantReport(
                APP_ID, PROCEDURE_REPORT, HEALTH_CODE, JAN1, JAN2);
        
        ResponseEntity<StatusMessage> retValue = controller.postProcedureRequest();
        assertEquals(retValue.getBody().getMessage(), "ProcedureRequest updated.");
        assertEquals(retValue.getStatusCodeValue(), 200);
        
        verify(mockHealthDataService).submitHealthData(eq(APP_ID), participantCaptor.capture(), dataCaptor.capture());
        HealthDataSubmission healthData = dataCaptor.getValue();
        assertEquals(healthData.getAppVersion(), "v1");
        assertEquals(healthData.getCreatedOn(), TIMESTAMP);
        assertEquals(healthData.getMetadata().toString(), "{\"fhir-type\":\""+PROCEDURE_REPORT+"\"}");
        assertEquals(healthData.getData().toString(), json);
    }

    @Test
    public void postObservationCreated() throws Exception {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        String json = makeObservation();
        mockRequestBody(mockRequest, json);
        
        DateRangeResourceList<? extends ReportData> results = new DateRangeResourceList<>(ImmutableList.of());
        doReturn(results).when(mockReportService).getParticipantReport(
                APP_ID, OBSERVATION_REPORT, HEALTH_CODE, JAN1, JAN2);
        
        ResponseEntity<StatusMessage> retValue = controller.postObservation();
        assertEquals(retValue.getBody().getMessage(), "Observation created.");
        assertEquals(retValue.getStatusCodeValue(), 201);
        
        verify(mockAccountService).authenticate(eq(app), signInCaptor.capture());
        SignIn capturedSignIn = signInCaptor.getValue();
        assertEquals(capturedSignIn.getAppId(), APP_ID);
        assertEquals(capturedSignIn.getExternalId(), CUIMC_USERNAME);
        assertEquals(capturedSignIn.getPassword(), "dummy-password");
        
        verify(mockReportService).saveParticipantReport(eq(APP_ID), eq(OBSERVATION_REPORT), eq(HEALTH_CODE),
                reportCaptor.capture());
        ReportData capturedReport = reportCaptor.getValue();
        assertEquals(capturedReport.getDate(), "1970-01-01");
        verifyIdentifier(capturedReport.getData());
        assertEquals(capturedReport.getSubstudyIds(), USER_SUBSTUDY_IDS);
        
        verify(mockAccountService).updateAccount(accountCaptor.capture(), isNull());
        Account capturedAcct = accountCaptor.getValue();
        assertEquals(capturedAcct.getDataGroups(), makeSetOf(CRCController.AccountStates.TESTS_AVAILABLE, "group1"));
        assertEquals(capturedAcct.getAttributes().get(TIMESTAMP_FIELD), TIMESTAMP.toString());
        
        verify(mockHealthDataService).submitHealthData(eq(APP_ID), participantCaptor.capture(), dataCaptor.capture());
        HealthDataSubmission healthData = dataCaptor.getValue();
        assertEquals(healthData.getAppVersion(), "v1");
        assertEquals(healthData.getCreatedOn(), TIMESTAMP);
        assertEquals(healthData.getMetadata().toString(), "{\"fhir-type\":\""+OBSERVATION_REPORT+"\"}");
        assertEquals(healthData.getData().toString(), json);
    }
    
    @Test
    public void postObservationUpdated() throws Exception {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        String json = makeObservation();
        mockRequestBody(mockRequest, json);
        
        DateRangeResourceList<? extends ReportData> results = new DateRangeResourceList<>(ImmutableList.of(ReportData.create()));
        doReturn(results).when(mockReportService).getParticipantReport(
                APP_ID, OBSERVATION_REPORT, HEALTH_CODE, JAN1, JAN2);
        
        ResponseEntity<StatusMessage> retValue = controller.postObservation();
        assertEquals(retValue.getBody().getMessage(), "Observation updated.");
        assertEquals(retValue.getStatusCodeValue(), 200);
        
        verify(mockHealthDataService).submitHealthData(eq(APP_ID), participantCaptor.capture(), dataCaptor.capture());
        HealthDataSubmission healthData = dataCaptor.getValue();
        assertEquals(healthData.getAppVersion(), "v1");
        assertEquals(healthData.getCreatedOn(), TIMESTAMP);
        assertEquals(healthData.getMetadata().toString(), "{\"fhir-type\":\""+OBSERVATION_REPORT+"\"}");
        assertEquals(healthData.getData().toString(), json);
    }

    @Test
    public void authenticationWorks() {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAppService.getApp(CRCController.APP_ID)).thenReturn(mockApp);
        when(mockAccountService.authenticate(eq(mockApp), signInCaptor.capture())).thenReturn(account);
        
        controller.httpBasicAuthentication();
        
        SignIn captured = signInCaptor.getValue();
        assertEquals(captured.getAppId(), CRCController.APP_ID);
        assertEquals(captured.getExternalId(), CRCController.CUIMC_USERNAME);
        assertEquals(captured.getPassword(), "dummy-password");
    }
    
    @Test(expectedExceptions = NotAuthenticatedException.class)
    public void authenticationMissingHeader() {
        controller.httpBasicAuthentication();
    }
    
    @Test(expectedExceptions = NotAuthenticatedException.class)
    public void authenticationHeaderTooShort() {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn("Foo");

        controller.httpBasicAuthentication();
    }
    
    @Test(expectedExceptions = NotAuthenticatedException.class)
    public void authenticationSplitsWrong() {
        String credentials = CRCController.CUIMC_USERNAME + ":dummy-password:some-nonsense";
        String authValue = "Digest " + new String(Base64.getEncoder().encode(credentials.getBytes()));        
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(authValue);

        controller.httpBasicAuthentication();
    }
    
    @Test(expectedExceptions = NotAuthenticatedException.class)
    public void authenticationInvalidUsername() {
        String credentials = "not-the-right-person:dummy-password";
        String authValue = "Digest " + new String(Base64.getEncoder().encode(credentials.getBytes()));        
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(authValue);

        controller.httpBasicAuthentication();
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void authenticationInvalidCredentials() {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAppService.getApp(CRCController.APP_ID)).thenReturn(mockApp);
        when(mockAccountService.authenticate(eq(mockApp), any()))
            .thenThrow(new EntityNotFoundException(Account.class));
        
        controller.httpBasicAuthentication();
    }
    
    @Test
    public void createPatient() {
        Account account = Account.create();
        account.setId("userId");
        account.setFirstName("Test");
        account.setLastName("User");
        account.setEmail(EMAIL);
        account.setEmailVerified(true);
        account.setPhone(PHONE);
        account.setPhoneVerified(true);
        account.setAttributes(new ImmutableMap.Builder<String, String>()
            .put("address1", "123 Sesame Street")
            .put("address2", "Apt. 6")
            .put("city", "Seattle")
            .put("dob", "1980-08-10")
            .put("gender", "female")
            .put("state", "WA")
            .put("zip_code", "10001").build());
        Patient patient = controller.createPatient(account);
        
        assertTrue(patient.getActive());
        assertEquals(patient.getIdentifier().get(0).getValue(), USER_ID);
        assertEquals(patient.getIdentifier().get(0).getSystem(), USER_ID_VALUE_NS);
        assertEquals(patient.getName().get(0).getGivenAsSingleString(), "Test");
        assertEquals(patient.getName().get(0).getFamily(), "User");
        assertEquals(patient.getGender().name(), "FEMALE");
        assertEquals(LocalDate.fromDateFields(patient.getBirthDate()).toString(), "1980-08-10");
        assertEquals(patient.getTelecom().get(0).getValue(), PHONE.getNumber());
        assertEquals(patient.getTelecom().get(0).getSystem().name(), "SMS");
        assertEquals(patient.getTelecom().get(1).getValue(), EMAIL);
        assertEquals(patient.getTelecom().get(1).getSystem().name(), "EMAIL");
        Address address = patient.getAddress().get(0);
        assertEquals(address.getLine().get(0).getValue(), "123 Sesame Street");
        assertEquals(address.getLine().get(1).getValue(), "Apt. 6");
        assertEquals(address.getCity(), "Seattle");
        assertEquals(address.getState(), "WA");
        assertEquals(address.getPostalCode(), "10001");
    }
    
    @Test
    public void createEmptyPatient() {
        Patient patient = controller.createPatient(Account.create());
        assertEquals(patient.getGender().name(), "UNKNOWN");
        // I'm defaulting this because I don't see the client submitting it in the UI, so
        // I'm anticipating it won't be there, but eventually we'll have to collect state.
        assertEquals(patient.getAddress().get(0).getState(), "NY");
        assertTrue(patient.getActive());
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = "Could not find Bridge user ID in identifiers.")
    public void appointmentMissingIdentifiers() throws Exception {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        mockRequestBody(mockRequest, makeAppointment(null, null));
        
        controller.postAppointment();
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = "Could not find Bridge user ID in identifiers.")
    public void appointmentWrongIdentifier() throws Exception {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        mockRequestBody(mockRequest, makeAppointment("wrong-ns", "wrong-identifier"));
        
        controller.postAppointment();
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = "Could not find Bridge user ID in identifiers.")
    public void procedureMissingIdentifiers() throws Exception {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        ProcedureRequest procedure = new ProcedureRequest();
        String json = FHIR_CONTEXT.newJsonParser().encodeResourceToString(procedure);
        mockRequestBody(mockRequest, json);
        
        controller.postProcedureRequest();
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = "Could not find Bridge user ID in identifiers.")
    public void procedureWrongIdentifier() throws Exception {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        Identifier identifier = new Identifier();
        identifier.setSystem("wrong-system");
        identifier.setValue(USER_ID);
        ProcedureRequest procedure = new ProcedureRequest();
        procedure.addIdentifier(identifier);
        String json = FHIR_CONTEXT.newJsonParser().encodeResourceToString(procedure);
        mockRequestBody(mockRequest, json);
        
        controller.postProcedureRequest();        
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = "Could not find Bridge user ID in identifiers.")
    public void operationMissingIdentifiers() throws Exception {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        Observation observation = new Observation();
        String json = FHIR_CONTEXT.newJsonParser().encodeResourceToString(observation);
        mockRequestBody(mockRequest, json);
        
        controller.postObservation();
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = "Could not find Bridge user ID in identifiers.")
    public void operationWrongIdentifier() throws Exception {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        Identifier identifier = new Identifier();
        identifier.setSystem("wrong-system");
        identifier.setValue(USER_ID);
        Observation observation = new Observation();
        observation.addIdentifier(identifier);
        String json = FHIR_CONTEXT.newJsonParser().encodeResourceToString(observation);
        mockRequestBody(mockRequest, json);
        
        controller.postObservation();
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp = "Account not found.")
    public void targetAccountNotFound() throws Exception {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        when(mockAccountService.getAccount(any())).thenReturn(null);
        mockRequestBody(mockRequest, makeAppointment(USER_ID_VALUE_NS, USER_ID));

        controller.postAppointment();
    }

    @Test(expectedExceptions = NotAuthenticatedException.class)
    public void callerUserNameIncorrect() throws Exception {
        String auth = new String(Base64.getEncoder().encode(("foo:dummy-password").getBytes()));
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn("Basic " + auth);
        mockRequestBody(mockRequest, makeAppointment(USER_ID_VALUE_NS, USER_ID));
        
        controller.postProcedureRequest();
    }

    @Test
    public void createPatientWithMaleGender() {
        Account account = Account.create();
        account.setAttributes(ImmutableMap.of("gender", "MALE"));
        
        Patient patient = controller.createPatient(account);
        assertEquals(patient.getGender(), AdministrativeGender.MALE);
    }

    @Test
    public void createPatientWithOtherGender() {
        Account account = Account.create();
        account.setAttributes(ImmutableMap.of("gender", "Other"));
        
        Patient patient = controller.createPatient(account);
        assertEquals(patient.getGender(), AdministrativeGender.OTHER);
    }

    @Test
    public void createPatientWithPhoneUnverified() {
        Account account = Account.create();
        account.setEmail(EMAIL);
        account.setEmailVerified(true);
        account.setPhone(PHONE);
        account.setPhoneVerified(false);

        Patient patient = controller.createPatient(account);
        assertEquals(patient.getTelecom().size(), 1);
        assertEquals(patient.getTelecom().get(0).getSystem(), ContactPointSystem.EMAIL);
    }

    @Test
    public void createPatientWithEmailUnverified() {
        Account account = Account.create();
        account.setEmail(EMAIL);
        account.setEmailVerified(false);
        account.setPhone(PHONE);
        account.setPhoneVerified(true);

        Patient patient = controller.createPatient(account);
        assertEquals(patient.getTelecom().size(), 1);
        assertEquals(patient.getTelecom().get(0).getSystem(), ContactPointSystem.SMS);
    }
    
    private void verifyIdentifier(JsonNode payload) {
        ArrayNode identifiers = (ArrayNode)payload.get("identifier");
        for (int i=0; i < identifiers.size(); i++) {
            JsonNode node = identifiers.get(i);
            if (node.get("system").textValue().equals(USER_ID_VALUE_NS) &&
                node.get("value").textValue().equals(USER_ID)) {
                return;
            }
        }
        fail("Should have thrown exception");
    }
    
    private Set<String> makeSetOf(CRCController.AccountStates state, String unaffectedGroup) {
        return ImmutableSet.of(state.name().toLowerCase(), unaffectedGroup);
    }
    
    private String makeAppointment(String ns, String identifier) {
        Appointment appt = new Appointment();
        if (identifier != null) {
            appt.addIdentifier(makeIdentifier(ns, identifier));    
        }
        return FHIR_CONTEXT.newJsonParser().encodeResourceToString(appt);
    }
    
    private String makeProcedureRequest() { 
        ProcedureRequest procedure = new ProcedureRequest();
        procedure.addIdentifier(makeIdentifier(USER_ID_VALUE_NS, USER_ID));
        return FHIR_CONTEXT.newJsonParser().encodeResourceToString(procedure);
    }
    
    private String makeObservation() {
        Observation obs = new Observation();
        obs.addIdentifier(makeIdentifier(USER_ID_VALUE_NS, USER_ID));
        return FHIR_CONTEXT.newJsonParser().encodeResourceToString(obs);
    }
    
    private Identifier makeIdentifier(String ns, String identifier) {
        Identifier id = new Identifier();
        id.setSystem(ns);
        id.setValue(identifier);
        return id;
    }
}
