package org.sagebionetworks.bridge.services;

import java.util.Map;
import java.util.Set;

import org.joda.time.DateTimeZone;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

/**
 * Service that updates the state of a user's session, eventually its dependencies as well (the state of the 
 * user's push notification topic subscriptions, and the session as it is cached). Changes that update a 
 * user's session should go through this service to ensure dependencies are handled correctly.
 */
@Component
public class SessionUpdateService {
    
    private CacheProvider cacheProvider;
    private ConsentService consentService;
    private NotificationTopicService notificationTopicService;
    private SponsorService sponsorService;
    
    @Autowired
    public final void setCacheProvider(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }
    @Autowired
    public final void setConsentService(ConsentService consentService) {
        this.consentService = consentService;
    }

    /** Notification topic service, used to manage auto-managed Push and SMS notifications. */
    @Autowired
    public final void setNotificationTopicService(NotificationTopicService notificationTopicService) {
        this.notificationTopicService = notificationTopicService;
    }
    
    @Autowired
    public final void setSponsorService(SponsorService sponsorService) { 
        this.sponsorService = sponsorService;
    }

    public void updateTimeZone(UserSession session, DateTimeZone timeZone) {
        session.setParticipant(builder(session).withTimeZone(timeZone).build());
        cacheProvider.setUserSession(session);
    }
    
    public void updateApp(UserSession session, String appId) {
        session.setAppId(appId);
        cacheProvider.setUserSession(session);
    }

    public void updateLanguage(UserSession session, CriteriaContext context) {
        updateCriteria(session, context, builder(session).withLanguages(context.getLanguages()).build());
    }
    
    public void updateExternalId(UserSession session, ExternalIdentifier externalId) {
        session.setParticipant(builder(session).withExternalId(externalId.getIdentifier()).build());
        cacheProvider.setUserSession(session);
    }
    
    public void updateParticipant(UserSession session, CriteriaContext context, StudyParticipant participant) {
        updateCriteria(session, context, participant);
    }
    
    public void updateDataGroups(UserSession session, CriteriaContext context) {
        updateCriteria(session, context, builder(session).withDataGroups(context.getUserDataGroups()).build());
    }

    private void updateCriteria(UserSession session, CriteriaContext context, StudyParticipant participant) {
        // Update session and consent statuses.
        session.setParticipant(participant);
        Map<SubpopulationGuid,ConsentStatus> statuses = consentService.getConsentStatuses(context);
        session.setConsentStatuses(statuses);
        cacheProvider.setUserSession(session);

        // Manage notifications, if necessary.
        notificationTopicService.manageCriteriaBasedSubscriptions(context.getAppId(), context,
                participant.getHealthCode());
    }
    
    public void updateSharingScope(UserSession session, SharingScope sharingScope) {
        session.setParticipant(builder(session).withSharingScope(sharingScope).build());
        
        cacheProvider.setUserSession(session);
    }
    
    public void updateSession(UserSession oldSession, UserSession newSession) {
        newSession.setSessionToken(oldSession.getSessionToken());
        newSession.setInternalSessionToken(oldSession.getInternalSessionToken());
        
        cacheProvider.setUserSession(newSession);
    }
    
    public void updateOrgMembership(String userId, String newOrgId) {
        UserSession session = cacheProvider.getUserSessionByUserId(userId);
        if (session != null) {
            session.setParticipant(new StudyParticipant.Builder().copyOf(
                    session.getParticipant()).withOrgMembership(newOrgId).build());
            cacheProvider.setUserSession(session);
        }
    }
    
    public RequestContext updateRequestContext(UserSession session) {
        Set<String> studyIds = sponsorService.getSponsoredStudyIds(session.getAppId(), session.getParticipant().getOrgMembership());

        RequestContext.Builder builder = BridgeUtils.getRequestContext().toBuilder();
        builder.withCallerLanguages(session.getParticipant().getLanguages());
        builder.withCallerAppId(session.getAppId());
        builder.withCallerOrgMembership(session.getParticipant().getOrgMembership());
        builder.withCallerStudies(session.getParticipant().getStudyIds());
        builder.withOrgSponsoredStudies(studyIds);
        builder.withCallerRoles(session.getParticipant().getRoles());
        builder.withCallerUserId(session.getParticipant().getId());
        RequestContext reqContext = builder.build();
        BridgeUtils.setRequestContext(reqContext);
        return reqContext;
    }

    private StudyParticipant.Builder builder(UserSession session) {
        return new StudyParticipant.Builder().copyOf(session.getParticipant());
    }
}
