package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.hibernate.HibernateAdherenceReportDao.ADHERENCE_MAX_FIELD;
import static org.sagebionetworks.bridge.hibernate.HibernateAdherenceReportDao.ADHERENCE_MIN_FIELD;
import static org.sagebionetworks.bridge.hibernate.HibernateAdherenceReportDao.ID_FILTER_FIELD;
import static org.sagebionetworks.bridge.hibernate.HibernateAdherenceReportDao.LABEL_FILTER_FIELD;
import static org.sagebionetworks.bridge.hibernate.HibernateAdherenceReportDao.PROGRESSION_FIELD;
import static org.sagebionetworks.bridge.hibernate.HibernateAdherenceReportDao.SELECT_COUNT;
import static org.sagebionetworks.bridge.hibernate.HibernateAdherenceReportDao.SELECT_DISTINCT;
import static org.sagebionetworks.bridge.models.AccountTestFilter.BOTH;
import static org.sagebionetworks.bridge.models.AccountTestFilter.PRODUCTION;
import static org.sagebionetworks.bridge.models.AccountTestFilter.TEST;
import static org.sagebionetworks.bridge.models.schedules2.adherence.ParticipantProgressionState.IN_PROGRESS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

import java.util.List;
import java.util.Map;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import org.mockito.MockitoAnnotations;
import org.sagebionetworks.bridge.models.AdherenceReportSearch;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.schedules2.adherence.ParticipantProgressionState;
import org.sagebionetworks.bridge.models.schedules2.adherence.weekly.WeeklyAdherenceReport;

public class HibernateAdherenceReportDaoTest extends Mockito {

    private static String ORDER_BY = " ORDER BY weeklyAdherencePercent, lastName, firstName, email, phone, externalId";
    
    private static String FULL_SQL = "FROM WeeklyAdherenceReport h JOIN h.searchableLabels label WHERE "
            +"h.appId = :appId AND h.studyId = :studyId AND weeklyAdherencePercent >= :adherenceMin AND "
            +"weeklyAdherencePercent <= :adherenceMax AND progression = :progressionFilter AND (label "
            +"LIKE :labelFilter0) AND (externalId LIKE :id OR identifier LIKE :id OR firstName LIKE :id "
            +"OR lastName LIKE :id OR email LIKE :id OR phone LIKE :id)"+ORDER_BY;
    
    private static String TEST_ACCOUNTS_SQL = "FROM WeeklyAdherenceReport h WHERE h.appId = :appId AND "
            +"h.studyId = :studyId AND testAccount = 1"+ORDER_BY;
    
    private static String PROD_ACCOUNTS_SQL = "FROM WeeklyAdherenceReport h WHERE h.appId = :appId AND "
            +"h.studyId = :studyId AND testAccount = 0"+ORDER_BY;
    
    private static String LABELS_SQL = "FROM WeeklyAdherenceReport h JOIN h.searchableLabels label WHERE "
            +"h.appId = :appId AND h.studyId = :studyId AND (label LIKE :labelFilter0 OR label LIKE "
            +":labelFilter1)"+ORDER_BY;
    
    private static String NO_LABELS_SQL = "FROM WeeklyAdherenceReport h WHERE h.appId = :appId AND "
            +"h.studyId = :studyId"+ORDER_BY;

    private static String MIN_NO_MAX_SQL = "FROM WeeklyAdherenceReport h WHERE h.appId = :appId AND "
            +"h.studyId = :studyId AND weeklyAdherencePercent >= :adherenceMin"+ORDER_BY;

    private static String MAX_NO_MIN_SQL = "FROM WeeklyAdherenceReport h WHERE h.appId = :appId AND "
            +"h.studyId = :studyId AND weeklyAdherencePercent <= :adherenceMax"+ORDER_BY;
    
    private static String ID_FILTER = "FROM WeeklyAdherenceReport h WHERE h.appId = "
            +":appId AND h.studyId = :studyId AND (externalId LIKE :id OR identifier LIKE :id OR firstName "
            +"LIKE :id OR lastName LIKE :id OR email LIKE :id OR phone LIKE :id)"+ORDER_BY;
    
