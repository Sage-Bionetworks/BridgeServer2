package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.validators.AdherenceRecordsSearchValidator.AFTER_LATEST_DATETIME_ERROR;
import static org.sagebionetworks.bridge.validators.AdherenceRecordsSearchValidator.ASSESSMENT_IDS_FIELD;
import static org.sagebionetworks.bridge.validators.AdherenceRecordsSearchValidator.BEFORE_EARLIEST_DATETIME_ERROR;
import static org.sagebionetworks.bridge.validators.AdherenceRecordsSearchValidator.DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.validators.AdherenceRecordsSearchValidator.EARLIEST_DATETIME;
import static org.sagebionetworks.bridge.validators.AdherenceRecordsSearchValidator.END_TIME_BEFORE_START_TIME;
import static org.sagebionetworks.bridge.validators.AdherenceRecordsSearchValidator.END_TIME_FIELD;
import static org.sagebionetworks.bridge.validators.AdherenceRecordsSearchValidator.EVENT_TIMESTAMPS_FIELD;
import static org.sagebionetworks.bridge.validators.AdherenceRecordsSearchValidator.INSTANCE;
import static org.sagebionetworks.bridge.validators.AdherenceRecordsSearchValidator.INSTANCE_GUIDS_FIELD;
import static org.sagebionetworks.bridge.validators.AdherenceRecordsSearchValidator.LATEST_DATETIME;
import static org.sagebionetworks.bridge.validators.AdherenceRecordsSearchValidator.MAX_MAP_SIZE;
import static org.sagebionetworks.bridge.validators.AdherenceRecordsSearchValidator.MAX_MAP_SIZE_ERROR;
import static org.sagebionetworks.bridge.validators.AdherenceRecordsSearchValidator.MAX_PAGE_SIZE;
import static org.sagebionetworks.bridge.validators.AdherenceRecordsSearchValidator.MAX_SET_SIZE;
import static org.sagebionetworks.bridge.validators.AdherenceRecordsSearchValidator.MAX_SET_SIZE_ERROR;
import static org.sagebionetworks.bridge.validators.AdherenceRecordsSearchValidator.OFFSET_BY_FIELD;
import static org.sagebionetworks.bridge.validators.AdherenceRecordsSearchValidator.PAGE_SIZE_ERROR;
import static org.sagebionetworks.bridge.validators.AdherenceRecordsSearchValidator.PAGE_SIZE_FIELD;
import static org.sagebionetworks.bridge.validators.AdherenceRecordsSearchValidator.SESSION_GUIDS_FIELD;
import static org.sagebionetworks.bridge.validators.AdherenceRecordsSearchValidator.START_TIME_FIELD;
import static org.sagebionetworks.bridge.validators.AdherenceRecordsSearchValidator.START_TIME_MISSING;
import static org.sagebionetworks.bridge.validators.AdherenceRecordsSearchValidator.STUDY_ID_FIELD;
import static org.sagebionetworks.bridge.validators.AdherenceRecordsSearchValidator.TIME_WINDOW_GUIDS_FIELD;
import static org.sagebionetworks.bridge.validators.AdherenceRecordsSearchValidator.USER_ID_FIELD;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NEGATIVE;
import static org.testng.Assert.assertEquals;

import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.joda.time.DateTime;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordsSearch;
import org.sagebionetworks.bridge.models.schedules2.adherence.SortOrder;

public class AdherenceRecordsSearchValidatorTest extends Mockito {

    @Test
    public void valid() {
        AdherenceRecordsSearch search = search().build();
        Validate.entityThrowingException(INSTANCE, search);
    }
    
    @Test
    public void setsDefaults() {
        AdherenceRecordsSearch search = new AdherenceRecordsSearch.Builder().build();
        assertEquals(search.getInstanceGuids(), ImmutableSet.of());
        assertEquals(search.getAssessmentIds(), ImmutableSet.of());
        assertEquals(search.getSessionGuids(), ImmutableSet.of());
        assertEquals(search.getTimeWindowGuids(), ImmutableSet.of());
        assertEquals(search.getEventTimestamps(), ImmutableMap.of());
        assertEquals(search.getIncludeRepeats(), Boolean.TRUE);
        assertEquals(search.getOffsetBy(), Integer.valueOf(0));
        assertEquals(search.getPageSize(), Integer.valueOf(DEFAULT_PAGE_SIZE));
        assertEquals(search.getSortOrder(), SortOrder.ASC);
    }
    
    @Test
    public void userIdBlank() {
        AdherenceRecordsSearch search = search().withUserId("").build();
        assertValidatorMessage(INSTANCE, search, USER_ID_FIELD, CANNOT_BE_BLANK);
    }
    
    @Test
    public void userIdNull() {
        AdherenceRecordsSearch search = search().withUserId(null).build();
        assertValidatorMessage(INSTANCE, search, USER_ID_FIELD, CANNOT_BE_BLANK);
    }
    
    @Test
    public void studyIdBlank() {
        AdherenceRecordsSearch search = search().withStudyId("").build();
        assertValidatorMessage(INSTANCE, search, STUDY_ID_FIELD, CANNOT_BE_BLANK);
    }
    
