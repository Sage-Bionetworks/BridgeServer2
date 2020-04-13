package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.BRIDGE_VIEW_EXPIRE_IN_SECONDS;
import static org.sagebionetworks.bridge.BridgeUtils.setRequestContext;
import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.HEALTH_CODE;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.USER_DATA_GROUPS;
import static org.sagebionetworks.bridge.TestConstants.USER_ID;
import static org.sagebionetworks.bridge.TestConstants.USER_SUBSTUDY_IDS;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.createJson;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.cache.ViewCache;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.services.ConsentService;
import org.sagebionetworks.bridge.services.NotificationTopicService;
import org.sagebionetworks.bridge.services.ParticipantService;
import org.sagebionetworks.bridge.services.SessionUpdateService;
import org.sagebionetworks.bridge.services.StudyService;

public class UserProfileControllerTest extends Mockito {
    
    private static final Map<SubpopulationGuid,ConsentStatus> CONSENT_STATUSES_MAP = Maps.newHashMap();
    private static final Set<String> TEST_STUDY_DATA_GROUPS = Sets.newHashSet("group1", "group2");
    private static final Set<String> TEST_STUDY_ATTRIBUTES = Sets.newHashSet("foo","bar"); 
    
    @Mock
    AccountService mockAccountService;
    
    @Mock
    ConsentService mockConsentService;
    
    @Mock
    CacheProvider mockCacheProvider;
    
    @Mock
    StudyService mockStudyService;
    
    @Mock
    ParticipantService mockParticipantService;
    
    @Mock
    NotificationTopicService mockNotificationTopicService;
    
    @Mock
    Account mockAccount;
    
    @Mock
    HttpServletRequest mockRequest;
    
    @Mock
    HttpServletResponse mockResponse;
    
    @Captor
    ArgumentCaptor<StudyParticipant> participantCaptor;
    
    @Captor
    ArgumentCaptor<Set<String>> stringSetCaptor;
    
    @Captor
    ArgumentCaptor<CriteriaContext> contextCaptor;
    
    @Captor
    ArgumentCaptor<UserSession> sessionCaptor;
    
    @Captor
    ArgumentCaptor<AccountId> accountIdCaptor;
    
    @Captor
    ArgumentCaptor<ExternalIdentifier> externalIdCaptor;
    
    UserSession session;
    
    Study study;
    
    @Spy
    @InjectMocks
    UserProfileController controller;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        
        study = new DynamoStudy();
        study.setIdentifier(TEST_APP_ID);
        study.setDataGroups(USER_DATA_GROUPS);
        study.setUserProfileAttributes(TEST_STUDY_ATTRIBUTES);

        when(mockConsentService.getConsentStatuses(any())).thenReturn(CONSENT_STATUSES_MAP);
        
        when(mockStudyService.getStudy((String)any())).thenReturn(study);
        
        ViewCache viewCache = new ViewCache();
        viewCache.setCachePeriod(BRIDGE_VIEW_EXPIRE_IN_SECONDS);
        viewCache.setObjectMapper(BridgeObjectMapper.get());
        viewCache.setCacheProvider(mockCacheProvider);
        controller.setViewCache(viewCache);
        
        SessionUpdateService sessionUpdateService = new SessionUpdateService();
        sessionUpdateService.setCacheProvider(mockCacheProvider);
        sessionUpdateService.setConsentService(mockConsentService);
        sessionUpdateService.setNotificationTopicService(mockNotificationTopicService);
        controller.setSessionUpdateService(sessionUpdateService);
        
        session = new UserSession(new StudyParticipant.Builder()
                .withHealthCode(HEALTH_CODE)
                .withId(USER_ID)
                .build());
        session.setStudyIdentifier(TEST_APP_ID);
        
