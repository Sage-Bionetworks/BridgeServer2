package org.sagebionetworks.bridge.upload;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.substudies.AccountSubstudy;

public class TranscribeConsentHandlerTest {
    private static final String TEST_HEALTHCODE = "test-healthcode";
    private static final String TEST_EXTERNAL_ID = "test-external-id";
    private static final Set<String> TEST_USER_GROUPS = ImmutableSet.of("test-group1","test-group2");

    @Mock
    private AccountDao mockAccountDao;
    
    @Mock
    private Account mockAccount;
    
    @BeforeMethod
    public void beforeMethod() { 
        MockitoAnnotations.initMocks(this);
    }
    
    @Test
    public void test() {
        AccountSubstudy as1 = AccountSubstudy.create("studyId", "subA", "id");
        AccountSubstudy as2 = AccountSubstudy.create("studyId", "subB", "id");
        as2.setExternalId(TEST_EXTERNAL_ID);
        
        when(mockAccount.getSharingScope()).thenReturn(SharingScope.SPONSORS_AND_PARTNERS);
        when(mockAccount.getDataGroups()).thenReturn(TEST_USER_GROUPS);
        when(mockAccount.getAccountSubstudies()).thenReturn(ImmutableSet.of(as1, as2));
        when(mockAccountDao.getAccount(any())).thenReturn(mockAccount);
        
        HealthDataRecord record = HealthDataRecord.create();
        HealthDataRecord outputRecord = setupContextAndRunHandler(record);
        
        assertSame(outputRecord, record);
        assertEquals(outputRecord.getUserSharingScope(), SharingScope.SPONSORS_AND_PARTNERS);
        assertEquals(outputRecord.getUserExternalId(), TEST_EXTERNAL_ID);
        assertEquals(outputRecord.getUserDataGroups(), Sets.newHashSet("test-group1","test-group2"));
        
        Map<String,String> substudyMemberships = outputRecord.getUserSubstudyMemberships();
        assertEquals(substudyMemberships.get("subA"), "<none>");
        assertEquals(substudyMemberships.get("subB"), TEST_EXTERNAL_ID);
    }

    @Test
    public void testNoParticipantOptions() {
        // account will return null for all values (nothing mocked)

        HealthDataRecord record = HealthDataRecord.create();
        HealthDataRecord outputRecord = setupContextAndRunHandler(record);

        assertSame(outputRecord, record);
        assertEquals(outputRecord.getUserSharingScope(), SharingScope.NO_SHARING);
        assertNull(outputRecord.getUserExternalId());
        assertNull(outputRecord.getUserDataGroups());
        assertNull(outputRecord.getUserSubstudyMemberships());
    }

    @Test
    public void emptyStringSetConvertedCorrectly() {
        when(mockAccount.getDataGroups()).thenReturn(ImmutableSet.of());
        when(mockAccountDao.getAccount(any())).thenReturn(mockAccount);

        HealthDataRecord record = HealthDataRecord.create();
        HealthDataRecord outputRecord = setupContextAndRunHandler(record);
        
        assertNull(outputRecord.getUserDataGroups());
    }
    
    private HealthDataRecord setupContextAndRunHandler(HealthDataRecord record) {
        TranscribeConsentHandler handler = new TranscribeConsentHandler();
        handler.setAccountDao(mockAccountDao);

        // set up context - handler expects Health Code and RecordBuilder
        UploadValidationContext context = new UploadValidationContext();
        context.setStudy(TestConstants.TEST_STUDY);
        context.setHealthCode(TEST_HEALTHCODE);
        context.setHealthDataRecord(record);

        // execute
        handler.handle(context);
        return context.getHealthDataRecord();
    }
}
