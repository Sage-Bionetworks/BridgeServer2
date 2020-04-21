package org.sagebionetworks.bridge.dynamodb;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.notifications.NotificationTopic;

import com.fasterxml.jackson.databind.JsonNode;

public class DynamoNotificationTopicTest {

    private static final DateTime DATE_TIME = DateTime.now(DateTimeZone.UTC);
    private static final long TIMESTAMP = DATE_TIME.getMillis();
    
    @Test
    public void canSerialize() throws Exception {
        // Create POJO.
        NotificationTopic topic = NotificationTopic.create();
        topic.setGuid("ABC");
        topic.setName("My Test Topic");
        topic.setShortName("Test Topic");
        topic.setAppId(TEST_APP_ID);
        topic.setTopicARN("aTopicARN");
        topic.setDescription("A description.");
        topic.setCreatedOn(TIMESTAMP);
        topic.setModifiedOn(TIMESTAMP);
        topic.setDeleted(true);

        Criteria criteria = Criteria.create();
        criteria.setAllOfGroups(ImmutableSet.of("group1", "group2"));
        topic.setCriteria(criteria);

        // Serialize to JSON.
        JsonNode node = BridgeObjectMapper.get().valueToTree(topic);
        assertEquals(node.get("guid").textValue(), "ABC");
        assertEquals(node.get("name").textValue(), "My Test Topic");
        assertEquals(node.get("shortName").textValue(), "Test Topic");
        assertEquals(node.get("type").textValue(), "NotificationTopic");
        assertEquals(node.get("description").textValue(), "A description.");
        assertEquals(node.get("createdOn").textValue(), DATE_TIME.toString());
        assertEquals(node.get("modifiedOn").textValue(), DATE_TIME.toString());
        assertNull(node.get("studyId"));
        assertNull(node.get("topicARN"));
        assertTrue(node.get("deleted").booleanValue());

        JsonNode criteriaNode = node.get("criteria");
        JsonNode allOfGroupsNode = criteriaNode.get("allOfGroups");
        assertEquals(allOfGroupsNode.size(), 2);

        Set<String> allOfGroupsJsonSet = new HashSet<>();
        for (JsonNode oneAllOfGroupNode : allOfGroupsNode) {
            allOfGroupsJsonSet.add(oneAllOfGroupNode.textValue());
        }
        assertEquals(allOfGroupsJsonSet, criteria.getAllOfGroups());

        // De-serialize back to POJO.
        // The values that are not serialized are provided by the service, they aren't
        // settable by the API caller.
        NotificationTopic deser = BridgeObjectMapper.get().readValue(node.toString(), NotificationTopic.class);
        assertEquals(deser.getGuid(), "ABC");
        assertEquals(deser.getName(), "My Test Topic");
        assertEquals(deser.getShortName(), "Test Topic");
        assertNull(deser.getAppId(), TEST_APP_ID);
        assertEquals(deser.getDescription(), "A description.");
        assertEquals(deser.getCreatedOn(), TIMESTAMP);
        assertEquals(deser.getModifiedOn(), TIMESTAMP);
        assertNull(deser.getTopicARN());
        assertEquals(deser.getCriteria(), criteria);
        assertTrue(deser.isDeleted());
    }
}
