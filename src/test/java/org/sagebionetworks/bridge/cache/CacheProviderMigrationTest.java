package org.sagebionetworks.bridge.cache;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableSet;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.redis.JedisOps;
import org.sagebionetworks.bridge.redis.JedisTransaction;

public class CacheProviderMigrationTest {
    private static final String USER_ID = "userId";
    private static final String STUDY_ID = "studyId";
    private static final String REQUEST_INFO_KEY = "userId:request-info";
    private static final String STUDY_ID_KEY = "studyId:study";
    private static final TypeReference<Study> STUDY_TYPE_REF = new TypeReference<Study>() {};
    private static final String SESSION_TOKEN = "sessionToken";
    private static final String USER_ID_SESSION_KEY = "userId:session2:user";
    private static final String SESSION_TOKEN_KEY = "sessionToken:session2";

    private BridgeObjectMapper MAPPER = BridgeObjectMapper.get();
    private CacheProvider cacheProvider;

    @Mock
    private JedisOps oldJedisOps;

    @Mock
    private JedisOps newJedisOps;

    @Mock
    private JedisTransaction newTransaction;

    @Mock
    private JedisTransaction oldTransaction;

    @Captor
    private ArgumentCaptor<String> stringCaptor;

    UserSession session;

    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);

        cacheProvider = new CacheProvider();
        cacheProvider.setOldJedisOps(oldJedisOps);
        cacheProvider.setNewJedisOps(newJedisOps);
        cacheProvider.setSessionExpireInSeconds(10);

        when(newJedisOps.getTransaction()).thenReturn(newTransaction);
        doReturn(newTransaction).when(newTransaction).setex(any(), anyInt(), any());
        doReturn(newTransaction).when(newTransaction).del(any());

        when(oldJedisOps.getTransaction()).thenReturn(oldTransaction);
        doReturn(oldTransaction).when(oldTransaction).setex(any(), anyInt(), any());
        doReturn(oldTransaction).when(oldTransaction).del(any());

        session = stubSession();
    }

    @Test
    public void updateRequestInfoEmptyCache() {
        when(newJedisOps.set(eq(REQUEST_INFO_KEY), any())).thenReturn("OK");

        RequestInfo info = new RequestInfo.Builder().withUserId(USER_ID).build();
        cacheProvider.updateRequestInfo(info);

        verify(newJedisOps).get(REQUEST_INFO_KEY);
        verify(oldJedisOps).get(REQUEST_INFO_KEY);
        verify(newJedisOps).set(eq(REQUEST_INFO_KEY), any());
        verify(oldJedisOps, never()).set(any(), any());
    }

    @Test
    public void updateRequestInfoOldCache() throws Exception {
        RequestInfo info = new RequestInfo.Builder().withUserId(USER_ID).build();

        RequestInfo old = new RequestInfo.Builder().withUserId("oldUserId").build();
        when(oldJedisOps.get(any())).thenReturn(MAPPER.writeValueAsString(old));

        when(newJedisOps.set(eq(REQUEST_INFO_KEY), any())).thenReturn("OK");

        cacheProvider.updateRequestInfo(info);

        verify(newJedisOps).get(REQUEST_INFO_KEY);
        verify(oldJedisOps).get(REQUEST_INFO_KEY);
        verify(newJedisOps).set(eq(REQUEST_INFO_KEY), stringCaptor.capture());
        verify(oldJedisOps, never()).set(any(), any());

        RequestInfo setInfo = MAPPER.readValue(stringCaptor.getValue(), RequestInfo.class);
        assertEquals(USER_ID, setInfo.getUserId());
    }

    @Test
    public void updateRequestInfoNewCache() throws Exception {
        RequestInfo info = new RequestInfo.Builder().withUserId(USER_ID).build();

        RequestInfo old = new RequestInfo.Builder().withUserId("oldUserId").build();
        when(newJedisOps.get(any())).thenReturn(MAPPER.writeValueAsString(old));

        when(newJedisOps.set(eq(REQUEST_INFO_KEY), any())).thenReturn("OK");

        cacheProvider.updateRequestInfo(info);

        verify(newJedisOps).get(REQUEST_INFO_KEY);
        verify(oldJedisOps, never()).get(any());
        verify(newJedisOps).set(eq(REQUEST_INFO_KEY), stringCaptor.capture());
        verify(oldJedisOps, never()).set(any(), any());

        RequestInfo setInfo = MAPPER.readValue(stringCaptor.getValue(), RequestInfo.class);
        assertEquals(USER_ID, setInfo.getUserId());
    }

    @Test
    public void removeRequestInfo() {
        cacheProvider.removeRequestInfo(USER_ID);

        verify(newJedisOps).del(REQUEST_INFO_KEY);
        verify(oldJedisOps).del(REQUEST_INFO_KEY);
    }

    @Test
    public void getRequestInfoNoCache() {
        RequestInfo returned = cacheProvider.getRequestInfo(USER_ID);
        assertNull(returned);

        verify(oldJedisOps).get(REQUEST_INFO_KEY);
        verify(newJedisOps).get(REQUEST_INFO_KEY);
    }

    @Test
    public void getRequestInfoOldCache() throws Exception {
        RequestInfo info = new RequestInfo.Builder().withUserId(USER_ID).build();
        when(oldJedisOps.get(REQUEST_INFO_KEY)).thenReturn(MAPPER.writeValueAsString(info));

        RequestInfo returned = cacheProvider.getRequestInfo(USER_ID);
        assertEquals(info, returned);

        verify(newJedisOps).get(REQUEST_INFO_KEY);
        verify(oldJedisOps).get(REQUEST_INFO_KEY);
    }

    @Test
    public void getRequestInfoNewCache() throws Exception {
        RequestInfo info = new RequestInfo.Builder().withUserId(USER_ID).build();
        when(newJedisOps.get(REQUEST_INFO_KEY)).thenReturn(MAPPER.writeValueAsString(info));

        RequestInfo returned = cacheProvider.getRequestInfo(USER_ID);
        assertEquals(info, returned);

        verify(newJedisOps).get(REQUEST_INFO_KEY);
        verify(oldJedisOps, never()).get(REQUEST_INFO_KEY);
    }

    @Test
    public void setUserSession() throws Exception {
        when(newJedisOps.ttl(USER_ID_SESSION_KEY)).thenReturn(20L);

        cacheProvider.setUserSession(session);

        verify(newJedisOps).ttl(USER_ID_SESSION_KEY);
        verify(newTransaction).setex(eq(USER_ID_SESSION_KEY), eq(20), stringCaptor.capture());
        verify(newTransaction).setex(SESSION_TOKEN_KEY, 20, USER_ID);
        verify(newTransaction).exec();

        UserSession captured = MAPPER.readValue(stringCaptor.getValue(), UserSession.class);
        assertEquals(USER_ID, captured.getId());

        verifyZeroInteractions(oldJedisOps);
    }

    private UserSession stubSession() {
        UserSession session = new UserSession();
        session.setSessionToken(SESSION_TOKEN);
        session.setParticipant(new StudyParticipant.Builder()
                .withId(USER_ID)
                .withHealthCode("healthCode").build());
        return session;
    }

    @Test
    public void getUserSessionTokenToUserIdNoCache() {
        UserSession returned = cacheProvider.getUserSession(SESSION_TOKEN);
        assertNull(returned);

        verify(oldJedisOps).get(SESSION_TOKEN_KEY);
        verify(newJedisOps).get(SESSION_TOKEN_KEY);
    }

    @Test
    public void getUserSessionUserIdToSessionNoCache() {
        when(newJedisOps.get(SESSION_TOKEN_KEY)).thenReturn(USER_ID);

        UserSession returned = cacheProvider.getUserSession(SESSION_TOKEN);
        assertNull(returned);

        verify(oldJedisOps).get(USER_ID_SESSION_KEY);
        verify(newJedisOps).get(USER_ID_SESSION_KEY);
    }

    @Test
    public void getUserSessionOldCache() throws Exception {
        when(oldJedisOps.get(SESSION_TOKEN_KEY)).thenReturn(USER_ID);

        UserSession session = stubSession();
        when(oldJedisOps.get(USER_ID_SESSION_KEY)).thenReturn(MAPPER.writeValueAsString(session));

        UserSession returned = cacheProvider.getUserSession(SESSION_TOKEN);
        assertEquals(USER_ID, returned.getId());

        verify(newJedisOps).get(SESSION_TOKEN_KEY);
        verify(oldJedisOps).get(SESSION_TOKEN_KEY);
        verify(newJedisOps).get(USER_ID_SESSION_KEY);
        verify(oldJedisOps).get(USER_ID_SESSION_KEY);
    }

    @Test
    public void getUserSessionNewCache() throws Exception {
        when(newJedisOps.get(SESSION_TOKEN_KEY)).thenReturn(USER_ID);

        UserSession session = stubSession();
        when(newJedisOps.get(USER_ID_SESSION_KEY)).thenReturn(MAPPER.writeValueAsString(session));

        UserSession returned = cacheProvider.getUserSession(SESSION_TOKEN);
        assertEquals(USER_ID, returned.getId());

        verify(newJedisOps).get(SESSION_TOKEN_KEY);
        verify(oldJedisOps, never()).get(SESSION_TOKEN_KEY);
        verify(newJedisOps).get(USER_ID_SESSION_KEY);
        verify(oldJedisOps, never()).get(USER_ID_SESSION_KEY);
    }

    @Test
    public void getUserSessionByUserIdNoCache() {
        UserSession returned = cacheProvider.getUserSessionByUserId(USER_ID);
        assertNull(returned);

        verify(newJedisOps).get(USER_ID_SESSION_KEY);
        verify(oldJedisOps).get(USER_ID_SESSION_KEY);
    }

    @Test
    public void getUserSessionByUserIdOldCache() throws Exception {
        UserSession session = stubSession();
        when(oldJedisOps.get(USER_ID_SESSION_KEY)).thenReturn(MAPPER.writeValueAsString(session));

        UserSession returned = cacheProvider.getUserSessionByUserId(USER_ID);
        assertEquals(USER_ID, returned.getId());

        verify(newJedisOps).get(USER_ID_SESSION_KEY);
        verify(oldJedisOps).get(USER_ID_SESSION_KEY);
    }

    @Test
    public void getUserSessionByUserIdNewCache() throws Exception {
        UserSession session = stubSession();
        when(newJedisOps.get(USER_ID_SESSION_KEY)).thenReturn(MAPPER.writeValueAsString(session));

        UserSession returned = cacheProvider.getUserSessionByUserId(USER_ID);
        assertEquals(USER_ID, returned.getId());

        verify(newJedisOps).get(USER_ID_SESSION_KEY);
        verify(oldJedisOps, never()).get(USER_ID_SESSION_KEY);
    }

    @Test
    public void removeSession() {
        cacheProvider.removeSession(session);

        verify(newTransaction).del(SESSION_TOKEN_KEY);
        verify(newTransaction).del(USER_ID_SESSION_KEY);
        verify(oldTransaction).del(SESSION_TOKEN_KEY);
        verify(oldTransaction).del(USER_ID_SESSION_KEY);
    }

    @Test
    public void removeSessionByUserId() throws Exception {
        UserSession session = stubSession();
        when(newJedisOps.get(USER_ID_SESSION_KEY)).thenReturn(MAPPER.writeValueAsString(session));

        cacheProvider.removeSessionByUserId(USER_ID);

        verify(newTransaction).del(SESSION_TOKEN_KEY);
        verify(newTransaction).del(USER_ID_SESSION_KEY);
        verify(oldTransaction).del(SESSION_TOKEN_KEY);
        verify(oldTransaction).del(USER_ID_SESSION_KEY);
    }

    @Test
    public void setStudy() throws Exception {
        Study study = Study.create();
        study.setIdentifier(STUDY_ID);
        String ser = MAPPER.writeValueAsString(study);

        when(newJedisOps.setex(any(), anyInt(), any())).thenReturn("OK");

        cacheProvider.setStudy(study);

        verify(newJedisOps).setex(STUDY_ID_KEY, BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS, ser);
        verify(oldJedisOps, never()).setex(STUDY_ID_KEY, BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS, ser);
    }

    @Test
    public void getStudyNoCache() {
        cacheProvider.getStudy(STUDY_ID);

        verify(newJedisOps).get(STUDY_ID_KEY);
        verify(oldJedisOps).get(STUDY_ID_KEY);

        verify(newJedisOps, never()).expire(STUDY_ID_KEY, BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS);
        verify(oldJedisOps, never()).expire(STUDY_ID_KEY, BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS);
    }

    @Test
    public void getStudyOldCache() throws Exception {
        Study study = Study.create();
        study.setIdentifier(STUDY_ID);
        String ser = MAPPER.writeValueAsString(study);

        when(oldJedisOps.get(STUDY_ID_KEY)).thenReturn(ser);

        Study returned = cacheProvider.getStudy(STUDY_ID);
        assertEquals(study, returned);

        verify(newJedisOps).get(STUDY_ID_KEY);
        verify(oldJedisOps).get(STUDY_ID_KEY);

        verify(newJedisOps, never()).expire(STUDY_ID_KEY, BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS);
        verify(oldJedisOps).expire(STUDY_ID_KEY, BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS);
    }

    @Test
    public void getStudyNewCache() throws Exception {
        Study study = Study.create();
        study.setIdentifier(STUDY_ID);
        String ser = MAPPER.writeValueAsString(study);

        when(newJedisOps.get(STUDY_ID_KEY)).thenReturn(ser);

        Study returned = cacheProvider.getStudy(STUDY_ID);
        assertEquals(study, returned);

        verify(newJedisOps).get(STUDY_ID_KEY);
        verify(oldJedisOps, never()).get(STUDY_ID_KEY);

        verify(newJedisOps).expire(STUDY_ID_KEY, BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS);
        verify(oldJedisOps, never()).expire(STUDY_ID_KEY, BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS);
    }

    @Test
    public void removeStudy() {
        cacheProvider.removeStudy(STUDY_ID);

        verify(newJedisOps).del(STUDY_ID_KEY);
        verify(oldJedisOps).del(STUDY_ID_KEY);
    }

    @Test
    public void getObjectByClassNoCache() {
        Study study = cacheProvider.getObject(CacheKey.study(STUDY_ID), Study.class);
        assertNull(study);

        verify(newJedisOps).get(STUDY_ID_KEY);
        verify(oldJedisOps).get(STUDY_ID_KEY);
    }

    @Test
    public void getObjectByClassOldCache() throws Exception {
        Study study = Study.create();
        study.setIdentifier(STUDY_ID);
        when(oldJedisOps.get(STUDY_ID_KEY)).thenReturn(MAPPER.writeValueAsString(study));

        Study returned = cacheProvider.getObject(CacheKey.study(STUDY_ID), Study.class);
        assertEquals(returned, study);

        verify(newJedisOps).get(STUDY_ID_KEY);
        verify(oldJedisOps).get(STUDY_ID_KEY);
    }

    @Test
    public void getObjectByClassNewCache() throws Exception {
        Study study = Study.create();
        study.setIdentifier(STUDY_ID);
        when(newJedisOps.get(STUDY_ID_KEY)).thenReturn(MAPPER.writeValueAsString(study));

        Study returned = cacheProvider.getObject(CacheKey.study(STUDY_ID), Study.class);
        assertEquals(returned, study);

        verify(newJedisOps).get(STUDY_ID_KEY);
        verify(oldJedisOps, never()).get(STUDY_ID_KEY);
    }

    @Test
    public void getObjectByTypeRefNoCache() {
        Study study = cacheProvider.getObject(CacheKey.study(STUDY_ID), STUDY_TYPE_REF);
        assertNull(study);

        verify(newJedisOps).get(STUDY_ID_KEY);
        verify(oldJedisOps).get(STUDY_ID_KEY);
    }

    @Test
    public void getObjectByTypeRefOldCache() throws Exception {
        Study study = Study.create();
        study.setIdentifier(STUDY_ID);
        when(oldJedisOps.get(STUDY_ID_KEY)).thenReturn(MAPPER.writeValueAsString(study));

        Study returned = cacheProvider.getObject(CacheKey.study(STUDY_ID), STUDY_TYPE_REF);
        assertEquals(returned, study);

        verify(newJedisOps).get(STUDY_ID_KEY);
        verify(oldJedisOps).get(STUDY_ID_KEY);
    }

    @Test
    public void getObjectByTypeRefNewCache() throws Exception {
        Study study = Study.create();
        study.setIdentifier(STUDY_ID);
        when(newJedisOps.get(STUDY_ID_KEY)).thenReturn(MAPPER.writeValueAsString(study));

        Study returned = cacheProvider.getObject(CacheKey.study(STUDY_ID), STUDY_TYPE_REF);
        assertEquals(returned, study);

        verify(newJedisOps).get(STUDY_ID_KEY);
        verify(oldJedisOps, never()).get(STUDY_ID_KEY);
    }

    @Test
    public void getObjectWithExpirationNoCache() {
        Study study = cacheProvider.getObject(CacheKey.study(STUDY_ID), Study.class, 20);
        assertNull(study);

        verify(newJedisOps).get(STUDY_ID_KEY);
        verify(newJedisOps, never()).expire(any(), anyInt());
        verify(oldJedisOps).get(STUDY_ID_KEY);
        verify(oldJedisOps, never()).expire(any(), anyInt());
    }

    @Test
    public void getObjectWithExpirationOldCache() throws Exception {
        Study study = Study.create();
        study.setIdentifier(STUDY_ID);
        when(oldJedisOps.get(STUDY_ID_KEY)).thenReturn(MAPPER.writeValueAsString(study));

        Study returned = cacheProvider.getObject(CacheKey.study(STUDY_ID), Study.class, 20);
        assertEquals(returned, study);

        verify(newJedisOps).get(STUDY_ID_KEY);
        verify(newJedisOps, never()).expire(any(), anyInt());
        verify(oldJedisOps).get(STUDY_ID_KEY);
        verify(oldJedisOps).expire(STUDY_ID_KEY, 20);
    }

    @Test
    public void getObjectWithExpirationNewCache() throws Exception {
        Study study = Study.create();
        study.setIdentifier(STUDY_ID);
        when(newJedisOps.get(STUDY_ID_KEY)).thenReturn(MAPPER.writeValueAsString(study));

        Study returned = cacheProvider.getObject(CacheKey.study(STUDY_ID), Study.class, 20);
        assertEquals(returned, study);

        verify(newJedisOps).get(STUDY_ID_KEY);
        verify(newJedisOps).expire(STUDY_ID_KEY, 20);
        verify(oldJedisOps, never()).get(STUDY_ID_KEY);
        verify(oldJedisOps, never()).expire(any(), anyInt());
    }

    @Test
    public void setObject() throws Exception {
        when(newJedisOps.set(eq(STUDY_ID_KEY), any())).thenReturn("OK");

        Study study = Study.create();
        study.setIdentifier(STUDY_ID);
        String ser = MAPPER.writeValueAsString(study);
        cacheProvider.setObject(CacheKey.study(STUDY_ID), study);

        verify(newJedisOps).set(STUDY_ID_KEY, ser);
        verify(oldJedisOps, never()).set(any(), any());
    }

    @Test
    public void setObjectWithExpiration() throws Exception {
        when(newJedisOps.setex(eq(STUDY_ID_KEY), anyInt(), any())).thenReturn("OK");

        Study study = Study.create();
        study.setIdentifier(STUDY_ID);
        String ser = MAPPER.writeValueAsString(study);
        cacheProvider.setObject(CacheKey.study(STUDY_ID), study, 20);

        verify(newJedisOps).setex(STUDY_ID_KEY, 20, ser);
        verify(oldJedisOps, never()).setex(any(), anyInt(), any());
    }

    @Test
    public void removeObject() {
        cacheProvider.removeObject(CacheKey.study(STUDY_ID));
        verify(newJedisOps).del(STUDY_ID_KEY);
        verify(oldJedisOps).del(STUDY_ID_KEY);
    }

    @Test
    public void addCacheKeyToSet() {
        cacheProvider.addCacheKeyToSet(CacheKey.study(STUDY_ID), "dummy key");
        verify(newJedisOps).sadd(STUDY_ID_KEY, "dummy key");
        verify(oldJedisOps, never()).sadd(any(), any());
    }

    @Test
    public void removeSetOfCacheKeys() {
        when(newJedisOps.smembers(STUDY_ID_KEY)).thenReturn(ImmutableSet.of("foo key", "bar key"));
        when(oldJedisOps.smembers(STUDY_ID_KEY)).thenReturn(ImmutableSet.of("asdf key", "jkl; key"));

        cacheProvider.removeSetOfCacheKeys(CacheKey.study(STUDY_ID));

        verify(newTransaction).del("foo key");
        verify(newTransaction).del("bar key");
        verify(newTransaction).del(STUDY_ID_KEY);
        verify(newTransaction).exec();

        verify(oldTransaction).del("asdf key");
        verify(oldTransaction).del("jkl; key");
        verify(oldTransaction).del(STUDY_ID_KEY);
        verify(oldTransaction).exec();
    }
}
