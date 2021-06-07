package org.sagebionetworks.bridge.models.schedules2.timelines;

import static java.util.stream.Collectors.toSet;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Optional;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.joda.time.DateTime;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.services.Schedule2Service;

public class MetadataContainerTest extends Mockito {
    
    @Mock
    Schedule2Service mockScheduleService;
    
    MetadataContainer container;
    
    @BeforeMethod
    public void beforeMethod() { 
        MockitoAnnotations.initMocks(this);
    }
    
    private MetadataContainer createContainer(List<AdherenceRecord> records) {
        return new MetadataContainer(mockScheduleService, records);
    }
    
    @Test
    public void constructorLoadsMetadataAndCategorizesRecords() {
        List<AdherenceRecord> records = ImmutableList.of(
            ar("AAA", "session1", CREATED_ON, MODIFIED_ON),
            ar(null, "session1", CREATED_ON, MODIFIED_ON),
            ar("BBB", "session2", CREATED_ON, MODIFIED_ON),
            ar("CCC", "session2", CREATED_ON, MODIFIED_ON),
            ar(null, "session2", CREATED_ON, MODIFIED_ON)
        );
        MetadataContainer container = createContainer(records);
        
        assertEquals(container.getSessionUpdates().size(), 2);
        assertEquals(container.getSessionUpdates().stream()
                .map(AdherenceRecord::getInstanceGuid)
                .collect(toSet()), ImmutableSet.of("session1", "session2"));
        assertEquals(container.getAssessments().size(), 3);
        assertEquals(container.getAssessments().stream()
                .map(AdherenceRecord::getInstanceGuid)
                .collect(toSet()), ImmutableSet.of("AAA", "BBB", "CCC"));
        
        verify(mockScheduleService).getTimelineMetadata("AAA");
        verify(mockScheduleService).getTimelineMetadata("BBB");
        verify(mockScheduleService).getTimelineMetadata("CCC");
        verify(mockScheduleService).getTimelineMetadata("session1");
        verify(mockScheduleService).getTimelineMetadata("session2");

        assertNotNull(container.getMetadata("AAA"));
        assertNotNull(container.getMetadata("BBB"));
        assertNotNull(container.getMetadata("CCC"));
        assertNotNull(container.getMetadata("session1"));
        assertNotNull(container.getMetadata("session2"));
    }
    
    @Test
    public void constructorSkipsRecordsWithoutMetadata() { 
        List<AdherenceRecord> records = ImmutableList.of(
            ar("AAA", "session1", CREATED_ON, MODIFIED_ON)
        );
        // Nothing found however, when we go to look for it
        reset(mockScheduleService);
        
        MetadataContainer container = createContainer(records);
        assertTrue(container.getAssessments().isEmpty());
    }
    
    @Test
    public void addRecord_addPersistentAssessmentAdherenceRecord() { 
        AdherenceRecord record = new AdherenceRecord();
        record.setStartedOn(TIMESTAMP);
        record.setInstanceGuid("instanceGuid");
        
        TimelineMetadata meta = new TimelineMetadata();
        meta.setTimeWindowPersistent(true);
        meta.setAssessmentInstanceGuid("assessmentInstanceGuid");
        
        when(mockScheduleService.getTimelineMetadata("instanceGuid"))
            .thenReturn(Optional.of(meta));
        
        MetadataContainer container = createContainer(ImmutableList.of(record));
        container.addRecord(record);
        
        assertEquals(record, container.getRecord("instanceGuid"));
        assertEquals(meta, container.getMetadata("instanceGuid"));
        assertEquals(record.getInstanceTimestamp(), TIMESTAMP);
        assertFalse(container.getAssessments().isEmpty());
        assertTrue(container.getSessionUpdates().isEmpty());
    }
    
    @Test
    public void addRecord_addPersistentSessionAdherenceRecord() {
        AdherenceRecord record = new AdherenceRecord();
        record.setStartedOn(TIMESTAMP);
        record.setInstanceGuid("instanceGuid");
        
        TimelineMetadata meta = new TimelineMetadata();
        meta.setTimeWindowPersistent(true);
        meta.setGuid("guid");
        
        when(mockScheduleService.getTimelineMetadata("instanceGuid"))
            .thenReturn(Optional.of(meta));
        
        MetadataContainer container = createContainer(ImmutableList.of(record));
        container.addRecord(record);
        
        assertEquals(record, container.getRecord("instanceGuid"));
        assertEquals(meta, container.getMetadata("instanceGuid"));
        assertEquals(record.getInstanceTimestamp(), TIMESTAMP);
        assertTrue(container.getAssessments().isEmpty());
        assertFalse(container.getSessionUpdates().isEmpty());
    }

    
    @Test
    public void addRecord_addNonPersistentAssessmentAdherenceRecord() { 
        AdherenceRecord record = new AdherenceRecord();
        record.setEventTimestamp(TIMESTAMP);
        record.setInstanceGuid("instanceGuid");
        
        TimelineMetadata meta = new TimelineMetadata();
        meta.setTimeWindowPersistent(false);
        meta.setAssessmentInstanceGuid("assessmentInstanceGuid");
        
        when(mockScheduleService.getTimelineMetadata("instanceGuid"))
            .thenReturn(Optional.of(meta));
        
        MetadataContainer container = createContainer(ImmutableList.of(record));
        container.addRecord(record);
        
        assertEquals(record, container.getRecord("instanceGuid"));
        assertEquals(meta, container.getMetadata("instanceGuid"));
        assertEquals(record.getInstanceTimestamp(), TIMESTAMP);
        assertFalse(container.getAssessments().isEmpty());
        assertTrue(container.getSessionUpdates().isEmpty());
    }
    
