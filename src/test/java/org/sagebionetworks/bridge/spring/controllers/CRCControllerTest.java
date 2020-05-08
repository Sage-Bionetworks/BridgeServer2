package org.sagebionetworks.bridge.spring.controllers;

import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.sagebionetworks.bridge.TestConstants.USER_ID;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.APPOINTMENT_REPORT;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.APP_ID;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.OBSERVATION_REPORT;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.PROCEDURAL_REQUEST_REPORT;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.USERNAME;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.util.Base64;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.Patient;
import org.joda.time.LocalDate;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.reports.ReportData;
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.services.AppService;
import org.sagebionetworks.bridge.services.ParticipantService;
import org.sagebionetworks.bridge.services.ReportService;
import org.sagebionetworks.bridge.services.SessionUpdateService;

public class CRCControllerTest extends Mockito {

    private static final String HEALTH_CODE = "healthCode";
    private String CREDENTIALS = USERNAME + ":dummy-password";
    private String AUTHORIZATION_HEADER_VALUE = "Basic "
            + new String(Base64.getEncoder().encode(CREDENTIALS.getBytes()));
    
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
    
    @Captor
    ArgumentCaptor<SignIn> signInCaptor;
    
    @Captor
    ArgumentCaptor<StudyParticipant> participantCaptor;
    
    @Captor
    ArgumentCaptor<ReportData> reportCaptor;
    
    @Captor
    ArgumentCaptor<Account> accountCaptor;
    
    Account account;
    
