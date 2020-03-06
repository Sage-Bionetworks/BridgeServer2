package org.sagebionetworks.bridge.models.assessments;

import static org.sagebionetworks.bridge.TestConstants.APPLICATION_JSON;
import static org.sagebionetworks.bridge.TestConstants.APP_ID;
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

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class HibernateAssessmentResourceTest {
    
    @Test
    public void testFactoryMethod() {
        AssessmentResource dto = AssessmentResourceTest.createAssessmentResource();
        
        HibernateAssessmentResource resource = HibernateAssessmentResource.create(dto, APP_ID, "assessmentId");
        assertHibernateAssessmentResource(resource);
    }
    
    @Test
    public void testFactoryMethodWithNulls() {
        HibernateAssessmentResource resource = HibernateAssessmentResource.create(new AssessmentResource(), APP_ID, null);
        assertNull(resource.getGuid());
        assertNull(resource.getAssessmentId());
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
    }
    
    @Test
    public void roundtripSerialization() throws Exception {
        HibernateAssessmentResource resource =  createHibernateAssessmentResource();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(resource);
        
        HibernateAssessmentResource deser = BridgeObjectMapper.get()
                .readValue(node.toString(), HibernateAssessmentResource.class);
        assertHibernateAssessmentResource(deser);
    }

    private void assertHibernateAssessmentResource(HibernateAssessmentResource resource) {
        assertEquals(resource.getGuid(), GUID);
        assertEquals(resource.getAssessmentId(), "assessmentId");
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
    
    public static HibernateAssessmentResource createHibernateAssessmentResource() { 
        HibernateAssessmentResource resource = new HibernateAssessmentResource();
        resource.setGuid(GUID);
        resource.setAssessmentId("assessmentId");
        resource.setTitle("title");
        resource.setCategory(PUBLICATION);
        resource.setUrl(TEST_BASE_URL);
        resource.setFormat(APPLICATION_JSON);
        resource.setDate("2020-10-10");
        resource.setDescription("description");
        resource.setContributors(CONTRIBUTORS);
        resource.setCreators(CREATORS);
        resource.setPublishers(PUBLISHERS);
        resource.setLanguage("en");
        resource.setMinRevision(3);
        resource.setMaxRevision(10);
        resource.setCreatedAtRevision(3);
        resource.setCreatedOn(CREATED_ON);
        resource.setModifiedOn(MODIFIED_ON);
        resource.setDeleted(true);
        resource.setVersion(10);
        return resource;
    }
}
