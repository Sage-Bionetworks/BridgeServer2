package org.sagebionetworks.bridge.services;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;

import java.util.List;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.ReportDataDao;
import org.sagebionetworks.bridge.dao.ReportIndexDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.DateRangeResourceList;
import org.sagebionetworks.bridge.models.ReportTypeResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.reports.ReportData;
import org.sagebionetworks.bridge.models.reports.ReportDataKey;
import org.sagebionetworks.bridge.models.reports.ReportIndex;
import org.sagebionetworks.bridge.models.reports.ReportType;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

public class ReportServiceTest {

    private static final String IDENTIFIER = "MyTestReport";
    
    private static final String HEALTH_CODE = "healthCode";
    
    private static final LocalDate START_DATE = LocalDate.parse("2015-01-02");
    
    private static final LocalDate END_DATE = LocalDate.parse("2015-02-02");
    
    private static final LocalDate DATE = LocalDate.parse("2015-02-01");
    
    private static final DateTime START_TIME = DateTime.parse("2015-01-02T10:00:00.000-05:00");
    
    private static final DateTime END_TIME = DateTime.parse("2015-02-02T17:10:00.000-05:00");
    
    private static final String OFFSET_KEY = "offsetKey";
    
    private static final int PAGE_SIZE = 75;
    
    private static final ReportDataKey STUDY_REPORT_DATA_KEY = new ReportDataKey.Builder()
            .withReportType(ReportType.STUDY).withStudyIdentifier(TEST_APP_ID).withIdentifier(IDENTIFIER).build();
    
    private static final ReportDataKey PARTICIPANT_REPORT_DATA_KEY = new ReportDataKey.Builder()
            .withReportType(ReportType.PARTICIPANT).withStudyIdentifier(TEST_APP_ID).withHealthCode(HEALTH_CODE)
            .withIdentifier(IDENTIFIER).build();
    
    private static final ReportData CANNED_REPORT = createReport(LocalDate.parse("2015-02-10"), "First", "Name");
    
    @Mock
    ReportDataDao mockReportDataDao;
    
    @Mock
    ReportIndexDao mockReportIndexDao;
    
    @Captor
    ArgumentCaptor<ReportData> reportDataCaptor;
    
    @Captor
    ArgumentCaptor<ReportIndex> reportIndexCaptor;
    
    @Captor
    ArgumentCaptor<ReportDataKey> reportDataKeyCaptor;
    
    @Captor
    ArgumentCaptor<DateTime> startTimeCaptor;
    
    @Captor
    ArgumentCaptor<DateTime> endTimeCaptor;

    @Captor
    private ArgumentCaptor<LocalDate> localDateCaptor;
    
    @Spy
    ReportService service;
    
    DateRangeResourceList<? extends ReportData> results;
    
    ReportTypeResourceList<? extends ReportIndex> indices;
    
    @BeforeMethod
    public void before() throws Exception {
        MockitoAnnotations.initMocks(this);
        
        service.setReportDataDao(mockReportDataDao);
        service.setReportIndexDao(mockReportIndexDao);

        List<ReportData> list = Lists.newArrayList();
        list.add(createReport(LocalDate.parse("2015-02-10"), "First", "Name"));
        list.add(createReport(LocalDate.parse("2015-02-12"), "Last", "Name"));
        results = new DateRangeResourceList<>(list)
                .withRequestParam("startDate", START_DATE)
                .withRequestParam("endDate", END_DATE);
        
        ReportIndex index = ReportIndex.create();
        index.setIdentifier(IDENTIFIER);
        indices = new ReportTypeResourceList<>(Lists.newArrayList(index))
                .withRequestParam(ResourceList.REPORT_TYPE, ReportType.STUDY);
        
        BridgeUtils.setRequestContext(new RequestContext.Builder().build());
    }
    
