package org.sagebionetworks.bridge.models.schedules2.adherence.weekly;

import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import org.mockito.Mockito;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceState;
import org.testng.annotations.Test;

public class WeeklyAdherenceReportGeneratorTest extends Mockito {
    
    @Test
    public void canSerialize() { 
        AdherenceState state = TestUtils.getAdherenceStateBuilder().withShowActive(false).build();
        WeeklyAdherenceReport report = WeeklyAdherenceReportGenerator.INSTANCE.generate(state);
    }
    
    @Test
    public void testNulls() {
        AdherenceState state = new AdherenceState.Builder()
                .withNow(CREATED_ON)
                .build();
        WeeklyAdherenceReport report = WeeklyAdherenceReportGenerator.INSTANCE.generate(state);
        
        assertEquals(report.getTimestamp(), CREATED_ON);
        assertEquals(report.getWeeklyAdherencePercent(), 100);
        assertTrue(report.getByDayEntries().isEmpty());
    }
}
