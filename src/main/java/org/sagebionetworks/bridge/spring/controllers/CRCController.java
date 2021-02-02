package org.sagebionetworks.bridge.spring.controllers;

import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static java.lang.Boolean.TRUE;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.http.entity.ContentType.APPLICATION_FORM_URLENCODED;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;
import static org.sagebionetworks.bridge.BridgeConstants.API_APP_ID;
import static org.sagebionetworks.bridge.BridgeConstants.TEST_USER_GROUP;
import static org.sagebionetworks.bridge.BridgeUtils.SPACE_JOINER;
import static org.sagebionetworks.bridge.BridgeUtils.encodeURIComponent;
import static org.sagebionetworks.bridge.BridgeUtils.getLocalDateOrDefault;
import static org.sagebionetworks.bridge.BridgeUtils.parseAccountId;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.AccountStates.SELECTED;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.AccountStates.SHIP_TESTS_REQUESTED;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.AccountStates.TESTS_AVAILABLE;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.AccountStates.TESTS_AVAILABLE_TYPE_UNKNOWN;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.AccountStates.TESTS_CANCELLED;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.AccountStates.TESTS_SCHEDULED;
import static org.sagebionetworks.bridge.util.BridgeCollectors.toImmutableSet;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.net.HttpHeaders;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.message.BasicHeader;
import org.hl7.fhir.dstu3.model.Address;
import org.hl7.fhir.dstu3.model.Appointment;
import org.hl7.fhir.dstu3.model.Appointment.AppointmentParticipantComponent;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.ContactPoint;
import org.hl7.fhir.dstu3.model.ContactPoint.ContactPointSystem;
import org.hl7.fhir.dstu3.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.dstu3.model.Extension;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.LimitExceededException;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.DateRangeResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.crc.gbf.external.CheckOrderStatusResponse;
import org.sagebionetworks.bridge.models.crc.gbf.external.Order;
import org.sagebionetworks.bridge.models.crc.gbf.external.ShippingConfirmations;
import org.sagebionetworks.bridge.models.healthdata.HealthDataSubmission;
import org.sagebionetworks.bridge.models.reports.ReportData;
import org.sagebionetworks.bridge.services.GBFOrderService;
import org.sagebionetworks.bridge.services.HealthDataService;
import org.sagebionetworks.bridge.services.ParticipantService;
import org.sagebionetworks.bridge.services.ReportService;
import org.sagebionetworks.bridge.upload.UploadValidationException;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;


/**
 * NOTE: There are references to some properties in the config file that can be removed now
 * that we are not calling Columbia's servers. These should also be removed.
 */
@CrossOrigin
@RestController
public class CRCController extends BaseController {
    private static final Logger LOG = LoggerFactory.getLogger(CRCController.class);

    static final String GEOCODING_API = "https://maps.googleapis.com/maps/api/geocode/json?address=%s&key=%s";
    static final String GEOCODE_KEY = "crc.geocode.api.key";
    static final String TIMESTAMP_FIELD = "state_change_timestamp";
    static final String USER_ID_VALUE_NS = "https://ws.sagebridge.org/#userId";
    static final String APP_ID = "czi-coronavirus";
    static final String OBSERVATION_REPORT = "observation";
    static final String PROCEDURE_REPORT = "procedurerequest";
    static final String APPOINTMENT_REPORT = "appointment";
    static final String SHIPMENT_REPORT = "shipmentrequest";
    static final String SHIPMENT_REPORT_KEY_ORDER_ID = "orderNumber";
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
    static final String GBF_TEST_KIT_PART_NUMBER = "FM-00049";

    static enum AccountStates {
        ENROLLED,
        SELECTED,
        DECLINED,
        TESTS_REQUESTED,
        TESTS_SCHEDULED,
        TESTS_CANCELLED,
        TESTS_COLLECTED,
        TESTS_AVAILABLE,
        TESTS_AVAILABLE_TYPE_UNKNOWN,
        SHIP_TESTS_REQUESTED
    }

