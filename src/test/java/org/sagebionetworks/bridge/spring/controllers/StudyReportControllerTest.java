package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeUtils.getRequestContext;
import static org.sagebionetworks.bridge.BridgeUtils.setRequestContext;
import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.TestConstants.CONSENTED_STATUS_MAP;
import static org.sagebionetworks.bridge.TestConstants.HEALTH_CODE;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.USER_SUBSTUDY_IDS;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.sagebionetworks.bridge.models.reports.ReportType.STUDY;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.joda.time.DateTime;
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
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoApp;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.DateRangeResourceList;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.reports.ReportData;
import org.sagebionetworks.bridge.models.reports.ReportDataKey;
import org.sagebionetworks.bridge.models.reports.ReportIndex;
import org.sagebionetworks.bridge.models.reports.ReportType;
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.services.ReportService;
import org.sagebionetworks.bridge.services.AppService;

public class StudyReportControllerTest extends Mockito {
    
    private static final ObjectMapper MAPPER = BridgeObjectMapper.get();
    
    private static final String REPORT_ID = "foo";

    private static final LocalDate START_DATE = LocalDate.parse("2015-01-02");
    
    private static final LocalDate END_DATE = LocalDate.parse("2015-02-02");
    
    private static final DateTime START_TIME = DateTime.parse("2015-01-02T08:32:50.000-07:00");
    
    private static final DateTime END_TIME = DateTime.parse("2015-02-02T15:00:32.123-07:00");
    
    private static final String OFFSET_KEY = "offsetKey";
    
    private static final String PAGE_SIZE = "20";
    
    @Mock
    ReportService mockReportService;
    
    @Mock
    AppService mockAppService;
    
    @Mock
    AccountService mockAccountService;
    
    @Mock
    Account mockAccount;
    
    @Mock
    Account mockOtherAccount;
    
    @Mock
    HttpServletRequest mockRequest;
    
    @Mock
    HttpServletResponse mockResponse;
    
    @Captor
    ArgumentCaptor<ReportData> reportDataCaptor;
    
    @Captor
    ArgumentCaptor<ReportIndex> reportDataIndex;
    
    ForwardCursorPagedResourceList<ReportData> page;
    
    @Spy
    @InjectMocks
    StudyReportController controller;
    
    UserSession session;
    
