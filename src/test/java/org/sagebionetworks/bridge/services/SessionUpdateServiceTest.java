package org.sagebionetworks.bridge.services;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.models.accounts.SharingScope.ALL_QUALIFIED_RESEARCHERS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.joda.time.DateTimeZone;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.models.CriteriaContext;
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
        session.setParticipant(EMPTY_PARTICIPANT);

        List<String> languages = ImmutableList.of("es");
        CriteriaContext context = new CriteriaContext.Builder().withStudyIdentifier(TEST_STUDY)
                .withLanguages(languages).build();

        // Execute test.
        service.updateLanguage(session, context);

        // Verify consent service.
        verify(mockConsentService).getConsentStatuses(context);

        // Verify saved session.
        verify(mockCacheProvider).setUserSession(session);
        assertEquals(session.getParticipant().getLanguages().iterator().next(), "es");
        assertSame(session.getConsentStatuses(), CONSENT_STATUS_MAP);

        // Verify notification service.
        verify(mockNotificationTopicService).manageCriteriaBasedSubscriptions(TEST_STUDY, context, HEALTH_CODE);
    }

    @Test
    public void updateExternalId() {
        UserSession session = new UserSession();
        ExternalIdentifier externalId = ExternalIdentifier.create(TEST_STUDY, "someExternalId");
        
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
        session.setParticipant(EMPTY_PARTICIPANT);

        CriteriaContext context = new CriteriaContext.Builder().withStudyIdentifier(TEST_STUDY).build();

        // Execute test.
        service.updateParticipant(session, context, EMPTY_PARTICIPANT);

        // Verify consent service.
        verify(mockConsentService).getConsentStatuses(context);

        // Verify saved session.
        verify(mockCacheProvider).setUserSession(session);
        assertEquals(session.getParticipant(), EMPTY_PARTICIPANT);
        assertSame(session.getConsentStatuses(), CONSENT_STATUS_MAP);

        // Verify notification service.
        verify(mockNotificationTopicService).manageCriteriaBasedSubscriptions(TEST_STUDY, context, HEALTH_CODE);
    }
    
    @Test
    public void updateParticipantWithConsentUpdate() {
        UserSession session = new UserSession();
        StudyParticipant participant = new StudyParticipant.Builder().build();
        CriteriaContext context = new CriteriaContext.Builder().withStudyIdentifier(TEST_STUDY).build();
        Map<SubpopulationGuid,ConsentStatus> statuses = Maps.newHashMap();
                
        when(mockConsentService.getConsentStatuses(context)).thenReturn(statuses);
        
        service.updateParticipant(session, context, participant);
        
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
        session.setParticipant(EMPTY_PARTICIPANT);

        Set<String> dataGroups = Sets.newHashSet("data1");
        CriteriaContext context = new CriteriaContext.Builder()
                .withUserDataGroups(dataGroups)
                .withStudyIdentifier(TEST_STUDY).build();

        // Execute test.
        service.updateDataGroups(session, context);

        // Verify consent service.
        verify(mockConsentService).getConsentStatuses(context);

        // Verify saved session.
        verify(mockCacheProvider).setUserSession(session);
        assertEquals(session.getParticipant().getDataGroups(), dataGroups);
        assertSame(session.getConsentStatuses(), CONSENT_STATUS_MAP);

        // Verify notification service.
        verify(mockNotificationTopicService).manageCriteriaBasedSubscriptions(TEST_STUDY, context, HEALTH_CODE);
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
