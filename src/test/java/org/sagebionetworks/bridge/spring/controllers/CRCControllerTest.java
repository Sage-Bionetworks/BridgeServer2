package org.sagebionetworks.bridge.spring.controllers;

import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static com.google.common.net.HttpHeaders.USER_AGENT;
import static org.sagebionetworks.bridge.BridgeConstants.TEST_USER_GROUP;
import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.sagebionetworks.bridge.TestConstants.USER_ID;
import static org.sagebionetworks.bridge.TestConstants.USER_STUDY_IDS;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.APPOINTMENT_REPORT;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.APP_ID;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.FHIR_CONTEXT;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.OBSERVATION_REPORT;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.PROCEDURE_REPORT;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.SYN_USERNAME;
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

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.hl7.fhir.dstu3.model.Address;
import org.hl7.fhir.dstu3.model.Appointment;
import org.hl7.fhir.dstu3.model.Appointment.AppointmentParticipantComponent;
import org.hl7.fhir.dstu3.model.ContactPoint.ContactPointSystem;
import org.hl7.fhir.dstu3.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.dstu3.model.Identifier;
import org.hl7.fhir.dstu3.model.Observation;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.ProcedureRequest;
import org.hl7.fhir.dstu3.model.Reference;
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
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.config.BridgeConfig;
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
import org.sagebionetworks.bridge.models.studies.Enrollment;
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.services.AppService;
import org.sagebionetworks.bridge.services.HealthDataService;
import org.sagebionetworks.bridge.services.ParticipantService;
import org.sagebionetworks.bridge.services.ReportService;
import org.sagebionetworks.bridge.services.SessionUpdateService;

public class CRCControllerTest extends Mockito {

    private static final String LOCATION_NS = "Location/";
    static final LocalDate JAN1 = LocalDate.parse("1970-01-01");
    static final LocalDate JAN2 = LocalDate.parse("1970-01-02");
    static final String HEALTH_CODE = "healthCode";
    String CREDENTIALS = CUIMC_USERNAME + ":dummy-password";
    String AUTHORIZATION_HEADER_VALUE = "Basic "
            + new String(Base64.getEncoder().encode(CREDENTIALS.getBytes()));
    
    static final Enrollment ENROLLMENT1 = Enrollment.create(APP_ID, "studyA", USER_ID);
    static final Enrollment ENROLLMENT2 = Enrollment.create(APP_ID, "studyB", USER_ID);
    static final AccountId ACCOUNT_ID = AccountId.forId(APP_ID, USER_ID);
    static final AccountId ACCOUNT_ID_FOR_HC = AccountId.forHealthCode(APP_ID, HEALTH_CODE);
    static final String LOCATION_JSON = TestUtils.createJson("{ 'id': 'ColSite1', 'meta': { 'id': 'Location/ColSite1', "
            +"'versionId': '1', 'lastUpdated': '2020-06-12T01:38:24.841Z' }, 'resourceType': 'Location', "
            +"'status': 'active', 'name': 'ColSite1', 'type': { 'coding': [ { 'code': 'HUSCS', 'display': "
            +"'Collection Site' } ] }, 'telecom': [ { 'system': 'phone', 'value': '1231231235', 'use': "
            +"'work' } ], 'address': { 'line': [ '123 east 165' ], 'city': 'New York', 'state': 'NY', "
            +"'postalCode': '10021' } }");
    
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
    BridgeConfig mockConfig;
    
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
        account.setId(USER_ID);
        account.setEnrollments(ImmutableSet.of(ENROLLMENT1, ENROLLMENT2));
        account.setDataGroups(ImmutableSet.of("group1", TEST_USER_GROUP));
        
        app = App.create();
        app.setIdentifier(APP_ID);
        when(mockAppService.getApp(APP_ID)).thenReturn(app);
        
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
        doReturn(TestConstants.TIMESTAMP).when(controller).getTimestamp();
        