    private static final Set<String> ALL_STATES = Arrays.stream(AccountStates.values()).map(e -> e.name().toLowerCase())
            .collect(toImmutableSet());

    private ParticipantService participantService;

    private ReportService reportService;

    private HealthDataService healthDataService;

    private GBFOrderService gbfOrderService;

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

    @Autowired
    final void setGbfOrderService(GBFOrderService GBFOrderService) {
        this.gbfOrderService = GBFOrderService;
    }

    DateTime getTimestamp() {
        return DateTime.now().withZone(DateTimeZone.UTC);
    }

    String getUserAgent() {
        String userAgent = request().getHeader(HttpHeaders.USER_AGENT);
        return (userAgent == null) ? "<Unknown>" : userAgent;
    }

    @PostMapping("v1/cuimc/participants/self/labshipments/request")
    public ResponseEntity<StatusMessage> postUserLabShipmentRequest() {
        // caller enrolled studies
        UserSession session = getAuthenticatedSession();
        App app = appService.getApp(session.getAppId());
    
        AccountId accountId = parseAccountId(app.getIdentifier(), session.getId());
        Account account = accountService.getAccount(accountId);
    
        return internalLabShipmentRequest(app, account);
    }

    @PostMapping("v1/cuimc/participants/{userId}/labshipments/request")
    public ResponseEntity<StatusMessage> postLabShipmentRequest(@PathVariable String userId) {
        App app = httpBasicAuthentication();
    
        // Participants will not have OrgSponsoredStudies which is used by writeReportAndUpdateState to set studies on
        // ReportData. Remove studies
        RequestContext requestContextWithoutOrgSponsoredStudies = RequestContext.get()
                .toBuilder().withOrgSponsoredStudies(ImmutableSet.of()).build();
        RequestContext.set(requestContextWithoutOrgSponsoredStudies);
    
        AccountId accountId = parseAccountId(app.getIdentifier(), userId);
        Account account = accountService.getAccount(accountId);
    
        return internalLabShipmentRequest(app, account);
    }

    ResponseEntity<StatusMessage> internalLabShipmentRequest(App app, Account account) {
        if (account == null) {
            throw new EntityNotFoundException(Account.class);
        }

        if (account.getDataGroups().contains(SHIP_TESTS_REQUESTED.name().toLowerCase())) {
            throw new LimitExceededException("Limited to one active shipment request.");
        }

        boolean isTestUser = account.getDataGroups().contains(TEST_USER_GROUP);
        LOG.info("Lab shipment requested for {} participant", isTestUser ? "test" : "non-test");

        LocalDate date = LocalDate.now();
        Order.ShippingInfo.Address address = validateAndGetAddress(account);
        String orderNumber = account.getId() + "_" + date;

        Order o = new Order(isTestUser, orderNumber, account.getId(), date,
                new Order.ShippingInfo(address, null), new Order.LineItem(GBF_TEST_KIT_PART_NUMBER, 1));
        gbfOrderService.placeOrder(o, true);

        JsonNode node = JsonNodeFactory.instance.objectNode().put(SHIPMENT_REPORT_KEY_ORDER_ID, orderNumber);
        
        // participants will not have Org Sponsored Studies in RequestContext, so for lab shipment reports, don't set study IDs
        writeReportAndUpdateState(app, account.getId(), node, SHIPMENT_REPORT, SHIP_TESTS_REQUESTED, false);

        return ResponseEntity.accepted()
                .body(new StatusMessage("Test shipment requested."));
    }

