package org.sagebionetworks.bridge.spring.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.GuidHolder;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;
import org.sagebionetworks.bridge.models.notifications.SubscriptionRequest;
import org.sagebionetworks.bridge.models.notifications.SubscriptionStatus;
import org.sagebionetworks.bridge.services.NotificationTopicService;
import org.sagebionetworks.bridge.services.NotificationsService;

@CrossOrigin
@RestController
public class NotificationRegistrationController extends BaseController {
    
    static final StatusMessage DELETED_MSG = new StatusMessage("Push notification registration deleted.");

    private NotificationsService notificationsService;
    
    private NotificationTopicService topicService;
    
    @Autowired
    final void setNotificationService(NotificationsService notificationsService) {
        this.notificationsService = notificationsService;
    }
    
    @Autowired
    final void setNotificationTopicService(NotificationTopicService topicService) {
        this.topicService = topicService;
    }

    @GetMapping("/v3/notifications")
    public ResourceList<NotificationRegistration> getAllRegistrations() {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        List<NotificationRegistration> registrations = notificationsService.listRegistrations(session.getHealthCode());
        
        return new ResourceList<>(registrations);
    }
    
    @PostMapping("/v3/notifications")
    @ResponseStatus(HttpStatus.CREATED)
    public GuidHolder createRegistration() {
        UserSession session = getAuthenticatedAndConsentedSession();

        NotificationRegistration registration = parseJson(NotificationRegistration.class);
        registration.setHealthCode(session.getHealthCode());
        
        updateRequestContext(session);
        
        NotificationRegistration result = notificationsService.createRegistration(session.getStudyIdentifier(),
                BridgeUtils.getRequestContext(), registration);
        
        return new GuidHolder(result.getGuid());
    }
    
    @PostMapping("/v3/notifications/{guid}")
    public GuidHolder updateRegistration(@PathVariable String guid) {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        NotificationRegistration registration = parseJson(NotificationRegistration.class);
        registration.setHealthCode(session.getHealthCode());
        registration.setGuid(guid);
        
        NotificationRegistration result = notificationsService.updateRegistration(session.getStudyIdentifier(),
                registration);
        
        return new GuidHolder(result.getGuid());
    }
    
    @GetMapping("/v3/notifications/{guid}")
    public NotificationRegistration getRegistration(@PathVariable String guid) {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        return notificationsService.getRegistration(session.getHealthCode(), guid);
    }
    
    @DeleteMapping("/v3/notifications/{guid}")
    public StatusMessage deleteRegistration(@PathVariable String guid) {
        UserSession session = getAuthenticatedAndConsentedSession();
        notificationsService.deleteRegistration(session.getStudyIdentifier(), session.getHealthCode(), guid);
        return DELETED_MSG;
    }

    @GetMapping("/v3/notifications/{guid}/subscriptions")
    public ResourceList<SubscriptionStatus> getSubscriptionStatuses(@PathVariable String guid) {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        List<SubscriptionStatus> statuses = topicService.currentSubscriptionStatuses(session.getStudyIdentifier(),
                session.getHealthCode(), guid);
        return new ResourceList<>(statuses);
    }
    
    @PostMapping("/v3/notifications/{guid}/subscriptions")
    public ResourceList<SubscriptionStatus> subscribe(@PathVariable String guid) {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        SubscriptionRequest request = parseJson(SubscriptionRequest.class);
        
        List<SubscriptionStatus> statuses = topicService.subscribe(session.getStudyIdentifier(),
                session.getHealthCode(), guid, request.getTopicGuids());
        return new ResourceList<>(statuses);
    }
}
