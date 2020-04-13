package org.sagebionetworks.bridge.dynamodb;

import static com.amazonaws.services.dynamodbv2.model.ComparisonOperator.BETWEEN;
import static org.sagebionetworks.bridge.TestConstants.HEALTH_CODE;
import static org.sagebionetworks.bridge.BridgeConstants.API_APP_ID;
import static org.sagebionetworks.bridge.models.reports.ReportType.PARTICIPANT;
import static org.sagebionetworks.bridge.models.reports.ReportType.STUDY;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.DateRangeResourceList;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.reports.ReportData;
import org.sagebionetworks.bridge.models.reports.ReportDataKey;

public class DynamoReportDataDaoTest extends Mockito {

    static final LocalDate START_DATE = LocalDate.parse("2016-03-28");
    static final LocalDate END_DATE = LocalDate.parse("2016-03-31");
    static final DateTime START_TIME = DateTime.parse("2016-03-28T17:16:28.711-07:00");
    static final DateTime END_TIME = DateTime.parse("2016-03-31T17:16:28.711-07:00");
    static final String REPORT_ID = "aReportId";
    static final String OFFSET_KEY = "anOffsetKey";
    static final ReportDataKey STUDY_REPORT_KEY = new ReportDataKey.Builder().withIdentifier(REPORT_ID)
            .withReportType(STUDY).withStudyIdentifier(API_APP_ID).build();
    static final ReportDataKey PARTICIPANT_REPORT_KEY = new ReportDataKey.Builder().withIdentifier(REPORT_ID)
            .withHealthCode(HEALTH_CODE).withReportType(PARTICIPANT).withStudyIdentifier(API_APP_ID).build();
    
    DynamoReportData report0;
    DynamoReportData report1;
    DynamoReportData report2;
    DynamoReportData report3;
    ImmutableList<DynamoReportData> reports;
    
    @Mock
    DynamoDBMapper mockMapper;
    
    @Mock
    PaginatedQueryList<DynamoReportData> mockQueryList;
    
    @Mock
    QueryResultPage<DynamoReportData> mockQueryPage; 
    
    @Captor
    ArgumentCaptor<DynamoDBQueryExpression<DynamoReportData>> queryCaptor;
    
    @Captor
    ArgumentCaptor<ReportData> reportDataCaptor;
    
    @Captor
    ArgumentCaptor<List<DynamoReportData>> dataListCaptor;
    
    @InjectMocks
    DynamoReportDataDao dao;
    