    @BeforeMethod
    public void before() throws Exception {
        MockitoAnnotations.initMocks(this);
        
        DynamoApp study = new DynamoApp();
        study.setIdentifier(TEST_APP_ID);
        
        StudyParticipant participant = new StudyParticipant.Builder().withHealthCode(HEALTH_CODE)
                .withRoles(ImmutableSet.of(DEVELOPER)).build();
        
        session = new UserSession(participant);
        session.setAppId(TEST_APP_ID);
        session.setAuthenticated(true);
        session.setConsentStatuses(CONSENTED_STATUS_MAP);
        
        doReturn(study).when(mockAppService).getApp(TEST_APP_ID);
        doReturn(session).when(controller).getSessionIfItExists();
        
        ReportIndex index = ReportIndex.create();
        index.setIdentifier("fofo");
        
        index = ReportIndex.create();
        index.setIdentifier("fofo");
        
        List<ReportData> reportList = ImmutableList.of();
        page = new ForwardCursorPagedResourceList<ReportData>(reportList, "nextPageOffsetKey")
                .withRequestParam(ResourceList.OFFSET_KEY, OFFSET_KEY)
                .withRequestParam(ResourceList.PAGE_SIZE, Integer.parseInt(PAGE_SIZE))
                .withRequestParam(ResourceList.START_TIME, START_TIME)
                .withRequestParam(ResourceList.END_TIME, END_TIME);
        
        // There are some tests that need to clear this for the call to work correctly.
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(USER_SUBSTUDY_IDS).build());
        
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
    }
    
    @AfterMethod
    public void after() {
        setRequestContext(NULL_INSTANCE);
    }
    
    @Test
    public void getStudyReportIndexAsDeveloper() throws Exception {
        // Developer is set up in the @Before method, no further changes necessary
        getStudyReportIndex();
    }
    
    @Test
    public void getStudyReportIndexAsResearcher() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder().withRoles(ImmutableSet.of(RESEARCHER)).build();
        session.setParticipant(participant);
        
        getStudyReportIndex();
    }
    
    @Test 
    public void userCanAccess() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder().withRoles(ImmutableSet.of()).build();
        session.setParticipant(participant);
        
        getStudyReportIndex();
    }

    private void getStudyReportIndex() throws Exception {
        ReportIndex index = ReportIndex.create();
        index.setIdentifier(REPORT_ID);
        index.setPublic(true);
        index.setKey(REPORT_ID+":STUDY");
        
        ReportDataKey key = new ReportDataKey.Builder()
                .withIdentifier(REPORT_ID)
                .withReportType(ReportType.STUDY)
                .withAppId(TEST_APP_ID).build();
        
        doReturn(index).when(mockReportService).getReportIndex(key);
        
        ReportIndex result = controller.getStudyReportIndex(REPORT_ID);

        // We're also testing the serialization of the object to ensure the key is not 
        // present; this used to be part of our controllers but now we have to do it 
        // manually in tests if we want to continue testing this
        ReportIndex deser = MAPPER.readValue(MAPPER.writeValueAsString(result), ReportIndex.class);
        
        assertEquals(deser.getIdentifier(), REPORT_ID);
        assertTrue(deser.isPublic());
        assertNull(deser.getKey()); // isn't returned in API
    }

    @Test
    public void getStudyReportData() throws Exception {
        mockRequestBody(mockRequest, "{}");
        doReturn(makeResults(START_DATE, END_DATE)).when(mockReportService).getStudyReport(session.getAppId(),
                REPORT_ID, START_DATE, END_DATE);
        
        DateRangeResourceList<? extends ReportData> result = controller.getStudyReport(REPORT_ID, START_DATE.toString(), END_DATE.toString());
        assertResult(result);
    }
    
    @Test
    public void getStudyReportDataWithNoDates() throws Exception {
        mockRequestBody(mockRequest, "{}");
        doReturn(makeResults(START_DATE, END_DATE)).when(mockReportService).getStudyReport(session.getAppId(),
                REPORT_ID, null, null);
        
        DateRangeResourceList<? extends ReportData>result = controller.getStudyReport(REPORT_ID, null, null);
        assertResult(result);
    }
    
    @Test
    public void saveStudyReportData() throws Exception {
        String json = TestUtils.createJson("{'date':'2015-02-12','data':{'field1':'Last','field2':'Name'}}");
        mockRequestBody(mockRequest, json);
                
        StatusMessage result = controller.saveStudyReport(REPORT_ID);
        assertEquals(result, StudyReportController.SAVED_MSG);
        
        verify(mockReportService).saveStudyReport(eq(TEST_APP_ID), eq(REPORT_ID), reportDataCaptor.capture());
        ReportData reportData = reportDataCaptor.getValue();
        assertEquals(LocalDate.parse("2015-02-12").toString(), reportData.getDate().toString());
        assertNull(reportData.getKey());
        assertEquals("Last", reportData.getData().get("field1").asText());
        assertEquals("Name", reportData.getData().get("field2").asText());
    }

    @Test
    public void deleteStudyReportData() throws Exception {
        StatusMessage result = controller.deleteStudyReport(REPORT_ID);
        assertEquals(result, StudyReportController.DELETED_MSG);
        
        verify(mockReportService).deleteStudyReport(session.getAppId(), REPORT_ID);
    }
    
    @Test
    public void deleteStudyReportDataRecord() throws Exception {
        StatusMessage result = controller.deleteStudyReportRecord(REPORT_ID, "2014-05-10");
        assertEquals(result, StudyReportController.DELETED_DATA_MSG);
        
        verify(mockReportService).deleteStudyReportRecord(session.getAppId(), REPORT_ID, "2014-05-10");
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void deleteStudyRecordDataRecordDeveloper() {
        StudyParticipant regularUser = new StudyParticipant.Builder().copyOf(session.getParticipant())
            .withRoles(ImmutableSet.of()).build();
        session.setParticipant(regularUser);
        
        controller.deleteStudyReportRecord(REPORT_ID, "2014-05-10");
    }
    
    @Test
    public void canUpdateStudyReportIndex() throws Exception {
        mockRequestBody(mockRequest, "{\"public\":true}");

        StatusMessage result = controller.updateStudyReportIndex(REPORT_ID);
        assertEquals(result, StudyReportController.UPDATED_MSG);
        
        verify(mockReportService).updateReportIndex(eq(TEST_APP_ID), eq(STUDY), reportDataIndex.capture());
        ReportIndex index = reportDataIndex.getValue();
        assertTrue(index.isPublic());
        assertEquals(index.getIdentifier(), REPORT_ID);
        assertEquals(index.getKey(), TEST_APP_ID+":STUDY");
    }
    
    @Test
    public void canGetPublicStudyReport() throws Exception {
        ReportDataKey key = new ReportDataKey.Builder().withAppId(TEST_APP_ID)
                .withIdentifier(REPORT_ID).withReportType(STUDY).build();
        
        ReportIndex index = ReportIndex.create();
        index.setPublic(true);
        index.setIdentifier(REPORT_ID);
        doReturn(index).when(mockReportService).getReportIndex(key);
        
        doReturn(makeResults(START_DATE, END_DATE)).when(mockReportService).getStudyReport(session.getAppId(),
                REPORT_ID, START_DATE, END_DATE);
        
        DateRangeResourceList<? extends ReportData>result = controller.getPublicStudyReport(
                TEST_APP_ID, REPORT_ID, START_DATE.toString(), END_DATE.toString());

        assertEquals(2, result.getItems().size());
        
        assertEquals(NULL_INSTANCE, getRequestContext());
        verify(mockReportService).getReportIndex(key);
        verify(mockReportService).getStudyReport(TEST_APP_ID, REPORT_ID, START_DATE, END_DATE);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void missingPublicStudyReturns404() throws Exception {
        controller.getPublicStudyReport(TEST_APP_ID, "does-not-exist", "2016-05-02", "2016-05-09");
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void privatePublicStudyReturns404() throws Exception {
        ReportDataKey key = new ReportDataKey.Builder().withIdentifier(REPORT_ID).withReportType(STUDY)
                .withAppId(TEST_APP_ID).build();
        
        ReportIndex index = ReportIndex.create();
        index.setPublic(false);
        index.setKey(key.getIndexKeyString());
        index.setIdentifier(REPORT_ID);
        
        doReturn(index).when(mockReportService).getReportIndex(key);
        
        controller.getPublicStudyReport(TEST_APP_ID, REPORT_ID, START_DATE.toString(), END_DATE.toString());
    }
    
    @Test
    public void getStudyReportV4() throws Exception {
        ReportDataKey key = new ReportDataKey.Builder().withIdentifier(REPORT_ID).withReportType(STUDY)
                .withAppId(TEST_APP_ID).build();
        
        ReportIndex index = ReportIndex.create();
        index.setPublic(false);
        index.setKey(key.getIndexKeyString());
        index.setIdentifier(REPORT_ID);
        
        doReturn(page).when(mockReportService).getStudyReportV4(session.getAppId(), REPORT_ID, START_TIME,
                END_TIME, OFFSET_KEY, Integer.parseInt(PAGE_SIZE));
        
        ForwardCursorPagedResourceList<ReportData> result = controller.getStudyReportV4(REPORT_ID,
                START_TIME.toString(), END_TIME.toString(), OFFSET_KEY, PAGE_SIZE);
        
        verify(mockReportService).getStudyReportV4(TEST_APP_ID, REPORT_ID, START_TIME, END_TIME,
                OFFSET_KEY, Integer.parseInt(PAGE_SIZE));
        
        assertEquals(result.getNextPageOffsetKey(), "nextPageOffsetKey");
        assertEquals(result.getRequestParams().get(ResourceList.OFFSET_KEY), OFFSET_KEY);
        assertEquals(result.getRequestParams().get(ResourceList.PAGE_SIZE), Integer.parseInt(PAGE_SIZE));
        assertEquals(result.getRequestParams().get(ResourceList.START_TIME), START_TIME.toString());
        assertEquals(result.getRequestParams().get(ResourceList.END_TIME), END_TIME.toString());
    }
    
    private void assertResult(DateRangeResourceList<? extends ReportData> result) throws Exception {
        JsonNode node = BridgeObjectMapper.get().readTree(BridgeObjectMapper.get().writeValueAsString(result));
        
        assertEquals("2015-01-02", node.get("startDate").asText());
        assertEquals("2015-02-02", node.get("endDate").asText());
        assertEquals(2, node.get("items").size());
        assertEquals("DateRangeResourceList", node.get("type").asText());
        
        JsonNode child1 = node.get("items").get(0);
        assertEquals("2015-02-10", child1.get("date").asText());
        assertEquals("ReportData", child1.get("type").asText());
        JsonNode child1Data = child1.get("data");
        assertEquals("First", child1Data.get("field1").asText());
        assertEquals("Name", child1Data.get("field2").asText());
        
        JsonNode child2 = node.get("items").get(1);
        assertEquals("2015-02-12", child2.get("date").asText());
        assertEquals("ReportData", child2.get("type").asText());
        JsonNode child2Data = child2.get("data");
        assertEquals("Last", child2Data.get("field1").asText());
        assertEquals("Name", child2Data.get("field2").asText());
    }
    
    private DateRangeResourceList<ReportData> makeResults(LocalDate startDate, LocalDate endDate){
        List<ReportData> list = ImmutableList.of(createReport(LocalDate.parse("2015-02-10"), "First", "Name"),
                createReport(LocalDate.parse("2015-02-12"), "Last", "Name"));
        
        return new DateRangeResourceList<ReportData>(list)
                .withRequestParam("startDate", startDate)
                .withRequestParam("endDate", endDate);
    }
    
    private ReportData createReport(LocalDate date, String fieldValue1, String fieldValue2) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("field1", fieldValue1);
        node.put("field2", fieldValue2);
        ReportData report = ReportData.create();
        report.setKey("foo:" + TEST_APP_ID);
        report.setLocalDate(date);
        report.setData(node);
        return report;
    }
    
}
