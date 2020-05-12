package org.sagebionetworks.bridge.spring.controllers;

import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static java.lang.Boolean.TRUE;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.AccountStates.TESTS_SCHEDULED;
import static org.sagebionetworks.bridge.util.BridgeCollectors.toImmutableSet;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import org.apache.commons.codec.binary.Base64;
import org.hl7.fhir.dstu3.model.Address;
import org.hl7.fhir.dstu3.model.Appointment;
import org.hl7.fhir.dstu3.model.ContactPoint;
import org.hl7.fhir.dstu3.model.ContactPoint.ContactPointSystem;
import org.hl7.fhir.dstu3.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.dstu3.model.HumanName;
import org.hl7.fhir.dstu3.model.Identifier;
import org.hl7.fhir.dstu3.model.Observation;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Procedure;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.DateRangeResourceList;
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
    
    static final String TIMESTAMP_FIELD = "state_change_timestamp";
    static final String USER_ID_VALUE_NS = "https://ws.sagebridge.org/#userId";
    static final String APP_ID = "czi-coronavirus";
    static final String OBSERVATION_REPORT = "observation";
    static final String PROCEDURE_REPORT = "procedurerequest";
    static final String APPOINTMENT_REPORT = "appointment";
    static final String USERNAME = "A5hfO-tdLP_eEjx9vf2orSd5";
    // This is thread-safe and it's recommended to reuse an instance because it's expensive to create;
    static final FhirContext FHIR_CONTEXT = FhirContext.forDstu3();
    static final LocalDate JAN1 = LocalDate.parse("1970-01-01");
    static final LocalDate JAN2 = LocalDate.parse("1970-01-02");
    
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
    
    DateTime getTimestamp() {
        return DateTime.now().withZone(DateTimeZone.UTC);
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
    
    @PutMapping("/v1/cuimc/appointments")
    public ResponseEntity<StatusMessage> postAppointment() {
        httpBasicAuthentication();
        
        IParser parser = FHIR_CONTEXT.newJsonParser();
        JsonNode data = parseJson(JsonNode.class);
        Appointment appointment = parser.parseResource(Appointment.class, data.toString());
        
        String userId = findUserId(appointment.getIdentifier());
        
        int status = writeReportAndUpdateState(userId, data, APPOINTMENT_REPORT, TESTS_SCHEDULED);
        if (status == 200) {
            return ResponseEntity.ok(new StatusMessage("Appointment updated."));
        }
        return ResponseEntity.created(URI.create("/v1/cuimc/appointments/" + userId))
                .body(new StatusMessage("Appointment created."));
    }

    @PutMapping("/v1/cuimc/procedurerequests")
    public ResponseEntity<StatusMessage> postProcedure() {
        httpBasicAuthentication();
        
        IParser parser = FHIR_CONTEXT.newJsonParser();
        JsonNode data = parseJson(JsonNode.class);
        Procedure procedure = parser.parseResource(Procedure.class, data.toString());
        
        String userId = findUserId(procedure.getIdentifier());
        
        int status = writeReportAndUpdateState(userId, data, PROCEDURE_REPORT, AccountStates.TESTS_COLLECTED);
        if (status == 200) {
            return ResponseEntity.ok(new StatusMessage("ProcedureRequest updated."));
        }
        return ResponseEntity.created(URI.create("/v1/cuimc/procedurerequests/" + userId))
                .body(new StatusMessage("ProcedureRequest created."));
    }

    @PutMapping("/v1/cuimc/observations")
    public ResponseEntity<StatusMessage> postObservation() {
        httpBasicAuthentication();
        
        IParser parser = FHIR_CONTEXT.newJsonParser();
        JsonNode data = parseJson(JsonNode.class);
        Observation observation = parser.parseResource(Observation.class, data.toString());
        
        String userId = findUserId(observation.getIdentifier());
        
        int status = writeReportAndUpdateState(userId, data, OBSERVATION_REPORT, AccountStates.TESTS_AVAILABLE);
        if (status == 200) {
            return ResponseEntity.ok(new StatusMessage("Observation updated."));
        }
        return ResponseEntity.created(URI.create("/v1/cuimc/observations/" + userId))
                .body(new StatusMessage("Observation created."));        
    }
    
    void createLabOrder(StudyParticipant participant) {
        Patient patient = createPatient(participant);
        
        IParser parser = FHIR_CONTEXT.newJsonParser();
        
        System.out.println(parser.encodeResourceToString(patient));
    }

    private String findUserId(List<Identifier> identifiers) {
        if (identifiers != null && !identifiers.isEmpty()) {
            for (int i=0; i < identifiers.size(); i++) {
                Identifier id = identifiers.get(i);
                if (id.getSystem().equals(USER_ID_VALUE_NS)) {
                    return id.getValue();
                }
            }
        }
        throw new BadRequestException("Could not find Bridge user ID in identifiers.");
    }

    private int writeReportAndUpdateState(String userId, JsonNode data, String reportName, AccountStates state) {
        AccountId accountId = AccountId.forId(APP_ID, userId);
        Account account = accountService.getAccount(accountId);
        if (account == null) {
            throw new EntityNotFoundException(Account.class);
        }
        Set<String> callerSubstudyIds = BridgeUtils.getRequestContext().getCallerSubstudies();
        ReportData report = ReportData.create();
        report.setDate(JAN1.toString());
        report.setData(data);
        report.setSubstudyIds(callerSubstudyIds);
        
        DateRangeResourceList<? extends ReportData> results = reportService.getParticipantReport(
                APP_ID, reportName, account.getHealthCode(), JAN1, JAN2);
        int status = (results.getItems().isEmpty()) ? 201 : 200;
        
        reportService.saveParticipantReport(APP_ID, reportName, account.getHealthCode(), report);
        
        updateState(account, state);
        accountService.updateAccount(account, null);
        return status;
    }    
    
    private void updateState(StudyParticipant.Builder builder, AccountStates state) {
        StudyParticipant p = builder.build();
        Set<String> mutableGroups = new HashSet<>(); 
        mutableGroups.addAll(p.getDataGroups());
        mutableGroups.removeAll(ALL_STATES);
        mutableGroups.add(state.name().toLowerCase());
        builder.withDataGroups(mutableGroups);
        
        Map<String, String> atts = new HashMap<>();
        atts.putAll(p.getAttributes());
        atts.put(TIMESTAMP_FIELD, getTimestamp().toString());
        builder.withAttributes(atts);
    }
    
    private void updateState(Account account, AccountStates state) {
        Set<String> mutableGroups = new HashSet<>(); 
        mutableGroups.addAll(account.getDataGroups());
        mutableGroups.removeAll(ALL_STATES);
        mutableGroups.add(state.name().toLowerCase());
        account.setDataGroups(mutableGroups);

        account.getAttributes().put(TIMESTAMP_FIELD, getTimestamp().toString());
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
        // This method of verification entirely sidesteps RequestContext 
        // initialization. Set up anything that is needed in this controller.
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(BridgeUtils.collectSubstudyIds(account))
                .build());
    }
    
    Patient createPatient(StudyParticipant participant) {
        Patient patient = new Patient();
        patient.setActive(true);
        
        Identifier identifier = new Identifier();
        identifier.setValue(participant.getId());
        identifier.setSystem(USER_ID_VALUE_NS);
        patient.addIdentifier(identifier);
        
        HumanName name = new HumanName();
        if (isNotBlank(participant.getFirstName())) {
            name.addGiven(participant.getFirstName());
        }
        if (isNotBlank(participant.getLastName())) {
            name.setFamily(participant.getLastName());
        }
        patient.addName(name);
        
        Map<String, String> atts = participant.getAttributes();
        if (isNotBlank(atts.get("gender"))) {
            if ("female".equalsIgnoreCase(atts.get("gender"))) {
                patient.setGender(AdministrativeGender.FEMALE);    
            } else if ("male".equalsIgnoreCase(atts.get("gender"))) {
                patient.setGender(AdministrativeGender.MALE);
            } else {
                patient.setGender(AdministrativeGender.OTHER);
            }
        } else {
            patient.setGender(AdministrativeGender.UNKNOWN);
        }
        if (isNotBlank(atts.get("dob"))) {
            LocalDate localDate = LocalDate.parse(atts.get("dob"));
            patient.setBirthDate(localDate.toDate());
        }
        
        Address address = new Address();
        if (isNotBlank(atts.get("address1"))) {
            address.addLine(atts.get("address1"));
        }
        if (isNotBlank(atts.get("address2"))) {
            address.addLine(atts.get("address2"));
        }
        if (isNotBlank(atts.get("city"))) {
            address.setCity(atts.get("city"));
        }
        if (isNotBlank(atts.get("state"))) {
            address.setState(atts.get("state"));
        } else {
            address.setState("NY");
        }
        if (isNotBlank(atts.get("zip_code"))) {
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

