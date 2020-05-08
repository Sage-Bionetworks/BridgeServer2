package org.sagebionetworks.bridge.spring.controllers;

import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static java.lang.Boolean.TRUE;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.util.BridgeCollectors.toImmutableSet;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import org.apache.commons.codec.binary.Base64;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.ContactPoint.ContactPointSystem;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Patient;
import org.joda.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.accounts.UserSessionInfo;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.reports.ReportData;
import org.sagebionetworks.bridge.services.ParticipantService;
import org.sagebionetworks.bridge.services.ReportService;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

@CrossOrigin
@RestController
public class CRCController extends BaseController {
    
    static final String APP_ID = "czi-coronavirus";
    static final String OBSERVATION_REPORT = "observation";
    static final String PROCEDURAL_REQUEST_REPORT = "proceduralrequest";
    static final String APPOINTMENT_REPORT = "appointment";
    static final String USERNAME = "A5hfO-tdLP_eEjx9vf2orSd5";
    
    static enum AccountStates {
        ENROLLED,
        SELECTED, 
        DECLINED,
        TESTS_REQUESTED,
        TESTS_SCHEDULED,
        TESTS_COLLECTED,
        TESTS_AVAILABLE
    }
    
    private static final Set<String> ALL_STATES = Arrays.stream(AccountStates.values())
            .map(e -> e.name().toLowerCase())
            .collect(toImmutableSet());
    
    private ParticipantService participantService;
    
    private ReportService reportService;
    
    @Autowired
    final void setParticipantService(ParticipantService participantService) {
        this.participantService = participantService;
    }
    
    @Autowired
    final void setReportService(ReportService reportService) {
        this.reportService = reportService;
    }
    
    @PostMapping("/v1/cuimc/participants/self")
    public JsonNode updateParticipantSelf() {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        App app = appService.getApp(session.getAppId());

        StudyParticipant participant = parseJson(StudyParticipant.class);
 
        // Force userId of the URL
        StudyParticipant.Builder builder = new StudyParticipant.Builder()
                .copyOf(participant)
                .withId(session.getId());
        
        Set<String> dataGroups = participant.getDataGroups();
        if (dataGroups.contains(AccountStates.SELECTED.name().toLowerCase())) {
            // Contact CUIMC
            createLabOrder(builder.build());
            updateState(builder, AccountStates.TESTS_REQUESTED);
        }
        
        participantService.updateParticipant(app, builder.build());

        // Now prepare an updated session
        StudyParticipant updatedParticipant = participantService.getParticipant(app, session.getId(), true);
        RequestContext reqContext = BridgeUtils.getRequestContext();
        
        CriteriaContext context = new CriteriaContext.Builder()
                .withLanguages(session.getParticipant().getLanguages())
                .withClientInfo(reqContext.getCallerClientInfo())
                .withHealthCode(session.getHealthCode())
                .withUserId(session.getId())
                .withUserDataGroups(updatedParticipant.getDataGroups())
                .withUserSubstudyIds(updatedParticipant.getSubstudyIds())
                .withAppId(session.getAppId())
                .build();
        
        sessionUpdateService.updateParticipant(session, context, updatedParticipant);
        
        return UserSessionInfo.toJSON(session);
    } 
    
    @PostMapping("/v1/cuimc/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    public StatusMessage postAppointment() {
        httpBasicAuthentication();
        
        JsonNode data = parseJson(JsonNode.class);
        String userId = data.get("userId").textValue(); // not the actual payload
        
        writeReportAndUpdateState(userId, data, APPOINTMENT_REPORT, AccountStates.TESTS_SCHEDULED);
        
        return new StatusMessage("Appointment created or updated.");
    }
    
    @PostMapping("/v1/cuimc/proceduralrequests")
    @ResponseStatus(HttpStatus.CREATED)
    public StatusMessage postProceduralRequest() {
        httpBasicAuthentication();
        
        JsonNode data = parseJson(JsonNode.class);
        String userId = data.get("userId").textValue(); // not the actual payload
        
        writeReportAndUpdateState(userId, data, PROCEDURAL_REQUEST_REPORT, AccountStates.TESTS_COLLECTED);
        
        return new StatusMessage("ProceduralRequest created or updated.");
    }

    @PostMapping("/v1/cuimc/observations")
    @ResponseStatus(HttpStatus.CREATED)
    public StatusMessage postObservation() {
        httpBasicAuthentication();
        
        JsonNode data = parseJson(JsonNode.class);
        String userId = data.get("userId").textValue(); // not the actual payload
        
        writeReportAndUpdateState(userId, data, OBSERVATION_REPORT, AccountStates.TESTS_AVAILABLE);
        
        return new StatusMessage("Observation created or updated.");
    }
    
