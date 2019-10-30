package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.BridgeConstants.SYNAPSE_OAUTH_CALLBACK;
import static org.sagebionetworks.bridge.BridgeConstants.SYNAPSE_OAUTH_CLIENT_ID;
import static org.sagebionetworks.bridge.BridgeConstants.SYNAPSE_OAUTH_CLIENT_SECRET;
import static org.sagebionetworks.bridge.BridgeConstants.SYNAPSE_OAUTH_URL;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.sagebionetworks.bridge.services.OAuthProviderService.AUTHORIZATION_PROP_NAME;
import static org.sagebionetworks.bridge.services.OAuthProviderService.SYNAPSE_USERID_KEY;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.IOException;
import java.util.List;

import com.google.common.collect.ImmutableList;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.oauth.OAuthAccessGrant;
import org.sagebionetworks.bridge.models.oauth.OAuthAuthorizationToken;
import org.sagebionetworks.bridge.models.studies.OAuthProvider;
import org.sagebionetworks.bridge.services.OAuthProviderService.Response;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtParser;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;

public class OAuthProviderServiceTest extends Mockito {
    private static final String SYNAPSE_OAUTH_CALLBACK_VALUE = "https://research.sagebridge.org";
    private static final String SYNAPSE_OAUTH_CLIENT_SECRET_VALUE = "client-secret";
    private static final String SYNAPSE_OAUTH_CLIENT_ID_VALUE = "client-id";
    private static final String SYNAPSE_OAUTH_URL_VALUE = "https://repo-prod.prod.sagebase.org/auth/v1/oauth2/token";
    private static final DateTime NOW = DateTime.now(DateTimeZone.UTC);
    private static final DateTime EXPIRES = NOW.plusSeconds(3600).minusMinutes(1);
    private static final String ACCESS_TOKEN = "accessToken";
    private static final String AUTH_TOKEN_STRING = "authToken";
    private static final String CALLBACK_URL = "callbackUrl";
    private static final String CLIENT_ID = "clientId";
    private static final String ENDPOINT = "endpoint";
    private static final String GRANT_FORM_DATA = "clientId=clientId&grant_type=authorization_code&redirect_uri=callbackUrl&code=authToken";
    private static final String INTROSPECT_ENDPOINT = "http://example.com/introspect";
    private static final String REFRESH_FORM_DATA = "grant_type=refresh_token&refresh_token=refreshToken";
    private static final String REFRESH_TOKEN = "refreshToken";
    private static final String REFRESH_TOKEN2 = "refreshToken";
    private static final String SECRET = "secret";
    private static final String USER_ID = "26FWFL";
    private static final String VENDOR_ID = "vendorId";
    private static final String SYNAPSE_ID = "synapse";
    private static final OAuthAuthorizationToken AUTH_TOKEN = new OAuthAuthorizationToken(TEST_STUDY_IDENTIFIER, VENDOR_ID, AUTH_TOKEN_STRING);
    private static final OAuthAuthorizationToken SIGNIN_TOKEN = new OAuthAuthorizationToken(TEST_STUDY_IDENTIFIER, SYNAPSE_ID, AUTH_TOKEN_STRING);
    private static final OAuthProvider PROVIDER = new OAuthProvider(CLIENT_ID, SECRET, ENDPOINT, CALLBACK_URL,
            null);
    private static final OAuthProvider PROVIDER_WITH_INTROSPECT = new OAuthProvider(CLIENT_ID, SECRET, ENDPOINT,
            CALLBACK_URL, INTROSPECT_ENDPOINT);

    // This is an actual Introspect response from FitBit (with client and user IDs obfuscated).
    private static final String INTROSPECT_RESPONSE_BODY = "{\n" +
            "   \"active\": true,\n" +
            "   \"scope\": \"{ACTIVITY=READ, HEARTRATE=READ, SLEEP=READ}\",\n" +
            "   \"client_id\": \"22CQ7B\",\n" +
            "   \"user_id\": \"6CGW8Z\",\n" +
            "   \"token_type\": \"access_token\",\n" +
            "   \"exp\": 1565861634000,\n" +
            "   \"iat\": 1565832834000\n" +
            "}";
    private static final List<String> EXPECTED_SCOPE_LIST = ImmutableList.of("ACTIVITY", "HEARTRATE", "SLEEP");

    @Spy
    @InjectMocks
    private OAuthProviderService service;
    
    @Mock
    private CloseableHttpClient mockClient;
    
    @Mock
    private HttpPost mockPost;
    
    @Mock
    private BridgeConfig mockBridgeConfig;
    
    @Mock
    private AccountDao mockAccountDao;
    
