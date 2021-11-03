package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.charset.Charset.defaultCharset;
import static org.sagebionetworks.bridge.BridgeConstants.SYNAPSE_OAUTH_CLIENT_ID;
import static org.sagebionetworks.bridge.BridgeConstants.SYNAPSE_OAUTH_CLIENT_SECRET;
import static org.sagebionetworks.bridge.BridgeConstants.SYNAPSE_OAUTH_URL;
import static org.sagebionetworks.bridge.BridgeConstants.SYNAPSE_OAUTH_VENDOR_ID;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.AbstractMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.node.NullNode;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.apps.OAuthProvider;
import org.sagebionetworks.bridge.models.oauth.OAuthAccessGrant;
import org.sagebionetworks.bridge.models.oauth.OAuthAuthorizationToken;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

/**
 * Service that specifically works for Fitbit API. We may eventually choose an OAuth library if the 
 * implementations vary. 
 */
@Component
class OAuthProviderService {
    private static final Logger LOG = LoggerFactory.getLogger(OAuthProviderService.class);

    private static final String ACCESS_TOKEN_PROP_NAME = "access_token";
    private static final String AUTHORIZATION_CODE_VALUE = "authorization_code";
    static final String AUTHORIZATION_PROP_NAME = "Authorization";
    private static final String CLIENT_ID_PROP_NAME = "clientId";
    private static final String CODE_PROP_NAME = "code";
    static final String CONTENT_TYPE_PROP_NAME = "Content-Type";
    private static final String ERROR_TYPE_PROP_NAME = "errorType";
    private static final String ERRORS_PROP_NAME = "errors";
    private static final String EXPIRES_IN_PROP_NAME = "expires_in";
    static final String FORM_ENCODING_VALUE = "application/x-www-form-urlencoded";
    private static final String GRANT_TYPE_PROP_NAME = "grant_type";
    private static final String LOG_ERROR_MSG = "Error retrieving access token, statusCode=%s, body=%s";
    private static final String MESSAGE_PROP_NAME = "message";
    private static final String REDIRECT_URI_PROP_NAME = "redirect_uri";
    private static final String REFRESH_TOKEN_PROP_NAME = "refresh_token";
    private static final String REFRESH_TOKEN_VALUE = "refresh_token";
    private static final Pattern SCOPE_PAIR_SPLIT_PATTERN = Pattern.compile("\\s*[{},]\\s*");
    private static final String SCOPE_PROP_NAME = "scope";
    private static final String SERVICE_ERROR_MSG = "Error retrieving access token";
    static final String TOKEN_PROP_NAME = "token";
    private static final String PROVIDER_USER_ID = "user_id";
    private static final Set<String> INVALID_OR_EXPIRED_ERRORS = ImmutableSet.of("invalid_token", "expired_token", "invalid_grant");
    private static final Set<String> INVALID_CLIENT_ERRORS = ImmutableSet.of("invalid_client");
    static final String SYNAPSE_USERID_KEY = "userid";
    private static final String SYNAPSE_ID_TOKEN_KEY = "id_token";
    private static final String SYNAPSE_ERROR_KEY = "reason";
    // These come from Synapse JWKS config. These are Base64URL-encoded. We decode them into a BigInt and hardcode the
    // results here.
    // https://repo-prod.prod.sagebase.org/auth/v1/oauth2/jwks
    // https://repo-dev.dev.sagebase.org/auth/v1/oauth2/jwks
    private static final BigInteger MODULUS = new BigInteger("238499454829654749051455172686433749207979434713239851759657441457377"+
            "1656597474337243022411171242737524732939908302581624575831937041782355615027727710820665290995695103877971991185257319"+
            "7057021578055098390526471329800633323810194331080461575761929434803674679324161848934212115408664545557149085782355814"+
            "2203009343325468419362811405260745578631362098820497061936813369203864883181658617346770893793830626123962885974940082"+
            "6219064421262331213358876970410749141172687535654897753523188343873412905059109968922576374393735637734252722397997640"+
            "7213258598569748635314939347146403284906522122994148568855913460554935920381");
    private static final BigInteger MODULUS_DEV = new BigInteger("25159655312783347884364772547748435634086427201698234303716556513"+
            "7729410580787588545867155811350794512509709106307227925547818198442773657899912426428316219488030985437725560574786643"+
            "7084312325975370218827691654202621804474741752982456390519875233862582355344320323419432100056890022373729963243244874"+
            "2023913527144664645993395992704226693605312330024170969703515168220424835383129339227771799451777328144746815351791157"+
            "6519217531094653932301202440450170518845133584029947053310629688248843358501502611734208642019539713376051085488732979" +
            "92379718298635525844478538144348008297367792825995385863082648480751012984282901");
    private static final BigInteger EXPONENT = new BigInteger("65537");
    private static final RSAPublicKeySpec KEY_SPEC = new RSAPublicKeySpec(MODULUS, EXPONENT);
    private static final RSAPublicKeySpec KEY_SPEC_DEV = new RSAPublicKeySpec(MODULUS_DEV, EXPONENT);