    void createLabOrder(StudyParticipant participant) {
        Patient patient = createPatient(participant);
        
        FhirContext ctx = FhirContext.forR4();
        IParser parser = ctx.newJsonParser();
        
        System.out.println(parser.encodeResourceToString(patient));
    }
    
    private void writeReportAndUpdateState(String userId, JsonNode data, String reportName, AccountStates state) {
        AccountId accountId = AccountId.forId(APP_ID, userId);
        Account account = accountService.getAccount(accountId);
        if (account == null) {
            throw new EntityNotFoundException(Account.class);
        }
        ReportData report = ReportData.create();
        report.setDate("1970-01-01");
        report.setData(data);
        
        reportService.saveParticipantReport(APP_ID, reportName, account.getHealthCode(), report);
        
        updateState(account, state);
        accountService.updateAccount(account, null);
    }    
    
    private void updateState(StudyParticipant.Builder builder, AccountStates state) {
        Set<String> mutableGroups = new HashSet<>(); 
        mutableGroups.addAll(builder.build().getDataGroups());
        mutableGroups.removeAll(ALL_STATES);
        mutableGroups.add(state.name().toLowerCase());
        
        builder.withDataGroups(mutableGroups);
    }
    
    private void updateState(Account account, AccountStates state) {
        Set<String> mutableGroups = new HashSet<>(); 
        mutableGroups.addAll(account.getDataGroups());
        mutableGroups.removeAll(ALL_STATES);
        mutableGroups.add(state.name().toLowerCase());
        
        account.setDataGroups(mutableGroups);
    }
    
    // This is bound to one specific account that we've given to Columbia. 
    // No one else can authenticate for these calls, and the account itself
    // has no administrative roles so it can't do anything else besides 
    // calling these methods.
    void httpBasicAuthentication() {
        String value = request().getHeader(AUTHORIZATION);
        if (value == null || value.length() < 5) {
            throw new NotAuthenticatedException();
        }
        // Remove "Basic ";
        value = value.substring(5).trim();

        // Decode the credentials from base 64
        value = new String(Base64.decodeBase64(value));
        // Split to username and password
        String[] credentials = value.split(":");
        if (credentials.length != 2) {
            throw new NotAuthenticatedException();
        }
        if (!credentials[0].equals(USERNAME)) {
            throw new NotAuthenticatedException();
        }
        SignIn signIn = new SignIn.Builder()
                .withAppId(APP_ID)
                .withExternalId(credentials[0])
                .withPassword(credentials[1]).build();
        
        App app = appService.getApp(APP_ID);
        
        // Verify the password
        Account account = accountService.authenticate(app, signIn);
        if (account == null) {
            throw new NotAuthenticatedException();
        }
    }
    
    Patient createPatient(StudyParticipant participant) {
        Patient patient = new Patient();
        patient.setId(participant.getId());
        patient.setActive(true);
        
        HumanName name = new HumanName();
        if (participant.getFirstName() != null) {
            name.addGiven(participant.getFirstName());
        }
        if (participant.getLastName() != null) {
            name.setFamily(participant.getLastName());
        }
        patient.addName(name);
        
        Map<String, String> atts = participant.getAttributes();
        if (atts.get("gender") != null) {
            AdministrativeGender gender = "female".equals(atts.get("gender")) ? 
                    AdministrativeGender.FEMALE : AdministrativeGender.MALE;
            patient.setGender(gender);
        }
        if (atts.get("dob") != null) {
            LocalDate localDate = LocalDate.parse(atts.get("dob"));
            patient.setBirthDate(localDate.toDate());
        }
        
        Address address = new Address();
        if (atts.get("address") != null) {
            address.addLine(atts.get("address"));
        }
        if (atts.get("city") != null) {
            address.setCity(atts.get("city"));
        }
        if (atts.get("state") != null) {
            address.setState(atts.get("state"));
        } else {
            address.setState("NY");
        }
        if (atts.get("zip_code") != null) {
            address.setPostalCode(atts.get("zip_code"));
        }
        patient.addAddress(address);
        
        if (participant.getPhone() != null && TRUE.equals(participant.getPhoneVerified())) {
            ContactPoint contact = new ContactPoint();
            contact.setSystem(ContactPointSystem.SMS);
            contact.setValue(participant.getPhone().getNumber());
            patient.addTelecom(contact);
        }
        if (participant.getEmail() != null && TRUE.equals(participant.getEmailVerified())) {
            ContactPoint contact = new ContactPoint();
            contact.setSystem(ContactPointSystem.EMAIL);
            contact.setValue(participant.getEmail());
            patient.addTelecom(contact);
        }
        return patient;
    }
}

