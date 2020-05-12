package org.sagebionetworks.bridge.spring.controllers;

import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
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
import static org.sagebionetworks.bridge.spring.controllers.CRCController.USERNAME;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.USER_ID_VALUE_NS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

import java.util.Base64;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.Appointment;
import org.hl7.fhir.r4.model.ContactPoint.ContactPointSystem;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Procedure;
import org.joda.time.LocalDate;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.reports.ReportData;
import org.sagebionetworks.bridge.models.substudies.AccountSubstudy;
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.services.AppService;
import org.sagebionetworks.bridge.services.ParticipantService;
import org.sagebionetworks.bridge.services.ReportService;
import org.sagebionetworks.bridge.services.SessionUpdateService;

public class CRCControllerTest extends Mockito {

    static final String HEALTH_CODE = "healthCode";
    String CREDENTIALS = USERNAME + ":dummy-password";
    String AUTHORIZATION_HEADER_VALUE = "Basic "
            + new String(Base64.getEncoder().encode(CREDENTIALS.getBytes()));
    
    static final AccountSubstudy ACCT_SUB1 = AccountSubstudy.create(APP_ID, "substudyA", USER_ID);
    static final AccountSubstudy ACCT_SUB2 = AccountSubstudy.create(APP_ID, "substudyB", USER_ID);
    static final AccountId ACCOUNT_ID = AccountId.forId(APP_ID, USER_ID);
    
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
        
        app = App.create();
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
    public void updateParticipantCallsExternal() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withDataGroups(makeSetOf(CRCController.AccountStates.SELECTED)).build();
        mockRequestBody(mockRequest, participant);
        
        UserSession session = new UserSession();
        session.setAppId(APP_ID);
        session.setParticipant(new StudyParticipant.Builder().withId(USER_ID).build());
        doReturn(session).when(controller).getAuthenticatedSession(RESEARCHER);

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

        when(mockParticipantService.getParticipant(app, USER_ID, true))
                .thenReturn(new StudyParticipant.Builder().build());
        
        controller.updateParticipantSelf();
        