    @InjectMocks
    @Spy
    CRCController controller;

    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);

        account = Account.create();
        
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
    }
    
    @Test
    public void updateParticipantCallsExternal() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withDataGroups(makeSetOf(CRCController.AccountStates.SELECTED)).build();
        mockRequestBody(mockRequest, participant);
        
        UserSession session = new UserSession();
        session.setAppId(APP_ID);
        session.setParticipant(new StudyParticipant.Builder().withId(USER_ID).build());
        doReturn(session).when(controller).getAuthenticatedSession(RESEARCHER);

        App app = App.create();
        when(mockAppService.getApp(APP_ID)).thenReturn(app);
        
        when(mockParticipantService.getParticipant(app, USER_ID, true))
                .thenReturn(new StudyParticipant.Builder().build());
        
        JsonNode retValue = controller.updateParticipantSelf();
        assertNotNull(retValue);
        
        verify(mockParticipantService).updateParticipant(eq(app), participantCaptor.capture());
        verify(controller).createLabOrder(any()); // expand on this
        verify(mockSessionUpdateService).updateParticipant(eq(session), any(), any(StudyParticipant.class));
        
        StudyParticipant captured = participantCaptor.getValue();
        assertEquals(captured.getDataGroups(), makeSetOf(CRCController.AccountStates.TESTS_REQUESTED));
    }
    
    @Test
    public void updateParticipantDoesNotCallExternal() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withDataGroups(makeSetOf(CRCController.AccountStates.DECLINED)).build();
        mockRequestBody(mockRequest, participant);
        
        UserSession session = new UserSession();
        session.setAppId(APP_ID);
        session.setParticipant(new StudyParticipant.Builder().withId(USER_ID).build());
        doReturn(session).when(controller).getAuthenticatedSession(RESEARCHER);

        App app = App.create();
        when(mockAppService.getApp(APP_ID)).thenReturn(app);
        
        when(mockParticipantService.getParticipant(app, USER_ID, true))
                .thenReturn(new StudyParticipant.Builder().build());
        
        controller.updateParticipantSelf();
        
        verify(controller, never()).createLabOrder(any());
    }    
    
    @Test
    public void postAppointment() throws Exception {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        
        account.setHealthCode(HEALTH_CODE);
        AccountId accountId = AccountId.forId(APP_ID, USER_ID);
        when(mockAccountService.getAccount(accountId)).thenReturn(account);
        
        mockRequestBody(mockRequest, "{\"userId\":\""+ USER_ID + "\"}");
        
        App app = App.create();
        when(mockAppService.getApp(APP_ID)).thenReturn(app);

        StatusMessage retValue = controller.postAppointment();
        assertEquals(retValue.getMessage(), "Appointment created or updated.");
        
        verify(mockAccountService).authenticate(eq(app), signInCaptor.capture());
        SignIn capturedSignIn = signInCaptor.getValue();
        assertEquals(capturedSignIn.getAppId(), APP_ID);
        assertEquals(capturedSignIn.getExternalId(), USERNAME);
        assertEquals(capturedSignIn.getPassword(), "dummy-password");
        
        verify(mockReportService).saveParticipantReport(eq(APP_ID), eq(APPOINTMENT_REPORT), eq(HEALTH_CODE),
                reportCaptor.capture());
        ReportData capturedReport = reportCaptor.getValue();
        assertEquals(capturedReport.getDate(), "1970-01-01");
        assertNotNull(capturedReport.getData()); // TODO
        
        verify(mockAccountService).updateAccount(accountCaptor.capture(), isNull());
        Account capturedAcct = accountCaptor.getValue();
        assertEquals(capturedAcct.getDataGroups(), makeSetOf(CRCController.AccountStates.TESTS_SCHEDULED));
    }
    
    @Test
    public void postProceduralRequest() throws Exception {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        
        account.setHealthCode(HEALTH_CODE);
        AccountId accountId = AccountId.forId(APP_ID, USER_ID);
        when(mockAccountService.getAccount(accountId)).thenReturn(account);
        
        mockRequestBody(mockRequest, "{\"userId\":\""+ USER_ID + "\"}");
        
        App app = App.create();
        when(mockAppService.getApp(APP_ID)).thenReturn(app);

        StatusMessage retValue = controller.postProceduralRequest();
        assertEquals(retValue.getMessage(), "ProceduralRequest created or updated.");
        
        verify(mockAccountService).authenticate(eq(app), signInCaptor.capture());
        SignIn capturedSignIn = signInCaptor.getValue();
        assertEquals(capturedSignIn.getAppId(), APP_ID);
        assertEquals(capturedSignIn.getExternalId(), USERNAME);
        assertEquals(capturedSignIn.getPassword(), "dummy-password");
        
        verify(mockReportService).saveParticipantReport(eq(APP_ID), eq(PROCEDURAL_REQUEST_REPORT), eq(HEALTH_CODE),
                reportCaptor.capture());
        ReportData capturedReport = reportCaptor.getValue();
        assertEquals(capturedReport.getDate(), "1970-01-01");
        assertNotNull(capturedReport.getData()); // TODO
        
        verify(mockAccountService).updateAccount(accountCaptor.capture(), isNull());
        Account capturedAcct = accountCaptor.getValue();
        assertEquals(capturedAcct.getDataGroups(), makeSetOf(CRCController.AccountStates.TESTS_COLLECTED));
    }

    @Test
    public void postObservation() throws Exception {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        
        account.setHealthCode(HEALTH_CODE);
        AccountId accountId = AccountId.forId(APP_ID, USER_ID);
        when(mockAccountService.getAccount(accountId)).thenReturn(account);
        
        mockRequestBody(mockRequest, "{\"userId\":\""+ USER_ID + "\"}");
        
        App app = App.create();
        when(mockAppService.getApp(APP_ID)).thenReturn(app);

        StatusMessage retValue = controller.postObservation();
        assertEquals(retValue.getMessage(), "Observation created or updated.");
        
        verify(mockAccountService).authenticate(eq(app), signInCaptor.capture());
        SignIn capturedSignIn = signInCaptor.getValue();
        assertEquals(capturedSignIn.getAppId(), APP_ID);
        assertEquals(capturedSignIn.getExternalId(), USERNAME);
        assertEquals(capturedSignIn.getPassword(), "dummy-password");
        
        verify(mockReportService).saveParticipantReport(eq(APP_ID), eq(OBSERVATION_REPORT), eq(HEALTH_CODE),
                reportCaptor.capture());
        ReportData capturedReport = reportCaptor.getValue();
        assertEquals(capturedReport.getDate(), "1970-01-01");
        assertNotNull(capturedReport.getData()); // TODO
        
        verify(mockAccountService).updateAccount(accountCaptor.capture(), isNull());
        Account capturedAcct = accountCaptor.getValue();
        assertEquals(capturedAcct.getDataGroups(), makeSetOf(CRCController.AccountStates.TESTS_AVAILABLE));
    }
    
    @Test
    public void authenticationWorks() {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);

        when(mockAppService.getApp(CRCController.APP_ID)).thenReturn(mockApp);
        when(mockAccountService.authenticate(eq(mockApp), signInCaptor.capture())).thenReturn(account);
        
        controller.httpBasicAuthentication();
        
        SignIn captured = signInCaptor.getValue();
        assertEquals(captured.getAppId(), CRCController.APP_ID);
        assertEquals(captured.getExternalId(), CRCController.USERNAME);
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
        String credentials = CRCController.USERNAME + ":dummy-password:some-nonsense";
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

    @Test(expectedExceptions = NotAuthenticatedException.class)
    public void authenticationInvalidCredentials() {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAppService.getApp(CRCController.APP_ID)).thenReturn(mockApp);
        
        controller.httpBasicAuthentication();
    }
    
    @Test
    public void createPatient() {
        StudyParticipant.Builder builder = new StudyParticipant.Builder()
        .withId("userId")
        .withFirstName("Test")
        .withLastName("User")
        .withEmail(EMAIL)
        .withEmailVerified(true)
        .withPhone(PHONE)
        .withPhoneVerified(true)
        .withAttributes(new ImmutableMap.Builder<String, String>()
            .put("address", "123 Sesame Street")
            .put("city", "New York")
            .put("dob", "1980-08-10")
            .put("gender", "female")
            .put("state", "NY")
            .put("zip_code", "10001").build());
        Patient patient = controller.createPatient(builder.build());
        
        assertTrue(patient.getActive());
        assertEquals(patient.getId(), USER_ID);
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
        assertEquals(address.getCity(), "New York");
        assertEquals(address.getState(), "NY");
        assertEquals(address.getPostalCode(), "10001");
    }
    
    private Set<String> makeSetOf(CRCController.AccountStates state) {
        return ImmutableSet.of(state.name().toLowerCase());
    }
}
