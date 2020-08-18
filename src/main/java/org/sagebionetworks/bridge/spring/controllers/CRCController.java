package org.sagebionetworks.bridge.spring.controllers;

import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static java.lang.Boolean.TRUE;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.BridgeConstants.API_APP_ID;
import static org.sagebionetworks.bridge.BridgeUtils.parseAccountId;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.AccountStates.SELECTED;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.AccountStates.TESTS_AVAILABLE;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.AccountStates.TESTS_AVAILABLE_TYPE_UNKNOWN;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.AccountStates.TESTS_CANCELLED;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.AccountStates.TESTS_SCHEDULED;
import static org.sagebionetworks.bridge.util.BridgeCollectors.toImmutableSet;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.HttpHeaders;

import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.hl7.fhir.dstu3.model.Address;
import org.hl7.fhir.dstu3.model.Appointment;
import org.hl7.fhir.dstu3.model.Appointment.AppointmentParticipantComponent;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.ContactPoint;
import org.hl7.fhir.dstu3.model.Extension;
import org.hl7.fhir.dstu3.model.ContactPoint.ContactPointSystem;
import org.hl7.fhir.dstu3.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.dstu3.model.HumanName;
import org.hl7.fhir.dstu3.model.Identifier;
import org.hl7.fhir.dstu3.model.Meta;
import org.hl7.fhir.dstu3.model.Observation;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Patient.ContactComponent;
import org.hl7.fhir.dstu3.model.ProcedureRequest;
import org.hl7.fhir.dstu3.model.Range;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.instance.model.api.IPrimitiveType;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
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
import org.sagebionetworks.bridge.services.HealthDataService;
import org.sagebionetworks.bridge.services.ParticipantService;
import org.sagebionetworks.bridge.services.ReportService;
import org.sagebionetworks.bridge.upload.UploadValidationException;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

@CrossOrigin
@RestController
public class CRCController extends BaseController {
    private static final Logger LOG = LoggerFactory.getLogger(CRCController.class);

//    private static final String UNAVAILABLE_ERROR = "The server encountered an error";
//    private static final String INT_ERROR = "Error calling CUIMC to submit patient record, user ";
//    private static final String EXT_ERROR = "Received non-2xx series response when submitting patient record for user ";
    static final String GEOCODING_API = "https://maps.googleapis.com/maps/api/geocode/json?address=%s&key=%s";
    static final String GEOCODE_KEY = "crc.geocode.api.key";
    static final String TIMESTAMP_FIELD = "state_change_timestamp";
    static final String USER_ID_VALUE_NS = "https://ws.sagebridge.org/#userId";
    static final String APP_ID = "czi-coronavirus";
    static final String OBSERVATION_REPORT = "observation";
    static final String PROCEDURE_REPORT = "procedurerequest";
    static final String APPOINTMENT_REPORT = "appointment";
    static final String CUIMC_USERNAME = "A5hfO-tdLP_eEjx9vf2orSd5";
    static final String SYN_USERNAME = "bridgeit+crc@sagebase.org";
    static final String TEST_USERNAME = "bridge-testing+crc@sagebase.org";
    static final String SELECTED_TAG = AccountStates.SELECTED.name().toLowerCase();
    static final String TEST_TAG = BridgeConstants.TEST_USER_GROUP;
    static final String UPDATE_MSG = "Participant updated.";
    static final String UPDATE_FOR_TEST_ACCOUNT_MSG = "Participant updated (although eligible, a lab order was not placed for this test account).";
    // This is thread-safe and it's recommended to reuse an instance because it's expensive to create;
    static final FhirContext FHIR_CONTEXT = FhirContext.forDstu3();
    static final LocalDate JAN1 = LocalDate.parse("1970-01-01");
    static final LocalDate JAN2 = LocalDate.parse("1970-01-02");
    static final Map<String, String> ACCOUNTS = ImmutableMap.of(
            CUIMC_USERNAME, APP_ID, 
            SYN_USERNAME, APP_ID,
            TEST_USERNAME, API_APP_ID);
    static final Set<String> SERUM_TEST_CODES = ImmutableSet.of("484670513");
    static final Set<String> SERUM_TEST_STATES = ImmutableSet.of("Negative", "Positive", "Indeterminate");

