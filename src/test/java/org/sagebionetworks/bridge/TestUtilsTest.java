package org.sagebionetworks.bridge;

import static org.sagebionetworks.bridge.models.OperatingSystem.ANDROID;
import static org.sagebionetworks.bridge.models.OperatingSystem.IOS;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.TIMELINE_RETRIEVED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

import java.util.HashSet;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.dynamodb.DynamoCriteria;
import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;

public class TestUtilsTest {

    private static final HashSet<String> ALL_OF_GROUPS = Sets.newHashSet("a","b");
    private static final HashSet<String> NONE_OF_GROUPS = Sets.newHashSet("c","d");
    
    @Test
    public void testAssertExceptionThrowsTheExceptionWithRightMessage() {
        TestUtils.assertException(EntityNotFoundException.class, "Account not found.", () -> {
            throw new EntityNotFoundException(Account.class);});
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void testAssertExceptionThrowsTheExceptionWithWrongMessage() {
        TestUtils.assertException(EntityNotFoundException.class, "Account not found", () -> {
            throw new EntityNotFoundException(AppConfig.class);});
    }
    
    @Test(expectedExceptions = ConstraintViolationException.class)
    public void testAssertThrowsExceptionUnrelatedException() {
        TestUtils.assertException(EntityNotFoundException.class, "Account not found", () -> {
            throw new ConstraintViolationException.Builder().build();});
    }
    
    @Test
    public void testAssertThrowsExceptionNoException() {
        try {
            TestUtils.assertException(Exception.class, "Any message at all", () -> {});
            fail("Should have thrown exception");
        } catch(Throwable e) {
            assertEquals(e.getMessage(), "Should have thrown exception: java.lang.Exception, message: 'Any message at all'");
        }
    }
    
    @Test
    public void createCriteriaWithArguments() {
        Criteria criteria = TestUtils.createCriteria(5, 15, ALL_OF_GROUPS, NONE_OF_GROUPS);

        assertEquals(criteria.getMinAppVersion(IOS), new Integer(5));
        assertEquals(criteria.getMaxAppVersion(IOS), new Integer(15));
        assertEquals(criteria.getAllOfGroups(), ALL_OF_GROUPS);
        assertEquals(criteria.getNoneOfGroups(), NONE_OF_GROUPS);
    }
    
    @Test
    public void copyWithNullObject() {
        Criteria newCriteria = TestUtils.copyCriteria(null);
        assertNotNull(newCriteria);
    }

    @Test
    public void copyWithCriteriaObject() {
        Criteria criteria = newCriteria();
        
        Criteria newCriteria = TestUtils.copyCriteria(criteria);
        assertEquals(newCriteria.getMinAppVersion(IOS), new Integer(5));
        assertEquals(newCriteria.getMaxAppVersion(IOS), new Integer(15));
        assertEquals(newCriteria.getMaxAppVersion(ANDROID), new Integer(12));
        assertEquals(newCriteria.getAllOfGroups(), ALL_OF_GROUPS);
        assertEquals(newCriteria.getNoneOfGroups(), NONE_OF_GROUPS);
        assertEquals(newCriteria.getAppVersionOperatingSystems(), Sets.newHashSet(IOS, ANDROID));
        assertFalse(criteria == newCriteria);
    }
    
    
    @Test
    public void findByEventType_emptyEvents() {
        List<StudyActivityEvent> events = ImmutableList.of();
        assertNull( TestUtils.findByEventId(events, TIMELINE_RETRIEVED) );
    }

    @Test
    public void findByEventType_eventMatches() {
        StudyActivityEvent event1 = new StudyActivityEvent();
        event1.setEventId("enrollment");
        
        StudyActivityEvent event2 = new StudyActivityEvent();
        event2.setEventId("timeline_retrieved");
        
        List<StudyActivityEvent> events = ImmutableList.of(event1, event2);
        
        assertEquals( TestUtils.findByEventId(events, TIMELINE_RETRIEVED), event2 );
    }
    
    private Criteria newCriteria() {
        // Don't use an interface method, that's what we're testing here.
        Criteria criteria = new DynamoCriteria();
        criteria.setMinAppVersion(IOS, 5);
        criteria.setMaxAppVersion(IOS, 15);
        criteria.setMaxAppVersion(ANDROID, 12);
        criteria.setAllOfGroups(ALL_OF_GROUPS);
        criteria.setNoneOfGroups(NONE_OF_GROUPS);
        return criteria;
    }
}
