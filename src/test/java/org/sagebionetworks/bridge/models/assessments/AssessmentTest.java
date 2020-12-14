package org.sagebionetworks.bridge.models.assessments;

import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.CUSTOMIZATION_FIELDS;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.IDENTIFIER;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_OWNER_ID;
import static org.sagebionetworks.bridge.TestConstants.STRING_TAGS;
import static org.sagebionetworks.bridge.TestConstants.TAGS;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.models.OperatingSystem.ANDROID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableSet;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class AssessmentTest {
    @Test
    public void revisionDefaultsToOne() {
        Assessment dto = new Assessment();
        assertEquals(dto.getRevision(), 1);
    }
    
    @Test
    public void testFields() {
        Assessment dto = createAssessment();
        assertAssessment(dto);
    }
    
    @Test
    public void createFactoryMethod() {
        HibernateAssessment assessment = new HibernateAssessment();
        assessment.setAppId(TEST_APP_ID);
        assessment.setGuid(GUID);
        assessment.setIdentifier(IDENTIFIER);
        assessment.setRevision(5);
        assessment.setOwnerId(TEST_OWNER_ID);
        assessment.setTitle("title");
        assessment.setSummary("summary");
        assessment.setOsName(ANDROID);
        assessment.setOriginGuid("originGuid");
        assessment.setValidationStatus("validationStatus");
        assessment.setNormingStatus("normingStatus");
        assessment.setTags(TAGS);
        assessment.setCustomizationFields(CUSTOMIZATION_FIELDS);
        assessment.setCreatedOn(CREATED_ON);
        assessment.setModifiedOn(MODIFIED_ON);
        assessment.setDeleted(true);
        assessment.setVersion(8L);
        
        Assessment dto = Assessment.create(assessment);
        assertAssessment(dto);
    }
    
    @Test
    public void copyFactoryMethod() {
        Assessment assessment = createAssessment();
        Assessment copy = Assessment.copy(assessment);

        assertNotSame(copy, assessment);
        assessment.setTags(null);
        assertNotNull(copy.getTags());
        assertAssessment(copy);
    }

    @Test
    public void serializeRountrip() throws Exception {
        Assessment dto = createAssessment();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(dto);
        assertEquals(node.size(), 18);
        assertEquals(node.get("guid").textValue(), GUID);
        assertEquals(node.get("identifier").textValue(), IDENTIFIER);
        assertEquals(node.get("revision").intValue(), 5);
        assertEquals(node.get("ownerId").textValue(), TEST_OWNER_ID);
        assertEquals(node.get("title").textValue(), "title");
        assertEquals(node.get("summary").textValue(), "summary");
        assertEquals(node.get("osName").textValue(), ANDROID);
        assertEquals(node.get("originGuid").textValue(), "originGuid");
        assertEquals(node.get("validationStatus").textValue(), "validationStatus");
        assertEquals(node.get("normingStatus").textValue(), "normingStatus");
        assertEquals(node.get("createdOn").textValue(), CREATED_ON.toString());
        assertEquals(node.get("modifiedOn").textValue(), MODIFIED_ON.toString());
        assertTrue(node.get("deleted").booleanValue());
        assertEquals(node.get("version").longValue(), 8L);
        assertEquals(node.get("type").textValue(), "Assessment");
        
        ArrayNode tags = (ArrayNode)node.get("tags");
        assertEquals(tags.size(), 2);
        assertEquals(tags.get(0).textValue(), "tag1");
        assertEquals(tags.get(1).textValue(), "tag2");
        
        ObjectNode customFields = (ObjectNode)node.get("customizationFields");
        ArrayNode node1 = (ArrayNode)customFields.get("guid1");

        assertEquals(node1.size(), 2);
        ObjectNode propInfo1 = (ObjectNode)node1.get(0);
        assertEquals(propInfo1.get("propName").textValue(), "foo");
        assertEquals(propInfo1.get("label").textValue(), "foo label");
        assertEquals(propInfo1.get("description").textValue(), "a description");
        assertEquals(propInfo1.get("propType").textValue(), "string");
        assertEquals(propInfo1.get("type").textValue(), "PropertyInfo");
        
        ObjectNode propInfo2 = (ObjectNode)node1.get(1);
        assertEquals(propInfo2.get("propName").textValue(), "bar");
        assertEquals(propInfo2.get("label").textValue(), "bar label");
        assertEquals(propInfo2.get("description").textValue(), "a description");
        assertEquals(propInfo2.get("propType").textValue(), "string");
        assertEquals(propInfo2.get("type").textValue(), "PropertyInfo");
        
        Assessment deser = BridgeObjectMapper.get().readValue(node.toString(), Assessment.class);
        assertAssessment(deser);
    }

    public static Assessment createAssessment() {
        Assessment dto = new Assessment();
        dto.setGuid(GUID);
        dto.setIdentifier(IDENTIFIER);
        dto.setRevision(5);
        dto.setOwnerId(TEST_OWNER_ID);
        dto.setTitle("title");
        dto.setSummary("summary");
        dto.setOsName(ANDROID);
        dto.setOriginGuid("originGuid");
        dto.setValidationStatus("validationStatus");
        dto.setNormingStatus("normingStatus");
        dto.setMinutesToComplete(10);
        dto.setTags(STRING_TAGS);
        dto.setCustomizationFields(CUSTOMIZATION_FIELDS);
        dto.setCreatedOn(CREATED_ON);
        dto.setModifiedOn(MODIFIED_ON);
        dto.setDeleted(true);
        dto.setVersion(8L);
        return dto;
    }
    
    private void assertAssessment(Assessment assessment) {
        assertEquals(assessment.getGuid(), GUID);
        assertEquals(assessment.getIdentifier(), IDENTIFIER);
        assertEquals(assessment.getRevision(), 5);
        assertEquals(assessment.getOwnerId(), TEST_OWNER_ID);
        assertEquals(assessment.getTitle(), "title");
        assertEquals(assessment.getSummary(), "summary");
        assertEquals(assessment.getOsName(), ANDROID);
        assertEquals(assessment.getOriginGuid(), "originGuid");
        assertEquals(assessment.getValidationStatus(), "validationStatus");
        assertEquals(assessment.getNormingStatus(), "normingStatus");
        assertEquals(assessment.getTags(), ImmutableSet.of("tag1", "tag2"));
        assertEquals(assessment.getCustomizationFields(), CUSTOMIZATION_FIELDS);
        assertEquals(assessment.getCreatedOn(), CREATED_ON);
        assertEquals(assessment.getModifiedOn(), MODIFIED_ON);
        assertTrue(assessment.isDeleted());
        assertEquals(assessment.getVersion(), 8L);         
    }
}