    static enum AccountStates {
        ENROLLED, 
        SELECTED, 
        DECLINED, 
        TESTS_REQUESTED, 
        TESTS_SCHEDULED,
        TESTS_CANCELLED, 
        TESTS_COLLECTED, 
        TESTS_AVAILABLE,
        TESTS_AVAILABLE_TYPE_UNKNOWN
    }

    private static final Set<String> ALL_STATES = Arrays.stream(AccountStates.values()).map(e -> e.name().toLowerCase())
            .collect(toImmutableSet());

    private ParticipantService participantService;

    private ReportService reportService;

    private HealthDataService healthDataService;

    @Autowired
    final void setParticipantService(ParticipantService participantService) {
        this.participantService = participantService;
    }

    @Autowired
    final void setReportService(ReportService reportService) {
        this.reportService = reportService;
    }

    @Autowired
    final void setHealthDataService(HealthDataService healthDataService) {
        this.healthDataService = healthDataService;
    }

    DateTime getTimestamp() {
        return DateTime.now().withZone(DateTimeZone.UTC);
    }

    String getUserAgent() {
        String userAgent = request().getHeader(HttpHeaders.USER_AGENT);
        return (userAgent == null) ? "<Unknown>" : userAgent;
    }

    @PostMapping("/v1/cuimc/participants/{userId}/laborders")
    public StatusMessage updateParticipant(@PathVariable String userId) {
        App app = httpBasicAuthentication();

        AccountId accountId = parseAccountId(app.getIdentifier(), userId);
        Account account = accountService.getAccount(accountId);
        if (account == null) {
            throw new EntityNotFoundException(Account.class);
        }
        // This is temporary so we can start using the CRC system in production, 
        // while continuing to test. The calling code should not update the state of
        // the user if it receives a 400.
//        if (!account.getDataGroups().contains(TEST_USER_GROUP)) {
//            throw new BadRequestException("Production accounts are not yet enabled.");
//        }
        // All the code related to requesting a lab order will be removed once we've 
        // confirmed that this is Columbia's final approach to the integration.
        // createLabOrder(account);

        updateState(account, SELECTED);
        accountService.updateAccount(account, null);
        return new StatusMessage("Participant updated.");
    }

    @PutMapping("/v1/cuimc/appointments")
    public ResponseEntity<StatusMessage> postAppointment() {
        App app = httpBasicAuthentication();

        IParser parser = FHIR_CONTEXT.newJsonParser();
        JsonNode data = parseJson(JsonNode.class);
        Appointment appointment = parser.parseResource(Appointment.class, data.toString());

        String userId = findUserId(appointment);
        
        // They send appointment when it is booked, cancelled, or (rarely) enteredinerror.
        AccountStates state = TESTS_SCHEDULED;
        String apptStatus = data.get("status").asText();
        if ("entered-in-error".equals(apptStatus)) {
            deleteReportAndUpdateState(app, userId);
            return ResponseEntity.ok(new StatusMessage("Appointment deleted."));
        } else if ("cancelled".equals(apptStatus)) {
            state = TESTS_CANCELLED;
        }
        
        // Columbia wants us to call back to them to get information about the location.
        // And UI team wants geocoding of location to render a map.
        /*
        String locationString = findLocation(appointment);
        if (locationString != null) {
            AccountId accountId = parseAccountId(app.getIdentifier(), userId);
            Account account = accountService.getAccount(accountId);
            if (account == null) {
                throw new EntityNotFoundException(Account.class);
            }
            addLocation(data, account, locationString);
        }
        */
        
        int status = writeReportAndUpdateState(app, userId, data, APPOINTMENT_REPORT, state);
        if (status == 200) {
            return ResponseEntity.ok(new StatusMessage("Appointment updated (status = " + apptStatus + ")."));
        }
        return ResponseEntity.created(URI.create("/v1/cuimc/appointments/" + userId))
                .body(new StatusMessage("Appointment created (status = " + apptStatus + ")."));
    }
    