    // performs basic check for fields required for shipping
    Order.ShippingInfo.Address validateAndGetAddress(Account account) {
        if (Strings.isNullOrEmpty(account.getFirstName())) {
            throw new BadRequestException("Missing first name");
        }
        if (Strings.isNullOrEmpty(account.getLastName())) {
            throw new BadRequestException(("Missing last name"));
        }
        String recipientName = account.getFirstName() + " " + account.getLastName();

        Map<String, String> atts = account.getAttributes();
        String address1 = atts.get("address1");
        if (Strings.isNullOrEmpty(address1)) {
            throw new BadRequestException("Missing shipping address1");
        }
        String city = atts.get("city");
        if (Strings.isNullOrEmpty(city)) {
            throw new BadRequestException("Missing shipping city");
        }
        String state = atts.get("state");
        if (Strings.isNullOrEmpty(state)) {
            throw new BadRequestException("Missing shipping state");
        }
        String zip = atts.get("zip_code");
        if (Strings.isNullOrEmpty(zip)) {
            throw new BadRequestException("Missing shipping zip code");
        }

        String phoneString = atts.get("home_phone");
        Phone phone = new Phone(atts.get("home_phone"), "US");
        if (Phone.isValid(phone)) {
            phoneString = phone.getNationalFormat();
        } else {
            throw new BadRequestException(("Missing a valid shipping contact phone number"));
        }

        return new Order.ShippingInfo.Address(
                recipientName,
                address1,
                atts.get("address2"),
                city,
                state,
                zip,
                "United States",
                phoneString
        );
    }

    // Waiting for integration workflow to be finalized
    //@GetMapping(path = "v1/cuimc/labshipments/{orderId}/status")
    public CheckOrderStatusResponse getLabShipmentStatus(@PathVariable String orderId) throws JsonProcessingException {
        httpBasicAuthentication();
        CheckOrderStatusResponse response = gbfOrderService.checkOrderStatus(orderId);
        return response;
    }
    
    // Waiting for integration workflow to be finalized
    //@GetMapping(path = "v1/cuimc/participants/labshipments/confirmations")
    public ShippingConfirmations getLabShipmentConfirmations(@RequestParam String startDate,
            @RequestParam String endDate) throws JsonProcessingException {
        httpBasicAuthentication();
        
        LocalDate startDateObj = getLocalDateOrDefault(startDate, null);
        LocalDate endDateObj = getLocalDateOrDefault(endDate, null);
        
        ShippingConfirmations shippingConfirmations = gbfOrderService.requestShippingConfirmations(startDateObj,
                endDateObj);
        return shippingConfirmations;
    }