    @Mock
    private JwtParser mockJwtParser;
    
    @Mock
    private Jws<Claims> mockJwtClaims;
    
    @Mock
    private Claims mockClaims;
    
    @Mock
    private UrlEncodedFormEntity mockFormEntity;
    
    @Captor
    private ArgumentCaptor<HttpPost> grantPostCaptor;
    
    @Captor
    private ArgumentCaptor<HttpPost> refreshPostCaptor;

    @Captor
    private ArgumentCaptor<HttpPost> introspectPostCaptor;
    
    @Captor
    private ArgumentCaptor<UrlEncodedFormEntity> formEntityCaptor;

    @BeforeMethod
    public void before() throws IOException {
        MockitoAnnotations.initMocks(this);
        doReturn(NOW).when(service).getDateTime();
        when(mockBridgeConfig.get(SYNAPSE_OAUTH_URL)).thenReturn(SYNAPSE_OAUTH_URL_VALUE);
        when(mockBridgeConfig.get(SYNAPSE_OAUTH_CLIENT_ID)).thenReturn(SYNAPSE_OAUTH_CLIENT_ID_VALUE);
        when(mockBridgeConfig.get(SYNAPSE_OAUTH_CLIENT_SECRET)).thenReturn(SYNAPSE_OAUTH_CLIENT_SECRET_VALUE);
        when(mockBridgeConfig.get(SYNAPSE_OAUTH_CALLBACK)).thenReturn(SYNAPSE_OAUTH_CALLBACK_VALUE);
        service.setBridgeConfig(mockBridgeConfig);
    }
    
    private void mockAccessGrantCall(int statusCode, String responseBody) throws IOException {
        String json = TestUtils.createJson(responseBody);
        JsonNode body = BridgeObjectMapper.get().readTree(json);
        doReturn(new Response(statusCode, body)).when(service).executeGrantRequest(grantPostCaptor.capture());
    }

    private void mockRefreshCall(int statusCode, String responseBody) throws IOException {
        String json = TestUtils.createJson(responseBody);
        JsonNode body = BridgeObjectMapper.get().readTree(json);
        doReturn(new Response(statusCode, body)).when(service).executeRefreshRequest(refreshPostCaptor.capture());
    }

    private void mockIntrospectCall(int statusCode, String responseBody) throws IOException {
        JsonNode body = BridgeObjectMapper.get().readTree(responseBody);
        doReturn(new Response(statusCode, body)).when(service).executeIntrospectRequest(introspectPostCaptor
                .capture());
    }

    private String successJson() {
        return "{'access_token': '"+ACCESS_TOKEN+"',"+
            "'expires_in': 3600,"+
            "'refresh_token': '"+REFRESH_TOKEN+"',"+
            "'token_type': 'Bearer',"+
            "'user_id': '"+USER_ID+"'}";
    }
    private String errorJson(String...strings) {
        List<String> errors = Lists.newArrayList();
        for (int i=0; i < strings.length; i+=2) {
            String type = strings[i];
            String msg = strings[i+1];
            errors.add("{'errorType':'"+type+"', 'message':'"+msg+"'}");
        }
        return "{'errors':["+BridgeUtils.COMMA_JOINER.join(errors)+"],'success':false}";
    }
    
    @Test
    public void makeAccessGrantCall() throws Exception {
        mockAccessGrantCall(200, successJson());
        
        OAuthAccessGrant grant = service.requestAccessGrant(PROVIDER, AUTH_TOKEN);
        
        assertEquals(grant.getAccessToken(), ACCESS_TOKEN);
        assertEquals(grant.getVendorId(), VENDOR_ID);
        assertEquals(grant.getRefreshToken(), REFRESH_TOKEN2);
        assertEquals(grant.getCreatedOn(), NOW.getMillis());
        assertEquals(grant.getProviderUserId(), USER_ID);
        assertEquals(grant.getExpiresOn(), EXPIRES.getMillis());
        
        String authHeader = "Basic " + Base64.encodeBase64String( (CLIENT_ID + ":" + SECRET).getBytes() );
        
        HttpPost thePost = grantPostCaptor.getValue();
        // Test the headers here... they don't need to be tested in every test, they're always the same.
        assertEquals(thePost.getURI().toString(), ENDPOINT);
        assertEquals(thePost.getFirstHeader("Authorization").getValue(), authHeader);
        assertEquals(thePost.getFirstHeader("Content-Type").getValue(), "application/x-www-form-urlencoded");
        String bodyString = EntityUtils.toString(thePost.getEntity());
        assertEquals(bodyString, GRANT_FORM_DATA);
    }