    @PutMapping("/v1/cuimc/procedurerequests")
    public ResponseEntity<StatusMessage> postProcedureRequest() {
        App app = httpBasicAuthentication();

        IParser parser = FHIR_CONTEXT.newJsonParser();
        JsonNode data = parseJson(JsonNode.class);
        ProcedureRequest procedure = parser.parseResource(ProcedureRequest.class, data.toString());

        String userId = findUserId(procedure.getSubject());

        int status = writeReportAndUpdateState(app, userId, data, PROCEDURE_REPORT, AccountStates.TESTS_COLLECTED);
        if (status == 200) {
            return ResponseEntity.ok(new StatusMessage("ProcedureRequest updated."));
        }
        return ResponseEntity.created(URI.create("/v1/cuimc/procedurerequests/" + userId))
                .body(new StatusMessage("ProcedureRequest created."));
    }

    @PutMapping("/v1/cuimc/observations")
    public ResponseEntity<StatusMessage> postObservation() {
        App app = httpBasicAuthentication();

        IParser parser = FHIR_CONTEXT.newJsonParser();
        JsonNode data = parseJson(JsonNode.class);
        Observation observation = parser.parseResource(Observation.class, data.toString());
        
        AccountStates state = TESTS_AVAILABLE;
        // There are two conditions under which the type of report is considered to be unknown. Either the code 
        // is for a test other than the serum test, or the result value is not recognized by our client application,
        // and that's also treated as an unknown type of report.
        String code = getObservationCoding(observation);
        String valueString = getObservationValue(observation);
        if (code == null || valueString == null || !SERUM_TEST_CODES.contains(code) || !SERUM_TEST_STATES.contains(valueString)) {
            state = TESTS_AVAILABLE_TYPE_UNKNOWN;
            LOG.warn("CRC observation in unknown format: code=" + code + ", valueString=" + valueString);
        }

        String userId = findUserId(observation.getSubject());

        int status = writeReportAndUpdateState(app, userId, data, OBSERVATION_REPORT, state);
        if (status == 200) {
            return ResponseEntity.ok(new StatusMessage("Observation updated."));
        }
        return ResponseEntity.created(URI.create("/v1/cuimc/observations/" + userId))
                .body(new StatusMessage("Observation created."));
    }
    
    String getObservationCoding(Observation observation) {
        CodeableConcept code = observation.getCode();
        if (code != null) {
            List<Coding> codings = code.getCoding();
            if (codings != null && !codings.isEmpty()) {
                Coding coding = codings.get(0);
                if (coding != null) {
                    return coding.getCode();
                }
            }
        }
        return null;
    }
    