    @Test
    public void addRecord_addNonPersistentSessionAdherenceRecord() { 
        AdherenceRecord record = new AdherenceRecord();
        record.setEventTimestamp(TIMESTAMP);
        record.setInstanceGuid("instanceGuid");
        
        TimelineMetadata meta = new TimelineMetadata();
        meta.setTimeWindowPersistent(false);
        
        when(mockScheduleService.getTimelineMetadata("instanceGuid"))
            .thenReturn(Optional.of(meta));
        
        MetadataContainer container = createContainer(ImmutableList.of(record));
        container.addRecord(record);
        
        assertEquals(record, container.getRecord("instanceGuid"));
        assertEquals(meta, container.getMetadata("instanceGuid"));
        assertEquals(record.getInstanceTimestamp(), TIMESTAMP);
        assertTrue(container.getAssessments().isEmpty());
        assertFalse(container.getSessionUpdates().isEmpty());
    }
    
    @Test
    public void addSessionWorks() {
        MetadataContainer container = createContainer(ImmutableList.of());
        container.addRecord(ar(null, "session3", CREATED_ON, MODIFIED_ON));
        
        assertEquals(container.getSessionUpdates().size(), 1);
        assertEquals(container.getSessionUpdates().stream()
                .map(AdherenceRecord::getInstanceGuid)
                .collect(toSet()), ImmutableSet.of("session3"));
    }
    
    @Test
    public void getRecordWorks() {
        MetadataContainer container = createContainer(
            ImmutableList.of(ar("AAA", "session1", CREATED_ON, MODIFIED_ON)));
    
        assertNotNull(container.getRecord("AAA"));
        assertNull(container.getRecord("DDD"));
    }

    @Test
    public void getMetadataWorks() {
        MetadataContainer container = createContainer(
                ImmutableList.of(ar(null, "session1", CREATED_ON, MODIFIED_ON)));
        
        TimelineMetadata meta = container.getMetadata("session1");
        assertEquals(meta.getSessionInstanceGuid(), "session1");
        
        verify(mockScheduleService).getTimelineMetadata("session1");
    }

    @Test
    public void getMetadataLoadsMetadata() {
        MetadataContainer container = createContainer(ImmutableList.of());
        
        container.addRecord(ar(null, "session1", CREATED_ON, MODIFIED_ON));
        
        TimelineMetadata meta = container.getMetadata("session1");
        assertEquals(meta.getSessionInstanceGuid(), "session1");
        
        verify(mockScheduleService).getTimelineMetadata("session1");
    }
    
    private AdherenceRecord ar(String asmtInstanceGuid, String sessionInstanceGuid, DateTime startedOn, DateTime finishedOn) {
        AdherenceRecord record = new AdherenceRecord();
        record.setAppId(TEST_APP_ID);
        record.setUserId(TEST_USER_ID);
        record.setStudyId(TEST_STUDY_ID);
        record.setStartedOn(startedOn);
        record.setFinishedOn(finishedOn);
        record.setEventTimestamp(TIMESTAMP);
        
        TimelineMetadata meta = new TimelineMetadata();
        meta.setAppId(TEST_APP_ID);
        meta.setSessionStartEventId("enrollment");
        if (asmtInstanceGuid == null) {
            // Session
            record.setInstanceGuid(sessionInstanceGuid);
            meta.setGuid(sessionInstanceGuid);
            meta.setSessionInstanceGuid(sessionInstanceGuid);
            when(mockScheduleService.getTimelineMetadata(sessionInstanceGuid))
                .thenReturn(Optional.of(meta));
        } else {
            // Assessment
            record.setInstanceGuid(asmtInstanceGuid);
            meta.setGuid(asmtInstanceGuid);
            meta.setSessionInstanceGuid(sessionInstanceGuid);
            meta.setAssessmentInstanceGuid(asmtInstanceGuid);
            when(mockScheduleService.getTimelineMetadata(asmtInstanceGuid))
                .thenReturn(Optional.of(meta));
        }
        return record;
    }
}
