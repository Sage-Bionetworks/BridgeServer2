package org.sagebionetworks.bridge.services;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.BridgeConstants.API_STUDY_ID;
import static org.sagebionetworks.bridge.models.accounts.SharingScope.ALL_QUALIFIED_RESEARCHERS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.joda.time.DateTimeZone;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class SessionUpdateServiceTest {
    private static final String HEALTH_CODE = "health-code";
    private static final StudyParticipant EMPTY_PARTICIPANT = new StudyParticipant.Builder()
            .withHealthCode(HEALTH_CODE).build();

    private static final SubpopulationGuid SUBPOP_GUID = SubpopulationGuid.create("consentA");
    private static final ConsentStatus CONSENT_STATUS = new ConsentStatus.Builder().withName("consentA")
            .withGuid(SUBPOP_GUID).withConsented(true).withSignedMostRecentConsent(true).build();
    private static final Map<SubpopulationGuid, ConsentStatus> CONSENT_STATUS_MAP = ImmutableMap.of(SUBPOP_GUID,
            CONSENT_STATUS);

    @Mock
    private ConsentService mockConsentService;
    
    @Mock
    private CacheProvider mockCacheProvider;

    @Mock
    private NotificationTopicService mockNotificationTopicService;
    
    @Mock
    private UserSession updatedSession;
    
    @Captor
    ArgumentCaptor<RequestContext> requestContextCaptor;

    
    private SessionUpdateService service;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        
        service = new SessionUpdateService();
        service.setConsentService(mockConsentService);
        service.setCacheProvider(mockCacheProvider);
        service.setNotificationTopicService(mockNotificationTopicService);
    }
    
    @Test
    public void updateTimeZone() {
        UserSession session = new UserSession();
        DateTimeZone timeZone = DateTimeZone.forOffsetHours(-7);
        
        service.updateTimeZone(session, timeZone);
        
        verify(mockCacheProvider).setUserSession(session);
        assertEquals(session.getParticipant().getTimeZone(), timeZone);
    }
    
    @Test
    public void updateLanguage() {
        // Mock consent service to return dummy consents.
        when(mockConsentService.getConsentStatuses(any())).thenReturn(CONSENT_STATUS_MAP);

        // Create inputs.
        UserSession session = new UserSession();
        session.setStudyIdentifier(API_STUDY_ID);
        session.setParticipant(EMPTY_PARTICIPANT);

        List<String> languages = ImmutableList.of("es");

        // Execute test.
        service.updateLanguage(session, languages);

        // Verify consent service.
        verify(mockConsentService).getConsentStatuses(requestContextCaptor.capture());
        
        // Verify saved session.
        verify(mockCacheProvider).setUserSession(session);
        assertEquals(session.getParticipant().getLanguages().iterator().next(), "es");
        assertSame(session.getConsentStatuses(), CONSENT_STATUS_MAP);

        // Verify notification service.
        verify(mockNotificationTopicService).manageCriteriaBasedSubscriptions(requestContextCaptor.capture());
        
        RequestContext rq1 = requestContextCaptor.getAllValues().get(0);
        assertEquals(rq1.getCallerLanguages(), languages);
        
        RequestContext rq2 = requestContextCaptor.getAllValues().get(1);
        assertEquals(rq2.getCallerLanguages(), languages);
    }

    @Test
    public void updateExternalId() {
        UserSession session = new UserSession();
        ExternalIdentifier externalId = ExternalIdentifier.create(API_STUDY_ID, "someExternalId");
        
        service.updateExternalId(session, externalId);
        
        verify(mockCacheProvider).setUserSession(session);
        assertEquals(session.getParticipant().getExternalId(), "someExternalId");
    }
    
    @Test
    public void updateParticipant() {
        // Mock consent service to return dummy consents.
        when(mockConsentService.getConsentStatuses(any())).thenReturn(CONSENT_STATUS_MAP);

        // Create inputs.
        UserSession session = new UserSession();
        session.setStudyIdentifier(API_STUDY_ID);
        session.setParticipant(EMPTY_PARTICIPANT);

        // Execute test.
        service.updateParticipant(session, EMPTY_PARTICIPANT);

        // Verify consent service.
        verify(mockConsentService).getConsentStatuses(requestContextCaptor.capture());

        // Verify saved session.
        verify(mockCacheProvider).setUserSession(session);
        assertEquals(session.getParticipant(), EMPTY_PARTICIPANT);
        assertSame(session.getConsentStatuses(), CONSENT_STATUS_MAP);

        // Verify notification service.
        verify(mockNotificationTopicService).manageCriteriaBasedSubscriptions(requestContextCaptor.capture());
        
        RequestContext rq1 = requestContextCaptor.getAllValues().get(0);
        assertEquals(rq1.getCallerStudyIdentifier(), API_STUDY_ID);
        assertEquals(rq1.getCallerHealthCode(), HEALTH_CODE);
        
        RequestContext rq2 = requestContextCaptor.getAllValues().get(0);
        assertEquals(rq2.getCallerStudyIdentifier(), API_STUDY_ID);
        assertEquals(rq2.getCallerHealthCode(), HEALTH_CODE);
    }
    
    @Test
    public void updateParticipantWithConsentUpdate() {
        UserSession session = new UserSession();
        StudyParticipant participant = new StudyParticipant.Builder().withHealthCode(HEALTH_CODE).build();
        RequestContext context = new RequestContext.Builder().withCallerStudyId(API_STUDY_ID).build();
        Map<SubpopulationGuid,ConsentStatus> statuses = Maps.newHashMap();
                
        when(mockConsentService.getConsentStatuses(context)).thenReturn(statuses);
        
        service.updateParticipant(session, participant);
        
        verify(mockCacheProvider).setUserSession(session);
        assertEquals(session.getParticipant(), participant);
        assertEquals(session.getConsentStatuses(), statuses);
    }
    
    @Test
    public void updateDataGroups() {
        // Mock consent service to return dummy consents.
        when(mockConsentService.getConsentStatuses(any())).thenReturn(CONSENT_STATUS_MAP);

        // Create inputs.
        UserSession session = new UserSession();
        session.setStudyIdentifier(API_STUDY_ID);
        session.setParticipant(EMPTY_PARTICIPANT);

        Set<String> dataGroups = Sets.newHashSet("data1");

        // Execute test.
        service.updateDataGroups(session, dataGroups);

        // Verify consent service.
        verify(mockConsentService).getConsentStatuses(requestContextCaptor.capture());
        assertEquals(requestContextCaptor.getValue().getCallerHealthCode(), HEALTH_CODE);
        assertEquals(requestContextCaptor.getValue().getCallerDataGroups(), dataGroups);
        assertEquals(requestContextCaptor.getValue().getCallerStudyIdentifier(), API_STUDY_ID);

        // Verify saved session.
        verify(mockCacheProvider).setUserSession(session);
        assertEquals(session.getParticipant().getDataGroups(), dataGroups);
        assertSame(session.getConsentStatuses(), CONSENT_STATUS_MAP);

        // Verify notification service.
        verify(mockNotificationTopicService).manageCriteriaBasedSubscriptions(requestContextCaptor.capture());
    }

    @Test
    public void updateStudy() {
        UserSession session = new UserSession();
        session.setStudyIdentifier(TestConstants.TEST_STUDY);
        
        StudyIdentifier newStudy = new StudyIdentifierImpl("new-study");
        
        service.updateStudy(session, newStudy);
        
        verify(mockCacheProvider).setUserSession(session);
        assertEquals(session.getStudyIdentifier(), newStudy);
    }
    
    @Test
    public void updateSession() {
        UserSession oldSession = new UserSession();
        oldSession.setSessionToken("oldSessionToken");
        oldSession.setInternalSessionToken("oldInternalSessionToken");
        
        service.updateSession(oldSession, updatedSession);
        
        verify(mockCacheProvider).setUserSession(updatedSession);
        
        verify(updatedSession).setSessionToken("oldSessionToken");
        verify(updatedSession).setInternalSessionToken("oldInternalSessionToken");
        // and nothing else is set in the updated session.
        verifyNoMoreInteractions(updatedSession);
    }
    
    @Test
    public void updateSharingScope() {
        UserSession session = new UserSession();
        
        service.updateSharingScope(session, ALL_QUALIFIED_RESEARCHERS);
        
        verify(mockCacheProvider).setUserSession(session);
        assertEquals(session.getParticipant().getSharingScope(), ALL_QUALIFIED_RESEARCHERS);
    }
}
