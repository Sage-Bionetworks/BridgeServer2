package org.sagebionetworks.bridge.models.assessments;

import static org.sagebionetworks.bridge.TestConstants.COLOR_SCHEME;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.CUSTOMIZATION_FIELDS;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.IDENTIFIER;
import static org.sagebionetworks.bridge.TestConstants.LABELS;
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
    private static final String FRAMEWORK_IDENTIFIER = "framework-identifier";
    private static final String JSON_SCHEMA_URL = "json schema url";
    private static final String CATEGORY = "cognition";
    private static final Integer MINAGE = 10;
    private static final Integer MAXAGE = 17;
    private static final JsonNode ADDITIONAL_METADATA = BridgeObjectMapper.get().createObjectNode()
            .put("key1", "value1").put("key2", "value2");

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
        assessment.setLabels(LABELS);
        assessment.setColorScheme(COLOR_SCHEME);
        assessment.setCreatedOn(CREATED_ON);
        assessment.setModifiedOn(MODIFIED_ON);
        assessment.setDeleted(true);
        assessment.setVersion(8L);
        ImageResource imageResource = new ImageResource();
        imageResource.setName("default");
        imageResource.setModule("sage_survey");
        imageResource.setLabels(LABELS);
        assessment.setImageResource(imageResource);
        assessment.setFrameworkIdentifier(FRAMEWORK_IDENTIFIER);
        assessment.setJsonSchemaUrl(JSON_SCHEMA_URL);
        assessment.setCategory(CATEGORY);
        assessment.setMinAge(MINAGE);
        assessment.setMaxAge(MAXAGE);
        assessment.setAdditionalMetadata(ADDITIONAL_METADATA);

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
        assertEquals(node.size(), 27);
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
        assertEquals(node.get("colorScheme").get("background").textValue(), "#000000");
        assertEquals(node.get("colorScheme").get("foreground").textValue(), "#FFFFFF");
        assertEquals(node.get("createdOn").textValue(), CREATED_ON.toString());
        assertEquals(node.get("modifiedOn").textValue(), MODIFIED_ON.toString());
        assertTrue(node.get("deleted").booleanValue());
        assertEquals(node.get("version").longValue(), 8L);
        assertEquals(node.get("imageResource").get("name").textValue(), "default");
        assertEquals(node.get("imageResource").get("module").textValue(), "sage_survey");
        assertEquals(node.get("imageResource").get("labels").get(0).get("lang").textValue(), LABELS.get(0).getLang());
        assertEquals(node.get("imageResource").get("labels").get(0).get("value").textValue(), LABELS.get(0).getValue());
        assertEquals(node.get("imageResource").get("labels").get(1).get("lang").textValue(), LABELS.get(1).getLang());
        assertEquals(node.get("imageResource").get("labels").get(1).get("value").textValue(), LABELS.get(1).getValue());
        assertEquals(node.get("imageResource").get("type").textValue(), "ImageResource");
        assertEquals(node.get("frameworkIdentifier").textValue(), FRAMEWORK_IDENTIFIER);
        assertEquals(node.get("jsonSchemaUrl").textValue(), JSON_SCHEMA_URL);
        assertEquals(node.get("category").textValue(), CATEGORY);
        assertEquals(node.get("minAge").intValue(), MINAGE.intValue());
        assertEquals(node.get("maxAge").intValue(), MAXAGE.intValue());
        assertEquals(node.get("additionalMetadata").get("key1").textValue(), "value1");
        assertEquals(node.get("additionalMetadata").get("key2").textValue(), "value2");
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
        dto.setLabels(LABELS);
        dto.setColorScheme(COLOR_SCHEME);
        dto.setCreatedOn(CREATED_ON);
        dto.setModifiedOn(MODIFIED_ON);
        dto.setDeleted(true);
        dto.setVersion(8L);
        ImageResource imageResource = new ImageResource();
        imageResource.setName("default");
        imageResource.setModule("sage_survey");
        imageResource.setLabels(LABELS);
        dto.setImageResource(imageResource);
        dto.setFrameworkIdentifier(FRAMEWORK_IDENTIFIER);
        dto.setJsonSchemaUrl(JSON_SCHEMA_URL);
        dto.setCategory(CATEGORY);
        dto.setMinAge(MINAGE);
        dto.setMaxAge(MAXAGE);
        dto.setAdditionalMetadata(ADDITIONAL_METADATA);
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
        assertEquals(assessment.getLabels().get(0).getLang(), LABELS.get(0).getLang());
        assertEquals(assessment.getLabels().get(0).getValue(), LABELS.get(0).getValue());
        assertEquals(assessment.getLabels().get(1).getLang(), LABELS.get(1).getLang());
        assertEquals(assessment.getLabels().get(1).getValue(), LABELS.get(1).getValue());
        assertEquals(assessment.getColorScheme().getBackground(), COLOR_SCHEME.getBackground());
        assertEquals(assessment.getColorScheme().getForeground(), COLOR_SCHEME.getForeground());
        assertEquals(assessment.getColorScheme().getActivated(), COLOR_SCHEME.getActivated());
        assertEquals(assessment.getColorScheme().getInactivated(), COLOR_SCHEME.getInactivated());
        assertEquals(assessment.getCreatedOn(), CREATED_ON);
        assertEquals(assessment.getModifiedOn(), MODIFIED_ON);
        assertTrue(assessment.isDeleted());
        assertEquals(assessment.getVersion(), 8L);
        assertEquals(assessment.getImageResource().getName(), "default");
        assertEquals(assessment.getImageResource().getModule(), "sage_survey");
        assertEquals(assessment.getImageResource().getLabels().get(0).getLang(), LABELS.get(0).getLang());
        assertEquals(assessment.getImageResource().getLabels().get(0).getValue(), LABELS.get(0).getValue());
        assertEquals(assessment.getImageResource().getLabels().get(1).getLang(), LABELS.get(1).getLang());
        assertEquals(assessment.getImageResource().getLabels().get(1).getValue(), LABELS.get(1).getValue());
        assertEquals(assessment.getFrameworkIdentifier(), FRAMEWORK_IDENTIFIER);
        assertEquals(assessment.getJsonSchemaUrl(), JSON_SCHEMA_URL);
        assertEquals(assessment.getCategory(), CATEGORY);
        assertEquals(assessment.getMinAge(), MINAGE);
        assertEquals(assessment.getMaxAge(), MAXAGE);
        assertEquals(assessment.getAdditionalMetadata(), ADDITIONAL_METADATA);
    }
}
