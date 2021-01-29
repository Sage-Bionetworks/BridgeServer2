package org.sagebionetworks.bridge.spring.controllers;

import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static com.google.common.net.HttpHeaders.USER_AGENT;
import static org.hl7.fhir.dstu3.model.Appointment.AppointmentStatus.BOOKED;
import static org.hl7.fhir.dstu3.model.Appointment.AppointmentStatus.CANCELLED;
import static org.hl7.fhir.dstu3.model.Appointment.AppointmentStatus.ENTEREDINERROR;
import static org.sagebionetworks.bridge.BridgeConstants.TEST_USER_GROUP;
import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.IP_ADDRESS;
import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.sagebionetworks.bridge.TestConstants.USER_STUDY_IDS;
import static org.sagebionetworks.bridge.TestUtils.createJson;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.APPOINTMENT_REPORT;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.APP_ID;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.AccountStates.SHIP_TESTS_REQUESTED;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.CUIMC_USERNAME;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.FHIR_CONTEXT;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.OBSERVATION_REPORT;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.PROCEDURE_REPORT;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.SHIPMENT_REPORT;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.SHIPMENT_REPORT_KEY_ORDER_ID;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.SYN_USERNAME;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.TIMESTAMP_FIELD;
import static org.sagebionetworks.bridge.spring.controllers.CRCController.USER_ID_VALUE_NS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.IOException;
import java.util.Base64;
import java.util.Set;
import java.util.stream.Stream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.hl7.fhir.dstu3.model.Address;
import org.hl7.fhir.dstu3.model.Appointment;
import org.hl7.fhir.dstu3.model.Appointment.AppointmentParticipantComponent;
import org.hl7.fhir.dstu3.model.Appointment.AppointmentStatus;
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
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.slf4j.LoggerFactory;
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
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.LimitExceededException;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.DateRangeResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.crc.gbf.external.ShippingConfirmations;
import org.sagebionetworks.bridge.models.healthdata.HealthDataSubmission;
import org.sagebionetworks.bridge.models.reports.ReportData;
import org.sagebionetworks.bridge.models.studies.Enrollment;
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.services.AppService;
import org.sagebionetworks.bridge.services.GBFOrderService;
import org.sagebionetworks.bridge.services.HealthDataService;
import org.sagebionetworks.bridge.services.ParticipantService;
import org.sagebionetworks.bridge.services.ReportService;
import org.sagebionetworks.bridge.services.SessionUpdateService;

import ca.uhn.fhir.parser.IParser;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;

public class CRCControllerTest extends Mockito {

    private static final String LOCATION_NS = "Location/";
    static final LocalDate JAN1 = LocalDate.parse("1970-01-01");
    static final LocalDate JAN2 = LocalDate.parse("1970-01-02");
    static final String HEALTH_CODE = "healthCode";
    String CREDENTIALS = CUIMC_USERNAME + ":dummy-password";
    String AUTHORIZATION_HEADER_VALUE = "Basic "
            + new String(Base64.getEncoder().encode(CREDENTIALS.getBytes()));
    
