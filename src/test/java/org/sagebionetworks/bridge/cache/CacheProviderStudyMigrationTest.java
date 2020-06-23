package org.sagebionetworks.bridge.cache;

import static org.testng.Assert.assertEquals;

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.itp.IntentToParticipate;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.redis.JedisOps;

public class CacheProviderStudyMigrationTest extends Mockito {
    private static final TypeReference<List<Subpopulation>> SURVEY_LIST_REF = new TypeReference<List<Subpopulation>>() {};
    
    @Mock
    JedisOps mockJedisOps;
    
    @InjectMocks
    CacheProvider provider;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        provider.setSessionExpireInSeconds(10);
    }

    @Test
    public void requestInfoConvertedWithAppId() throws Exception {
        String json = TestUtils.createJson("{'userId':'userId','timeZone':'UTC',"+
                "'appId':'"+TEST_APP_ID+"'}");
        CacheKey key = CacheKey.requestInfo("userId");
        when(mockJedisOps.get(key.toString())).thenReturn(json);
        
        RequestInfo info = provider.getRequestInfo("userId");
        
        assertEquals(info.getAppId(), TEST_APP_ID);
    }

    @Test
    public void requestInfoConvertedWithStudyIdentifier() throws Exception {
        String json = TestUtils.createJson("{'userId':'userId','timeZone':'UTC',"+
                "'studyIdentifier':'"+TEST_APP_ID+"'}");
        CacheKey key = CacheKey.requestInfo("userId");
        when(mockJedisOps.get(key.toString())).thenReturn(json);
        
        RequestInfo info = provider.getRequestInfo("userId");
        
        assertEquals(info.getAppId(), TEST_APP_ID);
    }
    
    @Test
    public void userSesssionWithStudyIdentifier() { 
        String json = TestUtils.createJson(
                "{'studyIdentifier':'"+TEST_APP_ID+"','sessionToken':'aToken'}");

        doReturn("aUser").when(mockJedisOps).get("aToken:session2");
        doReturn(json).when(mockJedisOps).get("aUser:session2:user");
        
        UserSession session = provider.getUserSession("aToken");
        assertEquals(session.getAppId(), TEST_APP_ID);
    }
    
    @Test
    public void userSessionWithAppId() {
        String json = TestUtils.createJson(
                "{'appId':'"+TEST_APP_ID+"','sessionToken':'aToken'}");

        doReturn("aUser").when(mockJedisOps).get("aToken:session2");
        doReturn(json).when(mockJedisOps).get("aUser:session2:user");
        
        UserSession session = provider.getUserSession("aToken");
        assertEquals(session.getAppId(), TEST_APP_ID);
    }
    
    @Test
    public void intentToParticipanteWithStudyId() throws Exception {
        String json = TestUtils.createJson("{'studyId':'"+TEST_APP_ID+
                "','email':'email@email.com','subpopGuid':'subpopGuid',"+
                "'scope':'all_qualified_researchers',"+
                "'type':'IntentToParticipate'}");
        
        CacheKey key = CacheKey.itp(SubpopulationGuid.create("subpopGuid"), 
                TEST_APP_ID, "email@email.com");
        when(mockJedisOps.get(key.toString())).thenReturn(json);
        
        IntentToParticipate itp = provider.getObject(key, IntentToParticipate.class);
        assertEquals(itp.getAppId(), TEST_APP_ID);
    }
    
    @Test
    public void intentToParticipanteWithAppId() throws Exception {
        String json = TestUtils.createJson("{'appId':'"+TEST_APP_ID+
                "','email':'email@email.com','subpopGuid':'subpopGuid',"+
                "'scope':'all_qualified_researchers',"+
                "'type':'IntentToParticipate'}");
        
        CacheKey key = CacheKey.itp(SubpopulationGuid.create("subpopGuid"), 
                TEST_APP_ID, "email@email.com");
        when(mockJedisOps.get(key.toString())).thenReturn(json);
        
        IntentToParticipate itp = provider.getObject(key, IntentToParticipate.class);
        assertEquals(itp.getAppId(), TEST_APP_ID);
    }
    
    @Test
    public void subpopulationWithStudyIdentifier() throws Exception {
        String json = TestUtils.createJson("{'studyIdentifier':'"+TEST_APP_ID+"','type':'Subpopulation'}");
        CacheKey key = CacheKey.subpop(SubpopulationGuid.create("subpopGuid"), TEST_APP_ID);
        when(mockJedisOps.get(key.toString())).thenReturn(json);
        
        Subpopulation subpop = provider.getObject(key, Subpopulation.class);
        assertEquals(subpop.getAppId(), TEST_APP_ID);
    }
    
    @Test
    public void subpopulationWithAppId() throws Exception {
        String json = TestUtils.createJson("{'appId':'"+TEST_APP_ID+"','type':'Subpopulation'}");
        CacheKey key = CacheKey.subpop(SubpopulationGuid.create("subpopGuid"), TEST_APP_ID);
        when(mockJedisOps.get(key.toString())).thenReturn(json);
        
        Subpopulation subpop = provider.getObject(key, Subpopulation.class);
        assertEquals(subpop.getAppId(), TEST_APP_ID);
    }
    
    @Test
    public void subpopulationListWithStudyId() {
        String json = TestUtils.createJson("[{'studyIdentifier':'"+TEST_APP_ID+"','type':'Subpopulation'}]");
        CacheKey key = CacheKey.subpopList(TEST_APP_ID);
        when(mockJedisOps.get(key.toString())).thenReturn(json);
        
        List<Subpopulation> subpops = provider.getObject(key, SURVEY_LIST_REF);
        assertEquals(subpops.get(0).getAppId(), TEST_APP_ID);
    }
    
    @Test
    public void subpopulationListWithAppId() {
        String json = TestUtils.createJson("[{'appId':'"+TEST_APP_ID+"','type':'Subpopulation'}]");
        CacheKey key = CacheKey.subpopList(TEST_APP_ID);
        when(mockJedisOps.get(key.toString())).thenReturn(json);
        
        List<Subpopulation> subpops = provider.getObject(key, SURVEY_LIST_REF);
        assertEquals(subpops.get(0).getAppId(), TEST_APP_ID);
    }
}