    String getObservationValue(Observation observation) {
        Range range = observation.getValueRange();
        if (range != null) {
            List<Extension> extensions = range.getExtension();
            if (extensions != null && !extensions.isEmpty()) {
                @SuppressWarnings("rawtypes")
                IPrimitiveType ptype = extensions.get(0).getValueAsPrimitive();
                if (ptype != null) {
                    return ptype.getValueAsString();
                }
            }
        }
        return null;
    }
    /*
    void createLabOrder(Account account) {
        // Call external partner here and submit the patient record
        // this will trigger workflow at Columbia.
        Patient patient = createPatient(account);
        IParser parser = FHIR_CONTEXT.newJsonParser();
        String json = parser.encodeResourceToString(patient);

        String cuimcEnv = (account.getDataGroups().contains(TEST_USER_GROUP)) ? "test" : "prod";
        String cuimcUrl = "cuimc." + cuimcEnv + ".url";
        String url = bridgeConfig.get(cuimcUrl);
        url = resolveTemplate(url, ImmutableMap.of("patientId", account.getId()));

        try {
            HttpResponse response = put(url, json, account);
            throwExceptions(response, account.getId());
        } catch (IOException e) {
            LOG.error(INT_ERROR + account.getId(), e);
            throw new ServiceUnavailableException(UNAVAILABLE_ERROR);
        }
        LOG.info("Patient record submitted to CUIMC for user " + account.getId());
    }

    void addLocation(JsonNode node, Account account, String location) {
        String cuimcEnv = (account.getDataGroups().contains(TEST_USER_GROUP)) ? "test" : "prod";
        String cuimcUrl = "cuimc." + cuimcEnv + ".location.url";
        String url = bridgeConfig.get(cuimcUrl);
        url = resolveTemplate(url, ImmutableMap.of("location", location));

        try {
            HttpResponse response = get(url, account);
            throwExceptions(response, account.getId());
            
            String body = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8.name());
            JsonNode locationJson = BridgeObjectMapper.get().readTree(body);
            JsonNode telecom = locationJson.get("telecom");
            JsonNode address = locationJson.get("address");
            
            ArrayNode participants = (ArrayNode)node.get("participant");
            if (participants != null) {
                for (int i=0 ; i < participants.size(); i++) {
                    JsonNode child = participants.get(i);
                    ObjectNode actor = (ObjectNode)child.get("actor");
                    if (actor != null && actor.has("reference")) {
                        String ref = actor.get("reference").textValue();
                        if (ref.startsWith("Location")) {
                            if (telecom != null) {
                                actor.set("telecom", telecom);    
                            }
                            if (address != null) {
                                actor.set("address", address);
                                addGeocodingInformation(actor);                            
                            }
                            break;
                        }
                    }
                }
            }
        } catch (IOException e) {
            LOG.error(INT_ERROR + account.getId(), e);
            throw new ServiceUnavailableException(UNAVAILABLE_ERROR);
        }
        LOG.info("Location added to appointment record for user " + account.getId());
    }

    void addGeocodingInformation(ObjectNode actor) {
        String addressString = combineLocationJson(actor);
        if (addressString != null) {
            String url = String.format(GEOCODING_API, addressString, bridgeConfig.get(GEOCODE_KEY)); 
            try {
                HttpResponse response = get(url);
                if (response.getStatusLine().getStatusCode() != 200) {
                    LOG.error("HTTP error response when geocoding address", response.getStatusLine().toString());
                    return;
                }
                String body = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8.name());
                JsonNode payload = BridgeObjectMapper.get().readTree(body);
                
                if (payload.has("results") && payload.get("results").size() > 0
                        && payload.get("results").get(0).has("geometry")) {
                    JsonNode geometry = payload.get("results").get(0).get("geometry");
                    actor.set("geocoding", geometry);
                } else {
                    LOG.error("Error geocoding address (bad payload returned): " + payload.toString());
                }
            } catch (IOException e) {
                LOG.error("Error geocoding address", e);
            }
        }
    }

    String combineLocationJson(JsonNode actor) {
        if (actor.has("address")) {
            List<String> elements = Lists.newArrayList();
            JsonNode address = actor.get("address");
            if (address.has("line")) {
                ArrayNode lines = (ArrayNode)address.get("line");
                for (int i=0; i < lines.size(); i++) {
                    String line = lines.get(i).textValue();
                    elements.add(line);
                }
            }
            if (address.has("city")) {
                elements.add(address.get("city").textValue());    
            }
            if (address.has("state")) {
                elements.add(address.get("state").textValue());
            }
            if (address.has("postalCode")) {
                elements.add(address.get("postalCode").textValue());    
            }
            return encodeURIComponent(SPACE_JOINER.join(elements));
        }
        return null;
    }
    /*
    private void throwExceptions(HttpResponse response, String userId) {
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode != 200 && statusCode != 201) {
            // This is a transient server error from the external service and we
            // can communicate this back to the caller. Not sure we should log
            // this.
            if (statusCode == 503) {
                throw new ServiceUnavailableException("Service Unavailable");
            }
            LOG.error(EXT_ERROR + userId + ", status code: " + statusCode);
            throw new BridgeServiceException("Internal Service Error");
        }
    }

    HttpResponse put(String url, String bodyJson, Account account) throws IOException {
        Request request = Request.Put(url).bodyString(bodyJson, APPLICATION_JSON);
        request = addAuthorizationHeader(request, account);
        return request.execute().returnResponse();
    }
    */
    