    @PostMapping("/v1/cuimc/participants/{userId}/laborders")
    public StatusMessage updateParticipant(@PathVariable String userId) {
        App app = httpBasicAuthentication();

        AccountId accountId = parseAccountId(app.getIdentifier(), userId);
        Account account = accountService.getAccount(accountId);
        if (account == null) {
            throw new EntityNotFoundException(Account.class);
        }

        updateState(account, SELECTED);
        accountService.updateAccount(account);
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
        String locationString = findLocation(appointment);
        if (locationString != null) {
            AccountId accountId = parseAccountId(app.getIdentifier(), userId);
            Account account = accountService.getAccount(accountId);
            if (account == null) {
                throw new EntityNotFoundException(Account.class);
            }
            addLocation(data, account, locationString);
        }
        
        int status = writeReportAndUpdateState(app, userId, data, APPOINTMENT_REPORT, state, true);
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

        int status = writeReportAndUpdateState(app, userId, data, PROCEDURE_REPORT, AccountStates.TESTS_COLLECTED, true);
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

        int status = writeReportAndUpdateState(app, userId, data, OBSERVATION_REPORT, state, true);
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

    /**
     * This is a nice-to-have addition of address information for the location given by an
     * ID in the appointment record. Do not fail the request if this fails, but log enough
     * to troubleshoot if the issue is on our side.
     */
    void addLocation(JsonNode node, Account account, String locationId) {
        String cuimcEnv = (account.getDataGroups().contains(TEST_USER_GROUP)) ? "test" : "prod";
        String cuimcUrl = "cuimc." + cuimcEnv + ".location.url";
        String url = bridgeConfig.get(cuimcUrl);
        String reqBody = "id=\"" + locationId + "\"";

        try {
            HttpResponse response = post(url, account, reqBody);
            String resBody = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8.name());
            
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200 && statusCode != 201) {
                logWarningMessage(locationId, statusCode, resBody);
                return;
            }
            JsonNode bundleJson = BridgeObjectMapper.get().readTree(resBody);
            if (!bundleJson.has("entry") || ((ArrayNode)bundleJson.get("entry")).size() == 0) {
                logWarningMessage(locationId, statusCode, resBody);
                return;
            }
            JsonNode resJson = bundleJson.get("entry").get(0).get("resource");
            if (resJson == null) {
                logWarningMessage(locationId, statusCode, resBody);
                return;
            }
            JsonNode telecom = resJson.get("telecom");
            JsonNode address = resJson.get("address");
            
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
                                //addGeocodingInformation(actor);                            
                            }
                            break;
                        }
                    }
                }
            }
        } catch (IOException e) {
            LOG.warn("Error retrieving location, id = " + locationId, e);
            return;
        }
        LOG.info("Location added to appointment record for user " + account.getId());
    }

    private void logWarningMessage(String locationId, int statusCode, String resBody) {
        // May or may not be utilized
        String errorMsg = "Error retrieving location, id = " + locationId + ", status = " + statusCode
                + ", response body = " + resBody;
        LOG.warn(errorMsg);
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

    HttpResponse put(String url, String bodyJson, Account account) throws IOException {
        Request request = Request.Put(url).bodyString(bodyJson, APPLICATION_JSON);
        request = addAuthorizationHeader(request, account);
        return request.execute().returnResponse();
    }
    
    HttpResponse get(String url) throws IOException {
        return Request.Get(url).execute().returnResponse();
    }

//    HttpResponse get(String url, Account account) throws IOException {
//        Request request = Request.Get(url);
//        request = addAuthorizationHeader(request, account);
//        return request.execute().returnResponse();
//    }
    
    HttpResponse post(String url, Account account, String body) throws IOException {
        Request request = Request.Post(url).bodyString(body, APPLICATION_FORM_URLENCODED);
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
    }
    
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
            AccountStates state, boolean useCallerStudyIds) {
        String appId = RequestContext.get().getCallerAppId();
        AccountId accountId = AccountId.forId(appId, userId);
        Account account = accountService.getAccount(accountId);
        if (account == null) {
            throw new EntityNotFoundException(Account.class);
        }

        updateState(account, state);
        accountService.updateAccount(account);

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

        Set<String> callerStudyIds = useCallerStudyIds ? RequestContext.get().getOrgSponsoredStudies() : ImmutableSet.of();
        ReportData report = ReportData.create();
        report.setDate(JAN1.toString());
        report.setData(data);
        report.setStudyIds(callerStudyIds);

        DateRangeResourceList<? extends ReportData> results = reportService.getParticipantReport(appId, reportName,
                account.getHealthCode(), JAN1, JAN2);
        int status = (results.getItems().isEmpty()) ? 201 : 200;

        reportService.saveParticipantReport(appId, reportName, account.getHealthCode(), report);
        return status;
    }
    
    private int deleteReportAndUpdateState(App app, String userId) {
        String appId = RequestContext.get().getCallerAppId();
        AccountId accountId = AccountId.forId(appId, userId);
        Account account = accountService.getAccount(accountId);
        if (account == null) {
            throw new EntityNotFoundException(Account.class);
        }

        reportService.deleteParticipantReportRecord(app.getIdentifier(), APPOINTMENT_REPORT,
                JAN1.toString(), account.getHealthCode());

        updateState(account, SELECTED);
        accountService.updateAccount(account);
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
                .withOrgSponsoredStudies(studies)
                .withCallerOrgMembership(account.getOrgMembership());
        RequestContext.set(builder.build());
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

        if (isNotBlank(atts.get("home_phone"))) {
            ContactPoint contact = new ContactPoint();
            contact.setSystem(ContactPointSystem.PHONE);
            contact.setValue(atts.get("home_phone"));
            patient.addTelecom(contact);
        }

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
