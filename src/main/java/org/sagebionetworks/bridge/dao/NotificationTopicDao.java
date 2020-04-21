package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.notifications.NotificationTopic;

public interface NotificationTopicDao {

    List<NotificationTopic> listTopics(String appId, boolean includeDeleted);
    
    NotificationTopic getTopic(String appId, String guid);
    
    NotificationTopic createTopic(NotificationTopic topic);
    
    NotificationTopic updateTopic(NotificationTopic topic);
    
    void deleteTopic(String appId, String guid);
    
    void deleteTopicPermanently(String appId, String guid);
    
    void deleteAllTopics(String appId);
    
}