    HttpResponse get(String url) throws IOException {
        return Request.Get(url).execute().returnResponse();
    }
    /*
    HttpResponse get(String url, Account account) throws IOException {
        Request request = Request.Get(url);
        request = addAuthorizationHeader(request, account);
        return request.execute().returnResponse();
    }

    private Request addAuthorizationHeader(Request request, Account account) {
        String cuimcEnv = (account.getDataGroups().contains(TEST_USER_GROUP)) ? "test" : "prod";
        String cuimcUsername = "cuimc." + cuimcEnv + ".username";
        String cuimcPassword = "cuimc." + cuimcEnv + ".password";
        String username = bridgeConfig.get(cuimcUsername);
        String password = bridgeConfig.get(cuimcPassword);
        
        if (isNotBlank(username) && isNotBlank(password)) {
            String credentials = username + ":" + password;
            String hash = new String(Base64.getEncoder().encode(credentials.getBytes(Charset.defaultCharset())),
                    Charset.defaultCharset());
            Header authHeader = new BasicHeader(AUTHORIZATION, "Basic " + hash);
            request = request.addHeader(authHeader);
        }
        return request;
    }

    private String findLocation(Appointment appt) {
        if (appt != null && appt.getParticipant() != null) {
            for (AppointmentParticipantComponent component : appt.getParticipant()) {
                Reference actor = component.getActor();
                if (actor != null) {
                    String ref = actor.getReference();
                    if (ref != null && ref.toLowerCase().startsWith("location/")) {
                        return ref.substring(9);
                    }
                }
            }
        }
        return null;
    }*/
    
    private String findUserId(Appointment appt) {
        if (appt != null && appt.getParticipant() != null) {
            for (AppointmentParticipantComponent component : appt.getParticipant()) {
                Reference actor = component.getActor();
                if (actor != null) {
                    Identifier id = actor.getIdentifier();
                    if (id != null && USER_ID_VALUE_NS.equals(id.getSystem())) {
                        return id.getValue();
                    }
                }
            }
        }
        throw new BadRequestException("Could not find Bridge user ID.");
    }

    private String findUserId(Reference subject) {
        if (subject != null) {
            Identifier id = subject.getIdentifier();
            if (id != null && USER_ID_VALUE_NS.equals(id.getSystem())) {
                return id.getValue();
            }
        }
        throw new BadRequestException("Could not find Bridge user ID.");
    }

    private int writeReportAndUpdateState(App app, String userId, JsonNode data, String reportName,
            AccountStates state) {
        String appId = BridgeUtils.getRequestContext().getCallerAppId();
        AccountId accountId = AccountId.forId(appId, userId);
        Account account = accountService.getAccount(accountId);
        if (account == null) {
            throw new EntityNotFoundException(Account.class);
        }

        try {
            ObjectNode metadata = JsonNodeFactory.instance.objectNode();
            metadata.put("type", reportName);

            StudyParticipant participant = participantService.getParticipant(app, account, false);
            HealthDataSubmission healthData = new HealthDataSubmission.Builder()
                    .withAppVersion("v1")
                    .withCreatedOn(getTimestamp())
                    .withMetadata(metadata)
                    .withData(data)
                    .withPhoneInfo(getUserAgent())
                    .build();
            healthDataService.submitHealthData(appId, participant, healthData);
        } catch (IOException | UploadValidationException e) {
            throw new BridgeServiceException(e);
        }

        Set<String> callerStudyIds = BridgeUtils.getRequestContext().getCallerStudies();
        ReportData report = ReportData.create();
        report.setDate(JAN1.toString());
        report.setData(data);
        report.setStudyIds(callerStudyIds);

        DateRangeResourceList<? extends ReportData> results = reportService.getParticipantReport(appId, reportName,
                account.getHealthCode(), JAN1, JAN2);
        int status = (results.getItems().isEmpty()) ? 201 : 200;

        reportService.saveParticipantReport(appId, reportName, account.getHealthCode(), report);

        updateState(account, state);
        accountService.updateAccount(account, null);
        return status;
    }
    
    private int deleteReportAndUpdateState(App app, String userId) {
        String appId = BridgeUtils.getRequestContext().getCallerAppId();
        AccountId accountId = AccountId.forId(appId, userId);
        Account account = accountService.getAccount(accountId);
        if (account == null) {
            throw new EntityNotFoundException(Account.class);
        }

        reportService.deleteParticipantReportRecord(app.getIdentifier(), APPOINTMENT_REPORT, 
                JAN1.toString(), account.getHealthCode());

        updateState(account, SELECTED);
        accountService.updateAccount(account, null);
        return 200;
    }