    @Test
    public void makeAccessGrantCallWithScopes() throws Exception {
        mockAccessGrantCall(200, successJson());
        mockIntrospectCall(200, INTROSPECT_RESPONSE_BODY);
        OAuthAccessGrant grant = service.requestAccessGrant(PROVIDER_WITH_INTROSPECT, AUTH_TOKEN);
        assertEquals(grant.getScopes(), EXPECTED_SCOPE_LIST);

        String authHeader = "Basic " + Base64.encodeBase64String( (CLIENT_ID + ":" + SECRET).getBytes() );

        HttpPost thePost = introspectPostCaptor.getValue();
        // Test the headers here... they don't need to be tested in every test, they're always the same.
        assertEquals(thePost.getURI().toString(), INTROSPECT_ENDPOINT);
        assertEquals(thePost.getFirstHeader(OAuthProviderService.AUTHORIZATION_PROP_NAME).getValue(), authHeader);
        assertEquals(thePost.getFirstHeader(OAuthProviderService.CONTENT_TYPE_PROP_NAME).getValue(),
                OAuthProviderService.FORM_ENCODING_VALUE);
        String bodyString = EntityUtils.toString(thePost.getEntity());
        assertEquals(bodyString, OAuthProviderService.TOKEN_PROP_NAME + "=" + ACCESS_TOKEN);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void makeAccessGrantCallWithoutAuthTokenRefreshes() throws Exception {
        OAuthAuthorizationToken emptyPayload = new OAuthAuthorizationToken(null, VENDOR_ID, null);
        
        service.requestAccessGrant(PROVIDER, emptyPayload);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void makeAccessGrantCallAuthAndRefreshTokenMissing() throws Exception {
        OAuthAuthorizationToken emptyPayload = new OAuthAuthorizationToken(null, VENDOR_ID, null);
        service.requestAccessGrant(PROVIDER, emptyPayload);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void makeAccessGrantCallAuthAndRefreshTokenInvalid() throws Exception {
        mockAccessGrantCall(400, errorJson("invalid_grant", "Authorization code expired: [code]."));
        service.requestAccessGrant(PROVIDER, AUTH_TOKEN);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void makeAccessGrantCallAuthAndRefreshTokenExpired() throws Exception {
        mockAccessGrantCall(400, errorJson("invalid_grant", "Authorization code expired: [code]."));
        service.requestAccessGrant(PROVIDER, AUTH_TOKEN);
    }
    
    /**
     * 400s could be due to server code defects, but are most likely going to be the result of invalid 
     * user input that we cannot validate, so preserve the 400 status code.
     */
    @Test
    public void makeAccessGrantCallReturns400() throws Exception {
        mockAccessGrantCall(400, errorJson("invalid_request", "Missing parameters: refresh_token.", 
                "invalid_request", "Second error, which seems rare."));
        try {
            service.requestAccessGrant(PROVIDER, AUTH_TOKEN);
            fail("Should have thrown exception");
        } catch(BadRequestException e) {
            assertEquals(e.getMessage(), "Missing parameters: refresh_token. Second error, which seems rare.");
        }
    }
    
    /**
     * 401 specifically signals that an authorization token has expired, and the refresh token should be 
     * used to issue a new access grant.
     */
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void makeAccessGrantCallReturns401() throws Exception {
        mockAccessGrantCall(400, errorJson("expired_token", "Access token expired"));
        
        service.requestAccessGrant(PROVIDER, AUTH_TOKEN);
    }
    
    @Test(expectedExceptions = BridgeServiceException.class)
    public void makeAccessGrantCallReturns500() throws Exception {
        mockAccessGrantCall(500, errorJson());
        service.requestAccessGrant(PROVIDER, AUTH_TOKEN);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void makeAccessGrantCallAuthTokenInvalid() throws Exception {
        mockAccessGrantCall(400, errorJson("invalid_grant", "Authorization code expired: [code]."));
        service.requestAccessGrant(PROVIDER, AUTH_TOKEN);
    }
  
    @Test(expectedExceptions = UnauthorizedException.class)
    public void makeRefreshCallAuthorizationError() throws Exception {
        mockAccessGrantCall(403, errorJson("insufficient_scope",
                "This application does not have permission to [access-type] [resource-type] data."));
        
        service.requestAccessGrant(PROVIDER, AUTH_TOKEN);
    }
    
    @Test(expectedExceptions = BridgeServiceException.class)
    public void makeAccessGrantCallIsBadAsProgrammed() throws Exception {
        mockAccessGrantCall(401, errorJson("invalid_client", "Authorization header required."));

        service.requestAccessGrant(PROVIDER, AUTH_TOKEN);
    }
    
    @Test(expectedExceptions = BridgeServiceException.class)
    public void makeRefreshGrantCallIsBadAsProgrammed() throws Exception {
        mockRefreshCall(401, errorJson("invalid_client", "Authorization header required."));

        service.refreshAccessGrant(PROVIDER, VENDOR_ID, REFRESH_TOKEN);
    }
    
    @Test
    public void refreshAccessCallGrantOK() throws Exception {
        mockRefreshCall(200, successJson());
        
        service.refreshAccessGrant(PROVIDER, VENDOR_ID, REFRESH_TOKEN);
        
        String authHeader = "Basic " + Base64.encodeBase64String( (CLIENT_ID + ":" + SECRET).getBytes() );
        
        HttpPost thePost = refreshPostCaptor.getValue();
        // Test the headers here... they don't need to be tested in every test, they're always the same.
        assertEquals(thePost.getURI().toString(), ENDPOINT);
        assertEquals(thePost.getFirstHeader("Authorization").getValue(), authHeader);
        assertEquals(thePost.getFirstHeader("Content-Type").getValue(), "application/x-www-form-urlencoded");
        String bodyString = EntityUtils.toString(thePost.getEntity());
        assertEquals(bodyString, REFRESH_FORM_DATA);
    }

    @Test
    public void refreshAccessGrantCallWithScopes() throws Exception {
        mockRefreshCall(200, successJson());
        mockIntrospectCall(200, INTROSPECT_RESPONSE_BODY);
        OAuthAccessGrant grant = service.refreshAccessGrant(PROVIDER_WITH_INTROSPECT, VENDOR_ID, REFRESH_TOKEN);
        assertEquals(grant.getScopes(), EXPECTED_SCOPE_LIST);

        String authHeader = "Basic " + Base64.encodeBase64String( (CLIENT_ID + ":" + SECRET).getBytes() );

        HttpPost thePost = introspectPostCaptor.getValue();
        // Test the headers here... they don't need to be tested in every test, they're always the same.
        assertEquals(thePost.getURI().toString(), INTROSPECT_ENDPOINT);
        assertEquals(thePost.getFirstHeader(OAuthProviderService.AUTHORIZATION_PROP_NAME).getValue(), authHeader);
        assertEquals(thePost.getFirstHeader(OAuthProviderService.CONTENT_TYPE_PROP_NAME).getValue(),
                OAuthProviderService.FORM_ENCODING_VALUE);
        String bodyString = EntityUtils.toString(thePost.getEntity());
        assertEquals(bodyString, OAuthProviderService.TOKEN_PROP_NAME + "=" + ACCESS_TOKEN);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void refreshAccessCallGrant400Error() throws Exception {
        mockRefreshCall(400, errorJson("invalid_token", "Authorization code expired: [code]."));
        service.refreshAccessGrant(PROVIDER, VENDOR_ID, REFRESH_TOKEN);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void refreshAccessCallGrant403Error() throws Exception {
        mockRefreshCall(403, errorJson("insufficient_scope",
                "This application does not have permission to [access-type] [resource-type] data."));
        service.refreshAccessGrant(PROVIDER, VENDOR_ID, REFRESH_TOKEN);
    }
    
    @Test(expectedExceptions = BridgeServiceException.class)
    public void refreshAccessCallGrant500Error() throws Exception {
        mockRefreshCall(500, errorJson("insufficient_scope",
                "This application does not have permission to [access-type] [resource-type] data."));
        service.refreshAccessGrant(PROVIDER, VENDOR_ID, REFRESH_TOKEN);
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void introspect_GrantWithoutToken() {
        OAuthAccessGrant grant = OAuthAccessGrant.create();
        service.addScopesToAccessGrant(PROVIDER_WITH_INTROSPECT, grant);
    }

    @Test
    public void introspect_ErrorCallingIntrospect() throws Exception {
        // Set up.
        OAuthAccessGrant grant = OAuthAccessGrant.create();
        grant.setAccessToken(ACCESS_TOKEN);
        mockIntrospectCall(503, "\"Service Unavailable\"");

        // Execute. Should succeed with no scopes.
        service.addScopesToAccessGrant(PROVIDER_WITH_INTROSPECT, grant);
        assertTrue(grant.getScopes().isEmpty());
    }
    
    @Test
    public void oauthSignIn() throws Exception {
        // This is not encrypted, the real token is public/private key encrypted. We mock the parser
        // to avoid having to sign the payload.
        mockAccessGrantCall(201, "{\"access_token\":\"not used\",\"id_token\":{\"userid\":\"12345\"}}");
        
        when(service.getJwtParser()).thenReturn(mockJwtParser);
        when(mockJwtParser.parseClaimsJws(any())).thenReturn(mockJwtClaims);
        when(mockJwtClaims.getBody()).thenReturn(mockClaims);
        when(mockClaims.get(SYNAPSE_USERID_KEY, String.class)).thenReturn("12345");
        
        Account account = Account.create();
        AccountId accountId = AccountId.forSynapseUserId(TEST_STUDY_IDENTIFIER, "12345");
        when(mockAccountDao.getAccount(accountId)).thenReturn(account);
        
        Account returnValue = service.oauthSignIn(SIGNIN_TOKEN);
        assertEquals(returnValue, account);
        
        String authHeader = "Basic " + Base64.encodeBase64String(
                (SYNAPSE_OAUTH_CLIENT_ID_VALUE + ":" + SYNAPSE_OAUTH_CLIENT_SECRET_VALUE).getBytes());
        String body = "grant_type=authorization_code&code=" + BridgeUtils.encodeURIComponent(AUTH_TOKEN_STRING)
                + "&redirect_uri=" + BridgeUtils.encodeURIComponent(SYNAPSE_OAUTH_CALLBACK_VALUE);

        HttpPost thePost = grantPostCaptor.getValue();
        assertEquals(thePost.getURI().toString(), SYNAPSE_OAUTH_URL_VALUE);
        assertEquals(thePost.getFirstHeader(AUTHORIZATION_PROP_NAME).getValue(), authHeader);
        assertEquals(EntityUtils.toString(thePost.getEntity()), body);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void oauthSignInNoVendor() {
        OAuthAuthorizationToken token = new OAuthAuthorizationToken(TEST_STUDY_IDENTIFIER, null, AUTH_TOKEN_STRING);
        service.oauthSignIn(token);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void oauthSignInWrongVendor() {
        OAuthAuthorizationToken token = new OAuthAuthorizationToken(TEST_STUDY_IDENTIFIER, "google", AUTH_TOKEN_STRING);
        service.oauthSignIn(token);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void oauthSignNoStudyId() {
        OAuthAuthorizationToken token = new OAuthAuthorizationToken(null, SYNAPSE_ID, AUTH_TOKEN_STRING);
        service.oauthSignIn(token);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void oauthSignNoCode() {
        OAuthAuthorizationToken token = new OAuthAuthorizationToken(TEST_STUDY_IDENTIFIER, SYNAPSE_ID, null);
        service.oauthSignIn(token);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void oauthSignInNotFound() throws Exception {
        // This is not encrypted, the real token is public/private key encrypted. We mock the parser
        // to avoid having to sign the payload.
        mockAccessGrantCall(201, "{\"access_token\":\"not used\",\"id_token\":{\"userid\":\"12345\"}}");
        
        when(service.getJwtParser()).thenReturn(mockJwtParser);
        when(mockJwtParser.parseClaimsJws(any())).thenReturn(mockJwtClaims);
        when(mockJwtClaims.getBody()).thenReturn(mockClaims);
        when(mockClaims.get(SYNAPSE_USERID_KEY, String.class)).thenReturn("12345");
        
        AccountId accountId = AccountId.forSynapseUserId(TEST_STUDY_IDENTIFIER, "12345");
        when(mockAccountDao.getAccount(accountId)).thenReturn(null);
        
        service.oauthSignIn(SIGNIN_TOKEN);
    }
    
    @Test
    public void oauthSignInErrorFromSynapse() throws Exception {
        // This is not encrypted, the real token is public/private key encrypted. We mock the parser
        // to avoid having to sign the payload.
        mockAccessGrantCall(400, "{\"reason\":\"The token provided is invalid\"}");
        
        when(service.getJwtParser()).thenReturn(mockJwtParser);
        when(mockJwtParser.parseClaimsJws(any())).thenReturn(mockJwtClaims);
        when(mockJwtClaims.getBody()).thenReturn(mockClaims);
        when(mockClaims.get(SYNAPSE_USERID_KEY, String.class)).thenReturn("12345");
        
        Account account = Account.create();
        AccountId accountId = AccountId.forSynapseUserId(TEST_STUDY_IDENTIFIER, "12345");
        when(mockAccountDao.getAccount(accountId)).thenReturn(account);
        
        try {
            service.oauthSignIn(SIGNIN_TOKEN);
            fail("Should have thrown an exception");
        } catch(BadRequestException e) {
            assertEquals(e.getMessage(), "The token provided is invalid");
        }
    }
}