    static final Enrollment ENROLLMENT1 = Enrollment.create(APP_ID, "studyA", TEST_USER_ID);
    static final Enrollment ENROLLMENT2 = Enrollment.create(APP_ID, "studyB", TEST_USER_ID);
    static final AccountId ACCOUNT_ID = AccountId.forId(APP_ID, TEST_USER_ID);
    static final AccountId ACCOUNT_ID_FOR_HC = AccountId.forHealthCode(APP_ID, HEALTH_CODE);
    static final String LOCATION_JSON = TestUtils.createJson("{ 'id':'4b72216e-f638-4eb0-b901-e23ca2aa69de', "
            +"'meta':{ 'lastUpdated':'2020-08-20T12:41:26Z' }, 'resourceType':'Bundle', 'type':'searchset', "
            +"'total':1, 'entry':[ { 'resource':{ 'id':'CovidRecoveryChony', 'extension':[ { 'valueDuration':"
            +"{ 'code':'0/15 8-17 * * MON-FRI' } } ], 'meta':{ 'id':'Location/CovidRecoveryChony', 'versionId':"
            +"'1', 'lastUpdated':'2020-08-07T14:58:05.088-04:00' }, 'contained':[ { 'id':'CovidRecovery', "
            +"'resourceType':'Organization', 'name':'Covid Recovery Corp' } ], 'resourceType':'Location', "
            +"'status':'active', 'name':'CovidRecoveryChony', 'type':{ 'coding':[ { 'code':'HUSCS', 'display':"
            +"'Collection Site' } ] }, 'telecom':[ { 'system':'phone', 'value':'1231231235', 'use':'work' } ], "
            +"'address':{ 'line':[ '123 east 165' ], 'city':'New York', 'state':'NY', 'postalCode':'10021' } "
            +"}, 'search':{ 'mode':'match' } } ] }");
    // Errors are returned 200 with no entries in the bundle response (not 400 or whatever).
    static final String LOCATION_JSON_ERROR = TestUtils.createJson("{'id':'40bd082a-5182-4a30-a6ff-50f3daa85435',"
            +"'resourceType':'Bundle','type':'searchset','total':0}");
    static final String LOCATION_JSON_NO_RESOURCE = TestUtils.createJson("{ 'id':'4b72216e-f638-4eb0-b901-e23ca2aa69de', "
            +"'meta':{ 'lastUpdated':'2020-08-20T12:41:26Z' }, 'resourceType':'Bundle', 'type':'searchset', "
            +"'total':1, 'entry':[ { } ] }");
    static final String GEOCODE_JSON = TestUtils.createJson("{ 'results' : [ { 'address_components' : [ "
            +"{ 'long_name' : '330', 'short_name' : '330', 'types' : [ 'subpremise' ] }, { 'long_name' : "
            +"'2901', 'short_name' : '2901', 'types' : [ 'street_number' ] }, { 'long_name' : '3rd Avenue',"
            +" 'short_name' : '3rd Ave', 'types' : [ 'route' ] }, { 'long_name' : 'Downtown Seattle', "
            +"'short_name' : 'Downtown Seattle', 'types' : [ 'neighborhood', 'political' ] }, { 'long_name' "
            +": 'Seattle', 'short_name' : 'Seattle', 'types' : [ 'locality', 'political' ] }, { 'long_name' "
            +": 'King County', 'short_name' : 'King County', 'types' : [ 'administrative_area_level_2', "
            +"'political' ] }, { 'long_name' : 'Washington', 'short_name' : 'WA', 'types' : [ "
            +"'administrative_area_level_1', 'political' ] }, { 'long_name' : 'United States', 'short_name' "
            +": 'US', 'types' : [ 'country', 'political' ] }, { 'long_name' : '98121', 'short_name' : "
            +"'98121', 'types' : [ 'postal_code' ] } ], 'formatted_address' : '2901 3rd Ave #330, Seattle, "
            +"WA 98121, USA', 'geometry' : { 'bounds' : { 'northeast' : { 'lat' : 47.6184148, 'lng' : "
            +"-122.3510372 }, 'southwest' : { 'lat' : 47.617759, 'lng' : -122.3525807 } }, 'location' : { "
            +"'lat' : 47.6180007, 'lng' : -122.3516149 }, 'location_type' : 'ROOFTOP', 'viewport' : { "
            +"'northeast' : { 'lat' : 47.6194358802915, 'lng' : -122.3504599697085 }, 'southwest' : { 'lat' "
            +": 47.6167379197085, 'lng' : -122.3531579302915 } } }, 'place_id' : 'EikyOTAxIDNyZCBBdmUgIzMzMC"
            +"wgU2VhdHRsZSwgV0EgOTgxMjEsIFVTQSIfGh0KFgoUChIJidPohE8VkFQRVpUgA1LwJYcSAzMzMA', 'types' : [ "
            +"'subpremise' ] } ], 'status' : 'OK' }");
    static final String EXPECTED_GEOCODING_URL = "https://maps.googleapis.com/maps/api/geocode/json?address=123+east+165+New+York+NY+10021&key=GEOKEY";
    static final String APPOINTMENT_JSON = TestUtils.createJson("{'resourceType':'Appointment','status':'booked','participant'"
            +":[{'actor':{'reference':'Location/foo','telecom':[{'system':'phone','value':"
            +"'1231231235','use':'work'}],'address':{'line':['123 east 165'],'city':'New York','state':"
            +"'NY','postalCode':'10021'}}},{'actor':{'identifier':{'system':'https://ws.sagebridge.org/#userId',"
            +"'value':'userId'}}}]}");
    static final String APPOINTMENT_JSON_FULLY_RESOLVED = TestUtils.createJson("{'resourceType':'Appointment',"
            +"'status':'booked','participant':[{'actor':{'reference':'Location/foo','telecom':[{'system':'phone',"
            +"'value':'1231231235','use':'work'}],'address':{'line':['123 east 165'],'city':'New York',"
            +"'state':'NY','postalCode':'10021'}}},{'actor':{'identifier':{'system':'https://ws.sagebridge.org/#userId',"
            +"'value':'userId'}}}]}");
    static final String APPOINTMENT_JSON_FULLY_RESOLVED_W_GEOCODING = TestUtils.createJson("{'resourceType':'Appointment',"
            +"'status':'booked','participant':[{'actor':{'reference':'Location/foo','telecom':[{'system':'phone',"
            +"'value':'1231231235','use':'work'}],'address':{'line':['123 east 165'],'city':'New York',"
            +"'state':'NY','postalCode':'10021'},'geocoding':{'bounds':{'northeast':{'lat':47.6184148,"
            +"'lng':-122.3510372},'southwest':{'lat':47.617759,'lng':-122.3525807}},'location':{'lat':"
            +"47.6180007,'lng':-122.3516149},'location_type':'ROOFTOP','viewport':{'northeast':{'lat':"
            +"47.6194358802915,'lng':-122.3504599697085},'southwest':{'lat':47.6167379197085,'lng':"
            +"-122.3531579302915}}}}},{'actor':{'identifier':{'system':'https://ws.sagebridge.org/#userId',"
            +"'value':'userId'}}}]}");
    static final String APPOINTMENT_JSON_FULLY_RESOLVED_CANCELLED = TestUtils.createJson("{'resourceType':'Appointment',"
            +"'status':'cancelled','participant':[{'actor':{'reference':'Location/foo','telecom':[{'system':'phone',"
            +"'value':'1231231235','use':'work'}],'address':{'line':['123 east 165'],'city':'New York',"
            +"'state':'NY','postalCode':'10021'}}},{'actor':{'identifier':{'system':'https://ws.sagebridge.org/#userId',"
            +"'value':'userId'}}}]}");
    static final String APPOINTMENT_JSON_FULLY_RESOLVED_CANCELLED_W_GEOCODING = TestUtils.createJson("{'resourceType':'Appointment',"
            +"'status':'cancelled','participant':[{'actor':{'reference':'Location/foo','telecom':[{'system':'phone',"
            +"'value':'1231231235','use':'work'}],'address':{'line':['123 east 165'],'city':'New York',"
            +"'state':'NY','postalCode':'10021'},'geocoding':{'bounds':{'northeast':{'lat':47.6184148,"
            +"'lng':-122.3510372},'southwest':{'lat':47.617759,'lng':-122.3525807}},'location':{'lat':"
            +"47.6180007,'lng':-122.3516149},'location_type':'ROOFTOP','viewport':{'northeast':{'lat':"
            +"47.6194358802915,'lng':-122.3504599697085},'southwest':{'lat':47.6167379197085,'lng':"
            +"-122.3531579302915}}}}},{'actor':{'identifier':{'system':'https://ws.sagebridge.org/#userId',"
            +"'value':'userId'}}}]}");
    static final String VALID_SERUM_OBSERVATION_JSON = TestUtils.createJson("{'id':'3101000022_1665167373_484670513','meta':"
            +"{'id':'Observation/3101000022_1665167373_484670513','versionId':'1','lastUpdated':'2020-08-10T09:46:29."
            +"601-04:00','tag':[{'system':'source','code':'sage'}]},'contained':[{'resourceType':'Specimen','type':{"
            +"'text':'Blood'},'collection':{'collectedDateTime':'2020-08-08T09:42:00-04:00'}},{'id':'3101000022',"
            +"'extension':[{'extension':[{'url':'ombCategory','valueCoding':{'code':'DECLINED','display':'Declined'}}],"
            +"'url':'us-core-race'},{'extension':[{'url':'ombCategory','valueCoding':{'code':'DEC','display':'Refused "
            +"/ Declined'}}],'url':'us-core-ethnicity'}],'meta':{'id':'Patient/3101000022','versionId':'1','lastUpdated'"
            +":'2020-08-05T12:43:07.310-04:00','tag':[{'system':'source','code':'sage'}]},'resourceType':'Patient',"
            +"'identifier':[{'system':'https://ws.sagebridge.org/#userId','value':'userId'}],'active':"
            +"true,'name':[{'family':'Alex','given':['Alex']}],'telecom':[{'system':'email','value':'email@gmail.com'},"
            +"{'system':'phone','value':'111 222 333'}],'gender':'other','birthDate':'1910-01-01T05:00:00.000Z','address'"
            +":[{'line':['617 W 169 street'],'city':'New York','state':'NY','postalCode':'10032'}],'contact':[{"
            +"'relationship':[{'coding':[{'code':'E','display':'doctor'}]}],'telecom':[{'system':'phone','value':'111 222 "
            +"333','use':'home'}],'address':{'line':['Brodaway '],'city':'New York','state':'NY','postalCode':'10032',"
            +"'country':'US'},'organization':{'display':'Columbia'}}],'managingOrganization':{'reference':'CovidRecovery',"
            +"'display':'Covid Recovery Corp'}}],'resourceType':'Observation','status':'final','code':{'coding':[{'code':"
            +"'484670513','display':'COVID-19 Sero Interp'}]},'subject':{'reference':'Patient/3101000022','identifier':"
            +"{'system':'https://ws.sagebridge.org/#userId','value':'userId'},'display':'Alex, Alex'},"
            +"'context':{'id':'Encounter/COVIDRECOVERY01'},'effectiveDateTime':'2020-08-10T09:45:00-04:00','issued':"
            +"'2020-08-10T09:46:27-04:00','performer':[{'extension':[{'valueString':'Spitalnik,Steven','valueCode':"
            +"'33D0664187','valueAddress':{'text':'630 West 168th Street New York NY 10032'}}],'display':'NYP_Columbia'}],"
            +"'valueString':'Indeterminate','valueRange':{'extension':[{'valueString':'Negative'}]},'interpretation':"
            +"{'coding':[{'code':'A','display':'Abnormal'}]},'comment':''}");
    static final String INVALID_SERUM_OBSERVATION_JSON = TestUtils.createJson("{'id':'3101000022_1665167373_484670513','meta':"
            +"{'id':'Observation/3101000022_1665167373_484670513','versionId':'1','lastUpdated':'2020-08-10T09:46:29."
            +"601-04:00','tag':[{'system':'source','code':'sage'}]},'contained':[{'resourceType':'Specimen','type':{"
            +"'text':'Blood'},'collection':{'collectedDateTime':'2020-08-08T09:42:00-04:00'}},{'id':'3101000022',"
            +"'extension':[{'extension':[{'url':'ombCategory','valueCoding':{'code':'DECLINED','display':'Declined'}}],"
            +"'url':'us-core-race'},{'extension':[{'url':'ombCategory','valueCoding':{'code':'DEC','display':'Refused "
            +"/ Declined'}}],'url':'us-core-ethnicity'}],'meta':{'id':'Patient/3101000022','versionId':'1','lastUpdated'"
            +":'2020-08-05T12:43:07.310-04:00','tag':[{'system':'source','code':'sage'}]},'resourceType':'Patient',"
            +"'identifier':[{'system':'https://ws.sagebridge.org/#userId','value':'userId'}],'active':"
            +"true,'name':[{'family':'Alex','given':['Alex']}],'telecom':[{'system':'email','value':'email@gmail.com'},"
            +"{'system':'phone','value':'111 222 333'}],'gender':'other','birthDate':'1910-01-01T05:00:00.000Z','address'"
            +":[{'line':['617 W 169 street'],'city':'New York','state':'NY','postalCode':'10032'}],'contact':[{"
            +"'relationship':[{'coding':[{'code':'E','display':'doctor'}]}],'telecom':[{'system':'phone','value':'111 222 "
            +"333','use':'home'}],'address':{'line':['Brodaway '],'city':'New York','state':'NY','postalCode':'10032',"
            +"'country':'US'},'organization':{'display':'Columbia'}}],'managingOrganization':{'reference':'CovidRecovery',"
            +"'display':'Covid Recovery Corp'}}],'resourceType':'Observation','status':'final','code':{'coding':[{'code':"
            +"'484670513','display':'COVID-19 Sero Interp'}]},'subject':{'reference':'Patient/3101000022','identifier':"
            +"{'system':'https://ws.sagebridge.org/#userId','value':'userId'},'display':'Alex, Alex'},"
            +"'context':{'id':'Encounter/COVIDRECOVERY01'},'effectiveDateTime':'2020-08-10T09:45:00-04:00','issued':"
            +"'2020-08-10T09:46:27-04:00','performer':[{'extension':[{'valueString':'Spitalnik,Steven','valueCode':"
            +"'33D0664187','valueAddress':{'text':'630 West 168th Street New York NY 10032'}}],'display':'NYP_Columbia'}],"
            +"'valueString':'Bad Value','valueRange':{'extension':[{'valueString':'Bad Value'}]},'interpretation':"
            +"{'coding':[{'code':'A','display':'Abnormal'}]},'comment':''}");
    static final String VALID_PCR_OBSERVATION_JSON = TestUtils.createJson("{'id':'3101000005_1665149453_467420433','meta':"
            +"{'id':'Observation/3101000005_1665149453_467420433','versionId':'1','lastUpdated':'2020-07-31T13:47:30.168-04:00',"
            +"'tag':[{'system':'source','code':'sage'}]},'contained':[{'resourceType':'Specimen','type':{'text':'NP Swab'},"
            +"'collection':{'collectedDateTime':'2020-07-28T19:39:00-04:00'}},{'id':'3101000005','resourceType':'Patient',"
            +"'identifier':[{'value':'3101000005'}],'name':[{'family':'Person','given':['Man']}],'gender':'male','birthDate':"
            +"'1979-12-09','address':[{'extension':[{'valueString':','}],'text':'Main St','city':'Corona Del Mar','state':'CA',"
            +"'postalCode':'92625'}]}],'resourceType':'Observation','status':'registered','code':{'coding':[{'code':"
            +"'467420433','display':'SARS-CoV-2 PCR'}]},'subject':{'reference':'Patient/3101000005','identifier':{'system':"
            +"'https://ws.sagebridge.org/#userId','value':'userId'}},'context':{'id':'Encounter/"
            +"COVIDRECOVERY01'},'effectiveDateTime':'2020-07-31T13:32:00-04:00','issued':'2020-07-31T13:40:07-04:00','performer'"
            +":[{'extension':[{'valueString':'Spitalnik,Steven','valueCode':'33D0664187','valueAddress':{'text':'630 West 168th "
            +"Street New York NY 10032'}}],'display':'NYP_Columbia'}],'valueString':'Indeterminate','valueRange':{'extension':["
            +"{'valueString':'Not Detected'}]},'interpretation':{'coding':[{'code':'A','display':'Abnormal'}]},'comment':''}");
    
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
    