    private static String PROGRESSION_FILTER = "FROM WeeklyAdherenceReport h WHERE h.appId = :appId AND h.studyId "
            +"= :studyId AND progression = :progressionFilter"+ORDER_BY;
    
    @Mock
    HibernateHelper mockHelper;
    
    @Captor
    ArgumentCaptor<String> stringCaptor;
    
    @Captor
    ArgumentCaptor<Map<String,Object>> paramsCaptor;
    
    @InjectMocks
    HibernateAdherenceReportDao dao;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
    }
    
    @Test
    public void saveWeeklyAdherenceReport() {
        WeeklyAdherenceReport report = new WeeklyAdherenceReport();
        dao.saveWeeklyAdherenceReport(report);
        verify(mockHelper).saveOrUpdate(report);
    }
    
    @Test
    public void getWeeklyAdherenceReports() {
        List<WeeklyAdherenceReport> reports = ImmutableList.of();
        when(mockHelper.queryCount(any(), any())).thenReturn(1000);
        when(mockHelper.queryGet(any(), any(), eq(50), eq(100), eq(WeeklyAdherenceReport.class))).thenReturn(reports);

        AdherenceReportSearch search = new AdherenceReportSearch();
        search.setTestFilter(BOTH);
        search.setLabelFilters(ImmutableList.of("label"));
        search.setAdherenceMin(10);
        search.setAdherenceMax(75);
        search.setProgressionFilter(IN_PROGRESS);
        search.setIdFilter("anId");
        search.setOffsetBy(50);
        search.setPageSize(100);
        
        PagedResourceList<WeeklyAdherenceReport> retValue = dao.getWeeklyAdherenceReports(TEST_APP_ID, TEST_STUDY_ID,
                search);
        
        assertEquals(retValue.getTotal(), Integer.valueOf(1000));
        assertSame(retValue.getItems(), reports);

        verify(mockHelper).queryCount(stringCaptor.capture(), paramsCaptor.capture());
        verify(mockHelper).queryGet(stringCaptor.capture(), paramsCaptor.capture(), eq(50), eq(100),
                eq(WeeklyAdherenceReport.class));
        
        assertEquals(stringCaptor.getAllValues().get(0), SELECT_COUNT + FULL_SQL);
        assertEquals(stringCaptor.getAllValues().get(1), SELECT_DISTINCT + FULL_SQL);
        assertEquals(paramsCaptor.getValue().get(LABEL_FILTER_FIELD+"0"), "%:label:%");
        assertEquals(paramsCaptor.getValue().get(ADHERENCE_MIN_FIELD), 10);
        assertEquals(paramsCaptor.getValue().get(ADHERENCE_MAX_FIELD), 75);
        assertEquals(paramsCaptor.getValue().get(PROGRESSION_FIELD), IN_PROGRESS);
    }
    
    @Test
    public void getWeeklyAdherenceReports_testOnly() {
        List<WeeklyAdherenceReport> reports = ImmutableList.of();
        when(mockHelper.queryCount(any(), any())).thenReturn(1000);
        when(mockHelper.queryGet(any(), any(), eq(0), eq(50), eq(WeeklyAdherenceReport.class))).thenReturn(reports);
        
        AdherenceReportSearch search = new AdherenceReportSearch();
        search.setTestFilter(TEST);

        PagedResourceList<WeeklyAdherenceReport> retValue = dao.getWeeklyAdherenceReports(
                TEST_APP_ID, TEST_STUDY_ID, search);
        
        assertEquals(retValue.getTotal(), Integer.valueOf(1000));
        assertSame(retValue.getItems(), reports);

        verify(mockHelper).queryCount(stringCaptor.capture(), paramsCaptor.capture());
        verify(mockHelper).queryGet(stringCaptor.capture(), paramsCaptor.capture(), eq(0), eq(50),
                eq(WeeklyAdherenceReport.class));
        
        assertEquals(stringCaptor.getAllValues().get(0), SELECT_COUNT + TEST_ACCOUNTS_SQL);
        assertEquals(stringCaptor.getAllValues().get(1), SELECT_DISTINCT + TEST_ACCOUNTS_SQL);
    }
    
    @Test
    public void getWeeklyAdherenceReports_productionOnly() {
        List<WeeklyAdherenceReport> reports = ImmutableList.of();
        when(mockHelper.queryCount(any(), any())).thenReturn(1000);
        when(mockHelper.queryGet(any(), any(), eq(0), eq(50), eq(WeeklyAdherenceReport.class))).thenReturn(reports);
        
        AdherenceReportSearch search = new AdherenceReportSearch();
        search.setTestFilter(PRODUCTION);

        PagedResourceList<WeeklyAdherenceReport> retValue = dao.getWeeklyAdherenceReports(
                TEST_APP_ID, TEST_STUDY_ID, search);
        
        assertEquals(retValue.getTotal(), Integer.valueOf(1000));
        assertSame(retValue.getItems(), reports);

        verify(mockHelper).queryCount(stringCaptor.capture(), paramsCaptor.capture());
        verify(mockHelper).queryGet(stringCaptor.capture(), paramsCaptor.capture(), eq(0), eq(50),
                eq(WeeklyAdherenceReport.class));
        
        assertEquals(stringCaptor.getAllValues().get(0), SELECT_COUNT + PROD_ACCOUNTS_SQL);
        assertEquals(stringCaptor.getAllValues().get(1), SELECT_DISTINCT + PROD_ACCOUNTS_SQL);
    }
    
    @Test
    public void getWeeklyAdherenceReports_multipleLabels() {
        AdherenceReportSearch search = new AdherenceReportSearch();
        search.setLabelFilters(ImmutableList.of("A", "B"));

        List<WeeklyAdherenceReport> reports = ImmutableList.of();
        when(mockHelper.queryCount(any(), any())).thenReturn(1000);
        when(mockHelper.queryGet(any(), any(), eq(0), eq(50), eq(WeeklyAdherenceReport.class))).thenReturn(reports);

        PagedResourceList<WeeklyAdherenceReport> retValue = dao.getWeeklyAdherenceReports(TEST_APP_ID, TEST_STUDY_ID,
                search);
        assertEquals(retValue.getTotal(), Integer.valueOf(1000));
        assertSame(retValue.getItems(), reports);

        verify(mockHelper).queryCount(stringCaptor.capture(), paramsCaptor.capture());
        verify(mockHelper).queryGet(stringCaptor.capture(), paramsCaptor.capture(), eq(0), eq(50),
                eq(WeeklyAdherenceReport.class));
        
        assertEquals(stringCaptor.getAllValues().get(0), SELECT_COUNT + LABELS_SQL);
        assertEquals(stringCaptor.getAllValues().get(1), SELECT_DISTINCT + LABELS_SQL);
        assertEquals(paramsCaptor.getValue().get(LABEL_FILTER_FIELD+"0"), "%:A:%");
        assertEquals(paramsCaptor.getValue().get(LABEL_FILTER_FIELD+"1"), "%:B:%");
    }

    @Test
    public void getWeeklyAdherenceReports_withEmptyLabelFilter() {
        AdherenceReportSearch search = new AdherenceReportSearch();
        search.setLabelFilters(ImmutableList.of());

        List<WeeklyAdherenceReport> reports = ImmutableList.of();
        when(mockHelper.queryCount(any(), any())).thenReturn(1000);
        when(mockHelper.queryGet(any(), any(), eq(0), eq(50), eq(WeeklyAdherenceReport.class))).thenReturn(reports);

        PagedResourceList<WeeklyAdherenceReport> retValue = dao.getWeeklyAdherenceReports(TEST_APP_ID, TEST_STUDY_ID,
                search);
        assertEquals(retValue.getTotal(), Integer.valueOf(1000));
        assertSame(retValue.getItems(), reports);

        verify(mockHelper).queryCount(stringCaptor.capture(), paramsCaptor.capture());
        verify(mockHelper).queryGet(stringCaptor.capture(), paramsCaptor.capture(), eq(0), eq(50),
                eq(WeeklyAdherenceReport.class));
        
        assertEquals(stringCaptor.getAllValues().get(0), SELECT_COUNT + NO_LABELS_SQL);
        assertEquals(stringCaptor.getAllValues().get(1), SELECT_DISTINCT + NO_LABELS_SQL);
    }

    @Test
    public void getWeeklyAdherenceReports_withoutAdherenceMinNoMax() {
        AdherenceReportSearch search = new AdherenceReportSearch();
        search.setAdherenceMin(10);

        dao.getWeeklyAdherenceReports(TEST_APP_ID, TEST_STUDY_ID, search);
        
        verify(mockHelper).queryCount(stringCaptor.capture(), paramsCaptor.capture());
        verify(mockHelper).queryGet(stringCaptor.capture(), paramsCaptor.capture(), eq(0), eq(API_DEFAULT_PAGE_SIZE),
                eq(WeeklyAdherenceReport.class));
        
        assertEquals(stringCaptor.getAllValues().get(0), SELECT_COUNT + MIN_NO_MAX_SQL);
        assertEquals(stringCaptor.getAllValues().get(1), SELECT_DISTINCT + MIN_NO_MAX_SQL);
        assertEquals(paramsCaptor.getValue().get(ADHERENCE_MIN_FIELD), 10);
    }

    @Test
    public void getWeeklyAdherenceReports_withoutAdherenceMaxNoMin() {
        AdherenceReportSearch search = new AdherenceReportSearch();
        search.setAdherenceMax(90);

        dao.getWeeklyAdherenceReports(TEST_APP_ID, TEST_STUDY_ID, search);
        
        verify(mockHelper).queryCount(stringCaptor.capture(), paramsCaptor.capture());
        verify(mockHelper).queryGet(stringCaptor.capture(), paramsCaptor.capture(), eq(0), eq(API_DEFAULT_PAGE_SIZE),
                eq(WeeklyAdherenceReport.class));
        
        assertEquals(stringCaptor.getAllValues().get(0), SELECT_COUNT + MAX_NO_MIN_SQL);
        assertEquals(stringCaptor.getAllValues().get(1), SELECT_DISTINCT +  MAX_NO_MIN_SQL);
        assertEquals(paramsCaptor.getValue().get(ADHERENCE_MAX_FIELD), 90);
    }

    @Test
    public void getWeeklyAdherenceReports_idFilter() {
        AdherenceReportSearch search = new AdherenceReportSearch();
        search.setIdFilter("anId");

        dao.getWeeklyAdherenceReports(TEST_APP_ID, TEST_STUDY_ID, search);
        
        verify(mockHelper).queryCount(stringCaptor.capture(), paramsCaptor.capture());
        verify(mockHelper).queryGet(stringCaptor.capture(), paramsCaptor.capture(), eq(0), eq(API_DEFAULT_PAGE_SIZE),
                eq(WeeklyAdherenceReport.class));
        
        assertEquals(stringCaptor.getAllValues().get(0), SELECT_COUNT + ID_FILTER);
        assertEquals(stringCaptor.getAllValues().get(1), SELECT_DISTINCT + ID_FILTER);
        assertEquals(paramsCaptor.getValue().get(ID_FILTER_FIELD), "%anId%");
    }

    @Test
    public void getWeeklyAdherenceReports_progressionFilter() {
        AdherenceReportSearch search = new AdherenceReportSearch();
        search.setProgressionFilter(ParticipantProgressionState.UNSTARTED);

        dao.getWeeklyAdherenceReports(TEST_APP_ID, TEST_STUDY_ID, search);
        
        verify(mockHelper).queryCount(stringCaptor.capture(), paramsCaptor.capture());
        verify(mockHelper).queryGet(stringCaptor.capture(), paramsCaptor.capture(), eq(0), eq(API_DEFAULT_PAGE_SIZE),
                eq(WeeklyAdherenceReport.class));
        
        assertEquals(stringCaptor.getAllValues().get(0), SELECT_COUNT + PROGRESSION_FILTER);
        assertEquals(stringCaptor.getAllValues().get(1), SELECT_DISTINCT + PROGRESSION_FILTER);
        assertEquals(paramsCaptor.getValue().get(PROGRESSION_FIELD), ParticipantProgressionState.UNSTARTED);
    }
}