        when(mockConfig.get("cuimc.test.url")).thenReturn("http://testServer/${patientId}");
        when(mockConfig.get("cuimc.test.location.url")).thenReturn("http://testServer/location/${location}");
        when(mockConfig.get("cuimc.test.username")).thenReturn("testUsername");
        when(mockConfig.get("cuimc.test.password")).thenReturn("testPassword");
        when(mockConfig.get("cuimc.prod.url")).thenReturn("http://prodServer/${patientId}");
        when(mockConfig.get("cuimc.prod.location.url")).thenReturn("http://testServer/location/${location}");
        when(mockConfig.get("cuimc.prod.username")).thenReturn("prodUsername");
        when(mockConfig.get("cuimc.prod.password")).thenReturn("prodPassword");
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
    public void updateParticipantCallsExternalTest() throws Exception {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        
        when(mockAccountService.getAccount(ACCOUNT_ID_FOR_HC)).thenReturn(account);
        
        HttpResponse mockResponse = mock(HttpResponse.class);
        StatusLine mockStatusLine = mock(StatusLine.class);
        when(mockResponse.getStatusLine()).thenReturn(mockStatusLine);
        when(mockStatusLine.getStatusCode()).thenReturn(200);
                
        doReturn(mockResponse).when(controller).put(any(), any(), any());
        
        StatusMessage message = controller.updateParticipant("healthcode:"+HEALTH_CODE);
        assertEquals(message.getMessage(), CRCController.UPDATE_MSG);

        verify(mockAccountService).updateAccount(account, null);
        verify(controller).createLabOrder(account);
        verify(controller).put(any(), stringCaptor.capture(), any());

        assertEquals(account.getDataGroups(), makeSetOf(CRCController.AccountStates.TESTS_REQUESTED, "group1"));
        assertEquals(stringCaptor.getValue(), TestUtils.createJson("{'resourceType':'Patient','id':'userId',"
                +"'meta':{'tag':[{'system':'source','code':'sage'}]},'identifier':[{'system':"
                +"'https://ws.sagebridge.org/#userId','value':'userId'}],'active':true,'gender':'unknown',"
                +"'address':[{'state':'NY'}],'contact':[{'organization':{'reference':'CUZUCK','display':"
                +"'COVID Recovery Corps'}}]}"));
        assertFalse(BridgeUtils.getRequestContext().getCallerStudies().isEmpty());
    }
    
    @Test
    public void updateParticipantCallsExternalProd() throws Exception {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        
        account.setDataGroups(ImmutableSet.of("group1", TEST_USER_GROUP));
        when(mockAccountService.getAccount(ACCOUNT_ID_FOR_HC)).thenReturn(account);
        
        HttpResponse mockResponse = mock(HttpResponse.class);
        StatusLine mockStatusLine = mock(StatusLine.class);
        when(mockResponse.getStatusLine()).thenReturn(mockStatusLine);
        when(mockStatusLine.getStatusCode()).thenReturn(200);
                
        doReturn(mockResponse).when(controller).put(any(), any(), any());
        
        StatusMessage message = controller.updateParticipant("healthcode:"+HEALTH_CODE);
        assertEquals(message.getMessage(), CRCController.UPDATE_MSG);

        verify(mockAccountService).updateAccount(account, null);
        verify(controller).createLabOrder(account);
        verify(controller).put(any(), stringCaptor.capture(), any());

        assertEquals(account.getDataGroups(), makeSetOf(CRCController.AccountStates.TESTS_REQUESTED, "group1"));
        assertEquals(stringCaptor.getValue(), TestUtils.createJson("{'resourceType':'Patient','id':'userId',"
                +"'meta':{'tag':[{'system':'source','code':'sage'}]},'identifier':[{'system':"
                +"'https://ws.sagebridge.org/#userId','value':'userId'}],'active':true,'gender':'unknown',"
                +"'address':[{'state':'NY'}],'contact':[{'organization':{'reference':'CUZUCK','display':"
                +"'COVID Recovery Corps'}}]}"));
        assertFalse(BridgeUtils.getRequestContext().getCallerStudies().isEmpty());
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp = "Account not found.")
    public void updateParticipantAccountNotFound() {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
                
        controller.updateParticipant("healthcode:"+HEALTH_CODE);
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = "Production accounts are not yet enabled.")
    public void updateParticipantFailsOnProductionAccount() throws Exception {
        account.setDataGroups(ImmutableSet.of());
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        
        when(mockAccountService.getAccount(ACCOUNT_ID_FOR_HC)).thenReturn(account);
        
        controller.updateParticipant("healthcode:"+HEALTH_CODE);
    }

