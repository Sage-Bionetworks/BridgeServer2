package org.sagebionetworks.bridge.models.activities;

import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.IMMUTABLE;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.MUTABLE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.List;

import org.sagebionetworks.bridge.models.schedules2.StudyBurst;
import org.sagebionetworks.bridge.models.studies.StudyCustomEvent;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

/**
 * Some activity events have user-defined identifiers, and we validate that the values being
 * supplied match one of these identifiers. This is a parameter object that provides all these
 * values to validation methods (they might grow or change over time).
 */
public class StudyActivityEventMapTest {
    
    @Test
    public void nullSafe() {
        StudyActivityEventMap map = new StudyActivityEventMap();
        
        assertFalse(map.hasBurstId("key"));
        assertFalse(map.hasCustomId("key"));
        assertNull(map.getCustomUpdateType("key"));
        assertNull(map.getBurstUpdateType("key"));
        
        map.addCustomEvents(null);
        map.addStudyBursts(null);

        assertFalse(map.hasBurstId("key"));
        assertFalse(map.hasCustomId("key"));
        assertNull(map.getCustomUpdateType("key"));
        assertNull(map.getBurstUpdateType("key"));
    }
    
    @Test
    public void works() {
        List<StudyCustomEvent> customEvents = ImmutableList.of(
                new StudyCustomEvent("event1", MUTABLE),
                new StudyCustomEvent("event2", IMMUTABLE));
        
        List<StudyBurst> studyBursts = ImmutableList.of(
                new StudyBurst("burst1", MUTABLE),
                new StudyBurst("burst2", IMMUTABLE));
        
        StudyActivityEventMap map = new StudyActivityEventMap();
        map.addCustomEvents(customEvents);
        map.addStudyBursts(studyBursts);
        
        assertTrue(map.hasCustomId("event1"));
        assertTrue(map.hasCustomId("event2"));
        assertFalse(map.hasBurstId("event1"));
        assertFalse(map.hasBurstId("event2"));

        assertTrue(map.hasBurstId("burst1"));
        assertTrue(map.hasBurstId("burst2"));
        assertFalse(map.hasCustomId("burst1"));
        assertFalse(map.hasCustomId("burst2"));
        
        assertEquals(map.getCustomUpdateType("event1"), MUTABLE);
        assertEquals(map.getCustomUpdateType("event2"), IMMUTABLE);
        assertEquals(map.getBurstUpdateType("burst1"), MUTABLE);
        assertEquals(map.getBurstUpdateType("burst2"), IMMUTABLE);
}

}