    @Mock
    private Appender<ILoggingEvent> mockAppender;
    
    @Captor
    private ArgumentCaptor<LoggingEvent> loggingEventCaptor;
    
    @Mock
    GBFOrderService mockGBFOrderService;
    
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
        account.setId(TEST_USER_ID);
        account.setEnrollments(ImmutableSet.of(ENROLLMENT1, ENROLLMENT2));
        account.setDataGroups(ImmutableSet.of("group1", TEST_USER_GROUP));
        
        app = App.create();
        app.setIdentifier(APP_ID);
        when(mockAppService.getApp(APP_ID)).thenReturn(app);
        
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
        doReturn(TestConstants.TIMESTAMP).when(controller).getTimestamp();
        
        when(mockConfig.get("cuimc.test.url")).thenReturn("http://testServer/${patientId}");
        when(mockConfig.get("cuimc.test.location.url")).thenReturn("http://testServer/location/_search");
        when(mockConfig.get("cuimc.test.username")).thenReturn("testUsername");
        when(mockConfig.get("cuimc.test.password")).thenReturn("testPassword");
        when(mockConfig.get("cuimc.prod.url")).thenReturn("http://prodServer/${patientId}");
        when(mockConfig.get("cuimc.prod.location.url")).thenReturn("http://testServer/location/_search");
        when(mockConfig.get("cuimc.prod.username")).thenReturn("prodUsername");
        when(mockConfig.get("cuimc.prod.password")).thenReturn("prodPassword");
        when(mockConfig.get("crc.geocode.api.key")).thenReturn("GEOKEY");
        
        // Mock logging to ensure it's called
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.addAppender(mockAppender);
        root.setLevel(Level.DEBUG);
        
        RequestContext.set(new RequestContext.Builder().withOrgSponsoredStudies(USER_STUDY_IDS).build());
    }
    
    @AfterMethod
    public void afterMethod() {
        RequestContext.set(RequestContext.NULL_INSTANCE);
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
    public void updateParticipantSetsSelected() throws Exception {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        
        when(mockAccountService.getAccount(ACCOUNT_ID_FOR_HC)).thenReturn(account);
        
        HttpResponse mockResponse = mock(HttpResponse.class);
        StatusLine mockStatusLine = mock(StatusLine.class);
        when(mockResponse.getStatusLine()).thenReturn(mockStatusLine);
        when(mockStatusLine.getStatusCode()).thenReturn(200);
        
        // doReturn(mockResponse).when(controller).put(any(), any(), any());
        
        StatusMessage message = controller.updateParticipant("healthcode:"+HEALTH_CODE);
        assertEquals(message.getMessage(), CRCController.UPDATE_MSG);

        verify(mockAccountService).updateAccount(account);
        // verify(controller, never()).createLabOrder(account);

        assertEquals(account.getDataGroups(), makeSetOf(CRCController.AccountStates.SELECTED, "group1"));
        assertFalse(RequestContext.get().getOrgSponsoredStudies().isEmpty());
    }
    
    
    @Test(expectedExceptions = EntityNotFoundException.class,
            expectedExceptionsMessageRegExp = "Account not found.")
    public void updateParticipantAccountNotFound() {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        
        controller.updateParticipant("healthcode:"+HEALTH_CODE);
    }

    @Test
    public void externalIdAccountSubmitsCorrectCredentials() throws Exception {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.getAccount(ACCOUNT_ID_FOR_HC)).thenReturn(account);
        when(mockAppService.getApp(APP_ID)).thenReturn(mockApp);
        when(mockApp.getIdentifier()).thenReturn(APP_ID);
        when(mockAccountService.authenticate(eq(mockApp), any())).thenReturn(account);
//        mockExternalService(200, "OK");
        
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
//        mockExternalService(200, "OK");
        
        controller.updateParticipant("healthcode:"+HEALTH_CODE);
        
        verify(mockAccountService).authenticate(eq(mockApp), signInCaptor.capture());
        assertEquals(signInCaptor.getValue().getEmail(), SYN_USERNAME);
    }
    
