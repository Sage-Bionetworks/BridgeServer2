package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.models.GuidHolder;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.notifications.NotificationTopic;
import org.sagebionetworks.bridge.services.NotificationTopicService;

@CrossOrigin
@RestController
public class NotificationTopicController extends BaseController {
    static final StatusMessage DELETE_STATUS_MSG = new StatusMessage("Topic deleted.");
    static final StatusMessage SEND_STATUS_MSG = new StatusMessage("Message has been sent to external notification service.");
    
    private NotificationTopicService topicService;
    
    @Autowired
    final void setNotificationTopicService(NotificationTopicService topicService) {
        this.topicService = topicService;
    }
    
    @GetMapping("/v3/topics")
    public ResourceList<NotificationTopic> getAllTopics(@RequestParam(defaultValue = "false") boolean includeDeleted) {
        UserSession session = getAuthenticatedSession(DEVELOPER);

        List<NotificationTopic> list = topicService.listTopics(session.getAppId(), includeDeleted);

        return new ResourceList<>(list);
    }
    
    @PostMapping("/v3/topics")
    @ResponseStatus(HttpStatus.CREATED)
    public GuidHolder createTopic() {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        NotificationTopic topic = parseJson(NotificationTopic.class);
        topic.setStudyId(session.getAppId());
        
        NotificationTopic saved = topicService.createTopic(topic);
        
        return new GuidHolder(saved.getGuid());
    }
    
    @GetMapping("/v3/topics/{guid}")
    public NotificationTopic getTopic(@PathVariable String guid) {
        UserSession session = getAuthenticatedSession(DEVELOPER);

        return topicService.getTopic(session.getAppId(), guid);
    }
    
    @PostMapping("/v3/topics/{guid}")
    public GuidHolder updateTopic(@PathVariable String guid) {
        UserSession session = getAuthenticatedSession(DEVELOPER);

        NotificationTopic topic = parseJson(NotificationTopic.class);
        topic.setStudyId(session.getAppId());
        topic.setGuid(guid);
        
        NotificationTopic updated = topicService.updateTopic(topic);
        
        return new GuidHolder(updated.getGuid());
    }

    @DeleteMapping("/v3/topics/{guid}")
    public StatusMessage deleteTopic(@PathVariable String guid,
            @RequestParam(defaultValue = "false") boolean physical) {
        UserSession session = getAuthenticatedSession(DEVELOPER, ADMIN);

        if (physical && session.isInRole(ADMIN)) {
            topicService.deleteTopicPermanently(session.getAppId(), guid);
        } else {
            topicService.deleteTopic(session.getAppId(), guid);
        }
        return DELETE_STATUS_MSG;
    }
    
    @PostMapping("/v3/topics/{guid}/sendNotification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public StatusMessage sendNotification(@PathVariable String guid) {
        UserSession session = getAuthenticatedSession(ADMIN);
        
        NotificationMessage message = parseJson(NotificationMessage.class);
        
        topicService.sendNotification(session.getAppId(), guid, message);
        
        return SEND_STATUS_MSG;
    }}
