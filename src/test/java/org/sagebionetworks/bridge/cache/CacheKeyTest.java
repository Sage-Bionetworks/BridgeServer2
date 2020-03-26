package org.sagebionetworks.bridge.cache;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.ThrottleRequestType;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import nl.jqno.equalsverifier.EqualsVerifier;

public class CacheKeyTest {
    
    private static final SubpopulationGuid SUBPOP_GUID = SubpopulationGuid.create("guid");
    
    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(CacheKey.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test(expectedExceptions = NullPointerException.class)
    public void nullsRejected() {
        CacheKey.appConfigList(null);
    }
    
    @Test
    public void tagList() { 
        assertEquals(CacheKey.tagList().toString(), "TagList");
    }
    
    @Test
    public void reauthTokenLookupKey() {
        assertEquals(CacheKey.reauthTokenLookupKey("ABC", TestConstants.TEST_STUDY).toString(), "ABC:api:ReauthToken");
    }
    
    @Test
    public void shortenUrl() {
        assertEquals(CacheKey.shortenUrl("ABC").toString(), "ABC:ShortenedUrl");
    }
    
    @Test
    public void appConfigList() {
        assertEquals(CacheKey.appConfigList(TestConstants.TEST_STUDY).toString(), "api:AppConfigList");
    }

    @Test
    public void channelSignInToSessionToken() {
        assertEquals(CacheKey.channelSignInToSessionToken("ABC").toString(),
                "ABC:channel-signin-to-session-token");
    }

    @Test
    public void channelThrottling() {
        assertEquals(CacheKey.channelThrottling(ThrottleRequestType.EMAIL_SIGNIN, "userId").toString(),
                "userId:email_signin:channel-throttling");
    }
    
    @Test
    public void emailSignInRequest() {
        SignIn signIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
                .withEmail("email@email.com").build();
        assertEquals(CacheKey.emailSignInRequest(signIn).toString(), "email@email.com:api:signInRequest");
    }
    
    @Test
    public void emailVerification() {
        assertEquals(CacheKey.emailVerification("email@email.com").toString(), "email@email.com:emailVerificationStatus");
    }
    
    @Test
    public void itpWithPhone() {
        assertEquals(CacheKey.itp(SUBPOP_GUID, TestConstants.TEST_STUDY, TestConstants.PHONE).toString(),
                "guid:" + TestConstants.PHONE.getNumber() + ":api:itp");
    }
    
    @Test
    public void itpWithEmail() {
        assertEquals(CacheKey.itp(SUBPOP_GUID, TestConstants.TEST_STUDY, "email@email.com").toString(),
                "guid:email@email.com:api:itp");
    }
    
    @Test
    public void lock() {
        assertEquals(CacheKey.lock("value", String.class).toString(), "value:java.lang.String:lock");
    }
    
    @Test
    public void passwordResetForEmail() {
        assertEquals(CacheKey.passwordResetForEmail("sptoken", "api").toString(), "sptoken:api");
    }
    
    @Test
    public void passwordResetForPhone() {
        assertEquals(CacheKey.passwordResetForPhone("sptoken", TestConstants.PHONE.getNumber()).toString(),
                "sptoken:phone:" + TestConstants.PHONE.getNumber());
    }
    
    @Test
    public void phoneSignInRequest() {
        SignIn signIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
                .withPhone(TestConstants.PHONE).build();
        
        assertEquals(CacheKey.phoneSignInRequest(signIn).toString(),
                TestConstants.PHONE.getNumber() + ":api:phoneSignInRequest");
    }
    
    @Test
    public void requestInfo() {
        assertEquals(CacheKey.requestInfo("userId").toString(), "userId:request-info");
    }
    
    @Test
    public void study() {
        assertEquals(CacheKey.study("api").toString(), "api:study");
    }    
    
    @Test
    public void subpop() {
        assertEquals(CacheKey.subpop(SUBPOP_GUID, TestConstants.TEST_STUDY).toString(), "guid:api:Subpopulation");
    }
    
    @Test
    public void subpopList() {
        assertEquals(CacheKey.subpopList(TestConstants.TEST_STUDY).toString(), "api:SubpopulationList");
    }
    
    @Test
    public void verificationToken() {
        assertEquals(CacheKey.verificationToken("token").toString(), "token");
    }
    
    @Test
    public void viewKey() {
        assertEquals(CacheKey.viewKey(StringBuilder.class, "a", "b").toString(), "a:b:StringBuilder:view");
    }
    
    @Test
    public void userIdToSession() {
        assertEquals(CacheKey.userIdToSession("userId").toString(), "userId:session2:user");
    }
    
    @Test
    public void tokenToUserId() { 
        assertEquals(CacheKey.tokenToUserId("aSessionToken").toString(), "aSessionToken:session2");
    }
    
    @Test
    public void isPublic() {
        CacheKey privateKey = CacheKey.reauthTokenLookupKey("a", TestConstants.TEST_STUDY);
        assertFalse(CacheKey.isPublic(privateKey.toString()));
        
        CacheKey publicKey = CacheKey.study("studyId");
        assertTrue(CacheKey.isPublic(publicKey.toString()));
    }
}
