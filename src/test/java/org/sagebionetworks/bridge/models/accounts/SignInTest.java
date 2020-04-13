package org.sagebionetworks.bridge.models.accounts;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import com.fasterxml.jackson.databind.JsonNode;

public class SignInTest {

    @Test
    public void canDeserializeOldJson() throws Exception {
        String oldJson = "{\"username\":\"aName\",\"password\":\"password\"}";

        SignIn signIn = BridgeObjectMapper.get().readValue(oldJson, SignIn.class);

        assertEquals(signIn.getEmail(), "aName");
        assertEquals(signIn.getPassword(), "password");
    }
    
    @Test
    public void canDeserialize() throws Exception {
        String json = TestUtils.createJson("{'study':'foo','email':'aName','password':'password','token':'ABC'}");

        SignIn signIn = BridgeObjectMapper.get().readValue(json, SignIn.class);

        assertEquals(signIn.getStudyId(), "foo");
        assertEquals(signIn.getEmail(), "aName");
        assertEquals(signIn.getPassword(), "password");
        assertEquals(signIn.getToken(), "ABC");
    }
    
    @Test
    public void canSerialize() throws Exception {
        // We set up tests with this object so verify it creates the correct JSON
        SignIn signIn = new SignIn.Builder().withEmail("email@email.com").withExternalId("external-id")
                .withPassword("password").withPhone(TestConstants.PHONE).withReauthToken("reauthToken")
                .withStudy("study-key").withToken("token").build();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(signIn);
        assertEquals(node.get("email").textValue(), "email@email.com");
        assertEquals(node.get("password").textValue(), "password");
        assertEquals(node.get("phone").get("number").textValue(), TestConstants.PHONE.getNumber());
        assertEquals(node.get("phone").get("regionCode").textValue(), TestConstants.PHONE.getRegionCode());
        assertEquals(node.get("reauthToken").textValue(), "reauthToken");
        assertEquals(node.get("study").textValue(), "study-key");
        assertEquals(node.get("externalId").textValue(), "external-id");
        assertEquals(node.get("token").textValue(), "token");
    }
    
    @Test
    public void preferUsernameOverEmailForBackwardsCompatibility() throws Exception {
        String json = "{\"username\":\"aName\",\"email\":\"email@email.com\",\"password\":\"password\"}";

        SignIn signIn = BridgeObjectMapper.get().readValue(json, SignIn.class);

        assertEquals(signIn.getEmail(), "aName");
        assertEquals(signIn.getPassword(), "password");
    }
    
    @Test
    public void canSendReauthenticationToken() throws Exception {
        String json = "{\"email\":\"email@email.com\",\"reauthToken\":\"myReauthToken\"}";

        SignIn signIn = BridgeObjectMapper.get().readValue(json, SignIn.class);

        assertEquals(signIn.getEmail(), "email@email.com");
        assertEquals(signIn.getReauthToken(), "myReauthToken");
    }
    
    @Test
    public void test() throws Exception {
        JsonNode node = BridgeObjectMapper.get().readTree(TestUtils.createJson("{"+
                "'email':'emailValue',"+
                "'externalId':'external-id',"+
                "'password':'passwordValue',"+
                "'study':'studyValue',"+
                "'token':'tokenValue',"+
                "'phone':{'number':'"+TestConstants.PHONE.getNumber()+"',"+
                    "'regionCode':'"+TestConstants.PHONE.getRegionCode()+"'},"+
                "'reauthToken':'reauthTokenValue'"+
                "}"));
        
        SignIn signIn = BridgeObjectMapper.get().readValue(node.toString(), SignIn.class);
        assertEquals(signIn.getEmail(), "emailValue");
        assertEquals(signIn.getExternalId(), "external-id");
        assertEquals(signIn.getPassword(), "passwordValue");
        assertEquals(signIn.getStudyId(), "studyValue");
        assertEquals(signIn.getToken(), "tokenValue");
        assertEquals(signIn.getPhone().getNumber(), TestConstants.PHONE.getNumber());
        assertEquals(signIn.getPhone().getRegionCode(), TestConstants.PHONE.getRegionCode());
        assertEquals(signIn.getReauthToken(), "reauthTokenValue");
    }
    