//    @Test
//    public void createLabOrderOK() throws Exception {
//        mockExternalService(200, "OK");
//        // no errors
//        controller.createLabOrder(account);
//        
//        // Currently in production we are also using the test values
//        verify(controller).put(eq("http://testServer/userId"), any(), any());
//    }
//    
//    @Test
//    public void createLabOrderOKInTest() throws Exception {
//        account.setDataGroups(ImmutableSet.of(TEST_USER_GROUP));
//        mockExternalService(200, "OK");
//        // no errors
//        controller.createLabOrder(account);
//        
//        verify(controller).put(eq("http://testServer/userId"), any(), any());
//    }
//
//    @Test
//    public void createLabOrderCreated() throws Exception { 
//        mockExternalService(201, "Created");
//        // no errors
//        controller.createLabOrder(account);
//    }
//    
//    @Test(expectedExceptions = BridgeServiceException.class, 
//            expectedExceptionsMessageRegExp = "Internal Service Error")
//    public void createLabOrderBadRequest() throws Exception { 
//        mockExternalService(400, "Bad Request");
//        controller.createLabOrder(account);
//    }
//    
//    @Test(expectedExceptions = BridgeServiceException.class)
//    public void createLabOrderInternalServerError() throws Exception { 
//        mockExternalService(500, "Internal Server Error");
//        controller.createLabOrder(account);
//    }
//    
//    @Test(expectedExceptions = BridgeServiceException.class)
//    public void createLabOrderServiceUnavailable() throws Exception { 
//        mockExternalService(503, "Service Unavailable");
//        controller.createLabOrder(account);
//    }
//    
//    @Test(expectedExceptions = BridgeServiceException.class)
//    public void createLabOrderIOException() throws Exception {
//        doThrow(new IOException()).when(controller).put(any(), any(), any());
//        controller.createLabOrder(account);
//    }

//    private void mockExternalService(int statusCode, String statusReason) throws Exception {
//        StatusLine statusLine = new BasicStatusLine(new ProtocolVersion("HTTP", 1, 2), statusCode, statusReason);
//        HttpResponse response = new BasicHttpResponse(statusLine);
//        doReturn(response).when(controller).put(any(), any(), any());
//    }

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
        
        mockGetLocation(LOCATION_JSON);