    String reportId;
    ReportDataKey reportDataKey;
    ReportDataKey differentReportDataKey;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        
        report0 = createReport(DateTime.parse("2016-03-28T17:16:28.711-07:00"), "g", "h");
        report1 = createReport(DateTime.parse("2016-03-29T17:16:28.711-07:00"), "a", "b");
        report2 = createReport(DateTime.parse("2016-03-30T17:16:28.711-07:00"), "c", "d");
        report3 = createReport(DateTime.parse("2016-03-31T17:16:28.711-07:00"), "e", "f");
        reports = ImmutableList.of(report0, report1, report2, report3);
    }
    
    @Test
    public void getReportData() {
        when(mockMapper.query(eq(DynamoReportData.class), any())).thenReturn(mockQueryList);
        
        DateRangeResourceList<? extends ReportData> result = dao.getReportData(STUDY_REPORT_KEY, START_DATE, END_DATE);
        
        assertEquals(result.getRequestParams().get("startDate"), START_DATE);
        assertEquals(result.getRequestParams().get("endDate"), END_DATE);
        assertEquals(result.getItems(), mockQueryList);
        
        verify(mockMapper).query(eq(DynamoReportData.class), queryCaptor.capture());
        DynamoDBQueryExpression<DynamoReportData> query = queryCaptor.getValue();
        assertEquals(query.getHashKeyValues().getKey(), STUDY_REPORT_KEY.getKeyString());
        Condition dateCondition = query.getRangeKeyConditions().get("date");
        assertEquals(dateCondition.getComparisonOperator(), BETWEEN.name());
        assertEquals(dateCondition.getAttributeValueList().get(0).getS(), START_DATE.toString());
        assertEquals(dateCondition.getAttributeValueList().get(1).getS(), END_DATE.toString());
    }

    @Test
    public void getReportDataV4() {
        // For this test we want the timezone to start as UTC, not PST, so adjust this:
        List<DynamoReportData> list = reports.stream().map((report) -> {
            report.setDateTime(report.getDateTime().withZone(DateTimeZone.UTC));
            return report;
        }).collect(Collectors.toList());
        
        when(mockMapper.queryPage(eq(DynamoReportData.class), any())).thenReturn(mockQueryPage);
        when(mockQueryPage.getResults()).thenReturn(list);
        
        ForwardCursorPagedResourceList<ReportData> result = dao.getReportDataV4(STUDY_REPORT_KEY, START_TIME, END_TIME,
                OFFSET_KEY, 5);
        assertEquals(result.getItems().size(), 4);
        assertEquals(result.getRequestParams().get(ResourceList.PAGE_SIZE), 5);
        assertEquals(result.getRequestParams().get(ResourceList.OFFSET_KEY), OFFSET_KEY);
        assertEquals(result.getRequestParams().get(ResourceList.START_TIME), START_TIME.toString());
        assertEquals(result.getRequestParams().get(ResourceList.END_TIME), END_TIME.toString());
        for (int i=0; i < result.getItems().size(); i++) {
            ReportData oneReport = result.getItems().get(i);
            assertEquals(oneReport.getDateTime().getZone(), DateTimeZone.forOffsetHours(-7));
        }
        
        verify(mockMapper).queryPage(eq(DynamoReportData.class), queryCaptor.capture());
        DynamoDBQueryExpression<DynamoReportData> query = queryCaptor.getValue();
        assertEquals(query.getHashKeyValues().getKey(), STUDY_REPORT_KEY.getKeyString());
        assertEquals(query.getLimit(), new Integer(6));
        // When an offsetKey is provided, it's the starting timestamp for the next page of records.
        // In test offsetKey would fail because it's not a timestamp, but it demonstrates that
        // this is working.
        Condition dateCondition = query.getRangeKeyConditions().get("date");
        assertEquals(dateCondition.getComparisonOperator(), BETWEEN.name());
        assertEquals(dateCondition.getAttributeValueList().get(0).getS(), OFFSET_KEY);
        assertEquals(dateCondition.getAttributeValueList().get(1).getS(),
                END_TIME.withZone(DateTimeZone.UTC).toString());
    }
    
    @Test
    public void getReportDataV4NoOffsetKey() {
        when(mockMapper.queryPage(eq(DynamoReportData.class), any())).thenReturn(mockQueryPage);
        when(mockQueryPage.getResults()).thenReturn(reports);
        
        ForwardCursorPagedResourceList<ReportData> result = dao.getReportDataV4(STUDY_REPORT_KEY, START_TIME, END_TIME,
                null, 5);
        assertEquals(result.getItems().size(), 4);
        assertNull(result.getRequestParams().get(ResourceList.OFFSET_KEY));
        
        verify(mockMapper).queryPage(eq(DynamoReportData.class), queryCaptor.capture());
        DynamoDBQueryExpression<DynamoReportData> query = queryCaptor.getValue();
        Condition dateCondition = query.getRangeKeyConditions().get("date");
        assertEquals(dateCondition.getAttributeValueList().get(0).getS(),
                START_TIME.withZone(DateTimeZone.UTC).toString());
    }
    
    @Test
    public void getReportDataV4MultiplePages() {
        List<DynamoReportData> list = new ArrayList<>();
        list.addAll(reports);
        list.addAll(reports.subList(0, 2));
        
        when(mockMapper.queryPage(eq(DynamoReportData.class), any())).thenReturn(mockQueryPage);
        when(mockQueryPage.getResults()).thenReturn(list);
        
        ForwardCursorPagedResourceList<ReportData> result = dao.getReportDataV4(STUDY_REPORT_KEY, START_TIME, END_TIME,
                null, 5);
        assertEquals(result.getItems().size(), 5);
        assertEquals(result.getNextPageOffsetKey(), list.get(5).getDate()); // 5 is the next offsetKey
    }    
    
    @Test
    public void saveReportData() {
        dao.saveReportData(report0);
        
        verify(mockMapper).save(reportDataCaptor.capture());
        ReportData reportData = reportDataCaptor.getValue();
        assertSame(reportData, report0);
        assertEquals(reportData.getDateTime().getZone(), DateTimeZone.UTC);
    }
    
    @Test
    public void deleteReportData() {
        when(mockMapper.query(eq(DynamoReportData.class), any())).thenReturn(mockQueryList);
        
        dao.deleteReportData(report0.getReportDataKey());
        
        verify(mockMapper).query(eq(DynamoReportData.class), queryCaptor.capture());
        DynamoDBQueryExpression<DynamoReportData> query = queryCaptor.getValue();
        assertEquals(query.getHashKeyValues().getKey(), report0.getReportDataKey().getKeyString());
        
        verify(mockMapper).batchDelete(dataListCaptor.capture());
        
        assertEquals(dataListCaptor.getValue(), mockQueryList);
    }
    
    @Test
    public void deleteReportDataNoReports() {
        when(mockMapper.query(eq(DynamoReportData.class), any())).thenReturn(mockQueryList);
        when(mockQueryList.isEmpty()).thenReturn(true);
        
        dao.deleteReportData(report0.getReportDataKey());
        
        verify(mockMapper, never()).batchDelete(dataListCaptor.capture());
    }    
    
    @Test
    public void deleteReportDataRecord() {
        when(mockMapper.load(any())).thenReturn(report0);
        
        String localDateString = report0.getDateTime().toLocalDate().toString();
        dao.deleteReportDataRecord(report0.getReportDataKey(), localDateString);
        
        verify(mockMapper).load(reportDataCaptor.capture());
        assertEquals(reportDataCaptor.getValue().getKey(), report0.getReportDataKey().getKeyString());
        assertEquals(reportDataCaptor.getValue().getDate(), localDateString);
        
        verify(mockMapper).delete(report0);
    }
    
    @Test
    public void deleteReportDataRecordNoRecord() {
        String localDateString = report0.getDateTime().toLocalDate().toString();
        dao.deleteReportDataRecord(report0.getReportDataKey(), localDateString);
        
        verify(mockMapper, never()).delete(any());
    }    
    
    private static DynamoReportData createReport(DateTime date, String fieldValue1, String fieldValue2) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("field1", fieldValue1);
        node.put("field2", fieldValue2);
        DynamoReportData report = new DynamoReportData();
        report.setKey(STUDY_REPORT_KEY.getKeyString());
        report.setReportDataKey(STUDY_REPORT_KEY);
        report.setData(node);
        report.setDateTime(date);
        return report;
    }    
}
