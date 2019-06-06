package org.sagebionetworks.bridge.dynamodb;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

import java.util.List;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.google.common.collect.ImmutableList;

import org.joda.time.DateTime;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.subpopulations.StudyConsent;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

public class DynamoStudyConsentDaoTest extends Mockito {

    static final SubpopulationGuid SUBPOP_GUID = SubpopulationGuid.create("oneSubpopGuid");
    static final long CREATED_ON = DateTime.now().getMillis();
    static final String STORAGE_PATH = "storagePath";
    
    @InjectMocks
    DynamoStudyConsentDao dao;
    
    @Mock
    DynamoDBMapper mockMapper;
    
    @Mock
    QueryResultPage<DynamoStudyConsent1> mockQueryPage;
    
    @Mock
    PaginatedQueryList<DynamoStudyConsent1> mockQueryList;
    
    @Captor
    ArgumentCaptor<StudyConsent> consentCaptor;
    
    @Captor
    ArgumentCaptor<DynamoDBQueryExpression<DynamoStudyConsent1>> queryCaptor;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
    }
    
    @Test
    public void addConsent() {
        StudyConsent result = dao.addConsent(SUBPOP_GUID, STORAGE_PATH, CREATED_ON);
        assertEquals(result.getSubpopulationGuid(), SUBPOP_GUID.getGuid());
        assertEquals(result.getCreatedOn(), CREATED_ON);
        assertEquals(result.getStoragePath(), STORAGE_PATH);
        
        verify(mockMapper).save(consentCaptor.capture());
        StudyConsent consent = consentCaptor.getValue();
        assertEquals(consent.getSubpopulationGuid(), SUBPOP_GUID.getGuid());
        assertEquals(consent.getCreatedOn(), CREATED_ON);
        assertEquals(consent.getStoragePath(), STORAGE_PATH);
    }
    
    @Test
    public void getMostRecentConsent() {
        DynamoStudyConsent1 consent = new DynamoStudyConsent1();
        when(mockMapper.queryPage(eq(DynamoStudyConsent1.class), any())).thenReturn(mockQueryPage);
        when(mockQueryPage.getResults()).thenReturn(ImmutableList.of(consent));
        
        StudyConsent result = dao.getMostRecentConsent(SUBPOP_GUID);
        assertSame(result, consent);
        
        verify(mockMapper).queryPage(eq(DynamoStudyConsent1.class), queryCaptor.capture());
        DynamoDBQueryExpression<DynamoStudyConsent1> query = queryCaptor.getValue();
        
        assertEquals(query.getHashKeyValues().getSubpopulationGuid(), SUBPOP_GUID.getGuid());
        assertFalse(query.isScanIndexForward());
        assertEquals(query.getLimit(), new Integer(1));
    }
    
    @Test
    public void getMostRecentConsentNoRecord() {
        when(mockMapper.queryPage(eq(DynamoStudyConsent1.class), any())).thenReturn(mockQueryPage);
        when(mockQueryPage.getResults()).thenReturn(ImmutableList.of());
        
        StudyConsent result = dao.getMostRecentConsent(SUBPOP_GUID);
        assertNull(result);
    }
    
    @Test
    public void getConsent() {
        DynamoStudyConsent1 consent = new DynamoStudyConsent1();
        when(mockMapper.load(any())).thenReturn(consent);
        
        StudyConsent result = dao.getConsent(SUBPOP_GUID, CREATED_ON);
        assertSame(result, consent);
        
        verify(mockMapper).load(consentCaptor.capture());
        StudyConsent key = consentCaptor.getValue();
        assertEquals(key.getSubpopulationGuid(), SUBPOP_GUID.getGuid());
        assertEquals(key.getCreatedOn(), CREATED_ON);
    }

    @Test
    public void deleteConsentPermanently() {
        StudyConsent consent = StudyConsent.create();
        
        dao.deleteConsentPermanently(consent);

        verify(mockMapper).delete(consent);
    }

    @Test
    public void getConsents() {
        List<DynamoStudyConsent1> consentList = ImmutableList.of(new DynamoStudyConsent1(), new DynamoStudyConsent1());
        
        when(mockMapper.query(eq(DynamoStudyConsent1.class), any())).thenReturn(mockQueryList);
        when(mockQueryList.iterator()).thenReturn(consentList.iterator());
        
        List<StudyConsent> consents = dao.getConsents(SUBPOP_GUID);
        assertEquals(consents.size(), 2);
        
        verify(mockMapper).query(eq(DynamoStudyConsent1.class), queryCaptor.capture());
        DynamoDBQueryExpression<DynamoStudyConsent1> query = queryCaptor.getValue();
        
        assertEquals(query.getHashKeyValues().getSubpopulationGuid(), SUBPOP_GUID.getGuid());
        assertFalse(query.isScanIndexForward());
    }
}