//        mockGetGeocoding();
        
        DateRangeResourceList<? extends ReportData> results = new DateRangeResourceList<>(ImmutableList.of());
        doReturn(results).when(mockReportService).getParticipantReport(
                APP_ID, APPOINTMENT_REPORT, HEALTH_CODE, JAN1, JAN2);
        
        Appointment appointment = new Appointment();
        appointment.setStatus(BOOKED);
        // add a wrong participant to verify we go through them all and look for ours
        addAppointmentParticipantComponent(appointment, "Location/foo");
        addAppointmentSageId(appointment, TEST_USER_ID);
        
        String json = FHIR_CONTEXT.newJsonParser().encodeResourceToString(appointment);
        mockRequestBody(mockRequest, json);
        
        ResponseEntity<StatusMessage> retValue = controller.postAppointment();
        assertEquals(retValue.getBody().getMessage(), "Appointment created (status = booked).");
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
        
        verify(mockAccountService).updateAccount(accountCaptor.capture());
        Account capturedAcct = accountCaptor.getValue();
        assertEquals(capturedAcct.getDataGroups(), makeSetOf(CRCController.AccountStates.TESTS_SCHEDULED, "group1"));
        assertEquals(capturedAcct.getAttributes().get(TIMESTAMP_FIELD), TIMESTAMP.toString());
        
        verify(mockHealthDataService).submitHealthData(eq(APP_ID), participantCaptor.capture(), dataCaptor.capture());
        HealthDataSubmission healthData = dataCaptor.getValue();
        assertEquals(healthData.getAppVersion(), "v1");
        assertEquals(healthData.getCreatedOn(), TIMESTAMP);
        assertEquals(healthData.getMetadata().toString(), "{\"type\":\""+APPOINTMENT_REPORT+"\"}");
        assertEquals(healthData.getData().toString(), APPOINTMENT_JSON_FULLY_RESOLVED);
    }
    
    @Test
    public void postAppointmentUpdated() throws Exception {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        mockGetLocation(LOCATION_JSON);
//        mockGetGeocoding();
        
        DateRangeResourceList<? extends ReportData> results = new DateRangeResourceList<>(ImmutableList.of(ReportData.create()));
        doReturn(results).when(mockReportService).getParticipantReport(
                APP_ID, APPOINTMENT_REPORT, HEALTH_CODE, JAN1, JAN2);
        
        Appointment appointment = new Appointment();
        appointment.setStatus(BOOKED);
        // add a wrong participant to verify we go through them all and look for ours
        addAppointmentParticipantComponent(appointment, LOCATION_NS + "foo");
        addAppointmentSageId(appointment, TEST_USER_ID);
        
        String json = FHIR_CONTEXT.newJsonParser().encodeResourceToString(appointment);
        mockRequestBody(mockRequest, json);
        
        ResponseEntity<StatusMessage> retValue = controller.postAppointment();
        assertEquals(retValue.getBody().getMessage(), "Appointment updated (status = booked).");
        assertEquals(retValue.getStatusCodeValue(), 200);
        
        assertTrue(account.getDataGroups().contains("tests_scheduled"));
        
        verify(controller).addLocation(any(), eq(account), eq("foo"));
        verify(controller).post("http://testServer/location/_search", account, "id=\"foo\"");
        verify(mockHealthDataService).submitHealthData(eq(APP_ID), participantCaptor.capture(), dataCaptor.capture());
        
        HealthDataSubmission healthData = dataCaptor.getValue();
        assertEquals(healthData.getAppVersion(), "v1");
        assertEquals(healthData.getCreatedOn(), TIMESTAMP);
        assertEquals(healthData.getMetadata().toString(), "{\"type\":\""+APPOINTMENT_REPORT+"\"}");
        assertEquals(healthData.getData().toString(), APPOINTMENT_JSON_FULLY_RESOLVED);
    }
    
    @Test
    public void postAppointmentCancelled() throws Exception {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        mockGetLocation(LOCATION_JSON);
//        mockGetGeocoding();
        
        DateRangeResourceList<? extends ReportData> results = new DateRangeResourceList<>(ImmutableList.of(ReportData.create()));
        doReturn(results).when(mockReportService).getParticipantReport(
                APP_ID, APPOINTMENT_REPORT, HEALTH_CODE, JAN1, JAN2);
        
        Appointment appointment = new Appointment();
        appointment.setStatus(CANCELLED);
        addAppointmentParticipantComponent(appointment, LOCATION_NS + "foo");
        addAppointmentSageId(appointment, TEST_USER_ID);
        
        String json = FHIR_CONTEXT.newJsonParser().encodeResourceToString(appointment);
        mockRequestBody(mockRequest, json);
        
        ResponseEntity<StatusMessage> retValue = controller.postAppointment();
        assertEquals(retValue.getBody().getMessage(), "Appointment updated (status = cancelled).");
        assertEquals(retValue.getStatusCodeValue(), 200);
        
        assertTrue(account.getDataGroups().contains("tests_cancelled"));
        
        verify(mockHealthDataService).submitHealthData(eq(APP_ID), participantCaptor.capture(), dataCaptor.capture());
        
        HealthDataSubmission healthData = dataCaptor.getValue();
        assertEquals(healthData.getAppVersion(), "v1");
        assertEquals(healthData.getCreatedOn(), TIMESTAMP);
        assertEquals(healthData.getMetadata().toString(), "{\"type\":\""+APPOINTMENT_REPORT+"\"}");
        assertEquals(healthData.getData().toString(), APPOINTMENT_JSON_FULLY_RESOLVED_CANCELLED);
    }
    
    @Test
    public void postAppointmentMistakeRollsBackAccount() throws Exception {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        Appointment appointment = new Appointment();
        appointment.setStatus(ENTEREDINERROR);
        addAppointmentParticipantComponent(appointment, LOCATION_NS + "foo");
        addAppointmentSageId(appointment, TEST_USER_ID);
        
        String json = FHIR_CONTEXT.newJsonParser().encodeResourceToString(appointment);
        mockRequestBody(mockRequest, json);
        
        ResponseEntity<StatusMessage> retValue = controller.postAppointment();
        assertEquals(retValue.getBody().getMessage(), "Appointment deleted.");
        assertEquals(retValue.getStatusCodeValue(), 200);
        
        assertTrue(account.getDataGroups().contains("selected"));
        
        verify(mockAccountService).updateAccount(account);
        verify(mockReportService).deleteParticipantReportRecord(APP_ID, APPOINTMENT_REPORT,
                JAN1.toString(), HEALTH_CODE);
    }
    
    @Test
    public void postAppointmentFailsWhenLocationCallFails() throws Exception {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
//        mockGetGeocoding();
        
        DateRangeResourceList<? extends ReportData> results = new DateRangeResourceList<>(ImmutableList.of());
        doReturn(results).when(mockReportService).getParticipantReport(
                APP_ID, APPOINTMENT_REPORT, HEALTH_CODE, JAN1, JAN2);
        
        Appointment appointment = new Appointment();
        appointment.setStatus(BOOKED);
        // add a wrong participant to verify we go through them all and look for ours
        addAppointmentParticipantComponent(appointment, "Location/foo");
        addAppointmentSageId(appointment, TEST_USER_ID);
        
        String json = FHIR_CONTEXT.newJsonParser().encodeResourceToString(appointment);
        mockRequestBody(mockRequest, json);
        
        HttpResponse mockResponse = mock(HttpResponse.class);
        doReturn(mockResponse).when(controller).post(any(), any(), any());
        
        StatusLine mockStatusLine = mock(StatusLine.class);
        when(mockResponse.getStatusLine()).thenReturn(mockStatusLine);
        when(mockStatusLine.getStatusCode()).thenReturn(400);

        HttpEntity mockEntity = mock(HttpEntity.class);
        when(mockResponse.getEntity()).thenReturn(mockEntity);
        when(mockEntity.getContent()).thenReturn(IOUtils.toInputStream("This was an error."));
        
        ResponseEntity<StatusMessage> retValue = controller.postAppointment();
        assertEquals(retValue.getBody().getMessage(), "Appointment created (status = booked).");
        assertEquals(retValue.getStatusCodeValue(), 201);
        
        verify(mockAppender).doAppend(loggingEventCaptor.capture());
        final LoggingEvent loggingEvent = loggingEventCaptor.getValue();
        
        assertEquals(loggingEvent.getLevel(), Level.WARN);
        assertEquals(loggingEvent.getFormattedMessage(),
                "Error retrieving location, id = foo, status = 400, response body = This was an error.");
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
        appointment.setStatus(BOOKED);
        addAppointmentSageId(appointment, TEST_USER_ID);
        
        String json = FHIR_CONTEXT.newJsonParser().encodeResourceToString(appointment);
        mockRequestBody(mockRequest, json);
        
        controller.postAppointment();
        verify(controller, never()).post(any(), any(), any());
        verify(mockHealthDataService).submitHealthData(any(), any(), any());
    }
    
    @Test
    public void postProcedureCreated() throws Exception {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        String json = makeProcedureRequest();
        mockRequestBody(mockRequest, json);
        
        InOrder inorder = inOrder(mockAccountService, mockHealthDataService, mockReportService);
        
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
        
        inorder.verify(mockAccountService).updateAccount(accountCaptor.capture());
        Account capturedAcct = accountCaptor.getValue();
        assertEquals(capturedAcct.getDataGroups(), makeSetOf(CRCController.AccountStates.TESTS_COLLECTED, "group1"));
        assertEquals(capturedAcct.getAttributes().get(TIMESTAMP_FIELD), TIMESTAMP.toString());

        inorder.verify(mockHealthDataService).submitHealthData(eq(APP_ID), participantCaptor.capture(), dataCaptor.capture());
        HealthDataSubmission healthData = dataCaptor.getValue();
        assertEquals(healthData.getAppVersion(), "v1");
        assertEquals(healthData.getCreatedOn(), TIMESTAMP);
        assertEquals(healthData.getMetadata().toString(), "{\"type\":\""+PROCEDURE_REPORT+"\"}");
        assertEquals(healthData.getData().toString(), json);

        inorder.verify(mockReportService).saveParticipantReport(eq(APP_ID), eq(PROCEDURE_REPORT), eq(HEALTH_CODE),
                reportCaptor.capture());
        ReportData capturedReport = reportCaptor.getValue();
        assertEquals(capturedReport.getDate(), "1970-01-01");
        verifySubject(capturedReport.getData());
        assertEquals(capturedReport.getStudyIds(), USER_STUDY_IDS);
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
        String json = makeObservation(VALID_SERUM_OBSERVATION_JSON);
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
        
        verify(mockAccountService).updateAccount(accountCaptor.capture());
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
    
    @Test
    public void postObservationFailsOnUnknownTestCode() throws Exception {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        String json = makeObservation(VALID_PCR_OBSERVATION_JSON);
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
        
        verify(mockAccountService).updateAccount(accountCaptor.capture());
        Account capturedAcct = accountCaptor.getValue();
        assertEquals(capturedAcct.getDataGroups(), makeSetOf(CRCController.AccountStates.TESTS_AVAILABLE_TYPE_UNKNOWN, "group1"));
        assertEquals(capturedAcct.getAttributes().get(TIMESTAMP_FIELD), TIMESTAMP.toString());
        
        verify(mockHealthDataService).submitHealthData(eq(APP_ID), participantCaptor.capture(), dataCaptor.capture());
        HealthDataSubmission healthData = dataCaptor.getValue();
        assertEquals(healthData.getAppVersion(), "v1");
        assertEquals(healthData.getCreatedOn(), TIMESTAMP);
        assertEquals(healthData.getMetadata().toString(), "{\"type\":\""+OBSERVATION_REPORT+"\"}");
        assertEquals(healthData.getData().toString(), json);
    }
    
    @Test
    public void postObservationFailsOnInvalidSerumTestValue() throws Exception {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        String json = makeObservation(INVALID_SERUM_OBSERVATION_JSON);
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
        
        verify(mockAccountService).updateAccount(accountCaptor.capture());
        Account capturedAcct = accountCaptor.getValue();
        assertEquals(capturedAcct.getDataGroups(), makeSetOf(CRCController.AccountStates.TESTS_AVAILABLE_TYPE_UNKNOWN, "group1"));
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
        assertEquals(value, TEST_USER_ID);
    }
    
    @Test
    public void postObservationUpdated() throws Exception {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        String json = makeObservation(VALID_SERUM_OBSERVATION_JSON);
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
    
    void mockGetLocation(String payload) throws Exception {
        HttpResponse mockResponse = mock(HttpResponse.class);
        doReturn(mockResponse).when(controller).post(any(), any(), any());
        
        StatusLine mockStatusLine = mock(StatusLine.class);
        when(mockResponse.getStatusLine()).thenReturn(mockStatusLine);
        // Search failures do come back as 200s 
        when(mockStatusLine.getStatusCode()).thenReturn(200);

        HttpEntity mockEntity = mock(HttpEntity.class);
        when(mockResponse.getEntity()).thenReturn(mockEntity);
        when(mockEntity.getContent()).thenReturn(IOUtils.toInputStream(payload));
    }
    
//    void mockGetGeocoding() throws Exception {
//        HttpResponse mockResponse = mock(HttpResponse.class);
//        doReturn(mockResponse).when(controller).get(EXPECTED_GEOCODING_URL);
//        
//        StatusLine mockStatusLine = mock(StatusLine.class);
//        when(mockResponse.getStatusLine()).thenReturn(mockStatusLine);
//        when(mockStatusLine.getStatusCode()).thenReturn(200);
//
//        HttpEntity mockEntity = mock(HttpEntity.class);
//        when(mockResponse.getEntity()).thenReturn(mockEntity);
//        when(mockEntity.getContent()).thenReturn(IOUtils.toInputStream(GEOCODE_JSON));
//    }
    
    @Test
    public void authenticationPopulatesRequestContext() {
        account.setOrgMembership(TEST_ORG_ID);

        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAppService.getApp(CRCController.APP_ID)).thenReturn(mockApp);
        when(mockAccountService.authenticate(eq(mockApp), signInCaptor.capture())).thenReturn(account);
        
        controller.httpBasicAuthentication();
        
        RequestContext context = RequestContext.get();
        assertEquals(context.getCallerAppId(), APP_ID);
        assertEquals(context.getCallerOrgMembership(), TEST_ORG_ID);
        assertEquals(context.getOrgSponsoredStudies(), USER_STUDY_IDS);
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
                .put("zip_code", "10001")
                .put("home_phone", PHONE.getNumber()).build());
        Patient patient = controller.createPatient(account);
        
        assertTrue(patient.getActive());
        assertEquals(patient.getIdentifier().get(0).getValue(), TEST_USER_ID);
        assertEquals(patient.getIdentifier().get(0).getSystem(), USER_ID_VALUE_NS);
        assertEquals(patient.getName().get(0).getGivenAsSingleString(), "Test");
        assertEquals(patient.getName().get(0).getFamily(), "User");
        assertEquals(patient.getMeta().getTag().get(0).getSystem(), "source");
        assertEquals(patient.getMeta().getTag().get(0).getCode(), "sage");
        assertEquals(patient.getGender().name(), "FEMALE");
        assertEquals(LocalDate.fromDateFields(patient.getBirthDate()).toString(), "1980-08-10");
        assertEquals(patient.getTelecom().get(0).getValue(), PHONE.getNumber());
        assertEquals(patient.getTelecom().get(0).getSystem().name(), "PHONE");
        assertEquals(patient.getTelecom().get(1).getValue(), PHONE.getNumber());
        assertEquals(patient.getTelecom().get(1).getSystem().name(), "SMS");
        assertEquals(patient.getTelecom().get(2).getValue(), EMAIL);
        assertEquals(patient.getTelecom().get(2).getSystem().name(), "EMAIL");
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
        mockRequestBody(mockRequest, makeAppointment(null, BOOKED));
        
        mockGetLocation(LOCATION_JSON);
        
        controller.postAppointment();
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class,
            expectedExceptionsMessageRegExp = "Account not found.")
    public void appointmentWrongIdentifier() throws Exception {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        mockRequestBody(mockRequest, makeAppointment("not-the-right-id", BOOKED));
        
        mockGetLocation(LOCATION_JSON);
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
        identifier.setValue(TEST_USER_ID);
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
        identifier.setValue(TEST_USER_ID);
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
        mockRequestBody(mockRequest, makeAppointment(TEST_USER_ID, BOOKED));
        mockGetLocation(LOCATION_JSON);
        
        controller.postAppointment();
    }

    @Test(expectedExceptions = NotAuthenticatedException.class)
    public void callerUserNameIncorrect() throws Exception {
        String auth = new String(Base64.getEncoder().encode(("foo:dummy-password").getBytes()));
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn("Basic " + auth);
        mockRequestBody(mockRequest, makeAppointment(TEST_USER_ID, BOOKED));
        
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
    
    @Test
    public void addLocationIOException() throws Exception {
        JsonNode node = BridgeObjectMapper.get().readTree("{}");
        Account account = Account.create();
        String locationId = BridgeUtils.encodeURIComponent("badLocation");
        
        doThrow(new IOException("Something wrong")).when(controller).post(any(), any(), any());
        
        controller.addLocation(node, account, locationId);
        
        verify(mockAppender).doAppend(loggingEventCaptor.capture());
        final LoggingEvent loggingEvent = loggingEventCaptor .getValue();
        
        assertEquals(loggingEvent.getLevel(), Level.WARN);
        assertEquals(loggingEvent.getFormattedMessage(), "Error retrieving location, id = " + locationId);
    }
    
    @Test
    public void addLocationNoLocationReturned() throws Exception {
        JsonNode node = BridgeObjectMapper.get().readTree("{}");
        Account account = Account.create();
        String locationId = "badLocation";
        
        mockGetLocation(LOCATION_JSON_ERROR);
        
        controller.addLocation(node, account, locationId);
        
        verify(mockAppender).doAppend(loggingEventCaptor.capture());
        final LoggingEvent loggingEvent = loggingEventCaptor .getValue();
        
        assertEquals(loggingEvent.getLevel(), Level.WARN);
        assertEquals(loggingEvent.getFormattedMessage(), "Error retrieving location, id = " + locationId
                + ", status = 200, response body = " + LOCATION_JSON_ERROR);
    }
    
    @Test
    public void addLocationNoResource() throws Exception {
        JsonNode node = BridgeObjectMapper.get().readTree("{}");
        Account account = Account.create();
        String locationId = "badLocation";
        
        mockGetLocation(LOCATION_JSON_NO_RESOURCE);
        
        controller.addLocation(node, account, locationId);
        
        verify(mockAppender).doAppend(loggingEventCaptor.capture());
        final LoggingEvent loggingEvent = loggingEventCaptor .getValue();
        
        assertEquals(loggingEvent.getLevel(), Level.WARN);
        assertEquals(loggingEvent.getFormattedMessage(), "Error retrieving location, id = " + locationId
                + ", status = 200, response body = " + LOCATION_JSON_NO_RESOURCE);
    }

//    @Test
//    public void addGeocodingInformationIOException() throws Exception {
//        JsonNode appointment = BridgeObjectMapper.get().readTree(APPOINTMENT_JSON);
//        ObjectNode actor = (ObjectNode)appointment.get("participant").get(0).get("actor");
//        doThrow(new IOException("Bad")).when(controller).get(any());
//        
//        controller.addGeocodingInformation(actor);
//        // JSON remains unchanged because request for geocoding failed.
//        assertEquals(appointment.toString(), APPOINTMENT_JSON);
//    }
//    
//    @Test
//    public void addGeocodingInformationHttpErrorResponse() throws Exception {
//        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
//        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
//        when(mockAccountService.getAccount(any())).thenReturn(null);
//        mockRequestBody(mockRequest, makeAppointment(USER_ID, BOOKED));
//        mockGetLocation();
//
//        // However, the request for geocoding information goes awry
//        HttpResponse mockResponse = mock(HttpResponse.class);
//        doReturn(mockResponse).when(controller).get(any());
//        
//        StatusLine mockStatusLine = mock(StatusLine.class);
//        when(mockResponse.getStatusLine()).thenReturn(mockStatusLine);
//        when(mockStatusLine.getStatusCode()).thenReturn(400);
//
//        HttpEntity mockEntity = mock(HttpEntity.class);
//        when(mockResponse.getEntity()).thenReturn(mockEntity);
//        when(mockEntity.getContent()).thenReturn(IOUtils.toInputStream(""));
//
//        String json = createJson("{'participant':[{'actor':{'reference':'Location/foo'}}]}");
//        
//        JsonNode node = BridgeObjectMapper.get().readTree(json);
//        String location = BridgeUtils.encodeURIComponent("123 east 165 New York NY 10021");
//      
//        controller.addLocation(node, account, location);
//        // JSON remains unchanged because request for geocoding failed.
//        assertEquals(node.toString(), createJson("{'participant':[{'actor':{'reference':'Location/foo',"+
//                "'telecom':[{'system':'phone','value':'1231231235','use':'work'}],'address':{'line':"
//                +"['123 east 165'],'city':'New York','state':'NY','postalCode':'10021'}}}]}"));
//    }
//
//    @Test
//    public void addGeocodingInformationBadPayload() throws Exception {
//        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
//        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
//        when(mockAccountService.getAccount(any())).thenReturn(null);
//        mockRequestBody(mockRequest, makeAppointment(USER_ID, BOOKED));
//        mockGetLocation();
//
//        // However, the request for geocoding information goes awry
//        HttpResponse mockResponse = mock(HttpResponse.class);
//        doReturn(mockResponse).when(controller).get(any());
//        
//        StatusLine mockStatusLine = mock(StatusLine.class);
//        when(mockResponse.getStatusLine()).thenReturn(mockStatusLine);
//        when(mockStatusLine.getStatusCode()).thenReturn(200);
//
//        HttpEntity mockEntity = mock(HttpEntity.class);
//        when(mockResponse.getEntity()).thenReturn(mockEntity);
//        when(mockEntity.getContent()).thenReturn(IOUtils.toInputStream("{\"foo\":\"bar\"}"));
//
//        String json = createJson("{'participant':[{'actor':{'reference':'Location/foo'}}]}");
//        
//        JsonNode node = BridgeObjectMapper.get().readTree(json);
//        String location = BridgeUtils.encodeURIComponent("123 east 165 New York NY 10021");
//      
//        controller.addLocation(node, account, location);
//        // Again, no error beyond logging, and JSON is unchanged.
//        assertEquals(node.toString(), createJson("{'participant':[{'actor':{'reference':'Location/foo',"+
//                "'telecom':[{'system':'phone','value':'1231231235','use':'work'}],'address':{'line':"
//                +"['123 east 165'],'city':'New York','state':'NY','postalCode':'10021'}}}]}"));
//    }
//
    @Test
    public void combineLocationJsonWorks() throws Exception {
        JsonNode appointment = BridgeObjectMapper.get().readTree(createJson(APPOINTMENT_JSON));
        JsonNode actor = appointment.get("participant").get(0).get("actor");
        
        String result = controller.combineLocationJson(actor);
        assertEquals(result, "123+east+165+New+York+NY+10021");
    }

    @Test
    public void combineLocationJsonIgnoresNull() throws Exception {
        JsonNode appointment = BridgeObjectMapper.get().readTree(createJson(APPOINTMENT_JSON));
        ObjectNode actor = (ObjectNode)appointment.get("participant").get(0).get("actor");
        actor.remove("address");
        
        String result = controller.combineLocationJson(actor);
        assertNull(result);
    }
    
    @Test
    public void combineLocationJsonIgnoresMissingFields() throws Exception {
        JsonNode appointment = BridgeObjectMapper.get().readTree(createJson(APPOINTMENT_JSON));
        JsonNode actor = appointment.get("participant").get(0).get("actor");
        ((ObjectNode)actor.get("address")).remove("city");
        
        String result = controller.combineLocationJson(actor);
        assertEquals(result, "123+east+165+NY+10021");
    }
    
    @Test
    public void placeOrderAsParticipant() {
        setupParticipantAuthentication();
        setupShippingAddress();
        
        doNothing().when(mockGBFOrderService).placeOrder(any(), eq(true));
        
        ArgumentCaptor<AccountId> accountIdCaptor = ArgumentCaptor.forClass(AccountId.class);
        when(mockAccountService.getAccount(accountIdCaptor.capture())).thenReturn(account);
        
        DateRangeResourceList<? extends ReportData> results = new DateRangeResourceList<>(ImmutableList.of());
        doReturn(results).when(mockReportService).getParticipantReport(
                APP_ID, SHIPMENT_REPORT, HEALTH_CODE, JAN1, JAN2);
        
        controller.postUserLabShipmentRequest();
    
        verify(mockReportService).saveParticipantReport(eq(APP_ID), eq(SHIPMENT_REPORT), eq(HEALTH_CODE),
                reportCaptor.capture());
        verify(controller).internalLabShipmentRequest(any(), any());
        
        ReportData capturedReport = reportCaptor.getValue();
        String orderId = capturedReport.getData().get(SHIPMENT_REPORT_KEY_ORDER_ID).asText();
        assertTrue(orderId.startsWith(ACCOUNT_ID.getId()));
    }
    
    @Test
    public void placeOrderForHealthCode() {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        
        setupShippingAddress();
        
        ArgumentCaptor<AccountId> accountIdCaptor = ArgumentCaptor.forClass(AccountId.class);
        when(mockAccountService.getAccount(accountIdCaptor.capture())).thenReturn(account);
        
        DateRangeResourceList<? extends ReportData> results = new DateRangeResourceList<>(ImmutableList.of());
        doReturn(results).when(mockReportService).getParticipantReport(
                APP_ID, SHIPMENT_REPORT, HEALTH_CODE, JAN1, JAN2);
    
    
        controller.postLabShipmentRequest("healthcode:" + HEALTH_CODE);
    
        verify(mockAccountService).authenticate(any(), any());
    
        verify(mockAccountService, atLeastOnce()).getAccount(accountIdCaptor.capture());
        assertTrue(accountIdCaptor.getAllValues().stream()
                .anyMatch(accountId -> accountId.getHealthCode().equals(HEALTH_CODE)));
    
        verify(mockReportService).saveParticipantReport(eq(APP_ID), eq(SHIPMENT_REPORT), eq(HEALTH_CODE),
                reportCaptor.capture());
        verify(controller).internalLabShipmentRequest(any(), any());
    
        ReportData capturedReport = reportCaptor.getValue();
        String orderId = capturedReport.getData().get(SHIPMENT_REPORT_KEY_ORDER_ID).asText();
        assertTrue(orderId.startsWith(ACCOUNT_ID.getId()));
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void internalLabShipmentRequestAccountNotFound() {
        controller.internalLabShipmentRequest(mockApp, null);
    }
    
    @Test
    public void internalLabShipmentSetsShipTestRequestedState() {
        setupParticipantAuthentication();
        setupShippingAddress();
    
        DateRangeResourceList<? extends ReportData> results = new DateRangeResourceList<>(ImmutableList.of());
        doReturn(results).when(mockReportService).getParticipantReport(
                APP_ID, SHIPMENT_REPORT, HEALTH_CODE, JAN1, JAN2);
    
        controller.postUserLabShipmentRequest();
        
        assertTrue(account.getDataGroups().contains(SHIP_TESTS_REQUESTED.name().toLowerCase()));
    }
    
    @Test(expectedExceptions = LimitExceededException.class)
    public void internalLabShipmentRequestLimitedToOne() {
        setupParticipantAuthentication();
        setupShippingAddress();
    
        account.setDataGroups(ImmutableSet.of(
                TEST_USER_GROUP,
                SHIP_TESTS_REQUESTED.name().toLowerCase()
        ));
    
        controller.internalLabShipmentRequest(mockApp, account);
    }
    
    @Test
    public void internalLabShipmentValidatesAddress() {
        setupParticipantAuthentication();
        setupShippingAddress();
        
        DateRangeResourceList<? extends ReportData> results = new DateRangeResourceList<>(ImmutableList.of());
        doReturn(results).when(mockReportService).getParticipantReport(
                APP_ID, SHIPMENT_REPORT, HEALTH_CODE, JAN1, JAN2);
        
        controller.postUserLabShipmentRequest();
        
        verify(controller).validateAndGetAddress(any());
    }
    
    // somewhat redundant test
    @Test(expectedExceptions = BadRequestException.class)
    public void placeOrderMissingCity() {
        setupParticipantAuthentication();
        setupShippingAddress();
        account.getAttributes().remove("city");
        
        controller.postUserLabShipmentRequest();
    }
    
    @Test
    public void validateAndGetAddressChecksRequiredFields() {
        ImmutableList.of("address1", "city", "state", "zip_code", "home_phone")
                .forEach(requiredField -> {
                    setupShippingAddress();
                    account.getAttributes().remove(requiredField);
                    
                    try {
                        controller.validateAndGetAddress(account);
                        fail("Expected BadRequestException when account does not contain " + requiredField);
                    } catch (BadRequestException e) {
                        assertTrue(true, "Got BadRequestException due to missing field " + requiredField);
                    }
                });
        
        Stream.of("", null).forEach(firstName -> {
            setupShippingAddress();
            account.setFirstName(firstName);
            try {
                controller.validateAndGetAddress(account);
                fail("Expected BadRequestException when account does not contain First Name");
            } catch (BadRequestException e) {
                assertTrue(true, "Got BadRequestException due to missing First Name");
            }
        });
        
        Stream.of("", null).forEach(lastName -> {
            setupShippingAddress();
            account.setLastName(lastName);
            try {
                controller.validateAndGetAddress(account);
                fail("Expected BadRequestException when account does not contain Last Name");
            } catch (BadRequestException e) {
                assertTrue(true, "Got BadRequestException due to missing Last Name");
            }
        });
    }
    
    private void setupParticipantAuthentication() {
        UserSession mockSession = mock(UserSession.class);
        doReturn(mockSession).when(controller).getSessionIfItExists();
        doReturn(mockSession).when(controller).getAuthenticatedSession();
        when(mockParticipantService.getSelfParticipant(eq(app), any(), eq(false)))
                .thenReturn(new StudyParticipant.Builder().withId(TEST_USER_ID).withHealthCode(HEALTH_CODE).build());
        RequestContext.set(new RequestContext.Builder().withCallerIpAddress(IP_ADDRESS)
                .withCallerEnrolledStudies(ImmutableSet.of("studyA", "studyB")).withCallerAppId(APP_ID).build());
        
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        when(mockSession.getAppId()).thenReturn(APP_ID);
        when(mockSession.getId()).thenReturn(TEST_USER_ID);
        
        when(mockAppService.getApp(eq(APP_ID))).thenReturn(app);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
    }
    
    private void setupShippingAddress() {
        account.setFirstName("Test");
        account.setLastName("User");
        account.getAttributes().putAll(
                ImmutableMap.<String, String>builder()
                        .put("address1", "123 Abc St")
                        .put("address2", "Unit 456")
                        .put("city", "Seattle")
                        .put("state", "Washington")
                        .put("zip_code", "98119")
                        .put("home_phone", "206.547.2600")
                        .build()
        );
    }
    
    @Test
    public void checkOrderStatus() {
    
    }
    
    @Test
    public void getShippingConfirmation() throws Exception {
        when(mockRequest.getHeader(AUTHORIZATION)).thenReturn(AUTHORIZATION_HEADER_VALUE);
        when(mockAccountService.authenticate(any(), any())).thenReturn(account);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        LocalDate startDate = LocalDate.now().minusDays(10);
        LocalDate endDate = LocalDate.now();
        
        ShippingConfirmations shippingConfirmations = mock(ShippingConfirmations.class);
        when(mockGBFOrderService.requestShippingConfirmations(eq(startDate), eq(endDate)))
                .thenReturn(shippingConfirmations);
        
        controller.getLabShipmentConfirmations(startDate.toString(), endDate.toString());
        
        verify(mockRequest).getHeader(AUTHORIZATION);
        verify(mockAccountService).authenticate(any(), any());
        verify(mockGBFOrderService).requestShippingConfirmations(eq(startDate), eq(endDate));
    }
    
    private void verifyParticipant(JsonNode payload) {
        ArrayNode identifiers = (ArrayNode)payload.get("participant");
        for (int i=0; i < identifiers.size(); i++) {
            JsonNode node = identifiers.get(i);
            if (node.get("actor").has("identifier")) {
                String ns = node.get("actor").get("identifier").get("system").textValue();
                String value = node.get("actor").get("identifier").get("value").textValue();
                if (value.equals(TEST_USER_ID) && USER_ID_VALUE_NS.equals(ns)) {
                    return;
                }
            }
        }
        fail("Should have thrown exception");
    }
    
    private Set<String> makeSetOf(CRCController.AccountStates state, String unaffectedGroup) {
        return ImmutableSet.of(state.name().toLowerCase(), unaffectedGroup, TEST_USER_GROUP);
    }
    
    private String makeAppointment(String identifier, AppointmentStatus status) {
        Appointment appt = new Appointment();
        if (identifier != null) {
            addAppointmentSageId(appt, identifier);
        }
        addAppointmentParticipantComponent(appt, LOCATION_NS + "ny-location");
        appt.setStatus(status);
        return FHIR_CONTEXT.newJsonParser().encodeResourceToString(appt);
    }
    
    private String makeProcedureRequest() {
        ProcedureRequest procedure = new ProcedureRequest();
        
        Identifier id = new Identifier();
        id.setSystem(USER_ID_VALUE_NS);
        id.setValue(TEST_USER_ID);
        
        Reference ref = new Reference();
        ref.setIdentifier(id);
        
        procedure.setSubject(ref);
        return FHIR_CONTEXT.newJsonParser().encodeResourceToString(procedure);
    }
    
    private String makeObservation(String json) {
        IParser parser = FHIR_CONTEXT.newJsonParser();
        Observation observation = parser.parseResource(Observation.class, json);
        return FHIR_CONTEXT.newJsonParser().encodeResourceToString(observation);
    }
}
