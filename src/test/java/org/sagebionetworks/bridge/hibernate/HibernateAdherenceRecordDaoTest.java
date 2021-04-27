package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordId;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordsSearch;

public class HibernateAdherenceRecordDaoTest extends Mockito {
    
    @Mock
    HibernateHelper mockHelper;
    
    @Captor
    ArgumentCaptor<AdherenceRecordId> recordIdCaptor;
    
    @InjectMocks
    HibernateAdherenceRecordDao dao;

    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
    }
    
    @Test
    public void get() {
        AdherenceRecord persisted = new AdherenceRecord();
        when(mockHelper.getById(eq(AdherenceRecord.class), any())).thenReturn(persisted);
        
        AdherenceRecord record = createRecord();
        
        AdherenceRecord retValue = dao.get(record);
        assertSame(persisted, retValue);
        
        verify(mockHelper).getById(eq(AdherenceRecord.class), recordIdCaptor.capture());
        AdherenceRecordId id = recordIdCaptor.getValue();
        assertEquals(id.getUserId(), TEST_USER_ID);
        assertEquals(id.getStudyId(), TEST_STUDY_ID);
        assertEquals(id.getInstanceGuid(), GUID);
        assertEquals(id.getStartedOn(), CREATED_ON);
    }
    
    @Test
    public void create() {
        AdherenceRecord record = createRecord();
        dao.create(record);
        verify(mockHelper).create(record);
    }
    
    @Test
    public void update() {
        AdherenceRecord record = createRecord();
        dao.update(record);
        verify(mockHelper).update(record);
    }
    
    @Test
    public void getAdherenceRecords() {
        AdherenceRecordsSearch search = new AdherenceRecordsSearch.Builder().build();
        
        PagedResourceList<AdherenceRecord> list = dao.getAdherenceRecords(search);
        
    }
    
    @Test
    public void createQuery_baseOnly() {
        AdherenceRecordsSearch search = new AdherenceRecordsSearch.Builder().build();
        
        QueryBuilder builder = dao.createQuery(search);
        assertEquals(builder.getQuery(), HibernateAdherenceRecordDao.BASE_QUERY + "ORDER BY ar.startedOn ASC");
    }
    
    private AdherenceRecord createRecord() { 
        AdherenceRecord record = new AdherenceRecord();
        record.setUserId(TEST_USER_ID);
        record.setStudyId(TEST_STUDY_ID);
        record.setInstanceGuid(GUID);
        record.setStartedOn(CREATED_ON);
        return record;
    }
    
    private AdherenceRecordsSearch.Builder search() {
        return new AdherenceRecordsSearch.Builder()
                .withUserId(TEST_USER_ID)
                .withStudyId(TEST_STUDY_ID);
    }
}