    @Test
    public void acceptsUsernameAsEmail() throws Exception {
        JsonNode node = BridgeObjectMapper.get().readTree(TestUtils.createJson("{"+
                "'username':'emailValue',"+
                "'password':'passwordValue',"+
                "'study':'studyValue',"+
                "'token':'tokenValue',"+
                "'reauthToken':'reauthTokenValue'"+
                "}"));
        SignIn signIn = BridgeObjectMapper.get().readValue(node.toString(), SignIn.class);
        assertEquals(signIn.getEmail(), "emailValue");
        assertEquals(signIn.getPassword(), "passwordValue");
        assertEquals(signIn.getStudyId(), "studyValue");
        assertEquals(signIn.getToken(), "tokenValue");
        assertEquals(signIn.getReauthToken(), "reauthTokenValue");
    }
    
    @Test
    public void signInAccountIdWithEmail() {
        SignIn signIn = new SignIn.Builder().withStudy(TEST_APP_ID).withEmail("email")
                .withPassword("password").build();
        AccountId accountId = signIn.getAccountId();
        assertEquals(accountId.getStudyId(), TEST_APP_ID);
        assertEquals(accountId.getEmail(), "email");
    }
    
    @Test
    public void signInAccountIdWithPhone() {
        SignIn signIn = new SignIn.Builder().withStudy(TEST_APP_ID)
                .withPhone(TestConstants.PHONE).withPassword("password").build();
        AccountId accountId = signIn.getAccountId();
        assertEquals(accountId.getStudyId(), TEST_APP_ID);
        assertEquals(accountId.getPhone().getNumber(), TestConstants.PHONE.getNumber());
    }
    
    @Test
    public void signInAccountIdWithExternalId() {
        SignIn signIn = new SignIn.Builder().withStudy(TEST_APP_ID)
                .withExternalId("external-id").withPassword("password").build();
        AccountId accountId = signIn.getAccountId();
        assertEquals(accountId.getStudyId(), TEST_APP_ID);
        assertEquals(accountId.getExternalId(), "external-id");
    }
    
    @Test
    public void signInAccountIncomplete() {
        SignIn signIn = new SignIn.Builder().withStudy(TEST_APP_ID)
                .withPassword("password").build();
        // SignIn should be validated to hold either email or phone before we 
        // retrieve accountId 
        try {
            signIn.getAccountId();
            fail("Should have thrown an exception");
        } catch(IllegalArgumentException e) {
            assertEquals(e.getMessage(), "SignIn not constructed with enough information to retrieve an account");
        }
    }
    
    @Test
    public void fullCopy() {
        SignIn origin = new SignIn.Builder()
                .withUsername(TestConstants.EMAIL)
                .withPhone(TestConstants.PHONE)
                .withExternalId("externalId")
                .withPassword("password")
                .withStudy(TEST_APP_ID)
                .withToken("token")
                .withReauthToken("reauthToken").build();
        
        SignIn copy = new SignIn.Builder().withSignIn(origin).build();
        assertEquals(copy.getEmail(), TestConstants.EMAIL);
        assertEquals(copy.getPhone(), TestConstants.PHONE);
        assertEquals(copy.getExternalId(), "externalId");
        assertEquals(copy.getPassword(), "password");
        assertEquals(copy.getStudyId(), TEST_APP_ID);
        assertEquals(copy.getToken(), "token");
        assertEquals(copy.getReauthToken(), "reauthToken");
        
        // Also test the straight email-to-email copy as well as the username copy
        assertEquals(new SignIn.Builder().withSignIn(
                new SignIn.Builder().withEmail("email").build()
            ).build().getEmail(), "email");
    }
}
