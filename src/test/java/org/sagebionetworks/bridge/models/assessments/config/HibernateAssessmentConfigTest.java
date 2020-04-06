package org.sagebionetworks.bridge.models.assessments.config;

import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.assessments.config.AssessmentConfig;
import org.sagebionetworks.bridge.models.assessments.config.HibernateAssessmentConfig;

public class HibernateAssessmentConfigTest {
    @Test
    public void create() {
        JsonNode data = TestUtils.getClientData();
        
        AssessmentConfig config = new AssessmentConfig();
        config.setConfig(data);
        config.setCreatedOn(CREATED_ON);
        config.setModifiedOn(MODIFIED_ON);
        config.setVersion(2L);

        HibernateAssessmentConfig hibConfig = HibernateAssessmentConfig.create(GUID, config);
        assertEquals(hibConfig.getGuid(), GUID);
        assertEquals(hibConfig.getConfig(), data);
        assertEquals(hibConfig.getCreatedOn(), CREATED_ON);
        assertEquals(hibConfig.getModifiedOn(), MODIFIED_ON);
        assertEquals(hibConfig.getVersion(), 2L);
    }
}