    private BridgeConfig config;
    private String synapseOauthURL;
    private String synapseClientID;
    private String synapseClientSecret;
    private AppService appService;

    @Autowired
    final void setBridgeConfig(BridgeConfig config) {
        this.config = config;
        this.synapseOauthURL = config.get(SYNAPSE_OAUTH_URL);
        this.synapseClientID = config.get(SYNAPSE_OAUTH_CLIENT_ID);
        this.synapseClientSecret = config.get(SYNAPSE_OAUTH_CLIENT_SECRET);
    }
    
    @Autowired
    final void setAppService(AppService appService) {
        this.appService = appService;
    }
    
    /**
     * Simple container for the response, parsed before closing the stream.
     */
    static class Response {
        private final int status;
        private final JsonNode body;
        public Response(int status, JsonNode body) {
            this.status = status;
            this.body = body;
        }
        public int getStatusCode() {
            return this.status;
        }
        public JsonNode getBody() {
            return this.body;
        }
    }

    // accessor so time can be mocked in unit tests
    protected DateTime getDateTime() {
        return DateTime.now(DateTimeZone.UTC);
    }

    // There are separate methods for each HTTP call to enable test mocking
    protected OAuthProviderService.Response executeGrantRequest(HttpPost client) {
        return executeInternal(client);
    }

    protected OAuthProviderService.Response executeRefreshRequest(HttpPost client) {
        return executeInternal(client);
    }

    protected OAuthProviderService.Response executeIntrospectRequest(HttpPost client) {
        return executeInternal(client);
    }

