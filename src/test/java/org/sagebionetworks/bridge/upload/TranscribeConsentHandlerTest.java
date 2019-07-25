package org.sagebionetworks.bridge.upload;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.activities.ActivityEventObjectType;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.substudies.AccountSubstudy;
import org.sagebionetworks.bridge.services.ActivityEventService;

public class TranscribeConsentHandlerTest {
    private static final DateTime ACTIVITIES_RETRIEVED = DateTime.parse("2019-07-21T22:28:31.648-0700");
    private static final long MOCK_NOW_MILLIS = DateTime.parse("2019-07-25T15:22:42.531-0700").getMillis();
    private static final String TEST_HEALTHCODE = "test-healthcode";
    private static final String TEST_EXTERNAL_ID = "test-external-id";
    private static final Set<String> TEST_USER_GROUPS = ImmutableSet.of("test-group1","test-group2");

    @Mock
    private AccountDao mockAccountDao;
    
    @Mock
    private Account mockAccount;

    @Mock
    private ActivityEventService mockActivityEventService;

    @InjectMocks
    private TranscribeConsentHandler handler;

    private UploadValidationContext context;
    private HealthDataRecord inputRecord;

    @BeforeClass
    public static void mockNow() {
        DateTimeUtils.setCurrentMillisFixed(MOCK_NOW_MILLIS);
    }

    @AfterClass
    public static void unmockNow() {
        DateTimeUtils.setCurrentMillisSystem();
    }
    
    @BeforeMethod
    public void beforeMethod() { 
        MockitoAnnotations.initMocks(this);

        // By default, activity event service returns an empty map.
        when(mockActivityEventService.getActivityEventMap(any())).thenReturn(ImmutableMap.of());

        // Set up input record and context. Handler expects Health Code and RecordBuilder.
        inputRecord = HealthDataRecord.create();
        context = new UploadValidationContext();
        context.setStudy(TestConstants.TEST_STUDY);
        context.setHealthCode(TEST_HEALTHCODE);
        context.setHealthDataRecord(inputRecord);
    }

    @Test
    public void test() {
        AccountSubstudy as1 = AccountSubstudy.create("studyId", "subA", "id");
        AccountSubstudy as2 = AccountSubstudy.create("studyId", "subB", "id");
        as2.setExternalId("extB");
        
        when(mockAccount.getExternalId()).thenReturn(TEST_EXTERNAL_ID);
        when(mockAccount.getSharingScope()).thenReturn(SharingScope.SPONSORS_AND_PARTNERS);
        when(mockAccount.getDataGroups()).thenReturn(TEST_USER_GROUPS);
        when(mockAccount.getAccountSubstudies()).thenReturn(ImmutableSet.of(as1, as2));
        when(mockAccountDao.getAccount(any())).thenReturn(mockAccount);

        handler.handle(context);
        HealthDataRecord outputRecord = context.getHealthDataRecord();

        assertSame(outputRecord, inputRecord);
        assertEquals(outputRecord.getUserSharingScope(), SharingScope.SPONSORS_AND_PARTNERS);
        assertEquals(outputRecord.getUserExternalId(), TEST_EXTERNAL_ID);
        assertEquals(outputRecord.getUserDataGroups(), Sets.newHashSet("test-group1","test-group2"));
        
        Map<String,String> substudyMemberships = outputRecord.getUserSubstudyMemberships();
        assertEquals(substudyMemberships.get("subA"), "<none>");
        assertEquals(substudyMemberships.get("subB"), "extB");
    }

    @Test
    public void testNoParticipantOptions() {
        // account will return null for all values (nothing mocked)

        handler.handle(context);
        HealthDataRecord outputRecord = context.getHealthDataRecord();

        assertSame(outputRecord, inputRecord);
        assertEquals(outputRecord.getUserSharingScope(), SharingScope.NO_SHARING);
        assertNull(outputRecord.getUserExternalId());
        assertNull(outputRecord.getUserDataGroups());
        assertNull(outputRecord.getUserSubstudyMemberships());
    }

    @Test
    public void emptyStringSetConvertedCorrectly() {
        when(mockAccount.getDataGroups()).thenReturn(ImmutableSet.of());
        when(mockAccountDao.getAccount(any())).thenReturn(mockAccount);

        handler.handle(context);
        HealthDataRecord outputRecord = context.getHealthDataRecord();

        assertNull(outputRecord.getUserDataGroups());
    }

    @Test
    public void dayInStudy() {
        // Mock activity event service to return ACTIVITIES_RETRIEVED.
        when(mockActivityEventService.getActivityEventMap(TEST_HEALTHCODE)).thenReturn(ImmutableMap.of(
                ActivityEventObjectType.ACTIVITIES_RETRIEVED.name().toLowerCase(), ACTIVITIES_RETRIEVED));
        when(mockAccountDao.getAccount(any())).thenReturn(mockAccount);

        // Execute. Just check daysInStudy.
        handler.handle(context);
        HealthDataRecord outputRecord = context.getHealthDataRecord();
        assertEquals(outputRecord.getDayInStudy().intValue(), 5);
    }
}
