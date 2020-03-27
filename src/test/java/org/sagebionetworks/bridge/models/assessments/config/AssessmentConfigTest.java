package org.sagebionetworks.bridge.models.assessments.config;

import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.assessments.config.AssessmentConfig;
import org.sagebionetworks.bridge.models.assessments.config.HibernateAssessmentConfig;

public class AssessmentConfigTest {
    
    @Test
    public void create() {
        JsonNode data = TestUtils.getClientData();
        
        HibernateAssessmentConfig hibConfig = new HibernateAssessmentConfig();
        hibConfig.setConfig(data);
        hibConfig.setCreatedOn(CREATED_ON);
        hibConfig.setModifiedOn(MODIFIED_ON);
        hibConfig.setGuid(GUID);
        hibConfig.setVersion(2L);

        AssessmentConfig config = AssessmentConfig.create(hibConfig);
        assertEquals(config.getConfig().toString(), data.toString());
        assertEquals(config.getCreatedOn(), CREATED_ON);
        assertEquals(config.getModifiedOn(), MODIFIED_ON);
        assertEquals(config.getVersion(), 2L);
    }
    
    @Test
    public void canRoundtripSerialize() throws Exception {
        JsonNode data = TestUtils.getClientData();
        
        AssessmentConfig config = new AssessmentConfig();
        config.setConfig(TestUtils.getClientData());
        config.setCreatedOn(CREATED_ON);
        config.setModifiedOn(MODIFIED_ON);
        config.setVersion(2L);

        JsonNode node = BridgeObjectMapper.get().valueToTree(config);
        assertEquals(node.get("config").toString(), data.toString());
        assertEquals(node.get("createdOn").textValue(), CREATED_ON.toString());
        assertEquals(node.get("modifiedOn").textValue(), MODIFIED_ON.toString());
        assertEquals(node.get("version").longValue(), 2L);
        assertEquals(node.get("type").textValue(), "AssessmentConfig");
        
        AssessmentConfig deser = BridgeObjectMapper.get().readValue(node.toString(), AssessmentConfig.class);
        assertEquals(deser.getConfig().toString(), data.toString());
        assertEquals(deser.getCreatedOn(), CREATED_ON);
        assertEquals(deser.getModifiedOn(), MODIFIED_ON);
        assertEquals(deser.getVersion(), 2L);
    }
}