    private OAuthProviderService.Response executeInternal(HttpPost client) {
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            CloseableHttpResponse response = httpclient.execute(client);
            int statusCode = response.getStatusLine().getStatusCode();

            JsonNode body;
            try {
                body = BridgeObjectMapper.get().readTree(response.getEntity().getContent());
            } catch (JsonParseException ex) {
                // Log the error and the status code. Set body to a null node, so we don't break any callers.
                LOG.error("OAuth call failed with invalid JSON, status code " + statusCode);
                body = NullNode.getInstance();
            }

            return new Response(statusCode, body);
        } catch (IOException e) {
            LOG.error(SERVICE_ERROR_MSG, e);
            throw new BridgeServiceException(SERVICE_ERROR_MSG);
        }
    }

    /**
     * Authenticate a Bridge user via an external OAuth server that supports Open Connect ID (identified by the supplied
     * vendor ID; currently the only supported external authentication server is Synapse, but the same requirements are
     * fulfilled by Google, Facebook, etc.). This is the second contact of the OAuth server, after a client has sent a
     * user to authenticate on the OAuth server's web site and the OAuth server has redirected back to the Bridge client
     * with an authentication code. We now exchange it for an authentication token as well as the additional OCID
     * information to verify the identity of the caller (currently we ask for the user's Synapse ID from Synapse, but we
     * could also use email or phone number).
     * 
     * @param authToken
     *         that was passed from the OAuth server back to the authenticating client
     * @return accountId if the exchange is successful, the accountId will contain an identifying credential that should
     *         be usable to retrieve an account
     * 
     * @throws BadRequestException
     */
    public AccountId oauthSignIn(OAuthAuthorizationToken authToken) {
        checkNotNull(authToken);
        
        if (authToken.getVendorId() == null) {
            throw new BadRequestException("Vendor ID required");
        }
        if (authToken.getAppId() == null) {
            throw new BadRequestException("App ID required");
        }
        if (authToken.getAuthToken() == null) {
            throw new BadRequestException("Authorization token required");
        }
        OAuthProvider provider = null;
        if (SYNAPSE_OAUTH_VENDOR_ID.equals(authToken.getVendorId())) {
            // Synapse configuration is globally available and not stored in providers table.
            provider = new OAuthProvider(this.synapseClientID, this.synapseClientSecret, 
                    this.synapseOauthURL, authToken.getCallbackUrl(), null);
        } else {
            // Is this vendor ID mapped to another provider for this app?
            App app = appService.getApp(authToken.getAppId());
            provider = app.getOAuthProviders().get(authToken.getVendorId());
        }
        if (provider == null) {
            throw new BadRequestException("Vendor not supported: " + authToken.getVendorId());
        }
        
        HttpPost client = createOAuthProviderPost(provider, provider.getEndpoint());
        
        List<NameValuePair> pairs = new ArrayList<>();
        pairs.add(new BasicNameValuePair(GRANT_TYPE_PROP_NAME, AUTHORIZATION_CODE_VALUE));
        pairs.add(new BasicNameValuePair(CODE_PROP_NAME, authToken.getAuthToken()));
        pairs.add(new BasicNameValuePair(REDIRECT_URI_PROP_NAME, authToken.getCallbackUrl()));
        client.setEntity(formEntity(pairs));
        
        Response response = executeGrantRequest(client);
        if (response.getStatusCode() < 200 || response.getStatusCode() > 299) {
            throw new BadRequestException(response.getBody().get(SYNAPSE_ERROR_KEY).textValue());
        }
        JwtParser parser = getJwtParser();
        parser.setSigningKey(getRSAPublicKey());
        parser.setAllowedClockSkewSeconds(5);
        
        String idTokenBlock = response.getBody().get(SYNAPSE_ID_TOKEN_KEY).textValue();
        Jws<Claims> jwt = parser.parseClaimsJws(idTokenBlock);
        String synapseUserId = jwt.getBody().get(SYNAPSE_USERID_KEY, String.class);
        
        if (synapseUserId != null) {
            return AccountId.forSynapseUserId(authToken.getAppId(), synapseUserId);
        }
        return null;
    }
    
    // isolating static accessor for mocking
    JwtParser getJwtParser() {
        return Jwts.parser();
    }
    
    private RSAPublicKey getRSAPublicKey() {
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            RSAPublicKeySpec keySpec = config.isProduction() ? KEY_SPEC : KEY_SPEC_DEV;
            return (RSAPublicKey)kf.generatePublic(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException(e);
        } 
    }    
    
    /**
     * Request an access grant token.
     */
    OAuthAccessGrant requestAccessGrant(OAuthProvider provider, OAuthAuthorizationToken authToken) {
        checkNotNull(provider);
        checkNotNull(authToken);

        // If no authorization token has been provided, all we can do is attempt to refresh.
        if (StringUtils.isBlank(authToken.getAuthToken())) {
            throw new EntityNotFoundException(OAuthAccessGrant.class);
        }
        HttpPost client = createOAuthProviderPost(provider, provider.getEndpoint());

        List<NameValuePair> pairs = new ArrayList<>();
        pairs.add(new BasicNameValuePair(CLIENT_ID_PROP_NAME, provider.getClientId()));
        pairs.add(new BasicNameValuePair(GRANT_TYPE_PROP_NAME, AUTHORIZATION_CODE_VALUE));
        pairs.add(new BasicNameValuePair(REDIRECT_URI_PROP_NAME, provider.getCallbackUrl()));
        pairs.add(new BasicNameValuePair(CODE_PROP_NAME, authToken.getAuthToken()));
        client.setEntity(formEntity(pairs));

        Response response = executeGrantRequest(client);

        OAuthAccessGrant grant = handleResponse(response, this::jsonToGrant);
        grant.setVendorId(authToken.getVendorId());
        addScopesToAccessGrant(provider, grant);
        return grant;
    }
    
    /**
     * Refresh the access grant token.
     */
    OAuthAccessGrant refreshAccessGrant(OAuthProvider provider, String vendorId, String refreshToken) {
        checkNotNull(provider);
        checkNotNull(vendorId);
        
        if (refreshToken == null) {
            throw new EntityNotFoundException(OAuthAccessGrant.class);
        }
        HttpPost client = createOAuthProviderPost(provider, provider.getEndpoint());

        List<NameValuePair> pairs = new ArrayList<>();
        pairs.add(new BasicNameValuePair(GRANT_TYPE_PROP_NAME, REFRESH_TOKEN_VALUE));
        pairs.add(new BasicNameValuePair(REFRESH_TOKEN_PROP_NAME, refreshToken));
        client.setEntity(formEntity(pairs));

        Response response = executeRefreshRequest(client);

        OAuthAccessGrant grant = handleResponse(response, this::jsonToGrant);
        grant.setVendorId(vendorId);
        addScopesToAccessGrant(provider, grant);
        return grant;
    }

    // Helper method, which calls the OAuth Introspect API and adds scopes to the grant. Package-scoped for unit tests.
    void addScopesToAccessGrant(OAuthProvider provider, OAuthAccessGrant grant) {
        checkNotNull(provider);
        checkNotNull(grant);
        if (provider.getIntrospectEndpoint() == null) {
            // This OAuth provider does not have an introspect URL. Skip.
            return;
        }
        if (grant.getAccessToken() == null) {
            // We have the access grant, but it's invalid.
            throw new InvalidEntityException(grant, "OAuth access grant has no access token");
        }

        // Create args.
        try {
            HttpPost client = createOAuthProviderPost(provider, provider.getIntrospectEndpoint());
            List<NameValuePair> pairs = new ArrayList<>();
            pairs.add(new BasicNameValuePair(TOKEN_PROP_NAME, grant.getAccessToken()));
            client.setEntity(formEntity(pairs));

            // Call Introspect and parse response.
            Response response = executeIntrospectRequest(client);
            List<String> scopeList = handleResponse(response, this::jsonToScopeList);
            grant.setScopes(scopeList);
        } catch (RuntimeException ex) {
            // It's better to log an error and return a grant with no scopes than to bubble up the error and lose the
            // OAuth grant altogether.
            LOG.error("Could not get scopes for OAuth grant: " + ex.getMessage(), ex);
        }
    }

    protected HttpPost createOAuthProviderPost(OAuthProvider provider, String url) {
        String authHeader = provider.getClientId() + ":" + provider.getSecret();
        String encodedAuthHeader = "Basic " + Base64.encodeBase64String(authHeader.getBytes(defaultCharset()));

        HttpPost client = new HttpPost(url);
        client.addHeader(AUTHORIZATION_PROP_NAME, encodedAuthHeader);
        client.addHeader(CONTENT_TYPE_PROP_NAME, FORM_ENCODING_VALUE);
        return client;
    }

    protected <T> T handleResponse(Response response, Function<JsonNode, T> converter) {
        int statusCode = response.getStatusCode();
        
        // Note: this is an interpretation of the errors. It may not be what we finally want, but it was based
        // on initial conversations with client team about what would work for them. For example, returning 401 
        // here may trigger behavior that indicates the user needs to sign in to the client, so we avoid that.
        
        // Invalid client errors indicate that we have not written this service correctly.
        if (statusCode == 401 && isErrorType(response, INVALID_CLIENT_ERRORS)) {
            LOG.error(String.format(LOG_ERROR_MSG, response.getStatusCode(), response.getBody()));
            throw new BridgeServiceException(SERVICE_ERROR_MSG);
        } 
        // If it's a 401 (unauthorized) or the tokens are invalid/expired, we report a 404 (no grant).
        else if (statusCode == 401 || isErrorType(response, INVALID_OR_EXPIRED_ERRORS)) {
            throw new EntityNotFoundException(OAuthAccessGrant.class);
        } 
        // Other 403 exceptions indicate a permissions issue, possibly based on scope or something else
        // that Bridge doesn't control.
        else if (statusCode == 403) {
            throw new UnauthorizedException(jsonToErrorMessage(response.getBody()));
        } 
        // Other bad request, are bad requests... possibly due to input from the client
        else if (statusCode > 399 && statusCode < 500) {
            throw new BadRequestException(jsonToErrorMessage(response.getBody()));
        } 
        // And everything, for now, can be treated as Bridge server error.
        else if (statusCode != 200) {
            LOG.error(String.format(LOG_ERROR_MSG, response.getStatusCode(), response.getBody()));
            throw new BridgeServiceException(SERVICE_ERROR_MSG, response.getStatusCode());
        }
        return converter.apply(response.getBody());
    }

    protected OAuthAccessGrant jsonToGrant(JsonNode node) {
        String accessToken = node.get(ACCESS_TOKEN_PROP_NAME).textValue();
        String refreshToken = node.get(REFRESH_TOKEN_PROP_NAME).textValue();
        String providerUserId = node.get(PROVIDER_USER_ID).textValue();
        int expiresInSeconds = node.get(EXPIRES_IN_PROP_NAME).intValue();

        // Pull expiration back one minute to protect against clock skew between client and server
        DateTime createdOn = getDateTime();
        DateTime expiresOn = createdOn.plusSeconds(expiresInSeconds).minusMinutes(1);
        
        OAuthAccessGrant grant = OAuthAccessGrant.create();
        grant.setAccessToken(accessToken);
        grant.setRefreshToken(refreshToken);
        grant.setCreatedOn(createdOn.getMillis());
        grant.setExpiresOn(expiresOn.getMillis());
        grant.setProviderUserId(providerUserId);
        return grant;
    }

    // Helper method which takes the JSON body of an Introspect request and parses out the set of scopes.
    protected List<String> jsonToScopeList(JsonNode node) {
        // The Introspect API is defined by RFC7662 https://tools.ietf.org/html/rfc7662
        // We only use the scopes from this API.
        //
        // Note that scopes are associated on a per-token basis, and they are locked in when the participant first
        // provides the OAuth authorization. (This happens outside of Bridge Server, generally in the app.) The
        // Introspect API is scoped to a specific token. The response will contain a list of scopes associated with
        // that token. There may be other possible scopes in the FitBit API, but if they are not associated with the
        // token, they will not appear here.
        //
        // Also note that while RFC7662 defines scope as a space-delimited string, FitBit uses a non-standard format.
        // An example FitBit Introspect response looks like
        // {
        //     "active": true,
        //     "scope": "{ACTIVITY=READ, HEARTRATE=READ, SLEEP=READ}",
        //     "client_id": "22CQ7B",
        //     "user_id": "6CGW8Z",
        //     "token_type": "access_token",
        //     "exp": 1565861634000,
        //     "iat": 1565832834000
        // }

        String scopeString = node.get(SCOPE_PROP_NAME).textValue();
        String[] scopePairArray = SCOPE_PAIR_SPLIT_PATTERN.split(scopeString);

        // scopePairArray now looks like "SLEEP=READ", "HEARTRATE=READ", "ACTIVITY=READ". We only care about the scope
        // name, ie everything before the =. After the equal is always READ or READWRITE (usually READ), and we only
        // care about reading, so we can ignore it.
        List<String> scopeList = new ArrayList<>();
        for (String scopePair : scopePairArray) {
            // Note that because of the way we split strings, we may have some empty strings. Skip those.
            if (scopePair.isEmpty()) {
                continue;
            }

            int equalIdx = scopePair.indexOf('=');
            scopeList.add(scopePair.substring(0, equalIdx));
        }

        return scopeList;
    }

    protected String jsonToErrorMessage(JsonNode node) {
        List<String> messages = extractFromJSON(node, AbstractMap.SimpleEntry::getValue);
        return BridgeUtils.SPACE_JOINER.join(messages);
    }
    
    private boolean isErrorType(Response response, Set<String> errorTypes) {
        List<String> responseErrorTypes = extractFromJSON(response.getBody(), AbstractMap.SimpleEntry::getKey);
        return !Collections.disjoint(errorTypes, responseErrorTypes);
    }
    
    protected List<String> extractFromJSON(JsonNode node,
            Function<? super SimpleEntry<String, String>, ? extends String> mapField) {
        List<AbstractMap.SimpleEntry<String,String>> list = Lists.newArrayList();
        if (node.has(ERRORS_PROP_NAME)) {
            ArrayNode errors = (ArrayNode) node.get(ERRORS_PROP_NAME);
            errors.forEach((error) -> {
                if (error.has(MESSAGE_PROP_NAME)) {
                    String type = error.get(ERROR_TYPE_PROP_NAME).textValue();
                    String message = error.get(MESSAGE_PROP_NAME).textValue();
                    list.add(new AbstractMap.SimpleEntry<>(type, message));
                }
            });
        }
        return list.stream().map(mapField).collect(Collectors.toList());        
    }

    /**
     * UnsupportedEncodingException is one of those checked exceptions that will *never* be thrown if you use the
     * default system encoding.
     */
    private UrlEncodedFormEntity formEntity(List<NameValuePair> pairs) {
        try {
            return new UrlEncodedFormEntity(pairs);
        } catch (UnsupportedEncodingException e) {
            throw new BridgeServiceException(e);
        }
    }
}
