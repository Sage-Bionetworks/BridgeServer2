package org.sagebionetworks.bridge.upload;

import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.HEALTH_CODE;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.studies.Enrollment;
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.services.ParticipantService;

public class TranscribeConsentHandlerTest {
    private static final long MOCK_NOW_MILLIS = DateTime.parse("2019-07-25T15:22:42.531-0700").getMillis();
    private static final DateTime STUDY_START_TIME = DateTime.parse("2019-07-21T22:28:31.648-0700");
    private static final Set<String> TEST_USER_GROUPS = ImmutableSet.of("test-group1","test-group2");

    private static final AccountId ACCOUNT_ID = AccountId.forHealthCode(TEST_APP_ID, HEALTH_CODE);

    @Mock
    private AccountService mockAccountService;
    
    @Mock
    private Account mockAccount;

    @Mock
    private ParticipantService participantService;

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

        // Set up mocks.
        when(mockAccountService.getAccountNoFilter(ACCOUNT_ID)).thenReturn(Optional.of(mockAccount));
        when(participantService.getStudyStartTime(mockAccount)).thenReturn(STUDY_START_TIME);

        // Set up input record and context. Handler expects Health Code and RecordBuilder.
        inputRecord = HealthDataRecord.create();
        context = new UploadValidationContext();
        context.setAppId(TEST_APP_ID);
        context.setHealthCode(HEALTH_CODE);
        context.setHealthDataRecord(inputRecord);
    }

    @Test
    public void test() {
        Enrollment en1 = Enrollment.create(TEST_APP_ID, "subA", "id1");
        Enrollment en2 = Enrollment.create(TEST_APP_ID, "subB", "id2", "extB");
        
        when(mockAccount.getSharingScope()).thenReturn(SharingScope.SPONSORS_AND_PARTNERS);
        when(mockAccount.getDataGroups()).thenReturn(TEST_USER_GROUPS);
        when(mockAccount.getActiveEnrollments()).thenReturn(ImmutableSet.of(en1, en2));

        handler.handle(context);
        HealthDataRecord outputRecord = context.getHealthDataRecord();

        assertSame(outputRecord, inputRecord);
        assertEquals(outputRecord.getUserSharingScope(), SharingScope.SPONSORS_AND_PARTNERS);
        // For backwards compatibility we take one external ID from the Enrollment records and 
        // put it in this field. In 99.9% of our cases right now, it's the same as what was in externalId.
        assertEquals(outputRecord.getUserExternalId(), "extB");
        assertEquals(outputRecord.getUserDataGroups(), Sets.newHashSet("test-group1","test-group2"));
        
        Map<String,String> studyMemberships = outputRecord.getUserStudyMemberships();
        assertEquals(studyMemberships.get("subA"), "<none>");
        assertEquals(studyMemberships.get("subB"), "extB");

        assertEquals(outputRecord.getDayInStudy().intValue(), 5);
    }

    @Test
    public void testNoParticipantOptions() {
        // account is null
        when(mockAccountService.getAccountNoFilter(ACCOUNT_ID)).thenReturn(Optional.empty());

        handler.handle(context);
        HealthDataRecord outputRecord = context.getHealthDataRecord();

        assertSame(outputRecord, inputRecord);
        assertEquals(outputRecord.getUserSharingScope(), SharingScope.NO_SHARING);
        assertNull(outputRecord.getUserExternalId());
        assertNull(outputRecord.getUserDataGroups());
        assertNull(outputRecord.getUserStudyMemberships());
    }

    @Test
    public void emptyStringSetConvertedCorrectly() {
        when(mockAccount.getDataGroups()).thenReturn(ImmutableSet.of());

        handler.handle(context);
        HealthDataRecord outputRecord = context.getHealthDataRecord();

        assertNull(outputRecord.getUserDataGroups());
    }
}
