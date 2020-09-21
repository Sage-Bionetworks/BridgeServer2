package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

import java.util.Map;

import com.google.common.collect.ImmutableMap;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordEx3;

public class HealthDataRecordEx3ValidatorTest {
    private static final String CLIENT_INFO = "test-client";
    private static final Map<String, String> METADATA_MAP = ImmutableMap.of("foo", "bar");
    private static final String RECORD_ID = "test-record";
    private static final String STUDY_ID = "test-study";
    private static final long VERSION = 3L;

    @Test
    public void validRecord() {
        Validate.entityThrowingException(HealthDataRecordEx3Validator.INSTANCE, makeValidRecord());
    }

    @Test
    public void validWithOptionalParams() {
        HealthDataRecordEx3 record = makeValidRecord();
        record.setId(RECORD_ID);
        record.setStudyId(STUDY_ID);
        record.setClientInfo(CLIENT_INFO);
        record.setExported(true);
        record.setMetadata(METADATA_MAP);
        record.setVersion(VERSION);

        Validate.entityThrowingException(HealthDataRecordEx3Validator.INSTANCE, record);
    }

    @Test
    public void nullAppId() {
        HealthDataRecordEx3 record = makeValidRecord();
        record.setAppId(null);
        assertValidatorMessage(HealthDataRecordEx3Validator.INSTANCE, record, "appId",
                "is required");
    }

    @Test
    public void emptyAppId() {
        HealthDataRecordEx3 record = makeValidRecord();
        record.setAppId("");
        assertValidatorMessage(HealthDataRecordEx3Validator.INSTANCE, record, "appId",
                "is required");
    }

    @Test
    public void blankAppId() {
        HealthDataRecordEx3 record = makeValidRecord();
        record.setAppId("   ");
        assertValidatorMessage(HealthDataRecordEx3Validator.INSTANCE, record, "appId",
                "is required");
    }

    @Test
    public void nullHealthCode() {
        HealthDataRecordEx3 record = makeValidRecord();
        record.setHealthCode(null);
        assertValidatorMessage(HealthDataRecordEx3Validator.INSTANCE, record, "healthCode",
                "is required");
    }

    @Test
    public void emptyHealthCode() {
        HealthDataRecordEx3 record = makeValidRecord();
        record.setHealthCode("");
        assertValidatorMessage(HealthDataRecordEx3Validator.INSTANCE, record, "healthCode",
                "is required");
    }

    @Test
    public void blankHealthCode() {
        HealthDataRecordEx3 record = makeValidRecord();
        record.setHealthCode("   ");
        assertValidatorMessage(HealthDataRecordEx3Validator.INSTANCE, record, "healthCode",
                "is required");
    }

    @Test
    public void nullCreatedOn() {
        HealthDataRecordEx3 record = makeValidRecord();
        record.setCreatedOn(null);
        assertValidatorMessage(HealthDataRecordEx3Validator.INSTANCE, record, "createdOn",
                "is required");
    }

    private static HealthDataRecordEx3 makeValidRecord() {
        HealthDataRecordEx3 record = HealthDataRecordEx3.create();
        record.setAppId(TestConstants.TEST_APP_ID);
        record.setHealthCode(TestConstants.HEALTH_CODE);
        record.setCreatedOn(TestConstants.CREATED_ON.getMillis());
        return record;
    }
}