        doReturn(session).when(controller).getAuthenticatedSession();
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
    }
    
    @AfterMethod
    public void after() {
        setRequestContext(NULL_INSTANCE);
    }
    
    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(UserProfileController.class);
        assertGet(UserProfileController.class, "getUserProfile");
        assertPost(UserProfileController.class, "updateUserProfile");
        assertGet(UserProfileController.class, "getDataGroups");
        assertPost(UserProfileController.class, "updateDataGroups");
    }
    
    @Test
    @SuppressWarnings("deprecation")
    public void getUserProfile() throws Exception {
        Map<String,String> attributes = Maps.newHashMap();
        attributes.put("bar","baz");
        StudyParticipant participant = new StudyParticipant.Builder().withLastName("Last")
                .withFirstName("First").withEmail(EMAIL).withAttributes(attributes).build();
        
        doReturn(participant).when(mockParticipantService).getParticipant(study, USER_ID, false);
        
        String result = controller.getUserProfile();
        JsonNode node = BridgeObjectMapper.get().readTree(result);
        
        verify(mockParticipantService).getParticipant(study, USER_ID, false);
        
        assertEquals(node.get("firstName").textValue(), "First");
        assertEquals("Last", node.get("lastName").textValue());
        assertEquals(node.get("email").textValue(), EMAIL);
        assertEquals(node.get("username").textValue(), EMAIL);
        assertEquals(node.get("bar").textValue(), "baz");
        assertEquals(node.get("type").textValue(), "UserProfile");
    }
    
    @Test
    @SuppressWarnings("deprecation")    
    public void getUserProfileWithNoName() throws Exception {
        Map<String,String> attributes = Maps.newHashMap();
        attributes.put("bar","baz");
        StudyParticipant participant = new StudyParticipant.Builder().withEmail(EMAIL).withAttributes(attributes)
                .build();
        
        doReturn(participant).when(mockParticipantService).getParticipant(study, USER_ID, false);
        
        String result = controller.getUserProfile();
        JsonNode node = BridgeObjectMapper.get().readTree(result);
        
        verify(mockParticipantService).getParticipant(study, USER_ID, false);
        
        assertFalse(node.has("firstName"));
        assertFalse(node.has("lastName"));
        assertEquals(node.get("email").textValue(), EMAIL);
        assertEquals(node.get("username").textValue(), EMAIL);
        assertEquals(node.get("bar").textValue(), "baz");
        assertEquals(node.get("type").textValue(), "UserProfile");
    }
    
    @Test
    @SuppressWarnings("deprecation")
    public void updateUserProfile() throws Exception {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerSubstudies(
                ImmutableSet.of("substudyA")).build());
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withHealthCode("existingHealthCode")
                .withExternalId("originalId")
                .withFirstName("OldFirstName")
                .withLastName("OldLastName")
                .withSubstudyIds(ImmutableSet.of("substudyA"))
                .withId(USER_ID).build();
        
        StudyParticipant updatedParticipant = new StudyParticipant.Builder()
                .copyOf(participant)
                .withFirstName("First")
                .withLastName("Last").build();
        
        doReturn(participant, updatedParticipant).when(mockParticipantService).getParticipant(eq(study), eq(USER_ID), anyBoolean());
        
        // This has a field that should not be passed to the StudyParticipant, because it didn't exist before
        // (externalId)
        mockRequestBody(mockRequest, createJson("{'firstName':'First','lastName':'Last',"+
                "'username':'email@email.com','foo':'belgium','externalId':'updatedId','type':'UserProfile'}"));
        
        JsonNode result = controller.updateUserProfile();
        
        assertEquals(result.get("firstName").textValue(), "First");
        assertEquals(result.get("lastName").textValue(), "Last");
        assertEquals(result.get("externalId").textValue(), "originalId");
                
        // Verify that existing user information (health code) has been retrieved and used when updating session
        InOrder inOrder = inOrder(mockParticipantService);
        inOrder.verify(mockParticipantService).getParticipant(study, USER_ID, false);
        inOrder.verify(mockParticipantService).updateParticipant(eq(study), participantCaptor.capture());
        inOrder.verify(mockParticipantService).getParticipant(study, USER_ID, true);
        
        StudyParticipant persisted = participantCaptor.getValue();
        assertEquals(persisted.getHealthCode(), "existingHealthCode");
        assertEquals(persisted.getExternalId(), "originalId");
        assertEquals(persisted.getId(), USER_ID);
        assertEquals(persisted.getFirstName(), "First");
        assertEquals(persisted.getLastName(), "Last");
        assertEquals(persisted.getAttributes().get("foo"), "belgium");
    }
    
    @Test
    @SuppressWarnings("deprecation")
    public void validDataGroupsCanBeAdded() throws Exception {
        setRequestContext(new RequestContext.Builder().withCallerSubstudies(
                ImmutableSet.of("substudyA")).build());
        
        // We had a bug where this call lost the health code in the user's session, so verify in particular 
        // that healthCode (as well as something like firstName) are in the session. 
        StudyParticipant existing = new StudyParticipant.Builder()
                .withHealthCode(HEALTH_CODE)
                .withId(USER_ID)
                .withSubstudyIds(USER_SUBSTUDY_IDS) // which includes substudyA
                .withFirstName("First").build();
        doReturn(existing).when(mockParticipantService).getParticipant(study, USER_ID, false);
        session.setParticipant(existing);
        
        Set<String> dataGroupSet = ImmutableSet.of("group1");
        mockRequestBody(mockRequest, "{\"dataGroups\":[\"group1\"]}");

        JsonNode result = controller.updateDataGroups();
        
        assertEquals(result.get("firstName").textValue(), "First");
        assertEquals(result.get("dataGroups").get(0).textValue(), "group1");
        
        verify(mockParticipantService).updateParticipant(eq(study), participantCaptor.capture());
        verify(mockConsentService).getConsentStatuses(contextCaptor.capture());
        
        StudyParticipant participant = participantCaptor.getValue();
        assertEquals(participant.getId(), USER_ID);
        assertEquals(participant.getDataGroups(), dataGroupSet);
        assertEquals(participant.getFirstName(), "First");
        assertEquals(participant.getSubstudyIds(), USER_SUBSTUDY_IDS);
        assertEquals(contextCaptor.getValue().getUserDataGroups(), dataGroupSet);
        assertEquals(contextCaptor.getValue().getUserSubstudyIds(), USER_SUBSTUDY_IDS);
        
        // Session continues to be initialized
        assertEquals(session.getParticipant().getDataGroups(), dataGroupSet);
        assertEquals(session.getParticipant().getSubstudyIds(), USER_SUBSTUDY_IDS);
        assertEquals(session.getParticipant().getHealthCode(), HEALTH_CODE);
        assertEquals(session.getParticipant().getFirstName(), "First");
    }
    
    // Validation is no longer done in the controller, but verify that user is not changed
    // when the service throws an InvalidEntityException.
    @Test
    @SuppressWarnings("deprecation")
    public void invalidDataGroupsRejected() throws Exception {
        StudyParticipant existing = new StudyParticipant.Builder().withFirstName("First").build();
        doReturn(existing).when(mockParticipantService).getParticipant(study, USER_ID, false);
        doThrow(new InvalidEntityException("Invalid data groups")).when(mockParticipantService).updateParticipant(eq(study),
                any());
        
        mockRequestBody(mockRequest, "{\"dataGroups\":[\"completelyInvalidGroup\"]}");
        try {
            controller.updateDataGroups();
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertEquals(Sets.newHashSet(), session.getParticipant().getDataGroups());
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    public void canGetDataGroups() throws Exception {
        when(mockAccount.getDataGroups()).thenReturn(ImmutableSet.of("group1","group2"));
        when(mockAccountService.getAccount(any())).thenReturn(mockAccount);
        
        JsonNode result = controller.getDataGroups();
        
        assertEquals(result.get("type").textValue(), "DataGroups");
        ArrayNode array = (ArrayNode)result.get("dataGroups");
        assertEquals(array.size(), 2);
        for (int i=0; i < array.size(); i++) {
            TEST_STUDY_DATA_GROUPS.contains(array.get(i).textValue());
        }
    }
    
    @SuppressWarnings({ "deprecation" })
    @Test
    public void evenEmptyJsonActsOK() throws Exception {
        setRequestContext(new RequestContext.Builder().withCallerSubstudies(ImmutableSet.of("substudyA")).build());
        
        StudyParticipant existing = new StudyParticipant.Builder()
                .withHealthCode(HEALTH_CODE)
                .withId(USER_ID)
                .withSubstudyIds(ImmutableSet.of("substudyA"))
                .withFirstName("First").build();
        doReturn(existing).when(mockParticipantService).getParticipant(study, USER_ID, false);
        session.setParticipant(existing);
        
        mockRequestBody(mockRequest, "{}");
        
        JsonNode result = controller.updateDataGroups();
        
        assertEquals(result.get("firstName").textValue(), "First");
        
        verify(mockParticipantService).updateParticipant(eq(study), participantCaptor.capture());
        
        StudyParticipant updated = participantCaptor.getValue();
        assertEquals(updated.getId(), USER_ID);
        assertTrue(updated.getDataGroups().isEmpty());
        assertEquals(updated.getFirstName(), "First");
    }
}