    private void updateState(Account account, AccountStates state) {
        Set<String> mutableGroups = new HashSet<>();
        mutableGroups.addAll(account.getDataGroups());
        mutableGroups.removeAll(ALL_STATES);
        mutableGroups.add(state.name().toLowerCase());
        account.setDataGroups(mutableGroups);

        account.getAttributes().put(TIMESTAMP_FIELD, getTimestamp().toString());
    }

    /**
     * This is bound to specific “machine” accounts that are enumerated in the controller. Authentication is
     * session-less. The account itself has no administrative roles, so it can only execute these endpoints that
     * specifically allows it, in the app to which it is bound.
     */
    App httpBasicAuthentication() {
        String value = request().getHeader(AUTHORIZATION);
        if (value == null || value.length() < 5) {
            throw new NotAuthenticatedException();
        }
        // Remove "Basic ";
        value = value.substring(5).trim();

        // Decode the credentials from base 64
        value = new String(Base64.getDecoder().decode(value), Charset.defaultCharset());
        // Split to username and password
        String[] credentials = value.split(":");
        if (credentials.length != 2) {
            throw new NotAuthenticatedException();
        }
        String appId = ACCOUNTS.get(credentials[0]);
        if (appId == null) {
            throw new NotAuthenticatedException();
        }
        SignIn.Builder signInBuilder = new SignIn.Builder().withAppId(appId).withPassword(credentials[1]);
        if (credentials[0].contains("@sagebase.org")) {
            signInBuilder.withEmail(credentials[0]);
        } else {
            signInBuilder.withExternalId(credentials[0]);
        }
        App app = appService.getApp(appId);

        // Verify the password
        SignIn signIn = signInBuilder.build();
        Account account = accountService.authenticate(app, signIn);

        // This method of verification sidesteps RequestContext initialization
        // through a session. Set up what is needed in the controller.
        Set<String> studies = BridgeUtils.collectStudyIds(account);

        RequestContext.Builder builder = new RequestContext.Builder().withCallerAppId(appId)
                .withCallerStudies(studies).withCallerOrgMembership(account.getOrgMembership());
        BridgeUtils.setRequestContext(builder.build());
        return app;
    }
    
    Patient createPatient(Account account) {
        Patient patient = new Patient();
        patient.setActive(true);
        patient.setId(account.getId());

        Identifier identifier = new Identifier();
        identifier.setValue(account.getId());
        identifier.setSystem(USER_ID_VALUE_NS);
        patient.addIdentifier(identifier);
        
        Coding coding = new Coding();
        coding.setSystem("source");
        coding.setCode("sage");
        
        Meta meta = new Meta();
        meta.setTag(ImmutableList.of(coding));
        patient.setMeta(meta);

        HumanName name = new HumanName();
        if (isNotBlank(account.getFirstName())) {
            name.addGiven(account.getFirstName());
        }
        if (isNotBlank(account.getLastName())) {
            name.setFamily(account.getLastName());
        }
        patient.addName(name);

        Map<String, String> atts = account.getAttributes();
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

        if (account.getPhone() != null && TRUE.equals(account.getPhoneVerified())) {
            ContactPoint contact = new ContactPoint();
            contact.setSystem(ContactPointSystem.SMS);
            contact.setValue(account.getPhone().getNumber());
            patient.addTelecom(contact);
        }
        if (account.getEmail() != null && TRUE.equals(account.getEmailVerified())) {
            ContactPoint contact = new ContactPoint();
            contact.setSystem(ContactPointSystem.EMAIL);
            contact.setValue(account.getEmail());
            patient.addTelecom(contact);
        }
        
        Reference ref = new Reference("CUZUCK");
        ref.setDisplay("COVID Recovery Corps");
        ContactComponent contact = new ContactComponent();
        contact.setOrganization(ref);
        patient.addContact(contact);
        
        return patient;
    }
}
