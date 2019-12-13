package org.sagebionetworks.bridge.upload;

import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.HEALTH_CODE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

import java.util.Map;
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

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.substudies.AccountSubstudy;
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.services.ParticipantService;

public class TranscribeConsentHandlerTest {
    private static final long MOCK_NOW_MILLIS = DateTime.parse("2019-07-25T15:22:42.531-0700").getMillis();
    private static final DateTime STUDY_START_TIME = DateTime.parse("2019-07-21T22:28:31.648-0700");
    private static final Set<String> TEST_USER_GROUPS = ImmutableSet.of("test-group1","test-group2");

    private static final AccountId ACCOUNT_ID = AccountId.forHealthCode(TestConstants.TEST_STUDY_IDENTIFIER,
            HEALTH_CODE);

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
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(mockAccount);
        when(participantService.getStudyStartTime(ACCOUNT_ID)).thenReturn(STUDY_START_TIME);

        // Set up input record and context. Handler expects Health Code and RecordBuilder.
        inputRecord = HealthDataRecord.create();
        context = new UploadValidationContext();
        context.setStudy(TestConstants.TEST_STUDY);
        context.setHealthCode(HEALTH_CODE);
        context.setHealthDataRecord(inputRecord);
    }

    @Test
    public void test() {
        AccountSubstudy as1 = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "subA",
                "id1");
        AccountSubstudy as2 = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "subB",
                "id2");
        as2.setExternalId("extB");
        
        when(mockAccount.getSharingScope()).thenReturn(SharingScope.SPONSORS_AND_PARTNERS);
        when(mockAccount.getDataGroups()).thenReturn(TEST_USER_GROUPS);
        when(mockAccount.getAccountSubstudies()).thenReturn(ImmutableSet.of(as1, as2));

        handler.handle(context);
        HealthDataRecord outputRecord = context.getHealthDataRecord();

        assertSame(outputRecord, inputRecord);
        assertEquals(outputRecord.getUserSharingScope(), SharingScope.SPONSORS_AND_PARTNERS);
        // For backwards compatibility we take one external ID from the AccountSubstudy records and 
        // put it in this field. In 99.9% of our cases right now, it's the same as what was in externalId.
        assertEquals(outputRecord.getUserExternalId(), "extB");
        assertEquals(outputRecord.getUserDataGroups(), Sets.newHashSet("test-group1","test-group2"));
        
        Map<String,String> substudyMemberships = outputRecord.getUserSubstudyMemberships();
        assertEquals(substudyMemberships.get("subA"), "<none>");
        assertEquals(substudyMemberships.get("subB"), "extB");

        assertEquals(outputRecord.getDayInStudy().intValue(), 5);
    }

    @Test
    public void testNoParticipantOptions() {
        // account is null
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(null);

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

        handler.handle(context);
        HealthDataRecord outputRecord = context.getHealthDataRecord();

        assertNull(outputRecord.getUserDataGroups());
    }
}