        verify(controller, never()).createLabOrder(any());
    }    
    
    @Test
    public void postAppointment() throws Exception {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        // add a wrong ID to verify we go through them all and look for ours
        Identifier wrongId = new Identifier();
        wrongId.setSystem("some-other-namespace");
        wrongId.setValue(USER_ID);
        
        Appointment appointment = new Appointment();
        appointment.addIdentifier(wrongId);
        appointment.addIdentifier(makeIdentifier(USER_ID_VALUE_NS, USER_ID));
        String json = FHIR_CONTEXT.newJsonParser().encodeResourceToString(appointment);
        mockRequestBody(mockRequest, json);
        
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
        verifyIdentifier(capturedReport.getData());
        assertEquals(capturedReport.getSubstudyIds(), USER_SUBSTUDY_IDS);
        
        verify(mockAccountService).updateAccount(accountCaptor.capture(), isNull());
        Account capturedAcct = accountCaptor.getValue();
        assertEquals(capturedAcct.getDataGroups(), makeSetOf(CRCController.AccountStates.TESTS_SCHEDULED));
        assertEquals(capturedAcct.getAttributes().get(TIMESTAMP_FIELD), TIMESTAMP.toString());
    }
    
    @Test
    public void postProcedure() throws Exception {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        mockRequestBody(mockRequest, makeProcedure());
        
        StatusMessage retValue = controller.postProcedure();
        assertEquals(retValue.getMessage(), "Procedure created or updated.");
        
        verify(mockAccountService).authenticate(eq(app), signInCaptor.capture());
        SignIn capturedSignIn = signInCaptor.getValue();
        assertEquals(capturedSignIn.getAppId(), APP_ID);
        assertEquals(capturedSignIn.getExternalId(), USERNAME);
        assertEquals(capturedSignIn.getPassword(), "dummy-password");
        
        verify(mockReportService).saveParticipantReport(eq(APP_ID), eq(PROCEDURE_REPORT), eq(HEALTH_CODE),
                reportCaptor.capture());
        ReportData capturedReport = reportCaptor.getValue();
        assertEquals(capturedReport.getDate(), "1970-01-01");
        verifyIdentifier(capturedReport.getData());
        assertEquals(capturedReport.getSubstudyIds(), USER_SUBSTUDY_IDS);
        
        verify(mockAccountService).updateAccount(accountCaptor.capture(), isNull());
        Account capturedAcct = accountCaptor.getValue();
        assertEquals(capturedAcct.getDataGroups(), makeSetOf(CRCController.AccountStates.TESTS_COLLECTED));
        assertEquals(capturedAcct.getAttributes().get(TIMESTAMP_FIELD), TIMESTAMP.toString());
    }

    @Test
    public void postObservation() throws Exception {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        mockRequestBody(mockRequest, makeObservation());
        
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
        verifyIdentifier(capturedReport.getData());
        assertEquals(capturedReport.getSubstudyIds(), USER_SUBSTUDY_IDS);
        
        verify(mockAccountService).updateAccount(accountCaptor.capture(), isNull());
        Account capturedAcct = accountCaptor.getValue();
        assertEquals(capturedAcct.getDataGroups(), makeSetOf(CRCController.AccountStates.TESTS_AVAILABLE));
        assertEquals(capturedAcct.getAttributes().get(TIMESTAMP_FIELD), TIMESTAMP.toString());
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
            .put("address1", "123 Sesame Street")
            .put("address2", "Apt. 6")
            .put("city", "Seattle")
            .put("dob", "1980-08-10")
            .put("gender", "female")
            .put("state", "WA")
            .put("zip_code", "10001").build());
        Patient patient = controller.createPatient(builder.build());
        
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
        Patient patient = controller.createPatient(new StudyParticipant.Builder().build());
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
        
        Procedure procedure = new Procedure();
        String json = FHIR_CONTEXT.newJsonParser().encodeResourceToString(procedure);
        mockRequestBody(mockRequest, json);
        
        controller.postProcedure();
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
        Procedure procedure = new Procedure();
        procedure.addIdentifier(identifier);
        String json = FHIR_CONTEXT.newJsonParser().encodeResourceToString(procedure);
        mockRequestBody(mockRequest, json);
        
        controller.postProcedure();        
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
        
        controller.postProcedure();
    }

    @Test
    public void createPatientWithMaleGender() {
        Map<String,String> map = ImmutableMap.of("gender", "MALE");
        StudyParticipant participant = new StudyParticipant.Builder()
                .withAttributes(map).build();
        
        Patient patient = controller.createPatient(participant);
        assertEquals(patient.getGender(), AdministrativeGender.MALE);
    }

    @Test
    public void createPatientWithOtherGender() {
        Map<String,String> map = ImmutableMap.of("gender", "Other");
        StudyParticipant participant = new StudyParticipant.Builder()
                .withAttributes(map).build();
        
        Patient patient = controller.createPatient(participant);
        assertEquals(patient.getGender(), AdministrativeGender.OTHER);
    }

    @Test
    public void createPatientWithPhoneUnverified() {
        StudyParticipant participant = new StudyParticipant.Builder()
            .withEmail(EMAIL)
            .withEmailVerified(true)
            .withPhone(PHONE)
            .withPhoneVerified(false).build();
        Patient patient = controller.createPatient(participant);
        assertEquals(patient.getTelecom().size(), 1);
        assertEquals(patient.getTelecom().get(0).getSystem(), ContactPointSystem.EMAIL);
    }

    @Test
    public void createPatientWithEmailUnverified() {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withEmail(EMAIL)
                .withEmailVerified(false)
                .withPhone(PHONE)
                .withPhoneVerified(true).build();
        Patient patient = controller.createPatient(participant);
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
    
    private Set<String> makeSetOf(CRCController.AccountStates state) {
        return ImmutableSet.of(state.name().toLowerCase());
    }
    
    private String makeAppointment(String ns, String identifier) {
        Appointment appt = new Appointment();
        if (identifier != null) {
            appt.addIdentifier(makeIdentifier(ns, identifier));    
        }
        return FHIR_CONTEXT.newJsonParser().encodeResourceToString(appt);
    }
    
    private String makeProcedure() { 
        Procedure procedure = new Procedure();
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
