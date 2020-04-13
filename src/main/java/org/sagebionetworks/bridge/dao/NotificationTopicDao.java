package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.notifications.NotificationTopic;

public interface NotificationTopicDao {

    List<NotificationTopic> listTopics(String studyId, boolean includeDeleted);
    
    NotificationTopic getTopic(String studyId, String guid);
    
    NotificationTopic createTopic(NotificationTopic topic);
    
    NotificationTopic updateTopic(NotificationTopic topic);
    
    void deleteTopic(String studyId, String guid);
    
    void deleteTopicPermanently(String studyId, String guid);
    
    void deleteAllTopics(String studyId);
    
}