    @Test
    public void studyIdNull() {
        AdherenceRecordsSearch search = search().withStudyId(null).build();
        assertValidatorMessage(INSTANCE, search, STUDY_ID_FIELD, CANNOT_BE_BLANK);
    }
    
    @Test
    public void assessmentIdsExceedsMax() {
        AdherenceRecordsSearch search = search()
                .withAssessmentIds(makeLargeSet()).build();
        assertValidatorMessage(INSTANCE, search, ASSESSMENT_IDS_FIELD, MAX_SET_SIZE_ERROR);
    }
    
    @Test
    public void sessionGuidsExceedsMax() {
        AdherenceRecordsSearch search = search()
                .withSessionGuids(makeLargeSet()).build();
        assertValidatorMessage(INSTANCE, search, SESSION_GUIDS_FIELD, MAX_SET_SIZE_ERROR);
    }
    
    @Test
    public void instanceGuidsExceedsMax() {
        AdherenceRecordsSearch search = search()
                .withInstanceGuids(makeLargeSet()).build();
        assertValidatorMessage(INSTANCE, search, INSTANCE_GUIDS_FIELD, MAX_SET_SIZE_ERROR);
    }
    
    @Test
    public void timeWindowGuidsExceedsMax() {
        AdherenceRecordsSearch search = search()
                .withTimeWindowGuids(makeLargeSet()).build();
        assertValidatorMessage(INSTANCE, search, TIME_WINDOW_GUIDS_FIELD, MAX_SET_SIZE_ERROR);
    }
    
    @Test
    public void eventTimestampsExceedsMax() {
        Map<String, DateTime> map = Maps.newHashMapWithExpectedSize(MAX_MAP_SIZE+1);
        for (int i=0; i < (MAX_MAP_SIZE+1); i++) {
            map.put("A"+i, CREATED_ON);
        }
        AdherenceRecordsSearch search = search()
                .withEventTimestamps(map).build();
        assertValidatorMessage(INSTANCE, search, EVENT_TIMESTAMPS_FIELD, MAX_MAP_SIZE_ERROR);
    }
    
    @Test
    public void startTimeWithoutEndTime() {
        AdherenceRecordsSearch search = search()
                .withStartTime(CREATED_ON).build();
        assertValidatorMessage(INSTANCE, search, END_TIME_FIELD, "must be provided if startTime is provided");
    }
    
    @Test
    public void endTimeWithoutStartTime() {
        AdherenceRecordsSearch search = search()
                .withEndTime(MODIFIED_ON).build();
        assertValidatorMessage(INSTANCE, search, START_TIME_FIELD, START_TIME_MISSING);
    }
    
    @Test
    public void startTimeBeforeEarliestTime() {
        AdherenceRecordsSearch search = search()
                .withStartTime(EARLIEST_DATETIME.minusMinutes(1))
                .withEndTime(LATEST_DATETIME).build();
        assertValidatorMessage(INSTANCE, search, START_TIME_FIELD, BEFORE_EARLIEST_DATETIME_ERROR);
    }
    
    @Test
    public void endTimeAfterLatestTime() {
        AdherenceRecordsSearch search = search()
                .withStartTime(EARLIEST_DATETIME)
                .withEndTime(LATEST_DATETIME.plusMinutes(1)).build();
        assertValidatorMessage(INSTANCE, search, END_TIME_FIELD, AFTER_LATEST_DATETIME_ERROR);
    }

    @Test
    public void endTimeBeforeStartTime() {
        AdherenceRecordsSearch search = search()
                .withStartTime(MODIFIED_ON)
                .withEndTime(CREATED_ON).build();
        assertValidatorMessage(INSTANCE, search, END_TIME_FIELD, END_TIME_BEFORE_START_TIME);
    }
    
    @Test
    public void offsetNegative() {
        AdherenceRecordsSearch search = search()
                .withOffsetBy(-3).build();
        assertValidatorMessage(INSTANCE, search, OFFSET_BY_FIELD, CANNOT_BE_NEGATIVE);
    }
    
    @Test
    public void pageSizeTooSmall() {
        AdherenceRecordsSearch search = search()
                .withPageSize(0).build();
        assertValidatorMessage(INSTANCE, search, PAGE_SIZE_FIELD, PAGE_SIZE_ERROR);
    }
    
    @Test
    public void pageSizeTooLarge() {
        AdherenceRecordsSearch search = search()
                .withPageSize(MAX_PAGE_SIZE+1).build();
        assertValidatorMessage(INSTANCE, search, PAGE_SIZE_FIELD, PAGE_SIZE_ERROR);
    }
    
    private Set<String> makeLargeSet() {
        Set<String> set = Sets.newHashSetWithExpectedSize(MAX_SET_SIZE+1);
        for (int i=0; i < (MAX_SET_SIZE+1); i++) {
            set.add("A"+i);
        }
        return set;
    }
    
    private AdherenceRecordsSearch.Builder search() {
        return new AdherenceRecordsSearch.Builder()
                .withUserId(TEST_USER_ID)
                .withStudyId(TEST_STUDY_ID);
    }
}
