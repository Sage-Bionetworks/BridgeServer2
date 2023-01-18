package org.sagebionetworks.bridge.dynamodb;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.lang.reflect.Method;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;

// more tests are located in DemographicValuesValidationConfigTest
public class DynamoDemographicValuesValidationConfigTest {
    DynamoDemographicValuesValidationConfig config;

    @BeforeMethod
    public void beforeMethod() {
        config = new DynamoDemographicValuesValidationConfig();
    }

    @Test
    public void getHash_nullAppId() {
        config.setAppId(null);
        config.setStudyId(TEST_STUDY_ID);
        assertNull(config.getHashKey());
    }

    @Test
    public void getHash_appLevel() {
        config.setAppId(TEST_APP_ID);
        config.setStudyId(null);
        assertEquals(config.getHashKey(), TEST_APP_ID + ":");
    }

    @Test
    public void getHash_studyLevel() {
        config.setAppId(TEST_APP_ID);
        config.setStudyId(TEST_STUDY_ID);
        assertEquals(config.getHashKey(), TEST_APP_ID + ":" + TEST_STUDY_ID);
    }

    @Test
    public void setHash_null() {
        config.setHashKey(null);
        assertNull(config.getAppId());
        assertNull(config.getStudyId());
    }

    @Test
    public void setHash_0parts_empty() {
        config.setHashKey("");
        assertNull(config.getAppId());
        assertNull(config.getStudyId());
    }

    @Test
    public void setHash_0parts_nonempty() {
        config.setHashKey(":");
        assertNull(config.getAppId());
        assertNull(config.getStudyId());
    }

    @Test
    public void setHash_1part() {
        config.setHashKey(TEST_APP_ID + ":");
        assertEquals(config.getAppId(), TEST_APP_ID);
        assertNull(config.getStudyId());
    }

    @Test
    public void setHash_2parts() {
        config.setHashKey(TEST_APP_ID + ":" + TEST_STUDY_ID);
        assertEquals(config.getAppId(), TEST_APP_ID);
        assertEquals(config.getStudyId(), TEST_STUDY_ID);
    }

    @Test
    public void setHash_3parts() {
        config.setHashKey(TEST_APP_ID + ":" + TEST_STUDY_ID + ":" + "foo");
        assertEquals(config.getAppId(), TEST_APP_ID);
        assertEquals(config.getStudyId(), TEST_STUDY_ID + ":" + "foo");
    }

    @Test
    public void rangeKeyAnnotation() throws NoSuchMethodException, SecurityException {
        Method method = DynamoDemographicValuesValidationConfig.class.getMethod("getCategoryName");
        assertTrue(method.isAnnotationPresent(DynamoDBRangeKey.class));
    }
}