    private static ReportData createReport(LocalDate date, String fieldValue1, String fieldValue2) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("field1", fieldValue1);
        node.put("field2", fieldValue2);
        ReportData report = ReportData.create();
        report.setKey(IDENTIFIER +":" + TEST_APP_ID);
        report.setData(node);
        report.setLocalDate(date);
        return report;
    }
    
    @Test
    public void canAccessIfNoIndex() {
        assertTrue(service.canAccess(null));
    }
    
    @Test
    public void canAccessIfReportHasNullSubstudies() {
        ReportIndex index = ReportIndex.create();
        assertTrue(service.canAccess(index));
    }
    
    @Test
    public void canAccessIfReportHasEmptySubstudies() {
        ReportIndex index = ReportIndex.create();
        index.setSubstudyIds(ImmutableSet.of());
        assertTrue(service.canAccess(index));        
    }

    @Test
    public void canAccessIfCallerHasNoSubstudies() {
        ReportIndex index = ReportIndex.create();
        index.setSubstudyIds(TestConstants.USER_SUBSTUDY_IDS);
        assertTrue(service.canAccess(index));
    }

    @Test
    public void canAccessIfCallerHasMatchingSubstudy() {
        ReportIndex index = ReportIndex.create();
        index.setSubstudyIds(TestConstants.USER_SUBSTUDY_IDS);
        BridgeUtils.setRequestContext(
                new RequestContext.Builder().withCallerSubstudies(ImmutableSet.of("substudyB", "substudyC")).build());
        assertTrue(service.canAccess(index));
    }

    // If the index has substudies, and the user doesn't have one of those substudies, this fails
    @Test
    public void canAccessFailsIfCallerDoesNotMatchSubstudies() {
        ReportIndex index = ReportIndex.create();
        index.setSubstudyIds(TestConstants.USER_SUBSTUDY_IDS);
        BridgeUtils.setRequestContext(
                new RequestContext.Builder().withCallerSubstudies(ImmutableSet.of("substudyC")).build());
        assertFalse(service.canAccess(index));        
    }
    
    @Test
    public void canAccessIfPublic() {
        // Create a situation where the user shares no substudies in common with the index, but 
        // the index is public. In that case, access is allowed.
        ReportIndex index = ReportIndex.create();
        index.setSubstudyIds(ImmutableSet.of("substudyC"));
        index.setPublic(true);
        
        BridgeUtils.setRequestContext(
                new RequestContext.Builder().withCallerSubstudies(TestConstants.USER_SUBSTUDY_IDS).build());
        assertTrue(service.canAccess(index));        
    }
    
    @Test
    public void getReportIndex() {
        ReportDataKey key = new ReportDataKey.Builder()
                .withIdentifier(IDENTIFIER).withReportType(ReportType.STUDY)
                .withStudyIdentifier(TEST_APP_ID).build();
        
        ReportIndex index = ReportIndex.create();
        index.setIdentifier(IDENTIFIER);
        doReturn(index).when(mockReportIndexDao).getIndex(key);
        
        ReportIndex retrievedKey = service.getReportIndex(key);
        assertEquals(retrievedKey.getIdentifier(), key.getIdentifier());
        verify(mockReportIndexDao).getIndex(key);
    }
    
    @Test
    public void getStudyReport() {
        doReturn(results).when(mockReportDataDao).getReportData(STUDY_REPORT_DATA_KEY, START_DATE, END_DATE);
        
        DateRangeResourceList<? extends ReportData> retrieved = service.getStudyReport(
                TEST_APP_ID, IDENTIFIER, START_DATE, END_DATE);
        
        verify(mockReportDataDao).getReportData(STUDY_REPORT_DATA_KEY, START_DATE, END_DATE);
        assertEquals(retrieved, results);
    }
    
    @Test
    public void getStudyReportDataNoDates() {
        DateTimeUtils.setCurrentMillisFixed(DateTime.parse("2015-05-05T12:00:00.000Z").getMillis());
        try {
            LocalDate yesterday = LocalDate.parse("2015-05-04");
            LocalDate today = LocalDate.parse("2015-05-05");
            
            doReturn(results).when(mockReportDataDao).getReportData(STUDY_REPORT_DATA_KEY, yesterday, today);
            
            DateRangeResourceList<? extends ReportData> retrieved = service.getStudyReport(
                    TEST_APP_ID, IDENTIFIER, null, null);
            
            verify(mockReportDataDao).getReportData(eq(STUDY_REPORT_DATA_KEY), localDateCaptor.capture(),
                    localDateCaptor.capture());
            assertEquals(localDateCaptor.getAllValues().get(0), yesterday);
            assertEquals(localDateCaptor.getAllValues().get(1), today);
            assertEquals(retrieved, results);
        } finally {
            DateTimeUtils.setCurrentMillisSystem();
        }
    }
    
    @Test
    public void getParticipantReport() {
        doReturn(results).when(mockReportDataDao).getReportData(PARTICIPANT_REPORT_DATA_KEY, START_DATE, END_DATE);
        
        DateRangeResourceList<? extends ReportData> retrieved = service.getParticipantReport(
                TEST_APP_ID, IDENTIFIER, HEALTH_CODE, START_DATE, END_DATE);

        verify(mockReportDataDao).getReportData(PARTICIPANT_REPORT_DATA_KEY, START_DATE, END_DATE);
        assertEquals(retrieved, results);
    }

    @Test
    public void getParticipantReportDataNoDates() {
        DateTimeUtils.setCurrentMillisFixed(DateTime.parse("2015-05-05T12:00:00.000Z").getMillis());
        try {
            LocalDate yesterday = LocalDate.parse("2015-05-04");
            LocalDate today = LocalDate.parse("2015-05-05");
            
            doReturn(results).when(mockReportDataDao).getReportData(PARTICIPANT_REPORT_DATA_KEY, yesterday, today);
            
            DateRangeResourceList<? extends ReportData> retrieved = service.getParticipantReport(
                    TEST_APP_ID, IDENTIFIER, HEALTH_CODE, null, null);
            
            verify(mockReportDataDao).getReportData(eq(PARTICIPANT_REPORT_DATA_KEY), localDateCaptor.capture(),
                    localDateCaptor.capture());
            assertEquals(localDateCaptor.getAllValues().get(0), yesterday);
            assertEquals(localDateCaptor.getAllValues().get(1), today);
            assertEquals(retrieved, results);
        } finally {
            DateTimeUtils.setCurrentMillisSystem();
        }
    }
    
    @Test
    public void saveStudyReport() {
        ReportData someData = createReport(LocalDate.parse("2015-02-10"), "First", "Name");
        service.saveStudyReport(TEST_APP_ID, IDENTIFIER, someData);
        
        verify(mockReportDataDao).saveReportData(reportDataCaptor.capture());
        ReportData retrieved = reportDataCaptor.getValue();
        assertEquals(retrieved, someData);
        assertEquals(retrieved.getKey(), STUDY_REPORT_DATA_KEY.getKeyString());
        assertEquals(retrieved.getDate(), "2015-02-10");
        assertEquals(retrieved.getData().get("field1").asText(), "First");
        assertEquals(retrieved.getData().get("field2").asText(), "Name");
        
        verify(mockReportIndexDao).addIndex(new ReportDataKey.Builder()
                .withStudyIdentifier(TEST_APP_ID)
                .withReportType(ReportType.STUDY)
                .withIdentifier(IDENTIFIER).build(), null);
    }
    
    @Test
    public void saveStudyReportDoesNotResaveIndex() {
        ReportData someData = createReport(LocalDate.parse("2015-02-10"), "First", "Name");
        when(mockReportIndexDao.getIndex(any())).thenReturn(ReportIndex.create());
        
        service.saveStudyReport(TEST_APP_ID, IDENTIFIER, someData);
        
        verify(mockReportIndexDao, never()).addIndex(any(), any());
    }
    
    @Test
    public void saveParticipantReport() throws Exception {
        ReportData someData = createReport(LocalDate.parse("2015-02-10"), "First", "Name");
        service.saveParticipantReport(TEST_APP_ID, IDENTIFIER, HEALTH_CODE, someData);

        verify(mockReportDataDao).saveReportData(reportDataCaptor.capture());
        ReportData retrieved = reportDataCaptor.getValue();
        assertEquals(retrieved, someData);
        assertEquals(retrieved.getKey(), PARTICIPANT_REPORT_DATA_KEY.getKeyString());
        assertEquals(retrieved.getDate(), "2015-02-10");
        assertEquals(retrieved.getData().get("field1").asText(), "First");
        assertEquals(retrieved.getData().get("field2").asText(), "Name");
        
        verify(mockReportIndexDao).addIndex(new ReportDataKey.Builder()
                .withHealthCode(HEALTH_CODE)
                .withStudyIdentifier(TEST_APP_ID)
                .withReportType(ReportType.PARTICIPANT)
                .withIdentifier(IDENTIFIER).build(), null);
    }
    
    @Test
    public void saveParticipantReportDoesNotResaveIndex() throws Exception {
        ReportData someData = createReport(LocalDate.parse("2015-02-10"), "First", "Name");
        when(mockReportIndexDao.getIndex(any())).thenReturn(ReportIndex.create());
        
        service.saveParticipantReport(TEST_APP_ID, IDENTIFIER, HEALTH_CODE, someData);

        verify(mockReportIndexDao, never()).addIndex(any(), any());
    }
    
    @Test
    public void deleteStudyReport() {
        service.deleteStudyReport(TEST_APP_ID, IDENTIFIER);
        
        verify(mockReportDataDao).deleteReportData(STUDY_REPORT_DATA_KEY);
        verify(mockReportIndexDao).removeIndex(STUDY_REPORT_DATA_KEY);
    }
    
    @Test
    public void deleteParticipantReport() {
        service.deleteParticipantReport(TEST_APP_ID, IDENTIFIER, HEALTH_CODE);
        
        verify(mockReportIndexDao).getIndex(any());
        verify(mockReportDataDao).deleteReportData(PARTICIPANT_REPORT_DATA_KEY);
    }
    
    @Test
    public void deleteParticipantReportIndex() {
        service.deleteParticipantReportIndex(TEST_APP_ID, IDENTIFIER);
        
        verify(mockReportIndexDao).removeIndex(reportDataKeyCaptor.capture());
        verifyNoMoreInteractions(mockReportDataDao);
        
        ReportDataKey key = reportDataKeyCaptor.getValue();
        assertEquals(key.getStudyId(), TEST_APP_ID);
        assertEquals(key.getIdentifier(), IDENTIFIER);
    }
    
    @Test
    public void deleteStudyReportRecordNoIndexCleanup() {
        LocalDate startDate = LocalDate.parse("2015-05-05").minusDays(45);
        LocalDate endDate = LocalDate.parse("2015-05-05");
        doReturn(results).when(mockReportDataDao).getReportData(STUDY_REPORT_DATA_KEY, startDate, endDate);
        
        DateTimeUtils.setCurrentMillisFixed(DateTime.parse("2015-05-05").getMillis());
        try {
            service.deleteStudyReportRecord(TEST_APP_ID, IDENTIFIER, DATE.toString());
            
            verify(mockReportDataDao).deleteReportDataRecord(STUDY_REPORT_DATA_KEY, DATE.toString());
            verify(mockReportDataDao).getReportData(STUDY_REPORT_DATA_KEY, startDate, endDate);
        } finally {
            DateTimeUtils.setCurrentMillisSystem();
        }
    }
    
    @Test
    public void deleteStudyReportRecord() {
        LocalDate startDate = LocalDate.parse("2015-05-05").minusDays(45);
        LocalDate endDate = LocalDate.parse("2015-05-05");
        DateRangeResourceList<ReportData> emptyResults = new DateRangeResourceList<>(Lists.<ReportData>newArrayList())
                .withRequestParam("startDate", START_DATE)
                .withRequestParam("endDate", END_DATE);
        doReturn(emptyResults).when(mockReportDataDao).getReportData(STUDY_REPORT_DATA_KEY, startDate, endDate);
        
        DateTimeUtils.setCurrentMillisFixed(DateTime.parse("2015-05-05").getMillis());
        try {
            service.deleteStudyReportRecord(TEST_APP_ID, IDENTIFIER, DATE.toString());
            
            verify(mockReportDataDao).deleteReportDataRecord(STUDY_REPORT_DATA_KEY, DATE.toString());
            verify(mockReportDataDao).getReportData(STUDY_REPORT_DATA_KEY, startDate, endDate);
            verify(mockReportIndexDao).removeIndex(STUDY_REPORT_DATA_KEY);
        } finally {
            DateTimeUtils.setCurrentMillisSystem();
        }
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void deleteStudyReportRecordValidatesDate() {
        service.deleteStudyReportRecord(TEST_APP_ID, IDENTIFIER, "");
    }
    
    @Test
    public void deleteStudyReportRecordValidatesIdentifier() {
        invalid(() -> service.deleteStudyReportRecord(TEST_APP_ID, null, START_DATE.toString()),
                "identifier", "cannot be missing or blank");        
    }
    
    @Test
    public void deleteStudyReportRecordValidatesStudyId() {
        invalid(() -> service.deleteStudyReportRecord(null, IDENTIFIER, START_DATE.toString()),
                "studyId", "is required");        
    }
    
    @Test
    public void deleteParticipantReportRecord() {
        DateTimeUtils.setCurrentMillisFixed(DateTime.parse("2015-05-05").getMillis());
        try {
            service.deleteParticipantReportRecord(TEST_APP_ID, IDENTIFIER, DATE.toString(), HEALTH_CODE);

            verify(mockReportDataDao).deleteReportDataRecord(PARTICIPANT_REPORT_DATA_KEY, DATE.toString());
        } finally {
            DateTimeUtils.setCurrentMillisSystem();
        }
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void deleteParticipantReportRecordValidatesDate() {
        service.deleteParticipantReportRecord(TEST_APP_ID, IDENTIFIER, "", HEALTH_CODE);
    }
    
    // The following are date range tests from the original MPowerVisualizationService, they should work with this
    // service too
    
    @Test
    public void defaultStartAndEndDates() {
        // mock now
        DateTimeUtils.setCurrentMillisFixed(DateTime.parse("2016-02-08T09:00-0800").getMillis());
        try {
            service.getParticipantReport(TEST_APP_ID, IDENTIFIER, HEALTH_CODE, null, null);
            
            verify(mockReportDataDao).getReportData(PARTICIPANT_REPORT_DATA_KEY, LocalDate.parse("2016-02-07"), LocalDate.parse("2016-02-08"));
        } finally {
            DateTimeUtils.setCurrentMillisSystem();
        }
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void startDateAfterEndDateParticipant() {
        service.getParticipantReport(TEST_APP_ID, IDENTIFIER, HEALTH_CODE, END_DATE, START_DATE);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void dateRangeTooWideParticipant() {
        service.getParticipantReport(TEST_APP_ID, IDENTIFIER, HEALTH_CODE, START_DATE, START_DATE.plusDays(46));
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void startDateAfterEndDateStudy() {
        service.getStudyReport(TEST_APP_ID, IDENTIFIER, END_DATE, START_DATE);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void dateRangeTooWideStudy() {
        service.getStudyReport(TEST_APP_ID, IDENTIFIER, START_DATE, START_DATE.plusDays(46));
    }
    
    // Verify that validation errors occur in the service and that nothing is changed in persistence.
    
    @Test
    public void getStudyReportBadIdentifier() {
        invalid(() -> service.getStudyReport(TEST_APP_ID, "bad identifier", START_DATE, END_DATE),
                "identifier", "can only contain letters, numbers, underscore and dash");
    }
    
    @Test
    public void getStudyReportDataNoStudy() {
        invalid(() -> service.getStudyReport(null, IDENTIFIER, START_DATE, END_DATE),
                "studyId", "is required");
    }
    
    @Test
    public void getStudyReportDataNoIdentifier() {
        invalid(() -> service.getStudyReport(TEST_APP_ID, null, START_DATE, END_DATE),
                "identifier", "cannot be missing or blank");
    }
    
    @Test
    public void getParticipantReportDataNoStudy() {
        invalid(() -> service.getParticipantReport(null, IDENTIFIER, HEALTH_CODE, START_DATE, END_DATE),
                "studyId", "is required");
    }

    @Test
    public void getParticipantReportDataNoIdentifier() {
        invalid(() -> service.getParticipantReport(TEST_APP_ID, null, HEALTH_CODE, START_DATE, END_DATE),
                "identifier", "cannot be missing or blank");
    }
    
    @Test
    public void getParticipantReportDataNoHealthCode() {
        invalid(() -> service.getParticipantReport(TEST_APP_ID, IDENTIFIER, null, START_DATE, END_DATE),
                "healthCode", "is required for participant reports");
    }
    
    @Test
    public void saveStudyReportDataNoStudy() {
        invalid(() -> service.saveStudyReport(null, IDENTIFIER, CANNED_REPORT),
                "studyId", "is required");
    }
    
    @Test
    public void saveStudyReportDataNoIdentifier() {
        invalid(() -> service.saveStudyReport(TEST_APP_ID, null, CANNED_REPORT), 
                "identifier", "cannot be missing or blank");
    }

    @Test
    public void saveStudyReportDataNoData() {
        checkNull(() -> service.saveStudyReport(TEST_APP_ID, IDENTIFIER, null));
    }

    @Test
    public void saveParticipantReportDataNoStudy() {
        invalid(() -> service.saveParticipantReport(null, IDENTIFIER, HEALTH_CODE, CANNED_REPORT),
                "studyId", "is required");
    }
    
    @Test
    public void saveParticipantReportDataNoIdentifier() {
        invalid(() -> service.saveParticipantReport(TEST_APP_ID, null, HEALTH_CODE, CANNED_REPORT),
                "identifier", "cannot be missing or blank");
    }
    
    @Test
    public void saveParticipantReportDataNoHealthCode() {
        invalid(() -> service.saveParticipantReport(TEST_APP_ID, IDENTIFIER, null, CANNED_REPORT),
                "healthCode", "is required for participant reports");
    }

    @Test
    public void saveParticipantReportDataNoData() {
        checkNull(() -> service.saveParticipantReport(TEST_APP_ID, IDENTIFIER, HEALTH_CODE, null));
    }
    
    @Test
    public void deleteStudyReportNoStudy() {
        invalid(() -> service.deleteStudyReport(null, IDENTIFIER),
                "studyId", "is required");
    }
    
    @Test
    public void deleteStudyReportNoIdentifier() {
        invalid(() -> service.deleteStudyReport(TEST_APP_ID, null), 
                "identifier", "cannot be missing or blank");
    }
    
    @Test
    public void deleteParticipantReportNoStudy() {
        invalid(() -> service.deleteParticipantReport(null, IDENTIFIER, HEALTH_CODE), 
                "studyId", "is required");
    }
    
    @Test
    public void deleteParticipantReportNoIdentifier() {
        invalid(() -> service.deleteParticipantReport(TEST_APP_ID, null, HEALTH_CODE), 
                "identifier", "cannot be missing or blank");
    }

    @Test
    public void deleteParticipantReportNoHealthCode() {
        invalid(() -> service.deleteParticipantReport(TEST_APP_ID, IDENTIFIER, null),
                "healthCode", "is required for participant reports");
    }
    
    @Test
    public void getStudyIndices() {
        doReturn(indices).when(mockReportIndexDao).getIndices(TEST_APP_ID, ReportType.STUDY);

        ReportTypeResourceList<? extends ReportIndex> indices = service.getReportIndices(TEST_APP_ID, ReportType.STUDY);
        
        assertEquals(indices.getItems().get(0).getIdentifier(), IDENTIFIER);
        assertEquals(indices.getRequestParams().get("reportType"), ReportType.STUDY);
        verify(mockReportIndexDao).getIndices(TEST_APP_ID, ReportType.STUDY);
    }
    
    @Test
    public void getParticipantIndices() {
        // Need to create an index list with ReportType.PARTICIPANT for this test
        ReportIndex index = ReportIndex.create();
        index.setIdentifier(IDENTIFIER);
        indices = new ReportTypeResourceList<>(Lists.newArrayList(index)).withRequestParam(ResourceList.REPORT_TYPE, ReportType.PARTICIPANT);
        
        doReturn(indices).when(mockReportIndexDao).getIndices(TEST_APP_ID, ReportType.PARTICIPANT);

        ReportTypeResourceList<? extends ReportIndex> indices = service.getReportIndices(TEST_APP_ID, ReportType.PARTICIPANT);
        
        assertEquals(indices.getItems().get(0).getIdentifier(), IDENTIFIER);
        assertEquals(indices.getRequestParams().get("reportType"), ReportType.PARTICIPANT);
        verify(mockReportIndexDao).getIndices(TEST_APP_ID, ReportType.PARTICIPANT);
    }
    
    @Test
    public void updateReportIndex() {
        ReportIndex index = ReportIndex.create();
        index.setIdentifier(IDENTIFIER);
        indices = new ReportTypeResourceList<>(Lists.newArrayList(index)).withRequestParam(ResourceList.REPORT_TYPE, ReportType.STUDY);
        
        // This is all that is needed. Everything else is actually inferred by the controller
        ReportIndex updatedIndex = ReportIndex.create();
        updatedIndex.setPublic(true);
        updatedIndex.setIdentifier(IDENTIFIER);
        updatedIndex.setKey(IDENTIFIER+":STUDY");
        
        when(mockReportIndexDao.getIndex(STUDY_REPORT_DATA_KEY)).thenReturn(ReportIndex.create());
        
        service.updateReportIndex(TEST_APP_ID, ReportType.STUDY, updatedIndex);
        
        verify(mockReportIndexDao).updateIndex(reportIndexCaptor.capture());
        
        ReportIndex captured = reportIndexCaptor.getValue();
        assertEquals(captured.getIdentifier(), IDENTIFIER);
        assertTrue(captured.isPublic());
    }
    
    @Test
    public void cannotMakeParticipantStudyPublic() {
        ReportIndex index = ReportIndex.create();
        index.setIdentifier(IDENTIFIER);
        when(mockReportIndexDao.getIndex(any())).thenReturn(index);
        
        // This is all that is needed. Everything else is actually inferred by the controller
        ReportIndex updatedIndex = ReportIndex.create();
        updatedIndex.setPublic(true);
        updatedIndex.setIdentifier(IDENTIFIER);
        updatedIndex.setKey(IDENTIFIER+":STUDY");
        
        service.updateReportIndex(TEST_APP_ID, ReportType.PARTICIPANT, updatedIndex);
        
        verify(mockReportIndexDao).updateIndex(reportIndexCaptor.capture());
        
        ReportIndex captured = reportIndexCaptor.getValue();
        assertEquals(captured.getIdentifier(), IDENTIFIER);
        assertFalse(captured.isPublic());
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void updateReportIndexValidates() throws Exception { 
        ReportIndex updatedIndex = ReportIndex.create();
        
        service.updateReportIndex(TEST_APP_ID, ReportType.PARTICIPANT, updatedIndex);
    }
    
    @Test
    public void getParticipantReportV4() throws Exception {
        service.getParticipantReportV4(TEST_APP_ID, IDENTIFIER, HEALTH_CODE, START_TIME, END_TIME, OFFSET_KEY, PAGE_SIZE);

        verify(mockReportDataDao).getReportDataV4(reportDataKeyCaptor.capture(), eq(START_TIME), eq(END_TIME),
                eq(OFFSET_KEY), eq(PAGE_SIZE));
        
        ReportDataKey key = reportDataKeyCaptor.getValue();
        assertEquals(key.getHealthCode(), HEALTH_CODE);
        assertEquals(key.getStudyId(), TEST_APP_ID);
        assertEquals(key.getReportType(), ReportType.PARTICIPANT);
        assertEquals(key.getIdentifier(), IDENTIFIER);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void getParticipantReportV4MinPageEnforced() {
        service.getParticipantReportV4(TEST_APP_ID, IDENTIFIER, HEALTH_CODE, START_TIME, END_TIME, OFFSET_KEY,
                BridgeConstants.API_MINIMUM_PAGE_SIZE - 1);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void getParticipantReportV4MaxPageEnforced() {
        service.getParticipantReportV4(TEST_APP_ID, IDENTIFIER, HEALTH_CODE, START_TIME, END_TIME, OFFSET_KEY,
                BridgeConstants.API_MAXIMUM_PAGE_SIZE + 1);
    }
    
    @Test
    public void getStudyReportV4() throws Exception {
        service.getStudyReportV4(TEST_APP_ID, IDENTIFIER, START_TIME, END_TIME, OFFSET_KEY, PAGE_SIZE);
        
        verify(mockReportDataDao).getReportDataV4(reportDataKeyCaptor.capture(), eq(START_TIME), eq(END_TIME),
                eq(OFFSET_KEY), eq(PAGE_SIZE));
        
        ReportDataKey key = reportDataKeyCaptor.getValue();
        assertEquals(key.getStudyId(), TEST_APP_ID);
        assertEquals(key.getReportType(), ReportType.STUDY);
        assertEquals(key.getIdentifier(), IDENTIFIER);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void verifiesPageSizeTooSmallV4() throws Exception {
        service.getStudyReportV4(TEST_APP_ID, IDENTIFIER, START_TIME, END_TIME, OFFSET_KEY,
                BridgeConstants.API_MINIMUM_PAGE_SIZE - 1);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void verifiesPageSizeTooLargeV4() throws Exception {
        service.getStudyReportV4(TEST_APP_ID, IDENTIFIER, START_TIME, END_TIME, OFFSET_KEY,
                BridgeConstants.API_MAXIMUM_PAGE_SIZE + 1);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void verifiesTimeZonesEqualV4() throws Exception {
        service.getStudyReportV4(TEST_APP_ID, IDENTIFIER, START_TIME, END_TIME.withZone(DateTimeZone.UTC), OFFSET_KEY,
                BridgeConstants.API_DEFAULT_PAGE_SIZE);
    }
    
    @Test
    public void defaultsDateRangeV4() throws Exception {
        DateTime now = DateTime.parse("2017-05-30T20:00:00.000Z");
        doReturn(now).when(service).getDateTime();
        
        service.getStudyReportV4(TEST_APP_ID, IDENTIFIER, null, null, OFFSET_KEY, PAGE_SIZE);
        
        verify(mockReportDataDao).getReportDataV4(reportDataKeyCaptor.capture(), startTimeCaptor.capture(),
                endTimeCaptor.capture(), eq(OFFSET_KEY), eq(PAGE_SIZE));
        
        DateTime startTime = startTimeCaptor.getValue();
        DateTime endTime = endTimeCaptor.getValue();
        assertEquals(startTime.toString(), now.minusDays(14).toString());
        assertEquals(endTime.toString(), now.toString());
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void verifiesStartTimeMissingV4() throws Exception {
        service.getStudyReportV4(TEST_APP_ID, IDENTIFIER, null, END_TIME, OFFSET_KEY, PAGE_SIZE);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void verifiesEndTimeMissingV4() throws Exception {
        service.getStudyReportV4(TEST_APP_ID, IDENTIFIER, START_TIME, null, OFFSET_KEY, PAGE_SIZE);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void verifiesStartTimeAfterEndTimeV4() throws Exception {
        service.getStudyReportV4(TEST_APP_ID, IDENTIFIER, END_TIME, START_TIME, OFFSET_KEY, PAGE_SIZE);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void verifiesTimeZonesIdenticalV4() throws Exception {
        DateTimeZone zone = DateTimeZone.forOffsetHours(4);
        service.getStudyReportV4(TEST_APP_ID, IDENTIFIER, END_TIME, START_TIME.withZone(zone), OFFSET_KEY, PAGE_SIZE);
    }
    
    @Test
    public void getReportIndexDoesNotAuthorize() {
        // These don't match, but the call succeeds
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("substudyC")).build());
        
        ReportIndex index = ReportIndex.create();
        index.setSubstudyIds(TestConstants.USER_SUBSTUDY_IDS);
        
        when(mockReportIndexDao.getIndex(STUDY_REPORT_DATA_KEY)).thenReturn(index);
        
        ReportIndex retrieved = service.getReportIndex(STUDY_REPORT_DATA_KEY);
        assertEquals(retrieved, index);
    }
    
    private ReportIndex setupMismatchedSubstudies(ReportDataKey reportKey, 
            Set<String> callerSubstudies, Set<String> indexSubstudies) {
        // These don't match and the call succeeds
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(callerSubstudies).build());
        
        ReportIndex index = ReportIndex.create();
        index.setKey(reportKey.getIndexKeyString());
        index.setIdentifier(reportKey.getIdentifier());
        index.setSubstudyIds(indexSubstudies);
        
        when(mockReportIndexDao.getIndex(any())).thenReturn(index);
        return index;
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void getStudyReportAuthorizes() {
        setupMismatchedSubstudies(STUDY_REPORT_DATA_KEY);
        
        service.getStudyReport(TEST_APP_ID, IDENTIFIER, START_DATE, END_DATE);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void getParticipantReportAuthorizes() {
        setupMismatchedSubstudies(PARTICIPANT_REPORT_DATA_KEY);
        
        service.getParticipantReport(TEST_APP_ID, IDENTIFIER, HEALTH_CODE, START_DATE, END_DATE);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void getParticipantReportV4Authorizes() {
        setupMismatchedSubstudies(PARTICIPANT_REPORT_DATA_KEY);
        
        service.getParticipantReportV4(TEST_APP_ID, IDENTIFIER, HEALTH_CODE, 
                START_TIME, END_TIME, null, BridgeConstants.API_MINIMUM_PAGE_SIZE);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void getParticipantReportV4VerifiesSameTimeZone() {
        service.getParticipantReportV4(TEST_APP_ID, IDENTIFIER, HEALTH_CODE, START_TIME,
                END_TIME.withZone(DateTimeZone.UTC), null, BridgeConstants.API_MINIMUM_PAGE_SIZE);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void getStudyReportV4Authorizes() {
        setupMismatchedSubstudies(STUDY_REPORT_DATA_KEY);
        
        service.getStudyReportV4(TEST_APP_ID, IDENTIFIER, 
                START_TIME, END_TIME, null, BridgeConstants.API_MINIMUM_PAGE_SIZE);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void saveStudyReportAuthorizes() {
        setupMismatchedSubstudies(STUDY_REPORT_DATA_KEY);
        
        ReportData data = createReport(START_DATE, "value", "value2");
        
        service.saveStudyReport(TEST_APP_ID, IDENTIFIER, data);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void saveParticipantReportAuthorizes() {
        setupMismatchedSubstudies(PARTICIPANT_REPORT_DATA_KEY);
        
        ReportData data = createReport(START_DATE, "value", "value2");
        
        service.saveParticipantReport(TEST_APP_ID, IDENTIFIER, HEALTH_CODE, data);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void deleteStudyReportAuthorizes() {
        setupMismatchedSubstudies(STUDY_REPORT_DATA_KEY);
        
        service.deleteStudyReport(TEST_APP_ID, IDENTIFIER);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void deleteStudyReportRecordAuthorizes() {
        setupMismatchedSubstudies(STUDY_REPORT_DATA_KEY);
        
        service.deleteStudyReportRecord(TEST_APP_ID, IDENTIFIER, START_DATE.toString());
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void deleteParticipantReportAuthorizes() {
        setupMismatchedSubstudies(PARTICIPANT_REPORT_DATA_KEY);
        
        service.deleteParticipantReport(TEST_APP_ID, IDENTIFIER, HEALTH_CODE);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void deleteParticipantReportRecordAuthorizes() {
        setupMismatchedSubstudies(PARTICIPANT_REPORT_DATA_KEY);
        
        service.deleteParticipantReportRecord(TEST_APP_ID, 
                IDENTIFIER, START_DATE.toString(), HEALTH_CODE);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void deleteParticipantReportIndexAuthorizes() {
        ReportDataKey key = new ReportDataKey.Builder()
                   .withHealthCode("dummy-value") 
                   .withReportType(ReportType.PARTICIPANT)
                   .withIdentifier(IDENTIFIER)
                   .withStudyIdentifier(TEST_APP_ID).build();
        setupMismatchedSubstudies(key);
        
        service.deleteParticipantReportIndex(TEST_APP_ID, IDENTIFIER);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateReportIndexNotFound() {
        ReportIndex updatedIndex = ReportIndex.create();
        updatedIndex.setPublic(true);
        updatedIndex.setIdentifier(IDENTIFIER);
        updatedIndex.setKey(IDENTIFIER+":STUDY");

        service.updateReportIndex(TEST_APP_ID, ReportType.STUDY, updatedIndex);
    }
    
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void updateReportIndexAuthorizes() {
        ReportIndex index = setupMismatchedSubstudies(STUDY_REPORT_DATA_KEY);
        
        service.updateReportIndex(TEST_APP_ID, ReportType.STUDY, index);
    }
    
    @Test
    public void updateReportWithSubstudiesCannotChangeSubstudies() {
        // This is a removal, and it is not allowed because user has substudies memberships
        ReportIndex index = setupMismatchedSubstudies(STUDY_REPORT_DATA_KEY, 
                TestConstants.USER_SUBSTUDY_IDS, ImmutableSet.of("substudyA"));
        
        ReportIndex existingIndex = ReportIndex.create();
        existingIndex.setSubstudyIds(ImmutableSet.of("substudyA", "substudyE"));
        when(mockReportIndexDao.getIndex(any())).thenReturn(existingIndex);
        
        service.updateReportIndex(TEST_APP_ID, ReportType.STUDY, index);
        
        verify(mockReportIndexDao).updateIndex(reportIndexCaptor.capture());
        
        assertEquals(reportIndexCaptor.getValue().getSubstudyIds(), ImmutableSet.of("substudyA", "substudyE"));
    }
    
    @Test
    public void updateReportWithNoSubstudiesCanChangeSubstudies() {
        // This is a removal, and it IS allowed because user has no substudies
        ReportIndex index = setupMismatchedSubstudies(STUDY_REPORT_DATA_KEY, 
                ImmutableSet.of(), ImmutableSet.of("substudyA"));
        
        ReportIndex existingIndex = ReportIndex.create();
        existingIndex.setSubstudyIds(ImmutableSet.of("substudyA", "substudyE"));
        when(mockReportIndexDao.getIndex(any())).thenReturn(existingIndex);
        
        service.updateReportIndex(TEST_APP_ID, ReportType.STUDY, index);
        
        verify(mockReportIndexDao).updateIndex(reportIndexCaptor.capture());
        
        assertEquals(reportIndexCaptor.getValue().getSubstudyIds(), ImmutableSet.of("substudyA"));
    }
    
    private ReportIndex setupMismatchedSubstudies(ReportDataKey reportKey) {
        return setupMismatchedSubstudies(reportKey, ImmutableSet.of("substudyC"), TestConstants.USER_SUBSTUDY_IDS);
    }
    
    private void invalid(Runnable runnable, String fieldName, String message) {
        try {
            runnable.run();
        } catch(InvalidEntityException e) {
            verifyNoMoreInteractions(mockReportDataDao);
            String errorMsg = e.getErrors().get(fieldName).get(0);
            assertEquals(errorMsg, fieldName + " " + message);
            // Also verify that we didn't call the DAO
            verifyNoMoreInteractions(mockReportDataDao);
        }
    }
    
    private void checkNull(Runnable runnable) {
        try {
            runnable.run();
        } catch(NullPointerException e) {
            // verify that we did no work in this case.
            verifyNoMoreInteractions(mockReportDataDao);
        }
    }
}
