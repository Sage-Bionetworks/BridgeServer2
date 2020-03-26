package org.sagebionetworks.bridge.models.assessments;

import static org.sagebionetworks.bridge.TestConstants.APPLICATION_JSON;
import static org.sagebionetworks.bridge.TestConstants.CONTRIBUTORS;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.CREATORS;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.PUBLISHERS;
import static org.sagebionetworks.bridge.TestConstants.TEST_BASE_URL;
import static org.sagebionetworks.bridge.models.assessments.ResourceCategory.PUBLICATION;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class AssessmentResourceTest {
    
    @Test
    public void testFactoryMethod() {
        HibernateAssessmentResource model = HibernateAssessmentResourceTest.createHibernateAssessmentResource();
        
        AssessmentResource resource = AssessmentResource.create(model);
        assertAssessmentResource(resource);
    }
    
    @Test
    public void testFactoryMethodWithNulls() { 
        AssessmentResource resource = AssessmentResource.create(new HibernateAssessmentResource());
        
        assertNull(resource.getGuid());
        assertNull(resource.getTitle());
        assertNull(resource.getCategory());
        assertNull(resource.getUrl());
        assertNull(resource.getFormat());
        assertNull(resource.getDate());
        assertNull(resource.getDescription());
        assertNull(resource.getContributors());
        assertNull(resource.getCreators());
        assertNull(resource.getPublishers());
        assertNull(resource.getLanguage());
        assertNull(resource.getMinRevision());
        assertNull(resource.getMaxRevision());
        assertEquals(resource.getCreatedAtRevision(), 0);
        assertNull(resource.getCreatedOn());
        assertNull(resource.getModifiedOn());
        assertFalse(resource.isDeleted());
        assertEquals(resource.getVersion(), 0);
        assertFalse(resource.isUpToDate());
    }
    
    @Test
    public void roundtripSerialization() throws Exception {
        AssessmentResource resource =  createAssessmentResource();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(resource);
        assertEquals(node.get("guid").textValue(), GUID);
        assertEquals(node.get("title").textValue(), "title");
        assertEquals(node.get("category").textValue(), PUBLICATION.name().toLowerCase());
        assertEquals(node.get("url").textValue(), TEST_BASE_URL);
        assertEquals(node.get("format").textValue(), APPLICATION_JSON);
        assertEquals(node.get("date").textValue(), "2020-10-10");
        assertEquals(node.get("description").textValue(), "description");
        assertEquals(node.get("language").textValue(), "en");
        assertEquals(node.get("minRevision").intValue(), 3);
        assertEquals(node.get("maxRevision").intValue(), 10);
        assertEquals(node.get("createdAtRevision").intValue(), 3);
        assertEquals(node.get("createdOn").textValue(), CREATED_ON.toString());
        assertEquals(node.get("modifiedOn").textValue(), MODIFIED_ON.toString());
        assertTrue(node.get("deleted").booleanValue());
        assertEquals(node.get("version").longValue(), 10L);
        assertFalse(node.get("upToDate").booleanValue());
        assertEquals(node.get("type").textValue(), "AssessmentResource");
        
        ArrayNode contribs = (ArrayNode)node.get("contributors");
        assertEquals(contribs.get(0).textValue(), "contrib1");
        assertEquals(contribs.get(1).textValue(), "contrib2");
        
        ArrayNode creators = (ArrayNode)node.get("creators");
        assertEquals(creators.get(0).textValue(), "creator1");
        assertEquals(creators.get(1).textValue(), "creator2");
        
        ArrayNode publishers = (ArrayNode)node.get("publishers");
        assertEquals(publishers.get(0).textValue(), "pub1");
        assertEquals(publishers.get(1).textValue(), "pub2");
        
        AssessmentResource deser = BridgeObjectMapper.get().readValue(node.toString(), AssessmentResource.class);
        assertAssessmentResource(deser);
    }
    
    private void assertAssessmentResource(AssessmentResource resource) {
        assertEquals(resource.getGuid(), GUID);
        assertEquals(resource.getTitle(), "title");
        assertEquals(resource.getCategory(), PUBLICATION);
        assertEquals(resource.getUrl(), TEST_BASE_URL);
        assertEquals(resource.getFormat(), APPLICATION_JSON);
        assertEquals(resource.getDate(), "2020-10-10");
        assertEquals(resource.getDescription(), "description");
        assertEquals(resource.getContributors(), CONTRIBUTORS);
        assertEquals(resource.getCreators(), CREATORS);
        assertEquals(resource.getPublishers(), PUBLISHERS);
        assertEquals(resource.getLanguage(), "en");
        assertEquals(resource.getMinRevision(), Integer.valueOf(3));
        assertEquals(resource.getMaxRevision(), Integer.valueOf(10));
        assertEquals(resource.getCreatedAtRevision(), 3);
        assertEquals(resource.getCreatedOn(), CREATED_ON);
        assertEquals(resource.getModifiedOn(), MODIFIED_ON);
        assertTrue(resource.isDeleted());
        assertEquals(resource.getVersion(), 10);
    }
    
    public static AssessmentResource createAssessmentResource() {
        AssessmentResource dto = new AssessmentResource();
        dto.setGuid(GUID);
        dto.setTitle("title");
        dto.setCategory(PUBLICATION);
        dto.setUrl(TEST_BASE_URL);
        dto.setFormat(APPLICATION_JSON);
        dto.setDate("2020-10-10");
        dto.setDescription("description");
        dto.setContributors(CONTRIBUTORS);
        dto.setCreators(CREATORS);
        dto.setPublishers(PUBLISHERS);
        dto.setLanguage("en");
        dto.setMinRevision(3);
        dto.setMaxRevision(10);
        dto.setCreatedAtRevision(3);
        dto.setCreatedOn(CREATED_ON);
        dto.setModifiedOn(MODIFIED_ON);
        dto.setDeleted(true);
        dto.setVersion(10);
        return dto;
    }
    
}