    @Test
    public void externalIdAccountSubmitsCorrectCredentials() throws Exception {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.getAccount(ACCOUNT_ID_FOR_HC)).thenReturn(account);
        when(mockAppService.getApp(APP_ID)).thenReturn(mockApp);
        when(mockApp.getIdentifier()).thenReturn(APP_ID);
        when(mockAccountService.authenticate(eq(mockApp), any())).thenReturn(account);
        mockExternalService(200, "OK");
        
        controller.updateParticipant("healthcode:"+HEALTH_CODE);
        
        verify(mockAccountService).authenticate(eq(mockApp), signInCaptor.capture());
        assertEquals(signInCaptor.getValue().getExternalId(), CUIMC_USERNAME);
    }

    @Test
    public void emailAccountSubmitsCorrectCredentials() throws Exception {
        String credentials = SYN_USERNAME + ":dummy-password";
        String authHeader = "Basic "
                + new String(Base64.getEncoder().encode(credentials.getBytes()));
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(authHeader);
        when(mockAccountService.getAccount(ACCOUNT_ID_FOR_HC)).thenReturn(account);
        when(mockAppService.getApp(APP_ID)).thenReturn(mockApp);
        when(mockApp.getIdentifier()).thenReturn(APP_ID);
        when(mockAccountService.authenticate(eq(mockApp), any())).thenReturn(account);
        mockExternalService(200, "OK");
        
        controller.updateParticipant("healthcode:"+HEALTH_CODE);
        
        verify(mockAccountService).authenticate(eq(mockApp), signInCaptor.capture());
        assertEquals(signInCaptor.getValue().getEmail(), SYN_USERNAME);
    }
    
    @Test
    public void createLabOrderOK() throws Exception {
        mockExternalService(200, "OK");
        // no errors
        controller.createLabOrder(account);
        
        // Currently in production we are also using the test values
        verify(controller).put(eq("http://testServer/userId"), any(), any());
    }
    
    @Test
    public void createLabOrderOKInTest() throws Exception {
        account.setDataGroups(ImmutableSet.of(TEST_USER_GROUP));
        mockExternalService(200, "OK");
        // no errors
        controller.createLabOrder(account);
        
        verify(controller).put(eq("http://testServer/userId"), any(), any());
    }

    @Test
    public void createLabOrderCreated() throws Exception { 
        mockExternalService(201, "Created");
        // no errors
        controller.createLabOrder(account);
    }
    
    @Test(expectedExceptions = BridgeServiceException.class, 
            expectedExceptionsMessageRegExp = "Internal Service Error")
    public void createLabOrderBadRequest() throws Exception { 
        mockExternalService(400, "Bad Request");
        controller.createLabOrder(account);
    }
    
    @Test(expectedExceptions = BridgeServiceException.class)
    public void createLabOrderInternalServerError() throws Exception { 
        mockExternalService(500, "Internal Server Error");
        controller.createLabOrder(account);
    }
    
    @Test(expectedExceptions = BridgeServiceException.class)
    public void createLabOrderServiceUnavailable() throws Exception { 
        mockExternalService(503, "Service Unavailable");
        controller.createLabOrder(account);
    }
    
    @Test(expectedExceptions = BridgeServiceException.class)
    public void createLabOrderIOException() throws Exception {
        doThrow(new IOException()).when(controller).put(any(), any(), any());
        controller.createLabOrder(account);
    }
    
    private void mockExternalService(int statusCode, String statusReason) throws Exception {
        StatusLine statusLine = new BasicStatusLine(new ProtocolVersion("HTTP", 1, 2), statusCode, statusReason);
        HttpResponse response = new BasicHttpResponse(statusLine);
        doReturn(response).when(controller).put(any(), any(), any());
    }

    private void addAppointmentSageId(Appointment appointment, String value) {
        AppointmentParticipantComponent comp = new AppointmentParticipantComponent();
        
        Identifier id = new Identifier();
        id.setSystem(USER_ID_VALUE_NS);
        id.setValue(value);
        
        Reference ref = new Reference();
        ref.setIdentifier(id);
        
        comp.setActor(ref);
        appointment.addParticipant(comp);
    }
    
    private void addAppointmentParticipantComponent(Appointment appointment, String value) {
        AppointmentParticipantComponent comp = new AppointmentParticipantComponent();
        comp.setActor(new Reference(value));
        appointment.addParticipant(comp);
    }
    
    @Test
    public void postAppointmentCreated() throws Exception {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        mockGetLocation();
        
        DateRangeResourceList<? extends ReportData> results = new  DateRangeResourceList<>(ImmutableList.of());
        doReturn(results).when(mockReportService).getParticipantReport(
                APP_ID, APPOINTMENT_REPORT, HEALTH_CODE, JAN1, JAN2);
        
        Appointment appointment = new Appointment();

        // add a wrong participant to verify we go through them all and look for ours
        addAppointmentParticipantComponent(appointment, "Location/some-other-id");
        addAppointmentSageId(appointment, USER_ID);
        
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
        verifyParticipant(capturedReport.getData());
        assertEquals(capturedReport.getStudyIds(), USER_STUDY_IDS);
        
        verify(mockAccountService).updateAccount(accountCaptor.capture(), isNull());
        Account capturedAcct = accountCaptor.getValue();
        assertEquals(capturedAcct.getDataGroups(), makeSetOf(CRCController.AccountStates.TESTS_SCHEDULED, "group1"));
        assertEquals(capturedAcct.getAttributes().get(TIMESTAMP_FIELD), TIMESTAMP.toString());
        
        verify(mockHealthDataService).submitHealthData(eq(APP_ID), participantCaptor.capture(), dataCaptor.capture());
        HealthDataSubmission healthData = dataCaptor.getValue();
        assertEquals(healthData.getAppVersion(), "v1");
        assertEquals(healthData.getCreatedOn(), TIMESTAMP);
        assertEquals(healthData.getMetadata().toString(), "{\"type\":\""+APPOINTMENT_REPORT+"\"}");
        assertEquals(healthData.getData().toString(), TestUtils.createJson("{'resourceType':'Appointment',"
                +"'participant':[{'actor':{'reference':'Location/some-other-id','telecom':[{'system':"
                +"'phone','value':'1231231235','use':'work'}],'address':{'line':['123 east 165'],'city'"
                +":'New York','state':'NY','postalCode':'10021'}}},{'actor':{'identifier':{'system':"
                +"'https://ws.sagebridge.org/#userId','value':'userId'}}}]}"));
    }
    
    @Test
    public void postAppointmentUpdated() throws Exception {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        mockGetLocation();
        
        DateRangeResourceList<? extends ReportData> results = new DateRangeResourceList<>(ImmutableList.of(ReportData.create()));
        doReturn(results).when(mockReportService).getParticipantReport(
                APP_ID, APPOINTMENT_REPORT, HEALTH_CODE, JAN1, JAN2);
        
        Appointment appointment = new Appointment();
        // add a wrong participant to verify we go through them all and look for ours
        addAppointmentParticipantComponent(appointment, LOCATION_NS + "foo");
        addAppointmentSageId(appointment, USER_ID);
        
        String json = FHIR_CONTEXT.newJsonParser().encodeResourceToString(appointment);
        mockRequestBody(mockRequest, json);
        
        ResponseEntity<StatusMessage> retValue = controller.postAppointment();
        assertEquals(retValue.getBody().getMessage(), "Appointment updated.");
        assertEquals(retValue.getStatusCodeValue(), 200);
        
        verify(controller).addLocation(any(), eq(account), eq("foo"));
        verify(controller).get("http://testServer/location/foo", account);
        verify(mockHealthDataService).submitHealthData(eq(APP_ID), participantCaptor.capture(), dataCaptor.capture());
        
        HealthDataSubmission healthData = dataCaptor.getValue();
        assertEquals(healthData.getAppVersion(), "v1");
        assertEquals(healthData.getCreatedOn(), TIMESTAMP);
        assertEquals(healthData.getMetadata().toString(), "{\"type\":\""+APPOINTMENT_REPORT+"\"}");
        assertEquals(healthData.getData().toString(), TestUtils.createJson("{'resourceType':'Appointment',"
                +"'participant':[{'actor':{'reference':'Location/foo','telecom':"
                +"[{'system':'phone','value':'1231231235','use':'work'}],'address':{'line':"
                +"['123 east 165'],'city':'New York','state':'NY','postalCode':'10021'}}},"
                +"{'actor':{'identifier':{'system':'https://ws.sagebridge.org/#userId',"
                +"'value':'userId'}}}]}"));
    }
    
    @Test
    public void postAppointmentFailsWhenLocationCallFails() throws Exception {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        HttpResponse mockResponse = mock(HttpResponse.class);
        doReturn(mockResponse).when(controller).get(any(), any());
        
        StatusLine mockStatusLine = mock(StatusLine.class);
        when(mockResponse.getStatusLine()).thenReturn(mockStatusLine);
        when(mockStatusLine.getStatusCode()).thenReturn(500);
        
        Appointment appointment = new Appointment();
        addAppointmentParticipantComponent(appointment, LOCATION_NS + "some-other-id");
        addAppointmentSageId(appointment, USER_ID);
        
        String json = FHIR_CONTEXT.newJsonParser().encodeResourceToString(appointment);
        mockRequestBody(mockRequest, json);
        
        try {
            controller.postAppointment();
            fail("Should have thrown exception");
        } catch(BridgeServiceException e) {
        }
        verify(mockHealthDataService, never()).submitHealthData(any(), any(), any());
    }
    
    @Test
    public void postAppointmentSkipsLocationIfNotPresent() throws Exception {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        DateRangeResourceList<? extends ReportData> results = new  DateRangeResourceList<>(ImmutableList.of());
        doReturn(results).when(mockReportService).getParticipantReport(
                APP_ID, APPOINTMENT_REPORT, HEALTH_CODE, JAN1, JAN2);

        Appointment appointment = new Appointment();
        addAppointmentSageId(appointment, USER_ID);
        
        String json = FHIR_CONTEXT.newJsonParser().encodeResourceToString(appointment);
        mockRequestBody(mockRequest, json);
        
        controller.postAppointment();
        verify(controller, never()).get(any(), any());
        verify(mockHealthDataService).submitHealthData(any(), any(), any());        
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
        verifySubject(capturedReport.getData());
        assertEquals(capturedReport.getStudyIds(), USER_STUDY_IDS);
        
        verify(mockAccountService).updateAccount(accountCaptor.capture(), isNull());
        Account capturedAcct = accountCaptor.getValue();
        assertEquals(capturedAcct.getDataGroups(), makeSetOf(CRCController.AccountStates.TESTS_COLLECTED, "group1"));
        assertEquals(capturedAcct.getAttributes().get(TIMESTAMP_FIELD), TIMESTAMP.toString());
        
        verify(mockHealthDataService).submitHealthData(eq(APP_ID), participantCaptor.capture(), dataCaptor.capture());
        HealthDataSubmission healthData = dataCaptor.getValue();
        assertEquals(healthData.getAppVersion(), "v1");
        assertEquals(healthData.getCreatedOn(), TIMESTAMP);
        assertEquals(healthData.getMetadata().toString(), "{\"type\":\""+PROCEDURE_REPORT+"\"}");
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
        assertEquals(healthData.getMetadata().toString(), "{\"type\":\""+PROCEDURE_REPORT+"\"}");
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
        verifySubject(capturedReport.getData());
        assertEquals(capturedReport.getStudyIds(), USER_STUDY_IDS);
        
        verify(mockAccountService).updateAccount(accountCaptor.capture(), isNull());
        Account capturedAcct = accountCaptor.getValue();
        assertEquals(capturedAcct.getDataGroups(), makeSetOf(CRCController.AccountStates.TESTS_AVAILABLE, "group1"));
        assertEquals(capturedAcct.getAttributes().get(TIMESTAMP_FIELD), TIMESTAMP.toString());
        
        verify(mockHealthDataService).submitHealthData(eq(APP_ID), participantCaptor.capture(), dataCaptor.capture());
        HealthDataSubmission healthData = dataCaptor.getValue();
        assertEquals(healthData.getAppVersion(), "v1");
        assertEquals(healthData.getCreatedOn(), TIMESTAMP);
        assertEquals(healthData.getMetadata().toString(), "{\"type\":\""+OBSERVATION_REPORT+"\"}");
        assertEquals(healthData.getData().toString(), json);
    }
    
    private void verifySubject(JsonNode node) {
        String ns = node.get("subject").get("identifier").get("system").textValue();
        String value = node.get("subject").get("identifier").get("value").textValue();
        assertEquals(ns, USER_ID_VALUE_NS); 
        assertEquals(value, USER_ID);
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
        assertEquals(healthData.getMetadata().toString(), "{\"type\":\""+OBSERVATION_REPORT+"\"}");
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
    
    void mockGetLocation() throws Exception {
        HttpResponse mockResponse = mock(HttpResponse.class);
        doReturn(mockResponse).when(controller).get(any(), any());
        
        StatusLine mockStatusLine = mock(StatusLine.class);
        when(mockResponse.getStatusLine()).thenReturn(mockStatusLine);
        when(mockStatusLine.getStatusCode()).thenReturn(200);

        HttpEntity mockEntity = mock(HttpEntity.class);
        when(mockResponse.getEntity()).thenReturn(mockEntity);
        when(mockEntity.getContent()).thenReturn(IOUtils.toInputStream(LOCATION_JSON));
    }
    
    @Test
    public void authenticationPopulatesRequestContext() {
        account.setOrgMembership(TEST_ORG_ID);

        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAppService.getApp(CRCController.APP_ID)).thenReturn(mockApp);
        when(mockAccountService.authenticate(eq(mockApp), signInCaptor.capture())).thenReturn(account);
        
        controller.httpBasicAuthentication();
        
        RequestContext context = BridgeUtils.getRequestContext();
        assertEquals(context.getCallerAppId(), APP_ID);
        assertEquals(context.getCallerOrgMembership(), TEST_ORG_ID);
        assertEquals(context.getCallerStudies(), USER_STUDY_IDS);
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
        String authValue = "Basic " + new String(Base64.getEncoder().encode(credentials.getBytes()));        
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(authValue);

        controller.httpBasicAuthentication();
    }
    
    @Test(expectedExceptions = NotAuthenticatedException.class)
    public void authenticationInvalidUsername() {
        String credentials = "not-the-right-person:dummy-password";
        String authValue = "Basic " + new String(Base64.getEncoder().encode(credentials.getBytes()));        
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
        assertEquals(patient.getMeta().getTag().get(0).getSystem(), "source");
        assertEquals(patient.getMeta().getTag().get(0).getCode(), "sage");
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
        Patient patient = controller.createPatient(account);
        assertEquals(patient.getGender().name(), "UNKNOWN");
        // I'm defaulting this because I don't see the client submitting it in the UI, so
        // I'm anticipating it won't be there, but eventually we'll have to collect state.
        assertEquals(patient.getAddress().get(0).getState(), "NY");
        assertTrue(patient.getActive());
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = "Could not find Bridge user ID.")
    public void appointmentMissingIdentifiers() throws Exception {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        mockRequestBody(mockRequest, makeAppointment(null));
        
        mockGetLocation();
        
        controller.postAppointment();
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp = "Account not found.")
    public void appointmentWrongIdentifier() throws Exception {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        mockRequestBody(mockRequest, makeAppointment("not-the-right-id"));
        
        mockGetLocation();
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(null);
        
        controller.postAppointment();
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = "Could not find Bridge user ID.")
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
            expectedExceptionsMessageRegExp = "Could not find Bridge user ID.")
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
            expectedExceptionsMessageRegExp = "Could not find Bridge user ID.")
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
            expectedExceptionsMessageRegExp = "Could not find Bridge user ID.")
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
        mockRequestBody(mockRequest, makeAppointment(USER_ID));
        mockGetLocation();
        
        controller.postAppointment();
    }

    @Test(expectedExceptions = NotAuthenticatedException.class)
    public void callerUserNameIncorrect() throws Exception {
        String auth = new String(Base64.getEncoder().encode(("foo:dummy-password").getBytes()));
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn("Basic " + auth);
        mockRequestBody(mockRequest, makeAppointment(USER_ID));
        
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
    
    private void verifyParticipant(JsonNode payload) {
        ArrayNode identifiers = (ArrayNode)payload.get("participant");
        for (int i=0; i < identifiers.size(); i++) {
            JsonNode node = identifiers.get(i);
            if (node.get("actor").has("identifier")) {
                String ns = node.get("actor").get("identifier").get("system").textValue();
                String value = node.get("actor").get("identifier").get("value").textValue();
                if (value.equals(USER_ID) && USER_ID_VALUE_NS.equals(ns)) {
                    return;
                }
            }
        }
        fail("Should have thrown exception");
    }
    
    private Set<String> makeSetOf(CRCController.AccountStates state, String unaffectedGroup) {
        return ImmutableSet.of(state.name().toLowerCase(), unaffectedGroup, TEST_USER_GROUP);
    }
    
    private String makeAppointment(String identifier) {
        Appointment appt = new Appointment();
        if (identifier != null) {
            addAppointmentSageId(appt, identifier);
        }
        addAppointmentParticipantComponent(appt, LOCATION_NS + "ny-location");
        return FHIR_CONTEXT.newJsonParser().encodeResourceToString(appt);
    }
    
    private String makeProcedureRequest() { 
        ProcedureRequest procedure = new ProcedureRequest();
        
        Identifier id = new Identifier();
        id.setSystem(USER_ID_VALUE_NS);
        id.setValue(USER_ID);
        
        Reference ref = new Reference();
        ref.setIdentifier(id);
        
        procedure.setSubject(ref);
        return FHIR_CONTEXT.newJsonParser().encodeResourceToString(procedure);
    }
    
    private String makeObservation() {
        Observation obs = new Observation();
        Identifier id = new Identifier();
        id.setSystem(USER_ID_VALUE_NS);
        id.setValue(USER_ID);
        
        Reference ref = new Reference();
        ref.setIdentifier(id);

        obs.setSubject(ref);
        return FHIR_CONTEXT.newJsonParser().encodeResourceToString(obs);
    }
}
