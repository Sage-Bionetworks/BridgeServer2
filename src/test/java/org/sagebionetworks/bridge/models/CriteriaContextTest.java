package org.sagebionetworks.bridge.models;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.accounts.AccountId;

import static org.sagebionetworks.bridge.BridgeConstants.API_APP_ID;
import static org.testng.Assert.assertEquals;

import com.google.common.collect.ImmutableList;

import nl.jqno.equalsverifier.EqualsVerifier;

public class CriteriaContextTest {
    
    private static final ClientInfo CLIENT_INFO = ClientInfo.parseUserAgentString("app/20");
    private static final String USER_ID = "user-id";
    
    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(CriteriaContext.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void defaultsClientInfo() {
        CriteriaContext context = new CriteriaContext.Builder()
                .withUserId(USER_ID)
                .withStudyIdentifier(TestConstants.TEST_STUDY).build();
        assertEquals(context.getClientInfo(), ClientInfo.UNKNOWN_CLIENT);
        assertEquals(context.getLanguages(), ImmutableList.of());
        assertEquals(context.getUserDataGroups(), ImmutableList.of());
        assertEquals(context.getUserSubstudyIds(), ImmutableList.of());
    }
    
    @Test(expectedExceptions = NullPointerException.class)
    public void requiresStudyIdentifier() {
        new CriteriaContext.Builder().withUserId(USER_ID).build();
    }
    
    @Test
    public void builderWorks() {
        CriteriaContext context = new CriteriaContext.Builder()
                .withStudyIdentifier(TestConstants.TEST_STUDY)
                .withUserId(USER_ID)
                .withClientInfo(CLIENT_INFO)
                .withUserDataGroups(TestConstants.USER_DATA_GROUPS)
                .withUserSubstudyIds(TestConstants.USER_SUBSTUDY_IDS).build();
        
        // There are defaults
        assertEquals(context.getClientInfo(), CLIENT_INFO);
        assertEquals(context.getUserDataGroups(), TestConstants.USER_DATA_GROUPS);
        assertEquals(context.getUserSubstudyIds(), TestConstants.USER_SUBSTUDY_IDS);
        
        CriteriaContext copy = new CriteriaContext.Builder().withContext(context).build();
        assertEquals(copy.getClientInfo(), CLIENT_INFO);
        assertEquals(copy.getStudyIdentifier(), TestConstants.TEST_STUDY);
        assertEquals(copy.getUserId(), USER_ID);
        assertEquals(copy.getUserDataGroups(), TestConstants.USER_DATA_GROUPS);
        assertEquals(copy.getUserSubstudyIds(), TestConstants.USER_SUBSTUDY_IDS);
    }
    
    @Test
    public void contextHasAccountId() {
        CriteriaContext context = new CriteriaContext.Builder()
                .withStudyIdentifier(TestConstants.TEST_STUDY)
                .withUserId(USER_ID).build();
        
        AccountId accountId = context.getAccountId();
        assertEquals(accountId.getStudyId(), API_APP_ID);
        assertEquals(accountId.getId(), USER_ID);
    }
}
